package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.analysis.PrimitiveArrayKind;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.core.code.analysis.flow.MethodCustomArrayFlow;
import war.jnt.core.code.bindings.JniBindingCatalog;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;
import war.jnt.innercache.InnerCache;

public class ArrayHandlerUnit implements Opcodes {

    public static void process(final InnerCache ic, InsnNode insn, UnitContext ctx, TempJumpVM tjvm) {
        String npe = ic.FindClass("java/lang/NullPointerException");
        String aob = ic.FindClass("java/lang/ArrayIndexOutOfBoundsException");
        String oom = ic.FindClass("java/lang/OutOfMemoryError");

        switch (insn.getOpcode()) {
            case AALOAD -> processObjectLoad(insn, ctx, tjvm, npe, aob, oom);
            case AASTORE -> processObjectStore(insn, ctx, tjvm, npe, aob, oom);
            case IALOAD, BALOAD, CALOAD, SALOAD, LALOAD, FALOAD, DALOAD -> processPrimitiveLoad(insn, ctx, tjvm, npe, aob);
            case IASTORE, BASTORE, CASTORE, SASTORE, LASTORE, FASTORE, DASTORE -> processPrimitiveStore(insn, ctx, tjvm, npe, aob);
        }
    }

    private static void processObjectLoad(InsnNode insn, UnitContext ctx, TempJumpVM tjvm, String npe, String aob, Object oom) {
        String idx = Internal.computePop(ctx.getTracker());
        String arr = Internal.computePop(ctx.getTracker());
        String out = Internal.computePush(ctx.getTracker());

        /*ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "aaload-null-array", npe, "array is null"), ctx.handlerLabel);
        ctx.fmtAppend("\tif (%s.i < 0 || %s.i >= %s) { %s; goto %s; }\n", idx, idx,
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.GET_ARRAY_LENGTH, "aaload-length", "(jsize (*)(JNIEnv *, jarray))", "env", arr + ".l"),
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "aaload-bounds", aob, "array index out of bounds"), ctx.handlerLabel);*/

        ctx.fmtAppend("\t%s.l = %s;\n", out, JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.GET_OBJECT_ARRAY_ELEMENT, "aaload", "(jobject (*)(JNIEnv *, jobjectArray, jsize))", "env", arr + ".l", idx + ".i"));

        MethodCustomArrayFlow state = ctx.getMethodArrayState();
        TrackedArrayValue produced = state == null ? TrackedArrayValue.uninitialized() : state.getStackValueAfter(insn, 0);
        if (produced.isCustomPrimitiveArray()) {
            PrimitiveArrayUnit.emitJniToCustom(ctx, tjvm, out + ".l", produced.primitiveArrayKind(), oom, out + ".l");
        } else if (produced.requiresFallback()) {
            ctx.getMethodArrayState().warnFallback(insn, produced, "object array load");
        }
    }

    private static void processObjectStore(InsnNode insn, UnitContext ctx, TempJumpVM tjvm, String npe, String aob, Object oom) {
        String val = Internal.computePop(ctx.getTracker());
        String idx = Internal.computePop(ctx.getTracker());
        String arr = Internal.computePop(ctx.getTracker());
        String valueExpr = val + ".l";

        /*ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "aastore-null-array", npe, "array is null"), ctx.handlerLabel);
        ctx.fmtAppend("\tif (%s.i < 0 || %s.i >= %s) { %s; goto %s; }\n", idx, idx,
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.GET_ARRAY_LENGTH, "aastore-length", "(jsize (*)(JNIEnv *, jarray))", "env", arr + ".l"),
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "aastore-bounds", aob, "array index out of bounds"), ctx.handlerLabel);*/

        TrackedArrayValue storedValue = getStackValueBefore(ctx, insn, 0);
        if (storedValue.isCustomPrimitiveArray()) {
            String jniTemp = "__jnt_aastore_" + insn.index + "_" + ctx.getArgManager().args;
            ctx.fmtAppend("\tjobject %s = NULL;\n", jniTemp);
            PrimitiveArrayUnit.emitCustomToNewJni(ctx, tjvm, valueExpr, storedValue.primitiveArrayKind(), oom, jniTemp);
            valueExpr = jniTemp;
        } else if (storedValue.requiresFallback()) {
            ctx.getMethodArrayState().warnFallback(insn, storedValue, "object array store");
        }

        if (valueExpr.equals(val + ".l")) {
            ctx.fmtAppend("""
                    \t%s;
                    """,
                    JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "aastore",
                            "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", arr + ".l", idx + ".i", val + ".l"));
        } else {
            ctx.fmtAppend("""
                    \t%s;
                    """,
                    JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "aastore-bridged",
                            "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", arr + ".l", idx + ".i", valueExpr));
        }

        if (!valueExpr.equals(val + ".l")) {
            PrimitiveArrayUnit.emitDeleteLocalRef(ctx, tjvm, valueExpr);
        }

        PrimitiveArrayUnit.maybeReleaseCustom(ctx, val + ".l", storedValue);
    }

    private static void processPrimitiveLoad(InsnNode insn, UnitContext ctx, TempJumpVM tjvm, String npe, String aob) {
        PrimitiveArrayKind kind = resolveArrayKind(ctx, insn, 1, "primitive array load");
        if (kind != null && isCustomArray(ctx, insn, 1, kind)) {
            emitCustomLoad(ctx, tjvm, insn, kind, npe, aob);
            return;
        }

        if (requiresHybridArrayAccess(ctx, insn, 1)) {
            emitHybridLoad(ctx, tjvm, insn, kind != null ? kind : fallbackKind(insn), npe, aob);
            return;
        }

        emitJniLoad(ctx, tjvm, insn, kind != null ? kind : fallbackKind(insn), npe, aob);
    }

    private static void processPrimitiveStore(InsnNode insn, UnitContext ctx, TempJumpVM tjvm, String npe, String aob) {
        PrimitiveArrayKind kind = resolveArrayKind(ctx, insn, 2, "primitive array store");
        if (kind != null && isCustomArray(ctx, insn, 2, kind)) {
            emitCustomStore(ctx, tjvm, insn, kind, npe, aob);
            return;
        }

        if (requiresHybridArrayAccess(ctx, insn, 2)) {
            emitHybridStore(ctx, tjvm, insn, kind != null ? kind : fallbackKind(insn), npe, aob);
            return;
        }

        emitJniStore(ctx, tjvm, insn, kind != null ? kind : fallbackKind(insn), npe, aob);
    }

    private static PrimitiveArrayKind resolveArrayKind(UnitContext ctx, InsnNode insn, int depthFromTop, String action) {
        MethodCustomArrayFlow state = ctx.getMethodArrayState();
        if (state == null) {
            return null;
        }

        TrackedArrayValue value = state.getStackValueBefore(insn, depthFromTop);
        if (value.requiresFallback()) {
            state.warnFallback(insn, value, action);
        }

        return value.primitiveArrayKind();
    }

    private static boolean isCustomArray(UnitContext ctx, InsnNode insn, int depthFromTop, PrimitiveArrayKind expectedKind) {
        MethodCustomArrayFlow state = ctx.getMethodArrayState();
        if (state == null) {
            return false;
        }

        TrackedArrayValue value = state.getStackValueBefore(insn, depthFromTop);
        return value.isCustomPrimitiveArray() && value.primitiveArrayKind() == expectedKind && expectedKind.matchesArrayOpcode(insn.getOpcode());
    }

    private static PrimitiveArrayKind fallbackKind(InsnNode insn) {
        return switch (insn.getOpcode()) {
            case BALOAD, BASTORE -> PrimitiveArrayKind.BYTE;
            case CALOAD, CASTORE -> PrimitiveArrayKind.CHAR;
            case SALOAD, SASTORE -> PrimitiveArrayKind.SHORT;
            case IALOAD, IASTORE -> PrimitiveArrayKind.INT;
            case LALOAD, LASTORE -> PrimitiveArrayKind.LONG;
            case FALOAD, FASTORE -> PrimitiveArrayKind.FLOAT;
            case DALOAD, DASTORE -> PrimitiveArrayKind.DOUBLE;
            default -> null;
        };
    }

    private static boolean requiresHybridArrayAccess(UnitContext ctx, InsnNode insn, int depthFromTop) {
        MethodCustomArrayFlow state = ctx.getMethodArrayState();
        if (state == null) {
            return false;
        }

        return state.getStackValueBefore(insn, depthFromTop).requiresFallback();
    }

    private static TrackedArrayValue getStackValueBefore(UnitContext ctx, InsnNode insn, int depthFromTop) {
        MethodCustomArrayFlow state = ctx.getMethodArrayState();
        return state == null ? TrackedArrayValue.uninitialized() : state.getStackValueBefore(insn, depthFromTop);
    }

    private static void emitCustomLoad(UnitContext ctx, TempJumpVM tjvm, InsnNode insn, PrimitiveArrayKind kind, String npe, String aob) {
        String idx = Internal.computePop(ctx.getTracker());
        String arr = Internal.computePop(ctx.getTracker());
        String out = Internal.computePush(ctx.getTracker());
        TrackedArrayValue arrayValue = getStackValueBefore(ctx, insn, 1);
        /*ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "custom-load-null-array", npe, "array is null"), ctx.handlerLabel);
        ctx.fmtAppend("\tif (%s.i < 0 || %s.i >= getArrayLength(%s.l)) { %s; goto %s; }\n", idx, idx, arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "custom-load-bounds", aob, "array index out of bounds"), ctx.handlerLabel);*/
        String arrayExpr = PrimitiveArrayUnit.snapshotPointer(ctx, arr + ".l", "__jnt_custom_load_" + insn.index);
        ctx.fmtAppend("\t%s%s = ((%s*) %s)->data[%s.i];\n", out, kind.stackField(), kind.customStructType(), arrayExpr, idx);
        PrimitiveArrayUnit.maybeReleaseCustom(ctx, arrayExpr, arrayValue);
    }

    private static void emitCustomStore(UnitContext ctx, TempJumpVM tjvm, InsnNode insn, PrimitiveArrayKind kind, String npe, String aob) {
        String val = Internal.computePop(ctx.getTracker());
        String idx = Internal.computePop(ctx.getTracker());
        String arr = Internal.computePop(ctx.getTracker());
        /*ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", arr, JniDispatchCodegen.throwNew(ctx, tjvm, insn, "custom-store-null-array", npe, "array is null"), ctx.handlerLabel);
        ctx.fmtAppend("\tif (%s.i < 0 || %s.i >= getArrayLength(%s.l)) { %s; goto %s; }\n", idx, idx, arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "custom-store-bounds", aob, "array index out of bounds"), ctx.handlerLabel);*/
        ctx.fmtAppend("\t((%s*) %s.l)->data[%s.i] = %s%s;\n", kind.customStructType(), arr, idx, val, kind.stackField());
        PrimitiveArrayUnit.maybeReleaseCustom(ctx, arr + ".l", getStackValueBefore(ctx, insn, 2));
    }

    private static void emitJniLoad(UnitContext ctx, TempJumpVM tjvm, InsnNode insn, PrimitiveArrayKind kind, String npe, String aob) {
        String idx = Internal.computePop(ctx.getTracker());
        String arr = Internal.computePop(ctx.getTracker());
        String out = Internal.computePush(ctx.getTracker());

        /*ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", arr, JniDispatchCodegen.throwNew(ctx, tjvm, insn, "jni-load-null-array", npe, "array is null"), ctx.handlerLabel);
        ctx.fmtAppend("\tif (%s.i < 0 || %s.i >= %s) { %s; goto %s; }\n", idx, idx,
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.GET_ARRAY_LENGTH, "xaload-length", "(jsize (*)(JNIEnv *, jarray))", "env", arr + ".l"),
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "jni-load-bounds", aob, "array index out of bounds"),
                ctx.handlerLabel);*/

        ctx.append("\t{\n");
        ctx.fmtAppend("\t\t%s __temp;\n", kind.componentType());
        ctx.fmtAppend("\t\t%s;\n", JniDispatchCodegen.callExpr(ctx, tjvm, insn,
                        JniBindingCatalog.byFunction(kind.getRegionFunction()), "xaload", "(void (*)(JNIEnv *, " +
                        kind.jniArrayType() + ", jsize, jsize, " + kind.componentType() + " *))", "env", arr + ".l", idx + ".i", "1", "&__temp"));
        ctx.fmtAppend("\t\t%s%s = __temp;\n", out, kind.stackField());
        ctx.append("\t}\n");
    }

    private static void emitJniStore(UnitContext ctx, TempJumpVM tjvm, InsnNode insn, PrimitiveArrayKind kind, String npe, String aob) {
        String val = Internal.computePop(ctx.getTracker());
        String idx = Internal.computePop(ctx.getTracker());
        String arr = Internal.computePop(ctx.getTracker());

        /*ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "jni-store-null-array", npe, "array is null"), ctx.handlerLabel);
        ctx.fmtAppend("\tif (%s.i < 0 || %s.i >= %s) { %s; goto %s; }\n", idx, idx,
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.GET_ARRAY_LENGTH, "xastore-length", "(jsize (*)(JNIEnv *, jarray))", "env", arr + ".l"),
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "jni-store-bounds", aob, "array index out of bounds"),
                ctx.handlerLabel);*/
        ctx.fmtAppend("""
                \t%s;
                """,
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingCatalog.byFunction(kind.setRegionFunction()), "xastore",
                        "(void (*)(JNIEnv *, " + kind.jniArrayType() + ", jsize, jsize, " + kind.componentType() + " *))",
                        "env", arr + ".l", idx + ".i", "1", "&" + val + kind.stackField())
        );
    }

    private static void emitHybridLoad(UnitContext ctx, TempJumpVM tjvm, InsnNode insn, PrimitiveArrayKind kind, String npe, String aob) {
        String idx = Internal.computePop(ctx.getTracker());
        String arr = Internal.computePop(ctx.getTracker());
        String out = Internal.computePush(ctx.getTracker());

        ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "hybrid-load-null-array", npe, "array is null"), ctx.handlerLabel);
        ctx.fmtAppend("\tif (isCustomArrayPtr(%s.l)) {\n", arr);
        ctx.fmtAppend("\t\tif (%s.i < 0 || %s.i >= getArrayLength(%s.l)) { %s; goto %s; }\n", idx, idx, arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "hybrid-custom-load-bounds", aob, "array index out of bounds"), ctx.handlerLabel);
        ctx.fmtAppend("\t\t%s%s = ((%s*) %s.l)->data[%s.i];\n", out, kind.stackField(), kind.customStructType(), arr, idx);
        ctx.fmtAppend("\t} else {\n");
        ctx.fmtAppend("\t\tif (%s.i < 0 || %s.i >= %s) { %s; goto %s; }\n", idx, idx,
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.GET_ARRAY_LENGTH, "hybrid-xaload-length",
                        "(jsize (*)(JNIEnv *, jarray))", "env", arr + ".l"),
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "hybrid-jni-load-bounds", aob, "array index out of bounds"),
                ctx.handlerLabel);
        ctx.append("\t\t{\n");
        ctx.fmtAppend("\t\t\t%s __temp;\n", kind.componentType());
        ctx.fmtAppend("\t\t\t%s;\n",
                JniDispatchCodegen.callExpr(ctx, tjvm, insn,
                        JniBindingCatalog.byFunction(kind.getRegionFunction()), "hybrid-xaload", "(void (*)(JNIEnv *, " +
                                kind.jniArrayType() + ", jsize, jsize, " + kind.componentType() + " *))", "env", arr + ".l", idx + ".i", "1", "&__temp"));
        ctx.fmtAppend("\t\t\t%s%s = __temp;\n", out, kind.stackField());
        ctx.append("\t\t}\n");
        ctx.fmtAppend("\t}\n");
    }

    private static void emitHybridStore(UnitContext ctx, TempJumpVM tjvm, InsnNode insn, PrimitiveArrayKind kind, String npe, String aob) {
        String val = Internal.computePop(ctx.getTracker());
        String idx = Internal.computePop(ctx.getTracker());
        String arr = Internal.computePop(ctx.getTracker());

        ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "hybrid-store-null-array", npe, "array is null"), ctx.handlerLabel);
        ctx.fmtAppend("\tif (isCustomArrayPtr(%s.l)) {\n", arr);
        ctx.fmtAppend("\t\tif (%s.i < 0 || %s.i >= getArrayLength(%s.l)) { %s; goto %s; }\n", idx, idx, arr,
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "hybrid-custom-store-bounds", aob, "array index out of bounds"), ctx.handlerLabel);
        ctx.fmtAppend("\t\t((%s*) %s.l)->data[%s.i] = %s%s;\n", kind.customStructType(), arr, idx, val, kind.stackField());
        ctx.fmtAppend("\t} else {\n");
        ctx.fmtAppend("\t\tif (%s.i < 0 || %s.i >= %s) { %s; goto %s; }\n", idx, idx,
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.GET_ARRAY_LENGTH, "hybrid-xastore-length",
                        "(jsize (*)(JNIEnv *, jarray))", "env", arr + ".l"),
                JniDispatchCodegen.throwNew(ctx, tjvm, insn, "hybrid-jni-store-bounds", aob, "array index out of bounds"),
                ctx.handlerLabel);
        ctx.fmtAppend("\t\t%s;\n",
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingCatalog.byFunction(kind.setRegionFunction()), "hybrid-xastore",
                        "(void (*)(JNIEnv *, " + kind.jniArrayType() + ", jsize, jsize, " + kind.componentType() + " *))",
                        "env", arr + ".l", idx + ".i", "1", "&" + val + kind.stackField()));
        ctx.fmtAppend("\t}\n");
    }
}
