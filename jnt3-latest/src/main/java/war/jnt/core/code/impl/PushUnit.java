package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.IntInsnNode;
import war.jnt.cache.Cache;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;
import war.jnt.obfuscation.MutatedNumeric;

public class PushUnit implements Opcodes {

    private static final String[] arrays = {
            "NewBooleanArray",
            "NewCharArray",
            "NewFloatArray",
            "NewDoubleArray",
            "NewByteArray",
            "NewShortArray",
            "NewIntArray",
            "NewLongArray",
    };

    private static final String[] customArrays = {
            "newBooleanArray",
            "newCharArray",
            "newFloatArray",
            "newDoubleArray",
            "newByteArray",
            "newShortArray",
            "newIntArray",
            "newLongArray",
    };

    private static final JniBindingId[] jniArrays = {
            JniBindingId.NEW_BOOLEAN_ARRAY,
            JniBindingId.NEW_CHAR_ARRAY,
            JniBindingId.NEW_FLOAT_ARRAY,
            JniBindingId.NEW_DOUBLE_ARRAY,
            JniBindingId.NEW_BYTE_ARRAY,
            JniBindingId.NEW_SHORT_ARRAY,
            JniBindingId.NEW_INT_ARRAY,
            JniBindingId.NEW_LONG_ARRAY,
    };

    public static void process(IntInsnNode insn, UnitContext ctx, TempJumpVM tjvm, boolean isCustomArray) {
        if (insn.getOpcode() == NEWARRAY) {
            processNewArray(insn, ctx, tjvm, isCustomArray);
            return;
        }

        String computed = Internal.computePush(ctx.getTracker());

        var mutator = new MutatedNumeric(computed, insn.operand, Type.INT_TYPE);
        ctx.append(mutator.get());
    }

    private static void processNewArray(IntInsnNode insn, UnitContext ctx, TempJumpVM tjvm, boolean isCustomArray) {
        String arrayType;
        if (isCustomArray) {
            arrayType = customArrays[insn.operand - 4];
        } else {
            arrayType = arrays[insn.operand - 4];
        }

        String len = Internal.computePop(ctx.getTracker());
        String push = Internal.computePush(ctx.getTracker());
        int naseIdx = Cache.Companion.request_klass("java/lang/NegativeArraySizeException");

        ctx.fmtAppend(
                "\tif (%s.i < 0) { %s; goto %s; }\n",
                len,
                JniDispatchCodegen.throwNew(
                        ctx,
                        tjvm,
                        insn,
                        "newarray-negative-size",
                        "request_klass(env, " + naseIdx + ")",
                        "negative array size"
                ),
                ctx.handlerLabel
        );

        if (isCustomArray) {
            ctx.fmtAppend("""
                    \t%s.l = %s(%s.i);
                    """,
                    push, arrayType, len);
            int oomIdx = Cache.Companion.request_klass("java/lang/OutOfMemoryError");
            ctx.fmtAppend(
                    "\tif (%s.l == NULL) { %s; goto %s; }\n",
                    push,
                    JniDispatchCodegen.throwNew(
                            ctx,
                            tjvm,
                            insn,
                            "newarray-custom-oom",
                            "request_klass(env, " + oomIdx + ")",
                            "failed to allocate custom array"
                    ),
                    ctx.handlerLabel
            );
        } else {
            ctx.fmtAppend(
                    "\t%s.l = %s;\n",
                    push,
                    JniDispatchCodegen.callExpr(
                            ctx,
                            tjvm,
                            insn,
                            jniArrays[insn.operand - 4],
                            "newarray-jni",
                            "(jarray (*)(JNIEnv *, jsize))",
                            "env",
                            len + ".i"
                    )
            );
        }
    }
}
