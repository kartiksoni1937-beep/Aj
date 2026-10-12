package war.jnt.core.code.analysis.invoke;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class MethodInvokePlan {

    private static final DirectInvokeSitePlan DEFAULT_PLAN =
            DirectInvokeSitePlan.jni(DirectInvokeFallbackReason.LIBRARY_OR_MISSING_TARGET);

    private final MethodNode methodNode;
    private final DirectInvokeSitePlan[] sitePlans;

    public MethodInvokePlan(MethodNode methodNode, DirectInvokeSitePlan[] sitePlans) {
        this.methodNode = methodNode;
        this.sitePlans = sitePlans;
    }

    public DirectInvokeSitePlan getSitePlan(MethodInsnNode insn) {
        return getSitePlan((AbstractInsnNode) insn);
    }

    public DirectInvokeSitePlan getSitePlan(AbstractInsnNode insn) {
        if (insn == null || insn.index < 0 || insn.index >= sitePlans.length) {
            return DEFAULT_PLAN;
        }
        DirectInvokeSitePlan sitePlan = sitePlans[insn.index];
        return sitePlan != null ? sitePlan : DEFAULT_PLAN;
    }

    public MethodNode methodNode() {
        return methodNode;
    }
}
