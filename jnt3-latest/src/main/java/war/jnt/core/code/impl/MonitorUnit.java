package war.jnt.core.code.impl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;
import war.jnt.cache.Cache;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;

public class MonitorUnit {

    public static void process(InsnNode insn, UnitContext ctx, TempJumpVM tjvm) {
        String object = Internal.computePop(ctx.getTracker());
        int npeIdx = Cache.Companion.request_klass("java/lang/NullPointerException");
        int imseIdx = Cache.Companion.request_klass("java/lang/IllegalMonitorStateException");

        ctx.fmtAppend(
                "\tif (%s.l == NULL) { %s; goto %s; }\n",
                object,
                JniDispatchCodegen.throwNew(
                        ctx,
                        tjvm,
                        insn,
                        "monitor-null",
                        "request_klass(env, " + npeIdx + ")",
                        "monitor is null"
                ),
                ctx.handlerLabel
        );

        if (insn.getOpcode() == Opcodes.MONITORENTER) {
            ctx.fmtAppend(
                    "\tif (%s != JNI_OK && !%s) { %s; goto %s; }\n",
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.MONITOR_ENTER,
                            "MonitorUnit#enter#" + object,
                            "(jint (*)(JNIEnv *, jobject))",
                            "env",
                            object + ".l"
                    ),
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.EXCEPTION_CHECK,
                            "MonitorUnit#enter#check#" + object,
                            "(jboolean (*)(JNIEnv *))",
                            "env"
                    ),
                    JniDispatchCodegen.throwNew(
                            ctx,
                            tjvm,
                            insn,
                            "monitor-enter-failed",
                            "request_klass(env, " + imseIdx + ")",
                            "monitor enter failed"
                    ),
                    ctx.handlerLabel
            );
        } else {
            ctx.fmtAppend(
                    "\tif (%s != JNI_OK && !%s) { %s; goto %s; }\n",
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.MONITOR_EXIT,
                            "MonitorUnit#exit#" + object,
                            "(jint (*)(JNIEnv *, jobject))",
                            "env",
                            object + ".l"
                    ),
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.EXCEPTION_CHECK,
                            "MonitorUnit#exit#check#" + object,
                            "(jboolean (*)(JNIEnv *))",
                            "env"
                    ),
                    JniDispatchCodegen.throwNew(
                            ctx,
                            tjvm,
                            insn,
                            "monitor-exit-failed",
                            "request_klass(env, " + imseIdx + ")",
                            "monitor exit failed"
                    ),
                    ctx.handlerLabel
            );
        }
    }
}
