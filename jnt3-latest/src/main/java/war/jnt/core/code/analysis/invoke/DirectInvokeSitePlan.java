package war.jnt.core.code.analysis.invoke;

public record DirectInvokeSitePlan(
        NativeInvokeKind invokeKind,
        DirectInvokeTarget directTarget,
        DirectInvokeFallbackReason fallbackReason
) {

    public static DirectInvokeSitePlan jni(DirectInvokeFallbackReason fallbackReason) {
        return new DirectInvokeSitePlan(NativeInvokeKind.JNI, null, fallbackReason);
    }

    public static DirectInvokeSitePlan direct(DirectInvokeTarget directTarget) {
        return new DirectInvokeSitePlan(NativeInvokeKind.DIRECT_JNICALL, directTarget, null);
    }

    public boolean isDirect() {
        return invokeKind == NativeInvokeKind.DIRECT_JNICALL && directTarget != null;
    }
}
