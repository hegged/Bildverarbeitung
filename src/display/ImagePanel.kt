package display

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.math.min
import kotlin.math.roundToInt

class ImagePanel(private var image: BufferedImage? = null) : JPanel() {
    init {
        preferredSize = Dimension(650, 700)
        background = Color.DARK_GRAY
    }

    fun showImage(newImage: BufferedImage?) {
        image = newImage
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val currentImage = image ?: return
        if (width <= 0 || height <= 0) return
        val scale = min(width.toDouble() / currentImage.width, height.toDouble() / currentImage.height)
        val scaledWidth = (currentImage.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (currentImage.height * scale).roundToInt().coerceAtLeast(1)
        val graphics = g.create() as Graphics2D
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(currentImage, (width - scaledWidth) / 2, (height - scaledHeight) / 2, scaledWidth, scaledHeight, null)
        } finally {
            graphics.dispose()
        }
    }
}
