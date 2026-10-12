package war.jnt.core.code.bindings;

import war.configuration.ConfigurationSection;

public final class JniDispatchConfiguration {

    public static final String ENABLED_KEY = "mutators.jnt.jni-protection.enabled";
    public static final String LEGACY_ENABLED_KEY = "mutators.jnt.custom-call-handling.enabled";
    public static final String ENABLED_DEFINE = "JNT_JNI_PROTECTION_ENABLED";

    private static volatile boolean generationEnabled = true;

    private JniDispatchConfiguration() {
    }

    public static boolean isEnabled(ConfigurationSection config) {
        if (config == null) {
            return true;
        }
        if (config.contains(ENABLED_KEY)) {
            return config.getBoolean(ENABLED_KEY, true);
        }
        if (config.contains(LEGACY_ENABLED_KEY)) {
            return config.getBoolean(LEGACY_ENABLED_KEY, true);
        }
        return true;
    }

    public static void configure(ConfigurationSection config) {
        generationEnabled = isEnabled(config);
    }

    public static boolean isGenerationEnabled() {
        return generationEnabled;
    }

    public static String emitCHeader(boolean enabled) {
        return """
                #ifndef JNT_JNI_DISPATCH_CONFIG_H
                #define JNT_JNI_DISPATCH_CONFIG_H

                #define %s %d

                #endif
                """.formatted(ENABLED_DEFINE, enabled ? 1 : 0);
    }
}
