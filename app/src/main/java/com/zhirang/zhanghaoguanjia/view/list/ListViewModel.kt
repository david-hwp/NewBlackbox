package com.zhirang.zhanghaoguanjia.view.list

import androidx.lifecycle.MutableLiveData
import com.zhirang.zhanghaoguanjia.app.App
import com.zhirang.zhanghaoguanjia.bean.InstalledAppBean
import com.zhirang.zhanghaoguanjia.data.AppsRepository
import com.zhirang.zhanghaoguanjia.view.base.BaseViewModel


class ListViewModel(private val repo: AppsRepository) : BaseViewModel() {

    val appsLiveData = MutableLiveData<List<InstalledAppBean>>()

    val loadingLiveData = MutableLiveData<Boolean>()

    fun previewInstalledList() {
        launchOnUI { repo.previewInstallList(App.getContext()) }
    }

    fun getInstallAppList(userID: Int) {
        launchOnUI { repo.getInstalledAppList(userID, loadingLiveData, appsLiveData) }
    }
}
