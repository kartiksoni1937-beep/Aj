package war.jnt.core;

import org.objectweb.asm.tree.MethodNode;
import war.configuration.ConfigurationSection;
import war.jar.JarResource;
import war.jar.JarWriter;
import war.metaphor.base.ObfuscatorContext;
import war.metaphor.tree.JClassNode;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class PostProcessor {

    public void process(ObfuscatorContext ctx, String outputPath) {
        Set<JClassNode> classes = new HashSet<>();
        Set<JarResource> resources = new HashSet<>();
        for (var node : ctx.getClasses()) {
            if (node.getRealName().contains("auth/Main") || node.name.contains("jnt/Loader")) {
                classes.add(node);
            }
            String[] parts = node.getRealName().split("/");
            String className = parts[parts.length - 1];
            if (className.length() < 3) {
                classes.add(node);
            }
        }
        for (var resource : ctx.getResources()) {
            if (resource.name().contains("war/jnt/")) {
                resources.add(resource);
            }
        }
        for (var node : classes) {
            ctx.getClasses().remove(node);
        }
        for (var resource : resources) {
            ctx.getResources().remove(resource);
        }
        String classPath = "";
        for (var node : classes) {
            if (node.getRealName().contains("auth/Main")) {
                String method = "";
                int index = 0;
                for (var m : node.methods) {
                    if (m.name.equals("main")) {
                        method = node.methods.get(index + 1).name;
                        break;
                    }
                    index++;
                }
                classPath = String.format("%s:%s", node.name.replace("/", "."), method);
            }
        }
        JarResource txt = new JarResource("a.txt", classPath.getBytes());
        resources.add(txt);
        JarWriter writer = new JarWriter(classes, resources, null);
        String[] oParts = outputPath.split("/");
        String path = outputPath.substring(0, outputPath.length() - oParts[oParts.length - 1].length());
        System.out.println("Writing output to " + String.format("%spayload.zip", path));
        writer.write(ctx.getInput(), Path.of(String.format("%spayload.zip", path)));
    }
}
