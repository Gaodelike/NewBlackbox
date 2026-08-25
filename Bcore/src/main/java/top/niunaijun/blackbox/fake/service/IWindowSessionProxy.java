package top.niunaijun.blackbox.fake.service;

import android.os.IInterface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;



public class IWindowSessionProxy extends BinderInvocationStub {
    public static final String TAG = "WindowSessionStub";

    private IInterface mSession;
    private static Object sLastVisibleApplicationWindow;
    private static final Set<Object> sApplicationWindows = Collections.newSetFromMap(new WeakHashMap<>());

    public IWindowSessionProxy(IInterface session) {
        super(session.asBinder());
        mSession = session;
    }

    @Override
    protected Object getWho() {
        return mSession;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object getProxyInvocation() {
        return super.getProxyInvocation();
    }

    @ProxyMethod("addToDisplay")
    public static class AddToDisplay extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof WindowManager.LayoutParams) {
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                    lp.packageName = BlackBoxCore.getHostPkg();
                    if (BlackBoxCore.get().isDisableFlagSecure()) {
                        lp.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                    }
                }
            }
            trackVisibleApplicationWindow(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("addToDisplayAsUser")
    public static class AddToDisplayAsUser extends AddToDisplay {
    }

    @ProxyMethod("relayout")
    public static class Relayout extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            for (Object arg : args) {
                if (arg == null) {
                    continue;
                }
                if (arg instanceof WindowManager.LayoutParams) {
                    WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                    if (BlackBoxCore.get().isDisableFlagSecure()) {
                        lp.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                    }
                }
            }
            trackVisibleApplicationWindow(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("relayoutAsync")
    public static class RelayoutAsync extends Relayout {
    }

    @ProxyMethod("updateRequestedVisibleTypes")
    public static class UpdateRequestedVisibleTypes extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length >= 2 && args[1] instanceof Integer) {
                int requestedVisibleTypes = (int) args[1];
                if ((requestedVisibleTypes & WindowInsets.Type.ime()) != 0
                        && sLastVisibleApplicationWindow != null
                        && args[0] != sLastVisibleApplicationWindow) {
                    Slog.d(TAG, "Redirect IME visibility request to current visible window");
                    args[0] = sLastVisibleApplicationWindow;
                }
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("windowFocusChanged")
    public static class WindowFocusChanged extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length >= 2 && args[1] instanceof Boolean && (Boolean) args[1]) {
                noteFocusedWindow(args[0]);
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishDrawing")
    public static class FinishDrawing extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0) {
                noteFocusedWindow(args[0]);
            }
            return method.invoke(who, args);
        }
    }

    private static void trackVisibleApplicationWindow(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        Object window = args[0];
        WindowManager.LayoutParams lp = null;
        Integer viewVisibility = null;
        for (Object arg : args) {
            if (arg instanceof WindowManager.LayoutParams) {
                lp = (WindowManager.LayoutParams) arg;
            }
        }
        if (args.length > 4 && args[4] instanceof Integer) {
            viewVisibility = (Integer) args[4];
        }
        if (lp == null && sApplicationWindows.contains(window)) {
            if (viewVisibility == null || viewVisibility == View.VISIBLE) {
                sLastVisibleApplicationWindow = window;
            }
            return;
        }
        if (lp == null || !isApplicationWindow(lp.type)) {
            return;
        }
        if (viewVisibility != null && viewVisibility != View.VISIBLE) {
            return;
        }
        sApplicationWindows.add(window);
        sLastVisibleApplicationWindow = window;
    }

    private static void noteFocusedWindow(Object window) {
        if (window == null) {
            return;
        }
        if (sApplicationWindows.contains(window) || window.getClass().getName().contains("IWindow")) {
            sApplicationWindows.add(window);
            sLastVisibleApplicationWindow = window;
        }
    }

    private static boolean isApplicationWindow(int type) {
        return type >= WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW
                && type <= WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
    }
}
