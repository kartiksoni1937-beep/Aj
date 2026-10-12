package war.jnt.core.code.bindings;

public enum JniBindingId {
    FIND_CLASS(1, "FindClass", 6, "jclass"),
    THROW(2, "Throw", 13, "jint"),
    THROW_NEW(3, "ThrowNew", 14, "jint"),
    EXCEPTION_OCCURRED(4, "ExceptionOccurred", 15, "jthrowable"),
    EXCEPTION_DESCRIBE(5, "ExceptionDescribe", 16, "void"),
    EXCEPTION_CLEAR(6, "ExceptionClear", 17, "void"),
    EXCEPTION_CHECK(7, "ExceptionCheck", -1, "jboolean"),
    PUSH_LOCAL_FRAME(8, "PushLocalFrame", 19, "jint"),
    POP_LOCAL_FRAME(9, "PopLocalFrame", 20, "jobject"),
    NEW_GLOBAL_REF(10, "NewGlobalRef", 21, "jobject"),
    DELETE_LOCAL_REF(11, "DeleteLocalRef", 23, "void"),
    IS_SAME_OBJECT(12, "IsSameObject", 24, "jboolean"),
    NEW_LOCAL_REF(13, "NewLocalRef", 25, "jobject"),
    ALLOC_OBJECT(14, "AllocObject", 27, "jobject"),
    NEW_OBJECT_ARRAY(15, "NewObjectArray", 172, "jobjectArray"),
    GET_OBJECT_CLASS(16, "GetObjectClass", 31, "jclass"),
    IS_INSTANCE_OF(17, "IsInstanceOf", 32, "jboolean"),
    GET_METHOD_ID(18, "GetMethodID", 33, "jmethodID"),
    CALL_OBJECT_METHOD(19, "CallObjectMethod", 34, "jobject"),
    CALL_BOOLEAN_METHOD(20, "CallBooleanMethod", 37, "jboolean"),
    CALL_BYTE_METHOD(21, "CallByteMethod", 40, "jbyte"),
    CALL_CHAR_METHOD(22, "CallCharMethod", 43, "jchar"),
    CALL_SHORT_METHOD(23, "CallShortMethod", 46, "jshort"),
    CALL_INT_METHOD(24, "CallIntMethod", 49, "jint"),
    CALL_LONG_METHOD(25, "CallLongMethod", 52, "jlong"),
    CALL_FLOAT_METHOD(26, "CallFloatMethod", 55, "jfloat"),
    CALL_DOUBLE_METHOD(27, "CallDoubleMethod", 58, "jdouble"),
    CALL_OBJECT_METHOD_A(28, "CallObjectMethodA", 36, "jobject"),
    CALL_BOOLEAN_METHOD_A(29, "CallBooleanMethodA", 39, "jboolean"),
    CALL_BYTE_METHOD_A(30, "CallByteMethodA", 42, "jbyte"),
    CALL_CHAR_METHOD_A(31, "CallCharMethodA", 45, "jchar"),
    CALL_SHORT_METHOD_A(32, "CallShortMethodA", 48, "jshort"),
    CALL_INT_METHOD_A(33, "CallIntMethodA", 51, "jint"),
    CALL_LONG_METHOD_A(34, "CallLongMethodA", 54, "jlong"),
    CALL_FLOAT_METHOD_A(35, "CallFloatMethodA", 57, "jfloat"),
    CALL_DOUBLE_METHOD_A(36, "CallDoubleMethodA", 60, "jdouble"),
    CALL_VOID_METHOD_A(37, "CallVoidMethodA", 63, "void"),
    CALL_NONVIRTUAL_OBJECT_METHOD_A(38, "CallNonvirtualObjectMethodA", 66, "jobject"),
    CALL_NONVIRTUAL_BOOLEAN_METHOD_A(39, "CallNonvirtualBooleanMethodA", 69, "jboolean"),
    CALL_NONVIRTUAL_BYTE_METHOD_A(40, "CallNonvirtualByteMethodA", 72, "jbyte"),
    CALL_NONVIRTUAL_CHAR_METHOD_A(41, "CallNonvirtualCharMethodA", 75, "jchar"),
    CALL_NONVIRTUAL_SHORT_METHOD_A(42, "CallNonvirtualShortMethodA", 78, "jshort"),
    CALL_NONVIRTUAL_INT_METHOD_A(43, "CallNonvirtualIntMethodA", 81, "jint"),
    CALL_NONVIRTUAL_LONG_METHOD_A(44, "CallNonvirtualLongMethodA", 84, "jlong"),
    CALL_NONVIRTUAL_FLOAT_METHOD_A(45, "CallNonvirtualFloatMethodA", 87, "jfloat"),
    CALL_NONVIRTUAL_DOUBLE_METHOD_A(46, "CallNonvirtualDoubleMethodA", 90, "jdouble"),
    CALL_NONVIRTUAL_VOID_METHOD_A(47, "CallNonvirtualVoidMethodA", 93, "void"),
    GET_FIELD_ID(48, "GetFieldID", 94, "jfieldID"),
    GET_OBJECT_FIELD(49, "GetObjectField", 95, "jobject"),
    GET_BOOLEAN_FIELD(50, "GetBooleanField", 96, "jboolean"),
    GET_BYTE_FIELD(51, "GetByteField", 97, "jbyte"),
    GET_CHAR_FIELD(52, "GetCharField", 98, "jchar"),
    GET_SHORT_FIELD(53, "GetShortField", 99, "jshort"),
    GET_INT_FIELD(54, "GetIntField", 100, "jint"),
    GET_LONG_FIELD(55, "GetLongField", 101, "jlong"),
    GET_FLOAT_FIELD(56, "GetFloatField", 102, "jfloat"),
    GET_DOUBLE_FIELD(57, "GetDoubleField", 103, "jdouble"),
    SET_OBJECT_FIELD(58, "SetObjectField", 104, "void"),
    SET_BOOLEAN_FIELD(59, "SetBooleanField", 105, "void"),
    SET_BYTE_FIELD(60, "SetByteField", 106, "void"),
    SET_CHAR_FIELD(61, "SetCharField", 107, "void"),
    SET_SHORT_FIELD(62, "SetShortField", 108, "void"),
    SET_INT_FIELD(63, "SetIntField", 109, "void"),
    SET_LONG_FIELD(64, "SetLongField", 110, "void"),
    SET_FLOAT_FIELD(65, "SetFloatField", 111, "void"),
    SET_DOUBLE_FIELD(66, "SetDoubleField", 112, "void"),
    GET_STATIC_METHOD_ID(67, "GetStaticMethodID", 113, "jmethodID"),
    CALL_STATIC_OBJECT_METHOD(68, "CallStaticObjectMethod", 114, "jobject"),
    CALL_STATIC_OBJECT_METHOD_A(69, "CallStaticObjectMethodA", 116, "jobject"),
    CALL_STATIC_BOOLEAN_METHOD_A(70, "CallStaticBooleanMethodA", 119, "jboolean"),
    CALL_STATIC_BYTE_METHOD_A(71, "CallStaticByteMethodA", 122, "jbyte"),
    CALL_STATIC_CHAR_METHOD_A(72, "CallStaticCharMethodA", 125, "jchar"),
    CALL_STATIC_SHORT_METHOD_A(73, "CallStaticShortMethodA", 128, "jshort"),
    CALL_STATIC_INT_METHOD_A(74, "CallStaticIntMethodA", 131, "jint"),
    CALL_STATIC_LONG_METHOD_A(75, "CallStaticLongMethodA", 134, "jlong"),
    CALL_STATIC_FLOAT_METHOD_A(76, "CallStaticFloatMethodA", 137, "jfloat"),
    CALL_STATIC_DOUBLE_METHOD_A(77, "CallStaticDoubleMethodA", 140, "jdouble"),
    CALL_STATIC_VOID_METHOD_A(78, "CallStaticVoidMethodA", 143, "void"),
    GET_STATIC_FIELD_ID(79, "GetStaticFieldID", 144, "jfieldID"),
    GET_STATIC_OBJECT_FIELD(80, "GetStaticObjectField", 145, "jobject"),
    GET_STATIC_BOOLEAN_FIELD(81, "GetStaticBooleanField", 146, "jboolean"),
    GET_STATIC_BYTE_FIELD(82, "GetStaticByteField", 147, "jbyte"),
    GET_STATIC_CHAR_FIELD(83, "GetStaticCharField", 148, "jchar"),
    GET_STATIC_SHORT_FIELD(84, "GetStaticShortField", 149, "jshort"),
    GET_STATIC_INT_FIELD(85, "GetStaticIntField", 150, "jint"),
    GET_STATIC_LONG_FIELD(86, "GetStaticLongField", 151, "jlong"),
    GET_STATIC_FLOAT_FIELD(87, "GetStaticFloatField", 152, "jfloat"),
    GET_STATIC_DOUBLE_FIELD(88, "GetStaticDoubleField", 153, "jdouble"),
    SET_STATIC_OBJECT_FIELD(89, "SetStaticObjectField", 154, "void"),
    SET_STATIC_BOOLEAN_FIELD(90, "SetStaticBooleanField", 155, "void"),
    SET_STATIC_BYTE_FIELD(91, "SetStaticByteField", 156, "void"),
    SET_STATIC_CHAR_FIELD(92, "SetStaticCharField", 157, "void"),
    SET_STATIC_SHORT_FIELD(93, "SetStaticShortField", 158, "void"),
    SET_STATIC_INT_FIELD(94, "SetStaticIntField", 159, "void"),
    SET_STATIC_LONG_FIELD(95, "SetStaticLongField", 160, "void"),
    SET_STATIC_FLOAT_FIELD(96, "SetStaticFloatField", 161, "void"),
    SET_STATIC_DOUBLE_FIELD(97, "SetStaticDoubleField", 162, "void"),
    NEW_STRING(98, "NewString", 163, "jstring"),
    NEW_STRING_UTF(99, "NewStringUTF", 167, "jstring"),
    GET_ARRAY_LENGTH(100, "GetArrayLength", 171, "jsize"),
    GET_OBJECT_ARRAY_ELEMENT(101, "GetObjectArrayElement", 173, "jobject"),
    SET_OBJECT_ARRAY_ELEMENT(102, "SetObjectArrayElement", 174, "void"),
    NEW_BOOLEAN_ARRAY(103, "NewBooleanArray", 175, "jbooleanArray"),
    NEW_BYTE_ARRAY(104, "NewByteArray", 176, "jbyteArray"),
    NEW_CHAR_ARRAY(105, "NewCharArray", 177, "jcharArray"),
    NEW_SHORT_ARRAY(106, "NewShortArray", 178, "jshortArray"),
    NEW_INT_ARRAY(107, "NewIntArray", 179, "jintArray"),
    NEW_LONG_ARRAY(108, "NewLongArray", 180, "jlongArray"),
    NEW_FLOAT_ARRAY(109, "NewFloatArray", 181, "jfloatArray"),
    NEW_DOUBLE_ARRAY(110, "NewDoubleArray", 182, "jdoubleArray"),
    GET_BOOLEAN_ARRAY_REGION(111, "GetBooleanArrayRegion", 199, "void"),
    GET_BYTE_ARRAY_REGION(112, "GetByteArrayRegion", 200, "void"),
    GET_CHAR_ARRAY_REGION(113, "GetCharArrayRegion", 201, "void"),
    GET_SHORT_ARRAY_REGION(114, "GetShortArrayRegion", 202, "void"),
    GET_INT_ARRAY_REGION(115, "GetIntArrayRegion", 203, "void"),
    GET_LONG_ARRAY_REGION(116, "GetLongArrayRegion", 204, "void"),
    GET_FLOAT_ARRAY_REGION(117, "GetFloatArrayRegion", 205, "void"),
    GET_DOUBLE_ARRAY_REGION(118, "GetDoubleArrayRegion", 206, "void"),
    SET_BOOLEAN_ARRAY_REGION(119, "SetBooleanArrayRegion", 207, "void"),
    SET_BYTE_ARRAY_REGION(120, "SetByteArrayRegion", 208, "void"),
    SET_CHAR_ARRAY_REGION(121, "SetCharArrayRegion", 209, "void"),
    SET_SHORT_ARRAY_REGION(122, "SetShortArrayRegion", 210, "void"),
    SET_INT_ARRAY_REGION(123, "SetIntArrayRegion", 211, "void"),
    SET_LONG_ARRAY_REGION(124, "SetLongArrayRegion", 212, "void"),
    SET_FLOAT_ARRAY_REGION(125, "SetFloatArrayRegion", 213, "void"),
    SET_DOUBLE_ARRAY_REGION(126, "SetDoubleArrayRegion", 214, "void"),
    MONITOR_ENTER(127, "MonitorEnter", 217, "jint"),
    MONITOR_EXIT(128, "MonitorExit", 218, "jint"),
    GET_STRING_LENGTH(129, "GetStringLength", 164, "jsize");

    private final int id;
    private final String functionName;
    private final int slotIndex;
    private final String returnType;

    JniBindingId(int id, String functionName, int slotIndex, String returnType) {
        this.id = id;
        this.functionName = functionName;
        this.slotIndex = slotIndex;
        this.returnType = returnType;
    }

    public int id() {
        return id;
    }

    public String functionName() {
        return functionName;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public String returnType() {
        return returnType;
    }

    public String cDefineName() {
        return "JNT_BINDING_" + name();
    }
}
