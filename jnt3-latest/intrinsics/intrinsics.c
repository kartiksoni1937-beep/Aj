#if defined(__linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif

#include "intrinsics.h"

typedef char* address;

#if defined(_WIN32)
#include <windows.h>
#else
#include <dlfcn.h>
#include <sys/mman.h>
#include <time.h>
#if defined(__linux__)
#include <unistd.h>
#endif
#endif

#if defined(_MSC_VER)
#define JNT_THREAD_LOCAL __declspec(thread)
#else
#define JNT_THREAD_LOCAL __thread
#endif

#define JNT_ALIGN_UP(value, alignment) (((value) + ((alignment) - 1u)) & ~((alignment) - 1u))
#define JNT_ARRAY_DATA_OFFSET(array_type, component_type) JNT_ALIGN_UP(sizeof(array_type), _Alignof(component_type))

static JNT_THREAD_LOCAL JNTArrayScope *jnt_current_array_scope = NULL;
static JNTJniDispatchArena jnt_jni_dispatch = {0};
static int jnt_jni_dispatch_initialized = 0;

static void jnt_force_free_array(JNTArrayHeader *header) {
    if (header == NULL) {
        return;
    }
    header->owner_scope = NULL;
    header->scope_prev = NULL;
    header->scope_next = NULL;
    header->refcount = 0u;
    header->magic = 0u;
    free(header);
}

static void jnt_array_scope_register(JNTArrayHeader *header) {
    if (header == NULL || jnt_current_array_scope == NULL) {
        return;
    }

    header->owner_scope = jnt_current_array_scope;
    header->scope_prev = NULL;
    header->scope_next = jnt_current_array_scope->head;
    if (jnt_current_array_scope->head != NULL) {
        jnt_current_array_scope->head->scope_prev = header;
    }
    jnt_current_array_scope->head = header;
}

static void jnt_array_scope_unlink(JNTArrayHeader *header) {
    if (header == NULL) {
        return;
    }

    if (header->owner_scope == NULL) {
#ifndef NDEBUG
        fprintf(stderr, "jnt_array_scope_unlink: detached node %p\n", (void*) header);
#endif
        return;
    }

    JNTArrayScope *scope = header->owner_scope;
    if (header->scope_prev != NULL) {
        header->scope_prev->scope_next = header->scope_next;
    } else if (scope->head == header) {
        scope->head = header->scope_next;
    }
    if (header->scope_next != NULL) {
        header->scope_next->scope_prev = header->scope_prev;
    }

    header->owner_scope = NULL;
    header->scope_prev = NULL;
    header->scope_next = NULL;
}

static void *jnt_alloc_custom_array(size_t header_size, size_t component_align, size_t component_size, jsize length) {
    size_t element_count = length > 0 ? (size_t) length : 0u;
    if (component_size != 0u && element_count > (SIZE_MAX / component_size)) {
        return NULL;
    }

    size_t data_offset = JNT_ALIGN_UP(header_size, component_align);
    size_t data_size = element_count * component_size;
    if (data_offset > (SIZE_MAX - data_size)) {
        return NULL;
    }

    JNTArrayHeader *header = calloc(1u, data_offset + data_size);
    if (header == NULL) {
        return NULL;
    }

    header->magic = JNTA_MAGIC;
    header->refcount = 1u;
    header->length = length;
    jnt_array_scope_register(header);
    return header;
}

void jnt_array_scope_enter(JNTArrayScope *scope) {
    if (scope == NULL) {
        return;
    }

    scope->head = NULL;
    scope->prev = jnt_current_array_scope;
    jnt_current_array_scope = scope;
}

void jnt_array_scope_cleanup(JNTArrayScope *scope) {
    if (scope == NULL) {
        return;
    }

    JNTArrayHeader *current = scope->head;
    scope->head = NULL;
    while (current != NULL) {
        JNTArrayHeader *next = current->scope_next;
#ifndef NDEBUG
        if (current->owner_scope != scope) {
            fprintf(stderr, "jnt_array_scope_cleanup: detached node %p\n", (void*) current);
        }
#endif
        jnt_force_free_array(current);
        current = next;
    }
}

void jnt_array_scope_leave(JNTArrayScope *scope) {
    if (scope == NULL) {
        return;
    }

    if (jnt_current_array_scope == scope) {
        jnt_current_array_scope = scope->prev;
    }
    scope->prev = NULL;
}

#define JNT_JNI_ENTRY_REAL 0x1u
#define JNT_JNI_ENTRY_DECOY 0x2u

typedef struct JNTAddressInfo {
    uintptr_t module_token;
    int executable;
    int writable;
    int image_backed;
} JNTAddressInfo;

static uint64_t jnt_rng_state = UINT64_C(0xD1B54A32D192ED03);
static uintptr_t jnt_baseline_modules[8];
static size_t jnt_baseline_module_count = 0;

static uint64_t jnt_rng_next(void) {
    uint64_t x = jnt_rng_state;
    x ^= x << 13;
    x ^= x >> 7;
    x ^= x << 17;
    jnt_rng_state = x;
    return x;
}

static void jnt_rng_seed(JNIEnv *env) {
    uint64_t seed = UINT64_C(0x9E3779B97F4A7C15);
    seed ^= (uint64_t) (uintptr_t) env;
    seed ^= (uint64_t) (uintptr_t) &jnt_rng_state;
#if defined(_WIN32)
    seed ^= (uint64_t) GetTickCount64();
#else
    seed ^= (uint64_t) time(NULL);
#endif
    if (seed == 0) {
        seed = UINT64_C(0xA57C0F13E5D4A77B);
    }
    jnt_rng_state = seed;
}

static uintptr_t jnt_encode_ptr(uintptr_t value, uint64_t cookie) {
    return value ^ (uintptr_t) cookie ^ (uintptr_t) (cookie >> 11);
}

static uintptr_t jnt_decode_ptr(uintptr_t value, uint64_t cookie) {
    return value ^ (uintptr_t) cookie ^ (uintptr_t) (cookie >> 11);
}

static int jnt_decode_binding_id(uint64_t marker) {
    uint64_t payload = jnt_rotr64(marker ^ JNT_MARKER_XOR_B, JNT_MARKER_ROTATE) ^ JNT_MARKER_XOR_A;
    return (int) (payload & UINT64_C(0xFF));
}

static int jnt_is_exec_protect(unsigned long protect) {
#if defined(_WIN32)
    switch (protect & 0xffu) {
        case PAGE_EXECUTE:
        case PAGE_EXECUTE_READ:
        case PAGE_EXECUTE_READWRITE:
        case PAGE_EXECUTE_WRITECOPY:
            return 1;
        default:
            return 0;
    }
#else
    return (protect & PROT_EXEC) != 0;
#endif
}

static int jnt_is_write_protect(unsigned long protect) {
#if defined(_WIN32)
    switch (protect & 0xffu) {
        case PAGE_READWRITE:
        case PAGE_WRITECOPY:
        case PAGE_EXECUTE_READWRITE:
        case PAGE_EXECUTE_WRITECOPY:
            return 1;
        default:
            return 0;
    }
#else
    return (protect & PROT_WRITE) != 0;
#endif
}

static int jnt_query_address_info(void *addr, JNTAddressInfo *info) {
    if (addr == NULL || info == NULL) {
        return 0;
    }
    memset(info, 0, sizeof(*info));
#if defined(_WIN32)
    MEMORY_BASIC_INFORMATION mbi;
    if (VirtualQuery(addr, &mbi, sizeof(mbi)) != sizeof(mbi)) {
        return 0;
    }
    HMODULE module = NULL;
    GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            (LPCSTR) addr, &module);
    info->module_token = (uintptr_t) module;
    info->executable = jnt_is_exec_protect((unsigned long) mbi.Protect);
    info->writable = jnt_is_write_protect((unsigned long) mbi.Protect);
    info->image_backed = mbi.Type == MEM_IMAGE;
    return 1;
#else
    Dl_info dl_info;
    if (dladdr(addr, &dl_info) != 0) {
        info->module_token = (uintptr_t) dl_info.dli_fbase;
        info->image_backed = dl_info.dli_fname != NULL;
    }
#if defined(__linux__)
    FILE *maps = fopen("/proc/self/maps", "r");
    if (maps != NULL) {
        char line[512];
        uintptr_t query = (uintptr_t) addr;
        while (fgets(line, sizeof(line), maps) != NULL) {
            unsigned long long start = 0;
            unsigned long long end = 0;
            char perms[5] = {0};
            if (sscanf(line, "%llx-%llx %4s", &start, &end, perms) != 3) {
                continue;
            }
            if (query < (uintptr_t) start || query >= (uintptr_t) end) {
                continue;
            }
            info->executable = strchr(perms, 'x') != NULL;
            info->writable = strchr(perms, 'w') != NULL;
            info->image_backed = strchr(line, '/') != NULL;
            fclose(maps);
            return 1;
        }
        fclose(maps);
    }
#endif
    return info->module_token != 0;
#endif
}

static int jnt_module_is_accepted(uintptr_t module_token) {
    if (module_token == 0) {
        return 0;
    }
    for (size_t i = 0; i < jnt_baseline_module_count; i++) {
        if (jnt_baseline_modules[i] == module_token) {
            return 1;
        }
    }
    return 0;
}

static void jnt_record_baseline_module(uintptr_t module_token) {
    if (module_token == 0 || jnt_module_is_accepted(module_token)) {
        return;
    }
    if (jnt_baseline_module_count < (sizeof(jnt_baseline_modules) / sizeof(jnt_baseline_modules[0]))) {
        jnt_baseline_modules[jnt_baseline_module_count++] = module_token;
    }
}

static void *jnt_resolve_branch_target(void *target, JNTJniIntegritySignal *signal) {
    if (target == NULL) {
        return NULL;
    }
#if defined(__x86_64__) || defined(_M_X64)
    const unsigned char *p = (const unsigned char *) target;
    if (p[0] == 0xE9) {
        int32_t rel = *(const int32_t *) (p + 1);
        *signal = JNT_JNI_INTEGRITY_WEAK;
        return (void *) (p + 5 + rel);
    }
    if (p[0] == 0xFF && p[1] == 0x25) {
        int32_t disp = *(const int32_t *) (p + 2);
        uintptr_t *slot = (uintptr_t *) (p + 6 + disp);
        *signal = JNT_JNI_INTEGRITY_WEAK;
        return (void *) *slot;
    }
    if (p[0] == 0x48 && p[1] == 0xB8 && p[10] == 0xFF && p[11] == 0xE0) {
        uint64_t imm = *(const uint64_t *) (p + 2);
        *signal = JNT_JNI_INTEGRITY_WEAK;
        return (void *) (uintptr_t) imm;
    }
    if (p[0] == 0x49 && p[1] == 0xBB && p[10] == 0x41 && p[11] == 0xFF && p[12] == 0xE3) {
        uint64_t imm = *(const uint64_t *) (p + 2);
        *signal = JNT_JNI_INTEGRITY_WEAK;
        return (void *) (uintptr_t) imm;
    }
    if (p[0] == 0x68 && p[5] == 0xC3) {
        uint32_t imm = *(const uint32_t *) (p + 1);
        *signal = JNT_JNI_INTEGRITY_WEAK;
        return (void *) (uintptr_t) imm;
    }
#elif defined(__aarch64__) || defined(_M_ARM64)
    const uint32_t *p = (const uint32_t *) target;
    uint32_t inst0 = p[0];
    if ((inst0 & 0x7C000000u) == 0x14000000u) {
        int32_t imm26 = (int32_t) (inst0 & 0x03FFFFFFu);
        imm26 = (imm26 << 6) >> 4;
        *signal = JNT_JNI_INTEGRITY_WEAK;
        return (void *) ((uintptr_t) target + (uintptr_t) imm26);
    }
    if ((p[0] & 0xFFC0001Fu) == 0xD2800010u &&
            (p[1] & 0xFF80001Fu) == 0xF2800010u &&
            (p[2] & 0xFF80001Fu) == 0xF2800010u &&
            (p[3] & 0xFF80001Fu) == 0xF2800010u &&
            (p[4] & 0xFFFFFC1Fu) == 0xD61F0200u) {
        uint64_t imm = ((uint64_t) ((p[0] >> 5) & 0xFFFFu))
                | ((uint64_t) ((p[1] >> 5) & 0xFFFFu) << 16)
                | ((uint64_t) ((p[2] >> 5) & 0xFFFFu) << 32)
                | ((uint64_t) ((p[3] >> 5) & 0xFFFFu) << 48);
        *signal = JNT_JNI_INTEGRITY_WEAK;
        return (void *) (uintptr_t) imm;
    }
    if ((inst0 & 0x9F000000u) == 0x90000000u) {
        *signal = JNT_JNI_INTEGRITY_WEAK;
        return NULL;
    }
#endif
    return NULL;
}

static void *jnt_alloc_thunk_pages(size_t size) {
#if defined(_WIN32)
    return VirtualAlloc(NULL, size, MEM_COMMIT | MEM_RESERVE, PAGE_READWRITE);
#else
    void *mem = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    return mem == MAP_FAILED ? NULL : mem;
#endif
}

static int jnt_protect_thunk_pages(void *mem, size_t size) {
    if (mem == NULL || size == 0) {
        return 0;
    }
#if defined(_WIN32)
    DWORD old_protect = 0;
    if (!VirtualProtect(mem, size, PAGE_EXECUTE_READ, &old_protect)) {
        return 0;
    }
    FlushInstructionCache(GetCurrentProcess(), mem, size);
    return 1;
#else
    if (mprotect(mem, size, PROT_READ | PROT_EXEC) != 0) {
        return 0;
    }
#if defined(__GNUC__) || defined(__clang__)
    __builtin___clear_cache((char *) mem, (char *) mem + size);
#endif
    return 1;
#endif
}

static size_t jnt_emit_thunk(unsigned char *dst, void *target, uint64_t cookie) {
    if (dst == NULL || target == NULL) {
        return 0;
    }
#if defined(__x86_64__) || defined(_M_X64)
    size_t pad = (size_t) (cookie & 0x3u);
    int use_r11 = (cookie & 0x4u) != 0;
    size_t offset = 0;
    for (size_t i = 0; i < pad; i++) {
        dst[offset++] = 0x90;
    }
    if (use_r11) {
        dst[offset++] = 0x49;
        dst[offset++] = 0xBB;
        *(uint64_t *) (dst + offset) = (uint64_t) (uintptr_t) target;
        offset += sizeof(uint64_t);
        dst[offset++] = 0x41;
        dst[offset++] = 0xFF;
        dst[offset++] = 0xE3;
    } else {
        dst[offset++] = 0x48;
        dst[offset++] = 0xB8;
        *(uint64_t *) (dst + offset) = (uint64_t) (uintptr_t) target;
        offset += sizeof(uint64_t);
        dst[offset++] = 0xFF;
        dst[offset++] = 0xE0;
    }
    return offset;
#elif defined(__aarch64__) || defined(_M_ARM64)
    uint32_t *words = (uint32_t *) dst;
    unsigned int reg = (cookie & 1u) ? 17u : 16u;
    uint64_t value = (uint64_t) (uintptr_t) target;
    words[0] = 0xD2800000u | (((uint32_t) value & 0xFFFFu) << 5) | reg;
    words[1] = 0xF2A00000u | ((((uint32_t) (value >> 16)) & 0xFFFFu) << 5) | reg;
    words[2] = 0xF2C00000u | ((((uint32_t) (value >> 32)) & 0xFFFFu) << 5) | reg;
    words[3] = 0xF2E00000u | ((((uint32_t) (value >> 48)) & 0xFFFFu) << 5) | reg;
    words[4] = 0xD61F0000u | (reg << 5);
    return sizeof(uint32_t) * 5u;
#else
    (void) cookie;
    return 0;
#endif
}

void* jnt_jni_resolve_plain(JNIEnv* env, int bindingId) {
    switch (bindingId) {
        case JNT_BINDING_FIND_CLASS: return (void*) (uintptr_t) (*env)->FindClass;
        case JNT_BINDING_THROW: return (void*) (uintptr_t) (*env)->Throw;
        case JNT_BINDING_THROW_NEW: return (void*) (uintptr_t) (*env)->ThrowNew;
        case JNT_BINDING_EXCEPTION_OCCURRED: return (void*) (uintptr_t) (*env)->ExceptionOccurred;
        case JNT_BINDING_EXCEPTION_DESCRIBE: return (void*) (uintptr_t) (*env)->ExceptionDescribe;
        case JNT_BINDING_EXCEPTION_CLEAR: return (void*) (uintptr_t) (*env)->ExceptionClear;
        case JNT_BINDING_EXCEPTION_CHECK: return (void*) (uintptr_t) (*env)->ExceptionCheck;
        case JNT_BINDING_PUSH_LOCAL_FRAME: return (void*) (uintptr_t) (*env)->PushLocalFrame;
        case JNT_BINDING_POP_LOCAL_FRAME: return (void*) (uintptr_t) (*env)->PopLocalFrame;
        case JNT_BINDING_NEW_GLOBAL_REF: return (void*) (uintptr_t) (*env)->NewGlobalRef;
        case JNT_BINDING_DELETE_LOCAL_REF: return (void*) (uintptr_t) (*env)->DeleteLocalRef;
        case JNT_BINDING_IS_SAME_OBJECT: return (void*) (uintptr_t) (*env)->IsSameObject;
        case JNT_BINDING_NEW_LOCAL_REF: return (void*) (uintptr_t) (*env)->NewLocalRef;
        case JNT_BINDING_ALLOC_OBJECT: return (void*) (uintptr_t) (*env)->AllocObject;
        case JNT_BINDING_NEW_OBJECT_ARRAY: return (void*) (uintptr_t) (*env)->NewObjectArray;
        case JNT_BINDING_GET_OBJECT_CLASS: return (void*) (uintptr_t) (*env)->GetObjectClass;
        case JNT_BINDING_IS_INSTANCE_OF: return (void*) (uintptr_t) (*env)->IsInstanceOf;
        case JNT_BINDING_GET_METHOD_ID: return (void*) (uintptr_t) (*env)->GetMethodID;
        case JNT_BINDING_CALL_OBJECT_METHOD: return (void*) (uintptr_t) (*env)->CallObjectMethod;
        case JNT_BINDING_CALL_BOOLEAN_METHOD: return (void*) (uintptr_t) (*env)->CallBooleanMethod;
        case JNT_BINDING_CALL_BYTE_METHOD: return (void*) (uintptr_t) (*env)->CallByteMethod;
        case JNT_BINDING_CALL_CHAR_METHOD: return (void*) (uintptr_t) (*env)->CallCharMethod;
        case JNT_BINDING_CALL_SHORT_METHOD: return (void*) (uintptr_t) (*env)->CallShortMethod;
        case JNT_BINDING_CALL_INT_METHOD: return (void*) (uintptr_t) (*env)->CallIntMethod;
        case JNT_BINDING_CALL_LONG_METHOD: return (void*) (uintptr_t) (*env)->CallLongMethod;
        case JNT_BINDING_CALL_FLOAT_METHOD: return (void*) (uintptr_t) (*env)->CallFloatMethod;
        case JNT_BINDING_CALL_DOUBLE_METHOD: return (void*) (uintptr_t) (*env)->CallDoubleMethod;
        case JNT_BINDING_CALL_OBJECT_METHOD_A: return (void*) (uintptr_t) (*env)->CallObjectMethodA;
        case JNT_BINDING_CALL_BOOLEAN_METHOD_A: return (void*) (uintptr_t) (*env)->CallBooleanMethodA;
        case JNT_BINDING_CALL_BYTE_METHOD_A: return (void*) (uintptr_t) (*env)->CallByteMethodA;
        case JNT_BINDING_CALL_CHAR_METHOD_A: return (void*) (uintptr_t) (*env)->CallCharMethodA;
        case JNT_BINDING_CALL_SHORT_METHOD_A: return (void*) (uintptr_t) (*env)->CallShortMethodA;
        case JNT_BINDING_CALL_INT_METHOD_A: return (void*) (uintptr_t) (*env)->CallIntMethodA;
        case JNT_BINDING_CALL_LONG_METHOD_A: return (void*) (uintptr_t) (*env)->CallLongMethodA;
        case JNT_BINDING_CALL_FLOAT_METHOD_A: return (void*) (uintptr_t) (*env)->CallFloatMethodA;
        case JNT_BINDING_CALL_DOUBLE_METHOD_A: return (void*) (uintptr_t) (*env)->CallDoubleMethodA;
        case JNT_BINDING_CALL_VOID_METHOD_A: return (void*) (uintptr_t) (*env)->CallVoidMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_OBJECT_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualObjectMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_BOOLEAN_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualBooleanMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_BYTE_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualByteMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_CHAR_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualCharMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_SHORT_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualShortMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_INT_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualIntMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_LONG_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualLongMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_FLOAT_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualFloatMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_DOUBLE_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualDoubleMethodA;
        case JNT_BINDING_CALL_NONVIRTUAL_VOID_METHOD_A: return (void*) (uintptr_t) (*env)->CallNonvirtualVoidMethodA;
        case JNT_BINDING_GET_FIELD_ID: return (void*) (uintptr_t) (*env)->GetFieldID;
        case JNT_BINDING_GET_OBJECT_FIELD: return (void*) (uintptr_t) (*env)->GetObjectField;
        case JNT_BINDING_GET_BOOLEAN_FIELD: return (void*) (uintptr_t) (*env)->GetBooleanField;
        case JNT_BINDING_GET_BYTE_FIELD: return (void*) (uintptr_t) (*env)->GetByteField;
        case JNT_BINDING_GET_CHAR_FIELD: return (void*) (uintptr_t) (*env)->GetCharField;
        case JNT_BINDING_GET_SHORT_FIELD: return (void*) (uintptr_t) (*env)->GetShortField;
        case JNT_BINDING_GET_INT_FIELD: return (void*) (uintptr_t) (*env)->GetIntField;
        case JNT_BINDING_GET_LONG_FIELD: return (void*) (uintptr_t) (*env)->GetLongField;
        case JNT_BINDING_GET_FLOAT_FIELD: return (void*) (uintptr_t) (*env)->GetFloatField;
        case JNT_BINDING_GET_DOUBLE_FIELD: return (void*) (uintptr_t) (*env)->GetDoubleField;
        case JNT_BINDING_SET_OBJECT_FIELD: return (void*) (uintptr_t) (*env)->SetObjectField;
        case JNT_BINDING_SET_BOOLEAN_FIELD: return (void*) (uintptr_t) (*env)->SetBooleanField;
        case JNT_BINDING_SET_BYTE_FIELD: return (void*) (uintptr_t) (*env)->SetByteField;
        case JNT_BINDING_SET_CHAR_FIELD: return (void*) (uintptr_t) (*env)->SetCharField;
        case JNT_BINDING_SET_SHORT_FIELD: return (void*) (uintptr_t) (*env)->SetShortField;
        case JNT_BINDING_SET_INT_FIELD: return (void*) (uintptr_t) (*env)->SetIntField;
        case JNT_BINDING_SET_LONG_FIELD: return (void*) (uintptr_t) (*env)->SetLongField;
        case JNT_BINDING_SET_FLOAT_FIELD: return (void*) (uintptr_t) (*env)->SetFloatField;
        case JNT_BINDING_SET_DOUBLE_FIELD: return (void*) (uintptr_t) (*env)->SetDoubleField;
        case JNT_BINDING_GET_STATIC_METHOD_ID: return (void*) (uintptr_t) (*env)->GetStaticMethodID;
        case JNT_BINDING_CALL_STATIC_OBJECT_METHOD: return (void*) (uintptr_t) (*env)->CallStaticObjectMethod;
        case JNT_BINDING_CALL_STATIC_OBJECT_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticObjectMethodA;
        case JNT_BINDING_CALL_STATIC_BOOLEAN_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticBooleanMethodA;
        case JNT_BINDING_CALL_STATIC_BYTE_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticByteMethodA;
        case JNT_BINDING_CALL_STATIC_CHAR_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticCharMethodA;
        case JNT_BINDING_CALL_STATIC_SHORT_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticShortMethodA;
        case JNT_BINDING_CALL_STATIC_INT_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticIntMethodA;
        case JNT_BINDING_CALL_STATIC_LONG_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticLongMethodA;
        case JNT_BINDING_CALL_STATIC_FLOAT_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticFloatMethodA;
        case JNT_BINDING_CALL_STATIC_DOUBLE_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticDoubleMethodA;
        case JNT_BINDING_CALL_STATIC_VOID_METHOD_A: return (void*) (uintptr_t) (*env)->CallStaticVoidMethodA;
        case JNT_BINDING_GET_STATIC_FIELD_ID: return (void*) (uintptr_t) (*env)->GetStaticFieldID;
        case JNT_BINDING_GET_STATIC_OBJECT_FIELD: return (void*) (uintptr_t) (*env)->GetStaticObjectField;
        case JNT_BINDING_GET_STATIC_BOOLEAN_FIELD: return (void*) (uintptr_t) (*env)->GetStaticBooleanField;
        case JNT_BINDING_GET_STATIC_BYTE_FIELD: return (void*) (uintptr_t) (*env)->GetStaticByteField;
        case JNT_BINDING_GET_STATIC_CHAR_FIELD: return (void*) (uintptr_t) (*env)->GetStaticCharField;
        case JNT_BINDING_GET_STATIC_SHORT_FIELD: return (void*) (uintptr_t) (*env)->GetStaticShortField;
        case JNT_BINDING_GET_STATIC_INT_FIELD: return (void*) (uintptr_t) (*env)->GetStaticIntField;
        case JNT_BINDING_GET_STATIC_LONG_FIELD: return (void*) (uintptr_t) (*env)->GetStaticLongField;
        case JNT_BINDING_GET_STATIC_FLOAT_FIELD: return (void*) (uintptr_t) (*env)->GetStaticFloatField;
        case JNT_BINDING_GET_STATIC_DOUBLE_FIELD: return (void*) (uintptr_t) (*env)->GetStaticDoubleField;
        case JNT_BINDING_SET_STATIC_OBJECT_FIELD: return (void*) (uintptr_t) (*env)->SetStaticObjectField;
        case JNT_BINDING_SET_STATIC_BOOLEAN_FIELD: return (void*) (uintptr_t) (*env)->SetStaticBooleanField;
        case JNT_BINDING_SET_STATIC_BYTE_FIELD: return (void*) (uintptr_t) (*env)->SetStaticByteField;
        case JNT_BINDING_SET_STATIC_CHAR_FIELD: return (void*) (uintptr_t) (*env)->SetStaticCharField;
        case JNT_BINDING_SET_STATIC_SHORT_FIELD: return (void*) (uintptr_t) (*env)->SetStaticShortField;
        case JNT_BINDING_SET_STATIC_INT_FIELD: return (void*) (uintptr_t) (*env)->SetStaticIntField;
        case JNT_BINDING_SET_STATIC_LONG_FIELD: return (void*) (uintptr_t) (*env)->SetStaticLongField;
        case JNT_BINDING_SET_STATIC_FLOAT_FIELD: return (void*) (uintptr_t) (*env)->SetStaticFloatField;
        case JNT_BINDING_SET_STATIC_DOUBLE_FIELD: return (void*) (uintptr_t) (*env)->SetStaticDoubleField;
        case JNT_BINDING_NEW_STRING: return (void*) (uintptr_t) (*env)->NewString;
        case JNT_BINDING_GET_STRING_LENGTH: return (void*) (uintptr_t) (*env)->GetStringLength;
        case JNT_BINDING_NEW_STRING_UTF: return (void*) (uintptr_t) (*env)->NewStringUTF;
        case JNT_BINDING_GET_ARRAY_LENGTH: return (void*) (uintptr_t) (*env)->GetArrayLength;
        case JNT_BINDING_GET_OBJECT_ARRAY_ELEMENT: return (void*) (uintptr_t) (*env)->GetObjectArrayElement;
        case JNT_BINDING_SET_OBJECT_ARRAY_ELEMENT: return (void*) (uintptr_t) (*env)->SetObjectArrayElement;
        case JNT_BINDING_NEW_BOOLEAN_ARRAY: return (void*) (uintptr_t) (*env)->NewBooleanArray;
        case JNT_BINDING_NEW_BYTE_ARRAY: return (void*) (uintptr_t) (*env)->NewByteArray;
        case JNT_BINDING_NEW_CHAR_ARRAY: return (void*) (uintptr_t) (*env)->NewCharArray;
        case JNT_BINDING_NEW_SHORT_ARRAY: return (void*) (uintptr_t) (*env)->NewShortArray;
        case JNT_BINDING_NEW_INT_ARRAY: return (void*) (uintptr_t) (*env)->NewIntArray;
        case JNT_BINDING_NEW_LONG_ARRAY: return (void*) (uintptr_t) (*env)->NewLongArray;
        case JNT_BINDING_NEW_FLOAT_ARRAY: return (void*) (uintptr_t) (*env)->NewFloatArray;
        case JNT_BINDING_NEW_DOUBLE_ARRAY: return (void*) (uintptr_t) (*env)->NewDoubleArray;
        case JNT_BINDING_GET_BOOLEAN_ARRAY_REGION: return (void*) (uintptr_t) (*env)->GetBooleanArrayRegion;
        case JNT_BINDING_GET_BYTE_ARRAY_REGION: return (void*) (uintptr_t) (*env)->GetByteArrayRegion;
        case JNT_BINDING_GET_CHAR_ARRAY_REGION: return (void*) (uintptr_t) (*env)->GetCharArrayRegion;
        case JNT_BINDING_GET_SHORT_ARRAY_REGION: return (void*) (uintptr_t) (*env)->GetShortArrayRegion;
        case JNT_BINDING_GET_INT_ARRAY_REGION: return (void*) (uintptr_t) (*env)->GetIntArrayRegion;
        case JNT_BINDING_GET_LONG_ARRAY_REGION: return (void*) (uintptr_t) (*env)->GetLongArrayRegion;
        case JNT_BINDING_GET_FLOAT_ARRAY_REGION: return (void*) (uintptr_t) (*env)->GetFloatArrayRegion;
        case JNT_BINDING_GET_DOUBLE_ARRAY_REGION: return (void*) (uintptr_t) (*env)->GetDoubleArrayRegion;
        case JNT_BINDING_SET_BOOLEAN_ARRAY_REGION: return (void*) (uintptr_t) (*env)->SetBooleanArrayRegion;
        case JNT_BINDING_SET_BYTE_ARRAY_REGION: return (void*) (uintptr_t) (*env)->SetByteArrayRegion;
        case JNT_BINDING_SET_CHAR_ARRAY_REGION: return (void*) (uintptr_t) (*env)->SetCharArrayRegion;
        case JNT_BINDING_SET_SHORT_ARRAY_REGION: return (void*) (uintptr_t) (*env)->SetShortArrayRegion;
        case JNT_BINDING_SET_INT_ARRAY_REGION: return (void*) (uintptr_t) (*env)->SetIntArrayRegion;
        case JNT_BINDING_SET_LONG_ARRAY_REGION: return (void*) (uintptr_t) (*env)->SetLongArrayRegion;
        case JNT_BINDING_SET_FLOAT_ARRAY_REGION: return (void*) (uintptr_t) (*env)->SetFloatArrayRegion;
        case JNT_BINDING_SET_DOUBLE_ARRAY_REGION: return (void*) (uintptr_t) (*env)->SetDoubleArrayRegion;
        case JNT_BINDING_MONITOR_ENTER: return (void*) (uintptr_t) (*env)->MonitorEnter;
        case JNT_BINDING_MONITOR_EXIT: return (void*) (uintptr_t) (*env)->MonitorExit;
        default: return NULL;
    }
}

JNTJniIntegritySignal jnt_jni_probe_binding(JNIEnv* env, int bindingId, void* target) {
    (void) env;
    (void) bindingId;
    JNTAddressInfo info_a;
    JNTAddressInfo info_b;
    if (!jnt_query_address_info(target, &info_a) || !jnt_query_address_info(target, &info_b)) {
        return JNT_JNI_INTEGRITY_STRONG;
    }
    if (info_a.module_token != info_b.module_token ||
            info_a.executable != info_b.executable ||
            info_a.writable != info_b.writable ||
            info_a.image_backed != info_b.image_backed) {
        return JNT_JNI_INTEGRITY_STRONG;
    }
    if (!info_a.executable) {
        return JNT_JNI_INTEGRITY_STRONG;
    }
    if (info_a.writable && info_a.executable) {
        return JNT_JNI_INTEGRITY_STRONG;
    }
    if (!jnt_module_is_accepted(info_a.module_token)) {
        return JNT_JNI_INTEGRITY_STRONG;
    }
    JNTJniIntegritySignal branch_signal = JNT_JNI_INTEGRITY_NONE;
    void *branch_target = jnt_resolve_branch_target(target, &branch_signal);
    if (branch_signal == JNT_JNI_INTEGRITY_NONE) {
        return JNT_JNI_INTEGRITY_NONE;
    }
    if (branch_target == NULL) {
        return JNT_JNI_INTEGRITY_WEAK;
    }
    JNTAddressInfo branch_info;
    if (!jnt_query_address_info(branch_target, &branch_info)) {
        return JNT_JNI_INTEGRITY_STRONG;
    }
    if (!branch_info.executable || (branch_info.writable && branch_info.executable)) {
        return JNT_JNI_INTEGRITY_STRONG;
    }
    if (!jnt_module_is_accepted(branch_info.module_token)) {
        return JNT_JNI_INTEGRITY_STRONG;
    }
    return JNT_JNI_INTEGRITY_WEAK;
}

void jnt_jni_dispatch_init(JNIEnv* env) {
    if (jnt_jni_dispatch_initialized) {
        return;
    }

#if !JNT_JNI_PROTECTION_ENABLED
    memset(&jnt_jni_dispatch, 0, sizeof(jnt_jni_dispatch));
    for (size_t i = 0; i < JNT_BINDING_COUNT; i++) {
        jnt_jni_dispatch.slot_of_binding[i] = -1;
    }
    jnt_jni_dispatch.mode = PLAIN_JNI_FALLBACK;
    jnt_jni_dispatch_initialized = 1;
    return;
#endif

    jnt_rng_seed(env);
    memset(&jnt_jni_dispatch, 0, sizeof(jnt_jni_dispatch));
    for (size_t i = 0; i < JNT_BINDING_COUNT; i++) {
        jnt_jni_dispatch.slot_of_binding[i] = -1;
    }
    jnt_jni_dispatch.cookie = jnt_rng_next() ^ UINT64_C(0xA57C0F13E5D4A77B);
    jnt_jni_dispatch.mode = BOOTSTRAP_PLAIN;

    const int anchors[] = {
            JNT_BINDING_FIND_CLASS,
            JNT_BINDING_GET_METHOD_ID,
            JNT_BINDING_CALL_OBJECT_METHOD,
            JNT_BINDING_EXCEPTION_CHECK
    };
    jnt_baseline_module_count = 0;
    for (size_t i = 0; i < sizeof(anchors) / sizeof(anchors[0]); i++) {
        void *target = jnt_jni_resolve_plain(env, anchors[i]);
        JNTAddressInfo info;
        if (target != NULL && jnt_query_address_info(target, &info)) {
            jnt_record_baseline_module(info.module_token);
        }
    }

    size_t real_count = (size_t) (JNT_BINDING_COUNT - 1);
    for (int binding = 1; binding < JNT_BINDING_COUNT; binding++) {
        void *target = jnt_jni_resolve_plain(env, binding);
        if (target == NULL || jnt_jni_probe_binding(env, binding, target) == JNT_JNI_INTEGRITY_STRONG) {
            jnt_jni_dispatch.mode = PLAIN_JNI_FALLBACK;
            jnt_jni_dispatch_initialized = 1;
            return;
        }
    }

    size_t decoy_count = real_count < 16 ? 8 : (real_count / 4);
    jnt_jni_dispatch.real_entry_count = real_count;
    jnt_jni_dispatch.entry_count = real_count + decoy_count;
    jnt_jni_dispatch.entries = calloc(jnt_jni_dispatch.entry_count, sizeof(JNTJniDispatchEntry));
    if (jnt_jni_dispatch.entries == NULL) {
        jnt_jni_dispatch.mode = PLAIN_JNI_FALLBACK;
        jnt_jni_dispatch_initialized = 1;
        return;
    }

    size_t *order = malloc(sizeof(size_t) * jnt_jni_dispatch.entry_count);
    if (order == NULL) {
        jnt_jni_dispatch.mode = PLAIN_JNI_FALLBACK;
        jnt_jni_dispatch_initialized = 1;
        return;
    }
    for (size_t i = 0; i < jnt_jni_dispatch.entry_count; i++) {
        order[i] = i;
    }
    for (size_t i = jnt_jni_dispatch.entry_count; i > 1; i--) {
        size_t j = (size_t) (jnt_rng_next() % i);
        size_t tmp = order[i - 1];
        order[i - 1] = order[j];
        order[j] = tmp;
    }

    size_t thunk_stride = 32;
#if defined(__aarch64__) || defined(_M_ARM64)
    thunk_stride = 24;
#endif
    size_t thunk_size = thunk_stride * real_count;
    unsigned char *thunks = (unsigned char *) jnt_alloc_thunk_pages(thunk_size);
    size_t thunk_offset = 0;

    for (int binding = 1; binding < JNT_BINDING_COUNT; binding++) {
        size_t slot = order[binding - 1];
        void *target = jnt_jni_resolve_plain(env, binding);
        JNTJniDispatchEntry *entry = &jnt_jni_dispatch.entries[slot];
        uint64_t entry_cookie = jnt_rng_next() ^ jnt_jni_dispatch.cookie;
        entry->entry_cookie = entry_cookie;
        entry->flags = JNT_JNI_ENTRY_REAL;
        entry->encoded_binding = ((uint64_t) binding) ^ entry_cookie;
        entry->encoded_target = jnt_encode_ptr((uintptr_t) target, entry_cookie);
        entry->encoded_callable = entry->encoded_target;
        if (thunks != NULL) {
            size_t emitted = jnt_emit_thunk(thunks + thunk_offset, target, entry_cookie);
            if (emitted != 0) {
                entry->encoded_callable = jnt_encode_ptr((uintptr_t) (thunks + thunk_offset), entry_cookie);
                thunk_offset += thunk_stride;
            }
        }
        jnt_jni_dispatch.slot_of_binding[binding] = (int) slot;
    }

    for (size_t i = real_count; i < jnt_jni_dispatch.entry_count; i++) {
        size_t slot = order[i];
        JNTJniDispatchEntry *entry = &jnt_jni_dispatch.entries[slot];
        uint64_t entry_cookie = jnt_rng_next() ^ jnt_jni_dispatch.cookie;
        entry->entry_cookie = entry_cookie;
        entry->flags = JNT_JNI_ENTRY_DECOY;
        entry->encoded_binding = jnt_rng_next() ^ entry_cookie;
        entry->encoded_target = jnt_encode_ptr((uintptr_t) jnt_jni_resolve_plain(env, JNT_BINDING_FIND_CLASS), entry_cookie);
        entry->encoded_callable = entry->encoded_target;
    }

    free(order);
    if (thunks != NULL && thunk_offset != 0 && jnt_protect_thunk_pages(thunks, thunk_size)) {
        jnt_jni_dispatch.thunk_memory = thunks;
        jnt_jni_dispatch.thunk_memory_size = thunk_size;
    } else {
        jnt_jni_dispatch.thunk_memory = NULL;
        jnt_jni_dispatch.thunk_memory_size = 0;
    }

    jnt_jni_dispatch.mode = PROTECTED_ARENA;
    jnt_jni_dispatch_initialized = 1;
}

void* jnt_jni_resolve_callable(JNIEnv* env, uint64_t marker) {
    int binding_id = jnt_decode_binding_id(marker);
    if (binding_id <= 0 || binding_id >= JNT_BINDING_COUNT) {
        return NULL;
    }
#if !JNT_JNI_PROTECTION_ENABLED
    return jnt_jni_resolve_plain(env, binding_id);
#endif
    if (jnt_jni_dispatch.mode == PROTECTED_ARENA && jnt_jni_dispatch.entries != NULL) {
        int slot = jnt_jni_dispatch.slot_of_binding[binding_id];
        if (slot >= 0 && (size_t) slot < jnt_jni_dispatch.entry_count) {
            JNTJniDispatchEntry *entry = &jnt_jni_dispatch.entries[slot];
            return (void *) jnt_decode_ptr(entry->encoded_callable, entry->entry_cookie);
        }
    }
    return jnt_jni_resolve_plain(env, binding_id);
}

void jnt_reset_traceback(JNIEnv* env) {
    address env_addr = (address) env;
    address anchor_addr = env_addr - 0x20;
    *(void**)anchor_addr = 0;
}

finline void jnt_RegisterNative(JNIEnv* env, jclass cls,
                                const char* name, const char* desc,
                                int isStatic, void* fn) {

    jmethodID mid = isStatic
        ? (*env)->GetStaticMethodID(env, cls, name, desc)
        : (*env)->GetMethodID(env, cls, name, desc);

    void* method = *(void**)mid;
    address* addr = (address*)((uintptr_t)method + jnt_NativeAddrOffset);
    *addr = (address)fn;
}

finline void jnt_RegisterNatives(JNIEnv* env, jclass cls,
                                 JNTNativeMethod* methods, jint count) {
    for (jint i = 0; i < count; ++i) {
        const JNTNativeMethod* m = &methods[i];
        char* c_name = jstr_to_utf8(m->name, m->name_length);
        char* c_desc = jstr_to_utf8(m->desc, m->desc_length);
        jnt_RegisterNative(env, cls, c_name, c_desc, m->isStatic, m->fn);
        free(c_name);
        free(c_desc);
    }
}

finline jobject newBooleanArray(const jsize length) {
    JNTBooleanArray *array = jnt_alloc_custom_array(
            sizeof(JNTBooleanArray),
            _Alignof(jboolean),
            sizeof(jboolean),
            length
    );
    if (array == NULL) {
        return NULL;
    }
    array->data = (jboolean*) (((unsigned char*) array) + JNT_ARRAY_DATA_OFFSET(JNTBooleanArray, jboolean));
    return (jobject) array;
}

finline jobject newByteArray(const jsize length) {
    JNTByteArray *array = jnt_alloc_custom_array(
            sizeof(JNTByteArray),
            _Alignof(jbyte),
            sizeof(jbyte),
            length
    );
    if (array == NULL) {
        return NULL;
    }
    array->data = (jbyte*) (((unsigned char*) array) + JNT_ARRAY_DATA_OFFSET(JNTByteArray, jbyte));
    return (jobject) array;
}

finline jobject newCharArray(const jsize length) {
    JNTCharArray *array = jnt_alloc_custom_array(
            sizeof(JNTCharArray),
            _Alignof(jchar),
            sizeof(jchar),
            length
    );
    if (array == NULL) {
        return NULL;
    }
    array->data = (jchar*) (((unsigned char*) array) + JNT_ARRAY_DATA_OFFSET(JNTCharArray, jchar));
    return (jobject) array;
}

finline jobject newShortArray(const jsize length) {
    JNTShortArray *array = jnt_alloc_custom_array(
            sizeof(JNTShortArray),
            _Alignof(jshort),
            sizeof(jshort),
            length
    );
    if (array == NULL) {
        return NULL;
    }
    array->data = (jshort*) (((unsigned char*) array) + JNT_ARRAY_DATA_OFFSET(JNTShortArray, jshort));
    return (jobject) array;
}

finline jobject newIntArray(const jsize length) {
    JNTIntArray *array = jnt_alloc_custom_array(
            sizeof(JNTIntArray),
            _Alignof(jint),
            sizeof(jint),
            length
    );
    if (array == NULL) {
        return NULL;
    }
    array->data = (jint*) (((unsigned char*) array) + JNT_ARRAY_DATA_OFFSET(JNTIntArray, jint));
    return (jobject) array;
}

finline jobject newLongArray(const jsize length) {
    JNTLongArray *array = jnt_alloc_custom_array(
            sizeof(JNTLongArray),
            _Alignof(jlong),
            sizeof(jlong),
            length
    );
    if (array == NULL) {
        return NULL;
    }
    array->data = (jlong*) (((unsigned char*) array) + JNT_ARRAY_DATA_OFFSET(JNTLongArray, jlong));
    return (jobject) array;
}

finline jobject newFloatArray(const jsize length) {
    JNTFloatArray *array = jnt_alloc_custom_array(
            sizeof(JNTFloatArray),
            _Alignof(jfloat),
            sizeof(jfloat),
            length
    );
    if (array == NULL) {
        return NULL;
    }
    array->data = (jfloat*) (((unsigned char*) array) + JNT_ARRAY_DATA_OFFSET(JNTFloatArray, jfloat));
    return (jobject) array;
}

finline jobject newDoubleArray(const jsize length) {
    JNTDoubleArray *array = jnt_alloc_custom_array(
            sizeof(JNTDoubleArray),
            _Alignof(jdouble),
            sizeof(jdouble),
            length
    );
    if (array == NULL) {
        return NULL;
    }
    array->data = (jdouble*) (((unsigned char*) array) + JNT_ARRAY_DATA_OFFSET(JNTDoubleArray, jdouble));
    return (jobject) array;
}

finline jsize getArrayLength(const void *arr) {
    if (arr == NULL) return 0;
    const JNTArrayHeader *h = arr;
    return h->length;
}

jboolean isCustomArrayPtr(const void *arr) {
    if (arr == NULL || jnt_current_array_scope == NULL) {
        return JNI_FALSE;
    }

    for (JNTArrayHeader *header = jnt_current_array_scope->head; header != NULL; header = header->scope_next) {
        if ((const void*) header == arr) {
            return JNI_TRUE;
        }
    }

    return JNI_FALSE;
}

void retainArrayPtr(void *arr) {
    if (arr == NULL) {
        return;
    }

    JNTArrayHeader *header = arr;
    if (header->magic != JNTA_MAGIC) {
#ifndef NDEBUG
        fprintf(stderr, "retainArrayPtr: invalid JNTArray pointer %p\n", arr);
#endif
        return;
    }

    header->refcount += 1u;
}

void releaseArrayPtr(void *arr) {
    if (arr == NULL) {
        return;
    }

    JNTArrayHeader *header = arr;
    if (header->magic != JNTA_MAGIC) {
#ifndef NDEBUG
        fprintf(stderr, "releaseArrayPtr: invalid JNTArray pointer %p\n", arr);
#endif
        return;
    }

#ifndef NDEBUG
    if (header->refcount == 0u) {
        fprintf(stderr, "releaseArrayPtr: refcount underflow for %p\n", arr);
        return;
    }
#endif

    header->refcount -= 1u;
    if (header->refcount == 0u) {
        jnt_array_scope_unlink(header);
        jnt_force_free_array(header);
    }
}

void freeArrayPtr(void *arr) {
    if (arr == NULL) {
        return;
    }

    JNTArrayHeader *header = arr;
    if (header->magic != JNTA_MAGIC) {
#ifndef NDEBUG
        fprintf(stderr, "freeArrayPtr: invalid JNTArray pointer %p\n", arr);
#endif
        return;
    }

    jnt_array_scope_unlink(header);
    jnt_force_free_array(header);
}

finline void freeArray(jvalue v) {
    void *p = NULL;
#ifdef __SIZEOF_POINTER__
    p = (void*)v.j;
    if (!p) p = v.l;
#else
    p = v.l;
#endif
    releaseArrayPtr(p);
}

finline jbyteArray JNTByteArray_to_jByteArray(JNIEnv* env, void* arr) {
    if (arr != NULL) {
        jbyteArray temp = JNT_JNI_CALL(
                env,
                JNT_MAKE_MARKER(JNT_BINDING_NEW_BYTE_ARRAY, UINT64_C(0x2101)),
                (jbyteArray (*)(JNIEnv *, jsize)),
                env,
                getArrayLength(arr)
        );
        JNT_JNI_CALL(
                env,
                JNT_MAKE_MARKER(JNT_BINDING_SET_BYTE_ARRAY_REGION, UINT64_C(0x2102)),
                (void (*)(JNIEnv *, jbyteArray, jsize, jsize, const jbyte *)),
                env,
                temp,
                0,
                getArrayLength(arr),
                ((JNTByteArray*) arr)->data
        );
        return temp;
    }
    return NULL;
}

finline void jByteArray_to_JNTByteArray(JNIEnv* env, void *arr) {
    if (arr != NULL) {
        jsize len = JNT_JNI_CALL(
                env,
                JNT_MAKE_MARKER(JNT_BINDING_GET_ARRAY_LENGTH, UINT64_C(0x2103)),
                (jsize (*)(JNIEnv *, jarray)),
                env,
                arr
        );
        if (len > 0) {
            JNTByteArray *temp = newByteArray(len);
            JNT_JNI_CALL(
                    env,
                    JNT_MAKE_MARKER(JNT_BINDING_GET_BYTE_ARRAY_REGION, UINT64_C(0x2104)),
                    (void (*)(JNIEnv *, jbyteArray, jsize, jsize, jbyte *)),
                    env,
                    arr,
                    0,
                    len,
                    temp->data
            );
            arr = temp;
        }
    }
}

finline jclass jnt_Object_getClass(JNIEnv* env, jobject instance) {
    return JNT_JNI_CALL(
            env,
            JNT_MAKE_MARKER(JNT_BINDING_GET_OBJECT_CLASS, UINT64_C(0x2105)),
            (jclass (*)(JNIEnv *, jobject)),
            env,
            instance
    );
}

finline jint jnt_String_length(JNIEnv* env, jobject str) {
    return JNT_JNI_CALL(
            env,
            JNT_MAKE_MARKER(JNT_BINDING_GET_STRING_LENGTH, UINT64_C(0x2106)),
            (jsize (*)(JNIEnv *, jstring)),
            env,
            str
    );
}

finline jboolean jnt_String_isEmpty(JNIEnv* env, jobject instance) {
    return JNT_JNI_CALL(
            env,
            JNT_MAKE_MARKER(JNT_BINDING_GET_STRING_LENGTH, UINT64_C(0x2107)),
            (jsize (*)(JNIEnv *, jstring)),
            env,
            (jstring)instance
    ) == 0;
}

// jboolean jnt_String_equals(JNIEnv* env, jobject a, jstring b) {
//    // Must be handled a little differently.
//    const char* strA = (*env)->GetStringUTFChars(env, (jstring)a, 0);
//    const char* strB = (*env)->GetStringUTFChars(env, b, 0);
//
//    jboolean result = (strcmp(strA, strB) == 0) ? JNI_TRUE : JNI_FALSE;
//
//    (*env)->ReleaseStringUTFChars(env, (jstring)a, strA);
//    (*env)->ReleaseStringUTFChars(env, b, strB);
//
//    return result;
// }

void stackframe() {
    stackframe();
}

finline void jnt_Runtime_halt(JNIEnv* env, jint status) {
    // HANDLE WITH CARE, compilers optimize this out
    //  (-O2, -foptimize-sibling-calls)

    stackframe(); // Too many stack frames = stack overflow = segmentation fault.
}

finline void jnt_System_exit(JNIEnv* env, jint status) {
    exit(status);
}

// Abs ===
finline jint jnt_Math_abs_int(JNIEnv* env, jint a) {
    return (a < 0) ? -a : a;
}

finline jfloat jnt_Math_abs_float(JNIEnv* env, jfloat a) {
    return (a <= 0.0) ? 0.0 - a : a;
}

finline jlong jnt_Math_abs_long(JNIEnv* env, jlong a) {
    return (a < 0) ? -a : a;
}

finline jdouble jnt_Math_abs_double(JNIEnv* env, jdouble a) {
    return (a <= 0.0) ? 0.0 - a : a;
}

// Min ===
finline jint jnt_Math_min_int(JNIEnv* env, jint a, jint b) {
    return (a < b) ? a : b;
}

finline jlong jnt_Math_min_long(JNIEnv* env, jlong a, jlong b) {
    return (a < b) ? a : b;
}

finline jfloat jnt_Math_min_float(JNIEnv* env, jfloat a, jfloat b) {
    return (a < b) ? a : b;
}

finline jdouble jnt_Math_min_double(JNIEnv* env, jdouble a, jdouble b) {
    return (a < b) ? a : b;
}

// Max ===
finline jint jnt_Math_max_int(JNIEnv* env, jint a, jint b) {
    return (a > b) ? a : b;
}

finline jlong jnt_Math_max_long(JNIEnv* env, jlong a, jlong b) {
    return (a > b) ? a : b;
}

finline jfloat jnt_Math_max_float(JNIEnv* env, jfloat a, jfloat b) {
    return (a > b) ? a : b;
}

finline jdouble jnt_Math_max_double(JNIEnv* env, jdouble a, jdouble b) {
    return (a > b) ? a : b;
}

// Sqrt ===
finline jdouble jnt_Math_sqrt_double(JNIEnv* env, jdouble a) {
    return sqrt(a);
}

// Pow ===
finline jdouble jnt_Math_pow_double(JNIEnv* env, jdouble a, jdouble b) {
    return pow(a, b);
}

// java.lang.Math#round(float)
finline jint jnt_Math_round_float(JNIEnv* env, jfloat a) {
    if (isnan(a) || isinf(a) || fabs(a) >= (1 << (24 - 1))) {
        return (jint)a;
    }

    int32_t intBits = *(int32_t*)&a;
    int32_t biasedExp = (intBits & 0x7F800000) >> (24 - 1);
    int32_t shift = (24 - 2 + 127) - biasedExp;

    if ((shift & -32) == 0) {
        int32_t r = (intBits & 0x007FFFFF) | (0x007FFFFF + 1);
        if (intBits < 0) {
            r = -r;
        }
        return ((r >> shift) + 1) >> 1;
    } else {
        return (jint)a;
    }
}

// java.lang.Math#round(double)
finline jlong jnt_Math_round_double(JNIEnv* env, jdouble a) {
    if (isnan(a) || isinf(a) || fabs(a) >= (1LL << (53 - 1))) {
        return (jlong)a;
    }

    int64_t longBits = *(int64_t*)&a;
    int64_t biasedExp = (longBits & 0x7FF0000000000000LL) >> (53 - 1);
    int64_t shift = (53 - 2 + 1023) - biasedExp;

    if ((shift & -64) == 0) {
        int64_t r = (longBits & 0x000FFFFFFFFFFFFFLL) | (0x000FFFFFFFFFFFFFLL + 1);
        if (longBits < 0) {
            r = -r;
        }
        return ((r >> shift) + 1) >> 1;
    } else {
        return (jlong)a;
    }
}

finline char* jstr_to_utf8(const jchar* input, int len) {
    if (!input || len <= 0) {
        static char empty[] = "";
        return empty;
    }

    size_t length = (size_t)len;

    size_t max_utf8 = length * 3 + 1;
    char* out = (char*)malloc(max_utf8);
    if (!out) return NULL;

    size_t out_idx = 0;

    for (size_t i = 0; i < length; i++) {
        jchar ch = input[i];

        if (ch == 0x0000) {
            out[out_idx++] = (char)0xC0;
            out[out_idx++] = (char)0x80;
        }
        else if (ch < 0x80) {
            out[out_idx++] = (char)ch;
        }
        else if (ch < 0x800) {
            out[out_idx++] = (char)(0xC0 | (ch >> 6));
            out[out_idx++] = (char)(0x80 | (ch & 0x3F));
        }
        else {
            out[out_idx++] = (char)(0xE0 | (ch >> 12));
            out[out_idx++] = (char)(0x80 | ((ch >> 6) & 0x3F));
            out[out_idx++] = (char)(0x80 | (ch & 0x3F));
        }
    }

    out[out_idx] = '\0';
    return out;
}
