import display.ProcessingWindow
import java.io.File
import javax.swing.SwingUtilities

fun main() {
    val inputDirectory = File("src/data")
    val supportedExtensions = setOf("png", "jpg", "jpeg", "bmp", "gif")
    val imageFiles = if (inputDirectory.isDirectory) {
        inputDirectory.walkTopDown().filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .sortedBy { it.relativeTo(inputDirectory).path.lowercase() }.toList()
    } else emptyList()

    SwingUtilities.invokeLater {
        ProcessingWindow(inputDirectory, imageFiles).isVisible = true
    }
}
