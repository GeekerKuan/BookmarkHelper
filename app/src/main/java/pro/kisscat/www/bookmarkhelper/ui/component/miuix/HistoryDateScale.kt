package pro.kisscat.www.bookmarkhelper.ui.component.miuix

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Maps six equally-spaced visual anchors to a continuous history-retention range.
 * Positions between two anchors are linearly interpolated and rounded to whole days.
 */
object HistoryDateScale {
    /** Radius in the 0..5 position space; about 1.6% of the full track. */
    const val NODE_MAGNET_RADIUS = 0.08f
    val dayNodes: List<Int> = listOf(1, 7, 30, 90, 180, 365)
    val positionRange: ClosedFloatingPointRange<Float> = 0f..dayNodes.lastIndex.toFloat()
    val keyPoints: List<Float> = dayNodes.indices.map(Int::toFloat)

    fun daysAt(position: Float): Int {
        val bounded = position.coerceIn(positionRange.start, positionRange.endInclusive)
        val lowerIndex = floor(bounded).toInt().coerceIn(0, dayNodes.lastIndex)
        if (lowerIndex == dayNodes.lastIndex) return dayNodes.last()

        val fraction = bounded - lowerIndex
        val lower = dayNodes[lowerIndex]
        val upper = dayNodes[lowerIndex + 1]
        return (lower + (upper - lower) * fraction).roundToInt().coerceIn(lower, upper)
    }

    fun positionOf(days: Int): Float {
        val bounded = days.coerceIn(dayNodes.first(), dayNodes.last())
        val lowerIndex = dayNodes.indexOfLast { it <= bounded }.coerceIn(0, dayNodes.lastIndex)
        if (lowerIndex == dayNodes.lastIndex) return positionRange.endInclusive

        val lower = dayNodes[lowerIndex]
        val upper = dayNodes[lowerIndex + 1]
        return lowerIndex + (bounded - lower).toFloat() / (upper - lower).toFloat()
    }

    fun magnetizedPosition(position: Float, radius: Float = NODE_MAGNET_RADIUS): Float {
        val bounded = position.coerceIn(positionRange.start, positionRange.endInclusive)
        val nearest = keyPoints.minByOrNull { kotlin.math.abs(it - bounded) } ?: return bounded
        return if (kotlin.math.abs(nearest - bounded) <= radius + 0.000_001f) nearest else bounded
    }

    fun nodeCenterPx(nodeIndex: Int, layoutWidthPx: Float, sliderHeightPx: Float): Float {
        val thumbRadius = sliderHeightPx / 2f
        val effectiveWidth = (layoutWidthPx - sliderHeightPx).coerceAtLeast(0f)
        val fraction = nodeIndex.coerceIn(0, dayNodes.lastIndex).toFloat() / dayNodes.lastIndex
        return thumbRadius + effectiveWidth * fraction
    }

    fun crossedNode(previousPosition: Float, currentPosition: Float): Boolean {
        if (previousPosition == currentPosition) return false
        return keyPoints.any { node ->
            if (currentPosition > previousPosition) {
                node > previousPosition && node <= currentPosition
            } else {
                node < previousPosition && node >= currentPosition
            }
        }
    }
}
