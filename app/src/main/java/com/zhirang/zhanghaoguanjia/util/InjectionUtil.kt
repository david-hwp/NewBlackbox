package com.zhirang.zhanghaoguanjia.util

import com.zhirang.zhanghaoguanjia.data.AppsRepository
import com.zhirang.zhanghaoguanjia.data.FakeLocationRepository
import com.zhirang.zhanghaoguanjia.data.GmsRepository

import com.zhirang.zhanghaoguanjia.view.apps.AppsFactory
import com.zhirang.zhanghaoguanjia.view.fake.FakeLocationFactory
import com.zhirang.zhanghaoguanjia.view.gms.GmsFactory
import com.zhirang.zhanghaoguanjia.view.list.ListFactory



object InjectionUtil {

    private val appsRepository = AppsRepository()



    private val gmsRepository = GmsRepository()

    private val fakeLocationRepository = FakeLocationRepository()

    fun getAppsFactory() : AppsFactory {
        return AppsFactory(appsRepository)
    }

    fun getListFactory(): ListFactory {
        return ListFactory(appsRepository)
    }


    fun getGmsFactory():GmsFactory{
        return GmsFactory(gmsRepository)
    }

    fun getFakeLocationFactory():FakeLocationFactory{
        return FakeLocationFactory(fakeLocationRepository)
    }
}