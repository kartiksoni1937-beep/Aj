package war.jnt.core.code.analysis.invoke;

import org.objectweb.asm.tree.MethodNode;
import war.metaphor.tree.ClassMethod;
import war.metaphor.tree.JClassNode;

public record DirectInvokeTarget(
        ClassMethod classMethod,
        JClassNode owner,
        MethodNode methodNode,
        int opcode
) {
}
