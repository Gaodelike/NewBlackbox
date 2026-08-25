package black.android.view.inputmethod;

import android.os.IInterface;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticField;

@BClassName("android.view.inputmethod.IInputMethodManagerGlobalInvoker")
public interface IInputMethodManagerGlobalInvoker {
    @BStaticField
    IInterface sServiceCache();
}
