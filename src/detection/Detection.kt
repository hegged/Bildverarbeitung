package detection

import java.awt.image.BufferedImage

// Ein eigener Zustand pro Bild: Vorschau und Algorithmus-Eingaben bleiben getrennt.
class ProcessingContext(val originalImage: BufferedImage) {
    var redMask: BufferedImage? = null
    var yellowMask: BufferedImage? = null
    var dilatedRedMask: BufferedImage? = null
    var dilatedYellowMask: BufferedImage? = null
    var cleanedRedMask: BufferedImage? = null
    var cleanedYellowMask: BufferedImage? = null
    var candidates: List<ConnectedComponent> = emptyList()
}

data class StepResult(val image: BufferedImage, val message: String)

data class ProcessingStep(
    val name: String,
    val process: (context: ProcessingContext, previousImage: BufferedImage) -> StepResult
)

fun createProcessingSteps(): List<ProcessingStep> = listOf(
    ProcessingStep("Original") { context, _ ->
        StepResult(context.originalImage, "Ausgangsbild. Als Nächstes werden rote, danach gelbe Bereiche gesucht.")
    },
    ProcessingStep("Rotmaske") { context, _ ->
        val redMask = createRedMask(context.originalImage)
        context.redMask = redMask
        StepResult(redMask, "Rotmaske: Farbton 330–360° oder 0–20°, Sättigung mindestens 15 %, Helligkeit mindestens 12 %. Das sind Farb-Kandidaten, noch keine erkannten Schilder.")
    },
    ProcessingStep("Gelbmaske") { context, _ ->
        val yellowMask = createYellowMask(context.originalImage)
        context.yellowMask = yellowMask
        StepResult(yellowMask, "Gelbmaske: Farbton 30–60°, Sättigung mindestens 10 %, Helligkeit mindestens 15 %. Das sind Farb-Kandidaten, noch keine erkannten Schilder.")
    },
    ProcessingStep("Rotmaske: Dilatation") { context, _ ->
        val mask = requireNotNull(context.redMask) { "Zuerst die Rotmaske berechnen." }
        val dilated = dilate(mask)
        context.dilatedRedMask = dilated
        StepResult(dilated, "Rotmaske – Dilatation: Maximum im 3×3-Feld. Weiße Bereiche wachsen; kleine Lücken werden überbrückt. Weiter zeigt die anschließende Erosion.")
    },
    ProcessingStep("Rotmaske: Erosion nach Dilatation") { context, _ ->
        val dilated = requireNotNull(context.dilatedRedMask) { "Zuerst die Rotmaske dilatieren." }
        val cleaned = erode(dilated)
        context.cleanedRedMask = cleaned
        StepResult(cleaned, "Rotmaske – Erosion: Minimum im 3×3-Feld. Nach der Dilatation werden die Flächen wieder verkleinert. Diese Kombination heißt Closing: kleine schwarze Lücken schließen sich; weiße Störflecken können bleiben.")
    },
    ProcessingStep("Gelbmaske: Dilatation") { context, _ ->
        // Die gespeicherte Gelbmaske verwenden, nicht die zuletzt angezeigte Rotmaske.
        val mask = requireNotNull(context.yellowMask) { "Zuerst die Gelbmaske berechnen." }
        val dilated = dilate(mask)
        context.dilatedYellowMask = dilated
        StepResult(dilated, "Gelbmaske – Dilatation: Maximum im 3×3-Feld. Die ursprüngliche Gelbmaske wird unabhängig von der Rotmaske verarbeitet. Weiter zeigt die anschließende Erosion.")
    },
    ProcessingStep("Gelbmaske: Erosion nach Dilatation") { context, _ ->
        val dilated = requireNotNull(context.dilatedYellowMask) { "Zuerst die Gelbmaske dilatieren." }
        val cleaned = erode(dilated)
        context.cleanedYellowMask = cleaned
        StepResult(cleaned, "Gelbmaske – Erosion nach Dilatation (Closing). Beide bereinigten Farbmasken sind getrennt gespeichert. Kleine weiße Störflecken werden später anhand ihrer Fläche aussortiert; die Bereichssuche ist noch offen.")
    },
    ProcessingStep("Zusammenhängende Bereiche") { context, _ ->
        val redMask = requireNotNull(context.cleanedRedMask) { "Zuerst die rote Maske bereinigen." }
        val yellowMask = requireNotNull(context.cleanedYellowMask) { "Zuerst die gelbe Maske bereinigen." }
        val red = findConnectedComponents(redMask, CandidateColor.RED)
        val yellow = findConnectedComponents(yellowMask, CandidateColor.YELLOW)
        context.candidates = red + yellow
        val preview = drawCandidateBoxes(context.originalImage, context.candidates)
        StepResult(preview, "8er-Nachbarschaft: ${red.size} rote und ${yellow.size} gelbe Bereiche mit mindestens 9 Pixeln. Die Rahmen zeigen nur Farb-Kandidaten; Form und Schildtyp sind noch ungeprüft.")
    },
    ProcessingStep("Konturen und Form prüfen") { _, previousImage ->
        // HIER ERGÄNZEN: extractContours und measureShape für jeden Kandidaten.
        // Außenkontur vereinfachen; Ecken, Seitenverhältnis und Formähnlichkeit prüfen.
        // Bei Bedarf Graustufen/Glättung/Sobel auf Originalausschnitten ergänzen.
        StepResult(previousImage, "NOCH OFFEN: Dreiecke, Vierecke und achteckige Formen prüfen. Noch keine endgültige Schildklasse. Vorschau unverändert.")
    },
    ProcessingStep("Ausrichtung der Kandidaten") { _, previousImage ->
        // HIER ERGÄNZEN: Kandidaten aus dem Original ausschneiden.
        // normalizeCandidate: bei mehrdeutiger Form mehrere plausible Drehungen testen.
        // Originalkoordinaten behalten. Rotation korrigiert keine Perspektivverzerrung.
        StepResult(previousImage, "NOCH OFFEN: Ausschnitte und mögliche Ausrichtungen vorbereiten. Mehrdeutige Drehungen erst mit Details bewerten. Vorschau unverändert.")
    },
    ProcessingStep("Details und Schrift prüfen") { _, previousImage ->
        // HIER ERGÄNZEN: compareDetails, z. B. Referenzvergleich für STOP/Kreuzungssymbol.
        // Ausgerichtete Ausschnitte vergleichen und beste Orientierung bestimmen.
        // Ein rotes Dreieck allein identifiziert das Vorfahrt-Schild noch nicht.
        StepResult(previousImage, "NOCH OFFEN: Innere Symbole und gegebenenfalls STOP-Schrift prüfen. Vorschau unverändert.")
    },
    ProcessingStep("Klassifikation und Ergebnis") { context, _ ->
        // HIER ERGÄNZEN: classifyCandidate aus Farbe, Form und Detailmerkmalen.
        // Bei unzureichender Übereinstimmung unbekannt ausgeben.
        // Rahmen und deutsche Beschriftungen auf einer Kopie des Originals zeichnen.
        StepResult(context.originalImage, "NOCH OFFEN: Schildtyp zuordnen oder als unbekannt markieren. Noch keine Erkennung durchgeführt. Angezeigt wird das Original.")
    }
)
