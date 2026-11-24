import detection.CandidateColor
import detection.drawCandidateBoxes
import detection.findConnectedComponents
import java.awt.Color
import java.awt.image.BufferedImage

fun main() {
    val mask = BufferedImage(7, 5, BufferedImage.TYPE_BYTE_BINARY)
    // Diagonal contact belongs to one component with 8-neighbour connectivity.
    mask.setRGB(0, 0, Color.WHITE.rgb)
    mask.setRGB(1, 1, Color.WHITE.rgb)
    mask.setRGB(6, 4, Color.WHITE.rgb)
    val all = findConnectedComponents(mask, CandidateColor.RED, minArea = 1)
    check(all.size == 2)
    check(all[0].x == 0 && all[0].y == 0 && all[0].width == 2 && all[0].height == 2 && all[0].area == 2)
    check(all[1].x == 6 && all[1].y == 4 && all[1].area == 1)
    check(findConnectedComponents(mask, CandidateColor.YELLOW, minArea = 2).single().color == CandidateColor.YELLOW)
    check(findConnectedComponents(mask, CandidateColor.RED).isEmpty())

    val original = BufferedImage(7, 5, BufferedImage.TYPE_INT_RGB)
    val preview = drawCandidateBoxes(original, all)
    check(preview !== original)
    check(preview.getRGB(0, 0) != original.getRGB(0, 0))
    check(original.getRGB(0, 0) == Color.BLACK.rgb)
    println("Connected components: OK")
}
