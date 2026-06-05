package top.niunaijun.blackboxa.view.logs

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.niunaijun.blackboxa.bean.LogEntry
import top.niunaijun.blackboxa.bean.LogType
import top.niunaijun.blackboxa.bean.dto.LogEntryDto
import top.niunaijun.blackboxa.data.LogRepository
import top.niunaijun.blackboxa.network.PagedResult
import top.niunaijun.blackboxa.network.RetrofitClient
import java.text.SimpleDateFormat
import java.util.*

class LogsViewModel : ViewModel() {

    private val logRepository = LogRepository(RetrofitClient.apiService)

    private val _logsLiveData = MutableLiveData<List<LogEntryDto>>()
    val logsLiveData: LiveData<List<LogEntryDto>> = _logsLiveData

    private val _localLogsLiveData = MutableLiveData<List<LogEntry>>()
    val localLogsLiveData: LiveData<List<LogEntry>> = _localLogsLiveData

    val hasMoreLiveData = MutableLiveData<Boolean>()
    val errorLiveData = MutableLiveData<String>()

    private var currentPage = 1
    private val pageSize = 20

    private val allLogs = mutableListOf<LogEntry>()

    fun loadLogs(type: LogType? = null) {
        // Load local mock data for UI display
        if (allLogs.isEmpty()) {
            allLogs.addAll(createMockLogs())
        }
        val filtered = if (type == null) {
            allLogs
        } else {
            allLogs.filter { it.type == type }
        }
        _localLogsLiveData.value = filtered.sortedByDescending { it.timestamp }

        // Also load from API
        viewModelScope.launch {
            currentPage = 1
            val apiType = type?.name?.lowercase()
            val result = logRepository.getMyLogs(apiType, currentPage, pageSize)
            result.fold(
                onSuccess = { pagedResult: PagedResult<LogEntryDto> ->
                    _logsLiveData.value = pagedResult.list
                    hasMoreLiveData.value = pagedResult.list.size >= pageSize
                },
                onFailure = { e: Throwable ->
                    errorLiveData.value = e.message
                }
            )
        }
    }

    fun loadMore(type: LogType? = null) {
        viewModelScope.launch {
            currentPage++
            val apiType = type?.name?.lowercase()
            val result = logRepository.getMyLogs(apiType, currentPage, pageSize)
            result.fold(
                onSuccess = { pagedResult: PagedResult<LogEntryDto> ->
                    val current = _logsLiveData.value ?: emptyList()
                    _logsLiveData.value = current + pagedResult.list
                    hasMoreLiveData.value = pagedResult.list.size >= pageSize
                },
                onFailure = { e: Throwable ->
                    errorLiveData.value = e.message
                }
            )
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

    private fun createMockLogs(): List<LogEntry> {
        val now = System.currentTimeMillis()
        return listOf(
            LogEntry(1, LogType.CONSUME, 10, "美团外卖 - 张三的店", now - 3600000),
            LogEntry(2, LogType.CONSUME, 10, "淘宝闪购 - 李四的店", now - 7200000),
            LogEntry(3, LogType.OUT, 100, "转给 138****1234", now - 18000000),
            LogEntry(4, LogType.IN, 500, "来自 138****5678", now - 86400000),
            LogEntry(5, LogType.CONSUME, 10, "京东秒送 - 王五的店", now - 90000000),
            LogEntry(6, LogType.OUT, 200, "转给 138****9999", now - 172800000),
            LogEntry(7, LogType.IN, 1000, "充值", now - 259200000),
            LogEntry(8, LogType.CONSUME, 10, "快手团购 - 赵六的店", now - 300000000),
            LogEntry(9, LogType.CONSUME, 10, "小红书 - 孙七的店", now - 345600000),
            LogEntry(10, LogType.IN, 200, "来自 138****1111", now - 432000000)
        )
    }
}

sealed class LogListItem {
    data class DateHeader(val date: String) : LogListItem()
    data class LogItem(val log: LogEntry) : LogListItem()
}
