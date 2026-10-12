package org.cnag.clarid.pocket

import org.junit.Assert.*
import org.junit.Test

class ScanTargetTest {
    private val centre = ScanCandidate("chosen", ScanBounds(35f, 85f, 65f, 115f))
    private val neighbor = ScanCandidate("neighbor", ScanBounds(5f, 80f, 25f, 100f))

    @Test fun singleQrReadsImmediatelyAnywhereVisibleAndOnlyOnce() {
        val target = ScanTarget()
        assertEquals("neighbor", target.update(listOf(neighbor), 100f, 200f).value)
        assertNull(target.update(listOf(neighbor), 100f, 200f).value)
    }

    @Test fun multipleVisibleQrsUseCrosshairRegardlessOfDetectionOrder() {
        for (codes in listOf(listOf(neighbor, centre), listOf(centre, neighbor))) {
            assertEquals("chosen", ScanTarget().update(codes, 100f, 200f).value)
        }
    }

    @Test fun ambiguousCodesWaitWithoutSelectingArbitrarily() {
        val target = ScanTarget()
        assertTrue(target.update(listOf(centre, centre.copy(text = "other")), 100f, 200f).ambiguous)
        assertTrue(target.update(listOf(centre, centre), 100f, 200f).ambiguous)
        assertEquals("chosen", target.update(listOf(centre), 100f, 200f).value)
    }

    @Test fun largeQrDoesNotHaveToFitInsideTheGuide() {
        val large = centre.copy(bounds = ScanBounds(-5f, 30f, 105f, 170f))
        assertEquals("chosen", ScanTarget().update(listOf(large), 100f, 200f).value)
    }

    @Test fun invisibleOrNonTextCodesDoNotTriggerAndLandscapeWorks() {
        val target = ScanTarget()
        assertNull(target.update(listOf(centre.copy(text = null)), 100f, 200f).value)
        assertNull(target.update(listOf(centre.copy(bounds = ScanBounds(200f, 0f, 300f, 100f))), 100f, 200f).value)
        assertEquals("rotated", target.update(listOf(ScanCandidate("rotated",
            ScanBounds(85f, 35f, 115f, 65f))), 200f, 100f).value)
    }
}
