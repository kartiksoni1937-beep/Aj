package war.jnt.core.code.analysis.exception;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import war.jnt.core.code.analysis.PrimitiveArrayKind;
import war.jnt.core.code.analysis.TrackedArrayValue;
import war.jnt.core.code.analysis.flow.MethodCustomArrayFlow;
import war.jnt.core.code.impl.BlockUnit;
import war.jnt.core.code.impl.invoke.InvocationUnit;

import java.util.*;

public final class ExceptionPlanBuilder implements Opcodes {

    private ExceptionPlanBuilder() {}

    public static MethodExceptionPlan build(
            ClassNode classNode,
            MethodNode methodNode,
            MethodCustomArrayFlow arrayFlow,
            boolean intrinsicEnabled
    ) {
        methodNode.instructions.toArray();

        ExceptionSitePlan[] sitePlans = new ExceptionSitePlan[methodNode.instructions.size()];
        List<TryCatchBlockNode> tryCatchBlocks = methodNode.tryCatchBlocks == null
                ? List.of()
                : methodNode.tryCatchBlocks;

        Map<TryCatchBlockNode, Integer> tryCatchOrder = new IdentityHashMap<>();
        for (int i = 0; i < tryCatchBlocks.size(); i++) {
            tryCatchOrder.put(tryCatchBlocks.get(i), i);
        }

        LinkedHashMap<ExceptionDispatchKey, ExceptionDispatchGroup> groups = new LinkedHashMap<>();
        int nextGroupId = 0;
        for (AbstractInsnNode insn : methodNode.instructions) {
            int index = insn.index;
            if (index < 0) {
                continue;
            }

            ExceptionSiteKind siteKind = classify(insn, arrayFlow, intrinsicEnabled);
            List<TryCatchBlockNode> activeCatches = activeCatchesFor(insn, tryCatchBlocks, tryCatchOrder);
            ExceptionDispatchGroup dispatchGroup = null;
            if (!activeCatches.isEmpty()) {
                ExceptionDispatchKey key = toDispatchKey(activeCatches);
                dispatchGroup = groups.get(key);
                if (dispatchGroup == null) {
                    dispatchGroup = new ExceptionDispatchGroup(nextGroupId++, key, activeCatches);
                    groups.put(key, dispatchGroup);
                }
            }

            sitePlans[index] = new ExceptionSitePlan(
                    siteKind,
                    dispatchGroup,
                    collectExplicitRaises(insn, siteKind, arrayFlow)
            );
        }

        return new MethodExceptionPlan(classNode.name, methodNode, sitePlans, new ArrayList<>(groups.values()));
    }

    private static List<TryCatchBlockNode> activeCatchesFor(
            AbstractInsnNode insn,
            List<TryCatchBlockNode> tryCatchBlocks,
            Map<TryCatchBlockNode, Integer> tryCatchOrder
    ) {
        if (insn.getOpcode() == -1 || tryCatchBlocks.isEmpty()) {
            return List.of();
        }

        List<TryCatchBlockNode> active = new ArrayList<>();
        for (TryCatchBlockNode tryCatchBlock : tryCatchBlocks) {
            if (covers(tryCatchBlock, insn)) {
                active.add(tryCatchBlock);
            }
        }
        active.sort(
                Comparator.comparingInt(ExceptionPlanBuilder::rangeWidth)
                        .thenComparingInt(tryCatchOrder::get)
        );
        return active;
    }

    private static boolean covers(TryCatchBlockNode tryCatchBlock, AbstractInsnNode insn) {
        return tryCatchBlock.start != null
                && tryCatchBlock.end != null
                && tryCatchBlock.start.index <= insn.index
                && insn.index < tryCatchBlock.end.index;
    }

    private static int rangeWidth(TryCatchBlockNode tryCatchBlock) {
        return Math.max(0, tryCatchBlock.end.index - tryCatchBlock.start.index);
    }

    private static ExceptionDispatchKey toDispatchKey(List<TryCatchBlockNode> catches) {
        List<ExceptionDispatchKey.CatchTarget> targets = new ArrayList<>(catches.size());
        for (TryCatchBlockNode tryCatchBlock : catches) {
            targets.add(new ExceptionDispatchKey.CatchTarget(
                    BlockUnit.resolveBlock(tryCatchBlock.handler),
                    tryCatchBlock.type
            ));
        }
        return new ExceptionDispatchKey(targets);
    }

    private static ExceptionSiteKind classify(
            AbstractInsnNode insn,
            MethodCustomArrayFlow arrayFlow,
            boolean intrinsicEnabled
    ) {
        int opcode = insn.getOpcode();
        if (opcode == -1) {
            return ExceptionSiteKind.NONE;
        }

        return switch (opcode) {
            case AALOAD,
                 IALOAD, LALOAD, FALOAD, DALOAD, BALOAD, CALOAD, SALOAD,
                 IASTORE, LASTORE, FASTORE, DASTORE, BASTORE, CASTORE, SASTORE,
                 GETFIELD, PUTFIELD,
                 CHECKCAST,
                 ARRAYLENGTH,
                 IDIV, IREM, LDIV, LREM,
                 ATHROW -> opcode == ATHROW
                    ? ExceptionSiteKind.ATHROW_DIRECT
                    : ExceptionSiteKind.EXPLICIT_RAISE;
            case AASTORE,
                 ANEWARRAY -> ExceptionSiteKind.EXPLICIT_RAISE_AND_JNI_POLL;
            case MONITORENTER,
                 MONITOREXIT -> ExceptionSiteKind.EXPLICIT_RAISE;
            case INVOKESTATIC -> classifyStaticInvoke((MethodInsnNode) insn, arrayFlow, intrinsicEnabled);
            case INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE ->
                    classifyReceiverInvoke((MethodInsnNode) insn, arrayFlow, intrinsicEnabled);
            case NEW,
                 INVOKEDYNAMIC,
                 MULTIANEWARRAY -> ExceptionSiteKind.JNI_POLL;
            case NEWARRAY -> arrayFlow != null
                    ? ExceptionSiteKind.EXPLICIT_RAISE
                    : ExceptionSiteKind.EXPLICIT_RAISE_AND_JNI_POLL;
            default -> ExceptionSiteKind.NONE;
        };
    }

    private static ExceptionSiteKind classifyStaticInvoke(
            MethodInsnNode insn,
            MethodCustomArrayFlow arrayFlow,
            boolean intrinsicEnabled
    ) {
        if (InvocationUnit.isPollFreeIntrinsic(insn, arrayFlow, intrinsicEnabled)) {
            return ExceptionSiteKind.NONE;
        }
        return ExceptionSiteKind.JNI_POLL;
    }

    private static ExceptionSiteKind classifyReceiverInvoke(
            MethodInsnNode insn,
            MethodCustomArrayFlow arrayFlow,
            boolean intrinsicEnabled
    ) {
        if (InvocationUnit.isPollFreeIntrinsic(insn, arrayFlow, intrinsicEnabled)) {
            return ExceptionSiteKind.EXPLICIT_RAISE;
        }
        return ExceptionSiteKind.EXPLICIT_RAISE_AND_JNI_POLL;
    }

    private static Set<ExceptionRaiseKind> collectExplicitRaises(
            AbstractInsnNode insn,
            ExceptionSiteKind siteKind,
            MethodCustomArrayFlow arrayFlow
    ) {
        if (!siteKind.hasExplicitRaisePath()) {
            return Set.of();
        }

        EnumSet<ExceptionRaiseKind> kinds = EnumSet.noneOf(ExceptionRaiseKind.class);
        switch (insn.getOpcode()) {
            case AALOAD,
                 AASTORE,
                 IALOAD, LALOAD, FALOAD, DALOAD, BALOAD, CALOAD, SALOAD,
                 IASTORE, LASTORE, FASTORE, DASTORE, BASTORE, CASTORE, SASTORE -> {
                kinds.add(ExceptionRaiseKind.NULL_POINTER);
                kinds.add(ExceptionRaiseKind.ARRAY_INDEX_OUT_OF_BOUNDS);
                if (insn.getOpcode() == AALOAD || insn.getOpcode() == AASTORE) {
                    maybeAddArrayBridgeOom(kinds, insn, arrayFlow);
                }
            }
            case GETFIELD, PUTFIELD,
                 INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE,
                 ARRAYLENGTH,
                 ATHROW -> kinds.add(ExceptionRaiseKind.NULL_POINTER);
            case CHECKCAST -> {
                kinds.add(ExceptionRaiseKind.CLASS_CAST);
                PrimitiveArrayKind targetKind = PrimitiveArrayKind.fromDescriptor(((TypeInsnNode) insn).desc);
                if (targetKind != null) {
                    kinds.add(ExceptionRaiseKind.OUT_OF_MEMORY);
                }
            }
            case IDIV, IREM, LDIV, LREM -> kinds.add(ExceptionRaiseKind.ARITHMETIC);
            case NEWARRAY, ANEWARRAY -> kinds.add(ExceptionRaiseKind.NEGATIVE_ARRAY_SIZE);
            case MONITORENTER, MONITOREXIT -> {
                kinds.add(ExceptionRaiseKind.NULL_POINTER);
                kinds.add(ExceptionRaiseKind.ILLEGAL_MONITOR_STATE);
            }
            default -> {
            }
        }
        return kinds;
    }

    private static void maybeAddArrayBridgeOom(
            Set<ExceptionRaiseKind> kinds,
            AbstractInsnNode insn,
            MethodCustomArrayFlow arrayFlow
    ) {
        if (arrayFlow == null) {
            return;
        }
        if (insn.getOpcode() == AALOAD) {
            TrackedArrayValue produced = arrayFlow.getStackValueAfter(insn, 0);
            if (produced.isCustomPrimitiveArray()) {
                kinds.add(ExceptionRaiseKind.OUT_OF_MEMORY);
            }
            return;
        }

        TrackedArrayValue stored = arrayFlow.getStackValueBefore(insn, 0);
        if (stored.isCustomPrimitiveArray()) {
            kinds.add(ExceptionRaiseKind.OUT_OF_MEMORY);
        }
    }
}
