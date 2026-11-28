package detection

import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.hypot

enum class ShapeType(val label: String) {
    TRIANGLE("Dreieck"), QUADRILATERAL("Viereck"), OCTAGON("Achteck"), UNKNOWN("unbestimmt")
}

data class ShapeAnalysis(val candidate: ConnectedComponent, val shape: ShapeType, val corners: List<PixelPoint>)

/** Uses the outer convex hull of the connected colour region as a first shape hint. */
fun analyzeShape(candidate: ConnectedComponent): ShapeAnalysis {
    val hull = convexHull(candidate.contour)
    if (hull.size < 3 || candidate.width < 8 || candidate.height < 8) {
        return ShapeAnalysis(candidate, ShapeType.UNKNOWN, hull)
    }
    val tolerance = maxOf(candidate.width, candidate.height) * 0.07
    val corners = simplifyConvexPolygon(hull, tolerance)
    val shape = when (corners.size) {
        3 -> ShapeType.TRIANGLE
        4 -> ShapeType.QUADRILATERAL
        in 7..9 -> ShapeType.OCTAGON
        else -> ShapeType.UNKNOWN
    }
    return ShapeAnalysis(candidate, shape, corners)
}

fun checkContourAndShape(candidatePreview: BufferedImage, candidates: List<ConnectedComponent>): ProcessingStage {
    val analyses = candidates.map(::analyzeShape)
    val result = BufferedImage(candidatePreview.width, candidatePreview.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = result.createGraphics()
    try {
        graphics.drawImage(candidatePreview, 0, 0, null)
        graphics.stroke = BasicStroke(maxOf(2, minOf(result.width, result.height) / 200).toFloat())
        for (analysis in analyses) {
            val polygon = analysis.corners
            if (polygon.size < 3) continue
            graphics.color = if (analysis.shape == ShapeType.UNKNOWN) Color.LIGHT_GRAY else Color.CYAN
            graphics.drawPolygon(polygon.map { it.x }.toIntArray(), polygon.map { it.y }.toIntArray(), polygon.size)
            graphics.drawString(analysis.shape.label, analysis.candidate.x, maxOf(12, analysis.candidate.y - 5))
        }
    } finally {
        graphics.dispose()
    }
    val triangles = analyses.count { it.shape == ShapeType.TRIANGLE }
    val quadrilaterals = analyses.count { it.shape == ShapeType.QUADRILATERAL }
    val octagons = analyses.count { it.shape == ShapeType.OCTAGON }
    return ProcessingStage(
        listOf(StageImage("6. Konturen und Form prüfen", result)),
        "Außenkonturen vereinfacht: $triangles Dreiecke, $quadrilaterals Vierecke, $octagons Achtecke; " +
            "${analyses.size - triangles - quadrilaterals - octagons} unbestimmt. Formhinweise sind noch keine Schildklassifikation."
    )
}

private fun convexHull(points: List<PixelPoint>): List<PixelPoint> {
    val sorted = points.distinct().sortedWith(compareBy<PixelPoint> { it.x }.thenBy { it.y })
    if (sorted.size <= 2) return sorted
    fun cross(a: PixelPoint, b: PixelPoint, c: PixelPoint): Long =
        (b.x - a.x).toLong() * (c.y - a.y) - (b.y - a.y).toLong() * (c.x - a.x)
    val lower = ArrayList<PixelPoint>()
    for (point in sorted) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), point) <= 0) lower.removeAt(lower.lastIndex)
        lower += point
    }
    val upper = ArrayList<PixelPoint>()
    for (point in sorted.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), point) <= 0) upper.removeAt(upper.lastIndex)
        upper += point
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

private fun simplifyConvexPolygon(hull: List<PixelPoint>, tolerance: Double): List<PixelPoint> {
    val corners = hull.toMutableList()
    while (corners.size > 3) {
        var closestIndex = -1
        var closestDistance = Double.POSITIVE_INFINITY
        for (index in corners.indices) {
            val previous = corners[(index - 1 + corners.size) % corners.size]
            val current = corners[index]
            val next = corners[(index + 1) % corners.size]
            val length = hypot((next.x - previous.x).toDouble(), (next.y - previous.y).toDouble())
            if (length == 0.0) continue
            val distance = kotlin.math.abs(
                (next.x - previous.x).toDouble() * (current.y - previous.y) -
                    (next.y - previous.y).toDouble() * (current.x - previous.x)
            ) / length
            if (distance < closestDistance) {
                closestDistance = distance
                closestIndex = index
            }
        }
        if (closestDistance > tolerance || closestIndex < 0) break
        corners.removeAt(closestIndex)
    }
    return corners
}
