package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.TypeInsnNode;
import war.jnt.cache.Cache;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.analysis.PrimitiveArrayKind;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;

public class TypeUnit {

    public static void process(TypeInsnNode insn, UnitContext ctx, TempJumpVM tjvm, boolean isCustomArray) {
        int idx = Cache.Companion.request_klass(insn.desc);

        switch (insn.opcode) {
            case Opcodes.NEW: {
                String computed = Internal.computePush(ctx.tracker);
                ctx.append("\t" + computed + ".l = " + JniDispatchCodegen.callExpr(
                        ctx,
                        tjvm,
                        insn,
                        JniBindingId.ALLOC_OBJECT,
                        "new",
                        "(jobject (*)(JNIEnv *, jclass))",
                        "env",
                        "request_klass(env, " + idx + ")"
                ) + ";\n");
                break;
            }
            case Opcodes.ANEWARRAY: {
                String size = Internal.computePop(ctx.tracker);
                String computed = Internal.computePush(ctx.tracker);
                int naseIdx = Cache.Companion.request_klass("java/lang/NegativeArraySizeException");
                ctx.append("\tif (" + size + ".i < 0) { "
                        + JniDispatchCodegen.throwNew(
                        ctx,
                        tjvm,
                        insn,
                        "anewarray-negative-size",
                        "request_klass(env, " + naseIdx + ")",
                        "negative array size"
                )
                        + "; goto " + ctx.handlerLabel + "; }\n");
                ctx.append("\t" + computed + ".l = " + JniDispatchCodegen.callExpr(
                        ctx,
                        tjvm,
                        insn,
                        JniBindingId.NEW_OBJECT_ARRAY,
                        "anewarray",
                        "(jobjectArray (*)(JNIEnv *, jsize, jclass, jobject))",
                        "env",
                        size + ".i",
                        "request_klass(env, " + idx + ")",
                        "NULL"
                ) + ";\n");
                break;
            }
            case Opcodes.CHECKCAST: {
                String computed = Internal.computePop(ctx.tracker);
                int cceIdx = Cache.Companion.request_klass("java/lang/ClassCastException");
                PrimitiveArrayKind targetKind = isCustomArray ? PrimitiveArrayKind.fromDescriptor(insn.desc) : null;
                if (targetKind != null && ctx.getMethodArrayState() != null) {
                    TrackedArrayValue value = ctx.getMethodArrayState().getStackValueBefore(insn, 0);
                    if (value.isCustomPrimitiveArray()) {
                        if (value.primitiveArrayKind() != targetKind) {
                            ctx.append("\t" + JniDispatchCodegen.throwNew(
                                    ctx,
                                    tjvm,
                                    insn,
                                    "checkcast-custom-mismatch",
                                    "request_klass(env, " + cceIdx + ")",
                                    "bad type cast"
                            ) + ";\n");
                            ctx.append("\tgoto " + ctx.handlerLabel + ";\n");
                        }
                    } else if (value.isUnknownPrimitiveArray() && value.primitiveArrayKind() == targetKind) {
                        ctx.getMethodArrayState().warnFallback(insn, value, "checkcast");
                    } else {
                        int oomIdx = Cache.Companion.request_klass("java/lang/OutOfMemoryError");
                        ctx.append("\tif (" + computed + ".l != NULL && !" + JniDispatchCodegen.callExpr(
                                ctx,
                                tjvm,
                                insn,
                                JniBindingId.IS_INSTANCE_OF,
                                "checkcast",
                                "(jboolean (*)(JNIEnv *, jobject, jclass))",
                                "env",
                                computed + ".l",
                                "request_klass(env, " + idx + ")"
                        ) + ") {\n");
                        ctx.append("\t\t" + JniDispatchCodegen.throwNew(
                                ctx,
                                tjvm,
                                insn,
                                "checkcast-custom-jni-mismatch",
                                "request_klass(env, " + cceIdx + ")",
                                "bad type cast"
                        ) + ";\n");
                        ctx.append("\t\tgoto " + ctx.handlerLabel + ";\n");
                        ctx.append("\t}\n");
                        ctx.append("\tif (" + computed + ".l != NULL) {\n");
                        PrimitiveArrayUnit.emitJniToCustom(ctx, tjvm, computed + ".l", targetKind, "request_klass(env, " + oomIdx + ")", computed + ".l");
                        ctx.append("\t}\n");
                    }
                } else {
                    ctx.append("\tif (" + computed + ".l != NULL && !" + JniDispatchCodegen.callExpr(
                            ctx,
                            tjvm,
                            insn,
                            JniBindingId.IS_INSTANCE_OF,
                            "checkcast-plain",
                            "(jboolean (*)(JNIEnv *, jobject, jclass))",
                            "env",
                            computed + ".l",
                            "request_klass(env, " + idx + ")"
                    ) + ") {\n");
                    ctx.append("\t\t" + JniDispatchCodegen.throwNew(
                            ctx,
                            tjvm,
                            insn,
                            "checkcast-plain-mismatch",
                            "request_klass(env, " + cceIdx + ")",
                            "bad type cast"
                    ) + ";\n");
                    ctx.append("\t\tgoto " + ctx.handlerLabel + ";\n");
                    ctx.append("\t}\n");
                }
                Internal.computePush(ctx.tracker);
                break;
            }
            case Opcodes.INSTANCEOF: {
                String computed = Internal.computePop(ctx.tracker);
                PrimitiveArrayKind targetKind = isCustomArray ? PrimitiveArrayKind.fromDescriptor(insn.desc) : null;
                if (targetKind != null && ctx.getMethodArrayState() != null) {
                    TrackedArrayValue value = ctx.getMethodArrayState().getStackValueBefore(insn, 0);
                    if (value.isCustomPrimitiveArray()) {
                        String customExpr = PrimitiveArrayUnit.snapshotPointer(
                                ctx,
                                computed + ".l",
                                "__jnt_instanceof_" + insn.index
                        );
                        ctx.fmtAppend(
                                "\t%s.i = (%s != NULL && %s) ? 1 : 0;\n",
                                computed,
                                customExpr,
                                value.primitiveArrayKind() == targetKind
                        );
                        PrimitiveArrayUnit.maybeReleaseCustom(ctx, customExpr, value);
                    } else {
                        if (value.requiresFallback()) {
                            ctx.getMethodArrayState().warnFallback(insn, value, "instanceof");
                        }
                        ctx.append("\t" + computed + ".i = " + computed + ".l != NULL && (" + JniDispatchCodegen.callExpr(
                                ctx,
                                tjvm,
                                insn,
                                JniBindingId.IS_INSTANCE_OF,
                                "instanceof",
                                "(jboolean (*)(JNIEnv *, jobject, jclass))",
                                "env",
                                computed + ".l",
                                "request_klass(env, " + idx + ")"
                        ) + ") ? 1 : 0;\n");
                    }
                } else {
                    ctx.append("\t" + computed + ".i = " + computed + ".l != NULL && (" + JniDispatchCodegen.callExpr(
                            ctx,
                            tjvm,
                            insn,
                            JniBindingId.IS_INSTANCE_OF,
                            "instanceof-plain",
                            "(jboolean (*)(JNIEnv *, jobject, jclass))",
                            "env",
                            computed + ".l",
                            "request_klass(env, " + idx + ")"
                    ) + ") ? 1 : 0;\n");
                }
                Internal.computePush(ctx.tracker);
                break;
            }
        }
    }
}
