package top.niunaijun.blackbox.engine;

import top.niunaijun.blackbox.entity.pm.WechatShareTarget;

interface IWechatShareCaptureCallback {
    void onShareTargetResolved(in WechatShareTarget target);
    void onShareTargetTimeout(String packageName, int userId);
    void onShareTargetCancelled(String packageName, int userId);
    void onShareTargetError(String packageName, int userId, String message);
}
