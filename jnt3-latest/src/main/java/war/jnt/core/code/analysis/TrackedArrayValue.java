package war.jnt.core.code.analysis;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.Objects;

public class TrackedArrayValue extends BasicValue {

    public enum SyncTargetKind {
        PARAM,
        LOCAL
    }

    public record SyncTarget(SyncTargetKind kind, int index) {}

    private static final Type NULL_TYPE = Type.getObjectType("null");
    private static final TrackedArrayValue UNINITIALIZED =
            new TrackedArrayValue(null, null, ArrayRepresentation.NON_ARRAY, null, null, null);
    private static final TrackedArrayValue NULL =
            new TrackedArrayValue(NULL_TYPE, null, ArrayRepresentation.NON_ARRAY, null, null, null);

    private final PrimitiveArrayKind primitiveArrayKind;
    private final ArrayRepresentation representation;
    private final SyncTarget syncTarget;
    private final Integer originId;
    private final String uncertaintyReason;

    private TrackedArrayValue(
            Type type,
            PrimitiveArrayKind primitiveArrayKind,
            ArrayRepresentation representation,
            SyncTarget syncTarget,
            Integer originId,
            String uncertaintyReason
    ) {
        super(type);
        this.primitiveArrayKind = primitiveArrayKind;
        this.representation = representation;
        this.syncTarget = syncTarget;
        this.originId = originId;
        this.uncertaintyReason = uncertaintyReason;
    }

    public static TrackedArrayValue uninitialized() {
        return UNINITIALIZED;
    }

    public static TrackedArrayValue nullValue() {
        return NULL;
    }

    public static TrackedArrayValue nonArray(Type type) {
        return new TrackedArrayValue(type, null, ArrayRepresentation.NON_ARRAY, null, null, null);
    }

    public static TrackedArrayValue jni(Type type, PrimitiveArrayKind kind) {
        return new TrackedArrayValue(type, kind, ArrayRepresentation.JNI, null, null, null);
    }

    public static TrackedArrayValue custom(Type type, PrimitiveArrayKind kind, SyncTarget syncTarget) {
        return custom(type, kind, syncTarget, null);
    }

    public static TrackedArrayValue custom(Type type, PrimitiveArrayKind kind, SyncTarget syncTarget, Integer originId) {
        return new TrackedArrayValue(type, kind, ArrayRepresentation.CUSTOM, syncTarget, originId, null);
    }

    public static TrackedArrayValue unknown(Type type, PrimitiveArrayKind kind, String reason) {
        return new TrackedArrayValue(type, kind, ArrayRepresentation.UNKNOWN, null, null, reason);
    }

    public static TrackedArrayValue unknownReference(Type type, String reason) {
        return new TrackedArrayValue(type, null, ArrayRepresentation.UNKNOWN, null, null, reason);
    }

    public static TrackedArrayValue fromType(Type type) {
        if (type == null) {
            return uninitialized();
        }
        PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(type);
        if (kind != null) {
            return custom(type, kind, null);
        }
        if (type.getSort() == Type.ARRAY) {
            return jni(type, null);
        }
        return nonArray(type);
    }

    public static TrackedArrayValue fromVerifierType(Type type) {
        if (type == null) {
            return uninitialized();
        }
        PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(type);
        if (kind != null) {
            return unknown(type, kind, "verifier-only primitive array");
        }
        if (type.getSort() == Type.ARRAY) {
            return jni(type, null);
        }
        return nonArray(type);
    }

    public PrimitiveArrayKind primitiveArrayKind() {
        return primitiveArrayKind;
    }

    public ArrayRepresentation representation() {
        return representation;
    }

    public SyncTarget syncTarget() {
        return syncTarget;
    }

    public Integer originId() {
        return originId;
    }

    public String uncertaintyReason() {
        return uncertaintyReason;
    }

    public boolean isUninitialized() {
        return getType() == null;
    }

    public boolean isNullReference() {
        return getType() != null && "null".equals(getType().getInternalName());
    }

    public boolean isReference() {
        return isNullReference()
                || (getType() != null && (getType().getSort() == Type.OBJECT || getType().getSort() == Type.ARRAY));
    }

    public boolean isSupportedPrimitiveArray() {
        return primitiveArrayKind != null;
    }

    public boolean isCustomPrimitiveArray() {
        return primitiveArrayKind != null && representation == ArrayRepresentation.CUSTOM;
    }

    public boolean isJniPrimitiveArray() {
        return primitiveArrayKind != null && representation == ArrayRepresentation.JNI;
    }

    public boolean isUnknownPrimitiveArray() {
        return primitiveArrayKind != null && representation == ArrayRepresentation.UNKNOWN;
    }

    public boolean isUnknownReference() {
        return primitiveArrayKind == null && representation == ArrayRepresentation.UNKNOWN;
    }

    public boolean isUnsupportedArray() {
        return primitiveArrayKind == null
                && getType() != null
                && getType().getSort() == Type.ARRAY;
    }

    public boolean requiresFallback() {
        return isUnknownPrimitiveArray() || isUnknownReference() || isUnsupportedArray();
    }

    public String fallbackReason() {
        if (uncertaintyReason != null) {
            return uncertaintyReason;
        }
        if (isUnsupportedArray()) {
            return "unsupported array kind";
        }
        return "untracked array flow";
    }

    public TrackedArrayValue withType(Type type) {
        return new TrackedArrayValue(type, primitiveArrayKind, representation, syncTarget, originId, uncertaintyReason);
    }

    public TrackedArrayValue withSyncTarget(SyncTarget newSyncTarget) {
        return new TrackedArrayValue(getType(), primitiveArrayKind, representation, newSyncTarget, originId, uncertaintyReason);
    }

    public TrackedArrayValue withOriginId(Integer newOriginId) {
        return new TrackedArrayValue(getType(), primitiveArrayKind, representation, syncTarget, newOriginId, uncertaintyReason);
    }

    public TrackedArrayValue withoutSyncTarget() {
        return withSyncTarget(null);
    }

    public String describe() {
        if (isCustomPrimitiveArray()) {
            return "CUSTOM(" + primitiveArrayKind.className() + ")";
        }
        if (isJniPrimitiveArray()) {
            return "JNI(" + primitiveArrayKind.className() + ")";
        }
        if (isUnknownPrimitiveArray()) {
            return "UNKNOWN(" + primitiveArrayKind.className() + ")";
        }
        if (isUnknownReference()) {
            return "UNKNOWN_REF";
        }
        if (isUnsupportedArray()) {
            return "JNI(" + getType().getDescriptor() + ")";
        }
        if (isNullReference()) {
            return "NULL";
        }
        if (isUninitialized()) {
            return "UNINITIALIZED";
        }
        return "NON_ARRAY";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TrackedArrayValue other)) {
            return false;
        }
        return Objects.equals(getType(), other.getType())
                && primitiveArrayKind == other.primitiveArrayKind
                && representation == other.representation
                && Objects.equals(syncTarget, other.syncTarget)
                && Objects.equals(originId, other.originId)
                && Objects.equals(uncertaintyReason, other.uncertaintyReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getType(), primitiveArrayKind, representation, syncTarget, originId, uncertaintyReason);
    }

    @Override
    public String toString() {
        return describe();
    }
}
