package lensmarket.image.data.watermark

import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.mutiny.core.Vertx
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import lensmarket.image.domain.port.WatermarkPort
import lensmarket.image.domain.port.WatermarkResult
import lensmarket.image.domain.port.InvalidImageContentException
import net.coobird.thumbnailator.Thumbnails
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.util.concurrent.Callable

/**
 * Ergebnis der Wasserzeichen-Erzeugung.
 *
 * Enthält neben den fertigen Bildbytes auch die Originalmaße, damit Aufrufer diese ohne
 * ein zweites Decodieren des Bildes in die Metadaten übernehmen können.
 *
 * @param bytes Bildbytes mit eingebranntem Wasserzeichen.
 * @param width Breite des Originalbildes in Pixeln.
 * @param height Höhe des Originalbildes in Pixeln.
 */
/**
 * Erzeugt Wasserzeichenbilder aus hochgeladenen Originaldateien.
 *
 * Der Dienst liest Bildbytes, zeichnet einen konfigurierbaren Text in das Bild und
 * schreibt das Ergebnis wieder im passenden Bildformat.
 *
 * @param watermarkText Text, der als Wasserzeichen eingezeichnet wird.
 * @param opacity Deckkraft des Wasserzeichens.
 */
@ApplicationScoped
class WatermarkService(
    @param:ConfigProperty(name = "lensmarket.watermark.text", defaultValue = "© LensMarket")
    private val watermarkText: String,
    @param:ConfigProperty(name = "lensmarket.watermark.opacity", defaultValue = "0.4")
    private val opacity: Float,
    @param:ConfigProperty(name = "lensmarket.watermark.max-dimension", defaultValue = "1600")
    private val maxDimension: Int,
    @param:ConfigProperty(name = "lensmarket.watermark.output-quality", defaultValue = "0.8")
    private val outputQuality: Float
) : WatermarkPort {
    @Inject
    lateinit var vertx: Vertx

    /**
     * Verkleinert bei Bedarf und brennt anschließend ein Text-Wasserzeichen ein.
     *
     * Das Original wird zuerst auf maximal `maxDimension` Pixel Kantenlänge herunterskaliert
     * (nie hochskaliert), damit große Originale (z.B. Handyfotos) im Object Storage nicht
     * unnötig viel Platz belegen. Das Wasserzeichen wird erst danach auf das bereits
     * verkleinerte Bild gezeichnet, damit die Textbreite sich auf die tatsächlich
     * gespeicherte Bildgröße bezieht statt auf die Originalauflösung.
     *
     * @param imageBytes Originalbild als Byte-Array.
     * @param mimeType MIME-Type des Originalbildes.
     * @return Wasserzeichenbytes zusammen mit den Originalmaßen (nicht den skalierten Maßen).
     */
    override suspend fun applyWatermark(imageBytes: ByteArray, mimeType: String): WatermarkResult =
        vertx.executeBlocking(Callable { applyWatermarkBlocking(imageBytes, mimeType) }).awaitSuspending()

    internal fun applyWatermarkBlocking(imageBytes: ByteArray, mimeType: String): WatermarkResult {
        val format = if (mimeType == "image/png") "png" else "jpeg"
        val source = try {
            ImageIO.read(ByteArrayInputStream(imageBytes))
        } catch (exception: java.io.IOException) {
            throw InvalidImageContentException("file content is not a decodable JPEG or PNG image", exception)
        } ?: throw InvalidImageContentException("file content is not a decodable JPEG or PNG image")
        val resized = resizeIfNeeded(source)
        val watermarked = drawTextWatermark(resized)
        val out = ByteArrayOutputStream()
        Thumbnails.of(watermarked)
            .scale(1.0)
            .outputQuality(outputQuality.toDouble())
            .outputFormat(format)
            .toOutputStream(out)
        return WatermarkResult(bytes = out.toByteArray(), width = source.width, height = source.height)
    }

    /**
     * Skaliert ein Bild auf maximal `maxDimension` Pixel Kantenlänge herunter.
     *
     * Kleinere Bilder bleiben unverändert; es wird nie hochskaliert.
     *
     * @param source Gelesenes Originalbild.
     * @return Verkleinertes Bild oder das Original, falls es die Grenze nicht überschreitet.
     */
    private fun resizeIfNeeded(source: BufferedImage): BufferedImage {
        val exceedsMax = source.width > maxDimension || source.height > maxDimension
        if (!exceedsMax) return source
        return Thumbnails.of(source)
            .size(maxDimension, maxDimension)
            .keepAspectRatio(true)
            .asBufferedImage()
    }

    /**
     * Zeichnet den konfigurierten Wasserzeichentext wiederholt über die gesamte Bildhöhe.
     *
     * Die Schriftgröße wird so gewählt, dass der Text ca. 80% der Bildbreite einnimmt.
     * Die Anzahl der Wiederholungen ergibt sich aus der Texthöhe zzgl. Zeilenabstand;
     * die Zeilen werden anschließend gleichmäßig über die volle Höhe verteilt, sodass
     * die Abstände zwischen den Zeilen (und zu den Rändern) überall gleich groß sind.
     *
     * @param source Bereits auf die Zielgröße skaliertes Bild.
     * @return Neues Bild mit wiederholtem Wasserzeichen.
     */
    private fun drawTextWatermark(source: BufferedImage): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val g = result.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(source, 0, 0, null)

        val targetTextWidth = source.width * WATERMARK_WIDTH_RATIO
        val fontSize = fontSizeForTargetWidth(g, targetTextWidth)
        g.font = Font("Arial", Font.BOLD, fontSize)
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)
        g.color = Color.WHITE

        val fm = g.fontMetrics
        val textWidth = fm.stringWidth(watermarkText)
        val textHeight = fm.height
        val x = (source.width - textWidth) / 2

        // Zeilenraster: Texthöhe plus Abstand von der Größe der Texthöhe selbst, damit
        // die Lücke zwischen den Wiederholungen sichtbar gleich groß bleibt.
        val rowPitch = textHeight * ROW_PITCH_FACTOR
        val rows = (source.height / rowPitch).toInt().coerceAtLeast(1)
        val actualPitch = source.height.toDouble() / rows
        for (row in 0 until rows) {
            val centerY = ((row + 0.5) * actualPitch).toInt()
            val y = centerY + textHeight / 2
            g.drawString(watermarkText, x, y)
        }
        g.dispose()
        return result
    }

    /**
     * Ermittelt die Schriftgröße, bei der der Wasserzeichentext die Zielbreite erreicht.
     *
     * Nutzt eine lineare Näherung über eine Referenzschriftgröße, da die Textbreite bei
     * fester Schriftart annähernd proportional zur Schriftgröße wächst.
     *
     * @param g Graphics-Kontext zur Textvermessung.
     * @param targetTextWidth Gewünschte Textbreite in Pixeln.
     * @return Schriftgröße in Punkt, mindestens `MIN_FONT_SIZE`.
     */
    private fun fontSizeForTargetWidth(g: java.awt.Graphics2D, targetTextWidth: Double): Int {
        val referenceSize = 100
        g.font = Font("Arial", Font.BOLD, referenceSize)
        val referenceWidth = g.fontMetrics.stringWidth(watermarkText)
        if (referenceWidth <= 0) return MIN_FONT_SIZE
        return ((referenceSize * targetTextWidth) / referenceWidth).toInt().coerceAtLeast(MIN_FONT_SIZE)
    }

    private companion object {
        /**
         * Zielanteil der Bildbreite, den der Wasserzeichentext einnehmen soll.
         */
        const val WATERMARK_WIDTH_RATIO = 0.8

        /**
         * Minimale Schriftgroesse fuer sehr kleine Bilder oder leere Referenzbreiten.
         */
        const val MIN_FONT_SIZE = 8

        /**
         * Faktor zwischen Texthoehe und Abstand der wiederholten Wasserzeichenzeilen.
         */
        const val ROW_PITCH_FACTOR = 3.0
    }
}
