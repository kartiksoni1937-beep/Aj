package war.jnt.core.code.analysis.invoke;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import war.jnt.fusebox.impl.Internal;
import war.metaphor.base.ObfuscatorContext;
import war.metaphor.tree.ClassMethod;
import war.metaphor.tree.JClassNode;
import war.metaphor.util.asm.BytecodeUtil;

import java.lang.reflect.Modifier;
import java.util.*;

public final class DirectInvokeRegistryBuilder implements Opcodes {

    private static final Set<String> FRAME_SENSITIVE_METHODS = Set.of(
            "java/lang/Thread#currentThread.()Ljava/lang/Thread;",
            "java/lang/Thread#getStackTrace.()[Ljava/lang/StackTraceElement;",
            "java/lang/Throwable#getStackTrace.()[Ljava/lang/StackTraceElement;",
            "java/lang/Throwable#fillInStackTrace.()Ljava/lang/Throwable;",
            "java/lang/invoke/MethodHandles#lookup.()Ljava/lang/invoke/MethodHandles$Lookup;"
    );

    private DirectInvokeRegistryBuilder() {}

    public static DirectInvokeRegistry build(Set<JClassNode> classes) {
        Map<MethodInsnNode, DirectInvokeRegistry.ResolvedInvokeSite> resolvedSites = new IdentityHashMap<>();
        Map<ClassMethod, Set<ClassMethod>> candidateGraph = new HashMap<>();
        Set<ClassMethod> transpiledMethods = collectTranspiledMethods(classes);
        Set<ClassMethod> frameSensitiveSeeds = new HashSet<>();

        for (ClassMethod caller : transpiledMethods) {
            MethodNode methodNode = caller.getMember();
            methodNode.instructions.toArray();

            if (isFrameSensitive(methodNode)) {
                frameSensitiveSeeds.add(caller);
            }

            for (AbstractInsnNode insn : methodNode.instructions) {
                if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                    continue;
                }

                int opcode = methodInsnNode.getOpcode();
                if (opcode != INVOKESTATIC && opcode != INVOKEVIRTUAL && opcode != INVOKESPECIAL && opcode != INVOKEINTERFACE) {
                    continue;
                }

                DirectInvokeRegistry.ResolvedInvokeSite site = resolveSite(caller, methodInsnNode, transpiledMethods);
                resolvedSites.put(methodInsnNode, site);
                if (site.directTarget() != null) {
                    candidateGraph.computeIfAbsent(caller, ignored -> new HashSet<>()).add(site.directTarget().classMethod());
                }
            }
        }

        Set<ClassMethod> recursiveMethods = computeRecursiveMethods(candidateGraph);
        Set<ClassMethod> frameSensitiveMethods = propagateFrameSensitive(candidateGraph, frameSensitiveSeeds);
        return new DirectInvokeRegistry(resolvedSites, recursiveMethods, frameSensitiveMethods);
    }

    private static Set<ClassMethod> collectTranspiledMethods(Set<JClassNode> classes) {
        Set<ClassMethod> transpiledMethods = new HashSet<>();
        for (JClassNode node : classes) {
            if (node.isExempt()) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if (node.isExempt(method) || Internal.disallowedTranspile(node, method)) {
                    continue;
                }
                transpiledMethods.add(ClassMethod.of(node, method));
            }
        }
        return transpiledMethods;
    }

    private static DirectInvokeRegistry.ResolvedInvokeSite resolveSite(
            ClassMethod caller,
            MethodInsnNode insn,
            Set<ClassMethod> transpiledMethods
    ) {
        JClassNode invokeOwner = ObfuscatorContext.INSTANCE.loadClass(insn.owner);
        if (invokeOwner == null || invokeOwner.isLibrary()) {
            return fallback(DirectInvokeFallbackReason.LIBRARY_OR_MISSING_TARGET);
        }

        if (insn.getOpcode() == INVOKEINTERFACE || invokeOwner.isInterface()) {
            return fallback(DirectInvokeFallbackReason.INTERFACE_DISPATCH);
        }

        ClassMethod resolvedMethod = BytecodeUtil.getMethodNode(invokeOwner, insn.name, insn.desc);
        if (resolvedMethod == null) {
            return fallback(DirectInvokeFallbackReason.LIBRARY_OR_MISSING_TARGET);
        }

        JClassNode resolvedOwner = resolvedMethod.getClassNode();
        MethodNode resolvedMethodNode = resolvedMethod.getMember();
        if (resolvedOwner == null || resolvedOwner.isLibrary()) {
            return fallback(DirectInvokeFallbackReason.LIBRARY_OR_MISSING_TARGET);
        }
        if (Modifier.isAbstract(resolvedMethodNode.access)) {
            return fallback(DirectInvokeFallbackReason.ABSTRACT_TARGET);
        }
        if (!transpiledMethods.contains(resolvedMethod)) {
            return fallback(DirectInvokeFallbackReason.NOT_TRANSPILLED);
        }
        if (Internal.isAccess(resolvedMethodNode.access, ACC_SYNCHRONIZED)) {
            return fallback(DirectInvokeFallbackReason.SYNCHRONIZED_CALLEE);
        }

        return switch (insn.getOpcode()) {
            case INVOKESTATIC -> resolveStaticSite(caller, resolvedMethod, resolvedOwner, resolvedMethodNode);
            case INVOKESPECIAL -> direct(resolvedMethod, resolvedOwner, resolvedMethodNode, insn.getOpcode());
            case INVOKEVIRTUAL -> resolveVirtualSite(invokeOwner, resolvedMethod, resolvedOwner, resolvedMethodNode, insn.getOpcode());
            default -> fallback(DirectInvokeFallbackReason.LIBRARY_OR_MISSING_TARGET);
        };
    }

    private static DirectInvokeRegistry.ResolvedInvokeSite resolveStaticSite(
            ClassMethod caller,
            ClassMethod resolvedMethod,
            JClassNode resolvedOwner,
            MethodNode resolvedMethodNode
    ) {
        if (!caller.getClassNode().name.equals(resolvedOwner.name)) {
            return fallback(DirectInvokeFallbackReason.CROSS_CLASS_STATIC_INIT);
        }
        return direct(resolvedMethod, resolvedOwner, resolvedMethodNode, INVOKESTATIC);
    }

    private static DirectInvokeRegistry.ResolvedInvokeSite resolveVirtualSite(
            JClassNode invokeOwner,
            ClassMethod resolvedMethod,
            JClassNode resolvedOwner,
            MethodNode resolvedMethodNode,
            int opcode
    ) {
        boolean exactDispatch =
                Internal.isAccess(resolvedMethodNode.access, ACC_PRIVATE)
                        || Internal.isAccess(resolvedMethodNode.access, ACC_FINAL)
                        || invokeOwner.isFinal();
        if (!exactDispatch) {
            return fallback(DirectInvokeFallbackReason.OPEN_VIRTUAL);
        }
        return direct(resolvedMethod, resolvedOwner, resolvedMethodNode, opcode);
    }

    private static DirectInvokeRegistry.ResolvedInvokeSite direct(
            ClassMethod resolvedMethod,
            JClassNode resolvedOwner,
            MethodNode resolvedMethodNode,
            int opcode
    ) {
        return new DirectInvokeRegistry.ResolvedInvokeSite(
                new DirectInvokeTarget(resolvedMethod, resolvedOwner, resolvedMethodNode, opcode),
                null
        );
    }

    private static DirectInvokeRegistry.ResolvedInvokeSite fallback(DirectInvokeFallbackReason reason) {
        return new DirectInvokeRegistry.ResolvedInvokeSite(null, reason);
    }

    private static boolean isFrameSensitive(MethodNode methodNode) {
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (!(insn instanceof MethodInsnNode methodInsnNode)) {
                continue;
            }

            String signature = methodInsnNode.owner + "#" + methodInsnNode.name + "." + methodInsnNode.desc;
            if (FRAME_SENSITIVE_METHODS.contains(signature)) {
                return true;
            }
            if (methodInsnNode.owner.startsWith("java/lang/StackWalker")) {
                return true;
            }
        }
        return false;
    }

    private static Set<ClassMethod> computeRecursiveMethods(Map<ClassMethod, Set<ClassMethod>> graph) {
        Set<ClassMethod> recursive = new HashSet<>();
        Map<ClassMethod, Integer> indexByNode = new HashMap<>();
        Map<ClassMethod, Integer> lowLinkByNode = new HashMap<>();
        Deque<ClassMethod> stack = new ArrayDeque<>();
        Set<ClassMethod> onStack = new HashSet<>();
        int[] nextIndex = {0};

        Set<ClassMethod> nodes = new HashSet<>(graph.keySet());
        for (Set<ClassMethod> edges : graph.values()) {
            nodes.addAll(edges);
        }

        for (ClassMethod node : nodes) {
            if (!indexByNode.containsKey(node)) {
                strongConnect(node, graph, recursive, indexByNode, lowLinkByNode, stack, onStack, nextIndex);
            }
        }
        return recursive;
    }

    private static void strongConnect(
            ClassMethod node,
            Map<ClassMethod, Set<ClassMethod>> graph,
            Set<ClassMethod> recursive,
            Map<ClassMethod, Integer> indexByNode,
            Map<ClassMethod, Integer> lowLinkByNode,
            Deque<ClassMethod> stack,
            Set<ClassMethod> onStack,
            int[] nextIndex
    ) {
        indexByNode.put(node, nextIndex[0]);
        lowLinkByNode.put(node, nextIndex[0]);
        nextIndex[0]++;
        stack.push(node);
        onStack.add(node);

        for (ClassMethod neighbor : graph.getOrDefault(node, Set.of())) {
            if (!indexByNode.containsKey(neighbor)) {
                strongConnect(neighbor, graph, recursive, indexByNode, lowLinkByNode, stack, onStack, nextIndex);
                lowLinkByNode.put(node, Math.min(lowLinkByNode.get(node), lowLinkByNode.get(neighbor)));
            } else if (onStack.contains(neighbor)) {
                lowLinkByNode.put(node, Math.min(lowLinkByNode.get(node), indexByNode.get(neighbor)));
            }
        }

        if (!Objects.equals(lowLinkByNode.get(node), indexByNode.get(node))) {
            return;
        }

        List<ClassMethod> component = new ArrayList<>();
        ClassMethod member;
        do {
            member = stack.pop();
            onStack.remove(member);
            component.add(member);
        } while (!node.equals(member));

        if (component.size() > 1) {
            recursive.addAll(component);
            return;
        }

        if (graph.getOrDefault(node, Set.of()).contains(node)) {
            recursive.add(node);
        }
    }

    private static Set<ClassMethod> propagateFrameSensitive(
            Map<ClassMethod, Set<ClassMethod>> graph,
            Set<ClassMethod> seeds
    ) {
        Map<ClassMethod, Set<ClassMethod>> reverseGraph = new HashMap<>();
        for (Map.Entry<ClassMethod, Set<ClassMethod>> entry : graph.entrySet()) {
            reverseGraph.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>());
            for (ClassMethod callee : entry.getValue()) {
                reverseGraph.computeIfAbsent(callee, ignored -> new HashSet<>()).add(entry.getKey());
            }
        }

        Set<ClassMethod> frameSensitive = new HashSet<>(seeds);
        Deque<ClassMethod> work = new ArrayDeque<>(seeds);
        while (!work.isEmpty()) {
            ClassMethod method = work.removeFirst();
            for (ClassMethod caller : reverseGraph.getOrDefault(method, Set.of())) {
                if (frameSensitive.add(caller)) {
                    work.addLast(caller);
                }
            }
        }
        return frameSensitive;
    }
}
