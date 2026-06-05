package com.zhirang.zhanghaoguanjia.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.util.zip.ZipFile

/**
 * EngineLoader handles dynamic loading of the Engine APK at runtime.
 * It extracts native libraries and prepares a DexClassLoader for advanced use.
 */
object EngineLoader {
    private const val TAG = "EngineLoader"
    private const val ENGINE_PACKAGE = "com.zhirang.zhanghaoguanjia.engine"
    private const val ENGINE_SERVICE = "top.niunaijun.blackbox.engine.BlackBoxEngineService"

    /**
     * Initialize the engine loading process:
     * 1. Check if Engine APK is installed
     * Returns true if engine is available (installed), false otherwise.
     *
     * Note: Native libraries are NOT loaded here. They belong to the Engine
     * process and are loaded automatically when the Engine APK starts.
     * The host app only needs to bind to the Engine Service via AIDL.
     */
    fun init(context: Context): Boolean {
        return try {
            if (!isEngineInstalled(context)) {
                Log.w(TAG, "Engine APK not installed: $ENGINE_PACKAGE")
                return false
            }

            val apkPath = getEngineApkPath(context)
            if (apkPath == null) {
                Log.w(TAG, "Could not get Engine APK path")
                return false
            }

            Log.d(TAG, "Engine APK found at: $apkPath")
            Log.d(TAG, "EngineLoader init completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing EngineLoader: ${e.message}", e)
            false
        }
    }

    /**
     * Create a DexClassLoader for the Engine APK.
     * For advanced use when reflection-based class loading is needed.
     */
    fun loadClasses(context: Context, apkFile: File): DexClassLoader {
        val optimizedDir = File(context.filesDir, "engine_dex").apply { mkdirs() }
        val libDir = File(context.filesDir, "engine_lib/${getCurrentAbi()}").apply { mkdirs() }
        return DexClassLoader(
            apkFile.absolutePath,
            optimizedDir.absolutePath,
            libDir.absolutePath,
            context.classLoader
        )
    }

    /**
     * Extract .so files from the Engine APK matching the current ABI.
     */
    fun extractNativeLibs(context: Context, apkFile: File): List<File> {
        val extractedLibs = mutableListOf<File>()
        try {
            val abi = getCurrentAbi()
            val libDir = File(context.filesDir, "engine_lib/$abi").apply {
                mkdirs()
            }

            ZipFile(apkFile).use { zipFile ->
                val entries = zipFile.entries()
                val libPrefix = "lib/$abi/"
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.startsWith(libPrefix) && entry.name.endsWith(".so")) {
                        val libName = File(entry.name).name
                        val outFile = File(libDir, libName)
                        try {
                            zipFile.getInputStream(entry).use { input ->
                                outFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            extractedLibs.add(outFile)
                            Log.d(TAG, "Extracted native lib: ${outFile.absolutePath}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to extract ${entry.name}: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting native libs: ${e.message}", e)
        }
        return extractedLibs
    }

    /**
     * Load extracted native libraries with System.load().
     */
    fun loadNativeLibs(libFiles: List<File>) {
        for (libFile in libFiles) {
            try {
                if (libFile.exists()) {
                    System.load(libFile.absolutePath)
                    Log.d(TAG, "Loaded native lib: ${libFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load native lib ${libFile.absolutePath}: ${e.message}")
            }
        }
    }

    /**
     * Check if the Engine APK is installed on the device.
     */
    fun isEngineInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(ENGINE_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking engine installation: ${e.message}")
            false
        }
    }

    /**
     * Get the APK path of the installed Engine package.
     */
    fun getEngineApkPath(context: Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(ENGINE_PACKAGE, 0)
            appInfo.sourceDir
        } catch (e: Exception) {
            Log.w(TAG, "Error getting engine APK path: ${e.message}")
            null
        }
    }

    /**
     * Get the current device ABI (arm64-v8a or armeabi-v7a).
     */
    fun getCurrentAbi(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "armeabi-v7a" } ?: "armeabi-v7a"
        } else {
            Build.CPU_ABI
        }
    }
}
