#ifndef JNT_INTRINSICS
#define JNT_INTRINSICS

#include "jni.h"
#include "jni_dispatch_ids.h"
#if defined(__has_include)
#if __has_include("jni_dispatch_config.h")
#include "jni_dispatch_config.h"
#endif
#endif
#include <math.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define JNTA_MAGIC 0x4A4E5441u

#ifndef JNT_JNI_PROTECTION_ENABLED
#define JNT_JNI_PROTECTION_ENABLED 1
#endif

#ifndef JNT_CUSTOM_CALL_HANDLING_ENABLED
#define JNT_CUSTOM_CALL_HANDLING_ENABLED JNT_JNI_PROTECTION_ENABLED
#endif

#if defined(__clang__) || defined(__GNUC__)
#define JNT_LIKELY(x) __builtin_expect(!!(x), 1)
#define JNT_UNLIKELY(x) __builtin_expect(!!(x), 0)
#else
#define JNT_LIKELY(x) (x)
#define JNT_UNLIKELY(x) (x)
#endif

#define JNT_MARKER_XOR_A UINT64_C(0xA57C0F13E5D4A77B)
#define JNT_MARKER_XOR_B UINT64_C(0x9E3779B97F4A7C15)
#define JNT_MARKER_ROTATE 17u

static inline uint64_t jnt_rotl64(uint64_t value, unsigned int shift) {
    shift &= 63u;
    return (value << shift) | (value >> ((64u - shift) & 63u));
}

static inline uint64_t jnt_rotr64(uint64_t value, unsigned int shift) {
    shift &= 63u;
    return (value >> shift) | (value << ((64u - shift) & 63u));
}

static inline uint64_t jnt_make_marker(uint64_t binding_id, uint64_t salt) {
    uint64_t payload = ((salt & UINT64_C(0x00FFFFFFFFFFFFFF)) << 8) | (binding_id & UINT64_C(0xFF));
    return jnt_rotl64(payload ^ JNT_MARKER_XOR_A, JNT_MARKER_ROTATE) ^ JNT_MARKER_XOR_B;
}

#define JNT_MAKE_MARKER(binding_id, salt) jnt_make_marker((uint64_t)(binding_id), (uint64_t)(salt))
#define JNT_JNI_CALL(env, marker, fn_type, ...) (((fn_type jnt_jni_resolve_callable((env), (marker))))(__VA_ARGS__))
#define JNT_JNI_CALL0(env, marker, fn_type) (((fn_type jnt_jni_resolve_callable((env), (marker))))())

typedef struct JNTNativeMethod {
    int isStatic;
    const jchar* name;
    int name_length;
    const jchar* desc;
    int desc_length;
    void* fn;
} JNTNativeMethod;

typedef struct JNTArrayScope JNTArrayScope;

typedef struct JNTArrayHeader {
    uint32_t magic;
    uint32_t refcount;
    jsize length;
    JNTArrayScope *owner_scope;
    struct JNTArrayHeader *scope_prev;
    struct JNTArrayHeader *scope_next;
} JNTArrayHeader;

struct JNTArrayScope {
    JNTArrayHeader *head;
    JNTArrayScope *prev;
};

typedef struct {
    JNTArrayHeader header;
    jboolean *data;
} JNTBooleanArray;

typedef struct {
    JNTArrayHeader header;
    jbyte *data;
} JNTByteArray;

typedef struct {
    JNTArrayHeader header;
    jchar *data;
} JNTCharArray;

typedef struct {
    JNTArrayHeader header;
    jshort *data;
} JNTShortArray;

typedef struct {
    JNTArrayHeader header;
    jint *data;
} JNTIntArray;

typedef struct {
    JNTArrayHeader header;
    jlong *data;
} JNTLongArray;

typedef struct {
    JNTArrayHeader header;
    jfloat *data;
} JNTFloatArray;

typedef struct {
    JNTArrayHeader header;
    jdouble *data;
} JNTDoubleArray;

typedef enum {
    BOOTSTRAP_PLAIN = 0,
    PROTECTED_ARENA = 1,
    PLAIN_JNI_FALLBACK = 2
} JNTJniDispatchMode;

typedef enum {
    JNT_JNI_INTEGRITY_NONE = 0,
    JNT_JNI_INTEGRITY_WEAK = 1,
    JNT_JNI_INTEGRITY_STRONG = 2
} JNTJniIntegritySignal;

typedef struct JNTJniDispatchEntry {
    uint64_t encoded_binding;
    uintptr_t encoded_target;
    uintptr_t encoded_callable;
    uint64_t entry_cookie;
    uint32_t flags;
} JNTJniDispatchEntry;

typedef struct JNTJniDispatchArena {
    uint64_t cookie;
    JNTJniDispatchMode mode;
    int slot_of_binding[JNT_BINDING_COUNT];
    size_t entry_count;
    size_t real_entry_count;
    JNTJniDispatchEntry *entries;
    void *thunk_memory;
    size_t thunk_memory_size;
} JNTJniDispatchArena;

typedef struct JNTJniProbeResult {
    JNTJniIntegritySignal signal;
    void *branch_target;
    uintptr_t module_token;
    int executable;
    int writable;
    int image_backed;
} JNTJniProbeResult;

extern uintptr_t jnt_NativeAddrOffset;

void jnt_reset_traceback(JNIEnv* env);

void* jnt_jni_resolve_plain(JNIEnv* env, int bindingId);
void* jnt_jni_resolve_callable(JNIEnv* env, uint64_t marker);
void jnt_jni_dispatch_init(JNIEnv* env);
JNTJniIntegritySignal jnt_jni_probe_binding(JNIEnv* env, int bindingId, void* target);

void jnt_RegisterNatives(JNIEnv* env, jclass cls,
                         JNTNativeMethod* methods, jint count);
void jnt_RegisterNative(JNIEnv* env, jclass cls,
                        const char* name, const char* desc, int isStatic, void* fn);

jobject newBooleanArray(jsize length);
jobject newByteArray(jsize length);
jobject newCharArray(jsize length);
jobject newShortArray(jsize length);
jobject newIntArray(jsize length);
jobject newLongArray(jsize length);
jobject newFloatArray(jsize length);
jobject newDoubleArray(jsize length);

void jnt_array_scope_enter(JNTArrayScope *scope);
void jnt_array_scope_cleanup(JNTArrayScope *scope);
void jnt_array_scope_leave(JNTArrayScope *scope);

jsize getArrayLength(const void *arr);
jboolean isCustomArrayPtr(const void *arr);

void retainArrayPtr(void *arr);
void releaseArrayPtr(void *arr);
void freeArrayPtr(void *arr);
void freeArray(jvalue v);

jbyteArray JNTByteArray_to_jByteArray(JNIEnv* env, void *arr);
void jByteArray_to_JNTByteArray(JNIEnv* env, void *arr);

jclass jnt_Object_getClass(JNIEnv* env, jobject instance);

jint jnt_String_length(JNIEnv* env, jobject instance);
jboolean jnt_String_isEmpty(JNIEnv* env, jobject instance);

void jnt_Runtime_halt(JNIEnv* env, jint status);
void jnt_System_exit(JNIEnv* env, jint status);

jint jnt_Math_abs_int(JNIEnv* env, jint a);
jfloat jnt_Math_abs_float(JNIEnv* env, jfloat a);
jlong jnt_Math_abs_long(JNIEnv* env, jlong a);
jdouble jnt_Math_abs_double(JNIEnv* env, jdouble a);

jint jnt_Math_min_int(JNIEnv* env, jint a, jint b);
jfloat jnt_Math_min_float(JNIEnv* env, jfloat a, jfloat b);
jlong jnt_Math_min_long(JNIEnv* env, jlong a, jlong b);
jdouble jnt_Math_min_double(JNIEnv* env, jdouble a, jdouble b);

jint jnt_Math_max_int(JNIEnv* env, jint a, jint b);
jfloat jnt_Math_max_float(JNIEnv* env, jfloat a, jfloat b);
jlong jnt_Math_max_long(JNIEnv* env, jlong a, jlong b);
jdouble jnt_Math_max_double(JNIEnv* env, jdouble a, jdouble b);

jdouble jnt_Math_sqrt_double(JNIEnv* env, jdouble a);
jdouble jnt_Math_pow_double(JNIEnv* env, jdouble a, jdouble b);
jint jnt_Math_round_float(JNIEnv* env, jfloat a);
jlong jnt_Math_round_double(JNIEnv* env, jdouble a);

char* jstr_to_utf8(const jchar* input, int length);

#endif
