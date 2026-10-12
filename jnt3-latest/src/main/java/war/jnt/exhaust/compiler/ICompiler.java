package war.jnt.exhaust.compiler;

import war.configuration.ConfigurationSection;

public interface ICompiler {
    void run(ConfigurationSection cfg, String dir);

    static String windowsRuntimeLinkFlags(String target) {
        if (!target.contains("windows")) {
            return "";
        }

        return "-static -static-libgcc -Wl,-Bstatic -lgcc -lgcc_eh -lssp -lwinpthread -Wl,-Bdynamic ";
    }
}
