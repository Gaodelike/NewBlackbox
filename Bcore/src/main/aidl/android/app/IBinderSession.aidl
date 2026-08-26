package android.app;

/** @hide */
interface IBinderSession {
    long binderTransactionStarting(in String debugTag);
    void binderTransactionCompleted(long token);
}
