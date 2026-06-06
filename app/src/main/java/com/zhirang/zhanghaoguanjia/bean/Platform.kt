package com.zhirang.zhanghaoguanjia.bean

class Platform private constructor(
    val id: String,
    val displayName: String
) {
    override fun equals(other: Any?): Boolean {
        return other is Platform && other.id == id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = id

    companion object {
        val MEITUAN = Platform("meituan", "美团")
        val TAOBAO = Platform("taobao", "淘宝")
        val JD = Platform("jd", "京东")
        val KUAISHOU = Platform("kuaishou", "快手")
        val XIAOHONGSHU = Platform("xiaohongshu", "小红书")
        val ALI = Platform("ali", "阿里本地")

        private val defaults = listOf(MEITUAN, TAOBAO, JD, KUAISHOU, XIAOHONGSHU, ALI)

        fun fromId(id: String): Platform {
            val normalized = id.trim()
            return defaults.firstOrNull { it.id == normalized }
                ?: Platform(normalized, normalized.ifBlank { "未知平台" })
        }

        fun from(id: String, displayName: String?): Platform {
            val normalized = id.trim()
            val known = defaults.firstOrNull { it.id == normalized }
            val name = displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: known?.displayName
                ?: normalized.ifBlank { "未知平台" }
            if (known != null && known.displayName == name) {
                return known
            }
            return Platform(normalized, name)
        }
    }
}
