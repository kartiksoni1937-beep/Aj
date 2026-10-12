package war.jnt.core.code.bindings;

import org.objectweb.asm.tree.AbstractInsnNode;
import war.jnt.core.code.UnitContext;

import java.nio.charset.StandardCharsets;

public final class JniMarkerCodec {

    public static final long XOR_MASK_A = 0xA57C0F13E5D4A77BL;
    public static final long XOR_MASK_B = 0x9E3779B97F4A7C15L;
    public static final int ROTATE = 17;

    private JniMarkerCodec() {
    }

    public static long encode(JniBindingId bindingId, String siteKey) {
        long salt = siteSalt(siteKey);
        long payload = ((salt & 0x00FFFFFFFFFFFFFFL) << 8) | (bindingId.id() & 0xFFL);
        return Long.rotateLeft(payload ^ XOR_MASK_A, ROTATE) ^ XOR_MASK_B;
    }

    public static String literal(long marker) {
        return String.format("UINT64_C(0x%016X)", marker);
    }

    public static String literal(JniBindingId bindingId, String siteKey) {
        return literal(encode(bindingId, siteKey));
    }

    public static String siteKey(UnitContext ctx, AbstractInsnNode insn, JniBindingId bindingId, String suffix) {
        String extra = suffix == null ? "" : "#" + suffix;
        return ctx.getClassNode().name + "#" + ctx.getMethodNode().name + "." + ctx.getMethodNode().desc
                + "#" + insn.index + "#" + bindingId.name() + extra;
    }

    public static long siteSalt(String siteKey) {
        byte[] bytes = siteKey.getBytes(StandardCharsets.UTF_8);
        long hash = 0xcbf29ce484222325L;
        for (byte b : bytes) {
            hash ^= (b & 0xFFL);
            hash *= 0x100000001b3L;
        }
        return hash & 0x00FFFFFFFFFFFFFFL;
    }
}
