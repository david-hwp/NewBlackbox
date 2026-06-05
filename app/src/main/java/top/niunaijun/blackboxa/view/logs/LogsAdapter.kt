package top.niunaijun.blackboxa.view.logs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.LogEntry
import top.niunaijun.blackboxa.bean.LogType
import java.text.SimpleDateFormat
import java.util.*

class LogsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_DATE_HEADER = 0
        const val TYPE_LOG_ENTRY = 1
    }

    private var items: List<LogListItem> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(logs: List<LogEntry>) {
        val grouped = mutableListOf<LogListItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val yesterday = dateFormat.format(Date(System.currentTimeMillis() - 86400000))

        val groupedByDate = logs.groupBy { dateFormat.format(Date(it.timestamp)) }
            .toSortedMap(compareByDescending { it })

        groupedByDate.forEach { (date, entries) ->
            val displayDate = when (date) {
                today -> "今天"
                yesterday -> "昨天"
                else -> date
            }
            grouped.add(LogListItem.DateHeader(displayDate))
            entries.sortedByDescending { it.timestamp }.forEach {
                grouped.add(LogListItem.LogItem(it))
            }
        }

        items = grouped
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is LogListItem.DateHeader -> TYPE_DATE_HEADER
            is LogListItem.LogItem -> TYPE_LOG_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_log_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_log_entry, parent, false)
                LogEntryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LogListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.date)
            is LogListItem.LogItem -> (holder as LogEntryViewHolder).bind(item.log)
        }
    }

    override fun getItemCount(): Int = items.size

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

        fun bind(date: String) {
            tvDate.text = date
        }
    }

    inner class LogEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvBadgeText: TextView = itemView.findViewById(R.id.tvBadgeText)
        private val tvTypeName: TextView = itemView.findViewById(R.id.tvTypeName)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)

        fun bind(log: LogEntry) {
            val (badgeText, typeName, amountPrefix, badgeBg, amountColor) = when (log.type) {
                LogType.CONSUME -> {
                    val bg = itemView.context.getDrawable(R.drawable.bg_log_badge_consume)
                    val color = itemView.context.getColor(R.color.duodian_info)
                    Quadruple("消", "算力消耗", "-", bg, color)
                }
                LogType.OUT -> {
                    val bg = itemView.context.getDrawable(R.drawable.bg_log_badge_out)
                    val color = itemView.context.getColor(R.color.duodian_warning)
                    Quadruple("转", "算力转出", "-", bg, color)
                }
                LogType.IN -> {
                    val bg = itemView.context.getDrawable(R.drawable.bg_log_badge_in)
                    val color = itemView.context.getColor(R.color.duodian_primary)
                    Quadruple("收", "算力转入", "+", bg, color)
                }
            }

            tvBadgeText.text = badgeText
            tvBadgeText.background = badgeBg
            tvTypeName.text = typeName
            tvDescription.text = log.description
            tvAmount.text = "$amountPrefix${log.amount}"
            tvAmount.setTextColor(amountColor)
            tvTime.text = timeFormat.format(Date(log.timestamp))
        }
    }

    private data class Quadruple<A, B, C, D, E>(
        val first: A, val second: B, val third: C, val fourth: D, val fifth: E
    )
}
