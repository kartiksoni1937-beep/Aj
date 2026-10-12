package war.jnt.core.code.analysis.flow;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class MethodControlFlowGraph implements Opcodes {

    private final MethodNode method;
    private final List<BasicBlock> blocks;
    private final Map<AbstractInsnNode, List<AbstractInsnNode>> normalSuccessors;
    private final Map<AbstractInsnNode, List<TryCatchBlockNode>> exceptionHandlers;
    private final Map<AbstractInsnNode, BasicBlock> blockByInstruction;

    private MethodControlFlowGraph(
            MethodNode method,
            List<BasicBlock> blocks,
            Map<AbstractInsnNode, List<AbstractInsnNode>> normalSuccessors,
            Map<AbstractInsnNode, List<TryCatchBlockNode>> exceptionHandlers,
            Map<AbstractInsnNode, BasicBlock> blockByInstruction
    ) {
        this.method = method;
        this.blocks = blocks;
        this.normalSuccessors = normalSuccessors;
        this.exceptionHandlers = exceptionHandlers;
        this.blockByInstruction = blockByInstruction;
    }

    public static MethodControlFlowGraph build(MethodNode method) {
        method.instructions.toArray();

        Map<AbstractInsnNode, List<AbstractInsnNode>> normalSuccessors = buildNormalSuccessors(method);
        Map<AbstractInsnNode, List<TryCatchBlockNode>> exceptionHandlers = buildExceptionHandlers(method);
        List<BasicBlock> blocks = buildBlocks(method, normalSuccessors, exceptionHandlers);
        Map<AbstractInsnNode, BasicBlock> blockByInstruction = new IdentityHashMap<>();
        for (BasicBlock block : blocks) {
            for (AbstractInsnNode insn : block.instructions()) {
                blockByInstruction.put(insn, block);
            }
        }

        for (BasicBlock block : blocks) {
            AbstractInsnNode tail = block.instructions().isEmpty()
                    ? null
                    : block.instructions().get(block.instructions().size() - 1);
            if (tail == null) {
                continue;
            }
            for (AbstractInsnNode successor : normalSuccessors.getOrDefault(tail, List.of())) {
                block.addNormalSuccessor(blockByInstruction.get(successor));
            }
            for (TryCatchBlockNode handler : exceptionHandlers.getOrDefault(tail, List.of())) {
                block.addExceptionalSuccessor(blockByInstruction.get(handler.handler));
            }
        }

        return new MethodControlFlowGraph(method, blocks, normalSuccessors, exceptionHandlers, blockByInstruction);
    }

    public MethodNode method() {
        return method;
    }

    public List<BasicBlock> blocks() {
        return blocks;
    }

    public List<AbstractInsnNode> normalSuccessorsOf(AbstractInsnNode insn) {
        return normalSuccessors.getOrDefault(insn, List.of());
    }

    public List<TryCatchBlockNode> exceptionHandlersOf(AbstractInsnNode insn) {
        return exceptionHandlers.getOrDefault(insn, List.of());
    }

    public BasicBlock blockOf(AbstractInsnNode insn) {
        return blockByInstruction.get(insn);
    }

    private static Map<AbstractInsnNode, List<AbstractInsnNode>> buildNormalSuccessors(MethodNode method) {
        Map<AbstractInsnNode, List<AbstractInsnNode>> successors = new IdentityHashMap<>();
        for (AbstractInsnNode insn : method.instructions) {
            List<AbstractInsnNode> next = new ArrayList<>();
            if (insn.getOpcode() == -1) {
                if (insn.getNext() != null) {
                    next.add(insn.getNext());
                }
                successors.put(insn, List.copyOf(next));
                continue;
            }

            switch (insn) {
                case JumpInsnNode jumpInsnNode -> {
                    next.add(jumpInsnNode.label);
                    if (jumpInsnNode.getOpcode() != GOTO && insn.getNext() != null) {
                        next.add(insn.getNext());
                    }
                }
                case LookupSwitchInsnNode lookupSwitchInsnNode -> {
                    next.add(lookupSwitchInsnNode.dflt);
                    next.addAll(lookupSwitchInsnNode.labels);
                }
                case TableSwitchInsnNode tableSwitchInsnNode -> {
                    next.add(tableSwitchInsnNode.dflt);
                    next.addAll(tableSwitchInsnNode.labels);
                }
                default -> {
                    int opcode = insn.getOpcode();
                    if (opcode != ATHROW
                            && opcode != RETURN
                            && opcode != IRETURN
                            && opcode != LRETURN
                            && opcode != FRETURN
                            && opcode != DRETURN
                            && opcode != ARETURN
                            && insn.getNext() != null) {
                        next.add(insn.getNext());
                    }
                }
            }

            successors.put(insn, List.copyOf(next));
        }
        return successors;
    }

    private static Map<AbstractInsnNode, List<TryCatchBlockNode>> buildExceptionHandlers(MethodNode method) {
        Map<AbstractInsnNode, List<TryCatchBlockNode>> handlers = new IdentityHashMap<>();
        if (method.tryCatchBlocks == null || method.tryCatchBlocks.isEmpty()) {
            return handlers;
        }

        for (AbstractInsnNode insn : method.instructions) {
            if (insn.getOpcode() == -1 || !mayThrow(insn)) {
                continue;
            }
            List<TryCatchBlockNode> active = new ArrayList<>();
            for (TryCatchBlockNode tryCatchBlock : method.tryCatchBlocks) {
                if (tryCatchBlock.start != null
                        && tryCatchBlock.end != null
                        && tryCatchBlock.start.index <= insn.index
                        && insn.index < tryCatchBlock.end.index) {
                    active.add(tryCatchBlock);
                }
            }
            if (!active.isEmpty()) {
                handlers.put(insn, List.copyOf(active));
            }
        }

        return handlers;
    }

    private static List<BasicBlock> buildBlocks(
            MethodNode method,
            Map<AbstractInsnNode, List<AbstractInsnNode>> normalSuccessors,
            Map<AbstractInsnNode, List<TryCatchBlockNode>> exceptionHandlers
    ) {
        Set<AbstractInsnNode> leaders = Collections.newSetFromMap(new IdentityHashMap<>());
        if (method.instructions.getFirst() != null) {
            leaders.add(method.instructions.getFirst());
        }

        for (TryCatchBlockNode tryCatchBlock : method.tryCatchBlocks == null ? List.<TryCatchBlockNode>of() : method.tryCatchBlocks) {
            if (tryCatchBlock.start != null) {
                leaders.add(tryCatchBlock.start);
            }
            if (tryCatchBlock.handler != null) {
                leaders.add(tryCatchBlock.handler);
            }
        }

        for (AbstractInsnNode insn : method.instructions) {
            for (AbstractInsnNode successor : normalSuccessors.getOrDefault(insn, List.of())) {
                leaders.add(successor);
            }
            if (!exceptionHandlers.getOrDefault(insn, List.of()).isEmpty() && insn.getNext() != null) {
                leaders.add(insn.getNext());
            }
        }

        List<BasicBlock> blocks = new ArrayList<>();
        BasicBlock current = null;
        for (AbstractInsnNode insn : method.instructions) {
            if (current == null || leaders.contains(insn)) {
                current = new BasicBlock(blocks.size(), insn);
                blocks.add(current);
            }
            current.addInstruction(insn);
        }
        return blocks;
    }

    private static boolean mayThrow(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        if (opcode == -1) {
            return false;
        }
        if (insn instanceof MethodInsnNode
                || insn instanceof InvokeDynamicInsnNode
                || insn instanceof FieldInsnNode
                || insn instanceof TypeInsnNode
                || insn instanceof MultiANewArrayInsnNode) {
            return true;
        }
        return switch (opcode) {
            case AALOAD, BALOAD, CALOAD, SALOAD, IALOAD, LALOAD, FALOAD, DALOAD,
                 AASTORE, BASTORE, CASTORE, SASTORE, IASTORE, LASTORE, FASTORE, DASTORE,
                 ARRAYLENGTH,
                 IDIV, IREM, LDIV, LREM,
                 ATHROW,
                 MONITORENTER, MONITOREXIT,
                 NEWARRAY,
                 ANEWARRAY,
                 CHECKCAST -> true;
            default -> false;
        };
    }
}
