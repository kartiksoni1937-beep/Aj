#include "invokedynamic.h"

#if JNT_JNI_PROTECTION_ENABLED
#define INDY_MARKER(binding, salt) JNT_MAKE_MARKER((binding), UINT64_C(salt))
#define INDY_FIND_CLASS(salt, name) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_FIND_CLASS, salt), (jclass (*)(JNIEnv *, const char *)), env, name)
#define INDY_NEW_GLOBAL_REF(salt, obj) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_NEW_GLOBAL_REF, salt), (jobject (*)(JNIEnv *, jobject)), env, obj)
#define INDY_DELETE_LOCAL_REF(salt, obj) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_DELETE_LOCAL_REF, salt), (void (*)(JNIEnv *, jobject)), env, obj)
#define INDY_GET_METHOD_ID(salt, cls, name, desc) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_GET_METHOD_ID, salt), (jmethodID (*)(JNIEnv *, jclass, const char *, const char *)), env, cls, name, desc)
#define INDY_GET_STATIC_METHOD_ID(salt, cls, name, desc) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_GET_STATIC_METHOD_ID, salt), (jmethodID (*)(JNIEnv *, jclass, const char *, const char *)), env, cls, name, desc)
#define INDY_NEW_STRING_UTF(salt, text) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_NEW_STRING_UTF, salt), (jstring (*)(JNIEnv *, const char *)), env, text)
#define INDY_NEW_STRING(salt, chars, len) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_NEW_STRING, salt), (jstring (*)(JNIEnv *, const jchar *, jsize)), env, chars, len)
#define INDY_EXCEPTION_CHECK(salt) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_EXCEPTION_CHECK, salt), (jboolean (*)(JNIEnv *)), env)
#define INDY_EXCEPTION_DESCRIBE(salt) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_EXCEPTION_DESCRIBE, salt), (void (*)(JNIEnv *)), env)
#define INDY_GET_OBJECT_CLASS(salt, obj) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_GET_OBJECT_CLASS, salt), (jclass (*)(JNIEnv *, jobject)), env, obj)
#define INDY_CALL_OBJECT0(salt, obj, mid) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_CALL_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#define INDY_CALL_OBJECT1(salt, obj, mid, a0) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_CALL_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid, a0)
#define INDY_CALL_OBJECT2(salt, obj, mid, a0, a1) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_CALL_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid, a0, a1)
#define INDY_CALL_OBJECT3(salt, obj, mid, a0, a1, a2) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_CALL_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid, a0, a1, a2)
#define INDY_CALL_OBJECT4(salt, obj, mid, a0, a1, a2, a3) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_CALL_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid, a0, a1, a2, a3)
#define INDY_CALL_STATIC_OBJECT0(salt, cls, mid) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_CALL_STATIC_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jclass, jmethodID, ...)), env, cls, mid)
#define INDY_CALL_STATIC_OBJECT2(salt, cls, mid, a0, a1) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_CALL_STATIC_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jclass, jmethodID, ...)), env, cls, mid, a0, a1)
#define INDY_CALL_STATIC_OBJECT5(salt, cls, mid, a0, a1, a2, a3, a4) JNT_JNI_CALL(env, INDY_MARKER(JNT_BINDING_CALL_STATIC_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jclass, jmethodID, ...)), env, cls, mid, a0, a1, a2, a3, a4)
#else
#define INDY_FIND_CLASS(salt, name) ((*env)->FindClass(env, name))
#define INDY_NEW_GLOBAL_REF(salt, obj) ((*env)->NewGlobalRef(env, obj))
#define INDY_DELETE_LOCAL_REF(salt, obj) ((*env)->DeleteLocalRef(env, obj))
#define INDY_GET_METHOD_ID(salt, cls, name, desc) ((*env)->GetMethodID(env, cls, name, desc))
#define INDY_GET_STATIC_METHOD_ID(salt, cls, name, desc) ((*env)->GetStaticMethodID(env, cls, name, desc))
#define INDY_NEW_STRING_UTF(salt, text) ((*env)->NewStringUTF(env, text))
#define INDY_NEW_STRING(salt, chars, len) ((*env)->NewString(env, chars, len))
#define INDY_EXCEPTION_CHECK(salt) ((*env)->ExceptionCheck(env))
#define INDY_EXCEPTION_DESCRIBE(salt) ((*env)->ExceptionDescribe(env))
#define INDY_GET_OBJECT_CLASS(salt, obj) ((*env)->GetObjectClass(env, obj))
#define INDY_CALL_OBJECT0(salt, obj, mid) ((*env)->CallObjectMethod(env, obj, mid))
#define INDY_CALL_OBJECT1(salt, obj, mid, a0) ((*env)->CallObjectMethod(env, obj, mid, a0))
#define INDY_CALL_OBJECT2(salt, obj, mid, a0, a1) ((*env)->CallObjectMethod(env, obj, mid, a0, a1))
#define INDY_CALL_OBJECT3(salt, obj, mid, a0, a1, a2) ((*env)->CallObjectMethod(env, obj, mid, a0, a1, a2))
#define INDY_CALL_OBJECT4(salt, obj, mid, a0, a1, a2, a3) ((*env)->CallObjectMethod(env, obj, mid, a0, a1, a2, a3))
#define INDY_CALL_STATIC_OBJECT0(salt, cls, mid) ((*env)->CallStaticObjectMethod(env, cls, mid))
#define INDY_CALL_STATIC_OBJECT2(salt, cls, mid, a0, a1) ((*env)->CallStaticObjectMethod(env, cls, mid, a0, a1))
#define INDY_CALL_STATIC_OBJECT5(salt, cls, mid, a0, a1, a2, a3, a4) ((*env)->CallStaticObjectMethod(env, cls, mid, a0, a1, a2, a3, a4))
#endif

static jobjectArray* static_arguments;
static jclass kls_Class_glob;
static jmethodID mid_getCL_glob;
static jclass kls_MethodHandles_glob;
static jclass kls_Lookup_glob;
static jmethodID mid_lookup_glob;
static jmethodID mid_privateLookupIn_glob;
static jmethodID mid_findStatic_glob;
static jmethodID mid_findVirtual_glob;
static jmethodID mid_findSpecial_glob;
static jmethodID mid_findConstructor_glob;
static jmethodID mid_findGetter_glob;
static jmethodID mid_findSetter_glob;
static jmethodID mid_findStaticGetter_glob;
static jmethodID mid_findStaticSetter_glob;
static jclass kls_CallSite_glob;
static jmethodID mid_makeSite_glob;
static jmethodID mid_dynamicInvoker_glob;

static jobject get_class_loader(JNIEnv* env, jclass cls) {
    if (!kls_Class_glob) {
        jclass t = INDY_FIND_CLASS(0x1001, "java/lang/Class");
        if (!t) return 0;
        kls_Class_glob = (jclass) INDY_NEW_GLOBAL_REF(0x1002, t);
        INDY_DELETE_LOCAL_REF(0x1003, t);
        mid_getCL_glob = INDY_GET_METHOD_ID(0x1004, kls_Class_glob, "getClassLoader", "()Ljava/lang/ClassLoader;");
        if (!mid_getCL_glob) return 0;
    }
    return INDY_CALL_OBJECT0(0x1005, cls, mid_getCL_glob);
}

jobject make_method_type(JNIEnv* env, const char* desc, jclass caller) {
    jclass kls_MethodType = INDY_FIND_CLASS(0x1101, "java/lang/invoke/MethodType");
    if (!kls_MethodType) return 0;
    jmethodID mid_fromDesc = INDY_GET_STATIC_METHOD_ID(0x1102, kls_MethodType, "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;");
    if (!mid_fromDesc) { INDY_DELETE_LOCAL_REF(0x1103, kls_MethodType); return 0; }
    jstring j_desc = INDY_NEW_STRING_UTF(0x1104, desc);
    if (!j_desc) { INDY_DELETE_LOCAL_REF(0x1105, kls_MethodType); return 0; }
    jobject cl = get_class_loader(env, caller);
    jobject mt = INDY_CALL_STATIC_OBJECT2(0x1106, kls_MethodType, mid_fromDesc, j_desc, cl);
    if (cl) INDY_DELETE_LOCAL_REF(0x1107, cl);
    INDY_DELETE_LOCAL_REF(0x1108, j_desc);
    INDY_DELETE_LOCAL_REF(0x1109, kls_MethodType);
    if (INDY_EXCEPTION_CHECK(0x110A)) { INDY_EXCEPTION_DESCRIBE(0x110B); return 0; }
    return mt;
}

static int ensure_methodhandles(JNIEnv* env) {
    if (kls_MethodHandles_glob) return 1;
    jclass t = INDY_FIND_CLASS(0x1201, "java/lang/invoke/MethodHandles");
    if (!t) return 0;
    kls_MethodHandles_glob = (jclass) INDY_NEW_GLOBAL_REF(0x1202, t);
    INDY_DELETE_LOCAL_REF(0x1203, t);
    t = INDY_FIND_CLASS(0x1204, "java/lang/invoke/MethodHandles$Lookup");
    if (!t) return 0;
    kls_Lookup_glob = (jclass) INDY_NEW_GLOBAL_REF(0x1205, t);
    INDY_DELETE_LOCAL_REF(0x1206, t);
    mid_lookup_glob = INDY_GET_STATIC_METHOD_ID(0x1207, kls_MethodHandles_glob, "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
    mid_privateLookupIn_glob = INDY_GET_STATIC_METHOD_ID(0x1208, kls_MethodHandles_glob, "privateLookupIn", "(Ljava/lang/Class;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/MethodHandles$Lookup;");
    mid_findStatic_glob = INDY_GET_METHOD_ID(0x1209, kls_Lookup_glob, "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
    mid_findVirtual_glob = INDY_GET_METHOD_ID(0x120A, kls_Lookup_glob, "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
    mid_findSpecial_glob = INDY_GET_METHOD_ID(0x120B, kls_Lookup_glob, "findSpecial", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    mid_findConstructor_glob = INDY_GET_METHOD_ID(0x120C, kls_Lookup_glob, "findConstructor", "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
    mid_findGetter_glob = INDY_GET_METHOD_ID(0x120D, kls_Lookup_glob, "findGetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    mid_findSetter_glob = INDY_GET_METHOD_ID(0x120E, kls_Lookup_glob, "findSetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    mid_findStaticGetter_glob = INDY_GET_METHOD_ID(0x120F, kls_Lookup_glob, "findStaticGetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    mid_findStaticSetter_glob = INDY_GET_METHOD_ID(0x1210, kls_Lookup_glob, "findStaticSetter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    if (!mid_lookup_glob || !mid_privateLookupIn_glob || !mid_findStatic_glob || !mid_findVirtual_glob || !mid_findSpecial_glob || !mid_findConstructor_glob || !mid_findGetter_glob || !mid_findSetter_glob || !mid_findStaticGetter_glob || !mid_findStaticSetter_glob) return 0;
    return 1;
}

jobject make_handle(JNIEnv* env, jint tag, const char* owner, const char* name, const char* desc, jclass field_type, jclass caller) {
    if (INDY_EXCEPTION_CHECK(0x1301)) INDY_EXCEPTION_DESCRIBE(0x1302);
    if (!ensure_methodhandles(env)) return 0;
    jclass ownerCls = INDY_FIND_CLASS(0x1303, owner);
    if (!ownerCls) return 0;
    jobject lookup = INDY_CALL_STATIC_OBJECT0(0x1304, kls_MethodHandles_glob, mid_lookup_glob);
    if (INDY_EXCEPTION_CHECK(0x1305) || !lookup) {
        INDY_DELETE_LOCAL_REF(0x1306, ownerCls);
        if (INDY_EXCEPTION_CHECK(0x1307)) INDY_EXCEPTION_DESCRIBE(0x1308);
        return 0;
    }

    jobject private_lookup = INDY_CALL_STATIC_OBJECT2(0x1309, kls_MethodHandles_glob, mid_privateLookupIn_glob, caller, lookup);
    if (INDY_EXCEPTION_CHECK(0x130A) || !private_lookup) {
        INDY_DELETE_LOCAL_REF(0x130B, ownerCls);
        INDY_DELETE_LOCAL_REF(0x130C, lookup);
        if (INDY_EXCEPTION_CHECK(0x130D)) INDY_EXCEPTION_DESCRIBE(0x130E);
        return 0;
    }
    INDY_DELETE_LOCAL_REF(0x130F, lookup);
    lookup = private_lookup;

    jobject mh = 0;

    if (tag >= 1 && tag <= 4) {
        if (!field_type) {
            INDY_DELETE_LOCAL_REF(0x1310, ownerCls);
            INDY_DELETE_LOCAL_REF(0x1311, lookup);
            return 0;
        }
        jstring jName = INDY_NEW_STRING_UTF(0x1312, name);
        if (!jName) {
            INDY_DELETE_LOCAL_REF(0x1313, ownerCls);
            INDY_DELETE_LOCAL_REF(0x1314, lookup);
            return 0;
        }
        if (tag == 1 || tag == 3) {
            mh = INDY_CALL_OBJECT3(0x1315, lookup, (tag == 1 ? mid_findGetter_glob : mid_findSetter_glob), ownerCls, jName, field_type);
        } else {
            mh = INDY_CALL_OBJECT3(0x1316, lookup, (tag == 2 ? mid_findStaticGetter_glob : mid_findStaticSetter_glob), ownerCls, jName, field_type);
        }
        INDY_DELETE_LOCAL_REF(0x1317, jName);
    } else {
        jobject mt = make_method_type(env, desc, caller);
        if (!mt) {
            INDY_DELETE_LOCAL_REF(0x1318, ownerCls);
            INDY_DELETE_LOCAL_REF(0x1319, lookup);
            return 0;
        }
        if (tag == 8) {
            mh = INDY_CALL_OBJECT2(0x131A, lookup, mid_findConstructor_glob, ownerCls, mt);
        } else {
            jstring jName = INDY_NEW_STRING_UTF(0x131B, name);
            if (!jName) {
                INDY_DELETE_LOCAL_REF(0x131C, mt);
                INDY_DELETE_LOCAL_REF(0x131D, ownerCls);
                INDY_DELETE_LOCAL_REF(0x131E, lookup);
                return 0;
            }
            if (tag == 6) {
                mh = INDY_CALL_OBJECT3(0x131F, lookup, mid_findStatic_glob, ownerCls, jName, mt);
            } else if (tag == 5 || tag == 9) {
                mh = INDY_CALL_OBJECT3(0x1320, lookup, mid_findVirtual_glob, ownerCls, jName, mt);
            } else if (tag == 7) {
                jclass sc = caller ? caller : ownerCls;
                mh = INDY_CALL_OBJECT4(0x1321, lookup, mid_findSpecial_glob, ownerCls, jName, mt, sc);
            }
            INDY_DELETE_LOCAL_REF(0x1322, jName);
        }
        INDY_DELETE_LOCAL_REF(0x1323, mt);
    }

    INDY_DELETE_LOCAL_REF(0x1324, ownerCls);
    INDY_DELETE_LOCAL_REF(0x1325, lookup);

    if (INDY_EXCEPTION_CHECK(0x1326)) { INDY_EXCEPTION_DESCRIBE(0x1327); return 0; }
    return mh;
}

jobject make_site(JNIEnv* env, jobject bootstrap_method, jchar* name, jint name_length, jobject method_type, jint index, jclass caller_class) {
    if (!kls_CallSite_glob) {
        jclass t = INDY_FIND_CLASS(0x1401, "java/lang/invoke/CallSite");
        if (!t) return 0;
        kls_CallSite_glob = (jclass) INDY_NEW_GLOBAL_REF(0x1402, t);
        INDY_DELETE_LOCAL_REF(0x1403, t);
        mid_makeSite_glob = INDY_GET_STATIC_METHOD_ID(0x1404, kls_CallSite_glob, "makeSite", "(Ljava/lang/invoke/MethodHandle;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/invoke/CallSite;");
        if (!mid_makeSite_glob) return 0;
        mid_dynamicInvoker_glob = INDY_GET_METHOD_ID(0x1405, kls_CallSite_glob, "dynamicInvoker", "()Ljava/lang/invoke/MethodHandle;");
        if (!mid_dynamicInvoker_glob) return 0;
    }
    jstring j_name = INDY_NEW_STRING(0x1406, name, name_length);
    if (!j_name) return 0;
    jobjectArray args = static_arguments ? static_arguments[index] : 0;
    jobject cs = INDY_CALL_STATIC_OBJECT5(0x1407, kls_CallSite_glob, mid_makeSite_glob, bootstrap_method, j_name, method_type, args, caller_class);
    INDY_DELETE_LOCAL_REF(0x1408, j_name);
    if (INDY_EXCEPTION_CHECK(0x1409) || !cs) {
        if (INDY_EXCEPTION_CHECK(0x140A)) INDY_EXCEPTION_DESCRIBE(0x140B);
        return 0;
    }
    jobject mh = INDY_CALL_OBJECT0(0x140C, cs, mid_dynamicInvoker_glob);
    INDY_DELETE_LOCAL_REF(0x140D, cs);
    if (INDY_EXCEPTION_CHECK(0x140E)) { INDY_EXCEPTION_DESCRIBE(0x140F); return 0; }
    return mh;
}

jobject invoke_with_arguments(JNIEnv* env, jobject mh, jobjectArray args) {
    jclass mh_cls = INDY_GET_OBJECT_CLASS(0x1501, mh);
    if (!mh_cls) return 0;
    jmethodID mid = INDY_GET_METHOD_ID(0x1502, mh_cls, "invokeWithArguments", "([Ljava/lang/Object;)Ljava/lang/Object;");
    jobject r = INDY_CALL_OBJECT1(0x1503, mh, mid, args);
    INDY_DELETE_LOCAL_REF(0x1504, mh_cls);
    return r;
}

__CACHE__
