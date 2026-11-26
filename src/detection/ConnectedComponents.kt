package detection

import java.awt.Color
import java.awt.image.BufferedImage

enum class CandidateColor { RED, YELLOW }

// Bounding box coordinates refer to the original image, including its top-left pixel.
data class ConnectedComponent(
    val color: CandidateColor,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val area: Int
)

/** Groups white pixels in a binary mask using 8-neighbour connectivity. */
fun findConnectedComponents(
    mask: BufferedImage,
    color: CandidateColor,
    minArea: Int = 9
): List<ConnectedComponent> {
    require(minArea > 0) { "minArea must be positive" }
    val width = mask.width
    val height = mask.height
    val pixelCount = Math.multiplyExact(width, height)
    val pixels = mask.getRGB(0, 0, width, height, null, 0, width)
    val visited = BooleanArray(pixelCount)
    val queue = IntArray(pixelCount)
    val components = mutableListOf<ConnectedComponent>()

    for (start in pixels.indices) {
        if (visited[start] || (pixels[start] and 0x00ffffff) != 0x00ffffff) continue
        visited[start] = true
        var head = 0
        var tail = 0
        queue[tail++] = start
        var left = start % width
        var right = left
        var top = start / width
        var bottom = top

        while (head < tail) {
            val current = queue[head++]
            val x = current % width
            val y = current / width
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)

            for (nextY in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
                for (nextX in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
                    val next = nextY * width + nextX
                    if (!visited[next] && (pixels[next] and 0x00ffffff) == 0x00ffffff) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
            }
        }

        if (tail >= minArea) {
            components += ConnectedComponent(color, left, top, right - left + 1, bottom - top + 1, tail)
        }
    }
    return components
}

/** Draws candidate boxes on a copy; the source image stays available for later steps. */
fun drawCandidateBoxes(original: BufferedImage, candidates: List<ConnectedComponent>): BufferedImage {
    val result = BufferedImage(original.width, original.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = result.createGraphics()
    try {
        graphics.drawImage(original, 0, 0, null)
        // Die Vorschau wird oft stark verkleinert. Eine Bildpixel-Linie wäre dann kaum sichtbar.
        val preferredThickness = maxOf(2, minOf(original.width, original.height) / 150)
        for (candidate in candidates) {
            graphics.color = when (candidate.color) {
                CandidateColor.RED -> Color.RED
                CandidateColor.YELLOW -> Color.YELLOW
            }
            // Alle vier Streifen liegen innerhalb der Bounding Box, auch direkt am Bildrand.
            val thickness = minOf(preferredThickness, candidate.width, candidate.height)
            val x = candidate.x
            val y = candidate.y
            val width = candidate.width
            val height = candidate.height
            graphics.fillRect(x, y, width, thickness)
            graphics.fillRect(x, y + height - thickness, width, thickness)
            graphics.fillRect(x, y, thickness, height)
            graphics.fillRect(x + width - thickness, y, thickness, height)
        }
    } finally {
        graphics.dispose()
    }
    return result
}
