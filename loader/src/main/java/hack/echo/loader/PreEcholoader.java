package hack.echo.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import hack.echo.loader.auth.LoadingFrame;
import hack.echo.loader.auth.LoginFrame;
import hack.echo.loader.classloader.CustomClassLoader;
import hack.echo.loader.classloader.ModLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PreEcholoader implements PreLaunchEntrypoint {

    public static String initClass = "hack.echo.client.EchoPreLaunch";
    public static String initAuthClass = "hack.echo.auth.Main:init";

    @Override
    public void onPreLaunch() {
        try {
            String prehwid = System.getenv("PROCESSOR_IDENTIFIER") + System.getenv("COMPUTERNAME") + "EC" + System.getProperty("user.name");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prehwid.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String shwid = hexString.toString();

            byte[] payload = null;
            try {
                //download from url
                URL url = new URI("https://api.echoclient.net/api/payload").toURL();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("X-HWID", shwid);
                try (InputStream in = connection.getInputStream()) {
                    payload = in.readAllBytes();
                } finally {
                    connection.disconnect();
                }
            } catch (IOException e) {
                //throw new RuntimeException(e);
            }

            if (payload == null) {
                return;
            }

            ModLoader modLoader = new ModLoader();
            CustomClassLoader classLoader = modLoader.createClassLoader();
            ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(payload));
            for (ZipEntry entry = zipInputStream.getNextEntry(); entry != null; entry = zipInputStream.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                //System.out.println("Found entry in payload: " + entry.getName());
                if (entry.getName().equals("war/jnt/Loader.class")) {
                    //System.out.println("Loading Loader.class from payload");
                    classLoader.loadClass("war/jnt/Loader.class", zipInputStream.readAllBytes());
                } else if (entry.getName().endsWith(".class")) {
                    //System.out.println("Found class in payload: " + String.format("%s.class", entry.getName().replace(".class", "").replace(".", "/")));
                    classLoader.loadClass(String.format("%s.class", entry.getName().replace(".class", "").replace(".", "/")), zipInputStream.readAllBytes());
                } else if (entry.getName().equals("a.txt")) {
                    //System.out.println("Found auth class name in payload");
                    initAuthClass = new String(zipInputStream.readAllBytes()).trim();
                } else {
                    classLoader.storeResource(entry.getName(), zipInputStream.readAllBytes());
                }
            }

            //classLoader.loadClass("hack/echo/auth/Main.class", authClassBytes);
            if (!new LoadingFrame(classLoader).waitForLogin().join()) {
                while (!new LoginFrame(classLoader).waitForLogin().get()) {
                    Thread.sleep(1000);
                }
            }

            /*byte[] payload = null;
            try {
                payload = new FileInputStream("../Echo/versions/1.21.11/build/devlibs/echo-mc1.21.11-b0.0.1-dev.jar").readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (payload == null) {
                return;
            }

            classLoader.load(payload);*/
            String jsonString;
            try (InputStream is = classLoader.localResourceProvider("fabric.mod.json")) {
                if (is == null) {
                    //System.out.println("Failed to find fabric.mod.json in mod jar");
                    return;
                }

                jsonString = new String(is.readAllBytes());
            }

            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(jsonString, JsonObject.class);
            initClass = jsonObject.getAsJsonObject("entrypoints")
                    .getAsJsonArray("preLaunch")
                    .get(0)
                    .getAsString();
            EchoLoader.initClass = jsonObject.getAsJsonObject("entrypoints")
                    .getAsJsonArray("main")
                    .get(0)
                    .getAsString();
            String accessWidenerPath = readOptionalString(jsonObject, "accessWidener");

            if (accessWidenerPath == null) {
                accessWidenerPath = readOptionalString(jsonObject, "classTweaker");
            }

            modLoader.loadAccessWidener(classLoader, accessWidenerPath);

            //System.out.println(jsonString);

            Class <?> clazz = Class.forName(initClass);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            clazz.getDeclaredMethod("onPreLaunch").invoke(instance);

            //System.out.println("preload complete, loading mods...");

            ModLoader.forceMixinEntry();
            modLoader.finishModLoading();
        } catch (Exception e) {
            //System.out.println("Failed to load Echo:");
            //e.printStackTrace();
        }
    }

    private static String readOptionalString(JsonObject jsonObject, String key) {
        if (!jsonObject.has(key)) {
            return null;
        }

        if (jsonObject.get(key).isJsonNull()) {
            return null;
        }

        return jsonObject.get(key).getAsString();
    }
}
