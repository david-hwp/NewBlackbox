package com.zhirang.zhanghaoguanjia.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.zhirang.zhanghaoguanjia.BuildConfig
import com.zhirang.zhanghaoguanjia.bean.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class LegacyEngineMigrationCoordinator(private val context: Context) {
    private var lastExportDirWarningAt = 0L

    data class CloneShopPayload(
        val cloneInstanceId: String,
        val packageName: String,
        val localVirtualUserId: Int?
    )

    data class LegacyExportZip(
        val file: File,
        val payload: CloneShopPayload?
    )

    sealed class LegacyExportWaitResult {
        data class Ready(val zip: File) : LegacyExportWaitResult()
        data class Failed(val reason: String) : LegacyExportWaitResult()
        data object Timeout : LegacyExportWaitResult()
    }

    fun isLegacyEngineAvailable(): Boolean {
        return packageExists(LEGACY_ENGINE_PACKAGE) &&
                LEGACY_ENGINE_PACKAGE != EngineInstaller.ENGINE_PACKAGE
    }

    fun hasImportableShops(shops: List<Shop>): Boolean {
        return buildShopPayloads(shops).isNotEmpty()
    }

    fun buildShopPayloads(shops: List<Shop>): List<CloneShopPayload> {
        return shops.mapNotNull { shop ->
            val cloneId = shop.cloneInstanceId?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val packageName = shop.packageName?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            CloneShopPayload(cloneId, packageName, shop.localVirtualUserId?.takeIf { it >= 0 })
        }.distinctBy { "${it.packageName}|${it.cloneInstanceId}" }
    }

    fun buildExportIntent(payload: CloneShopPayload? = null): Intent {
        return Intent().apply {
            component = ComponentName(
                LEGACY_ENGINE_PACKAGE,
                "top.niunaijun.blackbox.engine.EngineCloneDataExportActivity"
            )
            if (payload?.localVirtualUserId != null) {
                putExtra("includeAll", false)
                putExtra("packageName", payload.packageName)
                putExtra("userId", payload.localVirtualUserId)
            } else {
                putExtra("includeAll", true)
            }
            addFlags(
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
    }

    fun buildImportIntent(exports: List<LegacyExportZip>, serverUserId: Long, shops: List<CloneShopPayload>): Intent {
        val sources = JSONArray()
        var clipData: android.content.ClipData? = null
        exports.forEach { export ->
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                export.file
            )
            if (clipData == null) {
                clipData = android.content.ClipData.newRawUri("legacy-clone-data", uri)
            } else {
                clipData?.addItem(android.content.ClipData.Item(uri))
            }
            sources.put(
                JSONObject()
                    .put("path", export.file.absolutePath)
                    .put("uri", uri.toString())
                    .put("packageName", export.payload?.packageName)
                    .put("cloneInstanceId", export.payload?.cloneInstanceId)
                    .put("localVirtualUserId", export.payload?.localVirtualUserId)
            )
        }
        return Intent().apply {
            component = ComponentName(
                EngineInstaller.ENGINE_PACKAGE,
                "top.niunaijun.blackbox.engine.EngineCloneDataImportActivity"
            )
            exports.firstOrNull()?.let { first ->
                putExtra("zipPath", first.file.absolutePath)
                putExtra(
                    "zipUri",
                    FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        first.file
                    ).toString()
                )
            }
            putExtra("zipSourcesJson", sources.toString())
            putExtra("serverUserId", serverUserId)
            putExtra("shopsJson", shopsJson(shops).toString())
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            this.clipData = clipData
        }
    }

    fun findExistingExportZip(payload: CloneShopPayload?, startedAtMillis: Long): File? {
        return findLatestExportZip(startedAtMillis, payload)
    }

    suspend fun waitForLatestExportZip(startedAtMillis: Long, payload: CloneShopPayload? = null): File? = withContext(Dispatchers.IO) {
        when (val result = waitForLatestExportResult(startedAtMillis, payload)) {
            is LegacyExportWaitResult.Ready -> result.zip
            else -> null
        }
    }

    suspend fun waitForLatestExportResult(
        startedAtMillis: Long,
        payload: CloneShopPayload? = null
    ): LegacyExportWaitResult = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + EXPORT_WAIT_TIMEOUT_MS
        var lastCandidate: File? = null
        var lastSize = -1L
        var stableCount = 0
        var lastTmpCandidate: File? = null
        var lastTmpSize = -1L
        var stableTmpCount = 0
        Log.i(TAG, "Wait legacy export zip prefix=${exportFilePrefix(payload)} startedAt=$startedAtMillis")
        while (System.currentTimeMillis() < deadline) {
            val candidate = findLatestExportZip(startedAtMillis, payload)
            if (candidate != null) {
                val size = candidate.length()
                if (candidate != lastCandidate) {
                    Log.i(TAG, "Found legacy export candidate file=${candidate.name} bytes=$size")
                }
                if (candidate == lastCandidate && size == lastSize && size > 0L) {
                    stableCount++
                    if (stableCount >= 3) {
                        return@withContext LegacyExportWaitResult.Ready(candidate)
                    }
                } else {
                    lastCandidate = candidate
                    lastSize = size
                    stableCount = 0
                }
            }
            val tmpCandidate = findLatestExportTmp(startedAtMillis, payload)
            if (tmpCandidate != null) {
                val size = tmpCandidate.length()
                if (tmpCandidate != lastTmpCandidate) {
                    Log.i(TAG, "Found legacy export tmp file=${tmpCandidate.name} bytes=$size")
                }
                if (tmpCandidate == lastTmpCandidate && size == lastTmpSize && size in 1..FAILED_TMP_MAX_BYTES) {
                    stableTmpCount++
                    if (stableTmpCount >= 3) {
                        Log.w(TAG, "Legacy export failed tmp file=${tmpCandidate.name} bytes=$size")
                        return@withContext LegacyExportWaitResult.Failed("旧引擎没有找到该店铺的历史分身数据")
                    }
                } else {
                    lastTmpCandidate = tmpCandidate
                    lastTmpSize = size
                    stableTmpCount = 0
                }
            }
            delay(EXPORT_POLL_INTERVAL_MS)
        }
        Log.w(TAG, "Timed out waiting legacy export zip prefix=${exportFilePrefix(payload)}")
        LegacyExportWaitResult.Timeout
    }

    private fun findLatestExportZip(startedAtMillis: Long, payload: CloneShopPayload?): File? {
        val exportDir = File(
            "/sdcard/Android/data/$LEGACY_ENGINE_PACKAGE/files/clone-export"
        )
        val expectedPrefix = exportFilePrefix(payload)
        val files = runCatching { exportDir.listFiles() }.getOrNull()
        if (files == null) {
            val now = System.currentTimeMillis()
            if (now - lastExportDirWarningAt > EXPORT_DIR_WARNING_INTERVAL_MS) {
                lastExportDirWarningAt = now
                Log.w(
                    TAG,
                    "Legacy export dir is not readable path=${exportDir.absolutePath} exists=${exportDir.exists()} canRead=${exportDir.canRead()}"
                )
            }
            return null
        }
        return files
            .asSequence()
            .filter {
                it.isFile &&
                    it.name.endsWith(".zip") &&
                    it.name.startsWith(expectedPrefix) &&
                    it.lastModified() >= startedAtMillis - EXPORT_TIME_TOLERANCE_MS
            }
            .maxByOrNull { it.lastModified() }
    }

    private fun findLatestExportTmp(startedAtMillis: Long, payload: CloneShopPayload?): File? {
        val exportDir = File(
            "/sdcard/Android/data/$LEGACY_ENGINE_PACKAGE/files/clone-export"
        )
        val expectedPrefix = exportFilePrefix(payload)
        val files = runCatching { exportDir.listFiles() }.getOrNull() ?: return null
        return files
            .asSequence()
            .filter {
                it.isFile &&
                    it.name.endsWith(".zip.tmp") &&
                    it.name.startsWith(expectedPrefix) &&
                    it.lastModified() >= startedAtMillis - EXPORT_TIME_TOLERANCE_MS
            }
            .maxByOrNull { it.lastModified() }
    }

    private fun exportFilePrefix(payload: CloneShopPayload?): String {
        val safePackage = payload?.takeIf { it.localVirtualUserId != null }
            ?.packageName
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?: "all"
        val userId = payload?.localVirtualUserId ?: 0
        return "clone_data_${safePackage}_u${userId}_"
    }

    private fun packageExists(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun shopsJson(shops: List<CloneShopPayload>): JSONArray {
        val array = JSONArray()
        shops.forEach { shop ->
            array.put(
                JSONObject()
                    .put("cloneInstanceId", shop.cloneInstanceId)
                    .put("packageName", shop.packageName)
                    .put("localVirtualUserId", shop.localVirtualUserId)
            )
        }
        return array
    }

    companion object {
        private const val TAG = "LegacyEngineMigration"
        private const val LEGACY_ENGINE_PACKAGE = "com.zhirang.zhanghaoguanjia.engine"
        private const val EXPORT_WAIT_TIMEOUT_MS = 30 * 60 * 1000L
        private const val EXPORT_POLL_INTERVAL_MS = 2_000L
        private const val EXPORT_TIME_TOLERANCE_MS = 30_000L
        private const val EXPORT_DIR_WARNING_INTERVAL_MS = 30_000L
        private const val FAILED_TMP_MAX_BYTES = 1024L
    }
}
