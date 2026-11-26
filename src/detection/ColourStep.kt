package detection

import java.awt.Color
import java.awt.image.BufferedImage

private data class MaskCriteria(
    val minimumHue: Double,
    val maximumHue: Double,
    val minimumSaturation: Double,
    val minimumValue: Double,
    val wrapsHue: Boolean
) {
    fun matches(hue: Double, saturation: Double, value: Double): Boolean {
        val matchesHue = if (wrapsHue) {
            hue <= minimumHue || hue >= maximumHue
        } else {
            hue in minimumHue..maximumHue
        }
        return matchesHue && saturation >= minimumSaturation && value >= minimumValue
    }
}

fun createRedMask(
    originalImage: BufferedImage,
    minimumHue: Double = 20.0,
    maximumHue: Double = 330.0,
    minimumSaturation: Double = 0.15,
    minimumValue: Double = 0.12
): BufferedImage = createMasks(
    originalImage,
    listOf(MaskCriteria(minimumHue, maximumHue, minimumSaturation, minimumValue, wrapsHue = true))
).single()

fun createYellowMask(
    originalImage: BufferedImage,
    minimumHue: Double = 30.0,
    maximumHue: Double = 60.0,
    minimumSaturation: Double = 0.10,
    minimumValue: Double = 0.15
): BufferedImage = createMasks(
    originalImage,
    listOf(MaskCriteria(minimumHue, maximumHue, minimumSaturation, minimumValue, wrapsHue = false))
).single()

fun createColorMasks(originalImage: BufferedImage): Pair<BufferedImage, BufferedImage> {
    val masks = createMasks(
        originalImage,
        listOf(
            MaskCriteria(20.0, 330.0, 0.15, 0.12, wrapsHue = true),
            MaskCriteria(30.0, 60.0, 0.10, 0.15, wrapsHue = false)
        )
    )
    return masks[0] to masks[1]
}

private fun createMasks(originalImage: BufferedImage, criteria: List<MaskCriteria>): List<BufferedImage> {
    val masks = criteria.map {
        BufferedImage(originalImage.width, originalImage.height, BufferedImage.TYPE_BYTE_BINARY)
    }
    val hsv = FloatArray(3)

    for (y in 0 until originalImage.height) {
        for (x in 0 until originalImage.width) {
            val argb = originalImage.getRGB(x, y)
            if (argb ushr 24 == 0) continue

            Color.RGBtoHSB(argb ushr 16 and 0xff, argb ushr 8 and 0xff, argb and 0xff, hsv)
            val hue = (hsv[0] * 360.0f).toDouble()
            criteria.forEachIndexed { index, filter ->
                if (filter.matches(hue, hsv[1].toDouble(), hsv[2].toDouble())) {
                    masks[index].setRGB(x, y, Color.WHITE.rgb)
                }
            }
        }
    }
    return masks
}
