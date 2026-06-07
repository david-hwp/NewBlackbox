package top.niunaijun.blackbox.fake.service;

import android.content.Context;

import java.lang.reflect.Method;

import black.android.content.BRClipboardManager;
import black.android.content.BRClipboardManagerOreo;
import black.android.content.BRIClipboardStub;
import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;

public class IClipboardManagerProxy extends BinderInvocationStub {
    private static final String TAG = "IClipboardManagerProxy";

    public IClipboardManagerProxy() {
        super(BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIClipboardStub.get().asInterface(BRServiceManager.get().getService(Context.CLIPBOARD_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        try {
            if (BRClipboardManager.get()._check_sService() != null) {
                BRClipboardManager.get()._set_sService(proxyInvocation);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (BRClipboardManagerOreo.get()._check_sService() != null) {
                BRClipboardManagerOreo.get()._set_sService(proxyInvocation);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object clipboard = BlackBoxCore.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && BRClipboardManagerOreo.get(clipboard)._check_mService() != null) {
                BRClipboardManagerOreo.get(clipboard)._set_mService(proxyInvocation);
            }
        } catch (Throwable ignored) {
        }
        replaceSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String replacedPkg = MethodParameterUtils.replaceFirstAppPkg(args);
        if (replacedPkg != null) {
            Slog.d(TAG, "fix clipboard package for " + method.getName()
                    + ": " + replacedPkg + " -> " + BlackBoxCore.getHostPkg()
                    + ", app=" + BActivityThread.getAppPackageName());
        }
        return super.invoke(proxy, method, args);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
