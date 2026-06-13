package com.zhirang.zhanghaoguanjia.data

import android.content.Context
import com.zhirang.zhanghaoguanjia.app.App
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object LoginStateBackupStore {
    private const val ROOT_DIR = "login-state"
    private const val METADATA_FILE = "metadata.json"

    data class StagedArtifact(
        val shopId: Long,
        val packageName: String,
        val profile: String,
        val file: File,
        val sha256: String,
        val size: Long,
        val createdAtEpochMillis: Long,
        val purpose: Purpose
    )

    enum class Purpose {
        UPLOAD,
        RESTORE
    }

    fun stageForUpload(
        shopId: Long,
        packageName: String,
        userId: Int,
        profile: String,
        artifact: ByteArray,
        manifest: JSONObject
    ): StagedArtifact {
        return stage(shopId, packageName, userId, profile, artifact, manifest, Purpose.UPLOAD)
    }

    fun stageForRestore(
        shopId: Long,
        packageName: String,
        userId: Int,
        profile: String,
        artifact: ByteArray
    ): StagedArtifact {
        val now = System.currentTimeMillis()
        val manifest = JSONObject()
            .put("systemShopId", shopId)
            .put("packageName", packageName)
            .put("profileId", profile)
            .put("localVirtualUserId", userId)
            .put("artifactCreatedAtEpochMillis", now)
            .put("bytes", artifact.size)
        return stage(shopId, packageName, userId, profile, artifact, manifest, Purpose.RESTORE)
    }

    fun markUploadedAndDelete(staged: StagedArtifact) {
        deleteFile(staged.file)
        writeMetadata(
            staged,
            rawArtifactState = "UPLOADED_DELETED",
            uploadedAtEpochMillis = System.currentTimeMillis()
        )
    }

    fun markRestoreFinishedAndDelete(staged: StagedArtifact, restored: Boolean) {
        deleteFile(staged.file)
        writeMetadata(
            staged,
            rawArtifactState = if (restored) "RESTORED_DELETED" else "RESTORE_FAILED_DELETED",
            uploadedAtEpochMillis = null
        )
    }

    fun latestPendingUpload(shopId: Long, packageName: String, profile: String? = null): StagedArtifact? {
        val dir = stagingDir(shopId, Purpose.UPLOAD)
        if (!dir.exists()) {
            return null
        }
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".zip") }
            ?.mapNotNull { file -> stagedFromFile(shopId, packageName, profile, file, Purpose.UPLOAD) }
            ?.maxByOrNull { it.createdAtEpochMillis }
    }

    fun readBytes(staged: StagedArtifact): ByteArray? {
        return runCatching { staged.file.readBytes() }.getOrNull()
    }

    private fun stage(
        shopId: Long,
        packageName: String,
        @Suppress("UNUSED_PARAMETER") userId: Int,
        profile: String,
        artifact: ByteArray,
        manifest: JSONObject,
        purpose: Purpose
    ): StagedArtifact {
        val createdAt = manifest.optLong("artifactCreatedAtEpochMillis", System.currentTimeMillis())
        val sha256 = sha256(artifact)
        val dir = stagingDir(shopId, purpose).apply { mkdirs() }
        val file = File(dir, "${createdAt}-${sha256.take(16)}.zip")
        file.writeBytes(artifact)
        val staged = StagedArtifact(
            shopId = shopId,
            packageName = packageName,
            profile = profile,
            file = file,
            sha256 = sha256,
            size = artifact.size.toLong(),
            createdAtEpochMillis = createdAt,
            purpose = purpose
        )
        writeMetadata(
            staged,
            rawArtifactState = if (purpose == Purpose.UPLOAD) "STAGED_FOR_UPLOAD" else "STAGED_FOR_RESTORE",
            uploadedAtEpochMillis = null,
            extra = manifest
        )
        return staged
    }

    private fun stagedFromFile(
        shopId: Long,
        packageName: String,
        profile: String?,
        file: File,
        purpose: Purpose
    ): StagedArtifact? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val createdAt = file.name.substringBefore('-').toLongOrNull() ?: file.lastModified()
        return StagedArtifact(
            shopId = shopId,
            packageName = packageName,
            profile = profile ?: "",
            file = file,
            sha256 = sha256(bytes),
            size = bytes.size.toLong(),
            createdAtEpochMillis = createdAt,
            purpose = purpose
        )
    }

    private fun writeMetadata(
        staged: StagedArtifact,
        rawArtifactState: String,
        uploadedAtEpochMillis: Long?,
        extra: JSONObject? = null
    ) {
        val root = JSONObject()
            .put("systemShopId", staged.shopId)
            .put("packageName", staged.packageName)
            .put("profileId", staged.profile)
            .put("sha256", staged.sha256)
            .put("size", staged.size)
            .put("artifactCreatedAtEpochMillis", staged.createdAtEpochMillis)
            .put("rawArtifactState", rawArtifactState)
            .put("purpose", staged.purpose.name)
            .put("updatedAtEpochMillis", System.currentTimeMillis())
        uploadedAtEpochMillis?.let { root.put("uploadedAtEpochMillis", it) }
        extra?.let { root.put("manifest", it) }
        metadataFile(staged.shopId).apply {
            parentFile?.mkdirs()
            writeText(root.toString(), Charsets.UTF_8)
        }
    }

    private fun stagingDir(shopId: Long, purpose: Purpose): File {
        val name = if (purpose == Purpose.UPLOAD) "upload-staging" else "restore-staging"
        return File(shopDir(shopId), name)
    }

    private fun metadataFile(shopId: Long): File {
        return File(shopDir(shopId), METADATA_FILE)
    }

    private fun shopDir(shopId: Long): File {
        return File(App.getContext().filesDir, "$ROOT_DIR/shops/$shopId")
    }

    private fun deleteFile(file: File) {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
