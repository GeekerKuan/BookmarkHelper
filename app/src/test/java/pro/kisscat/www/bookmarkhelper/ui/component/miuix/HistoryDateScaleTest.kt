package pro.kisscat.www.bookmarkhelper.ui.component.miuix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDateScaleTest {
    @Test
    fun anchorsKeepEqualPositionsAndExactDayValues() {
        HistoryDateScale.dayNodes.forEachIndexed { index, days ->
            assertEquals(days, HistoryDateScale.daysAt(index.toFloat()))
            assertEquals(index.toFloat(), HistoryDateScale.positionOf(days), 0.0001f)
        }
    }

    @Test
    fun positionsBetweenAnchorsAreLinearlyInterpolatedWithoutSnapping() {
        assertEquals(4, HistoryDateScale.daysAt(0.5f))
        assertEquals(19, HistoryDateScale.daysAt(1.5f))
        assertEquals(60, HistoryDateScale.daysAt(2.5f))
        assertEquals(135, HistoryDateScale.daysAt(3.5f))
        assertEquals(273, HistoryDateScale.daysAt(4.5f))
    }

    @Test
    fun everyIntegerDayRoundTripsToItsSegment() {
        for (days in 1..365) {
            val position = HistoryDateScale.positionOf(days)
            assertTrue(position in HistoryDateScale.positionRange)
            assertEquals(days, HistoryDateScale.daysAt(position))
        }
    }

    @Test
    fun outOfRangeInputsAreClamped() {
        assertEquals(1, HistoryDateScale.daysAt(-100f))
        assertEquals(365, HistoryDateScale.daysAt(100f))
        assertEquals(0f, HistoryDateScale.positionOf(-1), 0f)
        assertEquals(5f, HistoryDateScale.positionOf(999), 0f)
    }

    @Test
    fun hapticThresholdIsReportedOnceWhenAVisualNodeIsCrossed() {
        assertTrue(HistoryDateScale.crossedNode(0.9f, 1.1f))
        assertTrue(HistoryDateScale.crossedNode(1.1f, 0.9f))
        assertTrue(!HistoryDateScale.crossedNode(1f, 1.1f))
        assertTrue(!HistoryDateScale.crossedNode(1.1f, 1.2f))
    }

    @Test
    fun onlySmallNeighborhoodAroundNodeIsMagnetized() {
        assertEquals(1f, HistoryDateScale.magnetizedPosition(0.94f), 0f)
        assertEquals(1f, HistoryDateScale.magnetizedPosition(1.08f), 0f)
        assertEquals(1.081f, HistoryDateScale.magnetizedPosition(1.081f), 0f)
        assertEquals(1.4f, HistoryDateScale.magnetizedPosition(1.4f), 0f)
    }

    @Test
    fun labelsUseSliderEffectiveTrackCenters() {
        assertEquals(14f, HistoryDateScale.nodeCenterPx(0, 300f, 28f), 0f)
        assertEquals(286f, HistoryDateScale.nodeCenterPx(5, 300f, 28f), 0f)
        assertEquals(150f, HistoryDateScale.nodeCenterPx(2, 368f, 28f), 0f)
    }
}
