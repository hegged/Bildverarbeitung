package display

import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import javax.imageio.ImageIO

/** Reads the stored pixels and applies the JPEG EXIF orientation exactly once. */
fun loadImage(file: File): BufferedImage {
    val orientation = if (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true)) {
        jpegOrientation(file)
    } else 1
    val image = ImageIO.read(file) ?: error("Das Bildformat konnte nicht gelesen werden.")
    if (orientation == 1) return image

    val width = image.width
    val height = image.height
    val swapped = orientation in 5..8
    val result = BufferedImage(
        if (swapped) height else width,
        if (swapped) width else height,
        if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
    )
    val row = IntArray(width)
    for (y in 0 until height) {
        image.getRGB(0, y, width, 1, row, 0, width)
        for (x in 0 until width) {
            val target = when (orientation) {
                2 -> width - 1 - x to y
                3 -> width - 1 - x to height - 1 - y
                4 -> x to height - 1 - y
                5 -> y to x
                6 -> height - 1 - y to x
                7 -> height - 1 - y to width - 1 - x
                8 -> y to width - 1 - x
                else -> x to y
            }
            result.setRGB(target.first, target.second, row[x])
        }
    }
    return result
}

/** Returns 1 when no valid EXIF orientation is present. JPEG segments end at SOS. */
private fun jpegOrientation(file: File): Int {
    return try {
    DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
        if (input.readUnsignedShort() != 0xffd8) return 1
        while (true) {
            if (input.readUnsignedByte() != 0xff) return 1
            var marker = input.readUnsignedByte()
            while (marker == 0xff) marker = input.readUnsignedByte()
            if (marker == 0xda || marker == 0xd9) return 1
            if (marker == 0x00 || marker in 0xd0..0xd7 || marker == 0x01) continue
            val length = input.readUnsignedShort() - 2
            if (length < 0) return 1
            if (marker == 0xe1) {
                val segment = ByteArray(length)
                input.readFully(segment)
                parseExifOrientation(segment)?.let { return it }
            } else {
                var remaining = length
                while (remaining > 0) {
                    val skipped = input.skipBytes(remaining)
                    if (skipped == 0) return 1
                    remaining -= skipped
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        1
    }
    } catch (_: java.io.IOException) {
        1
    }
}

private fun parseExifOrientation(bytes: ByteArray): Int? {
    if (bytes.size < 14 || !bytes.copyOfRange(0, 6).contentEquals(byteArrayOf(69, 120, 105, 102, 0, 0))) return null
    val littleEndian = when {
        bytes[6] == 73.toByte() && bytes[7] == 73.toByte() -> true
        bytes[6] == 77.toByte() && bytes[7] == 77.toByte() -> false
        else -> return null
    }
    fun word(offset: Int): Int? {
        if (offset < 0 || offset > bytes.size - 2) return null
        val a = bytes[offset].toInt() and 0xff
        val b = bytes[offset + 1].toInt() and 0xff
        return if (littleEndian) a or (b shl 8) else (a shl 8) or b
    }
    fun dword(offset: Int): Long? {
        val a = word(offset)?.toLong() ?: return null
        val b = word(offset + 2)?.toLong() ?: return null
        return if (littleEndian) a or (b shl 16) else (a shl 16) or b
    }
    if (word(8) != 42) return null
    val ifd = (dword(10) ?: return null) + 6
    if (ifd > bytes.size - 2 || ifd < 0) return null
    val count = word(ifd.toInt()) ?: return null
    for (index in 0 until count) {
        val entry = ifd + 2 + index.toLong() * 12
        if (entry > bytes.size - 12) break
        val offset = entry.toInt()
        if (word(offset) == 0x0112 && word(offset + 2) == 3 && dword(offset + 4) == 1L) {
            return word(offset + 8)?.takeIf { it in 1..8 }
        }
    }
    return null
}
