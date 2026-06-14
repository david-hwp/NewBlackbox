package com.zhirang.zhanghaoguanjia.data

import com.zhirang.zhanghaoguanjia.network.ApiService

class SystemParameterRepository(api: ApiService) : BaseRepository(api) {

    suspend fun getAppParameters(): Result<Map<String, String>> =
        safeApiCall { api.getAppSystemParameters() }

    companion object {
        const val REGISTER_TRIAL_SUBSCRIPTION_DAYS = "register.trial.subscription.days"
        const val APP_MENU_GIFT_COMPUTE_LABEL = "app.menu.gift_compute.label"
        const val APP_MENU_RECLAIM_COMPUTE_LABEL = "app.menu.reclaim_compute.label"
        const val APP_MENU_GIFT_PHONE_MINUTES_LABEL = "app.menu.gift_phone_minutes.label"
        const val APP_MENU_RECLAIM_PHONE_MINUTES_LABEL = "app.menu.reclaim_phone_minutes.label"
        const val APP_MENU_TRANSACTION_LOGS_LABEL = "app.menu.transaction_logs.label"
    }
}
