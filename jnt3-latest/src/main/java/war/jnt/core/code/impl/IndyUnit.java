package war.jnt.core.code.impl;

import lombok.SneakyThrows;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import war.jnt.cache.Cache;
import war.jnt.cache.struct.CachedIndyArgs;
import war.jnt.core.Processor;
import war.jnt.core.code.UnitContext;
import war.jnt.core.code.bindings.JniBindingId;
import war.jnt.core.code.bindings.JniDispatchCodegen;
import war.jnt.core.header.Header;
import war.jnt.core.source.Source;
import war.jnt.core.vm.TempJumpVM;
import war.jnt.fusebox.impl.Internal;
import war.jnt.fusebox.impl.VariableManager;
import war.jnt.obfuscation.MutatedString;
import war.jnt.obfuscation.StringLookup;

import java.nio.file.Files;
import java.nio.file.Path;

public class IndyUnit implements Opcodes {

    private static final StringBuilder cacheInitialisationCode = new StringBuilder();

    public static void process(InvokeDynamicInsnNode node, VariableManager varMan, UnitContext ctx, TempJumpVM tjvm) {
        int oldSize = Cache.Companion.cachedIndyArgs();
        int argumentsIndex = Cache.Companion.request_indy_args(new CachedIndyArgs(node.bsmArgs));

        if (argumentsIndex == oldSize)
            cacheArgs(oldSize, node.bsmArgs, ctx);

        ClassNode classNode = ctx.getClassNode();

        int idx = Cache.Companion.request_klass(classNode.name);

        String var = varMan.newBuffer();
        String bmVar = varMan.newBuffer();
        String typeVar = varMan.newBuffer();
        String callerClassVar = varMan.newBuffer();
        String nameVar = varMan.newBuffer();

        MutatedString name = new MutatedString(node.name);

//        ctx.fmtAppend("\tprintf(\"Creating invoke dynamic site for %%s.%%s\\n\\n\", \"%s\", \"%s\");\n",
//                node.bsm.owner, node.bsm.name + node.bsm.descriptor);

        int caller_idx = Cache.Companion.request_klass(classNode.name);

        ctx.append(name.compute(nameVar, true));

        ctx.fmtAppend("\tjobject %s = " + makeHandleConstant(classNode.name, node.bsm) + ";\n", bmVar);

//        ctx.fmtAppend("\tprintf(\"Bootstrap method: %%p\\n\\n\", %s);\n", bmVar);

        ctx.fmtAppend("\tjobject %s = make_method_type(env, \"%s\", request_klass(env, %d));\n", typeVar, node.desc, caller_idx);

//        ctx.fmtAppend("\tprintf(\"Method type: %%p\\n\\n\", %s);\n", typeVar);

        ctx.fmtAppend("\tjclass %s = request_klass(env, %d);\n",
                callerClassVar, idx);

//        ctx.fmtAppend("\tprintf(\"Caller class: %%p\\n\\n\", %s);\n", callerClassVar);

        ctx.fmtAppend("\tjobject %s = make_site(env, %s, %s, %d, %s, %d, %s);\n",
                var, bmVar, nameVar, node.name.length(), typeVar, argumentsIndex, callerClassVar);

        ctx.fmtAppend("\t%s;\n", jni(tjvm,
                JniBindingId.DELETE_LOCAL_REF,
                "IndyUnit#process#bmVar#" + node.name,
                "(void (*)(JNIEnv *, jobject))",
                "env",
                bmVar
        ));
        ctx.fmtAppend("\t%s;\n", jni(tjvm,
                JniBindingId.DELETE_LOCAL_REF,
                "IndyUnit#process#typeVar#" + node.name,
                "(void (*)(JNIEnv *, jobject))",
                "env",
                typeVar
        ));

//        ctx.fmtAppend("\tprintf(\"Created invoke dynamic site: %%p\\n\", %s);\n", var);

        Type[] args = Type.getArgumentTypes(node.desc);
        Type returnType = Type.getReturnType(node.desc);
//        int argc = args.length;

        int Object_idx = Cache.Companion.request_klass("java/lang/Object");

        ctx.fmtAppend("\t{\n");
        ctx.fmtAppend("\t\tjclass kls_obj = request_klass(env, %d);\n", Object_idx);
        ctx.fmtAppend(
                "\t\tjobjectArray mh_args = %s;\n",
                jni(tjvm,
                        JniBindingId.NEW_OBJECT_ARRAY,
                        "IndyUnit#process#mhArgs#" + node.name,
                        "(jobjectArray (*)(JNIEnv *, jsize, jclass, jobject))",
                        "env",
                        Integer.toString(args.length),
                        "kls_obj",
                        "NULL"
                )
        );

//        ctx.fmtAppend("\t\tprintf(\"Allocating %d slots for invoke dynamic arguments\\n\");\n", args.length);

        for (int i = args.length - 1; i >= 0; i--) {

            String slot = Internal.computePop(ctx.tracker);
            Type t = args[i];

            switch (t.getSort()) {
                case Type.INT -> ctx.fmtAppend("\t{ jobject tmp = make_object_int(env, %s.i); "
                        + "%s; "
                        + "%s; }\n", slot,
                        jni(tjvm, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "IndyUnit#process#set-int#" + i, "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", "mh_args", Integer.toString(i), "tmp"),
                        jni(tjvm, JniBindingId.DELETE_LOCAL_REF, "IndyUnit#process#del-int#" + i, "(void (*)(JNIEnv *, jobject))", "env", "tmp"));
                case Type.LONG -> ctx.fmtAppend("\t{ jobject tmp = make_object_long(env, %s.j); "
                        + "%s; "
                        + "%s; }\n", slot,
                        jni(tjvm, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "IndyUnit#process#set-long#" + i, "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", "mh_args", Integer.toString(i), "tmp"),
                        jni(tjvm, JniBindingId.DELETE_LOCAL_REF, "IndyUnit#process#del-long#" + i, "(void (*)(JNIEnv *, jobject))", "env", "tmp"));
                case Type.FLOAT -> ctx.fmtAppend("\t{ jobject tmp = make_object_float(env, %s.f); "
                        + "%s; "
                        + "%s; }\n", slot,
                        jni(tjvm, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "IndyUnit#process#set-float#" + i, "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", "mh_args", Integer.toString(i), "tmp"),
                        jni(tjvm, JniBindingId.DELETE_LOCAL_REF, "IndyUnit#process#del-float#" + i, "(void (*)(JNIEnv *, jobject))", "env", "tmp"));
                case Type.DOUBLE -> ctx.fmtAppend("\t{ jobject tmp = make_object_double(env, %s.d); "
                        + "%s; "
                        + "%s; }\n", slot,
                        jni(tjvm, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "IndyUnit#process#set-double#" + i, "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", "mh_args", Integer.toString(i), "tmp"),
                        jni(tjvm, JniBindingId.DELETE_LOCAL_REF, "IndyUnit#process#del-double#" + i, "(void (*)(JNIEnv *, jobject))", "env", "tmp"));
                case Type.BOOLEAN -> ctx.fmtAppend("\t{ jobject tmp = make_object_boolean(env, %s.z); "
                        + "%s; "
                        + "%s; }\n", slot,
                        jni(tjvm, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "IndyUnit#process#set-bool#" + i, "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", "mh_args", Integer.toString(i), "tmp"),
                        jni(tjvm, JniBindingId.DELETE_LOCAL_REF, "IndyUnit#process#del-bool#" + i, "(void (*)(JNIEnv *, jobject))", "env", "tmp"));
                case Type.CHAR -> ctx.fmtAppend("\t{ jobject tmp = make_object_char(env, %s.c); "
                        + "%s; "
                        + "%s; }\n", slot,
                        jni(tjvm, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "IndyUnit#process#set-char#" + i, "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", "mh_args", Integer.toString(i), "tmp"),
                        jni(tjvm, JniBindingId.DELETE_LOCAL_REF, "IndyUnit#process#del-char#" + i, "(void (*)(JNIEnv *, jobject))", "env", "tmp"));
                case Type.SHORT -> ctx.fmtAppend("\t{ jobject tmp = make_object_short(env, %s.s); "
                        + "%s; "
                        + "%s; }\n", slot,
                        jni(tjvm, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "IndyUnit#process#set-short#" + i, "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", "mh_args", Integer.toString(i), "tmp"),
                        jni(tjvm, JniBindingId.DELETE_LOCAL_REF, "IndyUnit#process#del-short#" + i, "(void (*)(JNIEnv *, jobject))", "env", "tmp"));
                case Type.BYTE -> ctx.fmtAppend("\t{ jobject tmp = make_object_byte(env, %s.b); "
                        + "%s; "
                        + "%s; }\n", slot,
                        jni(tjvm, JniBindingId.SET_OBJECT_ARRAY_ELEMENT, "IndyUnit#process#set-byte#" + i, "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))", "env", "mh_args", Integer.toString(i), "tmp"),
                        jni(tjvm, JniBindingId.DELETE_LOCAL_REF, "IndyUnit#process#del-byte#" + i, "(void (*)(JNIEnv *, jobject))", "env", "tmp"));
                default -> ctx.fmtAppend("\t%s;\n",
                        jni(tjvm,
                                JniBindingId.SET_OBJECT_ARRAY_ELEMENT,
                                "IndyUnit#process#set-obj#" + i,
                                "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))",
                                "env",
                                "mh_args",
                                Integer.toString(i),
                                slot + ".l"
                        ));
            }
        }

//        ctx.fmtAppend("\t\t(*env)->DeleteLocalRef(env, kls_obj);\n");

//        ctx.fmtAppend("\t\tprintf(\"Invoke dynamic arguments cached: %%p\\n\", mh_args);\n");

        ctx.fmtAppend("\t\tjobject mh_result = invoke_with_arguments(env, %s, mh_args);\n", var);

//        ctx.fmtAppend("\t\tprintf(\"Invoke dynamic result: %%p\\n\", mh_result);\n");

        switch (returnType.getSort()) {
            case Type.VOID -> {}
            case Type.BOOLEAN -> ctx.fmtAppend(
                    "\t\t%s%s = unbox_boolean(env, mh_result);\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
            case Type.BYTE -> ctx.fmtAppend(
                    "\t\t%s%s = unbox_byte(env, mh_result);\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
            case Type.CHAR -> ctx.fmtAppend(
                    "\t\t%s%s = unbox_char(env, mh_result);\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
            case Type.SHORT -> ctx.fmtAppend(
                    "\t\t%s%s = unbox_short(env, mh_result);\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
            case Type.INT -> ctx.fmtAppend(
                    "\t\t%s%s = unbox_int(env, mh_result);\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
            case Type.LONG -> ctx.fmtAppend(
                    "\t\t%s%s = unbox_long(env, mh_result);\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
            case Type.FLOAT -> ctx.fmtAppend(
                    "\t\t%s%s = unbox_float(env, mh_result);\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
            case Type.DOUBLE -> ctx.fmtAppend(
                    "\t\t%s%s = unbox_double(env, mh_result);\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
            case Type.OBJECT, Type.ARRAY -> ctx.fmtAppend(
                    "\t\t%s%s = mh_result;\n",
                    Internal.computePush(ctx.tracker), Internal.resolveType(returnType)
            );
        }
        ctx.fmtAppend("\t\t%s;\n", jni(tjvm,
                JniBindingId.DELETE_LOCAL_REF,
                "IndyUnit#process#delete-mhargs#" + node.name,
                "(void (*)(JNIEnv *, jobject))",
                "env",
                "mh_args"
        ));
        ctx.fmtAppend("\t}\n");

    }

    private static String jni(TempJumpVM tjvm, JniBindingId bindingId, String siteKey, String functionPointerType, String... args) {
        return JniDispatchCodegen.callExprLiteral(tjvm, bindingId, siteKey, functionPointerType, args);
    }

    private static void cacheArgs(int index, Object[] bsmArgs, UnitContext ctx) {
        StringBuilder init = new StringBuilder();

//        init.append(String.format("\tprintf(\"Caching invoke dynamic arguments %d\\n\");\n", index));

        init.append("\tjobject args").append(index)
                .append("[").append(bsmArgs.length).append("];\n");

        for (int i = 0; i < bsmArgs.length; i++) {
            String slot = "args" + index + "[" + i + "]";
            Object value = bsmArgs[i];

//            init.append(String.format("\tprintf(\"Slot %d: %s (%s)\\n\");\n", i, value instanceof String ? "..." : value, value == null ? "null" : value.getClass().getSimpleName()));

            switch (value) {
                case Integer v -> appendGlobalizedTemp(init, index, i, slot, "jobject tmp = make_object_int(env, " + v + ");");

                case Byte v -> appendGlobalizedTemp(init, index, i, slot, "jobject tmp = make_object_byte(env, " + v.intValue() + ");");

                case Character v -> appendGlobalizedTemp(init, index, i, slot, "jobject tmp = make_object_char(env, " + (int) v + ");");

                case Short v -> appendGlobalizedTemp(init, index, i, slot, "jobject tmp = make_object_short(env, " + v.intValue() + ");");

                case Boolean v -> appendGlobalizedTemp(init, index, i, slot, "jobject tmp = make_object_boolean(env, " + (v ? 1 : 0) + ");");

                case Float v -> appendGlobalizedTemp(init, index, i, slot, "jobject tmp = make_object_float(env, " + v + "f);");

                case Long v -> appendGlobalizedTemp(init, index, i, slot, "jobject tmp = make_object_long(env, " + v + "L);");

                case Double v -> appendGlobalizedTemp(init, index, i, slot, "jobject tmp = make_object_double(env, " + v + ");");

                case String s -> appendGlobalizedTemp(
                        init,
                        index,
                        i,
                        slot,
                        "jstring tmp = " + JniDispatchCodegen.callExprLiteral(
                                JniBindingId.NEW_STRING,
                                "IndyUnit#cacheArgs#newString#" + index + "#" + i,
                                "(jstring (*)(JNIEnv *, const jchar *, jsize))",
                                "env",
                                StringLookup.toUTF16(s),
                                Integer.toString(s.length())
                        ) + ";"
                );

                case Type type -> {
                    int sort = type.getSort();
                    if (sort == Type.OBJECT) {
                        int k = Cache.Companion.request_klass(type.getInternalName());
                        init.append("\t").append(slot).append(" = request_klass(env, ").append(k).append(");\n");
                    } else if (sort == Type.METHOD) {
                        appendGlobalizedTemp(
                                init,
                                index,
                                i,
                                slot,
                                "jobject tmp = make_method_type(env, \"" + type.getDescriptor() + "\", request_klass(env, " +
                                        Cache.Companion.request_klass(ctx.getClassNode().name) + "));"
                        );
                    } else if (sort == Type.ARRAY) {
                        int k = Cache.Companion.request_klass(type.getDescriptor());
                        init.append("\t").append(slot).append(" = request_klass(env, ").append(k).append(");\n");
                    } else {
                        String prim = type.getClassName();
                        int k = Cache.Companion.request_klass(prim);
                        init.append("\t").append(slot).append(" = request_klass(env, ").append(k).append(");\n");
                    }
                }

                case Handle h -> appendGlobalizedTemp(
                        init,
                        index,
                        i,
                        slot,
                        "jobject tmp = " + makeHandleConstant(ctx.getClassNode().name, h) + ";"
                );

                case null, default -> throw new IllegalArgumentException("Unsupported value: " + value);
            }

//            init.append(String.format("\tprintf(\"Slot %d cached: %s: %%p\\n\", %s);\n", i, slot, slot));
        }

        int Object_idx = Cache.Companion.request_klass("java/lang/Object");
        init.append("\t{\n")
                .append("\t\tjclass kls_obj = request_klass(env, " + Object_idx + ");\n")
//                .append("\t\tjclass kls_obj = (*env)->FindClass(env, \"java/lang/Object\");\n")
                .append("\t\tjobjectArray arr").append(index)
                .append(" = ").append(JniDispatchCodegen.callExprLiteral(
                        JniBindingId.NEW_OBJECT_ARRAY,
                        "IndyUnit#cacheArgs#newObjectArray#" + index,
                        "(jobjectArray (*)(JNIEnv *, jsize, jclass, jobject))",
                        "env",
                        Integer.toString(bsmArgs.length),
                        "kls_obj",
                        "NULL"
                )).append(";\n")
                .append("\t\tif (arr").append(index).append(") {\n");

        for (int i = 0; i < bsmArgs.length; i++) {
            init.append("\t\t\t")
                    .append(JniDispatchCodegen.callExprLiteral(
                            JniBindingId.SET_OBJECT_ARRAY_ELEMENT,
                            "IndyUnit#cacheArgs#setObjectArray#" + index + "#" + i,
                            "(void (*)(JNIEnv *, jobjectArray, jsize, jobject))",
                            "env",
                            "arr" + index,
                            Integer.toString(i),
                            "args" + index + "[" + i + "]"
                    ))
                    .append(";\n");
        }

        init.append("\t\t\tstatic_arguments[").append(index).append("] = (jobjectArray)")
                .append(JniDispatchCodegen.callExprLiteral(
                        JniBindingId.NEW_GLOBAL_REF,
                        "IndyUnit#cacheArgs#globalArray#" + index,
                        "(jobject (*)(JNIEnv *, jobject))",
                        "env",
                        "arr" + index
                )).append(";\n")
                .append("\t\t\t").append(JniDispatchCodegen.callExprLiteral(
                        JniBindingId.DELETE_LOCAL_REF,
                        "IndyUnit#cacheArgs#deleteArray#" + index,
                        "(void (*)(JNIEnv *, jobject))",
                        "env",
                        "arr" + index
                )).append(";\n")
                .append("\t\t}\n")
                .append("\t}\n");

        cacheInitialisationCode.append(init);
    }

    private static void appendGlobalizedTemp(StringBuilder init, int cacheIndex, int slotIndex, String slot, String tempDeclaration) {
        init.append("\t{ ").append(tempDeclaration).append('\n')
                .append("\t").append(slot).append(" = ").append(JniDispatchCodegen.callExprLiteral(
                        JniBindingId.NEW_GLOBAL_REF,
                        "IndyUnit#cacheArgs#global#" + cacheIndex + "#" + slotIndex,
                        "(jobject (*)(JNIEnv *, jobject))",
                        "env",
                        "tmp"
                )).append(";\n")
                .append("\t").append(JniDispatchCodegen.callExprLiteral(
                        JniBindingId.DELETE_LOCAL_REF,
                        "IndyUnit#cacheArgs#delete#" + cacheIndex + "#" + slotIndex,
                        "(void (*)(JNIEnv *, jobject))",
                        "env",
                        "tmp"
                )).append("; }\n");
    }

    public static String makeHandleConstant(String callerClass, Handle h) {

        String caller = String.format("request_klass(env, %d)", Cache.Companion.request_klass(callerClass));

        StringBuilder result = new StringBuilder();
        switch (h.getTag()) {
            case H_GETFIELD, H_GETSTATIC, H_PUTFIELD, H_PUTSTATIC -> {
                String request;

                Type type = Type.getType(h.getDesc());
                if (type.getSort() == Type.METHOD) {
                    type = type.getReturnType();
                }

                if (type.getSort() == Type.OBJECT) {
                    int idx = Cache.Companion.request_klass(type.getInternalName());
                    request = "request_klass(env, " + idx + ")";
                } else if (type.getSort() == Type.ARRAY) {
                    int idx = Cache.Companion.request_klass(type.getDescriptor());
                    request = "request_klass(env, " + idx + ")";
                } else {
                    int idx = Cache.Companion.request_klass(type.getClassName());
                    request = "request_klass(env, " + idx + ")";
                }

                result.append(String.format("make_handle(env, %d, \"%s\", \"%s\", \"%s\", %s, %s)",
                        h.getTag(), h.getOwner(), h.getName(), h.getDesc(), request, caller));
            }
            case H_INVOKEVIRTUAL, H_INVOKEINTERFACE, H_INVOKESTATIC, H_NEWINVOKESPECIAL, H_INVOKESPECIAL -> {


                result.append(String.format("make_handle(env, %d, \"%s\", \"%s\", \"%s\", 0, %s)",
                        h.getTag(), h.getOwner(), h.getName(), h.getDesc(), caller));

            }
            default -> throw new IllegalArgumentException("Unsupported handle tag: " + h.getTag());
        }
        return result.toString();
    }


    @SneakyThrows
    public static void make(Processor processor) {
        String output = String.format("""
                void ensure_indy_cache(JNIEnv* env) {
                    if (static_arguments) return;
                    static_arguments = malloc(sizeof(jobjectArray) * %d);
                %s
                }
                """, Cache.Companion.cachedIndyArgs(), cacheInitialisationCode);
        processor.getSources().add(new Source("lib/invokedynamic.c", Files.readString(Path.of("helpers/invokedynamic.c"))
                .replace("__CACHE__", output)));
        processor.getHeaders().add(new Header("lib/invokedynamic.h", Files.readString(Path.of("helpers/invokedynamic.h"))));
        processor.getHeaders().add(new Header("lib/boxing.h", Files.readString(Path.of("helpers/boxing.h"))));
        processor.getSources().add(new Source("lib/boxing.c", Files.readString(Path.of("helpers/boxing.c"))));
        cacheInitialisationCode.setLength(0);
    }

}
