package detection

import java.awt.image.BufferedImage

/** TODO: Innere Symbole und gegebenenfalls STOP-Schrift vergleichen. */
fun checkDetailsAndText(candidatePreview: BufferedImage): ProcessingStage = ProcessingStage(
    listOf(StageImage("8. Details und Schrift prüfen", candidatePreview)),
    "TODO: Innere Symbole und gegebenenfalls STOP-Schrift prüfen. Noch kein Detailvergleich; Vorschau unverändert."
)
