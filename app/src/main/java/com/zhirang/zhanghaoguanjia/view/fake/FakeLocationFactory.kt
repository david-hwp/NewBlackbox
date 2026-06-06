package com.zhirang.zhanghaoguanjia.view.fake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhirang.zhanghaoguanjia.data.FakeLocationRepository


class FakeLocationFactory(private val repo: FakeLocationRepository) :
    ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FakeLocationViewModel(repo) as T
    }
}