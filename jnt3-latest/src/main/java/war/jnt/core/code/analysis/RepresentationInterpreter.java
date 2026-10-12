package war.jnt.core.code.analysis;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;
import java.util.Objects;

public class RepresentationInterpreter extends BasicInterpreter {

    public RepresentationInterpreter() {
        super(Opcodes.ASM9);
    }

    @Override
    public TrackedArrayValue newValue(Type type) {
        if (type == Type.VOID_TYPE) {
            return null;
        }
        return TrackedArrayValue.fromType(type);
    }

    @Override
    public TrackedArrayValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
        if (insn.getOpcode() == Opcodes.ACONST_NULL) {
            return TrackedArrayValue.nullValue();
        }
        if (insn.getOpcode() == Opcodes.GETSTATIC) {
            Type fieldType = Type.getType(((FieldInsnNode) insn).desc);
            PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(fieldType);
            if (kind != null) {
                return TrackedArrayValue.custom(fieldType, kind, null, originId(insn));
            }
            if (fieldType.getSort() == Type.ARRAY) {
                return TrackedArrayValue.jni(fieldType, null);
            }
            return TrackedArrayValue.nonArray(fieldType);
        }
        BasicValue value = super.newOperation(insn);
        return value == null ? null : TrackedArrayValue.fromType(value.getType());
    }

    @Override
    public TrackedArrayValue copyOperation(AbstractInsnNode insn, BasicValue value) {
        TrackedArrayValue trackedValue = (TrackedArrayValue) value;
        if (insn.getOpcode() == Opcodes.ASTORE && trackedValue != null && trackedValue.isCustomPrimitiveArray()) {
            return trackedValue.withSyncTarget(
                    new TrackedArrayValue.SyncTarget(
                            TrackedArrayValue.SyncTargetKind.LOCAL,
                            ((VarInsnNode) insn).var
                    )
            );
        }
        return trackedValue;
    }

    @Override
    public TrackedArrayValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
        TrackedArrayValue trackedValue = (TrackedArrayValue) value;
        return switch (insn.getOpcode()) {
            case Opcodes.NEWARRAY -> {
                PrimitiveArrayKind kind = PrimitiveArrayKind.fromNewArrayOperand(((IntInsnNode) insn).operand);
                yield kind != null
                        ? TrackedArrayValue.custom(kind.arrayType(), kind, null, originId(insn))
                        : TrackedArrayValue.jni(Type.getType("[Ljava/lang/Object;"), null);
            }
            case Opcodes.ANEWARRAY -> TrackedArrayValue.jni(
                    Type.getType("[" + Type.getObjectType(((TypeInsnNode) insn).desc).getDescriptor()),
                    null
            );
            case Opcodes.CHECKCAST -> adaptCheckCast((TypeInsnNode) insn, trackedValue, originId(insn));
            case Opcodes.GETFIELD -> adaptFieldRead(Type.getType(((FieldInsnNode) insn).desc), originId(insn));
            case Opcodes.ARRAYLENGTH, Opcodes.INSTANCEOF -> TrackedArrayValue.nonArray(Type.INT_TYPE);
            default -> {
                BasicValue base = super.unaryOperation(insn, value);
                yield base == null ? null : TrackedArrayValue.fromType(base.getType());
            }
        };
    }

    @Override
    public TrackedArrayValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
        TrackedArrayValue arrayValue = (TrackedArrayValue) value1;
        return switch (insn.getOpcode()) {
            case Opcodes.BALOAD -> TrackedArrayValue.nonArray(
                    arrayValue.primitiveArrayKind() == PrimitiveArrayKind.BOOLEAN ? Type.BOOLEAN_TYPE : Type.BYTE_TYPE
            );
            case Opcodes.CALOAD -> TrackedArrayValue.nonArray(Type.CHAR_TYPE);
            case Opcodes.SALOAD -> TrackedArrayValue.nonArray(Type.SHORT_TYPE);
            case Opcodes.IALOAD -> TrackedArrayValue.nonArray(Type.INT_TYPE);
            case Opcodes.LALOAD -> TrackedArrayValue.nonArray(Type.LONG_TYPE);
            case Opcodes.FALOAD -> TrackedArrayValue.nonArray(Type.FLOAT_TYPE);
            case Opcodes.DALOAD -> TrackedArrayValue.nonArray(Type.DOUBLE_TYPE);
            case Opcodes.AALOAD -> {
                Type arrayType = arrayValue.getType();
                if (arrayType != null && arrayType.getSort() == Type.ARRAY && arrayType.getDimensions() > 0) {
                    Type elementType = Type.getType(arrayType.getDescriptor().substring(1));
                    PrimitiveArrayKind elementKind = PrimitiveArrayKind.fromType(elementType);
                    if (elementKind != null) {
                        yield TrackedArrayValue.custom(elementType, elementKind, null, originId(insn));
                    }
                    if (elementType.getSort() == Type.ARRAY) {
                        yield TrackedArrayValue.jni(elementType, null);
                    }
                    yield TrackedArrayValue.fromType(elementType);
                }
                yield TrackedArrayValue.nonArray(Type.getObjectType("java/lang/Object"));
            }
            default -> {
                BasicValue base = super.binaryOperation(insn, value1, value2);
                yield base == null ? null : TrackedArrayValue.fromType(base.getType());
            }
        };
    }

    @Override
    public TrackedArrayValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) throws AnalyzerException {
        BasicValue base = super.ternaryOperation(insn, value1, value2, value3);
        return base == null ? null : TrackedArrayValue.fromType(base.getType());
    }

    @Override
    public TrackedArrayValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
        return switch (insn.getOpcode()) {
            case Opcodes.MULTIANEWARRAY -> TrackedArrayValue.jni(Type.getType(((MultiANewArrayInsnNode) insn).desc), null);
            case Opcodes.INVOKEDYNAMIC -> TrackedArrayValue.fromType(Type.getReturnType(((InvokeDynamicInsnNode) insn).desc));
            case Opcodes.INVOKESTATIC, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                Type returnType = Type.getReturnType(methodInsn.desc);
                PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(returnType);
                if (kind != null) {
                    yield TrackedArrayValue.custom(returnType, kind, null, originId(insn));
                }
                yield TrackedArrayValue.fromType(returnType);
            }
            default -> {
                BasicValue base = super.naryOperation(insn, values);
                yield base == null ? null : TrackedArrayValue.fromType(base.getType());
            }
        };
    }

    @Override
    public TrackedArrayValue merge(BasicValue value1, BasicValue value2) {
        TrackedArrayValue left = (TrackedArrayValue) value1;
        TrackedArrayValue right = (TrackedArrayValue) value2;

        if (left.equals(right)) {
            return left;
        }
        if (left.isUninitialized()) {
            return right;
        }
        if (right.isUninitialized()) {
            return left;
        }
        if (left.isNullReference() && right.isSupportedPrimitiveArray()) {
            return right;
        }
        if (right.isNullReference() && left.isSupportedPrimitiveArray()) {
            return left;
        }
        if (left.isNullReference() && right.isReference()) {
            return right.withType(mergeVerifierType(left, right));
        }
        if (right.isNullReference() && left.isReference()) {
            return left.withType(mergeVerifierType(left, right));
        }

        Type mergedType = mergeVerifierType(left, right);

        if (left.primitiveArrayKind() != null && left.primitiveArrayKind() == right.primitiveArrayKind()) {
            if (left.representation() == right.representation() && left.representation() != ArrayRepresentation.UNKNOWN) {
                TrackedArrayValue.SyncTarget syncTarget =
                        Objects.equals(left.syncTarget(), right.syncTarget()) ? left.syncTarget() : null;
                Integer originId = Objects.equals(left.originId(), right.originId()) ? left.originId() : null;
                return switch (left.representation()) {
                    case CUSTOM -> TrackedArrayValue.custom(mergedType, left.primitiveArrayKind(), syncTarget, originId);
                    case JNI -> TrackedArrayValue.jni(mergedType, left.primitiveArrayKind());
                    case NON_ARRAY, UNKNOWN -> TrackedArrayValue.unknown(mergedType, left.primitiveArrayKind(), "mixed array merge");
                };
            }
            return TrackedArrayValue.unknown(mergedType, left.primitiveArrayKind(), "mixed custom/jni merge");
        }

        if (left.primitiveArrayKind() != null || right.primitiveArrayKind() != null) {
            return TrackedArrayValue.unknownReference(
                    mergedType != null ? mergedType : Type.getObjectType("java/lang/Object"),
                    "object flow lost unique origin"
            );
        }

        if (left.isUnsupportedArray() || right.isUnsupportedArray()) {
            if (mergedType != null && mergedType.getSort() == Type.ARRAY) {
                return TrackedArrayValue.jni(mergedType, null);
            }
            return TrackedArrayValue.unknownReference(
                    mergedType != null ? mergedType : Type.getObjectType("java/lang/Object"),
                    "unsupported array kind"
            );
        }

        if (mergedType == null) {
            return TrackedArrayValue.uninitialized();
        }
        if (mergedType.getSort() == Type.ARRAY) {
            PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(mergedType);
            return kind != null
                    ? TrackedArrayValue.unknown(mergedType, kind, "mixed array flow")
                    : TrackedArrayValue.jni(mergedType, null);
        }
        return TrackedArrayValue.nonArray(mergedType);
    }

    private TrackedArrayValue adaptCheckCast(TypeInsnNode insn, TrackedArrayValue value, Integer originId) {
        Type targetType = insn.desc.startsWith("[") ? Type.getType(insn.desc) : Type.getObjectType(insn.desc);
        PrimitiveArrayKind targetKind = PrimitiveArrayKind.fromType(targetType);
        if (targetKind != null) {
            if (value.isUnknownPrimitiveArray() && value.primitiveArrayKind() == targetKind) {
                return TrackedArrayValue.unknown(targetType, targetKind, value.fallbackReason());
            }
            if (value.isCustomPrimitiveArray() && value.primitiveArrayKind() == targetKind) {
                return value.withType(targetType);
            }
            return TrackedArrayValue.custom(targetType, targetKind, null, originId);
        }
        if (targetType.getSort() == Type.ARRAY) {
            return TrackedArrayValue.jni(targetType, null);
        }
        if (value.isSupportedPrimitiveArray()) {
            return value.withType(targetType);
        }
        return TrackedArrayValue.nonArray(targetType);
    }

    private Type mergeVerifierType(TrackedArrayValue left, TrackedArrayValue right) {
        Type leftType = left.getType();
        Type rightType = right.getType();

        if (Objects.equals(leftType, rightType)) {
            return leftType;
        }
        if (left.isNullReference()) {
            return rightType;
        }
        if (right.isNullReference()) {
            return leftType;
        }
        if (leftType == null || rightType == null) {
            return null;
        }
        if (leftType.getSort() == Type.ARRAY && rightType.getSort() == Type.ARRAY) {
            PrimitiveArrayKind leftKind = PrimitiveArrayKind.fromType(leftType);
            PrimitiveArrayKind rightKind = PrimitiveArrayKind.fromType(rightType);
            if (leftKind != null && leftKind == rightKind) {
                return leftKind.arrayType();
            }
            return Type.getObjectType("java/lang/Object");
        }
        if ((leftType.getSort() == Type.OBJECT || leftType.getSort() == Type.ARRAY)
                && (rightType.getSort() == Type.OBJECT || rightType.getSort() == Type.ARRAY)) {
            return Type.getObjectType("java/lang/Object");
        }
        return leftType;
    }

    private TrackedArrayValue adaptFieldRead(Type fieldType, Integer originId) {
        PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(fieldType);
        if (kind != null) {
            return TrackedArrayValue.custom(fieldType, kind, null, originId);
        }
        if (fieldType.getSort() == Type.ARRAY) {
            return TrackedArrayValue.jni(fieldType, null);
        }
        return TrackedArrayValue.nonArray(fieldType);
    }

    private Integer originId(AbstractInsnNode insn) {
        return insn != null && insn.index >= 0 ? insn.index : null;
    }
}
