package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.JumpInsnNode;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.core.code.analysis.flow.MethodCustomArrayFlow;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;

public class JumpUnit implements Opcodes {
    private static final String[] OPERATIONS = {
            "==", "!=", "<", ">=", ">", "<="
    };

    public static void process(JumpInsnNode insn, UnitContext ctx, TempJumpVM tjvm) {
        String targetBlock = BlockUnit.resolveBlock(insn.label);

        switch (insn.getOpcode()) {
            case GOTO -> {
                ctx.fmtAppend("\tgoto %s;\n", targetBlock);
            }
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                String computed = Internal.computePop(ctx.getTracker());
                String operation = OPERATIONS[insn.getOpcode() - IFEQ];

                ctx.fmtAppend("\tif (%s.i %s 0) goto %s;", computed, operation, targetBlock);
            }
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                String opA = Internal.computePop(ctx.getTracker());
                String opB = Internal.computePop(ctx.getTracker());
                String operation = OPERATIONS[insn.getOpcode() - IF_ICMPEQ];

                ctx.fmtAppend("\tif (%s.i %s %s.i) goto %s;\n", opB, operation, opA, targetBlock);
            }
            case IF_ACMPEQ -> {
                String objA = Internal.computePop(ctx.getTracker());
                String objB = Internal.computePop(ctx.getTracker());
                String cond = "__jnt_if_cmp_" + insn.index;
                TrackedArrayValue valueA = getStackValueBefore(ctx, insn, 0);
                TrackedArrayValue valueB = getStackValueBefore(ctx, insn, 1);

                if (valueA.isCustomPrimitiveArray() || valueB.isCustomPrimitiveArray()) {
                    ctx.fmtAppend("\tjboolean %s = (%s.l == %s.l);\n", cond, objB, objA);
                } else {
                    ctx.fmtAppend(
                            "\tjboolean %s = %s;\n",
                            cond,
                            JniDispatchCodegen.callExpr(
                                    ctx,
                                    tjvm,
                                    insn,
                                    JniBindingId.IS_SAME_OBJECT,
                                    "if-acmpeq",
                                    "(jboolean (*)(JNIEnv *, jobject, jobject))",
                                    "env",
                                    objB + ".l",
                                    objA + ".l"
                            )
                    );
                }
                PrimitiveArrayUnit.maybeReleaseCustom(ctx, objA + ".l", valueA);
                PrimitiveArrayUnit.maybeReleaseCustom(ctx, objB + ".l", valueB);
                ctx.fmtAppend("\tif(%s) goto %s;\n", cond, targetBlock);
            }
            case IF_ACMPNE -> {
                String objA = Internal.computePop(ctx.getTracker());
                String objB = Internal.computePop(ctx.getTracker());
                String cond = "__jnt_if_cmp_" + insn.index;
                TrackedArrayValue valueA = getStackValueBefore(ctx, insn, 0);
                TrackedArrayValue valueB = getStackValueBefore(ctx, insn, 1);

                if (valueA.isCustomPrimitiveArray() || valueB.isCustomPrimitiveArray()) {
                    ctx.fmtAppend("\tjboolean %s = (%s.l == %s.l);\n", cond, objB, objA);
                } else {
                    ctx.fmtAppend(
                            "\tjboolean %s = %s;\n",
                            cond,
                            JniDispatchCodegen.callExpr(
                                    ctx,
                                    tjvm,
                                    insn,
                                    JniBindingId.IS_SAME_OBJECT,
                                    "if-acmpne",
                                    "(jboolean (*)(JNIEnv *, jobject, jobject))",
                                    "env",
                                    objB + ".l",
                                    objA + ".l"
                            )
                    );
                }
                PrimitiveArrayUnit.maybeReleaseCustom(ctx, objA + ".l", valueA);
                PrimitiveArrayUnit.maybeReleaseCustom(ctx, objB + ".l", valueB);
                ctx.fmtAppend("\tif(!%s) goto %s;\n", cond, targetBlock);
            }
            case IFNULL, IFNONNULL -> {
                String obj = Internal.computePop(ctx.getTracker());
                String operation = insn.getOpcode() == IFNONNULL ? "!=" : "==";
                String cond = "__jnt_if_null_" + insn.index;
                TrackedArrayValue value = getStackValueBefore(ctx, insn, 0);

                ctx.fmtAppend("\tjboolean %s = (%s.l %s NULL);\n", cond, obj, operation);
                PrimitiveArrayUnit.maybeReleaseCustom(ctx, obj + ".l", value);
                ctx.fmtAppend("\tif (%s) goto %s;\n", cond, targetBlock);
            }
        }
    }

    private static TrackedArrayValue getStackValueBefore(UnitContext ctx, JumpInsnNode insn, int depthFromTop) {
        MethodCustomArrayFlow state = ctx.getMethodArrayState();
        return state == null ? TrackedArrayValue.uninitialized() : state.getStackValueBefore(insn, depthFromTop);
    }
}
