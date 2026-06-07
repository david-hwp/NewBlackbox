package black.com.android.internal.view;


import android.os.IBinder;
import android.os.IInterface;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticMethod;

@BClassName("com.android.internal.view.IInputMethodManager")
public interface IInputMethodManager {
    @BClassName("com.android.internal.view.IInputMethodManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
