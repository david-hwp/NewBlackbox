package top.niunaijun.blackbox.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import top.niunaijun.blackbox.app.BActivityThread;

public final class ByteDanceProcessCompat {
    private static final String TAG = "ByteDanceProcessCompat";
    private static final Set<String> sAppliedKeys = new HashSet<>();

    private ByteDanceProcessCompat() {
    }

    public static boolean isSupportedPackage(String packageName) {
        return "com.bytedance.ls.merchant".equals(packageName);
    }

    public static boolean isByteDanceStack(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (
                    message.contains("AppContextManager") ||
                            message.contains("Keva") ||
                            message.contains("PrivacyManager") ||
                            message.contains("bytedance"))) {
                return true;
            }
            StackTraceElement[] stackTrace = current.getStackTrace();
            if (stackTrace != null) {
                for (StackTraceElement element : stackTrace) {
                    String className = element.getClassName();
                    if (className != null && (
                            className.startsWith("com.bytedance.") ||
                                    className.startsWith("com.ss.") ||
                                    className.contains(".keva.") ||
                                    className.contains("AppContextManager"))) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public static void beforeCreateApplication(String packageName, String processName, Context context, int userId) {
        if (!isSupportedPackage(packageName) || context == null) {
            return;
        }
        ensurePrivacyAgreementFlag(context);
        apply("beforeCreateApplication", packageName, processName, userId, context.getClassLoader(), context);
    }

    public static void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {
        if (!isSupportedPackage(packageName) || application == null) {
            return;
        }
        apply("beforeApplicationOnCreate", packageName, processName, userId, application.getClassLoader(), application);
    }

    public static void afterApplicationOnCreate(String packageName, String processName, Application application, int userId) {
        if (!isSupportedPackage(packageName) || application == null) {
            return;
        }
        apply("afterApplicationOnCreate", packageName, processName, userId, application.getClassLoader(), application);
    }

    public static void beforeActivityOnStart(Activity activity) {
        if (activity == null) {
            return;
        }
        String packageName = BActivityThread.getAppPackageName();
        if (!isSupportedPackage(packageName)) {
            return;
        }
        String processName = BActivityThread.getAppProcessName();
        apply("beforeActivityOnStart", packageName, processName, BActivityThread.getUserId(), activity.getClassLoader(), activity.getApplicationContext());
    }

    private static void apply(
            String stage,
            String packageName,
            String processName,
            int userId,
            ClassLoader classLoader,
            Context context
    ) {
        if (classLoader == null || context == null) {
            return;
        }
        String key = packageName + ":" + processName + ":" + userId + ":" + stage;
        synchronized (sAppliedKeys) {
            if (sAppliedKeys.contains(key)) {
                return;
            }
            sAppliedKeys.add(key);
        }
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        setByteDanceAppContext(classLoader, appContext);
        initializeKeva(classLoader, appContext);
    }

    private static void ensurePrivacyAgreementFlag(Context context) {
        try {
            File filesDir = context.getFilesDir();
            if (filesDir == null) {
                return;
            }
            if (!filesDir.exists() && !filesDir.mkdirs()) {
                return;
            }
            File flag = new File(filesDir, "privacy_agreement_agreed");
            if (!flag.exists() && flag.createNewFile()) {
                Slog.d(TAG, "Created ByteDance privacy agreement flag: " + flag.getAbsolutePath());
            }
        } catch (Throwable e) {
            Slog.w(TAG, "Failed to create ByteDance privacy agreement flag: " + e.getMessage());
        }
    }

    private static void setByteDanceAppContext(ClassLoader classLoader, Context context) {
        Class<?> managerClass = findClass(classLoader, "com.bytedance.ies.ugc.appcontext.AppContextManager");
        if (managerClass == null) {
            return;
        }
        Object instance = findSingleton(managerClass);
        setContextFields(managerClass, instance, context);
        invokeContextSetters(managerClass, instance, context);
    }

    private static void initializeKeva(ClassLoader classLoader, Context context) {
        Class<?> kevaClass = findClass(classLoader, "com.bytedance.keva.Keva");
        if (kevaClass == null) {
            kevaClass = findClass(classLoader, "com.bytedance.keva.KevaImpl");
        }
        if (kevaClass == null) {
            return;
        }
        setContextFields(kevaClass, null, context);
        invokeContextSetters(kevaClass, null, context);
        invokeLikelyInitMethods(kevaClass, context);
    }

    private static Class<?> findClass(ClassLoader classLoader, String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findSingleton(Class<?> clazz) {
        String[] names = {"INSTANCE", "sInstance", "instance"};
        for (String name : names) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void setContextFields(Class<?> clazz, Object instance, Context context) {
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (!Context.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                boolean isStatic = Modifier.isStatic(field.getModifiers());
                Object target = isStatic ? null : instance;
                if (!isStatic && target == null) {
                    continue;
                }
                if (!field.getType().isInstance(context)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object existing = field.get(target);
                    if (existing == null) {
                        field.set(target, context);
                        Slog.d(TAG, "Initialized " + clazz.getName() + "." + field.getName());
                    }
                } catch (Throwable e) {
                    Slog.w(TAG, "Failed to initialize context field " + field.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private static void invokeContextSetters(Class<?> clazz, Object instance, Context context) {
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1 || !Context.class.isAssignableFrom(parameterTypes[0])) {
                continue;
            }
            String name = method.getName();
            if (TextUtils.isEmpty(name) || (
                    !name.toLowerCase().contains("context") &&
                            !name.toLowerCase().contains("init") &&
                            !name.toLowerCase().contains("attach"))) {
                continue;
            }
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            Object target = isStatic ? null : instance;
            if (!isStatic && target == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, context);
                Slog.d(TAG, "Invoked " + clazz.getName() + "." + name);
            } catch (Throwable e) {
                Slog.w(TAG, "Failed to invoke context method " + name + ": " + e.getMessage());
            }
        }
    }

    private static void invokeLikelyInitMethods(Class<?> clazz, Context context) {
        String[] names = {"init", "initialize", "initContext", "setContext", "setApplicationContext"};
        for (String name : names) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                if (!name.equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && Context.class.isAssignableFrom(parameterTypes[0])) {
                    try {
                        method.setAccessible(true);
                        method.invoke(null, context);
                        Slog.d(TAG, "Invoked " + clazz.getName() + "." + name);
                    } catch (Throwable e) {
                        Slog.w(TAG, "Failed to invoke init method " + name + ": " + e.getMessage());
                    }
                }
            }
        }
    }
}
