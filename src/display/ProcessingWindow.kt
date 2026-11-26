package display

import detection.ProcessingStage
import detection.processStages
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingWorker

class ProcessingWindow(private val inputDirectory: File, private val imageFiles: List<File>) : JFrame("Bildverarbeitung – Verarbeitungsschritte") {
    private var imageIndex = -1
    private var isBusy = false
    private val titleLabel = JLabel("Bilder werden vorbereitet …")
    private val statusText = JTextArea(2, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isFocusable = false
    }
    private val nextImageButton = JButton("Nächstes Bild").apply { isVisible = false }
    private val timeline = TimelinePanel { _, stage ->
        statusText.text = stage.message
        updateNextImageButton()
    }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(700, 450)
        layout = BorderLayout(10, 10)
        (contentPane as JComponent).border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        add(titleLabel, BorderLayout.NORTH)
        add(timeline, BorderLayout.CENTER)
        add(JPanel(BorderLayout(8, 8)).apply {
            add(statusText, BorderLayout.CENTER)
            add(nextImageButton, BorderLayout.EAST)
        }, BorderLayout.SOUTH)

        nextImageButton.addActionListener { loadNextImage() }
        nextImageButton.isFocusable = false
        registerKey(KeyEvent.VK_LEFT, "previousStep") { timeline.showPrevious() }
        registerKey(KeyEvent.VK_RIGHT, "nextStep") { timeline.showNext() }
        registerKey(KeyEvent.VK_SPACE, "advance") {
            if (timeline.isAtLastStage) loadNextImage() else timeline.showNext()
        }
        size = Dimension(1400, 900)
        setLocationRelativeTo(null)
        extendedState = MAXIMIZED_BOTH

        if (imageFiles.isEmpty()) {
            titleLabel.text = "Keine Bilder gefunden"
            statusText.text = "Lege Bilder unter ${inputDirectory.absolutePath} ab. Unterstützt: PNG, JPG, JPEG, BMP, GIF. Arbeitsverzeichnis: Projektordner."
        } else loadNextImage()
    }

    private fun registerKey(keyCode: Int, actionName: String, action: () -> Unit) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(keyCode, 0, true), actionName)
        rootPane.actionMap.put(actionName, object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) {
                if (!isBusy) action()
            }
        })
    }

    private fun updateNextImageButton() {
        val atResult = timeline.isAtLastStage
        nextImageButton.isVisible = atResult
        nextImageButton.text = if (imageIndex == imageFiles.lastIndex) "Letztes Bild" else "Nächstes Bild"
        nextImageButton.isEnabled = !isBusy && atResult && imageIndex < imageFiles.lastIndex
    }

    private fun loadNextImage() {
        if (isBusy || imageIndex >= imageFiles.lastIndex) return
        imageIndex++
        isBusy = true
        timeline.clear()
        nextImageButton.isVisible = false
        val file = imageFiles[imageIndex]
        val imageName = file.relativeTo(inputDirectory).path
        titleLabel.text = "Bild ${imageIndex + 1}/${imageFiles.size}: $imageName"
        statusText.text = "Alle Verarbeitungsschritte werden berechnet …"

        // Erst nach der vollständigen Berechnung erscheint der Zeitstrahl mit allen Schritten.
        object : SwingWorker<List<ProcessingStage>, Unit>() {
            override fun doInBackground(): List<ProcessingStage> {
                val original = ImageIO.read(file) ?: error("Das Bildformat konnte nicht gelesen werden.")
                return buildList { processStages(original) { add(it) } }
            }

            override fun done() {
                isBusy = false
                try {
                    timeline.setStages(get())
                } catch (exception: Exception) {
                    val cause = exception.cause?.cause ?: exception.cause ?: exception
                    statusText.text = "Fehler bei $imageName: ${cause.message}."
                    nextImageButton.isVisible = imageIndex < imageFiles.lastIndex
                    nextImageButton.isEnabled = imageIndex < imageFiles.lastIndex
                }
            }
        }.execute()
    }
}
