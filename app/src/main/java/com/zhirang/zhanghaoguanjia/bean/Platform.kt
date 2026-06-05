package com.zhirang.zhanghaoguanjia.bean

enum class Platform(
    val id: String,
    val displayName: String
) {
    MEITUAN("meituan", "美团"),
    TAOBAO("taobao", "淘宝"),
    JD("jd", "京东"),
    KUAISHOU("kuaishou", "快手"),
    XIAOHONGSHU("xiaohongshu", "小红书"),
    ALI("ali", "阿里本地");

    companion object {
        fun fromId(id: String): Platform = values().find { it.id == id } ?: MEITUAN
    }
}
