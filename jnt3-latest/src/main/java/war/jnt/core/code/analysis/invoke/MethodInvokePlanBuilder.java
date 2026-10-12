package war.jnt.core.code.analysis.invoke;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import war.jnt.core.code.analysis.flow.MethodCustomArrayFlow;
import war.jnt.core.code.impl.invoke.InvocationUnit;
import war.metaphor.tree.ClassMethod;
import war.metaphor.tree.JClassNode;

public final class MethodInvokePlanBuilder implements Opcodes {

    private MethodInvokePlanBuilder() {}

    public static MethodInvokePlan build(
            JClassNode owner,
            MethodNode methodNode,
            DirectInvokeRegistry registry,
            MethodCustomArrayFlow arrayFlow,
            boolean intrinsicEnabled
    ) {
        methodNode.instructions.toArray();

        DirectInvokeSitePlan[] sitePlans = new DirectInvokeSitePlan[methodNode.instructions.size()];
        ClassMethod caller = ClassMethod.of(owner, methodNode);

        for (AbstractInsnNode insn : methodNode.instructions) {
            if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                continue;
            }

            int opcode = methodInsnNode.getOpcode();
            if (opcode != INVOKESTATIC && opcode != INVOKEVIRTUAL && opcode != INVOKESPECIAL && opcode != INVOKEINTERFACE) {
                continue;
            }

            if (InvocationUnit.usesIntrinsic(methodInsnNode, arrayFlow, intrinsicEnabled)) {
                sitePlans[insn.index] = DirectInvokeSitePlan.jni(DirectInvokeFallbackReason.INTRINSIC);
                continue;
            }

            sitePlans[insn.index] = registry != null
                    ? registry.planFor(caller, methodInsnNode)
                    : DirectInvokeSitePlan.jni(DirectInvokeFallbackReason.LIBRARY_OR_MISSING_TARGET);
        }

        return new MethodInvokePlan(methodNode, sitePlans);
    }
}
