package war.jnt.core.code.analysis.flow;

import war.jnt.core.code.analysis.TrackedArrayValue;

public record FlowSlotValue(TrackedArrayValue trackedValue) {

    private static final FlowSlotValue UNINITIALIZED = new FlowSlotValue(TrackedArrayValue.uninitialized());

    public static FlowSlotValue of(TrackedArrayValue trackedValue) {
        return trackedValue == null ? UNINITIALIZED : new FlowSlotValue(trackedValue);
    }

    public static FlowSlotValue uninitialized() {
        return UNINITIALIZED;
    }

    public boolean isCustomPrimitiveArray() {
        return trackedValue.isCustomPrimitiveArray();
    }
}
