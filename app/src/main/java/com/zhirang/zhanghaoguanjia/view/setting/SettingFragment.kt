package com.zhirang.zhanghaoguanjia.view.setting

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.app.AppManager
import com.zhirang.zhanghaoguanjia.engine.EngineProxy
import com.zhirang.zhanghaoguanjia.util.toast
import com.zhirang.zhanghaoguanjia.view.gms.GmsManagerActivity

class SettingFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting, rootKey)

        initGms()

        invalidHideState {
            val rootHidePreference: SwitchPreferenceCompat = (findPreference("root_hide")!!)
            val hideRoot = AppManager.mBlackBoxLoader.hideRoot()
            rootHidePreference.setDefaultValue(hideRoot)
            rootHidePreference.isChecked = hideRoot
            rootHidePreference
        }

        invalidHideState {
            val daemonPreference: Preference = (findPreference("daemon_enable")!!)
            val mDaemonEnable = AppManager.mBlackBoxLoader.daemonEnable()
            daemonPreference.setDefaultValue(mDaemonEnable)
            daemonPreference
        }

        invalidHideState {
            val vpnPreference: Preference = (findPreference("use_vpn_network")!!)
            val mUseVpnNetwork = AppManager.mBlackBoxLoader.useVpnNetwork()
            vpnPreference.setDefaultValue(mUseVpnNetwork)
            vpnPreference
        }

        invalidHideState {
            val disableFlagSecurePreference: Preference = (findPreference("disable_flag_secure")!!)
            val mDisableFlagSecure = AppManager.mBlackBoxLoader.disableFlagSecure()
            disableFlagSecurePreference.setDefaultValue(mDisableFlagSecure)
            disableFlagSecurePreference
        }

        invalidHideState {
            val singleInstancePreference: SwitchPreferenceCompat = (findPreference("single_instance_mode")!!)
            val mSingleInstanceMode = AppManager.mBlackBoxLoader.singleInstanceMode()
            singleInstancePreference.setDefaultValue(mSingleInstanceMode)
            singleInstancePreference.isChecked = mSingleInstanceMode
            singleInstancePreference
        }

        initSendLogs()
    }

    private fun initGms() {
        val gmsManagerPreference: Preference = (findPreference("gms_manager")!!)

        if (EngineProxy.isSupportGms()) {
            gmsManagerPreference.setOnPreferenceClickListener {
                GmsManagerActivity.start(requireContext())
                true
            }
        } else {
            gmsManagerPreference.summary = getString(R.string.no_gms)
            gmsManagerPreference.isEnabled = false
        }
    }

    private fun invalidHideState(block: () -> Preference) {
        val pref = block()
        pref.setOnPreferenceChangeListener { preference, newValue ->
            val tmpHide = (newValue == true)
            when (preference.key) {
                "root_hide" -> {
                    AppManager.mBlackBoxLoader.invalidHideRoot(tmpHide)
                }
                "daemon_enable" -> {
                    AppManager.mBlackBoxLoader.invalidDaemonEnable(tmpHide)
                }
                "use_vpn_network" -> {
                    AppManager.mBlackBoxLoader.invalidUseVpnNetwork(tmpHide)
                }
                "disable_flag_secure" -> {
                    AppManager.mBlackBoxLoader.invalidDisableFlagSecure(tmpHide)
                }
                "single_instance_mode" -> {
                    AppManager.mBlackBoxLoader.invalidSingleInstanceMode(tmpHide)
                }
            }

            toast(R.string.restart_module)
            return@setOnPreferenceChangeListener true
        }
    }
    private fun initSendLogs() {
        val sendLogsPreference: Preference? = findPreference("send_logs")
        sendLogsPreference?.setOnPreferenceClickListener {
            it.isEnabled = false
            EngineProxy.sendLogs("Manual Log Upload from Settings", true)
            toast("Sending logs... (Check notifications for status)")
            // Re-enable after a delay since IPC callback is not available
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendLogsPreference?.isEnabled = true
            }, 5000)
            true
        }
    }
}
