package lensmarket.image.domain.model

import java.time.Instant

/**
 * Status fuer Bilder, deren Wasserzeichenkopie erzeugt wurde.
 */
const val IMAGE_STATUS_WATERMARKED = "WATERMARKED"

/**
 * Status fuer fachlich archivierte Bilder.
 */
const val IMAGE_STATUS_ARCHIVED = "ARCHIVED"

/**
 * Alle fachlich erlaubten Bildstatuswerte.
 */
val ALLOWED_IMAGE_STATUSES = setOf(IMAGE_STATUS_WATERMARKED, IMAGE_STATUS_ARCHIVED)

/**
 * Unterstuetzte MIME-Types fuer Bild-Uploads.
 */
val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png")

/**
 * Maximale Upload-Größe für Bilddateien in Bytes.
 */
const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

/**
 * Command für den echten Bild-Upload.
 *
 * @param listingId Technische ID der zugehörigen Anzeige.
 * @param fileName Ursprünglicher Dateiname.
 * @param mimeType MIME-Type der Datei.
 * @param fileBytes Inhalt der hochgeladenen Datei.
 */
data class UploadImageCommand(
    val listingId: String,
    val fileName: String,
    val mimeType: String,
    val fileBytes: ByteArray
)

/**
 * Command für den fachlichen Statuswechsel eines Bildes.
 *
 * @param status Zielstatus.
 */
data class ChangeImageStatusCommand(val status: String)

/**
 * Fachlicher Suchfilter für Bild-Metadaten.
 *
 * @param listingId Optionale Anzeigen-ID.
 * @param ownerUserId Optionale Besitzer-ID.
 * @param status Optionaler Bildstatus.
 */
data class ImageSearchFilter(
    val listingId: String? = null,
    val ownerUserId: String? = null,
    val status: String? = null
)

/**
 * Fachliches Domain-Modell eines Bildes.
 *
 * Das Modell enthält Metadaten, Status und optionale Objektpfade. Die Bilddateien
 * selbst liegen im Object Storage.
 *
 * @param id Technische Bild-ID.
 * @param listingId Technische ID der zugehörigen Anzeige.
 * @param ownerUserId Technische ID des Bildbesitzers.
 * @param originalFileName Ursprünglicher Dateiname.
 * @param mimeType MIME-Type des Bildes.
 * @param width Optionale Bildbreite.
 * @param height Optionale Bildhöhe.
 * @param status Fachlicher Bildstatus.
 * @param watermarkedUrl Öffentliche URL zum Wasserzeichenbild.
 * @param originalPath Historisches Pfadfeld; im aktuellen MVP bleibt es `null`, weil Originale nicht persistiert werden.
 * @param watermarkedPath Objektpfad der Wasserzeichendatei oder `null`.
 * @param createdAt Anlagezeitpunkt.
 */
data class Image(
    val id: String,
    val listingId: String,
    val ownerUserId: String,
    val originalFileName: String,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val status: String,
    val watermarkedUrl: String,
    val originalPath: String?,
    val watermarkedPath: String?,
    val createdAt: Instant
)
