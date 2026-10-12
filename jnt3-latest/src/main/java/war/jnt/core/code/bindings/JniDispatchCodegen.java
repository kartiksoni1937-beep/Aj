package war.jnt.core.code.bindings;

import org.apache.commons.lang3.RandomUtils;
import org.objectweb.asm.tree.AbstractInsnNode;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.impl.invoke.CallOffsetInfo;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;

import java.util.StringJoiner;

public final class JniDispatchCodegen {

    public static String callExpr(UnitContext ctx, TempJumpVM tjvm, AbstractInsnNode insn, JniBindingId bindingId, String suffix, String functionPointerType, String... args) {
        String normalizedFunctionPointerType = normalizeFunctionPointerType(functionPointerType);
        if (!JniDispatchConfiguration.isGenerationEnabled()) {
            return directCallExpr(bindingId, normalizedFunctionPointerType, tjvm, args);
        }

        long marker = JniMarkerCodec.encode(bindingId, JniMarkerCodec.siteKey(ctx, insn, bindingId, suffix));
        if (tjvm != null) {
            tjvm.makeLongValue(marker);
            return "JNT_JNI_CALL(env, (uint64_t) output, " + normalizedFunctionPointerType + ", " + joinArgs(args) + ")";
        }

        return "JNT_JNI_CALL(env, " + JniMarkerCodec.literal(marker) + ", " + normalizedFunctionPointerType + ", " + joinArgs(args) + ")";
    }

    public static String callExprLiteral(JniBindingId bindingId, String siteKey, String functionPointerType, String... args) {
        return callExprLiteral(null, bindingId, siteKey, functionPointerType, args);
    }

    public static String callExprLiteral(TempJumpVM tjvm, JniBindingId bindingId, String siteKey, String functionPointerType, String... args) {
        String normalizedFunctionPointerType = normalizeFunctionPointerType(functionPointerType);
        if (!JniDispatchConfiguration.isGenerationEnabled()) {
            return directCallExpr(bindingId, normalizedFunctionPointerType, tjvm, args);
        }

        return "JNT_JNI_CALL(env, " + JniMarkerCodec.literal(bindingId, siteKey) + ", " + normalizedFunctionPointerType + ", " + joinArgs(args) + ")";
    }

    public static String throwNew(UnitContext ctx, TempJumpVM tjvm, AbstractInsnNode insn, String suffix, String exceptionClassExpr, String message) {
        return callExpr(ctx, tjvm, insn, JniBindingId.THROW_NEW, suffix, "(jint (*)(JNIEnv *, jclass, const char *))", "env",
                exceptionClassExpr, "\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
    }

    private static String joinArgs(String... args) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String arg : args) {
            joiner.add(arg);
        }
        return joiner.toString();
    }

    private static String normalizeFunctionPointerType(String functionPointerType) {
        if (functionPointerType.startsWith("((") && functionPointerType.endsWith("))")) {
            return functionPointerType.substring(1, functionPointerType.length() - 1);
        }
        return functionPointerType;
    }

    private static String directCallExpr(JniBindingId bindingId, String functionPointerType, TempJumpVM tjvm, String... args) {
        if (!Internal.canBeResolved(bindingId.functionName())) {
            return "(*env)->" + bindingId.functionName() + "(" + joinArgs(args) + ")";
        }

        CallOffsetInfo dynamicCall = Internal.resolveCallIdx(bindingId.functionName());
        int xorKey = RandomUtils.nextInt();
        int encodedOffset = dynamicCall.getOffset() ^ xorKey;
        String offsetExpr;

        if (tjvm != null) {
            tjvm.makeValue(encodedOffset);
            offsetExpr = "(*(volatile int *)&output ^ " + xorKey + ")";
        } else {
            offsetExpr = "(" + encodedOffset + " ^ " + xorKey + ")";
        }

        return "(" + functionPointerType + " (*((void **)*env + " + offsetExpr + ")))(" + joinArgs(args) + ")";
    }
}
