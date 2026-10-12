package war.jnt.core.code.analysis.flow;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.dash.Level;
import war.jnt.dash.Logger;
import war.jnt.dash.Origin;
import war.metaphor.tree.JClassNode;

import java.util.HashSet;
import java.util.Set;

public class MethodCustomArrayFlow {

    private final String owner;
    private final MethodNode method;
    private final MethodControlFlowGraph graph;
    private final Frame<BasicValue>[] beforeFrames;
    private final Frame<BasicValue>[] afterFrames;
    private final Set<Integer> warnedFallbackInstructions = new HashSet<>();

    MethodCustomArrayFlow(
            String owner,
            MethodNode method,
            MethodControlFlowGraph graph,
            Frame<BasicValue>[] beforeFrames,
            Frame<BasicValue>[] afterFrames
    ) {
        this.owner = owner;
        this.method = method;
        this.graph = graph;
        this.beforeFrames = beforeFrames;
        this.afterFrames = afterFrames;
    }

    public static MethodCustomArrayFlow analyze(JClassNode classNode, MethodNode methodNode) {
        return CustomArrayFlowAnalyzer.analyze(classNode, methodNode);
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

    public FlowSlotValue getStackSlotBefore(AbstractInsnNode insn, int depthFromTop) {
        return FlowSlotValue.of(getStackValueBefore(insn, depthFromTop));
    }

    public FlowSlotValue getLocalSlotBefore(AbstractInsnNode insn, int localIndex) {
        return FlowSlotValue.of(getLocalValueBefore(insn, localIndex));
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

    public TrackedArrayValue getMethodArgumentValue(MethodInsnNode insn, int argumentIndex) {
        Frame<BasicValue> frame = getFrame(beforeFrames, insn);
        if (frame == null) {
            return TrackedArrayValue.uninitialized();
        }
        Type[] argumentTypes = Type.getArgumentTypes(insn.desc);
        int stackIndex = frame.getStackSize();
        for (int i = argumentTypes.length - 1; i >= 0; --i) {
            stackIndex -= 1;
            if (i == argumentIndex) {
                return stackIndex >= 0 && stackIndex < frame.getStackSize()
                        ? safe(frame.getStack(stackIndex))
                        : TrackedArrayValue.uninitialized();
            }
        }
        return TrackedArrayValue.uninitialized();
    }

    public TrackedArrayValue getMethodReceiverValue(MethodInsnNode insn) {
        Frame<BasicValue> frame = getFrame(beforeFrames, insn);
        if (frame == null || insn.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC) {
            return TrackedArrayValue.uninitialized();
        }
        int stackIndex = frame.getStackSize() - Type.getArgumentTypes(insn.desc).length - 1;
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
                String.format("Falling back to JNI for %s.%s%s @ %d during %s: %s [%s]",
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

    public MethodControlFlowGraph graph() {
        return graph;
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
        return TrackedArrayValue.fromVerifierType(value.getType());
    }
}
