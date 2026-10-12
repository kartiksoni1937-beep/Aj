package hack.echo.loader.classloader;

import hack.echo.loader.PreEcholoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.lib.classtweaker.api.ClassTweakerReader;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

public class ModLoader {

    public CustomClassLoader createClassLoader() {
        try {
            ClassLoader loader = PreEcholoader.class.getClassLoader();

            Field delegatefield = loader.getClass().getDeclaredField("delegate");
            delegatefield.setAccessible(true);
            Object delegate = delegatefield.get(loader);

            Field originalLoaderField = loader.getClass().getDeclaredField("originalLoader");
            originalLoaderField.setAccessible(true);
            ClassLoader originalLoader = (ClassLoader) originalLoaderField.get(loader);

            Field parentClassLoaderField = delegate.getClass().getDeclaredField("parentClassLoader");
            parentClassLoaderField.setAccessible(true);
            ClassLoader parentClassLoader = (ClassLoader) parentClassLoaderField.get(delegate);

            CustomClassLoader cl = new CustomClassLoader(parentClassLoader, originalLoader);
            originalLoaderField.set(loader, cl);
            parentClassLoaderField.set(delegate, cl);

            return cl;
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public void finishModLoading() {
        String config = "echo.mixins.json";
        try {
            Mixins.addConfiguration(config);
            GlobalProperties.Keys key = GlobalProperties.Keys.of(GlobalProperties.Keys.CONFIGS + ".queue");
            Set<Config> mixinConfigs = GlobalProperties.get(key);
            mixinConfigs.add(Config.create(config, MixinEnvironment.getDefaultEnvironment()));
            GlobalProperties.put(key, mixinConfigs);
        } catch (Throwable t) {
            throw new RuntimeException(String.format("Error creating Mixin config %s", config), t);
        }
    }

    public void loadAccessWidener(CustomClassLoader classLoader, String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return;
        }

        try (InputStream inputStream = classLoader.localResourceProvider(resourcePath)) {
            if (inputStream == null) {
                throw new RuntimeException("Missing access widener " + resourcePath);
            }

            byte[] bytes = inputStream.readAllBytes();
            String runtimeNamespace = FabricLauncherBase.getLauncher()
                    .getMappingConfiguration()
                    .getRuntimeNamespace();

            ClassTweakerReader reader = ClassTweakerReader.create(FabricLoaderImpl.INSTANCE.getClassTweaker());
            reader.read(bytes, runtimeNamespace);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read access widener " + resourcePath, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load access widener " + resourcePath, e);
        }
    }

    public static void forceMixinEntry() {
        try {
            Object transformer;
            ClassLoader knotClassLoader = FabricLauncherBase.getLauncher().getTargetClassLoader();
            Method getDelegate = knotClassLoader.getClass().getDeclaredMethod("getDelegate");
            getDelegate.setAccessible(true);
            Object knotDelegate = getDelegate.invoke(knotClassLoader);
            Field mixinTransformerField = knotDelegate.getClass().getDeclaredField("mixinTransformer");
            mixinTransformerField.setAccessible(true);
            Object mixinTransformer = transformer = mixinTransformerField.get(knotDelegate);
            if (!transformer.getClass().getName().equals("org.spongepowered.asm.mixin.transformer.MixinTransformer")) {
                for (Field field : transformer.getClass().getDeclaredFields()) {
                    if (!field.getType().getName().equals("org.spongepowered.asm.mixin.transformer.IMixinTransformer"))
                        continue;
                    field.setAccessible(true);
                    mixinTransformer = field.get(transformer);
                    break;
                }
            }
            Field processorField = mixinTransformer.getClass().getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(mixinTransformer);
            MixinEnvironment currentEnvironment = MixinEnvironment.getCurrentEnvironment();
            Class<?> processorClazz = processor.getClass();
            Method selectConfigsMethod = processorClazz.getDeclaredMethod("selectConfigs", MixinEnvironment.class);
            selectConfigsMethod.setAccessible(true);
            selectConfigsMethod.invoke(processor, currentEnvironment);
            try {
                Method prepareConfigsMethod = processorClazz.getDeclaredMethod("prepareConfigs", MixinEnvironment.class, Extensions.class);
                prepareConfigsMethod.setAccessible(true);
                Field extensionsField = processorClazz.getDeclaredField("extensions");
                extensionsField.setAccessible(true);
                Object extensions = extensionsField.get(processor);
                prepareConfigsMethod.invoke(processor, currentEnvironment, extensions);
            } catch (NoSuchMethodException prepareConfigsMethod) {
                Method prepareConfigsMethod2 = processorClazz.getDeclaredMethod("prepareConfigs", MixinEnvironment.class);
                prepareConfigsMethod2.setAccessible(true);
                prepareConfigsMethod2.invoke(processor, currentEnvironment);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup Mixin environment", e);
        }
    }
}