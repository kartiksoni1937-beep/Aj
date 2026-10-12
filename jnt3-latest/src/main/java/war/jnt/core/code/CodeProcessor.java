package war.jnt.core.code;

import lombok.SneakyThrows;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import war.configuration.ConfigurationSection;
import war.jnt.annotate.AnnotationManager;
import war.jnt.api.CustomArray;
import war.jnt.api.LimitCache;
import war.jnt.cache.Cache;
import war.jnt.core.Processor;
import war.jnt.core.code.analysis.PrimitiveArrayKind;
import war.jnt.core.code.analysis.flow.MethodCustomArrayFlow;
import war.jnt.core.code.analysis.exception.ExceptionDispatchGroup;
import war.jnt.core.code.analysis.exception.ExceptionPlanBuilder;
import war.jnt.core.code.analysis.exception.ExceptionSitePlan;
import war.jnt.core.code.analysis.exception.MethodExceptionPlan;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchConfiguration;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.code.bindings.JniMarkerCodec;
import war.jnt.core.code.analysis.invoke.DirectInvokeRegistry;
import war.jnt.core.code.analysis.invoke.MethodInvokePlan;
import war.jnt.core.code.analysis.invoke.MethodInvokePlanBuilder;
import war.jnt.core.code.impl.*;
import war.jnt.core.code.impl.field.FieldUnit;
import war.jnt.core.code.impl.invoke.InvocationUnit;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.dash.Level;
import war.jnt.dash.Logger;
import war.jnt.dash.Origin;
import war.jnt.fusebox.impl.Internal;
import war.jnt.fusebox.impl.VariableManager;
import war.jnt.innercache.InnerCache;
import war.jnt.stack.StackTracker;
import war.metaphor.analysis.graph.ControlFlowGraph;
import war.metaphor.tree.JClassNode;
import war.metaphor.util.asm.BytecodeUtil;

import java.lang.reflect.Modifier;
import java.util.*;

public class CodeProcessor implements Opcodes {

    @SneakyThrows
    public static String forMethod(JClassNode node, MethodNode method, ConfigurationSection config) {
        JniDispatchConfiguration.configure(config);
        List<TryCatchBlockNode> tryCatchBlocks = method.tryCatchBlocks;
        ControlFlowGraph graph = new ControlFlowGraph(node, method);
        graph.compute();

        Map<AbstractInsnNode, Frame<BasicValue>> frames = graph.getFrames();

        boolean hasLimitCache = AnnotationManager.getAnnotation(method, LimitCache.class);
        boolean isCustomArray = AnnotationManager.getAnnotation(method, CustomArray.class);
        Type returnType = Type.getReturnType(method.desc);

        var builder = new StringBuilder();

        var tjvm = new TempJumpVM(builder, config.getConfigurationSection("mutators.jnt.virtualize").getBoolean("enabled", false), config.getConfigurationSection("mutators.jnt.virtualize").getInt("chance"));
        tjvm.insertSetupCode();

        builder.append("/* MAX_STACK */");
        builder.append(Internal.createLocals(method.maxLocals));

        var tracker = new StackTracker();
        UnitContext ctx = new UnitContext(builder, tracker, node, method, false);
        MethodCustomArrayFlow methodCustomArrayFlow = isCustomArray ? MethodCustomArrayFlow.analyze(node, method) : null;
        ctx.setMethodCustomArrayFlow(methodCustomArrayFlow);
        MethodExceptionPlan methodExceptionPlan = ExceptionPlanBuilder.build(
                node,
                method,
                methodCustomArrayFlow,
                config.getBoolean("mutators.jnt.intrinsic", true)
        );
        ctx.setMethodExceptionPlan(methodExceptionPlan);
        DirectInvokeRegistry directInvokeRegistry = Processor.INSTANCE != null
                ? Processor.INSTANCE.getDirectInvokeRegistry()
                : null;
        MethodInvokePlan methodInvokePlan = MethodInvokePlanBuilder.build(
                node,
                method,
                directInvokeRegistry,
                methodCustomArrayFlow,
                config.getBoolean("mutators.jnt.intrinsic", true)
        );
        ctx.setMethodInvokePlan(methodInvokePlan);

        ctx.fmtAppend("\t/* %s */\n", String.format("%s -> %s%s", node.name, method.name, method.desc));
        ctx.fmtAppend(
                "\t/* exception-plan: polls=%d dispatchGroups=%d explicit=%d */\n",
                methodExceptionPlan.pollSiteCount(),
                methodExceptionPlan.dispatchGroups().size(),
                methodExceptionPlan.explicitRaiseSiteCount()
        );
        if (isCustomArray) {
            PrimitiveArrayUnit.emitArrayScopeDeclaration(ctx);
            PrimitiveArrayUnit.emitReturnStateDeclaration(ctx, returnType);
            PrimitiveArrayUnit.emitArrayScopeEnter(ctx);
        }

        // TODO: need to edit it a bit, sometimes it will crash the entire thing ;o
//        if (config.getBoolean("mutators.jnt.traceless", false))
//            ctx.fmtAppend("jnt_reset_traceback(env);\n");

        var varMan = new VariableManager();
        final InnerCache ic = new InnerCache(ctx, varMan, hasLimitCache);

        initializeParameterLocals(ic, ctx, tjvm, isCustomArray);
        int index = -1;
        for (AbstractInsnNode insn : method.instructions) {
            index++;
            ExceptionSitePlan exceptionSitePlan = methodExceptionPlan.getSitePlan(insn);
            ctx.handlerLabel = exceptionSitePlan.handlerLabelOr("unwind");

            int prev = ctx.getBuilder().length();

            ctx.fmtAppend("\t/* %s */\n", BytecodeUtil.toString(insn).replace("\\", "\\\\").replace("*/", "*\\"));

            switch (insn) {
                case VarInsnNode varInsn -> VarUnit.process(varInsn, ctx, method);
                case IntInsnNode intInsn -> PushUnit.process(intInsn, ctx, tjvm, isCustomArray);
                case LdcInsnNode ldcInsn -> LdcUnit.process(ldcInsn, ctx, varMan, tjvm);
                case LabelNode label -> {
                    if (label != method.instructions.getLast()) { // label at end of compound statement only available with '-std=c++2b' or '-std=gnu++2b'
                        BlockUnit.process(label, frames, ctx);
                    }
                }
                case InsnNode iNode -> {
                    if (insn.getOpcode() >= ICONST_M1 && insn.getOpcode() <= ICONST_5 || insn.getOpcode() == FCONST_0 || insn.getOpcode() == FCONST_1 || insn.getOpcode() == FCONST_2 || insn.getOpcode() == DCONST_0 || insn.getOpcode() == DCONST_1 || insn.getOpcode() == LCONST_0 || insn.getOpcode() == LCONST_1) {
                        ImmediateUnit.process(iNode, ctx);
                    } else if (
                            insn.getOpcode() == IRETURN ||
                                    insn.getOpcode() == DRETURN ||
                                    insn.getOpcode() == FRETURN ||
                                    insn.getOpcode() == LRETURN ||
                                    insn.getOpcode() == ARETURN ||
                                    insn.getOpcode() == RETURN
                    ) {
                        ReturnUnit.process(ic, iNode, ctx, tjvm, isCustomArray);
                    } else if (Internal.isArithmetic(insn.getOpcode())) {
                        ArithmeticUnit.process(ic, iNode, ctx, tjvm, index, isCustomArray);
                    } else if (insn.getOpcode() == ACONST_NULL) {
                        ImmediateUnit.process(iNode, ctx);
                    } else if (Internal.isNumericConversion(insn.getOpcode())) {
                        CastUnit.process(iNode, ctx);
                    } else if (Internal.isArrayHandling(insn.getOpcode())) {
                        ArrayHandlerUnit.process(ic, iNode, ctx, tjvm);
                    } else if (insn.getOpcode() >= POP && insn.getOpcode() <= SWAP) {
                        StackUnit.process(iNode, frames, ctx);
                    } else if (insn.getOpcode() >= MONITORENTER && insn.getOpcode() <= MONITOREXIT) {
                        MonitorUnit.process(iNode, ctx, tjvm);
                    } else if (insn.getOpcode() == ATHROW) {
                        ThrowUnit.process(ic, iNode, ctx, tjvm);
                    } else if (insn.getOpcode() == NOP) {
                        CommentUnit.process("NOP", ctx);
                    }
                }
                case JumpInsnNode jump -> {
                    if (Internal.isJump(jump.getOpcode())) {
                        JumpUnit.process(jump, ctx, tjvm);
                    }
                }
                case MethodInsnNode call -> {
                    if (
                        call.getOpcode() == INVOKESTATIC ||
                        call.getOpcode() == INVOKEVIRTUAL ||
                        call.getOpcode() == INVOKESPECIAL ||
                        call.getOpcode() == INVOKEINTERFACE
                    ) {
                        InvocationUnit.process(ic, call, ctx, varMan, tjvm, config.getBoolean("mutators.jnt.intrinsic", true), isCustomArray);
                    }
                }
                case FieldInsnNode fieldInsn -> FieldUnit.process(ic, fieldInsn, ctx, tjvm, varMan, isCustomArray);
                case TypeInsnNode typeInsn -> TypeUnit.process(typeInsn, ctx, tjvm, isCustomArray);
                case LookupSwitchInsnNode lsin -> SwitchUnit.process(lsin, ctx);
                case TableSwitchInsnNode tsin -> SwitchUnit.process(tsin, ctx);
                case IincInsnNode iinc -> IincUnit.process(iinc, ctx, method);
                case LineNumberNode line -> CommentUnit.process(String.valueOf(line.line), ctx);
                case InvokeDynamicInsnNode indy -> IndyUnit.process(indy, varMan, ctx, tjvm);
                case FrameNode ignored -> {}
                default -> Logger.INSTANCE.log(Level.WARNING, Origin.CORE, String.format("Unhandled instruction %s in %s -> %s%s\n", BytecodeUtil.toString(insn), node.name, method.name, method.desc));
            }

            if (insn.getOpcode() != -1 && ctx.getBuilder().length() == prev) {
                ctx.fmtAppend("\t/* SKIP -> %s */\n", BytecodeUtil.toString(insn));
                Logger.INSTANCE.log(Level.WARNING, Origin.CORE, String.format("Skipped instruction %s in %s -> %s%s\n", BytecodeUtil.toString(insn), node.name, method.name, method.desc));
            } else if (exceptionSitePlan.needsPoll()) {
                ctx.fmtAppend(
                        "\tif (JNT_UNLIKELY(%s)) goto %s;\n",
                        JniDispatchCodegen.callExprLiteral(
                                tjvm,
                                JniBindingId.EXCEPTION_CHECK,
                                JniMarkerCodec.siteKey(ctx, insn, JniBindingId.EXCEPTION_CHECK, "poll"),
                                "(jboolean (*)(JNIEnv *))",
                                "env"
                        ),
                        exceptionSitePlan.handlerLabelOr("unwind")
                );
            }

            if (isCustomArray && insn.getOpcode() != -1) {
                PrimitiveArrayUnit.emitDeadStackSlotCleanup(ctx, insn);
            }
        }

        emitSharedExceptionDispatch(ctx, methodExceptionPlan, tjvm);

        StringBuilder jniLookups = new StringBuilder();
        UnitContext tempCtx = new UnitContext(jniLookups, tracker, node, method, false);
        InnerCache tempIc = new InnerCache(tempCtx, varMan, hasLimitCache);

        if (!hasLimitCache) {
            tempIc.ccMap.putAll(ic.ccMap);
            tempIc.cmMap.putAll(ic.cmMap);
            tempIc.vmMap.putAll(ic.vmMap);
            tempIc.cfMap.putAll(ic.cfMap);
            tempIc.vfMap.putAll(ic.vfMap);

            tempIc.generateJNILookups();
        }

        String commentPattern = "\t/* " + node.name + " -> " + method.name + method.desc + " */\n";
        String currentContent = builder.toString();
        int insertPos = currentContent.indexOf(commentPattern);
        if (insertPos != -1) {
            builder.insert(insertPos + commentPattern.length(), jniLookups);
        } else {
            String altPattern = "/* " + node.name + " -> " + method.name + method.desc + " */\n";
            insertPos = currentContent.indexOf(altPattern);
            if (insertPos != -1) {
                builder.insert(insertPos + altPattern.length(), jniLookups);
            }
        }

        ctx.fmtAppend("unwind:\n");
        if (isCustomArray) {
            if (returnType.getSort() != Type.VOID) {
                ctx.fmtAppend("\t%s = JNI_FALSE;\n", PrimitiveArrayUnit.returnFlagName());
            }
            ctx.fmtAppend("\tgoto %s;\n", PrimitiveArrayUnit.arrayEpilogueLabel());
            ctx.fmtAppend("%s:\n", PrimitiveArrayUnit.arrayEpilogueLabel());
            PrimitiveArrayUnit.emitParameterSyncBack(ctx, tjvm);
            PrimitiveArrayUnit.emitParameterRootRelease(ctx);
            PrimitiveArrayUnit.emitArrayScopeCleanup(ctx);
            PrimitiveArrayUnit.emitArrayScopeLeave(ctx);
            switch (returnType.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT, Type.ARRAY, Type.OBJECT, Type.DOUBLE,
                     Type.LONG, Type.FLOAT ->
                        ctx.fmtAppend(
                                "\treturn %s ? %s : %s;\n",
                                PrimitiveArrayUnit.returnFlagName(),
                                PrimitiveArrayUnit.returnValueName(),
                                PrimitiveArrayUnit.defaultReturnValue(returnType)
                        );
                default -> ctx.fmtAppend("\treturn;\n");
            }
        } else {
            switch (returnType.getSort()) {
                case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> ctx.fmtAppend("\treturn 0;\n");
                case Type.FLOAT -> ctx.fmtAppend("\treturn 0.0f;\n");
                case Type.LONG -> ctx.fmtAppend("\treturn 0L;\n");
                case Type.DOUBLE -> ctx.fmtAppend("\treturn 0.0;\n");
                case Type.ARRAY, Type.OBJECT -> ctx.fmtAppend("\treturn NULL;\n");
                default -> ctx.fmtAppend("\treturn;\n");
            }
        }

        tjvm.buildVM();

        String out = builder.toString();
        out = out.replace("/* MAX_STACK */", Internal.createStack(tracker.max() + 1));

        return out;
    }

    private static void initializeParameterLocals(InnerCache ic, UnitContext ctx, TempJumpVM tjvm, boolean isCustomArray) {
        MethodNode method = ctx.getMethodNode();
        int localIndex = 0;
        int nativeParamIndex = 0;
        int argIndex = 0;

        if (!Modifier.isStatic(method.access)) {
            ctx.fmtAppend("\tlocals[%d].l = _auto;\n", localIndex++);
        }

        for (Type argType : Type.getArgumentTypes(method.desc)) {
            String parameterName = "p" + nativeParamIndex;
            PrimitiveArrayKind kind = isCustomArray ? PrimitiveArrayKind.fromType(argType) : null;
            if (kind != null) {
                String argName = "arg_" + argIndex;
                Object oom = ic.FindClass("java/lang/OutOfMemoryError");
                ctx.fmtAppend("\tjobject %s = NULL;\n", argName);
                String previousHandler = ctx.handlerLabel;
                ctx.handlerLabel = "unwind";
                PrimitiveArrayUnit.emitJniToCustom(ctx, tjvm, parameterName, kind, oom, argName);
                ctx.handlerLabel = previousHandler;
                PrimitiveArrayUnit.emitRetainArrayPtr(ctx, argName);
                ctx.fmtAppend("\tlocals[%d].l = %s;\n", localIndex, argName);
            } else {
                String field = Internal.resolveType(argType);
                char typeChar = field.charAt(1);
                if (argType.getSort() == Type.OBJECT || argType.getSort() == Type.ARRAY) {
                    ctx.fmtAppend("\tlocals[%d].l = (jobject) %s;\n", localIndex, parameterName);
                } else {
                    ctx.fmtAppend(
                            "\tlocals[%d].%s = (%s) %s;\n",
                            localIndex,
                            typeChar,
                            Internal.mapTypeFromChar(typeChar),
                            parameterName
                    );
                }
            }
            localIndex += argType.getSize();
            nativeParamIndex += argType.getSize();
            argIndex++;
        }
    }

    private static void emitSharedExceptionDispatch(UnitContext ctx, MethodExceptionPlan plan, TempJumpVM tjvm) {
        for (ExceptionDispatchGroup dispatchGroup : plan.dispatchGroups()) {
            String exVar = "__jnt_ex_" + dispatchGroup.id();
            ctx.fmtAppend("%s:\n", dispatchGroup.dispatchLabel());
            ctx.fmtAppend(
                    "\tif (!%s) goto unwind;\n",
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.EXCEPTION_CHECK,
                            dispatchGroup.dispatchLabel() + "#check",
                            "(jboolean (*)(JNIEnv *))",
                            "env"
                    )
            );
            ctx.fmtAppend(
                    "\tjthrowable %s = %s;\n",
                    exVar,
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.EXCEPTION_OCCURRED,
                            dispatchGroup.dispatchLabel() + "#occurred",
                            "(jthrowable (*)(JNIEnv *))",
                            "env"
                    )
            );
            ctx.fmtAppend(
                    "\t%s = %s;\n",
                    exVar,
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.NEW_LOCAL_REF,
                            dispatchGroup.dispatchLabel() + "#localref",
                            "(jobject (*)(JNIEnv *, jobject))",
                            "env",
                            exVar
                    )
            );
            ctx.fmtAppend(
                    "\t%s;\n",
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.EXCEPTION_CLEAR,
                            dispatchGroup.dispatchLabel() + "#clear",
                            "(void (*)(JNIEnv *))",
                            "env"
                    )
            );

            for (TryCatchBlockNode tcb : dispatchGroup.catches()) {
                if (tcb.type == null || "*".equals(tcb.type)) {
                    ctx.fmtAppend("\tif (%s) {\n", exVar);
                } else if ("null".equals(tcb.type)) {
                    int idx = Cache.Companion.request_klass("java/lang/NullPointerException");
                    ctx.fmtAppend(
                            "\tif (%s) {\n",
                            JniDispatchCodegen.callExprLiteral(
                                    tjvm,
                                    JniBindingId.IS_INSTANCE_OF,
                                    dispatchGroup.dispatchLabel() + "#null#" + idx,
                                    "(jboolean (*)(JNIEnv *, jobject, jclass))",
                                    "env",
                                    exVar,
                                    "request_klass(env, " + idx + ")"
                            )
                    );
                } else {
                    int idx = Cache.Companion.request_klass(tcb.type);
                    ctx.fmtAppend(
                            "\tif (%s) {\n",
                            JniDispatchCodegen.callExprLiteral(
                                    tjvm,
                                    JniBindingId.IS_INSTANCE_OF,
                                    dispatchGroup.dispatchLabel() + "#catch#" + idx,
                                    "(jboolean (*)(JNIEnv *, jobject, jclass))",
                                    "env",
                                    exVar,
                                    "request_klass(env, " + idx + ")"
                            )
                    );
                }

                ctx.fmtAppend("\t\tstack[0].l = %s;\n", exVar);
                ctx.fmtAppend("\t\tgoto %s;\n", BlockUnit.resolveBlock(tcb.handler));
                ctx.fmtAppend("\t}\n");
            }

            ctx.fmtAppend(
                    "\t%s;\n",
                    JniDispatchCodegen.callExprLiteral(
                            tjvm,
                            JniBindingId.THROW,
                            dispatchGroup.dispatchLabel() + "#throw",
                            "(jint (*)(JNIEnv *, jthrowable))",
                            "env",
                            exVar
                    )
            );
            ctx.fmtAppend("\tgoto unwind;\n");
        }
    }
}
