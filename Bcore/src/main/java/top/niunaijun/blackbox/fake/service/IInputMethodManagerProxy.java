package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.view.inputmethod.EditorInfo;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import black.android.view.inputmethod.BRIInputMethodManagerGlobalInvoker;
import black.com.android.internal.view.BRIInputMethodManagerStub;
import black.com.android.internal.view.inputmethod.BRInputMethodManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethods;
import top.niunaijun.blackbox.fake.hook.ScanClass;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;


@ScanClass(IInputMethodManagerProxy.class)
public class IInputMethodManagerProxy extends BinderInvocationStub {
    public static final String TAG = "IInputMethodManagerProxy";

    public IInputMethodManagerProxy() {
        super(BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE));
    }

    @Override
    protected Object getWho() {
        return BRIInputMethodManagerStub.get().asInterface(BRServiceManager.get().getService(Context.INPUT_METHOD_SERVICE));
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        try {
            if (BRIInputMethodManagerGlobalInvoker.get()._check_sServiceCache() != null) {
                BRIInputMethodManagerGlobalInvoker.get()._set_sServiceCache(proxyInvocation);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object inputMethodManager = BlackBoxCore.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null && BRInputMethodManager.get(inputMethodManager)._check_mService() != null) {
                BRInputMethodManager.get(inputMethodManager)._set_mService(proxyInvocation);
            }
        } catch (Throwable ignored) {
        }
        replaceSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        fixInputMethodArgs(method.getName(), args);
        return super.invoke(proxy, method, args);
    }

    private static void fixInputMethodArgs(String methodName, Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        boolean changed = false;
        for (Object arg : args) {
            if (arg instanceof EditorInfo) {
                EditorInfo editorInfo = (EditorInfo) arg;
                if (isVirtualPackage(editorInfo.packageName)) {
                    editorInfo.packageName = BlackBoxCore.getHostPkg();
                    changed = true;
                }
            }
        }
        String replacedPkg = MethodParameterUtils.replaceFirstAppPkg(args);
        if (replacedPkg != null) {
            changed = true;
        }
        if (changed) {
            Slog.d(TAG, "fix input method package for " + methodName + ", app=" + BActivityThread.getAppPackageName());
        }
    }

    private static boolean isVirtualPackage(String packageName) {
        return packageName != null && BlackBoxCore.get().isInstalled(packageName, BlackBoxCore.getUserId());
    }

    @ProxyMethods({
            "startInput",
            "windowGainedFocus",
            "startInputOrWindowGainedFocus",
            "startInputOrWindowGainedFocusAsync"
    })
    public static class FixEditorInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            fixInputMethodArgs(method.getName(), args);
            return method.invoke(who, args);
        }
    }
}
