package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;
import war.jnt.innercache.InnerCache;
import war.metaphor.util.interfaces.IRandom;

public class ThrowUnit implements Opcodes, IRandom {

    public static void process(final InnerCache ic, InsnNode insn, UnitContext ctx, TempJumpVM tjvm) {
        String npe = ic.FindClass("java/lang/NullPointerException");

        ctx.fmtAppend("\t{\n");
        ctx.fmtAppend("\t\tjthrowable thrown = (jthrowable) %s.l;\n", Internal.computePop(ctx.getTracker()));
        ctx.fmtAppend("\t\tif (thrown == NULL) {\n");
        ctx.fmtAppend(
                "\t\t\t%s;\n",
                JniDispatchCodegen.callExpr(
                        ctx,
                        tjvm,
                        insn,
                        JniBindingId.THROW_NEW,
                        "athrow-null",
                        "(jint (*)(JNIEnv *, jclass, const char *))",
                        "env",
                        npe,
                        "\"NullPointerException\""
                )
        );
        ctx.fmtAppend("\t\t} else {\n");
        ctx.fmtAppend(
                "\t\t\t%s;\n",
                JniDispatchCodegen.callExpr(
                        ctx,
                        tjvm,
                        insn,
                        JniBindingId.THROW,
                        "athrow",
                        "(jint (*)(JNIEnv *, jthrowable))",
                        "env",
                        "thrown"
                )
        );
        ctx.fmtAppend("\t\t}\n");
        ctx.fmtAppend("\t\tgoto %s;\n", ctx.handlerLabel);
        ctx.fmtAppend("\t}\n");
    }
}
