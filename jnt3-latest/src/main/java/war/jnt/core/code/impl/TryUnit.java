package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import war.jnt.core.code.analysis.MethodArrayState;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.metaphor.base.ObfuscatorContext;
import war.metaphor.tree.ClassMethod;
import war.metaphor.tree.JClassNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TryUnit implements Opcodes {

    public static Set<String> canThrow(AbstractInsnNode insn, Map<AbstractInsnNode, Frame<BasicValue>> frames, MethodArrayState arrayState) {
        return canThrow(insn, frames, new HashSet<>(), arrayState);
    }

    private static Set<String> canThrow(AbstractInsnNode insn, Map<AbstractInsnNode, Frame<BasicValue>> frames, Set<ClassMethod> visited, MethodArrayState arrayState) {
        Set<String> throwableTypes = new HashSet<>();
        Frame<BasicValue> frame = frames.get(insn);
        if (frame == null) return throwableTypes;

        switch (insn.getOpcode()) {
            case ATHROW -> {
                BasicValue value = frame.getStack(frame.getStackSize() - 1);
                Type type = value.getType();
                if (type == null || "null".equals(type.getInternalName())) {
                    throwableTypes.add("java/lang/NullPointerException");
                } else {
                    throwableTypes.add(type.getInternalName());
                }
            }
            case NEW, MULTIANEWARRAY, CHECKCAST, INSTANCEOF, INVOKEDYNAMIC -> throwableTypes.add("*");
            case INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (methodInsn.owner.equals("JNT") && methodInsn.name.equals("free")) {
                    break;
                }
                throwableTypes.add("java/lang/NullPointerException");

                if (ObfuscatorContext.INSTANCE == null) {
                    throwableTypes.add("*");
                    break;
                }

                JClassNode node = ObfuscatorContext.INSTANCE.loadClass(methodInsn.owner);
                if (node == null) {
                    throwableTypes.add("*");
                    break;
                }

                MethodNode method = node.getMethod(methodInsn.name, methodInsn.desc);
                if (method == null) {
                    throwableTypes.add("*");
                    break;
                }

                ClassMethod member = new ClassMethod(node, method);
                if (!visited.add(member)) break;

                throwableTypes.addAll(method.exceptions);
                for (AbstractInsnNode instruction : method.instructions) {
                    throwableTypes.addAll(canThrow(instruction, frames, visited, null));
                }
            }
            case IREM, DREM, LREM, FREM, IDIV, DDIV, LDIV, FDIV ->
                    throwableTypes.add("java/lang/ArithmeticException");
            case IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD,
                 IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE -> {
                if (!isCustomPrimitiveArrayAccess(insn, arrayState) || (insn.getOpcode() == AALOAD || insn.getOpcode() == AASTORE)) {
                    throwableTypes.add("java/lang/NullPointerException");
                    throwableTypes.add("java/lang/ArrayIndexOutOfBoundsException");
                    throwableTypes.add("java/lang/ArrayStoreException");
                }
            }
            case GETFIELD, PUTFIELD -> {
                throwableTypes.add("java/lang/NullPointerException");
                throwableTypes.add("java/lang/IncompatibleClassChangeError");
            }
            case MONITORENTER, MONITOREXIT -> {
                throwableTypes.add("java/lang/NullPointerException");
                throwableTypes.add("java/lang/IllegalMonitorStateException");
            }
            case ARRAYLENGTH -> {
                if (!isCustomPrimitiveArrayAccess(insn, arrayState)) {
                    throwableTypes.add("java/lang/NullPointerException");
                }
            }
            case NEWARRAY, ANEWARRAY -> {
                if (insn.getOpcode() == ANEWARRAY || arrayState == null) {
                    throwableTypes.add("java/lang/NegativeArraySizeException");
                }
            }
        }
        return throwableTypes;
    }

    private static boolean isCustomPrimitiveArrayAccess(AbstractInsnNode insn, MethodArrayState arrayState) {
        if (arrayState == null) {
            return false;
        }
        int depth = switch (insn.getOpcode()) {
            case IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> 1;
            case ARRAYLENGTH -> 0;
            case IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE -> 2;
            default -> -1;
        };
        if (depth < 0) {
            return false;
        }
        TrackedArrayValue value = arrayState.getStackValueBefore(insn, depth);
        return value.isCustomPrimitiveArray();
    }
}
