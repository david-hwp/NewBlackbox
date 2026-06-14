package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.network.ApiService

class SystemParameterRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getAppParameters(): Result<Map<String, String>> =
        safeApiCall { api.getAppSystemParameters() }

    companion object {
        const val APP_MENU_GIFT_COMPUTE_LABEL = "app.menu.gift_compute.label"
        const val APP_MENU_RECLAIM_COMPUTE_LABEL = "app.menu.reclaim_compute.label"
        const val APP_MENU_GIFT_PHONE_MINUTES_LABEL = "app.menu.gift_phone_minutes.label"
        const val APP_MENU_RECLAIM_PHONE_MINUTES_LABEL = "app.menu.reclaim_phone_minutes.label"
        const val APP_MENU_TRANSACTION_LOGS_LABEL = "app.menu.transaction_logs.label"
        const val APP_SHOP_FEATURE_BAD_REVIEW_LOCATION_LABEL = "app.shop_feature.bad_review_location.label"
        const val APP_SHOP_FEATURE_BAD_REVIEW_LOCATION_LINE1 = "app.shop_feature.bad_review_location.line1"
        const val APP_SHOP_FEATURE_BAD_REVIEW_LOCATION_LINE2 = "app.shop_feature.bad_review_location.line2"
        const val APP_SHOP_FEATURE_BUSINESS_REPORT_LABEL = "app.shop_feature.business_report.label"
        const val APP_SHOP_FEATURE_BUSINESS_REPORT_LINE1 = "app.shop_feature.business_report.line1"
        const val APP_SHOP_FEATURE_BUSINESS_REPORT_LINE2 = "app.shop_feature.business_report.line2"
        const val APP_SHOP_FEATURE_OUTBOUND_PRAISE_LABEL = "app.shop_feature.outbound_praise.label"
        const val APP_SHOP_FEATURE_OUTBOUND_PRAISE_LINE1 = "app.shop_feature.outbound_praise.line1"
        const val APP_SHOP_FEATURE_OUTBOUND_PRAISE_LINE2 = "app.shop_feature.outbound_praise.line2"
        const val APP_SHOP_FEATURE_REVIEW_APPEAL_LABEL = "app.shop_feature.review_appeal.label"
        const val APP_SHOP_FEATURE_REVIEW_APPEAL_LINE1 = "app.shop_feature.review_appeal.line1"
        const val APP_SHOP_FEATURE_REVIEW_APPEAL_LINE2 = "app.shop_feature.review_appeal.line2"
        const val APP_SHOP_FEATURE_PRIVATE_TRAFFIC_LABEL = "app.shop_feature.private_traffic.label"
        const val APP_SHOP_FEATURE_PRIVATE_TRAFFIC_LINE1 = "app.shop_feature.private_traffic.line1"
        const val APP_SHOP_FEATURE_PRIVATE_TRAFFIC_LINE2 = "app.shop_feature.private_traffic.line2"
    }
}
