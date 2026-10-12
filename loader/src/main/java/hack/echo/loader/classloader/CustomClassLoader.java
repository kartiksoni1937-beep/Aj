package hack.echo.loader.classloader;

import net.fabricmc.loader.impl.launch.knot.Knot;
import war.jnt.api.CustomArray;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.SecureClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

public class CustomClassLoader extends SecureClassLoader {

    private final ClassLoader parentClassLoader;
    private final ClassLoader originalLoader;

    private final Map<String, Class<?>> cache = new HashMap<>();
    private final Map<String, Object> storage = new HashMap<>();
    private byte[] key;
    private boolean initialized = false;

    public CustomClassLoader(ClassLoader parentClassLoader, ClassLoader originalLoader) {
        super(parentClassLoader);
        this.parentClassLoader = parentClassLoader;
        this.originalLoader = originalLoader;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            Class<?> ret = parentClassLoader.loadClass(name);
            if (ret != null) return ret;
        } catch (Exception e) {
            if (!(e instanceof ClassNotFoundException)) throw e;
        }

        return cloadClass(name);
    }

    private Class<?> cloadClass(String name) throws ClassNotFoundException {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }

        synchronized(super.getClassLoadingLock(name)) {
            String lookupName = String.format("%s.class", name.replace('.', '/'));
            if (storage.containsKey(lookupName)) {
                try {
                    byte[] bytes = (byte[]) storage.get(lookupName);
                    ClassLoader loader = Knot.getLauncher().getTargetClassLoader();
                    long l = 0x2FEB7911638998FBL;
                    if (initialized) {
                        bytes = decrypt(bytes, l);
                    }

                    Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
                    MethodHandle handle = LibraryLoader.makeMethodAccessor(m);

                    Class<?> ret = (Class<?>) handle.invokeWithArguments(loader, name, bytes, 0, bytes.length);
                    cache.put(name, ret);
                    return ret;
                } catch (Throwable e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }

        throw new ClassNotFoundException(name);
    }

    @Override
    public URL getResource(String name) {
        try {
            URL ret = originalLoader.getResource(name);
            if (ret == null) throw new RuntimeException();

            return ret;
        } catch (Exception ignored) {
            try {
                if (storage.containsKey(name)) {
                    byte[] bytes = (byte[]) storage.get(name);
                    long l = 0x2FEB7911638998FBL;
                    if (initialized) {
                        bytes = decrypt(bytes, l);
                    }

                    return (URL) makeFakeURL(bytes);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        try {
            InputStream is = super.getResourceAsStream(name);
            if (is == null) throw new RuntimeException();

            return is;
        } catch (Exception e) {
            try {
                if (storage.containsKey(name)) {
                    byte[] bytes = (byte[]) storage.get(name);
                    long l = 0x2FEB7911638998FBL;
                    if (initialized) {
                        bytes = decrypt(bytes, l);
                    }

                    return new ByteArrayInputStream(bytes);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public InputStream localResourceProvider(String name) {
        try {
            if (storage.containsKey(name)) {
                byte[] bytes = (byte[]) storage.get(name);
                long l = 0x2FEB7911638998FBL;
                if (initialized) {
                    bytes = decrypt(bytes, l);
                }

                return new ByteArrayInputStream(bytes);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private byte[] decrypt(byte[] data, long l) {
        byte[] key = this.key;
        l = l ^ 0x98CDD059A5571BFL;
        // String x$,kjSOcOTGky6R
        int aaa = -943787264;
        int k1 = aaa - 1540734344;
        int bbb = 1322138928;
        int k2 = bbb + 1257524325;
        byte[] iv = new byte[16];
        iv[0] = (byte) 207;
        iv[1] = (byte) 172;
        iv[2] = (byte) 223;
        iv[3] = (byte) 132;
        iv[4] = (byte) 229;
        iv[5] = (byte) 255;
        iv[6] = (byte) 117;
        iv[7] = (byte) 114;
        iv[8] = (byte) 59;
        iv[9] = (byte) 104;
        iv[10] = (byte) 122;
        iv[11] = (byte) 56;
        iv[12] = (byte) 123;
        iv[13] = (byte) 68;
        iv[14] = (byte) 172;
        iv[15] = (byte) 139;
        int n1 = ((k1 ^= k2) & 0xFF) - (int)l;
        if (n1 < 0) {
            n1 += 256;
        }

        int n2 = ((k1 & 0xFFFF) >>> 8) - (int)l;
        if (n2 < 0) {
            n2 += 256;
        }

        for (int i = 0; i < iv.length; i++) {
            int n3 = i % 2;
            int c = iv[i] & 0xFF;
            if (n3 == 0) {
                int out = c + n1;
                iv[i] = (byte) out;
                n1 = ((((n1 & 0xFF) >>> 3) | ((n1 & 0xFF) << 5)) ^ out) & 0xFF;
            } else {
                int out = c + n2;
                iv[i] = (byte) out;
                n2 = ((((n2 & 0xFF) << 3) | ((n2 & 0xFF) >>> 5)) ^ out) & 0xFF;
            }
        }

        byte[] derivedKey = new byte[16];
        for (int i = 0; i < key.length; i++) {
            derivedKey[i] = (byte) (key[i] ^ 0x37);
        }

        byte[] finalOut;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derivedKey, "AES"), new IvParameterSpec(iv));
            finalOut = cipher.doFinal(data);
        } catch (Exception e) {
            //e.printStackTrace();
            finalOut = new byte[0];
        }

        //System.out.println("Decryption: " + new String(finalOut));
        return finalOut;
    }

    private Object makeFakeURL(byte[] bytes) {
        try {
            return new URL(null, "https://echoclient.net/", new FakeURLStreamHandler(bytes));
        } catch(Exception ignored) {}

        return null;
    }

    public void load(byte[] payload) {
        byte[] zip = new byte[payload.length - 16];
        for (int i = 0; i < zip.length; i++) {
            zip[i] = payload[i];
        }
        key = new byte[16];
        for (int i = 0; i < 16; i++) {
            key[i] = payload[zip.length + i];
        }
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip));
        try {
            while (true) {
                var entry = zis.getNextEntry();
                if (entry == null) break;

                storeResource(entry.getName(), zis.readAllBytes());
            }
        } catch (Exception ignored) {}
        initialized = true;
    }

    public void loadClass(String name, byte[] payload) {
        storeResource(name, payload);
    }

    public void storeResource(String name, Object resource) {
        storage.put(name, resource);
    }
}