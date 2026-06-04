package top.niunaijun.blackboxa.bean

data class LogEntry(
    val id: Long,
    val type: LogType,
    val amount: Int,
    val description: String,
    val timestamp: Long
)

enum class LogType { CONSUME, OUT, IN }
