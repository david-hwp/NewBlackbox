package com.zhirang.zhanghaoguanjia.data

import androidx.lifecycle.MutableLiveData
import com.zhirang.zhanghaoguanjia.R
import com.zhirang.zhanghaoguanjia.app.AppManager
import com.zhirang.zhanghaoguanjia.bean.GmsBean
import com.zhirang.zhanghaoguanjia.bean.GmsInstallBean
import com.zhirang.zhanghaoguanjia.engine.EngineProxy
import com.zhirang.zhanghaoguanjia.util.getString


class GmsRepository {


    fun getGmsInstalledList(mInstalledLiveData: MutableLiveData<List<GmsBean>>) {
        val userList = arrayListOf<GmsBean>()

        EngineProxy.getUsers().forEach {
            val userId = it.id
            val userName =
                AppManager.mRemarkSharedPreferences.getString("Remark$userId", "User $userId") ?: ""
            val isInstalled = EngineProxy.isInstallGms(userId)
            val bean = GmsBean(userId, userName, isInstalled)
            userList.add(bean)
        }

        mInstalledLiveData.postValue(userList)
    }

    fun installGms(
        userID: Int,
        mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>
    ) {
        val installResult = EngineProxy.installGms(userID)

        val result = if (installResult.success) {
            getString(R.string.install_success)
        } else {
            getString(R.string.install_fail, installResult.msg)
        }

        val bean = GmsInstallBean(userID,installResult.success,result)
        mUpdateInstalledLiveData.postValue(bean)
    }

    fun uninstallGms(
        userID: Int,
        mUpdateInstalledLiveData: MutableLiveData<GmsInstallBean>
    ) {
        var isSuccess = false
        if (EngineProxy.isInstallGms(userID)) {
            isSuccess = EngineProxy.uninstallGms(userID)
        }

        val result = if (isSuccess) {
            getString(R.string.uninstall_success)
        } else {
            getString(R.string.uninstall_fail)
        }

        val bean = GmsInstallBean(userID,isSuccess,result)

        mUpdateInstalledLiveData.postValue(bean)
    }
}
