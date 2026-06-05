package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.PlatformInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platforms")
public class PlatformController {

    @GetMapping
    public ApiResponse<List<PlatformInfo>> list() {
        return ApiResponse.success(List.of(
                new PlatformInfo("meituan", "美团外卖", "com.sankuai.meituan.merchant", "/api/files/platform-icons/meituan.png", false),
                new PlatformInfo("taobao", "淘宝闪购", "com.taobao.qianniu", "/api/files/platform-icons/qianniu.png", false),
                new PlatformInfo("jd", "京东秒送", "com.jd.mrd.jingming", "/api/files/platform-icons/jd.png", true),
                new PlatformInfo("kuaishou", "快手团购", "com.kuaishou.nebula", "/api/files/platform-icons/kuaishou.png", false),
                new PlatformInfo("xiaohongshu", "小红书", "com.xingin.xhs", "/api/files/platform-icons/xiaohongshu.png", false),
                new PlatformInfo("ali", "阿里本地", "com.alipay.m.portal", "/api/files/platform-icons/koubei.png", false)
        ));
    }
}
