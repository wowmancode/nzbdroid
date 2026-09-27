package com.owan.nzbdroid.data.smb

import com.owan.nzbdroid.data.settings.SmbConfig
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Properties

/**
 * Thin wrapper around jcifs-ng for browsing one saved SMB/Samba share and moving files
 * to/from it. Android has no built-in SMB client usable for arbitrary authenticated
 * network shares, so this talks SMB2/3 directly.
 */
class SmbClient(private val config: SmbConfig) {

    private val cifsContext: CIFSContext by lazy {
        val props = Properties().apply {
            // Prefer modern SMB2/3; most NAS boxes and Windows have SMB1 disabled by default.
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.connTimeout", "10000")
        }
        val base = BaseContext(PropertyConfiguration(props))
        val auth = NtlmPasswordAuthenticator(
            config.domain.ifBlank { null },
            config.username,
            config.password
        )
        base.withCredentials(auth)
    }

    /** Builds a fully-qualified smb:// URL for a path relative to the share root. */
    private fun urlFor(relativePath: String, trailingSlash: Boolean): String {
        val clean = relativePath.trim('/')
        val suffix = if (clean.isEmpty()) "" else "$clean/"
        val url = config.smbUrl() + suffix
        return if (trailingSlash || clean.isEmpty()) url else url.trimEnd('/')
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        // Connect/response timeouts are set via jcifs.smb.client.connTimeout in cifsContext's
        // configuration above, rather than per-SmbFile (jcifs-ng has no such setter).
        runCatching { SmbFile(config.smbUrl(), cifsContext).exists() }.getOrDefault(false)
    }

    /** Lists a directory's immediate children. Pass "" (or config.startPath) for the share root. */
    suspend fun listDirectory(relativePath: String): List<SmbEntry> = withContext(Dispatchers.IO) {
        try {
            val dirUrl = urlFor(relativePath, trailingSlash = true)
            val dir = SmbFile(dirUrl, cifsContext)
            if (!dir.isDirectory) throw SmbException("$relativePath is not a directory")

            dir.listFiles().orEmpty()
                .filter { it.name.isNotBlank() }
                .map { f ->
                    val childRelative = (relativePath.trim('/') + "/" + f.name.trim('/')).trim('/')
                    SmbEntry(
                        name = f.name.trim('/'),
                        path = childRelative,
                        isDirectory = f.isDirectory,
                        sizeBytes = if (f.isDirectory) 0L else f.length(),
                        lastModifiedMillis = f.lastModified()
                    )
                }
                .sortedWith(compareByDescending<SmbEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        } catch (e: SmbException) {
            throw e
        } catch (e: Exception) {
            throw SmbException("Couldn't list \"$relativePath\": ${e.message}", e)
        }
    }

    /**
     * Downloads a file from the share into [destFile] on the device, reporting progress.
     * [destFile]'s parent directory must already exist.
     */
    suspend fun downloadFile(
        relativePath: String,
        destFile: File,
        onProgress: (bytesDone: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        try {
            val remote = SmbFile(urlFor(relativePath, trailingSlash = false), cifsContext)
            val total = remote.length()
            remote.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
        } catch (e: Exception) {
            destFile.delete()
            throw SmbException("Download failed for \"$relativePath\": ${e.message}", e)
        }
    }

    /**
     * Uploads [localFile] to the share at [destRelativePath] (full path including filename),
     * reporting progress.
     */
    suspend fun uploadFile(
        localFile: File,
        destRelativePath: String,
        onProgress: (bytesDone: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        try {
            val remote = SmbFile(urlFor(destRelativePath, trailingSlash = false), cifsContext)
            val total = localFile.length()
            remote.outputStream.use { output ->
                localFile.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
        } catch (e: Exception) {
            throw SmbException("Upload failed for \"${localFile.name}\": ${e.message}", e)
        }
    }

    suspend fun deleteFile(relativePath: String) = withContext(Dispatchers.IO) {
        try {
            SmbFile(urlFor(relativePath, trailingSlash = false), cifsContext).delete()
        } catch (e: Exception) {
            throw SmbException("Delete failed for \"$relativePath\": ${e.message}", e)
        }
    }

    suspend fun makeDirectory(relativePath: String) = withContext(Dispatchers.IO) {
        try {
            SmbFile(urlFor(relativePath, trailingSlash = true), cifsContext).mkdirs()
        } catch (e: Exception) {
            throw SmbException("Couldn't create folder \"$relativePath\": ${e.message}", e)
        }
    }
}
