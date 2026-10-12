package hack.echo.backend.util;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

public class EncryptionUtil {

    public static SecretKey getKeyFromPassword(String password, String salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 2048, 128);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    public static byte[] encrypt(byte[] data, SecretKey key) {
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            byte[] encryptedBytes = CipherUtil.encrypt(data, key.getEncoded(), iv);

            for (int i = 0; i < iv.length; i++) {
                iv[i] = (byte) (iv[i] ^ 0x24);
            }

            int randomPos = new SecureRandom().nextInt(5, encryptedBytes.length) & Integer.MAX_VALUE;

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedBytes.length + 4);
            buffer.put(encryptedBytes, 0, 1);
            buffer.putInt((randomPos ^ 36) << 1);
            buffer.put(encryptedBytes, 1, randomPos - 1);
            buffer.put(iv);
            buffer.put(encryptedBytes, randomPos, encryptedBytes.length - randomPos);

            return buffer.array();
        } catch (Exception ignored) {}

        return data;
    }

    public static byte[] decrypt(byte[] data, SecretKey key) {
        try {
            ByteBuffer reader = ByteBuffer.wrap(data);

            byte b = reader.get();
            int pos = ((reader.getInt() >> 1) ^ 36);
            byte[] part = new byte[pos - 1];
            reader.get(part);

            byte[] iv = new byte[16];
            reader.get(iv);

            for (int i = 0; i < iv.length; i++) {
                iv[i] = (byte) (iv[i] ^ 0x24);
            }

            ByteBuffer buffer = ByteBuffer.allocate(data.length - 20);
            buffer.put(b);
            buffer.put(part);
            buffer.put(reader);

            return CipherUtil.decrypt(buffer.array(), key.getEncoded(), iv);
        } catch (Exception e) {
            //e.printStackTrace();
        }

        return data;
    }

    public static byte[] encrypt(byte[] data, String key, boolean customIV) {
        try {
            byte[] iv = new byte[16];
            if (customIV) {
                new SecureRandom().nextBytes(iv);
            } else {
                iv = "x$,kjSOcOTGky6R,".getBytes();
            }
            SecretKey secretKey = getKeyFromPassword(key, "EchoUw!*R9C&.c:/\"?}pjJCL!jM/QZ]:");
            byte[] encryptedBytes = CipherUtil.encrypt(data, secretKey.getEncoded(), iv);

            if (customIV) {
                for (int i = 0; i < iv.length; i++) {
                    iv[i] = (byte) (iv[i] ^ 0x24);
                }

                int randomPos = new SecureRandom().nextInt(5, encryptedBytes.length) & Integer.MAX_VALUE;

                ByteBuffer buffer = ByteBuffer.allocate(iv.length + encryptedBytes.length + 4);
                buffer.put(encryptedBytes, 0, 1);
                buffer.putInt((randomPos ^ 36) << 1);
                buffer.put(encryptedBytes, 1, randomPos - 1);
                buffer.put(iv);
                buffer.put(encryptedBytes, randomPos, encryptedBytes.length - randomPos);

                return buffer.array();
            } else {
                return encryptedBytes;
            }
        } catch (Exception ignored) {}

        return data;
    }

    public static byte[] decrypt(byte[] data, String key) {
        try {
            ByteBuffer reader = ByteBuffer.wrap(data);

            byte b = reader.get();
            int pos = ((reader.getInt() >> 1) ^ 36);
            byte[] part = new byte[pos - 1];
            reader.get(part);

            byte[] iv = new byte[16];
            reader.get(iv);

            for (int i = 0; i < iv.length; i++) {
                iv[i] = (byte) (iv[i] ^ 0x24);
            }

            SecretKey secretKey = getKeyFromPassword(key, "EchoUw!*R9C&.c:/\"?}pjJCL!jM/QZ]:");

            ByteBuffer buffer = ByteBuffer.allocate(data.length - 20);
            buffer.put(b);
            buffer.put(part);
            buffer.put(reader);

            return CipherUtil.decrypt(buffer.array(), secretKey.getEncoded(), iv);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return data;
    }
}
