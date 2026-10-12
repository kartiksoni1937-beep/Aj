package war.jnt.core.code.analysis.flow;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import war.jnt.core.code.analysis.ArrayRepresentation;
import war.jnt.core.code.analysis.PrimitiveArrayKind;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.metaphor.tree.JClassNode;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

final class CustomArrayFlowAnalyzer implements Opcodes {

    private CustomArrayFlowAnalyzer() {}

    static MethodCustomArrayFlow analyze(JClassNode classNode, MethodNode methodNode) {
        methodNode.instructions.toArray();

        MethodControlFlowGraph graph = MethodControlFlowGraph.build(methodNode);
        CustomArrayFlowInterpreter interpreter = new CustomArrayFlowInterpreter();
        @SuppressWarnings("unchecked")
        Frame<BasicValue>[] beforeFrames = new Frame[methodNode.instructions.size()];
        @SuppressWarnings("unchecked")
        Frame<BasicValue>[] afterFrames = new Frame[methodNode.instructions.size()];

        AbstractInsnNode entry = methodNode.instructions.getFirst();
        if (entry == null) {
            return new MethodCustomArrayFlow(classNode.name, methodNode, graph, beforeFrames, afterFrames);
        }

        beforeFrames[entry.index] = initialFrame(classNode.name, methodNode, interpreter);
        ArrayDeque<AbstractInsnNode> worklist = new ArrayDeque<>();
        boolean[] queued = new boolean[methodNode.instructions.size()];
        worklist.add(entry);
        queued[entry.index] = true;

        while (!worklist.isEmpty()) {
            AbstractInsnNode insn = worklist.removeFirst();
            queued[insn.index] = false;

            Frame<BasicValue> before = beforeFrames[insn.index];
            if (before == null) {
                continue;
            }

            Frame<BasicValue> normalAfter = new Frame<>(before);
            if (insn.getOpcode() != -1) {
                try {
                    normalAfter.execute(insn, interpreter);
                } catch (AnalyzerException ex) {
                    throw new RuntimeException(
                            "Failed to analyze custom-array flow for "
                                    + classNode.name + "."
                                    + methodNode.name
                                    + methodNode.desc
                                    + " @ "
                                    + insn.index,
                            ex
                    );
                }
            }
            afterFrames[insn.index] = normalAfter;

            for (AbstractInsnNode successor : graph.normalSuccessorsOf(insn)) {
                Frame<BasicValue> candidate = new Frame<>(normalAfter);
                if (insn instanceof JumpInsnNode jumpInsnNode && successor == jumpInsnNode.label) {
                    candidate.initJumpTarget(insn.getOpcode(), jumpInsnNode.label);
                }
                mergeInto(beforeFrames, successor, candidate, interpreter, worklist, queued);
            }

            if (insn.getOpcode() == -1) {
                continue;
            }

            for (TryCatchBlockNode handler : graph.exceptionHandlersOf(insn)) {
                Frame<BasicValue> candidate = exceptionHandlerFrame(before, handler);
                mergeInto(beforeFrames, handler.handler, candidate, interpreter, worklist, queued);
            }
        }

        return new MethodCustomArrayFlow(classNode.name, methodNode, graph, beforeFrames, afterFrames);
    }

    private static Frame<BasicValue> initialFrame(String owner, MethodNode method, BasicInterpreter interpreter) {
        Frame<BasicValue> frame = new Frame<>(method.maxLocals, Math.max(1, method.maxStack));
        for (int i = 0; i < method.maxLocals; i++) {
            frame.setLocal(i, TrackedArrayValue.uninitialized());
        }
        frame.setReturn(interpreter.newValue(Type.getReturnType(method.desc)));

        int localIndex = 0;
        int argIndex = 0;
        if ((method.access & ACC_STATIC) == 0) {
            frame.setLocal(localIndex++, TrackedArrayValue.nonArray(Type.getObjectType(owner)));
        }
        for (Type argType : Type.getArgumentTypes(method.desc)) {
            PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(argType);
            BasicValue value = kind != null
                    ? TrackedArrayValue.custom(
                            argType,
                            kind,
                            new TrackedArrayValue.SyncTarget(TrackedArrayValue.SyncTargetKind.PARAM, argIndex)
                    )
                    : TrackedArrayValue.fromVerifierType(argType);
            frame.setLocal(localIndex, value);
            if (argType.getSize() == 2 && localIndex + 1 < method.maxLocals) {
                frame.setLocal(localIndex + 1, TrackedArrayValue.uninitialized());
            }
            localIndex += argType.getSize();
            argIndex++;
        }
        return frame;
    }

    private static Frame<BasicValue> exceptionHandlerFrame(Frame<BasicValue> before, TryCatchBlockNode handler) {
        Frame<BasicValue> frame = new Frame<>(before);
        frame.clearStack();
        String exceptionType = handler.type != null ? handler.type : "java/lang/Throwable";
        frame.push(TrackedArrayValue.nonArray(Type.getObjectType(exceptionType)));
        return frame;
    }

    private static void mergeInto(
            Frame<BasicValue>[] beforeFrames,
            AbstractInsnNode successor,
            Frame<BasicValue> candidate,
            BasicInterpreter interpreter,
            ArrayDeque<AbstractInsnNode> worklist,
            boolean[] queued
    ) {
        if (successor == null || successor.index < 0) {
            return;
        }

        Frame<BasicValue> existing = beforeFrames[successor.index];
        boolean changed;
        if (existing == null) {
            beforeFrames[successor.index] = new Frame<>(candidate);
            changed = true;
        } else {
            try {
                changed = existing.merge(candidate, interpreter);
            } catch (AnalyzerException ex) {
                throw new RuntimeException("Failed to merge custom-array flow @ " + successor.index, ex);
            }
        }

        if (changed && !queued[successor.index]) {
            worklist.add(successor);
            queued[successor.index] = true;
        }
    }

    private static final class CustomArrayFlowInterpreter extends BasicInterpreter {

        CustomArrayFlowInterpreter() {
            super(ASM9);
        }

        @Override
        public TrackedArrayValue newValue(Type type) {
            if (type == Type.VOID_TYPE) {
                return null;
            }
            return TrackedArrayValue.fromVerifierType(type);
        }

        @Override
        public TrackedArrayValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
            if (insn.getOpcode() == ACONST_NULL) {
                return TrackedArrayValue.nullValue();
            }
            if (insn.getOpcode() == GETSTATIC) {
                Type fieldType = Type.getType(((FieldInsnNode) insn).desc);
                PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(fieldType);
                if (kind != null) {
                    return TrackedArrayValue.custom(fieldType, kind, null);
                }
                if (fieldType.getSort() == Type.ARRAY) {
                    return TrackedArrayValue.jni(fieldType, null);
                }
                return TrackedArrayValue.nonArray(fieldType);
            }
            BasicValue base = super.newOperation(insn);
            return base == null ? null : TrackedArrayValue.fromVerifierType(base.getType());
        }

        @Override
        public TrackedArrayValue copyOperation(AbstractInsnNode insn, BasicValue value) {
            TrackedArrayValue trackedValue = safe(value);
            if (insn.getOpcode() == ASTORE && trackedValue.isCustomPrimitiveArray()) {
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
            TrackedArrayValue trackedValue = safe(value);
            return switch (insn.getOpcode()) {
                case NEWARRAY -> {
                    PrimitiveArrayKind kind = PrimitiveArrayKind.fromNewArrayOperand(((IntInsnNode) insn).operand);
                    yield kind != null
                            ? TrackedArrayValue.custom(kind.arrayType(), kind, null)
                            : TrackedArrayValue.jni(Type.getType("[Ljava/lang/Object;"), null);
                }
                case ANEWARRAY -> TrackedArrayValue.jni(
                        Type.getType("[" + Type.getObjectType(((TypeInsnNode) insn).desc).getDescriptor()),
                        null
                );
                case CHECKCAST -> adaptCheckCast((TypeInsnNode) insn, trackedValue);
                case GETFIELD -> adaptFieldRead(Type.getType(((FieldInsnNode) insn).desc));
                case ARRAYLENGTH, INSTANCEOF -> TrackedArrayValue.nonArray(Type.INT_TYPE);
                default -> {
                    BasicValue base = super.unaryOperation(insn, value);
                    yield base == null ? null : TrackedArrayValue.fromVerifierType(base.getType());
                }
            };
        }

        @Override
        public TrackedArrayValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
            TrackedArrayValue arrayValue = safe(value1);
            return switch (insn.getOpcode()) {
                case BALOAD -> TrackedArrayValue.nonArray(
                        arrayValue.primitiveArrayKind() == PrimitiveArrayKind.BOOLEAN ? Type.BOOLEAN_TYPE : Type.BYTE_TYPE
                );
                case CALOAD -> TrackedArrayValue.nonArray(Type.CHAR_TYPE);
                case SALOAD -> TrackedArrayValue.nonArray(Type.SHORT_TYPE);
                case IALOAD -> TrackedArrayValue.nonArray(Type.INT_TYPE);
                case LALOAD -> TrackedArrayValue.nonArray(Type.LONG_TYPE);
                case FALOAD -> TrackedArrayValue.nonArray(Type.FLOAT_TYPE);
                case DALOAD -> TrackedArrayValue.nonArray(Type.DOUBLE_TYPE);
                case AALOAD -> {
                    Type arrayType = arrayValue.getType();
                    if (arrayType != null && arrayType.getSort() == Type.ARRAY && arrayType.getDimensions() > 0) {
                        Type elementType = Type.getType(arrayType.getDescriptor().substring(1));
                        PrimitiveArrayKind elementKind = PrimitiveArrayKind.fromType(elementType);
                        if (elementKind != null) {
                            yield TrackedArrayValue.custom(elementType, elementKind, null);
                        }
                        if (elementType.getSort() == Type.ARRAY) {
                            yield TrackedArrayValue.jni(elementType, null);
                        }
                        yield TrackedArrayValue.fromVerifierType(elementType);
                    }
                    yield TrackedArrayValue.nonArray(Type.getObjectType("java/lang/Object"));
                }
                default -> {
                    BasicValue base = super.binaryOperation(insn, value1, value2);
                    yield base == null ? null : TrackedArrayValue.fromVerifierType(base.getType());
                }
            };
        }

        @Override
        public TrackedArrayValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) throws AnalyzerException {
            BasicValue base = super.ternaryOperation(insn, value1, value2, value3);
            return base == null ? null : TrackedArrayValue.fromVerifierType(base.getType());
        }

        @Override
        public TrackedArrayValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
            return switch (insn.getOpcode()) {
                case MULTIANEWARRAY -> TrackedArrayValue.jni(Type.getType(((MultiANewArrayInsnNode) insn).desc), null);
                case INVOKEDYNAMIC -> TrackedArrayValue.fromVerifierType(Type.getReturnType(((InvokeDynamicInsnNode) insn).desc));
                case INVOKESTATIC, INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE -> {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    Type returnType = Type.getReturnType(methodInsn.desc);
                    PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(returnType);
                    if (kind != null) {
                        yield TrackedArrayValue.custom(returnType, kind, null);
                    }
                    yield TrackedArrayValue.fromVerifierType(returnType);
                }
                default -> {
                    BasicValue base = super.naryOperation(insn, values);
                    yield base == null ? null : TrackedArrayValue.fromVerifierType(base.getType());
                }
            };
        }

        @Override
        public TrackedArrayValue merge(BasicValue value1, BasicValue value2) {
            TrackedArrayValue left = safe(value1);
            TrackedArrayValue right = safe(value2);

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
                    return switch (left.representation()) {
                        case CUSTOM -> TrackedArrayValue.custom(mergedType, left.primitiveArrayKind(), syncTarget);
                        case JNI -> TrackedArrayValue.jni(mergedType, left.primitiveArrayKind());
                        case NON_ARRAY, UNKNOWN ->
                                TrackedArrayValue.unknown(mergedType, left.primitiveArrayKind(), "mixed array merge");
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

        private TrackedArrayValue adaptCheckCast(TypeInsnNode insn, TrackedArrayValue value) {
            Type targetType = insn.desc.startsWith("[") ? Type.getType(insn.desc) : Type.getObjectType(insn.desc);
            PrimitiveArrayKind targetKind = PrimitiveArrayKind.fromType(targetType);
            if (targetKind != null) {
                if (value.isUnknownPrimitiveArray() && value.primitiveArrayKind() == targetKind) {
                    return TrackedArrayValue.unknown(targetType, targetKind, value.fallbackReason());
                }
                if (value.isCustomPrimitiveArray() && value.primitiveArrayKind() == targetKind) {
                    return value.withType(targetType);
                }
                return TrackedArrayValue.custom(targetType, targetKind, null);
            }
            if (targetType.getSort() == Type.ARRAY) {
                return TrackedArrayValue.jni(targetType, null);
            }
            if (value.isSupportedPrimitiveArray()) {
                return value.withType(targetType);
            }
            return TrackedArrayValue.nonArray(targetType);
        }

        private TrackedArrayValue adaptFieldRead(Type fieldType) {
            PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(fieldType);
            if (kind != null) {
                return TrackedArrayValue.custom(fieldType, kind, null);
            }
            if (fieldType.getSort() == Type.ARRAY) {
                return TrackedArrayValue.jni(fieldType, null);
            }
            return TrackedArrayValue.nonArray(fieldType);
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

        private TrackedArrayValue safe(BasicValue value) {
            if (value == null) {
                return TrackedArrayValue.uninitialized();
            }
            if (value instanceof TrackedArrayValue trackedValue) {
                return trackedValue;
            }
            return TrackedArrayValue.fromVerifierType(value.getType());
        }
    }
}
