package top.niunaijun.blackbox.engine

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.utils.Slog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class EngineCloneDataImportActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var messageView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        layout.addView(ProgressBar(this).apply { isIndeterminate = true })
        messageView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            text = "正在导入历史店铺数据"
            setPadding(0, 32, 0, 0)
        }
        layout.addView(messageView)
        setContentView(layout)

        Thread {
            val result = runCatching { importCloneData() }
                .getOrElse { error ->
                    Slog.w(TAG, "legacy clone data import failed", error)
                    JSONObject()
                        .put("ok", false)
                        .put("error", error.message ?: error.javaClass.name)
                }
            Slog.d(TAG, "legacy clone data import result=${result.toString(2)}")
            handler.post {
                messageView.text = if (result.optBoolean("ok")) {
                    "历史店铺数据迁移完成"
                } else {
                    "历史店铺数据迁移失败"
                }
                val resultIntent = android.content.Intent()
                    .putExtra(EXTRA_RESULT_JSON, result.toString())
                setResult(if (result.optBoolean("ok")) RESULT_OK else RESULT_CANCELED, resultIntent)
                handler.postDelayed({
                    finish()
                    overridePendingTransition(0, 0)
                }, 600)
            }
        }.start()
    }

    private fun importCloneData(): JSONObject {
        BEnvironment.load()
        val serverUserId = intent.getLongExtra(EXTRA_SERVER_USER_ID, -1L)
        if (serverUserId <= 0L) {
            error("Missing serverUserId")
        }
        val request = LegacyCloneImportRequest.from(intent.getStringExtra(EXTRA_SHOPS_JSON), serverUserId)
        if (request.shops.isEmpty()) {
            return JSONObject()
                .put("ok", true)
                .put("serverUserId", serverUserId)
                .put("imported", JSONArray())
                .put("skipped", JSONArray())
                .put("errors", JSONArray())
                .put("message", "No clone shops to import")
        }
        val sources = LegacyCloneZipSource.fromIntent(
            intent.getStringExtra(EXTRA_ZIP_SOURCES_JSON),
            path = intent.getStringExtra(EXTRA_ZIP_PATH),
            uri = intent.getStringExtra(EXTRA_ZIP_URI)?.let(Uri::parse),
            activity = this
        )
        if (sources.isEmpty()) {
            error("Missing zip sources")
        }
        val importer = LegacyCloneZipImporter(sources, request)
        return importer.import()
    }

    companion object {
        private const val TAG = "EngineCloneImport"
        const val EXTRA_ZIP_PATH = "zipPath"
        const val EXTRA_ZIP_URI = "zipUri"
        const val EXTRA_ZIP_SOURCES_JSON = "zipSourcesJson"
        const val EXTRA_SERVER_USER_ID = "serverUserId"
        const val EXTRA_SHOPS_JSON = "shopsJson"
        const val EXTRA_RESULT_JSON = "result"
    }
}

private data class LegacyCloneImportRequest(
    val serverUserId: Long,
    val shops: List<LegacyCloneShop>
) {
    val byCloneKey: Map<String, LegacyCloneShop> = shops.associateBy { it.key }

    companion object {
        fun from(rawJson: String?, serverUserId: Long): LegacyCloneImportRequest {
            val array = JSONArray(rawJson ?: "[]")
            val shops = (0 until array.length())
                .mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val cloneId = item.optString("cloneInstanceId").trim().takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val packageName = item.optString("packageName").trim().takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    val localUserId = if (item.has("localVirtualUserId") && !item.isNull("localVirtualUserId")) {
                        item.optInt("localVirtualUserId", -1).takeIf { it >= 0 }
                    } else {
                        null
                    }
                    LegacyCloneShop(
                        cloneInstanceId = cloneId,
                        packageName = packageName,
                        targetUserId = localUserId
                    )
                }
            return LegacyCloneImportRequest(serverUserId, shops)
        }
    }
}

private data class LegacyCloneShop(
    val cloneInstanceId: String,
    val packageName: String,
    val targetUserId: Int?
) {
    val key: String = "$packageName|$cloneInstanceId"
}

private data class LegacyCloneMapping(
    val shop: LegacyCloneShop,
    val oldUserId: Int,
    val targetUserId: Int
)

private class LegacyCloneZipSource(
    private val path: String?,
    private val uri: Uri?,
    private val activity: Activity,
    val payload: LegacyCloneShop? = null
) {
    fun open(): InputStream {
        uri?.let { sourceUri ->
            return activity.contentResolver.openInputStream(sourceUri)
                ?: error("Cannot open zip uri=$sourceUri")
        }
        val sourcePath = path?.takeIf { it.isNotBlank() } ?: error("Missing zipPath")
        return FileInputStream(File(sourcePath))
    }

    companion object {
        fun fromIntent(rawJson: String?, path: String?, uri: Uri?, activity: Activity): List<LegacyCloneZipSource> {
            val array = rawJson?.takeIf { it.isNotBlank() }?.let(::JSONArray) ?: JSONArray()
            val sources = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val itemPath = item.optString("path").trim().takeIf { it.isNotEmpty() }
                val itemUri = item.optString("uri").trim().takeIf { it.isNotEmpty() }?.let(Uri::parse)
                val cloneId = item.optString("cloneInstanceId").trim().takeIf { it.isNotEmpty() }
                val packageName = item.optString("packageName").trim().takeIf { it.isNotEmpty() }
                val localUserId = if (item.has("localVirtualUserId") && !item.isNull("localVirtualUserId")) {
                    item.optInt("localVirtualUserId", -1).takeIf { it >= 0 }
                } else {
                    null
                }
                val payload = if (cloneId != null && packageName != null) {
                    LegacyCloneShop(cloneId, packageName, localUserId)
                } else {
                    null
                }
                if (itemPath == null && itemUri == null) {
                    null
                } else {
                    LegacyCloneZipSource(itemPath, itemUri, activity, payload)
                }
            }
            if (sources.isNotEmpty()) {
                return sources
            }
            if (path.isNullOrBlank() && uri == null) {
                return emptyList()
            }
            return listOf(LegacyCloneZipSource(path, uri, activity))
        }
    }
}

private class LegacyCloneZipImporter(
    private val sources: List<LegacyCloneZipSource>,
    private val request: LegacyCloneImportRequest
) {
    private val imported = JSONArray()
    private val skipped = JSONArray()
    private val errors = JSONArray()
    private val matchedMappings = linkedMapOf<String, LegacyCloneMapping>()
    private val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    private var copiedFiles = 0
    private var copiedBytes = 0L

    fun import(): JSONObject {
        sources.forEachIndexed { index, source ->
            importSource(index, source)
        }
        if (matchedMappings.isEmpty()) {
            errors.put(
                JSONObject()
                    .put("error", "No requested clone mappings matched legacy export")
                    .put("requested", request.shops.size)
            )
            return result(ok = false)
        }
        return result(ok = errors.length() == 0)
    }

    private fun importSource(sourceIndex: Int, source: LegacyCloneZipSource) {
        val sourceMappings = linkedMapOf<String, LegacyCloneMapping>()
        val oldMappings = readLegacyMappings(source)
        if (oldMappings.isNotEmpty()) {
            oldMappings.forEach { mapping ->
                prepareMapping(mapping, sourceMappings)
            }
        } else {
            prepareScopedSourceMapping(source, sourceMappings)
        }
        if (sourceMappings.isEmpty()) {
            skipped.put(
                JSONObject()
                    .put("sourceIndex", sourceIndex)
                    .put("reason", "no requested mappings in source")
            )
            return
        }
        extractMatchedData(source, sourceMappings)
    }

    private fun prepareScopedSourceMapping(
        source: LegacyCloneZipSource,
        sourceMappings: MutableMap<String, LegacyCloneMapping>
    ) {
        val shop = source.payload ?: return
        val targetShop = request.byCloneKey[shop.key] ?: return
        val oldUserId = shop.targetUserId ?: return
        val targetUserId = allocateTargetUserId(shop.packageName, shop.cloneInstanceId, oldUserId, targetShop.targetUserId)
        if (targetUserId == null) {
            errors.put(errorReason(shop.packageName, shop.cloneInstanceId, oldUserId, "createUser failed"))
            return
        }
        if (!CloneInstanceStore.bindCloneUserForImport(shop.cloneInstanceId, shop.packageName, request.serverUserId, targetUserId)) {
            errors.put(errorReason(shop.packageName, shop.cloneInstanceId, targetUserId, "bindCloneUser failed"))
            return
        }
        val cloneMapping = LegacyCloneMapping(targetShop, oldUserId, targetUserId)
        matchedMappings[targetShop.key] = cloneMapping
        sourceMappings[targetShop.key] = cloneMapping
    }

    private fun readLegacyMappings(source: LegacyCloneZipSource): List<JSONObject> {
        ZipInputStream(BufferedInputStream(source.open())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == LEGACY_MAPPING_ENTRY) {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    val root = JSONObject(text)
                    return root.keys().asSequence()
                        .mapNotNull { root.optJSONObject(it) }
                        .toList()
                }
                zip.closeEntry()
            }
        }
        return emptyList()
    }

    private fun prepareMapping(mapping: JSONObject, sourceMappings: MutableMap<String, LegacyCloneMapping>) {
        val packageName = mapping.optString("packageName").trim().takeIf { it.isNotEmpty() }
        val cloneId = mapping.optString("cloneInstanceId").trim().takeIf { it.isNotEmpty() }
        val sourceServerUserId = mapping.optLong("serverUserId", -1L)
        val oldUserId = mapping.optInt("userId", -1)
        if (packageName == null || cloneId == null || oldUserId < 0) {
            return
        }
        if (sourceServerUserId != request.serverUserId) {
            skipped.put(skipReason(packageName, cloneId, oldUserId, "serverUserId mismatch"))
            return
        }
        val shop = request.byCloneKey["$packageName|$cloneId"]
        if (shop == null) {
            skipped.put(skipReason(packageName, cloneId, oldUserId, "not in requested shops"))
            return
        }
        val targetUserId = allocateTargetUserId(packageName, cloneId, oldUserId, shop.targetUserId)
        if (targetUserId == null) {
            errors.put(errorReason(packageName, cloneId, oldUserId, "createUser failed"))
            return
        }
        if (!CloneInstanceStore.bindCloneUserForImport(cloneId, packageName, request.serverUserId, targetUserId)) {
            errors.put(errorReason(packageName, cloneId, targetUserId, "bindCloneUser failed"))
            return
        }
        val cloneMapping = LegacyCloneMapping(shop, oldUserId, targetUserId)
        matchedMappings[shop.key] = cloneMapping
        sourceMappings[shop.key] = cloneMapping
    }

    private fun allocateTargetUserId(
        packageName: String,
        cloneId: String,
        oldUserId: Int,
        preferredUserId: Int?
    ): Int? {
        val candidates = linkedSetOf<Int>()
        preferredUserId?.takeIf { it >= 0 }?.let { candidates += it }
        if (oldUserId >= 0) {
            candidates += oldUserId
        }
        candidates.forEach { candidate ->
            if (canBindTargetUser(packageName, cloneId, candidate) && ensureUserExists(candidate)) {
                return candidate
            }
        }
        for (candidate in 0..9999) {
            if (candidate in candidates) {
                continue
            }
            if (canBindTargetUser(packageName, cloneId, candidate) && ensureUserExists(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun canBindTargetUser(packageName: String, cloneId: String, userId: Int): Boolean {
        val current = CloneInstanceStore.findCloneUserId(cloneId, packageName, request.serverUserId)
        if (current == userId) {
            return true
        }
        return !CloneInstanceStore.hasPackageUserConflict(cloneId, packageName, request.serverUserId, userId)
    }

    private fun ensureUserExists(userId: Int): Boolean {
        if (BlackBoxCore.get().getUsers().orEmpty().any { it.id == userId }) {
            return true
        }
        return BlackBoxCore.get().createUser(userId) != null
    }

    private fun extractMatchedData(source: LegacyCloneZipSource, sourceMappings: Map<String, LegacyCloneMapping>) {
        ZipInputStream(BufferedInputStream(source.open())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name
                val target = resolveTarget(name, sourceMappings)
                if (target != null) {
                    runCatching { copyEntry(zip, target, entry.time) }
                        .onFailure { error ->
                            errors.put(
                                JSONObject()
                                    .put("entry", name)
                                    .put("target", target.file.absolutePath)
                                    .put("error", error.message ?: error.javaClass.name)
                            )
                        }
                }
                zip.closeEntry()
            }
        }
        sourceMappings.values.forEach { mapping ->
            imported.put(
                JSONObject()
                    .put("cloneInstanceId", mapping.shop.cloneInstanceId)
                    .put("packageName", mapping.shop.packageName)
                    .put("oldUserId", mapping.oldUserId)
                    .put("targetUserId", mapping.targetUserId)
            )
            ScopedCloneStorage.ensurePackageDirs(
                request.serverUserId,
                mapping.shop.cloneInstanceId,
                mapping.shop.packageName,
                mapping.targetUserId
            )
        }
    }

    private fun resolveTarget(entryName: String, sourceMappings: Map<String, LegacyCloneMapping>): ExtractTarget? {
        sourceMappings.values.forEach { mapping ->
            resolveForMapping(entryName, mapping)?.let { return it }
        }
        return null
    }

    private fun resolveForMapping(entryName: String, mapping: LegacyCloneMapping): ExtractTarget? {
        val cloneId = safeName(mapping.shop.cloneInstanceId)
        val packageName = mapping.shop.packageName
        val oldUserId = mapping.oldUserId
        val serverUserId = safeName(request.serverUserId.toString())

        resolveRelative(
            entryName,
            "virtual-root/accounts/$serverUserId/cards/$cloneId/user/$oldUserId/$packageName/",
            ScopedCloneStorage.dataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "virtual-root/accounts/$serverUserId/cards/$cloneId/user_de/$oldUserId/$packageName/",
            ScopedCloneStorage.deDataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "external-virtual-root/accounts/$serverUserId/cards/$cloneId/storage/emulated/$oldUserId/Android/data/$packageName/",
            ScopedCloneStorage.externalDataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "virtual-root/data/user/$oldUserId/$packageName/",
            ScopedCloneStorage.dataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "virtual-root/data/user_de/$oldUserId/$packageName/",
            ScopedCloneStorage.deDataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "external-virtual-root/storage/emulated/$oldUserId/Android/data/$packageName/",
            ScopedCloneStorage.externalDataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "virtual-data-user-$oldUserId/$packageName/",
            ScopedCloneStorage.dataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "virtual-data-user-de-$oldUserId/$packageName/",
            ScopedCloneStorage.deDataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "external-data-user-$oldUserId/$packageName/",
            ScopedCloneStorage.externalDataDir(request.serverUserId, mapping.shop.cloneInstanceId, packageName, mapping.targetUserId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "virtual-root/accounts/$serverUserId/cards/$cloneId/auth/",
            ScopedCloneStorage.authDir(request.serverUserId, mapping.shop.cloneInstanceId)
        )?.let { return it }
        resolveRelative(
            entryName,
            "virtual-root/system/clone-auth/$cloneId/",
            ScopedCloneStorage.authDir(request.serverUserId, mapping.shop.cloneInstanceId)
        )?.let { return it }
        return resolveRelative(
            entryName,
            "virtual-root/data/app/$packageName/",
            File(BEnvironment.getAppDir(packageName), "")
        )
    }

    private fun resolveRelative(entryName: String, prefix: String, targetRoot: File): ExtractTarget? {
        if (!entryName.startsWith(prefix)) {
            return null
        }
        val relative = entryName.removePrefix(prefix).takeIf { it.isNotEmpty() } ?: return null
        return ExtractTarget(targetRoot, File(targetRoot, relative))
    }

    private fun copyEntry(zip: ZipInputStream, target: ExtractTarget, modifiedAt: Long) {
        val root = target.root
        val file = target.file
        root.mkdirs()
        val canonicalTarget = file.canonicalFile
        val canonicalRoot = root.canonicalFile
        if (!canonicalTarget.path.startsWith(canonicalRoot.path + File.separator) &&
            canonicalTarget.path != canonicalRoot.path
        ) {
            error("Unsafe zip entry target=${file.absolutePath}")
        }
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            while (true) {
                val read = zip.read(buffer)
                if (read <= 0) {
                    break
                }
                output.write(buffer, 0, read)
                copiedBytes += read
            }
        }
        if (modifiedAt > 0) {
            file.setLastModified(modifiedAt)
        }
        copiedFiles++
    }

    private fun result(ok: Boolean): JSONObject {
        return JSONObject()
            .put("ok", ok)
            .put("serverUserId", request.serverUserId)
            .put("requested", request.shops.size)
            .put("matched", matchedMappings.size)
            .put("copiedFiles", copiedFiles)
            .put("copiedBytes", copiedBytes)
            .put("imported", imported)
            .put("skipped", skipped)
            .put("errors", errors)
    }

    private fun skipReason(packageName: String, cloneId: String, userId: Int, reason: String): JSONObject {
        return JSONObject()
            .put("packageName", packageName)
            .put("cloneInstanceId", cloneId)
            .put("userId", userId)
            .put("reason", reason)
    }

    private fun errorReason(packageName: String, cloneId: String, userId: Int, reason: String): JSONObject {
        return JSONObject()
            .put("packageName", packageName)
            .put("cloneInstanceId", cloneId)
            .put("userId", userId)
            .put("error", reason)
    }

    private fun safeName(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    companion object {
        private const val LEGACY_MAPPING_ENTRY = "virtual-root/system/clone-instances.json"
    }
}

private data class ExtractTarget(
    val root: File,
    val file: File
)
