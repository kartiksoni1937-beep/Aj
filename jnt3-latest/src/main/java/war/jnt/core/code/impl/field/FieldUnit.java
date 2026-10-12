package war.jnt.core.code.impl.field;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.analysis.PrimitiveArrayKind;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.core.code.bindings.JniBindingCatalog;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.code.impl.PrimitiveArrayUnit;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;
import war.jnt.fusebox.impl.VariableManager;
import war.jnt.innercache.CacheMemberInfo;
import war.jnt.innercache.InnerCache;

public class FieldUnit {

    public static void process(InnerCache ic, FieldInsnNode insn, UnitContext ctx, TempJumpVM tjvm, VariableManager varMan, boolean isCustomArray) {
        FieldOffsetInfo func = Internal.resolveFieldOffset(insn.desc, insn.getOpcode());

        String klassName = ic.FindClass(insn.owner);
        String type = Internal.fromFieldType(insn.desc);
        String npe = ic.FindClass("java/lang/NullPointerException");
        Object oom = ic.FindClass("java/lang/OutOfMemoryError");
        JniBindingId bindingId = JniBindingCatalog.byFieldAccess(insn.desc, insn.getOpcode());

        switch (insn.getOpcode()) {
            case Opcodes.GETSTATIC -> {
                String name = ic.GetStaticField(new CacheMemberInfo(insn.owner, insn.name, insn.desc));
                String push = Internal.computePush(ctx.getTracker());

                ctx.append("\t" + push + type + " = " + JniDispatchCodegen.callExpr(
                        ctx,
                        tjvm,
                        insn,
                        bindingId,
                        "getstatic",
                        "(" + func.getValue() + " (*)(JNIEnv *, jclass, jfieldID))",
                        "env",
                        klassName,
                        name
                ) + ";\n");

                maybeNormalizeFieldRead(insn, ctx, tjvm, push + ".l", oom);

                ctx.getFieldMan().fields++;
                ctx.getFieldMan().classes++;
            }
            case Opcodes.GETFIELD -> {
                String object = Internal.computePop(ctx.getTracker());
                String push = Internal.computePush(ctx.getTracker());

                ctx.fmtAppend(
                        "\tif (%s.l == NULL) { %s; goto %s; }\n",
                        object,
                        JniDispatchCodegen.throwNew(
                                ctx,
                                tjvm,
                                insn,
                                "getfield-null-instance",
                                npe,
                                "instance is null " + ((int) (Math.random() * 1000))
                        ),
                        ctx.handlerLabel
                );
                String name = ic.GetVirtualField(new CacheMemberInfo(insn.owner, insn.name, insn.desc));

                ctx.fmtAppend(
                        "\t%s%s = %s;\n",
                        push,
                        type,
                        JniDispatchCodegen.callExpr(
                                ctx,
                                tjvm,
                                insn,
                                bindingId,
                                "getfield",
                                "(" + func.getValue() + " (*)(JNIEnv *, jobject, jfieldID))",
                                "env",
                                object + ".l",
                                name
                        )
                );

                maybeNormalizeFieldRead(insn, ctx, tjvm, push + ".l", oom);

                ctx.getFieldMan().fields++;
                ctx.getFieldMan().classes++;
            }
            case Opcodes.PUTSTATIC -> {
                String name = ic.GetStaticField(new CacheMemberInfo(insn.owner, insn.name, insn.desc));
                String value = Internal.computePop(ctx.getTracker());
                FieldWriteBridge bridge = maybeBridgeFieldWrite(insn, ctx, tjvm, value + ".l", oom);
                String fieldValueExpr = bridge != null ? bridge.jniExpr() : value + type;

                ctx.append("\t" + JniDispatchCodegen.callExpr(
                        ctx,
                        tjvm,
                        insn,
                        bindingId,
                        "putstatic",
                        "(void (*)(JNIEnv *, jclass, jfieldID, " + func.getValue() + "))",
                        "env",
                        klassName,
                        name,
                        fieldValueExpr
                ) + ";\n");
                if (bridge != null) {
                    PrimitiveArrayUnit.emitDeleteLocalRef(ctx, tjvm, bridge.jniExpr());
                    PrimitiveArrayUnit.maybeReleaseCustom(ctx, bridge.customExpr(), bridge.value());
                }

                ctx.getFieldMan().fields++;
                ctx.getFieldMan().classes++;
            }
            case Opcodes.PUTFIELD -> {
                String value = Internal.computePop(ctx.getTracker());
                String object = Internal.computePop(ctx.getTracker());
                String name = ic.GetVirtualField(new CacheMemberInfo(insn.owner, insn.name, insn.desc));

                ctx.fmtAppend(
                        "\tif (%s.l == NULL) { %s; goto %s; }\n",
                        object,
                        JniDispatchCodegen.throwNew(
                                ctx,
                                tjvm,
                                insn,
                                "putfield-null-instance",
                                npe,
                                "instance is null " + ((int) (Math.random() * 1000))
                        ),
                        ctx.handlerLabel
                );

                FieldWriteBridge bridge = maybeBridgeFieldWrite(insn, ctx, tjvm, value + ".l", oom);
                String fieldValueExpr = bridge != null ? bridge.jniExpr() : value + type;
                ctx.append("\t" + JniDispatchCodegen.callExpr(
                        ctx,
                        tjvm,
                        insn,
                        bindingId,
                        "putfield",
                        "(void (*)(JNIEnv *, jobject, jfieldID, " + func.getValue() + "))",
                        "env",
                        object + ".l",
                        name,
                        fieldValueExpr
                ) + ";\n");
                if (bridge != null) {
                    PrimitiveArrayUnit.emitDeleteLocalRef(ctx, tjvm, bridge.jniExpr());
                    PrimitiveArrayUnit.maybeReleaseCustom(ctx, bridge.customExpr(), bridge.value());
                }

                ctx.getFieldMan().fields++;
                ctx.getFieldMan().classes++;
            }
        }
    }

    private static void maybeNormalizeFieldRead(FieldInsnNode insn, UnitContext ctx, TempJumpVM tjvm, String stackExpr, Object oom) {
        if (ctx.getMethodArrayState() == null) {
            return;
        }
        TrackedArrayValue produced = ctx.getMethodArrayState().getStackValueAfter(insn, 0);
        if (produced.isCustomPrimitiveArray()) {
            PrimitiveArrayUnit.emitJniToCustom(ctx, tjvm, stackExpr, produced.primitiveArrayKind(), oom, stackExpr);
        } else if (produced.requiresFallback()) {
            ctx.getMethodArrayState().warnFallback(insn, produced, "field read");
        }
    }

    private static FieldWriteBridge maybeBridgeFieldWrite(FieldInsnNode insn, UnitContext ctx, TempJumpVM tjvm, String stackExpr, Object oom) {
        if (ctx.getMethodArrayState() == null) {
            return null;
        }
        TrackedArrayValue value = ctx.getMethodArrayState().getStackValueBefore(insn, 0);
        PrimitiveArrayKind kind = value.primitiveArrayKind();
        if (value.isCustomPrimitiveArray() && kind != null) {
            String jniTemp = "__jnt_field_" + insn.index + "_" + ctx.getFieldMan().fields;
            ctx.fmtAppend("\tjobject %s = NULL;\n", jniTemp);
            PrimitiveArrayUnit.emitCustomToNewJni(ctx, tjvm, stackExpr, kind, oom, jniTemp);
            return new FieldWriteBridge(jniTemp, stackExpr, value);
        }
        if (value.requiresFallback()) {
            ctx.getMethodArrayState().warnFallback(insn, value, "field write");
        }
        return null;
    }

    private record FieldWriteBridge(String jniExpr, String customExpr, TrackedArrayValue value) {}
}
