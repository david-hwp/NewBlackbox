package com.zhirang.zhanghaoguanjia.view.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.engine.EngineInstaller
import com.zhirang.zhanghaoguanjia.engine.EngineVersionChecker

/**
 * EngineUpgradeDialog shows upgrade information and handles user interaction.
 * Supports both force and optional upgrades.
 */
class EngineUpgradeDialog : DialogFragment() {

    companion object {
        private const val TAG = "EngineUpgradeDialog"
        private const val ARG_VERSION_CODE = "version_code"
        private const val ARG_VERSION_NAME = "version_name"
        private const val ARG_CHANGELOG = "changelog"
        private const val ARG_IS_FORCE = "is_force"
        private const val ARG_CURRENT_VERSION = "current_version"

        /**
         * Show the upgrade dialog.
         *
         * @param fragmentManager The FragmentManager to use
         * @param upgradeInfo The upgrade information
         * @param currentVersion The currently installed Engine version
         * @param listener Callback for user actions
         */
        fun show(
            fragmentManager: FragmentManager,
            upgradeInfo: EngineVersionChecker.UpgradeInfo,
            currentVersion: Int,
            listener: UpgradeDialogListener
        ): EngineUpgradeDialog {
            val dialog = EngineUpgradeDialog().apply {
                this.listener = listener
                arguments = Bundle().apply {
                    putInt(ARG_VERSION_CODE, upgradeInfo.versionCode)
                    putString(ARG_VERSION_NAME, upgradeInfo.versionName)
                    putString(ARG_CHANGELOG, upgradeInfo.changelog)
                    putBoolean(ARG_IS_FORCE, upgradeInfo.isForce)
                    putInt(ARG_CURRENT_VERSION, currentVersion)
                }
            }
            dialog.show(fragmentManager, TAG)
            return dialog
        }
    }

    interface UpgradeDialogListener {
        fun onUpgradeNow(versionCode: Int)
        fun onUpgradeLater(versionCode: Int)
        fun onExitApp()
    }

    private var listener: UpgradeDialogListener? = null

    private lateinit var titleText: TextView
    private lateinit var versionText: TextView
    private lateinit var changelogText: TextView
    private lateinit var upgradeButton: Button
    private lateinit var laterButton: Button
    private lateinit var exitButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = !(arguments?.getBoolean(ARG_IS_FORCE, false) ?: false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(isCancelable)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_engine_upgrade, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleText = view.findViewById(R.id.titleText)
        versionText = view.findViewById(R.id.versionText)
        changelogText = view.findViewById(R.id.changelogText)
        upgradeButton = view.findViewById(R.id.upgradeButton)
        laterButton = view.findViewById(R.id.laterButton)
        exitButton = view.findViewById(R.id.exitButton)
        progressBar = view.findViewById(R.id.progressBar)
        progressText = view.findViewById(R.id.progressText)

        val versionCode = arguments?.getInt(ARG_VERSION_CODE, 0) ?: 0
        val versionName = arguments?.getString(ARG_VERSION_NAME, "") ?: ""
        val changelog = arguments?.getString(ARG_CHANGELOG, "") ?: ""
        val isForce = arguments?.getBoolean(ARG_IS_FORCE, false) ?: false
        val currentVersion = arguments?.getInt(ARG_CURRENT_VERSION, 0) ?: 0

        // Set content
        titleText.text = if (isForce) "引擎需要更新" else "引擎新版本可用"
        versionText.text = "当前版本: $currentVersion → 新版本: $versionName ($versionCode)"
        changelogText.text = if (changelog.isNotEmpty()) {
            "更新内容:\n$changelog"
        } else {
            "修复了一些问题并提升了性能"
        }

        // Configure buttons based on force/optional
        if (isForce) {
            laterButton.visibility = View.GONE
            exitButton.visibility = View.VISIBLE
            exitButton.setOnClickListener {
                listener?.onExitApp()
                dismiss()
            }
        } else {
            laterButton.visibility = View.VISIBLE
            exitButton.visibility = View.GONE
            laterButton.setOnClickListener {
                listener?.onUpgradeLater(versionCode)
                dismiss()
            }
        }

        upgradeButton.setOnClickListener {
            showDownloadProgress()
            listener?.onUpgradeNow(versionCode)
        }
    }

    /**
     * Show the download progress UI.
     */
    fun showDownloadProgress() {
        upgradeButton.isEnabled = false
        laterButton.isEnabled = false
        exitButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressText.text = "准备下载..."
    }

    /**
     * Update download progress.
     */
    fun updateProgress(percentage: Int) {
        progressBar.progress = percentage
        progressText.text = "正在下载... $percentage%"
    }

    /**
     * Show install state.
     */
    fun showInstalling() {
        progressBar.isIndeterminate = true
        progressText.text = "正在安装..."
    }

    /**
     * Show error state.
     */
    fun showError(message: String) {
        progressBar.visibility = View.GONE
        progressText.text = message
        progressText.setTextColor(android.graphics.Color.RED)
        upgradeButton.isEnabled = true
        upgradeButton.text = "重试"
        laterButton.isEnabled = true
        exitButton.isEnabled = true
    }

    /**
     * Dismiss the dialog safely.
     */
    fun dismissSafely() {
        try {
            dismiss()
        } catch (_: Exception) {
        }
    }
}
