package com.zhirang.zhanghaoguanjia.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.app.AppManager
import com.zhirang.zhanghaoguanjia.bean.AppInfo
import com.zhirang.zhanghaoguanjia.util.ContextUtil.openAppSystemSettings
import com.zhirang.zhanghaoguanjia.view.main.ShortcutActivity


object ShortcutUtil {


    
    fun createShortcut(context: Context,userID: Int, info: AppInfo) {

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val labelName = info.name + userID
            val intent = Intent(context, ShortcutActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .putExtra("pkg", info.packageName)
                .putExtra("userId", userID)
            MaterialDialog(context).show {
                title(res = R.string.app_shortcut)
                input(
                    hintRes = R.string.shortcut_name,
                    prefill = labelName
                ) { _, input ->

                    val iconBitmap = info.icon?.toBitmap()
                    val shortcutBuilder = ShortcutInfoCompat.Builder(context, info.packageName + userID)
                        .setIntent(intent)
                        .setShortLabel(input)
                        .setLongLabel(input)
                    if (iconBitmap != null) {
                        shortcutBuilder.setIcon(IconCompat.createWithBitmap(iconBitmap))
                    }
                    val shortcutInfo: ShortcutInfoCompat = shortcutBuilder.build()

                    ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
                    showAllowPermissionDialog(context)
                }
                positiveButton(R.string.done)
                negativeButton(R.string.cancel)
            }

        } else {
            toast(R.string.cannot_create_shortcut)
        }
    }

    private fun showAllowPermissionDialog(context: Context){
        if (!AppManager.mBlackBoxLoader.showShortcutPermissionDialog()){
            return
        }

        MaterialDialog(context).show {
            title(R.string.try_add_shortcut)
            message(R.string.add_shortcut_fail_msg)
            positiveButton(R.string.done)
            negativeButton(R.string.permission_setting){
                App.getContext().openAppSystemSettings()
            }

            neutralButton(R.string.no_reminders){
                AppManager.mBlackBoxLoader.invalidShortcutPermissionDialog(false)
            }
        }

    }
}