package lensmarket.image.domain.port

/**
 * Port für binäre Bilddateien im Object Storage.
 */
interface ObjectStoragePort {
    /**
     * Speichert ein Objekt unter einem technischen Schlüssel.
     *
     * @param key Objektpfad im Storage.
     * @param bytes Dateiinhalt.
     * @param contentType MIME-Type des Objekts.
     */
    suspend fun putObject(key: String, bytes: ByteArray, contentType: String)

    /**
     * Lädt ein Objekt anhand seines technischen Schlüssels.
     *
     * @param key Objektpfad im Storage.
     * @return Dateiinhalt.
     */
    suspend fun getObject(key: String): ByteArray

    /**
     * Löscht ein Objekt anhand seines technischen Schlüssels.
     *
     * @param key Objektpfad im Storage.
     */
    suspend fun deleteObject(key: String)

    /** Listet Objektschlüssel unterhalb eines Präfixes für die Orphan-Erkennung. */
    suspend fun listObjectKeys(prefix: String): Set<String>
}
