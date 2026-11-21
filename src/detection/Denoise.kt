package detection

import java.awt.Color
import java.awt.image.BufferedImage

// Für Binärmasken: Schwarz = 0, Weiß = 255.
// Strukturelement: vollständiges 3×3-Feld einschließlich des Bezugspunkts.
fun dilate(mask: BufferedImage): BufferedImage = applyRankFilter(mask, useMaximum = true)

fun erode(mask: BufferedImage): BufferedImage = applyRankFilter(mask, useMaximum = false)

// Schließt kleine schwarze Lücken in weißen Flächen.
fun closeMask(mask: BufferedImage): BufferedImage = erode(dilate(mask))

// Entfernt kleine weiße Flecken; kann auch dünne Schildränder entfernen.
fun openMask(mask: BufferedImage): BufferedImage = dilate(erode(mask))

private fun applyRankFilter(mask: BufferedImage, useMaximum: Boolean): BufferedImage {
    val width = mask.width
    val height = mask.height
    val result = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
    // Nur aus der Eingabe lesen, damit das Ergebnis nicht von der Laufrichtung abhängt.
    val pixels = mask.getRGB(0, 0, width, height, null, 0, width)

    for (y in 0 until height) {
        for (x in 0 until width) {
            var selectedValue = if (useMaximum) 0 else 255

            // Am Bildrand nur vorhandene Nachbarn berücksichtigen.
            // So wird ein weißer Rand bei der Erosion nicht künstlich schwarz.
            for (neighborY in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
                for (neighborX in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
                    val neighborValue = pixels[neighborY * width + neighborX] and 0xff
                    selectedValue = if (useMaximum) {
                        maxOf(selectedValue, neighborValue)
                    } else {
                        minOf(selectedValue, neighborValue)
                    }
                }
            }

            result.setRGB(x, y, if (selectedValue == 255) Color.WHITE.rgb else Color.BLACK.rgb)
        }
    }
    return result
}
