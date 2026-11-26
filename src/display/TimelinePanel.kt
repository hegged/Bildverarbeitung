package display

import detection.ProcessingStage
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import kotlin.math.min
import kotlin.math.roundToInt

/** Shows one large phase and keeps every precomputed phase reachable on the timeline. */
class TimelinePanel(private val onSelectionChanged: (Int, ProcessingStage) -> Unit) : JPanel(BorderLayout(8, 12)) {
    private var stages: List<ProcessingStage> = emptyList()
    private var activeIndex = -1
    private val stageTitle = JLabel("Bild wird verarbeitet …")
    private val counter = JLabel("")
    private val imageArea = JPanel(GridLayout(1, 1, 12, 0))
    private val previousButton = JButton("‹").apply { toolTipText = "Vorheriger Schritt"; isEnabled = false }
    private val nextButton = JButton("›").apply { toolTipText = "Nächster Schritt"; isEnabled = false }
    private val track = TimelineTrack()
    private val thumbnails = mutableListOf<JButton>()

    val currentStageIndex: Int get() = activeIndex
    val stageCount: Int get() = stages.size
    val isAtLastStage: Boolean get() = stages.isNotEmpty() && activeIndex == stages.lastIndex

    init {
        add(JPanel(BorderLayout()).apply {
            add(stageTitle, BorderLayout.WEST)
            add(counter, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(JPanel(BorderLayout(8, 0)).apply {
            add(previousButton, BorderLayout.WEST)
            add(imageArea, BorderLayout.CENTER)
            add(nextButton, BorderLayout.EAST)
        }, BorderLayout.CENTER)
        add(JScrollPane(track).apply {
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(900, 178)
            horizontalScrollBar.unitIncrement = 24
        }, BorderLayout.SOUTH)

        previousButton.preferredSize = Dimension(48, 64)
        nextButton.preferredSize = Dimension(48, 64)
        previousButton.addActionListener { showPrevious() }
        nextButton.addActionListener { showNext() }
    }

    fun clear() {
        stages = emptyList()
        activeIndex = -1
        thumbnails.clear()
        track.removeAll()
        imageArea.removeAll()
        stageTitle.text = "Bild wird verarbeitet …"
        counter.text = ""
        previousButton.isEnabled = false
        nextButton.isEnabled = false
        revalidate()
        repaint()
    }

    fun setStages(readyStages: List<ProcessingStage>) {
        require(readyStages.isNotEmpty())
        clear()
        stages = readyStages.toList()
        stages.forEachIndexed { index, stage ->
            val title = stage.images.first().title.substringBefore(" ·")
            val button = JButton(title, ImageIcon(thumbnailIcon(stage))).apply {
                horizontalTextPosition = SwingConstants.CENTER
                verticalTextPosition = SwingConstants.BOTTOM
                preferredSize = Dimension(148, 116)
                toolTipText = stage.message
                addActionListener { showStage(index) }
            }
            thumbnails += button
            track.add(button)
        }
        showStage(0)
    }

    fun showPrevious() = showStage(activeIndex - 1)
    fun showNext() = showStage(activeIndex + 1)

    fun showStage(index: Int) {
        if (index !in stages.indices) return
        activeIndex = index
        val stage = stages[index]
        stageTitle.text = stage.images.first().title.substringBefore(" ·")
        counter.text = "Schritt ${index + 1} von ${stages.size}"
        previousButton.isEnabled = index > 0
        nextButton.isEnabled = index < stages.lastIndex
        imageArea.removeAll()
        imageArea.layout = GridLayout(1, stage.images.size, 12, 0)
        stage.images.forEach { item ->
            imageArea.add(JPanel(BorderLayout(0, 4)).apply {
                if (stage.images.size == 2) {
                    add(JLabel(item.title.substringAfter(" · ")), BorderLayout.NORTH)
                }
                add(ImagePanel().apply { showImage(item.image) }, BorderLayout.CENTER)
            })
        }
        thumbnails.forEachIndexed { buttonIndex, button ->
            button.border = if (buttonIndex == index) {
                BorderFactory.createLineBorder(Color(72, 143, 223), 3)
            } else {
                BorderFactory.createLineBorder(Color.GRAY, 1)
            }
        }
        imageArea.revalidate()
        imageArea.repaint()
        track.repaint()
        onSelectionChanged(index, stage)
    }
}

private class TimelineTrack : JPanel(FlowLayout(FlowLayout.LEFT, 10, 26)) {
    init {
        preferredSize = Dimension(6 * 148 + 7 * 10, 148)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics.create() as Graphics2D
        try {
            g.color = Color.GRAY
            g.stroke = BasicStroke(2f)
            g.drawLine(20, 13, width - 20, 13)
            for (button in components) {
                val center = button.x + button.width / 2
                g.drawLine(center, 13, center, button.y)
                g.fillOval(center - 4, 9, 8, 8)
            }
        } finally {
            g.dispose()
        }
    }
}

private fun thumbnailIcon(stage: ProcessingStage): BufferedImage {
    val width = 136
    val height = 72
    val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = result.createGraphics()
    try {
        graphics.color = Color.DARK_GRAY
        graphics.fillRect(0, 0, width, height)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val tileWidth = width / stage.images.size
        stage.images.forEachIndexed { index, item ->
            val image = item.image
            val scale = min(tileWidth.toDouble() / image.width, height.toDouble() / image.height)
            val drawWidth = (image.width * scale).roundToInt().coerceAtLeast(1)
            val drawHeight = (image.height * scale).roundToInt().coerceAtLeast(1)
            graphics.drawImage(
                image,
                index * tileWidth + (tileWidth - drawWidth) / 2,
                (height - drawHeight) / 2,
                drawWidth,
                drawHeight,
                null
            )
        }
    } finally {
        graphics.dispose()
    }
    return result
}
