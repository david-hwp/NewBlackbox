package com.zhirang.zhanghaoguanjia.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import top.niunaijun.blackbox.entity.pm.WechatShareTarget
import java.io.File

object WechatShareTargetStore {
    private const val TAG = "WechatShareTargetStore"
    private const val ROOT_DIR = "wechat-share-targets"
    private const val LAST_FILE = "last.json"

    data class Target(
        val systemShopId: Long,
        val receiverId: String,
        val receiverName: String?,
        val receiverType: String,
        val packageName: String,
        val localVirtualUserId: Int,
        val capturedAtEpochMillis: Long,
        val evidence: String?
    ) {
        val receiverTypeLabel: String
            get() = when (receiverType) {
                WechatShareTarget.TYPE_GROUP -> "群聊"
                WechatShareTarget.TYPE_CONTACT -> "个人微信"
                else -> "未知"
            }
    }

    fun save(context: Context, systemShopId: Long, target: WechatShareTarget): File? {
        if (systemShopId <= 0) {
            Log.w(TAG, "skip save: invalid systemShopId=$systemShopId")
            return null
        }
        val packageName = target.packageName?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "skip save: empty packageName systemShopId=$systemShopId target=$target")
            return null
        }
        if (target.userId < 0 || target.receiverId.isNullOrBlank()) {
            Log.w(TAG, "skip save: invalid target systemShopId=$systemShopId target=$target")
            return null
        }
        val json = JSONObject()
            .put("systemShopId", systemShopId)
            .put("receiverId", target.receiverId)
            .put("receiverName", target.receiverName)
            .put("receiverType", target.receiverType)
            .put("packageName", packageName)
            .put("localVirtualUserId", target.userId)
            .put("capturedAtEpochMillis", target.capturedAt)
            .put("evidence", target.evidence)
            .put("savedAtEpochMillis", System.currentTimeMillis())
        return runCatching {
            lastFile(context, systemShopId).apply {
                parentFile?.mkdirs()
                writeText(json.toString(), Charsets.UTF_8)
                Log.d(TAG, "saved target systemShopId=$systemShopId path=$absolutePath receiver=${target.receiverId} name=${target.receiverName}")
            }
        }.onFailure {
            Log.w(TAG, "save target failed systemShopId=$systemShopId target=$target", it)
        }.getOrNull()
    }

    fun readLast(context: Context, systemShopId: Long): JSONObject? {
        return runCatching {
            val file = lastFile(context, systemShopId)
            if (!file.exists()) {
                null
            } else {
                JSONObject(file.readText(Charsets.UTF_8))
            }
        }.onFailure {
            Log.w(TAG, "read target failed systemShopId=$systemShopId", it)
        }.getOrNull()
    }

    fun readTarget(context: Context, systemShopId: Long): Target? {
        val json = readLast(context, systemShopId) ?: return null
        val receiverId = json.optString("receiverId").takeIf { it.isNotBlank() } ?: return null
        val packageName = json.optString("packageName").takeIf { it.isNotBlank() } ?: return null
        return Target(
            systemShopId = json.optLong("systemShopId", systemShopId).takeIf { it > 0 } ?: systemShopId,
            receiverId = receiverId,
            receiverName = json.optString("receiverName").takeIf { it.isNotBlank() },
            receiverType = json.optString("receiverType").takeIf { it.isNotBlank() }
                ?: WechatShareTarget.TYPE_UNKNOWN,
            packageName = packageName,
            localVirtualUserId = json.optInt("localVirtualUserId", -1),
            capturedAtEpochMillis = json.optLong("capturedAtEpochMillis", 0L),
            evidence = json.optString("evidence").takeIf { it.isNotBlank() }
        )
    }

    private fun lastFile(context: Context, systemShopId: Long): File {
        return File(context.filesDir, "$ROOT_DIR/shops/$systemShopId/$LAST_FILE")
    }
}
