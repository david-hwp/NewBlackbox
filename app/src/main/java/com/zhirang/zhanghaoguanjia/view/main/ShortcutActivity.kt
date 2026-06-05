package com.zhirang.zhanghaoguanjia.view.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.zhirang.zhanghaoguanjia.engine.EngineProxy


class ShortcutActivity:AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra("pkg")
        val userID = intent.getIntExtra("userId",0)

        lifecycleScope.launch {
            val launchIntent = EngineProxy.getLaunchIntent(pkg ?: "", userID)
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
            finish()
        }
    }
}
