package display

import detection.ProcessingContext
import detection.StepResult
import detection.createProcessingSteps
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
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

class ProcessingWindow(private val inputDirectory: File, private val imageFiles: List<File>) : JFrame("Bildverarbeitung – Schritt für Schritt") {
    private val steps = createProcessingSteps()
    private var imageIndex = -1
    private var stepIndex = -1
    private var originalImage: BufferedImage? = null
    private var previousImage: BufferedImage? = null
    private var processingContext: ProcessingContext? = null
    private var isBusy = false

    private val originalPanel = ImagePanel()
    private val processedPanel = ImagePanel()
    private val titleLabel = JLabel("Bilder werden vorbereitet …")
    private val statusText = JTextArea(3, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isFocusable = false
    }
    private val nextButton = JButton("Weiter (Leertaste)")
    private val nextImageButton = JButton("Nächstes Bild / überspringen")

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(700, 450)
        layout = BorderLayout(10, 10)
        (contentPane as JComponent).border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        add(titleLabel, BorderLayout.NORTH)
        val imageContainer = JPanel(GridLayout(1, 2, 12, 0))
        imageContainer.add(JPanel(BorderLayout()).apply {
            add(JLabel("Original"), BorderLayout.NORTH)
            add(originalPanel, BorderLayout.CENTER)
        })
        imageContainer.add(JPanel(BorderLayout()).apply {
            add(JLabel("Aktueller Verarbeitungsschritt"), BorderLayout.NORTH)
            add(processedPanel, BorderLayout.CENTER)
        })
        add(imageContainer, BorderLayout.CENTER)
        val bottomPanel = JPanel(BorderLayout(8, 8))
        bottomPanel.add(statusText, BorderLayout.CENTER)
        bottomPanel.add(JPanel().apply { add(nextButton); add(nextImageButton) }, BorderLayout.SOUTH)
        add(bottomPanel, BorderLayout.SOUTH)

        nextButton.addActionListener { advance() }
        nextImageButton.addActionListener { if (!isBusy) loadNextImage() }
        // Buttons nehmen keinen Tastaturfokus an: Leertaste löst immer genau den gleichen Ablauf aus.
        nextButton.isFocusable = false
        nextImageButton.isFocusable = false
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "advance")
        rootPane.actionMap.put("advance", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = advance()
        })
        size = Dimension(1400, 900)
        setLocationRelativeTo(null)
        extendedState = MAXIMIZED_BOTH

        if (imageFiles.isEmpty()) {
            titleLabel.text = "Keine Bilder gefunden"
            statusText.text = "Lege Bilder unter ${inputDirectory.absolutePath} ab. Unterstützt: PNG, JPG, JPEG, BMP, GIF. Arbeitsverzeichnis: Projektordner."
            nextButton.isEnabled = false
            nextImageButton.isEnabled = false
        } else loadNextImage()
    }

    private fun advance() {
        if (isBusy || imageFiles.isEmpty() || !nextButton.isEnabled) return
        if (originalImage == null || stepIndex == steps.lastIndex) loadNextImage()
        else showStep(stepIndex + 1)
    }

    private fun loadNextImage() {
        if (imageIndex >= imageFiles.lastIndex) return
        imageIndex++
        stepIndex = -1
        originalImage = null
        previousImage = null
        processingContext = null
        originalPanel.showImage(null)
        processedPanel.showImage(null)
        titleLabel.text = "Bild ${imageIndex + 1}/${imageFiles.size}: ${imageFiles[imageIndex].relativeTo(inputDirectory).path}"
        runInBackground("Bild wird geladen …", {
            val image = ImageIO.read(imageFiles[imageIndex])
                ?: error("Das Bildformat konnte nicht gelesen werden.")
            StepResult(image, "Original geladen.")
        }) { result ->
            originalImage = result.image
            processingContext = ProcessingContext(result.image)
            previousImage = result.image
            originalPanel.showImage(result.image)
            showStep(0)
        }
    }

    private fun showStep(index: Int) {
        val context = processingContext ?: return
        val inputImage = previousImage ?: return
        val step = steps[index]
        runInBackground("${step.name} wird berechnet …", { step.process(context, inputImage) }) { result ->
            stepIndex = index
            previousImage = result.image
            processedPanel.showImage(result.image)
            titleLabel.text = "Bild ${imageIndex + 1}/${imageFiles.size}: ${imageFiles[imageIndex].relativeTo(inputDirectory).path} | Schritt ${index + 1}/${steps.size}: ${step.name}"
            statusText.text = result.message
            updateButtons()
        }
    }

    // Bildberechnungen laufen außerhalb des UI-Threads, damit das Fenster nicht einfriert.
    private fun runInBackground(text: String, compute: () -> StepResult, onComplete: (StepResult) -> Unit) {
        isBusy = true
        nextButton.isEnabled = false
        nextImageButton.isEnabled = false
        statusText.text = text
        object : SwingWorker<StepResult, Unit>() {
            override fun doInBackground(): StepResult = compute()
            override fun done() {
                if (!isDisplayable) return
                isBusy = false
                try {
                    onComplete(get())
                } catch (exception: Exception) {
                    statusText.text = "Fehler: ${exception.cause?.message ?: exception.message}. Weiter versucht den Schritt erneut; mit 'Nächstes Bild' überspringst du dieses Bild."
                    updateButtons()
                }
            }
        }.execute()
    }

    private fun updateButtons() {
        val isLastImage = imageIndex == imageFiles.lastIndex
        val isLastStep = stepIndex == steps.lastIndex
        nextButton.text = when {
            originalImage == null -> if (isLastImage) "Keine weiteren Bilder" else "Weiter zum nächsten Bild"
            isLastStep -> if (isLastImage) "Durchlauf beendet" else "Weiter zum nächsten Bild"
            else -> "Weiter (Leertaste)"
        }
        nextButton.isEnabled = !(isLastImage && (isLastStep || originalImage == null))
        nextImageButton.isEnabled = !isLastImage
    }
}
