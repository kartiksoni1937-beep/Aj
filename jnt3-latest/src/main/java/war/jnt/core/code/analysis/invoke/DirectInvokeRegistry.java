package war.jnt.core.code.analysis.invoke;

import org.objectweb.asm.tree.MethodInsnNode;
import war.metaphor.tree.ClassMethod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class DirectInvokeRegistry {

    private final Map<MethodInsnNode, ResolvedInvokeSite> resolvedSites;
    private final Set<ClassMethod> recursiveMethods;
    private final Set<ClassMethod> frameSensitiveMethods;

    public DirectInvokeRegistry(
            Map<MethodInsnNode, ResolvedInvokeSite> resolvedSites,
            Set<ClassMethod> recursiveMethods,
            Set<ClassMethod> frameSensitiveMethods
    ) {
        this.resolvedSites = Collections.unmodifiableMap(new IdentityHashMap<>(resolvedSites));
        this.recursiveMethods = Set.copyOf(recursiveMethods);
        this.frameSensitiveMethods = Set.copyOf(frameSensitiveMethods);
    }

    public DirectInvokeSitePlan planFor(ClassMethod caller, MethodInsnNode insn) {
        ResolvedInvokeSite resolvedInvokeSite = resolvedSites.get(insn);
        if (resolvedInvokeSite == null) {
            return DirectInvokeSitePlan.jni(DirectInvokeFallbackReason.LIBRARY_OR_MISSING_TARGET);
        }

        if (resolvedInvokeSite.directTarget() == null) {
            return DirectInvokeSitePlan.jni(resolvedInvokeSite.fallbackReason());
        }

        if (recursiveMethods.contains(caller) || recursiveMethods.contains(resolvedInvokeSite.directTarget().classMethod())) {
            return DirectInvokeSitePlan.jni(DirectInvokeFallbackReason.RECURSIVE_DIRECT_GRAPH);
        }

        if (frameSensitiveMethods.contains(caller) || frameSensitiveMethods.contains(resolvedInvokeSite.directTarget().classMethod())) {
            return DirectInvokeSitePlan.jni(DirectInvokeFallbackReason.FRAME_SENSITIVE);
        }

        return DirectInvokeSitePlan.direct(resolvedInvokeSite.directTarget());
    }

    public Set<ClassMethod> recursiveMethods() {
        return recursiveMethods;
    }

    public Set<ClassMethod> frameSensitiveMethods() {
        return frameSensitiveMethods;
    }

    record ResolvedInvokeSite(
            DirectInvokeTarget directTarget,
            DirectInvokeFallbackReason fallbackReason
    ) {
    }
}
