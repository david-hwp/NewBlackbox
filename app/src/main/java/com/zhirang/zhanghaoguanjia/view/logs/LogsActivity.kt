package com.zhirang.zhanghaoguanjia.view.logs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.bean.LogType
import com.zhirang.zhanghaoguanjia.databinding.ActivityLogsBinding

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private lateinit var viewModel: LogsViewModel
    private lateinit var adapter: LogsAdapter
    private var currentLogType: LogType? = null

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LogsActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Duodian)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[LogsViewModel::class.java]
        adapter = LogsAdapter()

        initToolbar()
        initRecyclerView()
        initChipFilters()
        observeViewModel()

        viewModel.loadLogs()
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Pagination: load more when scrolling to bottom
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (visibleItemCount + firstVisibleItemPosition >= totalItemCount
                    && firstVisibleItemPosition >= 0
                ) {
                    // Load more
                    viewModel.loadMore(getCurrentLogType())
                }
            }
        })
    }

    private fun initChipFilters() {
        val chips = listOf(
            binding.chipAll to null,
            binding.chipOut to LogType.OUT,
            binding.chipIn to LogType.IN,
            binding.chipConsume to LogType.CONSUME
        )

        chips.forEach { (chip, type) ->
            chip.setOnClickListener {
                currentLogType = type
                selectChip(chip)
                viewModel.loadLogs(type)
            }
        }
    }

    private fun selectChip(selected: android.widget.TextView) {
        val allChips = listOf(binding.chipAll, binding.chipOut, binding.chipIn, binding.chipConsume)
        allChips.forEach { chip ->
            if (chip == selected) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(getColor(R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected)
                chip.setTextColor(getColor(R.color.duodian_text_primary))
            }
        }
    }

    private fun observeViewModel() {
        viewModel.localLogsLiveData.observe(this) { logs ->
            adapter.submitList(logs)
        }

        viewModel.errorLiveData.observe(this) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentLogType(): LogType? {
        return currentLogType
    }
}
