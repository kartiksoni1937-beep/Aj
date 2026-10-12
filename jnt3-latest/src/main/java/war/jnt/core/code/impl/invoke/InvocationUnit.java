package war.jnt.core.code.impl.invoke;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.analysis.PrimitiveArrayKind;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.core.code.analysis.flow.MethodCustomArrayFlow;
import war.jnt.core.code.analysis.invoke.DirectInvokeSitePlan;
import war.jnt.core.code.analysis.invoke.DirectInvokeTarget;
import war.jnt.core.code.bindings.JniBindingCatalog;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.code.impl.PrimitiveArrayUnit;
import war.jnt.core.header.HeaderProcessor;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;
import war.jnt.fusebox.impl.TypeMaps;
import war.jnt.fusebox.impl.VariableManager;
import war.jnt.innercache.InnerCache;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class InvocationUnit {

    private static final TypeMaps TYPE_MAPS = new TypeMaps();

    private static final Set<String> POLL_FREE_INTRINSICS = Set.of(
            "java/lang/Runtime#halt.(I)V",
            "java/lang/System#exit.(I)V",
            "war/jnt/api/JNT#free.([B)V",
            "war/jnt/api/JNT#popLocal.()Ljava/lang/Object;",
            "java/lang/Math#abs.(I)I",
            "java/lang/Math#abs.(F)F",
            "java/lang/Math#abs.(J)J",
            "java/lang/Math#abs.(D)D",
            "java/lang/Math#min.(II)I",
            "java/lang/Math#min.(FF)F",
            "java/lang/Math#min.(JJ)J",
            "java/lang/Math#min.(DD)D",
            "java/lang/Math#max.(II)I",
            "java/lang/Math#max.(FF)F",
            "java/lang/Math#max.(JJ)J",
            "java/lang/Math#max.(DD)D",
            "java/lang/Math#pow.(DD)D",
            "java/lang/Math#sqrt.(D)D",
            "java/lang/Math#round.(F)F",
            "java/lang/Math#round.(D)D"
    );

    private static String signature(MethodInsnNode min) {
        return min.owner + "#" + min.name + "." + min.desc;
    }

    private static final Map<String, String> singleArgLookup;
    private static final Map<String, String> dualArgLookup;

    static {
        Map<String, String> s = new HashMap<>();
        s.put("java/lang/Runtime#halt.(I)V", "\tjnt_Runtime_halt(env, %s.i);\n");
        s.put("java/lang/System#exit.(I)V", "\tjnt_System_exit(env, %s.i);\n");
        s.put("war/jnt/api/JNT#free.([B)V", "\treleaseArrayPtr(%s.l);\n");
        s.put("java/lang/Math#abs.(I)I", "\t%s.i = jnt_Math_abs_int(env, %s.i);\n");
        s.put("java/lang/Math#abs.(F)F", "\t%s.f = jnt_Math_abs_float(env, %s.f);\n");
        s.put("java/lang/Math#abs.(J)J", "\t%s.j = jnt_Math_abs_long(env, %s.j);\n");
        s.put("java/lang/Math#abs.(D)D", "\t%s.d = jnt_Math_abs_double(env, %s.d);\n");
        s.put("java/lang/Math#sqrt.(D)D", "\t%s.d = jnt_Math_sqrt_double(env, %s.d);\n");
        s.put("java/lang/Math#round.(F)F", "\t%s.f = jnt_Math_round_float(env, %s.f);\n");
        s.put("java/lang/Math#round.(D)D", "\t%s.d = jnt_Math_round_double(env, %s.d);\n");
        singleArgLookup = Collections.unmodifiableMap(s);

        Map<String, String> d = new HashMap<>();
        d.put("java/lang/Math#min.(II)I", "\t%s.i = jnt_Math_min_int(env, %s.i, %s.i);\n");
        d.put("java/lang/Math#min.(FF)F", "\t%s.f = jnt_Math_min_float(env, %s.f, %s.f);\n");
        d.put("java/lang/Math#min.(JJ)J", "\t%s.j = jnt_Math_min_long(env, %s.j, %s.j);\n");
        d.put("java/lang/Math#min.(DD)D", "\t%s.d = jnt_Math_min_double(env, %s.d, %s.d);\n");
        d.put("java/lang/Math#max.(II)I", "\t%s.i = jnt_Math_max_int(env, %s.i, %s.i);\n");
        d.put("java/lang/Math#max.(FF)F", "\t%s.f = jnt_Math_max_float(env, %s.f, %s.f);\n");
        d.put("java/lang/Math#max.(JJ)J", "\t%s.j = jnt_Math_max_long(env, %s.j, %s.j);\n");
        d.put("java/lang/Math#max.(DD)D", "\t%s.d = jnt_Math_max_double(env, %s.d, %s.d);\n");
        d.put("java/lang/Math#pow.(DD)D", "\t%s.d = jnt_Math_pow_double(env, %s.d, %s.d);\n");
        dualArgLookup = Collections.unmodifiableMap(d);
    }

    public static boolean usesIntrinsic(MethodInsnNode insn, MethodCustomArrayFlow arrayState, boolean shouldIntrinsic) {
        if (!shouldIntrinsic) {
            return false;
        }

        if ("length".equals(insn.name) && "java/lang/String".equals(insn.owner)) {
            return true;
        }

        if ("isEmpty".equals(insn.name) && "java/lang/String".equals(insn.owner)) {
            return true;
        }

        if ("war/jnt/api/JNT#pushLocal.(I)I".equals(signature(insn))
                || "war/jnt/api/JNT#popLocal.()Ljava/lang/Object;".equals(signature(insn))) {
            return true;
        }

        if ("getClass".equals(insn.name) && "java/lang/Object".equals(insn.owner)) {
            TrackedArrayValue receiverState = arrayState != null ? arrayState.getMethodReceiverValue(insn) : TrackedArrayValue.uninitialized();
            return !receiverState.isCustomPrimitiveArray();
        }

        String signature = signature(insn);
        return singleArgLookup.containsKey(signature) || dualArgLookup.containsKey(signature);
    }

    public static boolean isPollFreeIntrinsic(MethodInsnNode insn, MethodCustomArrayFlow arrayState, boolean shouldIntrinsic) {
        if (!usesIntrinsic(insn, arrayState, shouldIntrinsic)) {
            return false;
        }

        if ("length".equals(insn.name) && "java/lang/String".equals(insn.owner)) {
            return true;
        }

        if ("isEmpty".equals(insn.name) && "java/lang/String".equals(insn.owner)) {
            return true;
        }

        if ("getClass".equals(insn.name) && "java/lang/Object".equals(insn.owner)) {
            return true;
        }

        return POLL_FREE_INTRINSICS.contains(signature(insn));
    }

    private static void handle(InnerCache ic, MethodInsnNode insn, DirectInvokeSitePlan invokeSitePlan, UnitContext ctx, VariableManager varMan, TempJumpVM tjvm, boolean shouldIntrinsic) {
        String signature = signature(insn);
        String comment = "// Replaced func access with an intrinsic func: \"" + signature + "\"";
        String npe = ic.FindClass("java/lang/NullPointerException");
        Object oom = ic.FindClass("java/lang/OutOfMemoryError");

        TrackedArrayValue receiverState = ctx.getMethodArrayState() != null ? ctx.getMethodArrayState().getMethodReceiverValue(insn) : TrackedArrayValue.uninitialized();

        int opcode = insn.getOpcode();
        switch (opcode) {
            case INVOKESPECIAL, INVOKEVIRTUAL, INVOKEINTERFACE -> {
                int sp = ctx.tracker.dump();
                int argc = Type.getArgumentCount(insn.desc);
                for (int i = argc - 1; i >= 0; --i) {
                    sp--;
                }

                String pop = "stack[" + sp + "]";
                ctx.fmtAppend("\tif (%s.l == NULL) { %s; goto %s; }\n", pop,
                        JniDispatchCodegen.throwNew(ctx, tjvm, insn, "invoke-null-receiver", npe, "instance is null " + ((int) (Math.random() * 1000))),
                        ctx.handlerLabel
                );
            }
        }

        if (shouldIntrinsic) {
            if ("war/jnt/api/JNT#pushLocal.(I)I".equals(signature)) {
                String computedPop = Internal.computePop(ctx.tracker);
                String computedPush = Internal.computePush(ctx.tracker);
                ctx.fmtAppend("\t%s.i = %s;\n", computedPush,
                        JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.PUSH_LOCAL_FRAME, "pushLocalFrame", "(jint (*)(JNIEnv *, jint))", "env", computedPop + ".i")
                );
                return;
            }
            if ("war/jnt/api/JNT#popLocal.()Ljava/lang/Object;".equals(signature)) {
                ctx.fmtAppend("\t%s;\n",
                        JniDispatchCodegen.callExpr(ctx, tjvm, insn, JniBindingId.POP_LOCAL_FRAME, "popLocalFrame", "(jobject (*)(JNIEnv *, jobject))", "env", "NULL")
                );
                return;
            }
            boolean allowGetClassIntrinsic = !receiverState.isCustomPrimitiveArray();
            if (allowGetClassIntrinsic && "getClass".equals(insn.name) && "java/lang/Object".equals(insn.owner)) {
                String computedPop = Internal.computePop(ctx.tracker);
                String computedPush = Internal.computePush(ctx.tracker);
                ctx.fmtAppend("\t%s.l = jnt_Object_getClass(env, %s.l);\n", computedPush, computedPop);
                return;
            } else if ("length".equals(insn.name) && "java/lang/String".equals(insn.owner)) {
                String computedPop = Internal.computePop(ctx.tracker);
                String computedPush = Internal.computePush(ctx.tracker);
                ctx.fmtAppend("\t%s.i = jnt_String_length(env, %s.l);\n", computedPush, computedPop);
                return;
            } else if ("isEmpty".equals(insn.name) && "java/lang/String".equals(insn.owner)) {
                String computedPop = Internal.computePop(ctx.tracker);
                String computedPush = Internal.computePush(ctx.tracker);
                ctx.fmtAppend("\t%s.i = jnt_String_isEmpty(env, %s.l);\n", computedPush, computedPop);
                return;
            }

            if (singleArgLookup.containsKey(signature)) {
                String computedPop = Internal.computePop(ctx.tracker);
                String computedPush = Internal.computePush(ctx.tracker);
                ctx.fmtAppend("\t" + comment + "\n");
                ctx.fmtAppend(singleArgLookup.get(signature), computedPush, computedPop);
                return;
            }

            if (dualArgLookup.containsKey(signature)) {
                String computedPopB = Internal.computePop(ctx.tracker);
                String computedPopA = Internal.computePop(ctx.tracker);
                String computedPush = Internal.computePush(ctx.tracker);
                ctx.fmtAppend("\t" + comment + "\n");
                ctx.fmtAppend(dualArgLookup.get(signature), computedPush, computedPopA, computedPopB);
                return;
            }
        }

        Type returnType = Type.getReturnType(insn.desc);
        Type[] argumentTypes = Type.getArgumentTypes(insn.desc);

        int argsIndex = ctx.getArgManager().args;
        String argsName = "args" + argsIndex;
        List<BridgeInfo> argumentBridges = new ArrayList<>();
        if (argumentTypes.length != 0) {
            ctx.fmtAppend("\tjvalue %s[%d];\n", argsName, argumentTypes.length);
        }

        for (int i = argumentTypes.length - 1; i >= 0; --i) {
            String pop = Internal.computePop(ctx.tracker);
            if (!bridgeArgumentIfNeeded(ic, ctx, tjvm, insn, argsName, i, pop + ".l", argumentBridges)) {
                ctx.append("\t" + argsName + "[" + i + "] = " + pop + ";\n");
            }
        }

        if (argumentTypes.length != 0) {
            ctx.getArgManager().args++;
        } else {
            argsName = "NULL";
        }

        ctx.append("/* " + signature + " */\n");
        String producedStackSlot;
        if (invokeSitePlan != null && invokeSitePlan.isDirect()) {
            producedStackSlot = emitDirectInvoke(ic, insn, invokeSitePlan.directTarget(), ctx, tjvm, returnType, argumentTypes, argsName, oom);
        } else {
            if (invokeSitePlan != null && invokeSitePlan.fallbackReason() != null) {
                ctx.fmtAppend("\t/* direct native fallback: %s */\n", invokeSitePlan.fallbackReason());
            }

            Lookup lookup = LookupUnit.process(ic, insn, ctx, varMan, shouldIntrinsic);
            producedStackSlot = emitJniInvoke(insn, ctx, tjvm, returnType, argsName, lookup, oom);
        }

        if (producedStackSlot != null) {
            maybeNormalizeReturnValue(insn, ctx, tjvm, producedStackSlot + ".l", oom);
        }

        for (BridgeInfo bridge : argumentBridges) {
            PrimitiveArrayUnit.emitCopyJniBackToCustom(ctx, tjvm, bridge.jniExpr, bridge.customExpr, bridge.kind);
            PrimitiveArrayUnit.emitDeleteLocalRef(ctx, tjvm, bridge.jniExpr);
            maybeReleaseBridgeValue(ctx, bridge);
        }
    }

    private static String emitDirectInvoke(InnerCache ic, MethodInsnNode insn, DirectInvokeTarget directTarget, UnitContext ctx,
                                           TempJumpVM tjvm, Type returnType, Type[] argumentTypes, String argsName, Object oom) {
        ctx.append("\t/* direct native call */\n");

        return switch (insn.getOpcode()) {
            case INVOKESTATIC -> emitDirectStaticInvoke(ic, directTarget, insn, ctx, returnType, argumentTypes, argsName);
            case INVOKESPECIAL, INVOKEVIRTUAL -> emitDirectReceiverInvoke(directTarget, insn, ctx, tjvm, returnType, argumentTypes, argsName, oom);
            default -> null;
        };
    }

    private static String emitDirectStaticInvoke(InnerCache ic, DirectInvokeTarget directTarget, MethodInsnNode insn, UnitContext ctx, Type returnType, Type[] argumentTypes, String argsName) {
        String classVar = ic.FindClass(directTarget.owner().name);
        String args = buildDirectCallArguments(classVar, argumentTypes, argsName);
        String call = HeaderProcessor.call(directTarget.owner(), directTarget.methodNode(), "env, " + args);

        if (returnType.getSort() == Type.VOID) {
            ctx.append("\t" + call + ";\n");
            return null;
        }

        String computed = Internal.computePush(ctx.tracker);
        String computedReturn = Internal.resolveReturnType(insn.desc);
        ctx.append("\t" + computed + computedReturn + " = " + call + ";\n");
        return computed;
    }

    private static String emitDirectReceiverInvoke(DirectInvokeTarget directTarget, MethodInsnNode insn, UnitContext ctx, TempJumpVM tjvm,
                                                   Type returnType, Type[] argumentTypes, String argsName, Object oom) {
        String receiver = Internal.computePop(ctx.tracker);
        String receiverExpr = receiver + ".l";
        BridgeInfo receiverBridge = bridgeReceiverIfNeeded(ctx, tjvm, insn, receiverExpr, oom);
        if (receiverBridge != null) {
            receiverExpr = receiverBridge.jniExpr;
        }

        String args = buildDirectCallArguments(receiverExpr, argumentTypes, argsName);
        String call = HeaderProcessor.call(directTarget.owner(), directTarget.methodNode(), "env, " + args);
        String producedStackSlot = null;

        if (returnType.getSort() == Type.VOID) {
            ctx.append("\t" + call + ";\n");
        } else {
            String computed = Internal.computePush(ctx.tracker);
            String computedReturn = Internal.resolveReturnType(insn.desc);
            ctx.append("\t" + computed + computedReturn + " = " + call + ";\n");
            producedStackSlot = computed;
        }

        if (receiverBridge != null) {
            PrimitiveArrayUnit.emitCopyJniBackToCustom(ctx, tjvm, receiverBridge.jniExpr, receiverBridge.customExpr, receiverBridge.kind);
            PrimitiveArrayUnit.emitDeleteLocalRef(ctx, tjvm, receiverBridge.jniExpr);
            maybeReleaseBridgeValue(ctx, receiverBridge);
        }

        return producedStackSlot;
    }

    private static String emitJniInvoke(MethodInsnNode insn, UnitContext ctx, TempJumpVM tjvm, Type returnType, String argsName, Lookup lookup, Object oom) {
        String func = "<init>".equals(insn.name) ? "CallVoidMethodA" : Internal.resolveFunction(insn.desc, insn.getOpcode());
        JniBindingId bindingId = JniBindingCatalog.byFunction(func);

        return switch (insn.getOpcode()) {
            case INVOKESTATIC -> emitJniStaticInvoke(insn, ctx, tjvm, returnType, argsName, lookup, bindingId);
            case INVOKESPECIAL, INVOKEVIRTUAL, INVOKEINTERFACE -> emitJniReceiverInvoke(insn, ctx, tjvm, returnType, argsName, lookup, bindingId, func, oom);
            default -> null;
        };
    }

    private static String emitJniStaticInvoke(MethodInsnNode insn, UnitContext ctx, TempJumpVM tjvm, Type returnType,
                                              String argsName, Lookup lookup, JniBindingId bindingId) {
        String classVar = lookup != null ? lookup.getClassVar() : null;
        String lookupVar = lookup != null ? lookup.getLookupVar() : null;

        if (returnType.getSort() == Type.VOID) {
            ctx.append("\t" +
                    JniDispatchCodegen.callExpr(ctx, tjvm, insn, bindingId, "invoke-static", "(void (*)(JNIEnv *, jclass, jmethodID, jvalue *))", "env", classVar, lookupVar, argsName
            ) + ";\n");
            return null;
        }

        String computed = Internal.computePush(ctx.tracker);
        String computedReturn = Internal.resolveReturnType(insn.desc);
        String cReturnType = TYPE_MAPS.fromAsm(returnType);
        ctx.append("\t" + computed + computedReturn + " = " +
                JniDispatchCodegen.callExpr(ctx, tjvm, insn, bindingId, "invoke-static", "(" + cReturnType + " (*)(JNIEnv *, jclass, jmethodID, jvalue *))", "env", classVar, lookupVar, argsName
        ) + ";\n");
        return computed;
    }

    private static String emitJniReceiverInvoke(MethodInsnNode insn, UnitContext ctx, TempJumpVM tjvm, Type returnType, String argsName,
                                                Lookup lookup, JniBindingId bindingId, String func, Object oom) {
        String receiver = Internal.computePop(ctx.tracker);
        String receiverExpr = receiver + ".l";
        BridgeInfo receiverBridge = bridgeReceiverIfNeeded(ctx, tjvm, insn, receiverExpr, oom);
        if (receiverBridge != null) {
            receiverExpr = receiverBridge.jniExpr;
        }

        String lookupVar = lookup != null ? lookup.getLookupVar() : null;
        String classVar = lookup != null ? lookup.getClassVar() : null;

        String producedStackSlot = null;
        ctx.append("\t/* " + func + " */");
        if (func.startsWith("CallNonvirtual")) {
            if (returnType.getSort() == Type.VOID) {
                ctx.append("\t" +
                        JniDispatchCodegen.callExpr(ctx, tjvm, insn, bindingId, "invoke-nonvirtual", "(void (*)(JNIEnv *, jobject, jclass, jmethodID, jvalue *))", "env", receiverExpr, classVar, lookupVar, argsName
                ) + ";\n");
            } else {
                String computed = Internal.computePush(ctx.tracker);
                String computedReturn = Internal.resolveReturnType(insn.desc);
                String cReturnType = TYPE_MAPS.fromAsm(returnType);
                ctx.append("\t" + computed + computedReturn + " = " +
                        JniDispatchCodegen.callExpr(ctx, tjvm, insn, bindingId, "invoke-nonvirtual", "(" + cReturnType + " (*)(JNIEnv *, jobject, jclass, jmethodID, jvalue *))", "env", receiverExpr, classVar, lookupVar, argsName
                ) + ";\n");
                producedStackSlot = computed;
            }
        } else {
            if (returnType.getSort() == Type.VOID) {
                ctx.append("\t" +
                        JniDispatchCodegen.callExpr(ctx, tjvm, insn, bindingId, "invoke-receiver", "(void (*)(JNIEnv *, jobject, jmethodID, jvalue *))", "env", receiverExpr, lookupVar, argsName
                ) + ";\n");
            } else {
                String computed = Internal.computePush(ctx.tracker);
                String computedReturn = Internal.resolveReturnType(insn.desc);
                String cReturnType = TYPE_MAPS.fromAsm(returnType);
                ctx.append("\t" + computed + computedReturn + " = " +
                        JniDispatchCodegen.callExpr(ctx, tjvm, insn, bindingId, "invoke-receiver", "(" + cReturnType + " (*)(JNIEnv *, jobject, jmethodID, jvalue *))", "env", receiverExpr, lookupVar, argsName
                ) + ";\n");
                producedStackSlot = computed;
            }
        }

        if (receiverBridge != null) {
            PrimitiveArrayUnit.emitCopyJniBackToCustom(ctx, tjvm, receiverBridge.jniExpr, receiverBridge.customExpr, receiverBridge.kind);
            PrimitiveArrayUnit.emitDeleteLocalRef(ctx, tjvm, receiverBridge.jniExpr);
            maybeReleaseBridgeValue(ctx, receiverBridge);
        }

        return producedStackSlot;
    }

    private static String buildDirectCallArguments(String firstArgument, Type[] argumentTypes, String argsName) {
        StringBuilder builder = new StringBuilder(firstArgument);
        if ("NULL".equals(argsName)) {
            return builder.toString();
        }

        for (int i = 0; i < argumentTypes.length; i++) {
            builder.append(", ");
            builder.append(castArgument(argumentTypes[i], argsName, i));
        }

        return builder.toString();
    }

    private static String castArgument(Type type, String argsName, int index) {
        String cType = TYPE_MAPS.fromAsm(type);
        return "(" + cType + ") " + argsName + "[" + index + "]" + Internal.resolveType(type);
    }

    private static boolean bridgeArgumentIfNeeded(InnerCache ic, UnitContext ctx, TempJumpVM tjvm, MethodInsnNode insn, String argsName,
                                                  int argumentIndex, String customExpr, List<BridgeInfo> bridges) {
        if (ctx.getMethodArrayState() == null) {
            return false;
        }

        TrackedArrayValue argumentValue = ctx.getMethodArrayState().getMethodArgumentValue(insn, argumentIndex);
        if (argumentValue.isCustomPrimitiveArray()) {
            PrimitiveArrayKind kind = argumentValue.primitiveArrayKind();
            String jniExpr = argsName + "[" + argumentIndex + "].l";
            String stableCustomExpr = snapshotCustomExpr(ctx, customExpr, "__jnt_custom_arg_" + insn.index + "_" + argumentIndex);
            PrimitiveArrayUnit.emitCustomToNewJni(ctx, tjvm, stableCustomExpr, kind, ic.FindClass("java/lang/OutOfMemoryError"), jniExpr);
            bridges.add(new BridgeInfo(jniExpr, stableCustomExpr, kind, argumentValue));
            return true;
        }

        if (argumentValue.requiresFallback()) {
            ctx.getMethodArrayState().warnFallback(insn, argumentValue, "invoke argument");
        }

        return false;
    }

    private static BridgeInfo bridgeReceiverIfNeeded(UnitContext ctx, TempJumpVM tjvm, MethodInsnNode insn, String receiverExpr, Object oom) {
        if (ctx.getMethodArrayState() == null) {
            return null;
        }

        TrackedArrayValue receiverValue = ctx.getMethodArrayState().getMethodReceiverValue(insn);
        if (receiverValue.isCustomPrimitiveArray()) {
            String tempName = "__jnt_receiver_" + insn.index + "_" + ctx.getArgManager().args;
            String stableCustomExpr = snapshotCustomExpr(ctx, receiverExpr, "__jnt_receiver_custom_" + insn.index);
            ctx.fmtAppend("\tjobject %s = NULL;\n", tempName);
            PrimitiveArrayUnit.emitCustomToNewJni(ctx, tjvm, stableCustomExpr, receiverValue.primitiveArrayKind(), oom, tempName);
            return new BridgeInfo(tempName, stableCustomExpr, receiverValue.primitiveArrayKind(), receiverValue);
        }

        if (receiverValue.requiresFallback()) {
            ctx.getMethodArrayState().warnFallback(insn, receiverValue, "invoke receiver");
        }

        return null;
    }

    private static void maybeNormalizeReturnValue(MethodInsnNode insn, UnitContext ctx, TempJumpVM tjvm, String stackExpr, Object oom) {
        if (ctx.getMethodArrayState() == null) {
            return;
        }

        TrackedArrayValue produced = ctx.getMethodArrayState().getStackValueAfter(insn, 0);
        if (produced.isCustomPrimitiveArray()) {
            PrimitiveArrayUnit.emitJniToCustom(ctx, tjvm, stackExpr, produced.primitiveArrayKind(), oom, stackExpr);
        } else if (produced.requiresFallback()) {
            ctx.getMethodArrayState().warnFallback(insn, produced, "invoke return");
        }
    }

    public static void process(InnerCache ic, MethodInsnNode insn, UnitContext ctx, VariableManager varMan, TempJumpVM tjvm, boolean shouldIntrinsic, boolean isCustomArray) {
        DirectInvokeSitePlan invokeSitePlan = ctx.getMethodInvokePlan() != null ? ctx.getMethodInvokePlan().getSitePlan(insn) : null;
        handle(ic, insn, invokeSitePlan, ctx, varMan, tjvm, shouldIntrinsic);
    }

    private static String snapshotCustomExpr(UnitContext ctx, String customExpr, String prefix) {
        return PrimitiveArrayUnit.snapshotPointer(ctx, customExpr, prefix);
    }

    private static void maybeReleaseBridgeValue(UnitContext ctx, BridgeInfo bridge) {
        PrimitiveArrayUnit.maybeReleaseCustom(ctx, bridge.customExpr, bridge.value);
    }

    private record BridgeInfo(String jniExpr, String customExpr, PrimitiveArrayKind kind, TrackedArrayValue value) {}
}
