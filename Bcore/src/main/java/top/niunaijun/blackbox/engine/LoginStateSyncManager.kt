package top.niunaijun.blackbox.engine

import org.json.JSONArray
import org.json.JSONObject
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.utils.FileUtils
import top.niunaijun.blackbox.utils.Slog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object LoginStateSyncManager {
    private const val TAG = "LoginStateSync"
    private const val MAX_ARTIFACT_BYTES = 2 * 1024 * 1024
    private const val MAX_RESTORE_FILES = 5000

    fun exportLoginState(packageName: String?, userId: Int, profileId: String?): ByteArray? {
        val pkg = packageName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val profile = resolveProfile(pkg, profileId) ?: return null
        val files = collectFiles(pkg, userId, profile)
        if (files.isEmpty()) {
            return null
        }
        val manifest = JSONObject()
            .put("version", 1)
            .put("packageName", pkg)
            .put("profileId", profile.id)
            .put("sourceUserId", userId)
            .put("fileCount", files.size)
            .put("rawBytes", files.sumOf { it.file.length() })
            .put("roots", JSONArray(files.map { it.entryName.substringBefore('/') }.distinct()))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.sortedBy { it.entryName }.forEach { item ->
                zip.putNextEntry(ZipEntry(item.entryName).apply { time = item.file.lastModified() })
                FileInputStream(item.file).use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
        val bytes = out.toByteArray()
        if (bytes.size > MAX_ARTIFACT_BYTES) {
            Slog.w(TAG, "export too large package=$pkg user=$userId profile=${profile.id} bytes=${bytes.size}")
            return null
        }
        val finalManifest = JSONObject(manifest.toString())
            .put("zipBytesBeforeManifestRewrite", bytes.size)
        return rewriteManifest(bytes, finalManifest)
    }

    fun restoreLoginState(packageName: String?, userId: Int, profileId: String?, artifact: ByteArray?): Boolean {
        val pkg = packageName?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val bytes = artifact ?: return false
        if (bytes.isEmpty() || bytes.size > MAX_ARTIFACT_BYTES) {
            return false
        }
        val profile = resolveProfile(pkg, profileId) ?: return false
        return runCatching {
            BlackBoxCore.get().stopPackage(pkg, userId)
            ensureTargetDirs(pkg, userId)
            val entries = readEntries(bytes, pkg, profile)
            if (entries.isEmpty() || entries.size > MAX_RESTORE_FILES) {
                return false
            }
            clearProfileTargets(pkg, userId, profile)
            entries.forEach { entry ->
                val target = resolveRestoreTarget(pkg, userId, entry.name) ?: return false
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { output -> output.write(entry.bytes) }
            }
            true
        }.getOrElse { error ->
            Slog.w(TAG, "restore failed package=$pkg user=$userId profile=${profile.id}", error)
            false
        }
    }

    fun defaultProfileId(packageName: String?): String? {
        val pkg = packageName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return resolveProfile(pkg, null)?.id
    }

    private fun collectFiles(packageName: String, userId: Int, profile: Profile): List<ExportItem> {
        val roots = listOf(
            LogicalRoot("data", BEnvironment.getDataDir(packageName, userId)),
            LogicalRoot("de-data", BEnvironment.getDeDataDir(packageName, userId)),
            LogicalRoot("external-data", BEnvironment.getExternalDataDir(packageName, userId))
        )
        return roots.flatMap { root ->
            if (!root.dir.exists()) {
                emptyList()
            } else {
                root.dir.walkTopDown()
                    .filter { it.isFile }
                    .mapNotNull { file ->
                        val rel = file.relativeTo(root.dir).invariantSeparatorsPath
                        val logical = "${root.name}/$rel"
                        if (profile.include(logical)) ExportItem(logical, file) else null
                    }
                    .toList()
            }
        }
    }

    private fun readEntries(bytes: ByteArray, packageName: String, profile: Profile): List<RestoreEntry> {
        val entries = mutableListOf<RestoreEntry>()
        var manifestPackage: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = normalizeEntryName(entry.name) ?: return emptyList()
                if (entry.isDirectory) {
                    continue
                }
                val data = zip.readBytes()
                if (name == "manifest.json") {
                    val manifest = runCatching { JSONObject(String(data, Charsets.UTF_8)) }.getOrNull()
                    manifestPackage = manifest?.optString("packageName")
                    continue
                }
                if (!profile.include(name)) {
                    return emptyList()
                }
                entries.add(RestoreEntry(name, data))
                if (entries.size > MAX_RESTORE_FILES) {
                    return emptyList()
                }
            }
        }
        if (!manifestPackage.isNullOrBlank() && manifestPackage != packageName) {
            return emptyList()
        }
        return entries
    }

    private fun normalizeEntryName(name: String?): String? {
        val value = name?.replace('\\', '/')?.trim('/') ?: return null
        if (value.isBlank() || value.startsWith("/") || value.contains("../") || value == ".." || value.contains("/..")) {
            return null
        }
        val root = value.substringBefore('/')
        if (value != "manifest.json" && root !in setOf("data", "de-data", "external-data")) {
            return null
        }
        return value
    }

    private fun resolveRestoreTarget(packageName: String, userId: Int, entryName: String): File? {
        val rootName = entryName.substringBefore('/')
        val rel = entryName.substringAfter('/', missingDelimiterValue = "")
        if (rel.isBlank() || rel.split('/').any { it == packageName }) {
            // Prevent nested package directories from source-device absolute exports.
            return null
        }
        val root = when (rootName) {
            "data" -> BEnvironment.getDataDir(packageName, userId)
            "de-data" -> BEnvironment.getDeDataDir(packageName, userId)
            "external-data" -> BEnvironment.getExternalDataDir(packageName, userId)
            else -> return null
        }
        val target = File(root, rel)
        val rootPath = root.canonicalPath
        val targetPath = target.canonicalPath
        return if (targetPath == rootPath || !targetPath.startsWith("$rootPath/")) null else target
    }

    private fun clearProfileTargets(packageName: String, userId: Int, profile: Profile) {
        profile.clearPrefixes.forEach { prefix ->
            val target = resolveRestoreTarget(packageName, userId, "$prefix/.placeholder")
                ?.parentFile
                ?: return@forEach
            FileUtils.deleteDir(target)
        }
        ensureTargetDirs(packageName, userId)
    }

    private fun ensureTargetDirs(packageName: String, userId: Int) {
        BEnvironment.getDataDir(packageName, userId).mkdirs()
        BEnvironment.getDeDataDir(packageName, userId).mkdirs()
        BEnvironment.getExternalDataDir(packageName, userId).mkdirs()
    }

    private fun resolveProfile(packageName: String, requestedProfileId: String?): Profile? {
        val requested = requestedProfileId?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
        return when (packageName) {
            "com.sankuai.meituan.meituanwaimaibusiness" -> MEITUAN_CIPS
            "com.jd.mrd.jingming" -> JD_PREFS_D
            "me.ele.napos" -> ELE_NAPOS_E_MIN
            else -> null
        }?.takeIf { requested == null || requested == it.id.lowercase(Locale.US) || requested == it.alias.lowercase(Locale.US) }
    }

    private fun rewriteManifest(bytes: ByteArray, manifest: JSONObject): ByteArray {
        val out = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            ZipOutputStream(out).use { output ->
                output.putNextEntry(ZipEntry("manifest.json"))
                output.write(manifest.toString().toByteArray(Charsets.UTF_8))
                output.closeEntry()
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.name == "manifest.json") {
                        continue
                    }
                    output.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    input.copyTo(output)
                    output.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    private data class LogicalRoot(val name: String, val dir: File)
    private data class ExportItem(val entryName: String, val file: File)
    private data class RestoreEntry(val name: String, val bytes: ByteArray)

    private data class Profile(
        val id: String,
        val alias: String,
        val clearPrefixes: List<String>,
        val include: (String) -> Boolean
    )

    private val MEITUAN_CIPS = Profile(
        id = "meituan-waimai-cips-f",
        alias = "phase13-meituan-cips-f-20260611",
        clearPrefixes = listOf("data/files/cips", "data/cache/cips")
    ) { path ->
        val lower = path.lowercase(Locale.US)
        val rel = lower.removePrefix("data/")
        val cipsRoot = lower.startsWith("data/files/cips/") || lower.startsWith("data/cache/cips/")
        cipsRoot &&
                !lower.contains("/assets/") &&
                !lower.contains("/mrn_dio/") &&
                !lower.contains("/ddload/assets/") &&
                !lower.contains("/codecache/") &&
                !lower.contains("/mmpackage/") &&
                !lower.endsWith(".dio") &&
                !lower.endsWith(".zip") &&
                !lower.endsWith(".png") &&
                !lower.endsWith(".jpg") &&
                !lower.endsWith(".jpeg") &&
                !lower.endsWith(".webp") &&
                !lower.endsWith(".so") &&
                !lower.endsWith(".chs") &&
                !lower.endsWith(".js") &&
                MEITUAN_CIPS_MARKERS.any { rel.contains(it) }
    }

    private val JD_PREFS_D = Profile(
        id = "jd-jingming-prefs-d",
        alias = "phase13-jd-jingming-prefs-d-20260611",
        clearPrefixes = listOf("data/shared_prefs")
    ) { path ->
        path.startsWith("data/shared_prefs/") && path.endsWith(".xml")
    }

    private val ELE_NAPOS_E_MIN = Profile(
        id = "ele-napos-prefs-e-min",
        alias = "phase13-ele-napos-prefs-e-min-20260611",
        clearPrefixes = listOf("data/shared_prefs")
    ) { path ->
        if (!path.startsWith("data/shared_prefs/") || !path.endsWith(".xml")) {
            return@Profile false
        }
        val name = path.substringAfterLast('/')
        name in ELE_PREF_NAMES ||
                Regex("""user_[^/]+_sp_config_\.xml""").matches(name) ||
                Regex("""user_[^/]+_rest_[^/]+_sp_config\.xml""").matches(name)
    }

    private val MEITUAN_CIPS_MARKERS = listOf(
        "/kv",
        "/obj",
        "oneid",
        "login",
        "account",
        "user",
        "wm",
        "wmb",
        "waimai",
        "shark",
        "dx_login",
        "at_me_info",
        "xm_",
        "uuid",
        "device",
        "jsbridge_storage",
        "horn_config",
        "pre_network_cache",
        "msc_init_cache"
    )

    private val ELE_PREF_NAMES = setOf(
        "ACCS_BIND_default.xml",
        "ACCS_SDK.xml",
        "AGOO_BIND.xml",
        "Agoo_AppStore.xml",
        "AltriaXDevice.xml",
        "Alvin2.xml",
        "MtopConfigStore.xml",
        "NAPOS_LTRACKER_SP.xml",
        "SharedPreferenceAdiu.xml",
        "UTCommon.xml",
        "app_sp_config.xml",
        "me.ele.foundation.xml",
        "me.ele.napos_preferences.xml",
        "me_ele_napos.xml",
        "sgPrefs.xml",
        "sp_eleme_foundation.xml",
        "sp_eleme_needle_unsafe.xml",
        "vkeyid_profiles_v3.xml",
        "vkeyid_profiles_v4.xml",
        "vkeyid_settings.xml"
    )
}
