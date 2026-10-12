package hack.echo.loader;

import net.fabricmc.api.ModInitializer;

public class EchoLoader implements ModInitializer {

    public static String initClass = "hack.echo.client.Echo";

    public void onInitialize() {
        try {
            Class <?> clazz = Class.forName(initClass);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            clazz.getDeclaredMethod("onInitialize").invoke(instance);
        } catch (Exception e) {
            //System.out.println("Failed to initialize Echo:");
            //e.printStackTrace();
        }
    }
}
