package lensmarket.image.resources

import org.eclipse.microprofile.openapi.annotations.media.Schema
import lensmarket.image.domain.model.Image
import lensmarket.image.domain.model.MAX_FILE_SIZE_BYTES
import lensmarket.image.domain.model.UploadImageCommand
import lensmarket.image.domain.usecase.ImageUseCaseValidationException
import org.jboss.resteasy.reactive.RestForm
import org.jboss.resteasy.reactive.multipart.FileUpload
import java.time.Instant

/**
 * Multipart-Request für den echten Bild-Upload.
 *
 * Die Felder werden von RESTEasy Reactive aus Formdaten befüllt.
 */
class UploadImageRequest {
    /**
     * Technische ID der zugehörigen Anzeige.
     */
    @field:RestForm("listingId")
    lateinit var listingId: String

    /**
     * Hochgeladene Bilddatei.
     */
    @field:RestForm("file")
    lateinit var file: FileUpload

    /**
     * Wandelt den Multipart-Request in ein Domain-Command um.
     *
     * @return Command für den UploadImageUseCase mit gelesenen Datei-Bytes.
     */
    fun toCommand(): UploadImageCommand {
        val uploadedFile = file.uploadedFile()
        val size = java.nio.file.Files.size(uploadedFile)
        if (size > MAX_FILE_SIZE_BYTES) {
            throw ImageUseCaseValidationException(
                "file exceeds maximum size of ${MAX_FILE_SIZE_BYTES / 1024 / 1024} MB"
            )
        }
        val bytes = java.nio.file.Files.readAllBytes(uploadedFile)
        val mimeType = file.contentType() ?: "image/jpeg"
        return UploadImageCommand(
            listingId = listingId,
            fileName = file.fileName(),
            mimeType = mimeType,
            fileBytes = bytes
        )
    }
}

/**
 * Request-Body für einen Bildstatuswechsel.
 *
 * @param status Zielstatus des Bildes.
 */
data class ChangeImageStatusRequest(
    @field:Schema(enumeration = ["WATERMARKED", "ARCHIVED"]) val status: String
) {
    /**
     * Wandelt den Status-Request in ein Domain-Command um.
     *
     * @return Command für den ChangeImageStatusUseCase.
     */
    fun toCommand() = lensmarket.image.domain.model.ChangeImageStatusCommand(status)
}

/**
 * API-Antwort für Bild-Metadaten.
 *
 * @param id Technische Bild-ID.
 * @param listingId Technische Anzeigen-ID.
 * @param ownerUserId Technische ID des Bildbesitzers.
 * @param originalFileName Ursprünglicher Dateiname.
 * @param mimeType MIME-Type des Bildes.
 * @param width Optionale Bildbreite.
 * @param height Optionale Bildhöhe.
 * @param status Fachlicher Bildstatus.
 * @param watermarkedUrl URL zum Wasserzeichenbild.
 * @param hasFile Gibt an, ob echte Dateiobjekte gespeichert sind.
 * @param createdAt Anlagezeitpunkt.
 */
data class ImageResponse(
    val id: String,
    val listingId: String,
    val ownerUserId: String,
    val originalFileName: String,
    @field:Schema(enumeration = ["image/jpeg", "image/png"]) val mimeType: String,
    @field:Schema(minimum = "1") val width: Int?,
    @field:Schema(minimum = "1") val height: Int?,
    @field:Schema(enumeration = ["WATERMARKED", "ARCHIVED"]) val status: String,
    @field:Schema(examples = ["/images/7d6d987f/watermarked"]) val watermarkedUrl: String,
    val hasFile: Boolean,
    val createdAt: Instant
)

/**
 * Wandelt ein Domain-Bild in die HTTP-Antwort um.
 *
 * @return API-Modell für Clients.
 */
fun Image.toResponse(): ImageResponse =
    ImageResponse(
        id = id,
        listingId = listingId,
        ownerUserId = ownerUserId,
        originalFileName = originalFileName,
        mimeType = mimeType,
        width = width,
        height = height,
        status = status,
        watermarkedUrl = watermarkedUrl,
        hasFile = watermarkedPath != null,
        createdAt = createdAt
    )
