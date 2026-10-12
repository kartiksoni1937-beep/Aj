package war.jnt.core.code.bindings;

import war.jnt.fusebox.impl.Internal;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class JniBindingCatalog {

    private static final Map<String, JniBindingInfo> BY_FUNCTION = Arrays.stream(JniBindingId.values())
            .map(JniBindingCatalog::info)
            .collect(Collectors.toMap(JniBindingInfo::functionName, info -> info, (left, right) -> left, LinkedHashMap::new));

    private JniBindingCatalog() {
    }

    public static JniBindingInfo info(JniBindingId id) {
        return new JniBindingInfo(id, id.functionName(), id.slotIndex(), id.returnType());
    }

    public static JniBindingInfo byFunctionName(String functionName) {
        JniBindingInfo info = BY_FUNCTION.get(functionName);
        if (info == null) {
            throw new IllegalArgumentException("Unknown JNI binding function: " + functionName);
        }
        return info;
    }

    public static JniBindingId byFunction(String functionName) {
        return byFunctionName(functionName).id();
    }

    public static JniBindingId byFieldAccess(String desc, int opcode) {
        return byFunction(Internal.resolveField(desc, opcode));
    }

    public static String emitCHeader() {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                #ifndef JNT_JNI_DISPATCH_IDS
                #define JNT_JNI_DISPATCH_IDS

                """);
        for (JniBindingId bindingId : JniBindingId.values()) {
            builder.append("#define ")
                    .append(bindingId.cDefineName())
                    .append(' ')
                    .append(bindingId.id())
                    .append('\n');
        }
        builder.append("\n#define JNT_BINDING_COUNT ").append(JniBindingId.values().length + 1).append('\n');
        builder.append("\n#endif\n");
        return builder.toString();
    }
}
