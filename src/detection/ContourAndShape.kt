package detection

import java.awt.image.BufferedImage

/** TODO: Konturen extrahieren und Dreiecke, Vierecke sowie Achtecke prüfen. */
fun checkContourAndShape(candidatePreview: BufferedImage): ProcessingStage = ProcessingStage(
    listOf(StageImage("6. Konturen und Form prüfen", candidatePreview)),
    "TODO: Konturen und Form der Kandidaten prüfen. Noch keine Formprüfung; Vorschau unverändert."
)
