package war.jnt.core.code.analysis;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import war.jnt.dash.Level;
import war.jnt.dash.Logger;
import war.jnt.dash.Origin;

import java.util.HashSet;
import java.util.Set;

public class MethodArrayState {

    private final String owner;
    private final MethodNode method;
    private final Frame<BasicValue>[] beforeFrames;
    private final Frame<BasicValue>[] afterFrames;
    private final Set<Integer> warnedFallbackInstructions = new HashSet<>();

    private MethodArrayState(
            String owner,
            MethodNode method,
            Frame<BasicValue>[] beforeFrames,
            Frame<BasicValue>[] afterFrames
    ) {
        this.owner = owner;
        this.method = method;
        this.beforeFrames = beforeFrames;
        this.afterFrames = afterFrames;
    }

    public static MethodArrayState analyze(ClassNode classNode, MethodNode methodNode) {
        methodNode.instructions.toArray();

        RepresentationInterpreter interpreter = new RepresentationInterpreter();
        Analyzer<BasicValue> analyzer = new Analyzer<>(interpreter) {
            @Override
            public Frame<BasicValue> computeInitialFrame(String owner, MethodNode method) {
                Frame<BasicValue> frame = super.computeInitialFrame(owner, method);

                int localIndex = 0;
                int argIndex = 0;
                if ((method.access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0) {
                    frame.setLocal(localIndex++, TrackedArrayValue.nonArray(Type.getObjectType(owner)));
                }
                for (Type argType : Type.getArgumentTypes(method.desc)) {
                    PrimitiveArrayKind kind = PrimitiveArrayKind.fromType(argType);
                    if (kind != null) {
                        frame.setLocal(
                                localIndex,
                                TrackedArrayValue.custom(
                                        argType,
                                        kind,
                                        new TrackedArrayValue.SyncTarget(TrackedArrayValue.SyncTargetKind.PARAM, argIndex)
                                )
                        );
                    }
                    localIndex += argType.getSize();
                    argIndex++;
                }
                return frame;
            }
        };

        try {
            Frame<BasicValue>[] before = analyzer.analyze(classNode.name, methodNode);
            Frame<BasicValue>[] after = buildAfterFrames(methodNode, before, interpreter);
            return new MethodArrayState(classNode.name, methodNode, before, after);
        } catch (AnalyzerException ex) {
            throw new RuntimeException("Failed to analyze array representations for "
                    + classNode.name + "." + methodNode.name + methodNode.desc, ex);
        }
    }

    private static Frame<BasicValue>[] buildAfterFrames(
            MethodNode methodNode,
            Frame<BasicValue>[] before,
            RepresentationInterpreter interpreter
    ) throws AnalyzerException {
        @SuppressWarnings("unchecked")
        Frame<BasicValue>[] after = new Frame[methodNode.instructions.size()];
        for (int i = 0; i < methodNode.instructions.size(); i++) {
            AbstractInsnNode insn = methodNode.instructions.get(i);
            Frame<BasicValue> frame = before[i];
            if (frame == null || insn.getOpcode() == -1) {
                continue;
            }
            Frame<BasicValue> executed = new Frame<>(frame);
            executed.execute(insn, interpreter);
            after[i] = executed;
        }
        return after;
    }

    public TrackedArrayValue getStackValueBefore(AbstractInsnNode insn, int depthFromTop) {
        Frame<BasicValue> frame = getFrame(beforeFrames, insn);
        if (frame == null) {
            return TrackedArrayValue.uninitialized();
        }
        int index = frame.getStackSize() - 1 - depthFromTop;
        if (index < 0 || index >= frame.getStackSize()) {
            return TrackedArrayValue.uninitialized();
        }
        return safe(frame.getStack(index));
    }

    public TrackedArrayValue getStackValueAfter(AbstractInsnNode insn, int depthFromTop) {
        Frame<BasicValue> frame = getFrame(afterFrames, insn);
        if (frame == null) {
            return TrackedArrayValue.uninitialized();
        }
        int index = frame.getStackSize() - 1 - depthFromTop;
        if (index < 0 || index >= frame.getStackSize()) {
            return TrackedArrayValue.uninitialized();
        }
        return safe(frame.getStack(index));
    }

    public TrackedArrayValue getStackValueBeforeAt(AbstractInsnNode insn, int indexFromBottom) {
        Frame<BasicValue> frame = getFrame(beforeFrames, insn);
        if (frame == null || indexFromBottom < 0 || indexFromBottom >= frame.getStackSize()) {
            return TrackedArrayValue.uninitialized();
        }
        return safe(frame.getStack(indexFromBottom));
    }

    public int getStackSizeBefore(AbstractInsnNode insn) {
        Frame<BasicValue> frame = getFrame(beforeFrames, insn);
        return frame == null ? 0 : frame.getStackSize();
    }

    public int getStackSizeAfter(AbstractInsnNode insn) {
        Frame<BasicValue> frame = getFrame(afterFrames, insn);
        return frame == null ? 0 : frame.getStackSize();
    }

    public TrackedArrayValue getLocalValueBefore(AbstractInsnNode insn, int localIndex) {
        Frame<BasicValue> frame = getFrame(beforeFrames, insn);
        if (frame == null || localIndex < 0 || localIndex >= frame.getLocals()) {
            return TrackedArrayValue.uninitialized();
        }
        return safe(frame.getLocal(localIndex));
    }

    public TrackedArrayValue getLocalValueAfter(AbstractInsnNode insn, int localIndex) {
        Frame<BasicValue> frame = getFrame(afterFrames, insn);
        if (frame == null || localIndex < 0 || localIndex >= frame.getLocals()) {
            return TrackedArrayValue.uninitialized();
        }
        return safe(frame.getLocal(localIndex));
    }

    public boolean isOriginLiveAfter(AbstractInsnNode insn, Integer originId) {
        if (originId == null) {
            return false;
        }
        Frame<BasicValue> frame = getFrame(afterFrames, insn);
        if (frame == null) {
            return false;
        }

        for (int i = 0; i < frame.getLocals(); i++) {
            if (originId.equals(safe(frame.getLocal(i)).originId())) {
                return true;
            }
        }
        for (int i = 0; i < frame.getStackSize(); i++) {
            if (originId.equals(safe(frame.getStack(i)).originId())) {
                return true;
            }
        }
        return false;
    }

    public TrackedArrayValue getMethodArgumentValue(MethodInsnNode insn, int argumentIndex) {
        Frame<BasicValue> frame = getFrame(beforeFrames, insn);
        if (frame == null) {
            return TrackedArrayValue.uninitialized();
        }
        Type[] argumentTypes = Type.getArgumentTypes(insn.desc);
        int stackIndex = frame.getStackSize();
        for (int i = argumentTypes.length - 1; i >= 0; --i) {
            stackIndex -= argumentTypes[i].getSize();
            if (i == argumentIndex) {
                return safe(frame.getStack(stackIndex));
            }
        }
        return TrackedArrayValue.uninitialized();
    }

    public TrackedArrayValue getMethodReceiverValue(MethodInsnNode insn) {
        Frame<BasicValue> frame = getFrame(beforeFrames, insn);
        if (frame == null || insn.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC) {
            return TrackedArrayValue.uninitialized();
        }
        Type[] argumentTypes = Type.getArgumentTypes(insn.desc);
        int stackIndex = frame.getStackSize();
        for (int i = argumentTypes.length - 1; i >= 0; --i) {
            stackIndex -= argumentTypes[i].getSize();
        }
        stackIndex -= 1;
        if (stackIndex < 0 || stackIndex >= frame.getStackSize()) {
            return TrackedArrayValue.uninitialized();
        }
        return safe(frame.getStack(stackIndex));
    }

    public void warnFallback(AbstractInsnNode insn, TrackedArrayValue value, String action) {
        int index = indexOf(insn);
        if (index < 0 || !warnedFallbackInstructions.add(index)) {
            return;
        }
        Logger.INSTANCE.logln(
                Level.WARNING,
                Origin.CORE,
                String.format(
                        "Falling back to JNI for %s.%s%s @ %d during %s: %s [%s]",
                        owner,
                        method.name,
                        method.desc,
                        index,
                        action,
                        value.fallbackReason(),
                        value.describe()
                )
        );
    }

    public String owner() {
        return owner;
    }

    public MethodNode method() {
        return method;
    }

    private Frame<BasicValue> getFrame(Frame<BasicValue>[] frames, AbstractInsnNode insn) {
        int index = indexOf(insn);
        if (index < 0 || index >= frames.length) {
            return null;
        }
        return frames[index];
    }

    private int indexOf(AbstractInsnNode insn) {
        return insn == null ? -1 : insn.index;
    }

    private TrackedArrayValue safe(BasicValue value) {
        if (value == null) {
            return TrackedArrayValue.uninitialized();
        }
        if (value instanceof TrackedArrayValue tracked) {
            return tracked;
        }
        return TrackedArrayValue.fromType(value.getType());
    }
}
