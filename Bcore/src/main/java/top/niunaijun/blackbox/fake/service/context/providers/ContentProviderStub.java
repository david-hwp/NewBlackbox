package top.niunaijun.blackbox.fake.service.context.providers;

import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IInterface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import black.android.content.BRAttributionSource;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.AttributionSourceUtils;
import top.niunaijun.blackbox.utils.ByteDanceProcessCompat;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ContextCompat;


public class ContentProviderStub extends ClassInvocationStub implements BContentProvider {
    public static final String TAG = "ContentProviderStub";
    private static final ThreadLocal<Integer> sByteDanceTokenProviderDepth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };
    private IInterface mBase;
    private String mAppPkg;

    public IInterface wrapper(final IInterface contentProviderProxy, final String appPkg) {
        mBase = contentProviderProxy;
        mAppPkg = appPkg;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    protected void onBindMethod() {

    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }
        
        
        String methodName = method.getName();
        
        
        
        if ("call".equals(methodName)) {
            
            AttributionSourceUtils.fixAttributionSourceInArgs(args);
        } else {
            
            if (args != null && args.length > 0) {
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];
                    if (arg instanceof String) {
                        String strArg = (String) arg;
                        
                        if (!isSystemProviderAuthority(strArg)) {
                            
                            args[i] = mAppPkg;
                        }
                    }
                }
                
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
            }
        }
        
        
        methodName = method.getName();
        if (methodName.equals("query") || methodName.equals("insert") || 
            methodName.equals("update") || methodName.equals("delete") || 
            methodName.equals("bulkInsert") || methodName.equals("call")) {
            
            
            try {
                return invokeProviderMethod(method, args, methodName);
            } catch (Throwable e) {
                
                Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
                if (isUidMismatchError(cause)) {
                    Slog.w(TAG, "UID mismatch in ContentProvider call, returning safe default: " + cause.getMessage());
                    return getSafeDefaultValue(methodName, method.getReturnType());
                } else if (cause instanceof RuntimeException) {
                    String message = cause.getMessage();
                    if (message != null && (message.contains("uid") || message.contains("permission"))) {
                        Slog.w(TAG, "Permission/UID error in ContentProvider call, returning safe default: " + message);
                        return getSafeDefaultValue(methodName, method.getReturnType());
                    }
                }
                
                
                if (methodName.equals("call")) {
                    Slog.w(TAG, "Error in call method, returning safe default: " + e.getMessage());
                    return getSafeDefaultValue(methodName, method.getReturnType());
                }
                
                throw e.getCause();
            }
        }
        
        
        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            
            Throwable cause = e.getCause();
            if (isUidMismatchError(cause)) {
                Slog.w(TAG, "UID mismatch in " + methodName + ", returning safe default: " + cause.getMessage());
                return getSafeDefaultValue(methodName, method.getReturnType());
            }
            throw e.getCause();
        }
    }

    private Object invokeProviderMethod(Method method, Object[] args, String methodName) throws Throwable {
        Uri byteDanceTokenProviderUri = findByteDanceTokenProviderQueryUri(methodName, args);
        if (byteDanceTokenProviderUri == null) {
            return method.invoke(mBase, args);
        }

        int depth = sByteDanceTokenProviderDepth.get();
        if (depth > 0) {
            Slog.w(TAG, "Blocked recursive ByteDance TokenObjectProvider query for " + mAppPkg);
            return newByteDanceTokenCursor(byteDanceTokenProviderUri);
        }
        sByteDanceTokenProviderDepth.set(depth + 1);
        try {
            return method.invoke(mBase, args);
        } finally {
            if (depth == 0) {
                sByteDanceTokenProviderDepth.remove();
            } else {
                sByteDanceTokenProviderDepth.set(depth);
            }
        }
    }

    private Uri findByteDanceTokenProviderQueryUri(String methodName, Object[] args) {
        if (!"query".equals(methodName) || !isCurrentByteDanceTarget()) {
            return null;
        }
        Uri uri = findUriArg(args);
        if (uri == null) {
            return null;
        }
        String authority = uri.getAuthority();
        if (!"com.bytedance.ls.merchant.TokenObjectProvider".equals(authority)) {
            return null;
        }
        String path = uri.getPath();
        return "/get_token_object".equals(path) || "/get_last_same_user_token".equals(path) ? uri : null;
    }

    private boolean isCurrentByteDanceTarget() {
        if (ByteDanceProcessCompat.isSupportedPackage(mAppPkg)) {
            return true;
        }
        try {
            return ByteDanceProcessCompat.isSupportedPackage(BActivityThread.getAppPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Uri findUriArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Uri) {
                return (Uri) arg;
            }
        }
        return null;
    }

    private MatrixCursor newByteDanceTokenCursor(Uri uri) {
        if ("/get_last_same_user_token".equals(uri.getPath())) {
            MatrixCursor cursor = new MatrixCursor(new String[]{"last_token"});
            cursor.newRow().add("");
            return cursor;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{
                "token",
                "ts_sign",
                "attest_ts_sign",
                "token_encrypted",
                "token_hash"
        });
        cursor.newRow().add("").add("").add("").add("").add("");
        return cursor;
    }
    
    private Object getSafeDefaultValue(String methodName) {
        switch (methodName) {
            case "query":
                return null; 
            case "insert":
                return null; 
            case "update":
            case "delete":
                return 0; 
            case "bulkInsert":
                return 0; 
            case "call":
                return new Bundle(); 
            case "getType":
                return null; 
            case "openFile":
                return null; 
            case "openAssetFile":
                return null; 
            default:
                return null; 
        }
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;
        
        
        return authority.equals("settings") || 
               authority.equals("settings_global") || 
               authority.equals("settings_system") || 
               authority.equals("settings_secure") ||
               authority.equals("media") ||
               authority.equals("telephony") ||
               authority.startsWith("android.provider.Settings");
    }
    
    
    private boolean isUidMismatchError(Throwable error) {
        if (error == null) return false;
        
        String message = error.getMessage();
        if (message == null) return false;
        
        
        return message.contains("Calling uid") && 
               message.contains("doesn't match source uid") ||
               message.contains("uid") && 
               message.contains("permission") ||
               message.contains("SecurityException") ||
               message.contains("UID mismatch");
    }
    
    
    private Object getSafeDefaultValue(String methodName, Class<?> returnType) {
        if (returnType == null) {
            return getSafeDefaultValue(methodName);
        }
        
        
        if (returnType == String.class) {
            return "true"; 
        } else if (returnType == int.class || returnType == Integer.class) {
            return 1; 
        } else if (returnType == long.class || returnType == Long.class) {
            return 1L; 
        } else if (returnType == float.class || returnType == Float.class) {
            return 1.0f; 
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            return true; 
        } else if (returnType == Bundle.class) {
            return new Bundle(); 
        }
        
        
        return getSafeDefaultValue(methodName);
    }

    
    private void fixAttributionSourceUid(Object attributionSource) {
        try {
            if (attributionSource == null) return;
            
            Class<?> attributionSourceClass = attributionSource.getClass();
            
            
            try {
                java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField("mUid");
                uidField.setAccessible(true);
                uidField.set(attributionSource, BlackBoxCore.getHostUid());
                Slog.d(TAG, "Fixed AttributionSource UID via field access");
            } catch (NoSuchFieldException e) {
                
                try {
                    java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField("uid");
                    uidField.setAccessible(true);
                    uidField.set(attributionSource, BlackBoxCore.getHostUid());
                    Slog.d(TAG, "Fixed AttributionSource UID via alternative field");
                } catch (NoSuchFieldException e2) {
                    
                    try {
                        java.lang.reflect.Method setUidMethod = attributionSourceClass.getDeclaredMethod("setUid", int.class);
                        setUidMethod.setAccessible(true);
                        setUidMethod.invoke(attributionSource, BlackBoxCore.getHostUid());
                        Slog.d(TAG, "Fixed AttributionSource UID via setter method");
                    } catch (Exception e3) {
                        Slog.w(TAG, "Could not fix AttributionSource UID: " + e3.getMessage());
                    }
                }
            }
            
            
            try {
                java.lang.reflect.Field packageField = attributionSourceClass.getDeclaredField("mPackageName");
                packageField.setAccessible(true);
                packageField.set(attributionSource, mAppPkg);
                Slog.d(TAG, "Fixed AttributionSource package name");
            } catch (Exception e) {
                
            }
            
        } catch (Exception e) {
            Slog.w(TAG, "Error fixing AttributionSource UID: " + e.getMessage());
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
