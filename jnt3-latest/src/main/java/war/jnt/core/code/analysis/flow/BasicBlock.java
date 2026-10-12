package war.jnt.core.code.analysis.flow;

import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BasicBlock {

    private final int id;
    private final AbstractInsnNode start;
    private final List<AbstractInsnNode> instructions = new ArrayList<>();
    private final List<BasicBlock> normalSuccessors = new ArrayList<>();
    private final List<BasicBlock> exceptionalSuccessors = new ArrayList<>();

    public BasicBlock(int id, AbstractInsnNode start) {
        this.id = id;
        this.start = start;
    }

    public int id() {
        return id;
    }

    public AbstractInsnNode start() {
        return start;
    }

    public List<AbstractInsnNode> instructions() {
        return Collections.unmodifiableList(instructions);
    }

    public List<BasicBlock> normalSuccessors() {
        return Collections.unmodifiableList(normalSuccessors);
    }

    public List<BasicBlock> exceptionalSuccessors() {
        return Collections.unmodifiableList(exceptionalSuccessors);
    }

    void addInstruction(AbstractInsnNode insn) {
        instructions.add(insn);
    }

    void addNormalSuccessor(BasicBlock successor) {
        if (successor != null && !normalSuccessors.contains(successor)) {
            normalSuccessors.add(successor);
        }
    }

    void addExceptionalSuccessor(BasicBlock successor) {
        if (successor != null && !exceptionalSuccessors.contains(successor)) {
            exceptionalSuccessors.add(successor);
        }
    }
}
