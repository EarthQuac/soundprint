package digital.anchorstudio.soundprint

import android.content.Context

/** Bundled fallback covers, keyed deterministically so a song keeps the same artwork. */
internal object SoundprintArtwork {
    private val resources = intArrayOf(
        R.drawable.art_01,
        R.drawable.art_02,
        R.drawable.art_03,
        R.drawable.art_04,
        R.drawable.art_05,
        R.drawable.art_06,
        R.drawable.art_07,
        R.drawable.art_08,
        R.drawable.art_09,
        R.drawable.art_10,
        R.drawable.art_11,
        R.drawable.art_12,
        R.drawable.art_13,
        R.drawable.art_14,
        R.drawable.art_15,
        R.drawable.art_16,
        R.drawable.art_17,
        R.drawable.art_18,
        R.drawable.art_19,
        R.drawable.art_20,
        R.drawable.art_21,
        R.drawable.art_22,
        R.drawable.art_23,
        R.drawable.art_24,
        R.drawable.art_25,
        R.drawable.art_26,
        R.drawable.art_27,
        R.drawable.art_28,
        R.drawable.art_29,
        R.drawable.art_30,
        R.drawable.art_31,
        R.drawable.art_32,
        R.drawable.art_33,
        R.drawable.art_34,
        R.drawable.art_35,
        R.drawable.art_36,
        R.drawable.art_37,
        R.drawable.art_38,
        R.drawable.art_39,
        R.drawable.art_40,
        R.drawable.art_41,
        R.drawable.art_42,
        R.drawable.art_43,
        R.drawable.art_44,
        R.drawable.art_45,
        R.drawable.art_46,
        R.drawable.art_47,
        R.drawable.art_48,
        R.drawable.art_49,
        R.drawable.art_50,
        R.drawable.art_51,
        R.drawable.art_52,
        R.drawable.art_53,
        R.drawable.art_54,
        R.drawable.art_55,
        R.drawable.art_56,
        R.drawable.art_57,
        R.drawable.art_58,
        R.drawable.art_59,
        R.drawable.art_60,
        R.drawable.art_61,
        R.drawable.art_62,
        R.drawable.art_63,
        R.drawable.art_64,
        R.drawable.art_65,
        R.drawable.art_66,
        R.drawable.art_67,
        R.drawable.art_68,
        R.drawable.art_69,
        R.drawable.art_70,
        R.drawable.art_71,
        R.drawable.art_72,
        R.drawable.art_73,
        R.drawable.art_74,
        R.drawable.art_75,
        R.drawable.art_76,
        R.drawable.art_77,
        R.drawable.art_78
    )

    fun resourceFor(seed: String): Int {
        var hash = 0
        seed.forEach { character -> hash = (hash * 31 + character.code) and 0x7fffffff }
        return resources[hash % resources.size]
    }

    fun uri(context: Context, seed: String): String =
        "android.resource://${context.packageName}/${resourceFor(seed)}"

    fun isBundledUri(context: Context, uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        val prefix = "android.resource://${context.packageName}/"
        if (!uri.startsWith(prefix)) return false
        val resourceId = uri.removePrefix(prefix).substringAfterLast('/').toIntOrNull() ?: return false
        return resources.contains(resourceId)
    }
}
