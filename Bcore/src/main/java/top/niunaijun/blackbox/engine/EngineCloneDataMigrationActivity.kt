package top.niunaijun.blackbox.engine

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import top.niunaijun.blackbox.core.env.BEnvironment
import top.niunaijun.blackbox.utils.Slog

class EngineCloneDataMigrationActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var messageView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        layout.addView(ProgressBar(this).apply { isIndeterminate = true })
        messageView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            text = "正在迁移分身数据"
            setPadding(0, 32, 0, 0)
        }
        layout.addView(messageView)
        setContentView(layout)

        Thread {
            val result = runCatching {
                BEnvironment.load()
                CloneInstanceStore.migrateAllScoped().toString()
            }.getOrElse { error ->
                Slog.w(TAG, "clone data migration failed", error)
                """{"ok":false,"error":"${error.message ?: error.javaClass.name}"}"""
            }
            Slog.d(TAG, "clone data migration result=$result")
            handler.post {
                messageView.text = "分身数据迁移完成"
                setResult(RESULT_OK)
                handler.postDelayed({
                    finish()
                    overridePendingTransition(0, 0)
                }, 600)
            }
        }.start()
    }

    companion object {
        private const val TAG = "EngineCloneDataMigration"
    }
}
