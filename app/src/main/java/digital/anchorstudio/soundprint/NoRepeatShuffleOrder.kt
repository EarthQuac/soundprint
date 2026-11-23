package digital.anchorstudio.soundprint

import androidx.media3.common.C
import androidx.media3.common.ShuffleOrder
import java.util.Random

/**
 * A stable shuffled permutation for Media3. Each queue index appears exactly once in a cycle;
 * queue edits preserve the existing order and only randomize newly inserted entries.
 */
internal class NoRepeatShuffleOrder private constructor(
    private val shuffledIndices: IntArray,
    private val random: Random
) : ShuffleOrder {
    constructor(length: Int, seed: Long = System.nanoTime()) : this(
        shuffled(length, Random(seed)),
        Random(seed xor 0x5DEECE66DL)
    )

    private val positions = IntArray(shuffledIndices.size).also { inverse ->
        shuffledIndices.forEachIndexed { position, index ->
            if (index in inverse.indices) inverse[index] = position
        }
    }

    override fun getLength(): Int = shuffledIndices.size

    override fun getFirstIndex(): Int = shuffledIndices.firstOrNull() ?: C.INDEX_UNSET

    override fun getLastIndex(): Int = shuffledIndices.lastOrNull() ?: C.INDEX_UNSET

    override fun getNextIndex(index: Int): Int {
        if (index !in positions.indices) return C.INDEX_UNSET
        val nextPosition = positions[index] + 1
        return shuffledIndices.getOrElse(nextPosition) { C.INDEX_UNSET }
    }

    override fun getPreviousIndex(index: Int): Int {
        if (index !in positions.indices) return C.INDEX_UNSET
        val previousPosition = positions[index] - 1
        return if (previousPosition >= 0) shuffledIndices[previousPosition] else C.INDEX_UNSET
    }

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        require(insertionIndex in 0..shuffledIndices.size)
        require(insertionCount >= 0)
        if (insertionCount == 0) return NoRepeatShuffleOrder(shuffledIndices.copyOf(), random)
        val result = shuffledIndices.map { index ->
            if (index >= insertionIndex) index + insertionCount else index
        }.toMutableList()
        repeat(insertionCount) { offset ->
            val newIndex = insertionIndex + offset
            result.add(random.nextInt(result.size + 1), newIndex)
        }
        return NoRepeatShuffleOrder(result.toIntArray(), random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        require(indexFrom in 0..shuffledIndices.size)
        require(indexToExclusive in indexFrom..shuffledIndices.size)
        val removedCount = indexToExclusive - indexFrom
        val result = shuffledIndices.asSequence()
            .filterNot { it in indexFrom until indexToExclusive }
            .map { if (it >= indexToExclusive) it - removedCount else it }
            .toList()
            .toIntArray()
        return NoRepeatShuffleOrder(result, random)
    }

    override fun cloneAndSet(shuffledIndices: IntArray): ShuffleOrder =
        fromOrder(shuffledIndices.size, shuffledIndices.asList(), random.nextLong())

    fun snapshotIndices(): List<Int> = shuffledIndices.toList()

    companion object {
        fun fromOrder(length: Int, preferredOrder: List<Int>, seed: Long = System.nanoTime()): NoRepeatShuffleOrder {
            require(length >= 0)
            val seen = BooleanArray(length)
            val valid = ArrayList<Int>(length)
            preferredOrder.forEach { index ->
                if (index in 0 until length && !seen[index]) {
                    seen[index] = true
                    valid += index
                }
            }
            val missing = (0 until length).filterNot { seen[it] }.toMutableList()
            shuffleInPlace(missing, Random(seed))
            valid.addAll(missing)
            return NoRepeatShuffleOrder(valid.toIntArray(), Random(seed xor 0x5DEECE66DL))
        }

        private fun shuffled(length: Int, random: Random): IntArray {
            val result = (0 until length.coerceAtLeast(0)).toMutableList()
            shuffleInPlace(result, random)
            return result.toIntArray()
        }

        private fun shuffleInPlace(values: MutableList<Int>, random: Random) {
            for (index in values.lastIndex downTo 1) {
                val other = random.nextInt(index + 1)
                val value = values[index]
                values[index] = values[other]
                values[other] = value
            }
        }
    }
}
