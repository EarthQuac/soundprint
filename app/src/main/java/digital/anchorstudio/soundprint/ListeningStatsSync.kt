package digital.anchorstudio.soundprint

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CloudListeningHistoryEvent(
    @SerialName("id") val id:String,
    @SerialName("user_id") val userId:String,
    @SerialName("track_id") val trackId:String,
    @SerialName("event_type") val eventType:String,
    @SerialName("timestamp") val timestamp:Long,
    @SerialName("position_ms") val positionMs:Long,
    @SerialName("duration_ms") val durationMs:Long
)

internal object ListeningStatsSync {
    fun enqueue(context:Context,userId:String) {
        if(userId.isBlank()) return
        val request=OneTimeWorkRequestBuilder<ListeningStatsSyncWorker>()
            .setInputData(workDataOf(ListeningStatsSyncWorker.INPUT_USER_ID to userId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "soundprint-listening-stats-$userId",ExistingWorkPolicy.APPEND_OR_REPLACE,request
        )
    }
}

internal class ListeningStatsSyncWorker(context:Context,params:androidx.work.WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val userId=inputData.getString(INPUT_USER_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        return try {
            val client=SoundprintSupabase.client
            val signedIn=client.auth.currentUserOrNull() ?: return Result.retry()
            if(signedIn.id!=userId) return Result.success()
            val preferences=applicationContext.getSharedPreferences("soundprint_account_security",Context.MODE_PRIVATE)
            val clearKey="listening_stats_clear_cloud_$userId"
            if(preferences.getBoolean(clearKey,false)) {
                client.from("listening_history").delete { filter { eq("user_id",userId) } }
                preferences.edit().remove(clearKey).apply()
            }
            val manager=MusicLibraryManager.getInstance(applicationContext)
            while(true) {
                val pending=manager.pendingListeningEvents(userId,1000)
                if(pending.isEmpty()) break
                pending.chunked(200).forEach { batch ->
                    val records=batch.mapNotNull { event ->
                        val id=event.syncId ?: return@mapNotNull null
                        CloudListeningHistoryEvent(id,userId,event.trackId,event.type.name,event.timestamp,event.positionMs,event.durationMs)
                    }
                    if(records.isNotEmpty()) {
                        client.from("listening_history").upsert(records) { onConflict = "id" }
                        manager.markListeningEventsSynced(records.map { it.id })
                    }
                }
                if(pending.size<1000) break
            }
            var offset=0
            while(true) {
                val rows=client.from("listening_history").select {
                    filter { eq("user_id",userId) }
                    range(offset.toLong()..(offset+PAGE_SIZE-1).toLong())
                }.decodeList<CloudListeningHistoryEvent>()
                if(rows.isEmpty()) break
                rows.forEach { row ->
                    val type=runCatching { HistoryEventType.valueOf(row.eventType) }.getOrNull() ?: return@forEach
                    manager.mergeRemoteListeningEvent(LibraryHistoryEvent(
                        0L,row.trackId,type,row.timestamp,row.positionMs,row.durationMs,row.id,row.userId,true
                    ))
                }
                if(rows.size<PAGE_SIZE) break
                offset+=rows.size
            }
            Result.success()
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) {
            Log.w(TAG,"Listening statistics sync failed; WorkManager will retry.",error)
            Result.retry()
        }
    }
    companion object {
        const val INPUT_USER_ID="listening_stats_user_id"
        private const val PAGE_SIZE=500
        private const val TAG="ListeningStatsSync"
    }
}
