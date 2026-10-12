package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;
import war.jnt.innercache.InnerCache;

public class ReturnUnit implements Opcodes {

    public static void process(InnerCache ic, InsnNode insn, UnitContext ctx, TempJumpVM tjvm, boolean isCustomArray) {
        if (isCustomArray) {
            processWithScopeCleanup(ic, insn, ctx, tjvm);
            return;
        }

        switch (insn.getOpcode()) {
            case IRETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.getBuilder().append(String.format("\treturn %s.i;\n", computed));
            }
            case DRETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.getBuilder().append(String.format("\treturn %s.d;\n", computed));
            }
            case FRETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.getBuilder().append(String.format("\treturn %s.f;\n", computed));
            }
            case LRETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.getBuilder().append(String.format("\treturn %s.j;\n", computed));
            }
            case ARETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.getBuilder().append(String.format("\treturn %s.l;\n", computed));
            }
            case RETURN -> ctx.getBuilder().append("\treturn;\n");
        }
    }

    private static void processWithScopeCleanup(InnerCache ic, InsnNode insn, UnitContext ctx, TempJumpVM tjvm) {
        switch (insn.getOpcode()) {
            case IRETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.fmtAppend("\t%s = %s.i;\n", PrimitiveArrayUnit.returnValueName(), computed);
                emitNormalReturnJump(ctx);
            }
            case DRETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.fmtAppend("\t%s = %s.d;\n", PrimitiveArrayUnit.returnValueName(), computed);
                emitNormalReturnJump(ctx);
            }
            case FRETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.fmtAppend("\t%s = %s.f;\n", PrimitiveArrayUnit.returnValueName(), computed);
                emitNormalReturnJump(ctx);
            }
            case LRETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                ctx.fmtAppend("\t%s = %s.j;\n", PrimitiveArrayUnit.returnValueName(), computed);
                emitNormalReturnJump(ctx);
            }
            case ARETURN -> {
                String computed = Internal.computePop(ctx.getTracker());
                if (ctx.getMethodArrayState() != null) {
                    TrackedArrayValue returnValue = ctx.getMethodArrayState().getStackValueBefore(insn, 0);
                    if (returnValue.isCustomPrimitiveArray()) {
                        String customExpr = PrimitiveArrayUnit.snapshotPointer(ctx, computed + ".l", "__jnt_return_custom_" + insn.index);
                        PrimitiveArrayUnit.emitCustomToNewJni(ctx, tjvm, customExpr, returnValue.primitiveArrayKind(), ic.FindClass("java/lang/OutOfMemoryError"), computed + ".l");
                        PrimitiveArrayUnit.emitPendingExceptionToUnwind(ctx, tjvm);
                        PrimitiveArrayUnit.maybeReleaseCustom(ctx, customExpr, returnValue);
                    } else if (returnValue.requiresFallback()) {
                        ctx.getMethodArrayState().warnFallback(insn, returnValue, "return");
                    }
                }
                ctx.fmtAppend("\t%s = %s.l;\n", PrimitiveArrayUnit.returnValueName(), computed);
                emitNormalReturnJump(ctx);
            }
            case RETURN -> emitNormalReturnJump(ctx);
        }
    }

    private static void emitNormalReturnJump(UnitContext ctx) {
        if (Type.getReturnType(ctx.getMethodNode().desc).getSort() != Type.VOID) {
            ctx.fmtAppend("\t%s = JNI_TRUE;\n", PrimitiveArrayUnit.returnFlagName());
        }
        ctx.fmtAppend("\tgoto %s;\n", PrimitiveArrayUnit.arrayEpilogueLabel());
    }
}
