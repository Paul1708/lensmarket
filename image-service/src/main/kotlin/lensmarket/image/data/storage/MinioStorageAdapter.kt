package lensmarket.image.data.storage

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.ListObjectsArgs
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.mutiny.core.Vertx
import jakarta.annotation.PostConstruct
import jakarta.enterprise.context.ApplicationScoped
import lensmarket.image.domain.port.ObjectStoragePort
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.concurrent.Callable

/**
 * MinIO-Implementierung des ObjectStoragePort.
 *
 * Der Adapter speichert Wasserzeichen-Kopien in einem konfigurierten Bucket und legt
 * diesen bei Bedarf beim Start an.
 *
 * @param endpoint MinIO-Endpunkt.
 * @param accessKey Zugriffsschlüssel.
 * @param secretKey Secret Key.
 * @param bucket Name des Buckets für Bilddateien.
 */
@ApplicationScoped
class MinioStorageAdapter(
    @param:ConfigProperty(name = "lensmarket.minio.endpoint") private val endpoint: String,
    @param:ConfigProperty(name = "lensmarket.minio.access-key") private val accessKey: String,
    @param:ConfigProperty(name = "lensmarket.minio.secret-key") private val secretKey: String,
    @param:ConfigProperty(name = "lensmarket.minio.bucket", defaultValue = "lensmarket") private val bucket: String,
    private val vertx: Vertx,
) : ObjectStoragePort {

    private lateinit var client: MinioClient

    /**
     * Initialisiert den MinIO-Client und stellt den Bucket bereit.
     */
    @PostConstruct
    fun init() {
        client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build()
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
        }
    }

    /**
     * Speichert ein Objekt im konfigurierten Bucket.
     *
     * @param key Objektpfad im Bucket.
     * @param bytes Dateiinhalt.
     * @param contentType MIME-Type des Objekts.
     */
    override suspend fun putObject(key: String, bytes: ByteArray, contentType: String) {
        vertx.executeBlocking(Callable { client.putObject(
            PutObjectArgs.builder()
                .bucket(bucket)
                .`object`(key)
                .stream(bytes.inputStream(), bytes.size.toLong(), -1)
                .contentType(contentType)
                .build()
        ) }).awaitSuspending()
    }

    /**
     * Lädt ein Objekt aus dem konfigurierten Bucket.
     *
     * @param key Objektpfad im Bucket.
     * @return Dateiinhalt.
     */
    override suspend fun getObject(key: String): ByteArray =
        vertx.executeBlocking(Callable { client.getObject(
            GetObjectArgs.builder()
                .bucket(bucket)
                .`object`(key)
                .build()
        ).readBytes() }).awaitSuspending()

    /**
     * Löscht ein Objekt aus dem konfigurierten Bucket.
     *
     * @param key Objektpfad im Bucket.
     */
    override suspend fun deleteObject(key: String) {
        vertx.executeBlocking(Callable { client.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucket)
                .`object`(key)
                .build()
        ) }).awaitSuspending()
    }

    override suspend fun listObjectKeys(prefix: String): Set<String> =
        vertx.executeBlocking(Callable {
            client.listObjects(
                ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build()
            ).map { it.get().objectName() }.toSet()
        }).awaitSuspending()
}
