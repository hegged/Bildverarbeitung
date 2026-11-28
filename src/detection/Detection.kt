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

        var previousImage: BufferedImage? = null
        var previousThumbnail: BufferedImage? = null
        fun preview(image: BufferedImage): BufferedImage {
            if (image !== previousImage) {
                previousImage = image
                previousThumbnail = thumbnail(image)
            }
            return previousThumbnail!!
        }

        fun publish(message: String, vararg images: StageImage) {
            // The two colour previews are independent and can be scaled in parallel.
            val previews = if (images.size == 2) {
                val (red, yellow) = both(
                    { thumbnail(images[0].image) },
                    { thumbnail(images[1].image) }
                )
                listOf(images[0].copy(image = red), images[1].copy(image = yellow))
            } else {
                // TODO steps reuse the same candidate preview without scaling it again.
                images.map { it.copy(image = preview(it.image)) }
            }
            onStage(ProcessingStage(previews, message))
        }

        publish("Originalbild. Die Farbfilterung beginnt mit Rot und Gelb gemeinsam.", StageImage("1. Original", original))

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

        val minArea = minimumComponentArea(original.width, original.height)
        val (redCandidates, yellowCandidates) = both(
            { findConnectedComponents(cleanedRed, CandidateColor.RED, minArea) },
            { findConnectedComponents(cleanedYellow, CandidateColor.YELLOW, minArea) }
        )
        val (redPreview, yellowPreview) = both(
            { drawCandidateBoxes(original, redCandidates) },
            { drawCandidateBoxes(original, yellowCandidates) }
        )
        publish(
            "8er-Nachbarschaft, mindestens $minArea Pixel: ${redCandidates.size} rote und ${yellowCandidates.size} gelbe Bereiche.",
            StageImage("5. Bereiche · Rot", redPreview),
            StageImage("5. Bereiche · Gelb", yellowPreview)
        )

        val result = drawCandidateBoxes(original, redCandidates + yellowCandidates)
        for (stage in listOf(
            checkContourAndShape(result, redCandidates + yellowCandidates),
            orientCandidates(result),
            checkDetailsAndText(result),
            classifyCandidates(result)
        )) {
            publish(stage.message, *stage.images.toTypedArray())
        }
        publish(
            "Farbliche Kandidaten auf dem Original. Formhinweise in Schritt 6; Details und Zuordnung zu den vier Schildtypen sind noch offen.",
            StageImage("10. Ergebnis · Kandidaten auf Original", result)
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
