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
                Slog.w(TAG, "restore rejected entries package=$pkg user=$userId profile=${profile.id} count=${entries.size}")
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
                    Slog.w(TAG, "restore rejected by profile package=$packageName profile=${profile.id} entry=$name")
                    return emptyList()
                }
                entries.add(RestoreEntry(name, data))
                if (entries.size > MAX_RESTORE_FILES) {
                    return emptyList()
                }
            }
        }
        if (!manifestPackage.isNullOrBlank() && manifestPackage != packageName) {
            Slog.w(TAG, "restore rejected manifest package mismatch package=$packageName manifest=$manifestPackage")
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
            "com.baidu.lbs.xinlingshou" -> ELE_RETAIL_E_MIN
            "com.sankuai.meituan.merchant" -> MEITUAN_MERCHANT_CIPS_E_MIN
            "com.Hotel.EBooking" -> CTRIP_EBOOKING_E_MIN
            "com.bytedance.ls.merchant" -> DOUYIN_LAIKE_E_MIN
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

    private val ELE_RETAIL_E_MIN = Profile(
        id = "ele-retail-prefs-e-min",
        alias = "phase13-ele-retail-prefs-e-min-20260613",
        clearPrefixes = listOf("data/shared_prefs")
    ) { path ->
        path.startsWith("data/shared_prefs/") &&
                path.endsWith(".xml") &&
                path.substringAfterLast('/') in ELE_RETAIL_PREF_NAMES
    }

    private val MEITUAN_MERCHANT_CIPS_E_MIN = Profile(
        id = "meituan-merchant-cips-e-min",
        alias = "phase13-meituan-merchant-cips-e-min-20260613",
        clearPrefixes = listOf("data/files/cips", "data/cache/cips", "data/shared_prefs")
    ) { path ->
        val lower = path.lowercase(Locale.US)
        if (lower.startsWith("data/shared_prefs/")) {
            return@Profile lower.substringAfterLast('/') in MEITUAN_MERCHANT_PREF_NAMES
        }
        val rel = lower.removePrefix("data/")
        (lower.startsWith("data/files/cips/") || lower.startsWith("data/cache/cips/")) &&
                !lower.contains("/assets/") &&
                !lower.contains("/mrn_dio/") &&
                !lower.contains("/ddload/") &&
                !lower.contains("/mmpackage/") &&
                !lower.endsWith(".dio") &&
                !lower.endsWith(".zip") &&
                !lower.endsWith(".png") &&
                !lower.endsWith(".jpg") &&
                !lower.endsWith(".jpeg") &&
                !lower.endsWith(".webp") &&
                !lower.endsWith(".so") &&
                !lower.endsWith(".js") &&
                MEITUAN_MERCHANT_CIPS_MARKERS.any { rel.contains(it) }
    }

    private val CTRIP_EBOOKING_E_MIN = Profile(
        id = "ctrip-ebooking-prefs-mmkv-e-min",
        alias = "phase13-ctrip-ebooking-prefs-mmkv-e-min-20260613",
        clearPrefixes = listOf("data/shared_prefs", "data/files/mmkv")
    ) { path ->
        when {
            path.startsWith("data/shared_prefs/") && path.endsWith(".xml") ->
                path.substringAfterLast('/') in CTRIP_EBOOKING_PREF_NAMES
            path.startsWith("data/files/mmkv/") ->
                path.substringAfter("data/files/mmkv/") in CTRIP_EBOOKING_MMKV_NAMES
            else -> false
        }
    }

    private val DOUYIN_LAIKE_E_MIN = Profile(
        id = "douyin-laike-account-keva-e-min",
        alias = "phase13-douyin-laike-account-keva-e-min-20260613",
        clearPrefixes = listOf(
            "data/shared_prefs",
            "data/files/keva/repo",
            "data/databases/account_db",
            "data/databases/account_db-journal",
            "data/databases/verifystorage.db",
            "data/databases/verifystorage.db-journal",
            "data/databases/299467@bd_tea_agent.db",
            "data/databases/299467@bd_tea_agent.db-journal",
            "data/databases/ss_app_log.db",
            "data/databases/ss_app_log.db-journal",
            "data/databases/bd_sync_sdk_v4.db",
            "data/databases/bd_sync_sdk_v4.db-shm",
            "data/databases/bd_sync_sdk_v4.db-wal",
            "data/files/keva/global",
            "data/files/npth/RuntimeContext",
            "data/files/.msdata/mssdk/ml",
            "data/cache/ttnet_storage/prefs",
            "data/files/keva/repo/guard",
            "data/files/keva/repo/guard_data",
            "data/files/keva/repo/guard_fuse",
            "data/files/keva/repo/guard_fuse_data"
        )
    ) { path ->
        when {
            path.startsWith("data/shared_prefs/") && path.endsWith(".xml") ->
                path.substringAfterLast('/') in DOUYIN_LAIKE_PREF_NAMES
            path.startsWith("data/files/keva/repo/") -> {
                val rel = path.substringAfter("data/files/keva/repo/")
                DOUYIN_LAIKE_KEVA_DIRS.any { rel.startsWith("$it/") }
            }
            path in DOUYIN_LAIKE_DATABASE_NAMES.map { "data/databases/$it" } -> true
            path == "data/files/keva/global/keva.gxi" -> true
            path in DOUYIN_LAIKE_NPTH_NAMES.map { "data/files/npth/RuntimeContext/$it" } -> true
            path == "data/cache/ttnet_storage/prefs/local_prefs.json" -> true
            path.startsWith("data/files/.msdata/mssdk/ml/") -> true
            else -> false
        }
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

    private val ELE_RETAIL_PREF_NAMES = setOf(
        "ACCS_BIND_default.xml",
        "ACCS_SDK.xml",
        "ACCS_SDK_CHANNEL.xml",
        "AGOO_BIND.xml",
        "Agoo_AppStore.xml",
        "Alvin2.xml",
        "Cookies_Prefs.xml",
        "MtopConfigStore.xml",
        "ReactNativeContainer.xml",
        "SHAREDPREF_SETTING.xml",
        "SharedPreferenceAdiu.xml",
        "UTCommon.xml",
        "com.baidu.lbs.xinlingshou_preferences.xml",
        "me.ele.foundation.xml",
        "settings.xml",
        "sp_eleme_foundation.xml",
        "sp_waimai_cuid.xml",
        "ut_setting.xml"
    )

    private val MEITUAN_MERCHANT_PREF_NAMES = setOf(
        "B2CAccountSwitch.xml",
        "bookinguuid.xml",
        "com.sankuai.meituan.merchant_preferences.xml",
        "com.sankuai.meituan.merchantpike_tunnel.xml",
        "com.sankuai.meituan.merchantshark.xml",
        "dppushservice.xml",
        "merchant_default_share_pref.xml",
        "shared_msg_sdk.xml"
    )

    private val MEITUAN_MERCHANT_CIPS_MARKERS = listOf(
        "/merchant_biz_accounts/",
        "/merchantaccount/",
        "/epassport_cip_login_history_account/",
        "/epassport_cip_account/",
        "/epassport_cip_related_account/",
        "/epassport_common/",
        "/cip_epassport_version/",
        "/xm_",
        "/shop_info/",
        "/oneid_shared_oneid/",
        "/sec_dfp_settings/",
        "/jsbridge_storage/",
        "/shark",
        "/pike",
        "/dppush"
    )

    private val CTRIP_EBOOKING_PREF_NAMES = setOf(
        "CommonSp.xml",
        "EbkChat_eBookingChat.xml",
        "SOTPStorageManager.xml",
        "UserLocale.xml",
        "ad_auth.xml",
        "authStatus_com.Hotel.EBooking.xml",
        "comHotelEBooking.xml",
        "ctrip.store.main.xml",
        "site.xml",
        "sso_config_xf.xml",
        "ssoconfigs.xml"
    )

    private val CTRIP_EBOOKING_MMKV_NAMES = setOf(
        "ctrip_DeviceSerialInfoDomain",
        "ctrip_DeviceSerialInfoDomain.crc",
        "ctrip_SOTPStorageManager",
        "ctrip_SOTPStorageManager.crc",
        "ctrip__ctstorage__EBK",
        "ctrip__ctstorage__EBK.crc",
        "ctrip__ctstorage__TRIP_EBK_ACCOUNT",
        "ctrip__ctstorage__TRIP_EBK_ACCOUNT.crc",
        "ctrip__ctstorage__ebk_CtripUser",
        "ctrip__ctstorage__ebk_CtripUser.crc",
        "ctrip__ctstorage__ebk_DeviceDomain",
        "ctrip__ctstorage__ebk_DeviceDomain.crc",
        "ctrip__ctstorage__ebk_hotel",
        "ctrip__ctstorage__ebk_hotel.crc",
        "ctrip__ctstorage__ebk_login",
        "ctrip__ctstorage__ebk_login.crc",
        "ctrip_comm_businessCookieKey",
        "ctrip_comm_businessCookieKey.crc",
        "ctrip_timezone_sotp_sp",
        "ctrip_timezone_sotp_sp.crc"
    )

    private val DOUYIN_LAIKE_PREF_NAMES = setOf(
        "AccountBadger.xml",
        "PrefetchV2.xml",
        "SP_EXPERIMENT_CACHE.xml",
        "SP_EXPERIMENT_EXPOSURE_CACHE.xml",
        "CsrfTokenManager_sp.xml",
        "TTWebViewChromiumPrefs.xml",
        "TTWebView_Json_Config_Manager.xml",
        "WebViewBytedancePrefs.xml",
        "WebViewChromiumPrefs.xml",
        "__ctx_info.sp.xml",
        "__local_settings_data.sp.xml",
        "__settings_meta.sp.xml",
        "annie-x-storage.xml",
        "account_sdk_d_ticket.xml",
        "account_sdk_settings_sp.xml",
        "applog_stats.xml",
        "bcm_cache.xml",
        "bd_hybrid_monitor_global_shared_preference.xml",
        "bd_location_beacon_cache.xml",
        "btm_page_show.xml",
        "btm_process_resume_cache.xml",
        "byte_sync_settings.xml",
        "cj_pay_new_settings.xml",
        "com.bytedance.sdk.account_setting.xml",
        "com.ss.android.deviceregister.utils.Cdid.xml",
        "com.tt.miniapp.shared_prefs_prefix_bdp_sttpkg_exception.xml",
        "com.tt.miniapp.shared_prefs_prefix_new_current_user_sec_uid.xml",
        "com.tt.miniapp.shared_prefs_prefix_new_mini_app_storage.xml",
        "com.tt.miniapp.shared_prefs_prefix_new_offline_zip.xml",
        "com_bytedance_sdk_account_utils_common_request_cache_helper.xml",
        "com_ss_android_token_sp_host.xml",
        "cookieStore.xml",
        "ct_account_api_sdk.xml",
        "device-register-oaid-xiaomi.xml",
        "device_register_migrate_detector.xml",
        "device_register_oaid_refine.xml",
        "diff_settings.sp.xml",
        "cu_auth.xml",
        "d8b674543fc0b023b69f6a3f5a0f287d458ea204.xml",
        "imsdk_7405100999544684852_aid350593.xml",
        "last_sp_session.xml",
        "local_settings_sp.xml",
        "lsm_setting.sp.xml",
        "lsm_settings.sp.xml",
        "monitor_config.xml",
        "pref.xml",
        "push_multi_process_config.xml",
        "sec_link_config.xml",
        "ss_app_config.xml",
        "sync.xml",
        "ttnet_tnc_config.xml",
        "ug_install_op_pref.xml",
        "ug_install_settings_pref.xml",
        "upc_sdk_multi_process_sp.xml",
        "open_common.xml",
        "snssdk_openudid.xml",
        "sp_TicketGuardHelper.xml",
        "sp_TicketGuardManager.xml",
        "splash_ad_sp.xml",
        "ssoconfigs.xml",
        "token_shared_preference.xml",
        "tt_token_time.xml",
        "ttpush_local_setting.xml",
        "wschannel_multi_process_config.xml"
    )

    private val DOUYIN_LAIKE_KEVA_DIRS = setOf(
        "lsm_account",
        "account_manage_repo",
        "account_bind_data",
        "lsm_user_and_account_relation",
        "user_role_and_partner_account",
        "bd_possess_info",
        "ls-merchant",
        "ls_device_t_info",
        "fe-storage",
        "home_tabbar",
        "dynamic_home_struct",
        "jsbridge2-permission",
        "guard",
        "guard_data",
        "guard_fuse",
        "guard_fuse_data",
        "lsm_assistant",
        "ad_sdk_settings_cache_bytedance_ad_sdk",
        "gecko_channel_meta_new",
        "rule_settings_storage",
        "timon_cache_repo",
        "mine_header",
        "mine_tools"
    )

    private val DOUYIN_LAIKE_DATABASE_NAMES = setOf(
        "account_db",
        "account_db-journal",
        "verifystorage.db",
        "verifystorage.db-journal",
        "299467@bd_tea_agent.db",
        "299467@bd_tea_agent.db-journal",
        "ss_app_log.db",
        "ss_app_log.db-journal",
        "bd_sync_sdk_v4.db",
        "bd_sync_sdk_v4.db-shm",
        "bd_sync_sdk_v4.db-wal"
    )

    private val DOUYIN_LAIKE_NPTH_NAMES = setOf(
        "did",
        "device_uuid"
    )
}
