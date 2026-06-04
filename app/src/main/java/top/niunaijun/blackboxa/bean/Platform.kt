package top.niunaijun.blackboxa.bean

enum class Platform(
    val id: String,
    val displayName: String,
    val packageName: String? = null
) {
    MEITUAN("meituan", "美团外卖", "com.sankuai.meituan.merchant"),
    TAOBAO("taobao", "淘宝闪购", null),
    JD("jd", "京东秒送", "com.jd.mrd.jingming"),
    KUAISHOU("kuaishou", "快手团购", null),
    XIAOHONGSHU("xiaohongshu", "小红书", null),
    ALI("ali", "阿里本地", null);

    companion object {
        fun fromId(id: String): Platform = values().find { it.id == id } ?: MEITUAN
    }
}
