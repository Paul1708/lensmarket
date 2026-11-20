package lensmarket.image.data.watermark

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import lensmarket.image.domain.port.InvalidImageContentException
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Tests fuer die Bildskalierung und Textplatzierung des [WatermarkService].
 */
class WatermarkServiceTest {

    @Test
    fun `undecodable bytes are rejected as typed invalid image content`() {
        val service = WatermarkService("© Test", 0.4f, maxDimension = 1600, outputQuality = 0.9f)
        assertThrows(InvalidImageContentException::class.java) {
            service.applyWatermarkBlocking("not-an-image".toByteArray(), "image/jpeg")
        }
    }

    /**
     * Prueft, dass das Wasserzeichen ungefaehr die konfigurierte Breite einnimmt.
     */
    @Test
    fun `watermark text spans roughly 80 percent of the stored image width`() {
        val service = WatermarkService("© Test", 0.4f, maxDimension = 1600, outputQuality = 0.9f)
        val background = Color(20, 40, 60)
        val source = solidJpeg(width = 1000, height = 600, color = background)

        val result = service.applyWatermarkBlocking(source, "image/jpeg")
        val stored = ImageIO.read(ByteArrayInputStream(result.bytes))

        val detectedWidth = widestDifferingSpan(stored, background)
        val ratio = detectedWidth.toDouble() / stored.width

        // Großzügige Toleranz: Buchstaben-Rundungen/Font-Fallbacks je nach Umgebung
        // verschieben die exakte Pixelbreite leicht, die Größenordnung muss aber stimmen.
        assertTrue(ratio in 0.6..0.95, "expected watermark to span ~80% of width, was ${"%.2f".format(ratio)}")
    }

    /**
     * Prueft, dass grosse Bilder vor dem Einbrennen des Wasserzeichens skaliert werden.
     */
    @Test
    fun `large images are downscaled before the watermark keeps its relative width`() {
        val service = WatermarkService("© Test", 0.4f, maxDimension = 1600, outputQuality = 0.9f)
        val background = Color(20, 40, 60)
        val source = solidJpeg(width = 3200, height = 1800, color = background)

        val result = service.applyWatermarkBlocking(source, "image/jpeg")
        val stored = ImageIO.read(ByteArrayInputStream(result.bytes))

        assertTrue(stored.width <= 1600, "stored copy should be capped at 1600px, was ${stored.width}")

        val detectedWidth = widestDifferingSpan(stored, background)
        val ratio = detectedWidth.toDouble() / stored.width
        assertTrue(ratio in 0.6..0.95, "expected watermark to span ~80% of the downscaled width, was ${"%.2f".format(ratio)}")
    }

    /**
     * Prueft, dass Wasserzeichenzeilen ueber die Bildhoehe gleichmaessig verteilt sind.
     */
    @Test
    fun `watermark is repeated vertically with evenly sized gaps`() {
        val service = WatermarkService("© Test", 0.4f, maxDimension = 1600, outputQuality = 0.9f)
        val background = Color(20, 40, 60)
        val source = solidJpeg(width = 1000, height = 1800, color = background)

        val result = service.applyWatermarkBlocking(source, "image/jpeg")
        val stored = ImageIO.read(ByteArrayInputStream(result.bytes))

        val bands = verticalWatermarkBands(stored, background)
        assertTrue(bands.size >= 2, "expected the watermark to repeat at least twice, found ${bands.size} band(s)")

        // Lücken zwischen aufeinanderfolgenden Zeilen (inkl. Rand zu Bildanfang/-ende)
        // müssen ungefähr gleich groß sein.
        val gaps = mutableListOf<Int>()
        gaps += bands.first().first
        for (i in 1 until bands.size) {
            gaps += bands[i].first - bands[i - 1].second
        }
        gaps += stored.height - bands.last().second

        val avgGap = gaps.average()
        gaps.forEach { gap ->
            assertTrue(
                kotlin.math.abs(gap - avgGap) <= avgGap * 0.5 + 5,
                "expected roughly equal gaps, got $gaps"
            )
        }
    }

    /**
     * Ermittelt die Zeilenbereiche zusammenhaengender Wasserzeichen-Baender.
     *
     * @param image Zu analysierendes Bild.
     * @param background Erwartete Hintergrundfarbe ohne Wasserzeichen.
     * @return Liste aus Start- und Endzeile je erkanntem Band.
     */
    private fun verticalWatermarkBands(image: BufferedImage, background: Color): List<Pair<Int, Int>> {
        val rowHasWatermark = (0 until image.height).map { y ->
            (0 until image.width).any { x -> colorDistance(Color(image.getRGB(x, y)), background) > 40 }
        }
        val bands = mutableListOf<Pair<Int, Int>>()
        var start = -1
        for (y in rowHasWatermark.indices) {
            if (rowHasWatermark[y] && start == -1) {
                start = y
            } else if (!rowHasWatermark[y] && start != -1) {
                bands += start to (y - 1)
                start = -1
            }
        }
        if (start != -1) bands += start to (rowHasWatermark.size - 1)
        return bands
    }

    /**
     * Erstellt ein einfarbiges JPEG-Testbild.
     *
     * @param width Gewuenschte Bildbreite.
     * @param height Gewuenschte Bildhoehe.
     * @param color Hintergrundfarbe des Bildes.
     * @return JPEG-Datei als Byte-Array.
     */
    private fun solidJpeg(width: Int, height: Int, color: Color): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    /**
     * Misst die breiteste zusammenhaengende Pixelspanne, die vom Hintergrund abweicht.
     *
     * @param image Zu analysierendes Bild.
     * @param background Erwartete Hintergrundfarbe ohne Wasserzeichen.
     * @return Breite der breitesten erkannten Wasserzeichen-Spanne.
     */
    private fun widestDifferingSpan(image: BufferedImage, background: Color): Int {
        var widest = 0
        for (y in 0 until image.height) {
            var first = -1
            var last = -1
            for (x in 0 until image.width) {
                val pixel = Color(image.getRGB(x, y))
                if (colorDistance(pixel, background) > 40) {
                    if (first == -1) first = x
                    last = x
                }
            }
            if (first != -1) widest = maxOf(widest, last - first + 1)
        }
        return widest
    }

    /**
     * Berechnet eine einfache Manhattan-Distanz zwischen zwei RGB-Farben.
     *
     * @param a Erste Farbe.
     * @param b Zweite Farbe.
     * @return Summe der absoluten RGB-Kanalunterschiede.
     */
    private fun colorDistance(a: Color, b: Color): Int =
        kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
}
