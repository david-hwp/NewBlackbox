package com.zhirang.zhanghaoguanjia.view.gms

import androidx.lifecycle.MutableLiveData
import com.zhirang.zhanghaoguanjia.bean.GmsBean
import com.zhirang.zhanghaoguanjia.bean.GmsInstallBean
import com.zhirang.zhanghaoguanjia.data.GmsRepository
import com.zhirang.zhanghaoguanjia.view.base.BaseViewModel


class GmsViewModel(private val mRepo: GmsRepository) : BaseViewModel() {

    val mInstalledLiveData = MutableLiveData<List<GmsBean>>()

    val mUpdateInstalledLiveData = MutableLiveData<GmsInstallBean>()

    fun getInstalledUser() {
        launchOnUI {
            mRepo.getGmsInstalledList(mInstalledLiveData)
        }
    }

    fun installGms(userID: Int) {
        launchOnUI {
            mRepo.installGms(userID,mUpdateInstalledLiveData)
        }
    }

    fun uninstallGms(userID: Int) {
        launchOnUI {
            mRepo.uninstallGms(userID,mUpdateInstalledLiveData)
        }
    }
}