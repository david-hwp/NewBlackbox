package top.niunaijun.blackbox.engine

import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.entity.pm.WechatShareTarget
import top.niunaijun.blackbox.utils.Slog
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.math.min

object WechatShareTargetResolver {
    private const val TAG = "WechatShareTargetResolver"
    private const val SYNC_MMKV_PREFIX = "SyncMMKV_"
    private const val MAX_PARSE_WINDOW = 768
    private const val MAX_SYNC_MMKV_BYTES = 8 * 1024 * 1024

    fun resolve(target: WechatShareTarget?): WechatShareTarget? {
        val captured = target ?: return null
        val receiverId = captured.receiverId?.takeIf { it.isNotBlank() } ?: return captured
        val packageName = captured.packageName?.takeIf { it.isNotBlank() } ?: return captured
        val type = inferType(receiverId)
        val resolved = resolveName(packageName, captured.userId, receiverId, type)
        return WechatShareTarget(
            receiverId,
            resolved.name,
            type,
            packageName,
            captured.userId,
            captured.capturedAt,
            listOfNotNull(captured.evidence, resolved.evidence).joinToString("|")
        )
    }

    private fun resolveName(
        packageName: String,
        userId: Int,
        receiverId: String,
        receiverType: String
    ): ResolveResult {
        val mmkvDir = File(BEnvironment.getDataDir(packageName, userId), "files/mmkv")
        val files = mmkvDir.listFiles { file ->
            file.isFile && file.name.startsWith(SYNC_MMKV_PREFIX) && file.length() in 1..MAX_SYNC_MMKV_BYTES
        }?.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.length() }).orEmpty()
        if (files.isEmpty()) {
            return ResolveResult(null, "sync-mmkv:not-found")
        }

        val idBytes = receiverId.toByteArray(StandardCharsets.UTF_8)
        for (file in files) {
            val bytes = runCatching { file.readBytes() }.getOrElse {
                Slog.w(TAG, "read ${file.name} failed: ${it.message}")
                null
            } ?: continue
            val best = findNameMatches(bytes, idBytes, receiverType).maxByOrNull { it.score }
            if (best != null) {
                return ResolveResult(best.name, "sync-mmkv:${file.name}:offset=${best.offset}:field=${best.field}")
            }
        }
        return ResolveResult(null, "sync-mmkv:name-not-found")
    }

    private fun findNameMatches(bytes: ByteArray, idBytes: ByteArray, receiverType: String): List<NameMatch> {
        val matches = mutableListOf<NameMatch>()
        var start = 0
        while (start < bytes.size) {
            val offset = indexOf(bytes, idBytes, start)
            if (offset < 0) {
                break
            }
            matches += parseFieldsAfterReceiver(bytes, offset, idBytes.size, receiverType)
            start = offset + idBytes.size
        }
        return matches
    }

    private fun parseFieldsAfterReceiver(
        bytes: ByteArray,
        receiverOffset: Int,
        receiverLength: Int,
        receiverType: String
    ): List<NameMatch> {
        val matches = mutableListOf<NameMatch>()
        var pos = receiverOffset + receiverLength
        val limit = min(bytes.size, pos + MAX_PARSE_WINDOW)
        var parsedFields = 0
        while (pos < limit && parsedFields < 24) {
            val tag = readVarint(bytes, pos, limit) ?: break
            pos = tag.next
            val field = tag.value ushr 3
            val wireType = tag.value and 0x7
            when (wireType) {
                0 -> {
                    val skipped = readVarint(bytes, pos, limit) ?: break
                    pos = skipped.next
                }
                1 -> pos += 8
                2 -> {
                    val lengthValue = readVarint(bytes, pos, limit) ?: break
                    val length = lengthValue.value
                    pos = lengthValue.next
                    if (length < 0 || pos + length > limit) {
                        break
                    }
                    readNestedString(bytes, pos, length)
                        ?.let { cleanCandidate(it) }
                        ?.takeIf { isUsableName(it) }
                        ?.let { name ->
                            matches += NameMatch(
                                name,
                                field,
                                receiverOffset,
                                scoreCandidate(name, field, receiverType)
                            )
                        }
                    pos += length
                }
                5 -> pos += 4
                else -> break
            }
            parsedFields++
        }
        return matches
    }

    private fun readNestedString(bytes: ByteArray, start: Int, length: Int): String? {
        if (length <= 0) {
            return null
        }
        val end = start + length
        if (start < end && (bytes[start].toInt() and 0xff) == 0x0a) {
            val innerLength = readVarint(bytes, start + 1, end)
            if (innerLength != null) {
                val innerStart = innerLength.next
                val innerEnd = innerStart + innerLength.value
                if (innerLength.value > 0 && innerEnd <= end) {
                    decodeUtf8(bytes, innerStart, innerLength.value)?.let { return it }
                }
            }
        }
        return decodeUtf8(bytes, start, length)
    }

    private fun decodeUtf8(bytes: ByteArray, start: Int, length: Int): String? {
        return runCatching {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes, start, length)).toString()
        }.getOrNull()
    }

    private fun scoreCandidate(name: String, field: Int, receiverType: String): Int {
        var score = 0
        if (receiverType == WechatShareTarget.TYPE_GROUP) {
            score += when (field) {
                2 -> 120
                10 -> 80
                else -> 10
            }
        } else {
            score += when (field) {
                10 -> 140
                2 -> 90
                else -> 10
            }
        }
        if (name.any { it.code > 127 }) {
            score += 20
        }
        if (name.length in 2..24) {
            score += 8
        }
        if (name.all { it.isLetterOrDigit() }) {
            score -= 25
        }
        return score
    }

    private fun isUsableName(value: String): Boolean {
        if (value.isBlank() || value.length > 80) {
            return false
        }
        val lower = value.lowercase()
        if (value.contains("@chatroom") ||
            lower.startsWith("wxid_") ||
            lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            value.contains("<") ||
            value.contains(">") ||
            value.count { it == '/' } >= 2
        ) {
            return false
        }
        return value.any { it.isLetterOrDigit() || it.code > 127 }
    }

    private fun cleanCandidate(value: String): String? {
        val cleaned = value
            .replace('\u0000', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun inferType(receiverId: String): String {
        return when {
            receiverId.endsWith("@chatroom") -> WechatShareTarget.TYPE_GROUP
            receiverId.isNotBlank() -> WechatShareTarget.TYPE_CONTACT
            else -> WechatShareTarget.TYPE_UNKNOWN
        }
    }

    private fun indexOf(bytes: ByteArray, pattern: ByteArray, startIndex: Int): Int {
        if (pattern.isEmpty() || bytes.isEmpty() || startIndex >= bytes.size) {
            return -1
        }
        var i = startIndex.coerceAtLeast(0)
        val max = bytes.size - pattern.size
        while (i <= max) {
            var j = 0
            while (j < pattern.size && bytes[i + j] == pattern[j]) {
                j++
            }
            if (j == pattern.size) {
                return i
            }
            i++
        }
        return -1
    }

    private fun readVarint(bytes: ByteArray, start: Int, limit: Int): Varint? {
        var result = 0
        var shift = 0
        var pos = start
        while (pos < limit && shift < 32) {
            val b = bytes[pos].toInt() and 0xff
            result = result or ((b and 0x7f) shl shift)
            pos++
            if ((b and 0x80) == 0) {
                return Varint(result, pos)
            }
            shift += 7
        }
        return null
    }

    private data class ResolveResult(val name: String?, val evidence: String)
    private data class NameMatch(val name: String, val field: Int, val offset: Int, val score: Int)
    private data class Varint(val value: Int, val next: Int)
}
