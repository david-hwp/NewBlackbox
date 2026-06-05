package top.niunaijun.blackboxa.view.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.dto.EngineVersionDto

class EngineVersionAdapter(
    currentVersionCode: Int,
    private val onClick: (EngineVersionDto) -> Unit
) : RecyclerView.Adapter<EngineVersionAdapter.VH>() {

    private var versions: List<EngineVersionDto> = emptyList()
    private var currentVersionCode: Int = currentVersionCode

    fun submitList(newVersions: List<EngineVersionDto>) {
        versions = newVersions
        notifyDataSetChanged()
    }

    fun updateCurrentVersion(versionCode: Int) {
        if (currentVersionCode == versionCode) return
        currentVersionCode = versionCode
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_engine_version, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(versions[position])
    }

    override fun getItemCount(): Int = versions.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvVersionTitle)
        private val status: TextView = itemView.findViewById(R.id.tvVersionStatus)

        fun bind(version: EngineVersionDto) {
            val isCurrent = version.versionCode == currentVersionCode
            val isDowngrade = currentVersionCode > 0 && version.versionCode < currentVersionCode
            val canInstall = !isCurrent && !isDowngrade
            title.text = "${version.versionName} (${version.versionCode})"
            status.text = when {
                isCurrent -> "当前使用"
                isDowngrade -> "低于当前版本"
                else -> "点击升级"
            }
            itemView.isEnabled = canInstall
            itemView.alpha = if (canInstall) 1f else 0.65f
            itemView.setOnClickListener {
                if (canInstall) onClick(version)
            }
        }
    }
}
