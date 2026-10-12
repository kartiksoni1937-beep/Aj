#include "boxing.h"

#if JNT_JNI_PROTECTION_ENABLED
#define BOX_MARKER(binding, salt) JNT_MAKE_MARKER((binding), UINT64_C(salt))
#define BOX_FIND_CLASS(salt, name) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_FIND_CLASS, salt), (jclass (*)(JNIEnv *, const char *)), env, name)
#define BOX_NEW_GLOBAL_REF(salt, obj) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_NEW_GLOBAL_REF, salt), (jobject (*)(JNIEnv *, jobject)), env, obj)
#define BOX_DELETE_LOCAL_REF(salt, obj) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_DELETE_LOCAL_REF, salt), (void (*)(JNIEnv *, jobject)), env, obj)
#define BOX_GET_STATIC_METHOD_ID(salt, cls, name, sig) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_GET_STATIC_METHOD_ID, salt), (jmethodID (*)(JNIEnv *, jclass, const char *, const char *)), env, cls, name, sig)
#define BOX_GET_METHOD_ID(salt, cls, name, sig) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_GET_METHOD_ID, salt), (jmethodID (*)(JNIEnv *, jclass, const char *, const char *)), env, cls, name, sig)
#define BOX_CALL_STATIC_OBJECT(salt, cls, mid, ...) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_STATIC_OBJECT_METHOD, salt), (jobject (*)(JNIEnv *, jclass, jmethodID, ...)), env, cls, mid, __VA_ARGS__)
#define BOX_CALL_INT(salt, obj, mid) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_INT_METHOD, salt), (jint (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#define BOX_CALL_LONG(salt, obj, mid) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_LONG_METHOD, salt), (jlong (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#define BOX_CALL_CHAR(salt, obj, mid) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_CHAR_METHOD, salt), (jchar (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#define BOX_CALL_SHORT(salt, obj, mid) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_SHORT_METHOD, salt), (jshort (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#define BOX_CALL_BYTE(salt, obj, mid) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_BYTE_METHOD, salt), (jbyte (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#define BOX_CALL_BOOLEAN(salt, obj, mid) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_BOOLEAN_METHOD, salt), (jboolean (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#define BOX_CALL_FLOAT(salt, obj, mid) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_FLOAT_METHOD, salt), (jfloat (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#define BOX_CALL_DOUBLE(salt, obj, mid) JNT_JNI_CALL(env, BOX_MARKER(JNT_BINDING_CALL_DOUBLE_METHOD, salt), (jdouble (*)(JNIEnv *, jobject, jmethodID, ...)), env, obj, mid)
#else
#define BOX_FIND_CLASS(salt, name) ((*env)->FindClass(env, name))
#define BOX_NEW_GLOBAL_REF(salt, obj) ((*env)->NewGlobalRef(env, obj))
#define BOX_DELETE_LOCAL_REF(salt, obj) ((*env)->DeleteLocalRef(env, obj))
#define BOX_GET_STATIC_METHOD_ID(salt, cls, name, sig) ((*env)->GetStaticMethodID(env, cls, name, sig))
#define BOX_GET_METHOD_ID(salt, cls, name, sig) ((*env)->GetMethodID(env, cls, name, sig))
#define BOX_CALL_STATIC_OBJECT(salt, cls, mid, ...) ((*env)->CallStaticObjectMethod(env, cls, mid, __VA_ARGS__))
#define BOX_CALL_INT(salt, obj, mid) ((*env)->CallIntMethod(env, obj, mid))
#define BOX_CALL_LONG(salt, obj, mid) ((*env)->CallLongMethod(env, obj, mid))
#define BOX_CALL_CHAR(salt, obj, mid) ((*env)->CallCharMethod(env, obj, mid))
#define BOX_CALL_SHORT(salt, obj, mid) ((*env)->CallShortMethod(env, obj, mid))
#define BOX_CALL_BYTE(salt, obj, mid) ((*env)->CallByteMethod(env, obj, mid))
#define BOX_CALL_BOOLEAN(salt, obj, mid) ((*env)->CallBooleanMethod(env, obj, mid))
#define BOX_CALL_FLOAT(salt, obj, mid) ((*env)->CallFloatMethod(env, obj, mid))
#define BOX_CALL_DOUBLE(salt, obj, mid) ((*env)->CallDoubleMethod(env, obj, mid))
#endif

static jclass kls_Integer;
static jmethodID mid_Integer_valueOf;
static jmethodID mid_Integer_intValue;

static jclass kls_Long;
static jmethodID mid_Long_valueOf;
static jmethodID mid_Long_longValue;

static jclass kls_Character;
static jmethodID mid_Character_valueOf;
static jmethodID mid_Character_charValue;

static jclass kls_Short;
static jmethodID mid_Short_valueOf;
static jmethodID mid_Short_shortValue;

static jclass kls_Byte;
static jmethodID mid_Byte_valueOf;
static jmethodID mid_Byte_byteValue;

static jclass kls_Boolean;
static jmethodID mid_Boolean_valueOf;
static jmethodID mid_Boolean_booleanValue;

static jclass kls_Float;
static jmethodID mid_Float_valueOf;
static jmethodID mid_Float_floatValue;

static jclass kls_Double;
static jmethodID mid_Double_valueOf;
static jmethodID mid_Double_doubleValue;

void init_boxing_classes(JNIEnv* env) {
    #define CACHE_BOXING(seed, wrapper, sigBox, nameUnbox, sigUnbox, cls, midBox, midUnbox) do { \
        jclass t = BOX_FIND_CLASS(seed + 0x01, "java/lang/" wrapper); \
        if (!t) return; \
        cls = (jclass) BOX_NEW_GLOBAL_REF(seed + 0x02, t); \
        BOX_DELETE_LOCAL_REF(seed + 0x03, t); \
        midBox = BOX_GET_STATIC_METHOD_ID(seed + 0x04, cls, "valueOf", sigBox); \
        if (!midBox) return; \
        midUnbox = BOX_GET_METHOD_ID(seed + 0x05, cls, nameUnbox, sigUnbox); \
        if (!midUnbox) return; \
    } while (0)

    CACHE_BOXING(0x100, "Integer", "(I)Ljava/lang/Integer;", "intValue", "()I", kls_Integer, mid_Integer_valueOf, mid_Integer_intValue);
    CACHE_BOXING(0x200, "Long", "(J)Ljava/lang/Long;", "longValue", "()J", kls_Long, mid_Long_valueOf, mid_Long_longValue);
    CACHE_BOXING(0x300, "Character", "(C)Ljava/lang/Character;", "charValue", "()C", kls_Character, mid_Character_valueOf, mid_Character_charValue);
    CACHE_BOXING(0x400, "Short", "(S)Ljava/lang/Short;", "shortValue", "()S", kls_Short, mid_Short_valueOf, mid_Short_shortValue);
    CACHE_BOXING(0x500, "Byte", "(B)Ljava/lang/Byte;", "byteValue", "()B", kls_Byte, mid_Byte_valueOf, mid_Byte_byteValue);
    CACHE_BOXING(0x600, "Boolean", "(Z)Ljava/lang/Boolean;", "booleanValue", "()Z", kls_Boolean, mid_Boolean_valueOf, mid_Boolean_booleanValue);
    CACHE_BOXING(0x700, "Float", "(F)Ljava/lang/Float;", "floatValue", "()F", kls_Float, mid_Float_valueOf, mid_Float_floatValue);
    CACHE_BOXING(0x800, "Double", "(D)Ljava/lang/Double;", "doubleValue", "()D", kls_Double, mid_Double_valueOf, mid_Double_doubleValue);

    #undef CACHE_BOXING
}

jobject make_object_int(JNIEnv* env, jint v) { return BOX_CALL_STATIC_OBJECT(0x901, kls_Integer, mid_Integer_valueOf, v); }
jobject make_object_long(JNIEnv* env, jlong v) { return BOX_CALL_STATIC_OBJECT(0x902, kls_Long, mid_Long_valueOf, v); }
jobject make_object_char(JNIEnv* env, jchar v) { return BOX_CALL_STATIC_OBJECT(0x903, kls_Character, mid_Character_valueOf, v); }
jobject make_object_short(JNIEnv* env, jshort v) { return BOX_CALL_STATIC_OBJECT(0x904, kls_Short, mid_Short_valueOf, v); }
jobject make_object_byte(JNIEnv* env, jbyte v) { return BOX_CALL_STATIC_OBJECT(0x905, kls_Byte, mid_Byte_valueOf, v); }
jobject make_object_boolean(JNIEnv* env, jboolean v) { return BOX_CALL_STATIC_OBJECT(0x906, kls_Boolean, mid_Boolean_valueOf, v); }
jobject make_object_float(JNIEnv* env, jfloat v) { return BOX_CALL_STATIC_OBJECT(0x907, kls_Float, mid_Float_valueOf, v); }
jobject make_object_double(JNIEnv* env, jdouble v) { return BOX_CALL_STATIC_OBJECT(0x908, kls_Double, mid_Double_valueOf, v); }

jint unbox_int(JNIEnv* env, jobject o) { return BOX_CALL_INT(0xA01, o, mid_Integer_intValue); }
jlong unbox_long(JNIEnv* env, jobject o) { return BOX_CALL_LONG(0xA02, o, mid_Long_longValue); }
jchar unbox_char(JNIEnv* env, jobject o) { return BOX_CALL_CHAR(0xA03, o, mid_Character_charValue); }
jshort unbox_short(JNIEnv* env, jobject o) { return BOX_CALL_SHORT(0xA04, o, mid_Short_shortValue); }
jbyte unbox_byte(JNIEnv* env, jobject o) { return BOX_CALL_BYTE(0xA05, o, mid_Byte_byteValue); }
jboolean unbox_boolean(JNIEnv* env, jobject o) { return BOX_CALL_BOOLEAN(0xA06, o, mid_Boolean_booleanValue); }
jfloat unbox_float(JNIEnv* env, jobject o) { return BOX_CALL_FLOAT(0xA07, o, mid_Float_floatValue); }
jdouble unbox_double(JNIEnv* env, jobject o) { return BOX_CALL_DOUBLE(0xA08, o, mid_Double_doubleValue); }
