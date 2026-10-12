package war.jnt.core.code.impl;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.Type;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.analysis.PrimitiveArrayKind;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.core.code.analysis.flow.MethodCustomArrayFlow;
import war.jnt.core.code.bindings.JniBindingCatalog;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.TypeMaps;

public final class PrimitiveArrayUnit {

    private static final String ARRAY_SCOPE_NAME = "__jnt_array_scope";
    private static final String ARRAY_EPILOGUE_LABEL = "__jnt_array_epilogue";
    private static final String RETURN_FLAG_NAME = "__jnt_has_return";
    private static final String RETURN_VALUE_NAME = "__jnt_return_value";

    public static String arrayScopeName() {
        return ARRAY_SCOPE_NAME;
    }

    public static String arrayEpilogueLabel() {
        return ARRAY_EPILOGUE_LABEL;
    }

    public static String returnFlagName() {
        return RETURN_FLAG_NAME;
    }

    public static String returnValueName() {
        return RETURN_VALUE_NAME;
    }

    public static void emitArrayScopeDeclaration(UnitContext ctx) {
        ctx.fmtAppend("\tJNTArrayScope %s = {0};\n", ARRAY_SCOPE_NAME);
    }

    public static void emitReturnStateDeclaration(UnitContext ctx, Type returnType) {
        if (returnType.getSort() == Type.VOID) {
            return;
        }

        TypeMaps maps = new TypeMaps();
        ctx.fmtAppend("\tjboolean %s = JNI_FALSE;\n", RETURN_FLAG_NAME);
        ctx.fmtAppend("\t%s %s = %s;\n", maps.fromAsm(returnType), RETURN_VALUE_NAME, defaultReturnValue(returnType));
    }

    public static void emitArrayScopeEnter(UnitContext ctx) {
        ctx.fmtAppend("\tjnt_array_scope_enter(&%s);\n", ARRAY_SCOPE_NAME);
    }

    public static void emitArrayScopeCleanup(UnitContext ctx) {
        ctx.fmtAppend("\tjnt_array_scope_cleanup(&%s);\n", ARRAY_SCOPE_NAME);
    }

    public static void emitArrayScopeLeave(UnitContext ctx) {
        ctx.fmtAppend("\tjnt_array_scope_leave(&%s);\n", ARRAY_SCOPE_NAME);
    }

    public static boolean isTrackedCustom(TrackedArrayValue value) {
        return value != null && value.isCustomPrimitiveArray();
    }

    public static void emitRetainArrayPtr(UnitContext ctx, String expr) {
        ctx.fmtAppend("\tretainArrayPtr(%s);\n", expr);
    }

    public static void emitReleaseArrayPtr(UnitContext ctx, String expr) {
        ctx.fmtAppend("\treleaseArrayPtr(%s);\n", expr);
    }

    public static void maybeRetainCustom(UnitContext ctx, String expr, TrackedArrayValue value) {
        if (isTrackedCustom(value)) {
            emitRetainArrayPtr(ctx, expr);
        }
    }

    public static void maybeReleaseCustom(UnitContext ctx, String expr, TrackedArrayValue value) {
        if (isTrackedCustom(value)) {
            emitReleaseArrayPtr(ctx, expr);
        }
    }

    public static String snapshotPointer(UnitContext ctx, String expr, String prefix) {
        String tempName = prefix + "_" + ctx.getArgManager().args;
        ctx.fmtAppend("\tvoid *%s = %s;\n", tempName, expr);
        return tempName;
    }

    public static void emitMoveOwnerToLocal(UnitContext ctx, int localIndex, String stackExpr) {
        ctx.fmtAppend("\tlocals[%d].l = %s.l;\n", localIndex, stackExpr);
        ctx.fmtAppend("\t%s.l = NULL;\n", stackExpr);
    }

    public static void emitDeadStackSlotCleanup(UnitContext ctx, AbstractInsnNode insn) {
        MethodCustomArrayFlow state = ctx.getMethodArrayState();
        if (state == null) {
            return;
        }

        int beforeSize = state.getStackSizeBefore(insn);
        int afterSize = state.getStackSizeAfter(insn);
        for (int i = afterSize; i < beforeSize; i++) {
            TrackedArrayValue value = state.getStackValueBeforeAt(insn, i);
            if (value.isCustomPrimitiveArray()) {
                ctx.fmtAppend("\tstack[%d].l = NULL;\n", i);
            }
        }
    }

    public static String defaultReturnValue(Type returnType) {
        return switch (returnType.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> "0";
            case Type.FLOAT -> "0.0f";
            case Type.LONG -> "0L";
            case Type.DOUBLE -> "0.0";
            case Type.ARRAY, Type.OBJECT -> "NULL";
            default -> "";
        };
    }

    public static void emitJniToCustom(UnitContext ctx, TempJumpVM tjvm, String sourceExpr, PrimitiveArrayKind kind, Object oomClass, String targetExpr) {
        String sitePrefix = "PrimitiveArrayCodegen#emitJniToCustom#" + kind.name() + "#" + sourceExpr + "#" + targetExpr;
        ctx.fmtAppend("\tif (%s != NULL) {\n", sourceExpr);
        ctx.fmtAppend("\t\tjsize len = %s;\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.GET_ARRAY_LENGTH, sitePrefix + "#length", "(jsize (*)(JNIEnv *, jarray))", "env", sourceExpr));
        ctx.fmtAppend("\t\t%s *out = %s(len);\n", kind.customStructType(), kind.customAllocator());
        ctx.fmtAppend("\t\tif (out == NULL) { %s; goto %s; }\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.THROW_NEW, sitePrefix + "#oom", "(jint (*)(JNIEnv *, jclass, const char *))", "env", oomClass.toString(), "\"failed to allocate custom array\""),
                ctx.handlerLabel);
        ctx.append("\t\tif (len > 0) {\n");
        ctx.fmtAppend("\t\t\t%s;\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingCatalog.byFunction(kind.getRegionFunction()), sitePrefix + "#getRegion",
                        "(void (*)(JNIEnv *, " + kind.jniArrayType() + ", jsize, jsize, " + kind.componentType() + " *))",
                        "env", sourceExpr, "0", "len", "out->data"));
        ctx.append("\t\t}\n");
        ctx.fmtAppend("\t\t%s = out;\n", targetExpr);
        ctx.append("\t}\n");
    }

    public static void emitCustomToNewJni(UnitContext ctx, TempJumpVM tjvm, String sourceExpr, PrimitiveArrayKind kind, Object oomClass, String targetExpr) {
        String sitePrefix = "PrimitiveArrayCodegen#emitCustomToNewJni#" + kind.name() + "#" + sourceExpr + "#" + targetExpr;
        if (!sourceExpr.equals(targetExpr)) {
            ctx.fmtAppend("\t%s = NULL;\n", targetExpr);
        }
        ctx.fmtAppend("\tif (%s != NULL) {\n", sourceExpr);
        ctx.fmtAppend("\t\t%s temp = %s;\n", kind.jniArrayType(),
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingCatalog.byFunction(kind.newJniArrayFunction()), sitePrefix +
                        "#newArray", "(" + kind.jniArrayType() + " (*)(JNIEnv *, jsize))", "env", "getArrayLength(" + sourceExpr + ")"));
        ctx.fmtAppend("\t\tif (temp == NULL) { %s; goto %s; }\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.THROW_NEW, sitePrefix + "#oom",
                        "(jint (*)(JNIEnv *, jclass, const char *))", "env", oomClass.toString(), "\"failed to allocate array\""),
                ctx.handlerLabel);
        ctx.append("\t\tif (getArrayLength(" + sourceExpr + ") > 0) {\n");
        ctx.fmtAppend("\t\t\t%s;\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingCatalog.byFunction(kind.setRegionFunction()), sitePrefix + "#setRegion",
                        "(void (*)(JNIEnv *, " + kind.jniArrayType() + ", jsize, jsize, const " + kind.componentType() + " *))",
                        "env", "temp", "0", "getArrayLength(" + sourceExpr + ")", "((" + kind.customStructType() + "*) " + sourceExpr + ")->data"));
        ctx.append("\t\t}\n");
        ctx.fmtAppend("\t\t%s = temp;\n", targetExpr);
        ctx.append("\t}\n");
    }

    public static void emitCopyJniBackToCustom(UnitContext ctx, TempJumpVM tjvm, String jniExpr, String customExpr, PrimitiveArrayKind kind) {
        String sitePrefix = "PrimitiveArrayCodegen#emitCopyJniBackToCustom#" + kind.name() + "#" + jniExpr + "#" + customExpr;
        ctx.fmtAppend("\tif (%s != NULL && %s != NULL) {\n", jniExpr, customExpr);
        ctx.fmtAppend("\t\tjsize len = %s;\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.GET_ARRAY_LENGTH, sitePrefix + "#length", "(jsize (*)(JNIEnv *, jarray))", "env", jniExpr));
        ctx.append("\t\tif (len > 0) {\n");
        ctx.fmtAppend("\t\t\t%s;\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingCatalog.byFunction(kind.getRegionFunction()), sitePrefix + "#getRegion",
                        "(void (*)(JNIEnv *, " + kind.jniArrayType() + ", jsize, jsize, " + kind.componentType() + " *))",
                        "env", jniExpr, "0", "len", "((" + kind.customStructType() + "*) " + customExpr + ")->data"));
        ctx.append("\t\t}\n");
        ctx.append("\t}\n");
    }

    public static void emitCopyCustomToExistingJni(UnitContext ctx, TempJumpVM tjvm, String customExpr, String jniExpr, PrimitiveArrayKind kind) {
        String sitePrefix = "PrimitiveArrayCodegen#emitCopyCustomToExistingJni#" + kind.name() + "#" + customExpr + "#" + jniExpr;
        ctx.fmtAppend("\tif (%s != NULL && %s != NULL) {\n", customExpr, jniExpr);
        ctx.fmtAppend("\t\t%s;\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingCatalog.byFunction(kind.setRegionFunction()), sitePrefix + "#setRegion",
                        "(void (*)(JNIEnv *, " + kind.jniArrayType() + ", jsize, jsize, const " + kind.componentType() + " *))",
                        "env", jniExpr, "0", "getArrayLength(" + customExpr + ")", "((" + kind.customStructType() + "*) " + customExpr + ")->data"));
        ctx.append("\t}\n");
    }

    public static void emitDeleteLocalRef(UnitContext ctx, TempJumpVM tjvm, String refExpr) {
        ctx.fmtAppend("\t%s;\n",
                JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.DELETE_LOCAL_REF, "PrimitiveArrayCodegen#emitDeleteLocalRef#"
                        + refExpr, "(void (*)(JNIEnv *, jobject))", "env", refExpr));
    }

    public static void emitPendingExceptionToUnwind(UnitContext ctx, TempJumpVM tjvm) {
        ctx.append("\tif (" + JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.EXCEPTION_CHECK, "PrimitiveArrayCodegen#emitPendingExceptionToUnwind#check",
                "(jboolean (*)(JNIEnv *))", "env") + ") {\n");
        ctx.append("\t\tjthrowable ex = " + JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.EXCEPTION_OCCURRED, "PrimitiveArrayCodegen#emitPendingExceptionToUnwind#occurred",
                "(jthrowable (*)(JNIEnv *))", "env") + ";\n");
        ctx.append("\t\tex = " + JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.NEW_LOCAL_REF, "PrimitiveArrayCodegen#emitPendingExceptionToUnwind#localref",
                "(jobject (*)(JNIEnv *, jobject))", "env", "ex") + ";\n");
        ctx.append("\t\t" + JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.EXCEPTION_CLEAR, "PrimitiveArrayCodegen#emitPendingExceptionToUnwind#clear",
                "(void (*)(JNIEnv *))", "env") + ";\n");
        ctx.append("\t\t" + JniDispatchCodegen.callExprLiteral(tjvm, JniBindingId.THROW, "PrimitiveArrayCodegen#emitPendingExceptionToUnwind#throw",
                "(jint (*)(JNIEnv *, jthrowable))", "env", "ex") + ";\n");
        ctx.append("\t\tgoto unwind;\n");
        ctx.append("\t}\n");
    }

    public static void emitParameterSyncBack(UnitContext ctx, TempJumpVM tjvm) {
        int nativeParamIndex = 0;
        int argIndex = 0;
        for (var argType : org.objectweb.asm.Type.getArgumentTypes(ctx.getMethodNode().desc)) {
            PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(argType);
            if (kind != null) {
                emitCopyCustomToExistingJni(ctx, tjvm, "arg_" + argIndex, "p" + nativeParamIndex, kind);
            }

            nativeParamIndex += argType.getSize();
            argIndex++;
        }
    }

    public static void emitParameterRootRelease(UnitContext ctx) {
        int argIndex = 0;
        for (var argType : org.objectweb.asm.Type.getArgumentTypes(ctx.getMethodNode().desc)) {
            if (PrimitiveArrayKind.fromType(argType) != null) {
                ctx.fmtAppend("\treleaseArrayPtr(arg_%d);\n", argIndex);
                ctx.fmtAppend("\targ_%d = NULL;\n", argIndex);
            }
            argIndex++;
        }
    }
}
