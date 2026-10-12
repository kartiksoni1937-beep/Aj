package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.analysis.TrackedArrayValue;

// j2cc - cuz im too stupid for this
public class VarUnit implements Opcodes {

    public static void process(VarInsnNode insn, UnitContext ctx, MethodNode method) {
        switch (insn.getOpcode()) {
            case ILOAD, FLOAD, DLOAD, LLOAD, ALOAD -> {
                String computed = war.jnt.fusebox.impl.Internal.computePush(ctx.getTracker());
                char c = "ijfdl".charAt(insn.getOpcode() - ILOAD);
                if (insn.getOpcode() == ALOAD && ctx.getMethodArrayState() != null) {
                    TrackedArrayValue localValue = ctx.getMethodArrayState().getLocalValueBefore(insn, insn.var);
                    PrimitiveArrayUnit.maybeRetainCustom(ctx, "locals[" + insn.var + "].l", localValue);
                }
                ctx.fmtAppend("\t%s.%s = locals[%d].%s;\n", computed, c, insn.var, c);
            }
            case ISTORE, FSTORE, DSTORE, LSTORE, ASTORE -> {
                String computed = war.jnt.fusebox.impl.Internal.computePop(ctx.getTracker());
                char c = "ijfdl".charAt(insn.getOpcode() - ISTORE);
                if (insn.getOpcode() == ASTORE && ctx.getMethodArrayState() != null) {
                    TrackedArrayValue previousLocalValue = ctx.getMethodArrayState().getLocalValueBefore(insn, insn.var);
                    TrackedArrayValue incomingValue = ctx.getMethodArrayState().getStackValueBefore(insn, 0);
                    String oldLocalExpr = PrimitiveArrayUnit.snapshotPointer(
                            ctx,
                            "locals[" + insn.var + "].l",
                            "__jnt_old_local_" + insn.index + "_" + insn.var
                    );
                    if (incomingValue.isCustomPrimitiveArray()) {
                        PrimitiveArrayUnit.emitMoveOwnerToLocal(ctx, insn.var, computed);
                    } else {
                        ctx.fmtAppend("\tlocals[%d].%s = %s.%s;\n", insn.var, c, computed, c);
                    }
                    PrimitiveArrayUnit.maybeReleaseCustom(ctx, oldLocalExpr, previousLocalValue);
                    return;
                }
                ctx.fmtAppend("\tlocals[%d].%s = %s.%s;\n", insn.var, c, computed, c);
            }
        }
    }
}
