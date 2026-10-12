package war.jnt.core.code.impl;

import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.MethodNode;
import war.jnt.core.code.UnitContext;

public class IincUnit {
    public static void process(IincInsnNode iinc, UnitContext ctx, MethodNode method) {
        ctx.fmtAppend("\tlocals[%d].i += %d;\n", iinc.var, iinc.incr);
    }
}
