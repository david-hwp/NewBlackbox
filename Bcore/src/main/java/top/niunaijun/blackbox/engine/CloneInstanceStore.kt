package top.niunaijun.blackbox.engine

import org.json.JSONObject
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.utils.Slog
import java.io.File

object CloneInstanceStore {
    private const val TAG = "CloneInstanceStore"
    private val lock = Any()

    fun ensureCloneUser(cloneInstanceId: String?, packageName: String?, serverUserId: Long): Int {
        val cloneId = normalize(cloneInstanceId) ?: return -1
        val pkg = normalize(packageName) ?: return -1
        return synchronized(lock) {
            try {
                val root = readMapping()
                val key = mappingKey(cloneId, pkg, serverUserId)
                val mappedUserId = root.optJSONObject(key)?.optInt("userId", -1) ?: -1
                if (mappedUserId >= 0 && ensureUserExists(mappedUserId)) {
                    ensurePackageDirs(mappedUserId, pkg)
                    return@synchronized mappedUserId
                }

                val authUserId = readAuthMeta(cloneId)?.takeIf {
                    it.optString("packageName") == pkg &&
                            it.optLong("serverUserId", -1L) == serverUserId
                }?.optInt("localVirtualUserId", -1) ?: -1
                if (authUserId >= 0 && ensureUserExists(authUserId)) {
                    root.put(key, mappingValue(cloneId, pkg, serverUserId, authUserId))
                    writeMapping(root)
                    ensurePackageDirs(authUserId, pkg)
                    return@synchronized authUserId
                }

                val newUserId = nextUserId() ?: return@synchronized -1
                val created = BlackBoxCore.get().createUser(newUserId)
                if (created == null && !ensureUserExists(newUserId)) {
                    return@synchronized -1
                }
                root.put(key, mappingValue(cloneId, pkg, serverUserId, newUserId))
                writeMapping(root)
                ensurePackageDirs(newUserId, pkg)
                newUserId
            } catch (e: Exception) {
                Slog.w(TAG, "ensureCloneUser failed clone=$cloneId package=$pkg serverUserId=$serverUserId", e)
                -1
            }
        }
    }

    fun bindCloneUser(cloneInstanceId: String?, packageName: String?, serverUserId: Long, userId: Int) {
        val cloneId = normalize(cloneInstanceId) ?: return
        val pkg = normalize(packageName) ?: return
        synchronized(lock) {
            try {
                ensureUserExists(userId)
                ensurePackageDirs(userId, pkg)
                val root = readMapping()
                root.put(mappingKey(cloneId, pkg, serverUserId), mappingValue(cloneId, pkg, serverUserId, userId))
                writeMapping(root)
            } catch (e: Exception) {
                Slog.w(TAG, "bindCloneUser failed clone=$cloneId package=$pkg userId=$userId", e)
            }
        }
    }

    fun writeAuthorization(
        cloneInstanceId: String?,
        packageName: String?,
        serverUserId: Long,
        phone: String?,
        userId: Int,
        publicKeyId: String?,
        authorizationToken: String?
    ): Boolean {
        val cloneId = normalize(cloneInstanceId) ?: return false
        val pkg = normalize(packageName) ?: return false
        val token = normalize(authorizationToken) ?: return false
        val keyId = normalize(publicKeyId) ?: CloneAuthTokenVerifier.DEFAULT_PUBLIC_KEY_ID
        return synchronized(lock) {
            try {
                ensureUserExists(userId)
                ensurePackageDirs(userId, pkg)
                bindCloneUser(cloneId, pkg, serverUserId, userId)
                val dir = authDir(cloneId)
                dir.mkdirs()
                val meta = JSONObject()
                    .put("version", 1)
                    .put("cloneInstanceId", cloneId)
                    .put("packageName", pkg)
                    .put("serverUserId", serverUserId)
                    .put("phone", phone ?: "")
                    .put("localVirtualUserId", userId)
                    .put("publicKeyId", keyId)
                File(dir, "meta.json").writeText(meta.toString())
                val tmp = File(dir, "auth.token.tmp")
                val target = File(dir, "auth.token")
                tmp.writeText(token)
                if (target.exists()) {
                    target.delete()
                }
                tmp.renameTo(target)
            } catch (e: Exception) {
                Slog.w(TAG, "writeAuthorization failed clone=$cloneId package=$pkg userId=$userId", e)
                false
            }
        }
    }

    fun isAuthorized(cloneInstanceId: String?, packageName: String?, serverUserId: Long, userId: Int): Boolean {
        val cloneId = normalize(cloneInstanceId) ?: return false
        val pkg = normalize(packageName) ?: return false
        return try {
            CloneAuthTokenVerifier.verifyCloneAuth(readAuthMeta(cloneId), readAuthToken(cloneId), cloneId, pkg, serverUserId, userId).valid
        } catch (e: Exception) {
            Slog.w(TAG, "isAuthorized failed clone=$cloneId package=$pkg userId=$userId", e)
            false
        }
    }

    fun findMappingForPackageUser(packageName: String?, userId: Int): JSONObject? {
        val pkg = normalize(packageName) ?: return null
        return synchronized(lock) {
            val root = readMapping()
            val mapped = root.keys().asSequence()
                .mapNotNull { root.optJSONObject(it) }
                .firstOrNull {
                    it.optString("packageName") == pkg && it.optInt("userId", -1) == userId
                }
            if (mapped != null) {
                return@synchronized mapped
            }

            val recovered = findAuthMetaForPackageUser(pkg, userId)
            if (recovered != null) {
                root.put(
                    mappingKey(
                        recovered.optString("cloneInstanceId"),
                        pkg,
                        recovered.optLong("serverUserId", -1L)
                    ),
                    mappingValue(
                        recovered.optString("cloneInstanceId"),
                        pkg,
                        recovered.optLong("serverUserId", -1L),
                        userId
                    )
                )
                writeMapping(root)
            }
            recovered
        }
    }

    fun isAuthorizedMapping(mapping: JSONObject?): Boolean {
        if (mapping == null) {
            return false
        }
        return isAuthorized(
            mapping.optString("cloneInstanceId"),
            mapping.optString("packageName"),
            mapping.optLong("serverUserId", -1L),
            mapping.optInt("userId", -1)
        )
    }

    fun clearCloneUser(cloneInstanceId: String?, packageName: String?, serverUserId: Long) {
        val cloneId = normalize(cloneInstanceId) ?: return
        val pkg = normalize(packageName) ?: return
        synchronized(lock) {
            try {
                val root = readMapping()
                root.remove(mappingKey(cloneId, pkg, serverUserId))
                writeMapping(root)
                authDir(cloneId).deleteRecursively()
            } catch (e: Exception) {
                Slog.w(TAG, "clearCloneUser failed clone=$cloneId package=$pkg", e)
            }
        }
    }

    private fun authRoot(): File = File(BEnvironment.getSystemDir(), "clone-auth")

    private fun authDir(cloneInstanceId: String): File = File(authRoot(), safeFileName(cloneInstanceId))

    private fun readAuthMeta(cloneInstanceId: String): JSONObject? {
        val file = File(authDir(cloneInstanceId), "meta.json")
        if (!file.exists()) {
            return null
        }
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }

    private fun readAuthToken(cloneInstanceId: String): String? {
        val file = File(authDir(cloneInstanceId), "auth.token")
        if (!file.exists()) {
            return null
        }
        return runCatching { file.readText().trim() }.getOrNull()
    }

    private fun findAuthMetaForPackageUser(packageName: String, userId: Int): JSONObject? {
        authRoot().listFiles()?.forEach { dir ->
            val metaFile = File(dir, "meta.json")
            if (!metaFile.exists()) {
                return@forEach
            }
            val meta = runCatching { JSONObject(metaFile.readText()) }.getOrNull() ?: return@forEach
            if (meta.optString("packageName") == packageName &&
                meta.optInt("localVirtualUserId", -1) == userId
            ) {
                return meta
            }
        }
        return null
    }

    private fun mappingFile(): File = File(BEnvironment.getSystemDir(), "clone-instances.json")

    private fun readMapping(): JSONObject {
        val file = mappingFile()
        if (!file.exists()) {
            return JSONObject()
        }
        return runCatching { JSONObject(file.readText()) }.getOrElse {
            Slog.w(TAG, "clone mapping is corrupt, rebuilding from authorization records", it)
            JSONObject()
        }
    }

    private fun writeMapping(root: JSONObject) {
        val file = mappingFile()
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    private fun mappingKey(cloneInstanceId: String, packageName: String, serverUserId: Long): String {
        return "$serverUserId|$packageName|$cloneInstanceId"
    }

    private fun mappingValue(
        cloneInstanceId: String,
        packageName: String,
        serverUserId: Long,
        userId: Int
    ): JSONObject {
        return JSONObject()
            .put("cloneInstanceId", cloneInstanceId)
            .put("packageName", packageName)
            .put("serverUserId", serverUserId)
            .put("userId", userId)
    }

    private fun ensurePackageDirs(userId: Int, packageName: String) {
        BEnvironment.getDataDir(packageName, userId).mkdirs()
        BEnvironment.getDataCacheDir(packageName, userId).mkdirs()
        BEnvironment.getDataFilesDir(packageName, userId).mkdirs()
        BEnvironment.getDataDatabasesDir(packageName, userId).mkdirs()
        BEnvironment.getDeDataDir(packageName, userId).mkdirs()
        BEnvironment.getExternalDataDir(packageName, userId).mkdirs()
    }

    private fun ensureUserExists(userId: Int): Boolean {
        val active = BlackBoxCore.get().getUsers().orEmpty().any { it.id == userId }
        if (active) {
            return true
        }
        return BlackBoxCore.get().createUser(userId) != null
    }

    private fun nextUserId(): Int? {
        val usedIds = discoveredUserIds()
        return (0..9999).firstOrNull { it !in usedIds }
    }

    private fun discoveredUserIds(): Set<Int> {
        val ids = mutableSetOf<Int>()
        BlackBoxCore.get().getUsers().orEmpty().forEach { ids.add(it.id) }
        val userRoot = File(BEnvironment.getVirtualRoot(), "data/user")
        userRoot.listFiles()?.forEach { file ->
            file.name.toIntOrNull()?.let { ids.add(it) }
        }
        return ids
    }

    private fun normalize(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun safeFileName(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
