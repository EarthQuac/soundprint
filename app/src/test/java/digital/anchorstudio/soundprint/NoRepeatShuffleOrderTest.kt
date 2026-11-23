package digital.anchorstudio.soundprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoRepeatShuffleOrderTest {
    @Test
    fun largeLibraryVisitsEveryIndexExactlyOnceBeforeCycleEnds() {
        val order = NoRepeatShuffleOrder(1_200, seed = 42L)
        val visited = mutableListOf<Int>()
        var index = order.getFirstIndex()
        while (index != -1) {
            visited += index
            index = order.getNextIndex(index)
        }

        assertEquals(1_200, visited.size)
        assertEquals((0 until 1_200).toSet(), visited.toSet())
        assertEquals(1_200, visited.distinct().size)
        assertEquals(-1, order.getPreviousIndex(order.getFirstIndex()))
        assertEquals(-1, order.getNextIndex(order.getLastIndex()))
    }

    @Test
    fun startingShuffleAtCurrentTrackKeepsEveryUpcomingIndexInTheCycle() {
        val order = NoRepeatShuffleOrder.startingWith(1_200, firstIndex = 633, seed = 123L)
        val indices = order.snapshotIndices()
        assertEquals(633, order.getFirstIndex())
        assertEquals(1_200, indices.size)
        assertEquals(1_200, indices.distinct().size)
        assertEquals((0 until 1_200).toSet(), indices.toSet())
    }

    @Test
    fun queueInsertAndRemoveKeepAValidUniquePermutation() {
        val original = NoRepeatShuffleOrder(800, seed = 7L)
        val inserted = original.cloneAndInsert(200, 37) as NoRepeatShuffleOrder
        val afterInsert = inserted.snapshotIndices()
        assertEquals(837, afterInsert.size)
        assertEquals(837, afterInsert.distinct().size)
        assertEquals((0 until 837).toSet(), afterInsert.toSet())

        val removed = inserted.cloneAndRemove(100, 250) as NoRepeatShuffleOrder
        val afterRemove = removed.snapshotIndices()
        assertEquals(687, afterRemove.size)
        assertEquals(687, afterRemove.distinct().size)
        assertEquals((0 until 687).toSet(), afterRemove.toSet())
    }

    @Test
    fun savedOrderRestoresExactlyAndFillsNewItemsWithoutDuplicates() {
        val original = NoRepeatShuffleOrder(100, seed = 15L)
        val saved = original.snapshotIndices()
        val restored = NoRepeatShuffleOrder.fromOrder(100, saved, seed = 99L)
        assertEquals(saved, restored.snapshotIndices())

        val expanded = NoRepeatShuffleOrder.fromOrder(104, saved, seed = 99L).snapshotIndices()
        assertEquals(104, expanded.size)
        assertEquals(104, expanded.distinct().size)
        assertTrue(expanded.take(100).containsAll(saved))
        assertTrue(saved != expanded)
    }
}
