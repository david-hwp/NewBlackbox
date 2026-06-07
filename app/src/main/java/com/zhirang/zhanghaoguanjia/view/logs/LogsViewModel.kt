package com.zhirang.zhanghaoguanjia.view.logs

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.bean.LogEntry
import com.zhirang.zhanghaoguanjia.bean.LogType
import com.zhirang.zhanghaoguanjia.bean.dto.LogEntryDto
import com.zhirang.zhanghaoguanjia.data.LogRepository
import com.zhirang.zhanghaoguanjia.network.PagedResult
import com.zhirang.zhanghaoguanjia.network.RetrofitClient
import com.zhirang.zhanghaoguanjia.util.PlatformRegistry
import java.text.SimpleDateFormat
import java.util.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class LogsViewModel : ViewModel() {

    private val logRepository = LogRepository(RetrofitClient.apiService)

    private val _localLogsLiveData = MutableLiveData<List<LogEntry>>()
    val localLogsLiveData: LiveData<List<LogEntry>> = _localLogsLiveData

    val hasMoreLiveData = MutableLiveData<Boolean>()
    val errorLiveData = MutableLiveData<String>()

    private var currentPage = 1
    private val pageSize = 20
    private var isLoading = false
    private var hasMore = true

    fun loadLogs(type: LogType? = null) {
        viewModelScope.launch {
            if (isLoading) return@launch
            isLoading = true
            currentPage = 1
            val apiType = type?.name?.lowercase()
            val result = logRepository.getMyLogs(apiType, currentPage, pageSize)
            result.fold(
                onSuccess = { pagedResult: PagedResult<LogEntryDto> ->
                    val logs = pagedResult.items().map { it.toLogEntry() }
                    _localLogsLiveData.value = logs
                    hasMore = logs.size >= pageSize
                    hasMoreLiveData.value = hasMore
                },
                onFailure = { e: Throwable ->
                    _localLogsLiveData.value = emptyList()
                    hasMore = false
                    hasMoreLiveData.value = false
                    errorLiveData.value = e.message
                }
            )
            isLoading = false
        }
    }

    fun loadMore(type: LogType? = null) {
        viewModelScope.launch {
            if (isLoading || !hasMore) return@launch
            isLoading = true
            currentPage++
            val apiType = type?.name?.lowercase()
            val result = logRepository.getMyLogs(apiType, currentPage, pageSize)
            result.fold(
                onSuccess = { pagedResult: PagedResult<LogEntryDto> ->
                    val current = _localLogsLiveData.value ?: emptyList()
                    val nextLogs = pagedResult.items().map { it.toLogEntry() }
                    _localLogsLiveData.value = current + nextLogs
                    hasMore = nextLogs.size >= pageSize
                    hasMoreLiveData.value = hasMore
                },
                onFailure = { e: Throwable ->
                    currentPage--
                    errorLiveData.value = e.message
                }
            )
            isLoading = false
        }
    }

    fun groupByDate(logs: List<LogEntry>): List<LogListItem> {
        val result = mutableListOf<LogListItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))

        val grouped = logs.groupBy { dateFormat.format(Date(it.timestamp)) }
            .toSortedMap(compareByDescending { it })

        grouped.forEach { (date, entries) ->
            val displayDate = when (date) {
                today -> "今天"
                yesterday -> "昨天"
                else -> date
            }
            result.add(LogListItem.DateHeader(displayDate))
            entries.sortedByDescending { it.timestamp }.forEach {
                result.add(LogListItem.LogItem(it))
            }
        }

        return result
    }

    private fun LogEntryDto.toLogEntry(): LogEntry {
        val logType = runCatching { LogType.valueOf(type.uppercase()) }.getOrDefault(LogType.CONSUME)
        val description = when (logType) {
            LogType.CONSUME -> listOfNotNull(platformDisplayName(platform), shopName).joinToString(" - ").ifBlank { "算力消耗" }
            LogType.OUT -> "转给 ${fromPhone ?: toPhone ?: "-"}"
            LogType.IN -> "来自 ${fromPhone ?: toPhone ?: "-"}"
        }
        return LogEntry(
            id = id,
            type = logType,
            amount = kotlin.math.abs(amount),
            description = description,
            timestamp = parseCreatedAt(createdAt)
        )
    }

    private fun platformDisplayName(platform: String?): String? {
        val value = platform?.trim()?.takeIf { it.isNotBlank() } ?: return null
        PlatformRegistry.get(value)?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
        return when (value.lowercase(Locale.ROOT)) {
            "jd" -> "京东秒送"
            "meituan" -> "美团"
            "taobao" -> "淘宝"
            "kuaishou" -> "快手"
            "xiaohongshu" -> "小红书"
            "ali" -> "阿里本地"
            else -> value
        }
    }

    private fun parseCreatedAt(value: String): Long {
        return runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())
    }
}

sealed class LogListItem {
    data class DateHeader(val date: String) : LogListItem()
    data class LogItem(val log: LogEntry) : LogListItem()
}
