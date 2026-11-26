package detection

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.Executors

data class StageImage(val title: String, val image: BufferedImage)

data class ProcessingStage(
    val images: List<StageImage>,
    val message: String
)

/** Processes red and yellow in the same phase, then provides the finished phase to the timeline. */
fun processStages(original: BufferedImage, onStage: (ProcessingStage) -> Unit) {
    fun publish(message: String, vararg images: StageImage) {
        // Keep only small previews in the UI; the original-sized intermediate images can be freed.
        onStage(ProcessingStage(images.map { it.copy(image = thumbnail(it.image)) }, message))
    }

    publish("Originalbild. Die Farbfilterung beginnt mit Rot und Gelb gemeinsam.", StageImage("1. Original", original))

    val workers = Executors.newFixedThreadPool(2)
    try {
        fun <T> both(red: () -> T, yellow: () -> T): Pair<T, T> {
            val redTask = workers.submit<T> { red() }
            val yellowTask = workers.submit<T> { yellow() }
            try {
                return redTask.get() to yellowTask.get()
            } catch (exception: Exception) {
                redTask.cancel(true)
                yellowTask.cancel(true)
                throw exception
            }
        }

        val (redMask, yellowMask) = createColorMasks(original)
        publish(
            "HSV-Farbfilter: Rot 330–360°/0–20°, Gelb 30–60°. Weiß bedeutet Farb-Kandidat.",
            StageImage("2. Farbfilter · Rot", redMask),
            StageImage("2. Farbfilter · Gelb", yellowMask)
        )

        val (dilatedRed, dilatedYellow) = both(
            { dilate(redMask) },
            { dilate(yellowMask) }
        )
        publish(
            "Dilatation: Maximum im 3×3-Feld; weiße Bereiche wachsen.",
            StageImage("3. Dilatation · Rot", dilatedRed),
            StageImage("3. Dilatation · Gelb", dilatedYellow)
        )

        val (cleanedRed, cleanedYellow) = both(
            { erode(dilatedRed) },
            { erode(dilatedYellow) }
        )
        publish(
            "Erosion nach Dilatation (Closing): kleine schwarze Lücken schließen sich.",
            StageImage("4. Closing · Rot", cleanedRed),
            StageImage("4. Closing · Gelb", cleanedYellow)
        )

        val (redCandidates, yellowCandidates) = both(
            { findConnectedComponents(cleanedRed, CandidateColor.RED) },
            { findConnectedComponents(cleanedYellow, CandidateColor.YELLOW) }
        )
        val (redPreview, yellowPreview) = both(
            { drawCandidateBoxes(original, redCandidates) },
            { drawCandidateBoxes(original, yellowCandidates) }
        )
        publish(
            "8er-Nachbarschaft, mindestens 9 Pixel: ${redCandidates.size} rote und ${yellowCandidates.size} gelbe Bereiche.",
            StageImage("5. Bereiche · Rot", redPreview),
            StageImage("5. Bereiche · Gelb", yellowPreview)
        )

        val result = drawCandidateBoxes(original, redCandidates + yellowCandidates)
        publish(
            "Farbliche Kandidaten auf dem Original. Formprüfung, Details und Zuordnung zu den vier Schildtypen sind noch offen.",
            StageImage("6. Ergebnis · Kandidaten auf Original", result)
        )
    } finally {
        workers.shutdownNow()
    }
}

private fun thumbnail(image: BufferedImage): BufferedImage {
    val scale = minOf(1.0, minOf(1200.0 / image.width, 600.0 / image.height))
    val width = (image.width * scale).toInt().coerceAtLeast(1)
    val height = (image.height * scale).toInt().coerceAtLeast(1)
    val preview = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = preview.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(image, 0, 0, width, height, null)
    } finally {
        graphics.dispose()
    }
    return preview
}
