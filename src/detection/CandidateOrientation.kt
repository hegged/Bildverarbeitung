package detection

import java.awt.image.BufferedImage

/** TODO: Kandidaten ausschneiden und mögliche Ausrichtungen prüfen. */
fun orientCandidates(candidatePreview: BufferedImage): ProcessingStage = ProcessingStage(
    listOf(StageImage("7. Ausrichtung der Kandidaten", candidatePreview)),
    "TODO: Kandidaten ausrichten und mehrdeutige Drehungen prüfen. Noch keine Ausrichtung; Vorschau unverändert."
)
