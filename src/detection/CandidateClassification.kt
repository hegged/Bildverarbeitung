package detection

import java.awt.image.BufferedImage

/** TODO: Farbe, Form und Details zu einem Schildtyp oder unbekannt zusammenführen. */
fun classifyCandidates(candidatePreview: BufferedImage): ProcessingStage = ProcessingStage(
    listOf(StageImage("9. Klassifikation", candidatePreview)),
    "TODO: Schildtyp zuordnen oder als unbekannt markieren. Noch keine Klassifikation; Vorschau unverändert."
)
