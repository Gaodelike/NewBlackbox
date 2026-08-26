package black.android.app;

import android.app.IBinderSession;
import android.content.ComponentName;
import android.os.IBinder;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BMethod;

@BClassName("android.app.IServiceConnection")
public interface IServiceConnectionB {
    @BMethod
    void connected(ComponentName componentName, IBinder service,
                   IBinderSession session, boolean dead);
}
