package top.niunaijun.blackbox.engine

import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.utils.FileUtils
import top.niunaijun.blackbox.utils.Slog
import java.io.File

object ScopedCloneStorage {
    private const val TAG = "ScopedCloneStorage"

    fun ensurePackageDirs(serverUserId: Long, cloneInstanceId: String, packageName: String, userId: Int) {
        migratePackageDirs(serverUserId, cloneInstanceId, packageName, userId)
        BEnvironment.getDataDir(packageName, userId).mkdirs()
        BEnvironment.getDataCacheDir(packageName, userId).mkdirs()
        BEnvironment.getDataFilesDir(packageName, userId).mkdirs()
        BEnvironment.getDataDatabasesDir(packageName, userId).mkdirs()
        BEnvironment.getDeDataDir(packageName, userId).mkdirs()
        BEnvironment.getExternalDataDir(packageName, userId).mkdirs()
    }

    fun clearPackageDirs(serverUserId: Long, cloneInstanceId: String, packageName: String, userId: Int): Boolean {
        return runCatching {
            BlackBoxCore.get().stopPackage(packageName, userId)
            val runtimeData = BEnvironment.getDataDir(packageName, userId)
            val runtimeDe = BEnvironment.getDeDataDir(packageName, userId)
            val runtimeExternal = BEnvironment.getExternalDataDir(packageName, userId)
            FileUtils.deleteDir(dataDir(serverUserId, cloneInstanceId, packageName, userId))
            FileUtils.deleteDir(deDataDir(serverUserId, cloneInstanceId, packageName, userId))
            FileUtils.deleteDir(externalDataDir(serverUserId, cloneInstanceId, packageName, userId))
            FileUtils.deleteDir(runtimeData)
            FileUtils.deleteDir(runtimeDe)
            FileUtils.deleteDir(runtimeExternal)
            ensurePackageDirs(serverUserId, cloneInstanceId, packageName, userId)
            true
        }.getOrElse { error ->
            Slog.w(TAG, "clearPackageDirs failed clone=$cloneInstanceId package=$packageName user=$userId", error)
            false
        }
    }

    fun migrateAll(mappings: List<JSONObject>): JSONObject {
        val migrated = JSONArray()
        val errors = JSONArray()
        mappings.forEach { mapping ->
            val cloneId = mapping.optString("cloneInstanceId").takeIf { it.isNotBlank() }
            val pkg = mapping.optString("packageName").takeIf { it.isNotBlank() }
            val serverUserId = mapping.optLong("serverUserId", -1L)
            val userId = mapping.optInt("userId", -1)
            if (cloneId == null || pkg == null || serverUserId < 0 || userId < 0) {
                return@forEach
            }
            runCatching {
                migratePackageDirs(serverUserId, cloneId, pkg, userId)
                migrated.put(JSONObject()
                    .put("cloneInstanceId", cloneId)
                    .put("packageName", pkg)
                    .put("serverUserId", serverUserId)
                    .put("userId", userId)
                )
            }.onFailure { error ->
                errors.put(JSONObject()
                    .put("cloneInstanceId", cloneId)
                    .put("packageName", pkg)
                    .put("serverUserId", serverUserId)
                    .put("userId", userId)
                    .put("error", error.message ?: error.javaClass.name)
                )
            }
        }
        return JSONObject()
            .put("ok", errors.length() == 0)
            .put("migrated", migrated)
            .put("errors", errors)
    }

    fun dataDir(serverUserId: Long, cloneInstanceId: String, packageName: String, userId: Int): File {
        return File(cardRoot(serverUserId, cloneInstanceId), "user/$userId/$packageName")
    }

    fun deDataDir(serverUserId: Long, cloneInstanceId: String, packageName: String, userId: Int): File {
        return File(cardRoot(serverUserId, cloneInstanceId), "user_de/$userId/$packageName")
    }

    fun externalDataDir(serverUserId: Long, cloneInstanceId: String, packageName: String, userId: Int): File {
        return File(externalCardRoot(serverUserId, cloneInstanceId), "storage/emulated/$userId/Android/data/$packageName")
    }

    fun authDir(serverUserId: Long, cloneInstanceId: String): File {
        return File(cardRoot(serverUserId, cloneInstanceId), "auth")
    }

    private fun migratePackageDirs(serverUserId: Long, cloneInstanceId: String, packageName: String, userId: Int) {
        moveAndLink(BEnvironment.getLegacyDataDir(packageName, userId), dataDir(serverUserId, cloneInstanceId, packageName, userId))
        moveAndLink(BEnvironment.getLegacyDeDataDir(packageName, userId), deDataDir(serverUserId, cloneInstanceId, packageName, userId))
        moveWithoutLink(BEnvironment.getLegacyExternalDataDir(packageName, userId), externalDataDir(serverUserId, cloneInstanceId, packageName, userId))
    }

    private fun moveAndLink(runtimePath: File, scopedPath: File) {
        scopedPath.parentFile?.mkdirs()
        if (runtimePath.exists() && !isSymlink(runtimePath)) {
            if (!scopedPath.exists()) {
                val renamed = runtimePath.renameTo(scopedPath)
                if (!renamed) {
                    copyRecursively(runtimePath, scopedPath)
                    FileUtils.deleteDir(runtimePath)
                }
            } else {
                mergeRuntimeIntoScoped(runtimePath, scopedPath)
                FileUtils.deleteDir(runtimePath)
            }
        }
        if (!scopedPath.exists()) {
            scopedPath.mkdirs()
        }
        if (runtimePath.exists()) {
            return
        }
        runtimePath.parentFile?.mkdirs()
        if (!createSymlink(scopedPath, runtimePath)) {
            Slog.w(TAG, "symlink unavailable runtime dir=${runtimePath.absolutePath}")
        }
    }

    private fun moveWithoutLink(runtimePath: File, scopedPath: File) {
        scopedPath.parentFile?.mkdirs()
        if (runtimePath.exists() && !isSymlink(runtimePath)) {
            if (!scopedPath.exists()) {
                val renamed = runtimePath.renameTo(scopedPath)
                if (!renamed) {
                    copyRecursively(runtimePath, scopedPath)
                    FileUtils.deleteDir(runtimePath)
                }
            } else {
                mergeRuntimeIntoScoped(runtimePath, scopedPath)
                FileUtils.deleteDir(runtimePath)
            }
        }
        if (!scopedPath.exists()) {
            scopedPath.mkdirs()
        }
    }

    private fun mergeRuntimeIntoScoped(runtimePath: File, scopedPath: File) {
        if (!runtimePath.exists() || isSymlink(runtimePath)) {
            return
        }
        if (runtimePath.isFile) {
            scopedPath.parentFile?.mkdirs()
            if (!scopedPath.exists()) {
                runtimePath.copyTo(scopedPath, overwrite = false)
            }
            return
        }
        runtimePath.listFiles()?.forEach { child ->
            val target = File(scopedPath, child.name)
            if (child.isDirectory && !isSymlink(child)) {
                mergeRuntimeIntoScoped(child, target)
            } else if (!target.exists()) {
                target.parentFile?.mkdirs()
                child.copyTo(target, overwrite = false)
            }
        }
    }

    private fun copyRecursively(source: File, target: File) {
        if (!source.exists()) {
            return
        }
        if (source.isDirectory && !isSymlink(source)) {
            target.mkdirs()
            source.listFiles()?.forEach { child ->
                copyRecursively(child, File(target, child.name))
            }
        } else {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }

    private fun createSymlink(target: File, link: File): Boolean {
        return runCatching {
            if (link.exists()) {
                return true
            }
            Os.symlink(target.absolutePath, link.absolutePath)
            true
        }.getOrElse { error ->
            Slog.w(TAG, "createSymlink failed target=${target.absolutePath} link=${link.absolutePath}", error)
            false
        }
    }

    private fun isSymlink(file: File): Boolean {
        return runCatching { FileUtils.isSymlink(file) }.getOrDefault(false)
    }

    private fun cardRoot(serverUserId: Long, cloneInstanceId: String): File {
        return File(BEnvironment.getVirtualRoot(), "accounts/${safeName(serverUserId.toString())}/cards/${safeName(cloneInstanceId)}")
    }

    private fun externalCardRoot(serverUserId: Long, cloneInstanceId: String): File {
        return File(BEnvironment.getExternalVirtualRoot(), "accounts/${safeName(serverUserId.toString())}/cards/${safeName(cloneInstanceId)}")
    }

    private fun safeName(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
