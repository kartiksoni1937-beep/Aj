package war.jnt.core.code.bindings;

public record JniBindingInfo(
        JniBindingId id,
        String functionName,
        int slotIndex,
        String returnType
) {
}
