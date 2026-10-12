package war.jnt.core.code.analysis;

import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.*;

public enum PrimitiveArrayKind {
    BOOLEAN("[Z", "boolean[]", "Boolean", "jboolean", "jbooleanArray", "JNTBooleanArray", "newBooleanArray", ".i"),
    BYTE("[B", "byte[]", "Byte", "jbyte", "jbyteArray", "JNTByteArray", "newByteArray", ".i"),
    CHAR("[C", "char[]", "Char", "jchar", "jcharArray", "JNTCharArray", "newCharArray", ".i"),
    SHORT("[S", "short[]", "Short", "jshort", "jshortArray", "JNTShortArray", "newShortArray", ".i"),
    INT("[I", "int[]", "Int", "jint", "jintArray", "JNTIntArray", "newIntArray", ".i"),
    LONG("[J", "long[]", "Long", "jlong", "jlongArray", "JNTLongArray", "newLongArray", ".j"),
    FLOAT("[F", "float[]", "Float", "jfloat", "jfloatArray", "JNTFloatArray", "newFloatArray", ".f"),
    DOUBLE("[D", "double[]", "Double", "jdouble", "jdoubleArray", "JNTDoubleArray", "newDoubleArray", ".d");

    private final String descriptor;
    private final String className;
    private final String callSuffix;
    private final String componentType;
    private final String jniArrayType;
    private final String customStructType;
    private final String customAllocator;
    private final String stackField;

    PrimitiveArrayKind(
            String descriptor,
            String className,
            String callSuffix,
            String componentType,
            String jniArrayType,
            String customStructType,
            String customAllocator,
            String stackField
    ) {
        this.descriptor = descriptor;
        this.className = className;
        this.callSuffix = callSuffix;
        this.componentType = componentType;
        this.jniArrayType = jniArrayType;
        this.customStructType = customStructType;
        this.customAllocator = customAllocator;
        this.stackField = stackField;
    }

    public String descriptor() {
        return descriptor;
    }

    public String className() {
        return className;
    }

    public String callSuffix() {
        return callSuffix;
    }

    public String componentType() {
        return componentType;
    }

    public String jniArrayType() {
        return jniArrayType;
    }

    public String customStructType() {
        return customStructType;
    }

    public String customAllocator() {
        return customAllocator;
    }

    public String stackField() {
        return stackField;
    }

    public String newJniArrayFunction() {
        return "New" + callSuffix + "Array";
    }

    public String getRegionFunction() {
        return "Get" + callSuffix + "ArrayRegion";
    }

    public String setRegionFunction() {
        return "Set" + callSuffix + "ArrayRegion";
    }

    public Type arrayType() {
        return Type.getType(descriptor);
    }

    public boolean matchesArrayOpcode(int opcode) {
        return switch (opcode) {
            case BALOAD, BASTORE -> this == BOOLEAN || this == BYTE;
            case CALOAD, CASTORE -> this == CHAR;
            case SALOAD, SASTORE -> this == SHORT;
            case IALOAD, IASTORE -> this == INT;
            case LALOAD, LASTORE -> this == LONG;
            case FALOAD, FASTORE -> this == FLOAT;
            case DALOAD, DASTORE -> this == DOUBLE;
            default -> false;
        };
    }

    public static PrimitiveArrayKind fromType(Type type) {
        if (type == null || type.getSort() != Type.ARRAY || type.getDimensions() != 1) {
            return null;
        }
        return fromDescriptor(type.getDescriptor());
    }

    public static PrimitiveArrayKind fromDescriptor(String desc) {
        if (desc == null) {
            return null;
        }
        for (PrimitiveArrayKind kind : values()) {
            if (kind.descriptor.equals(desc) || kind.className.equals(desc)) {
                return kind;
            }
        }
        return null;
    }

    public static PrimitiveArrayKind fromNewArrayOperand(int operand) {
        return switch (operand) {
            case T_BOOLEAN -> BOOLEAN;
            case T_BYTE -> BYTE;
            case T_CHAR -> CHAR;
            case T_SHORT -> SHORT;
            case T_INT -> INT;
            case T_LONG -> LONG;
            case T_FLOAT -> FLOAT;
            case T_DOUBLE -> DOUBLE;
            default -> null;
        };
    }

}
