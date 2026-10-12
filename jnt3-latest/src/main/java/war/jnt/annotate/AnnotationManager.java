package war.jnt.annotate;

import org.objectweb.asm.tree.MethodNode;

public class AnnotationManager {

    public static boolean getAnnotation(MethodNode methodNode, Class<?> annotationClass) {
        if (methodNode.invisibleAnnotations != null) {
            for (var annotation : methodNode.invisibleAnnotations) {
                if (annotation.desc.equals("L" + annotationClass.getName().replace('.', '/') + ";")) {
                    methodNode.invisibleAnnotations.remove(annotation);
                    return true;
                }
            }
        }
        if (methodNode.visibleAnnotations != null) {
            for (var annotation : methodNode.visibleAnnotations) {
                if (annotation.desc.equals("L" + annotationClass.getName().replace('.', '/') + ";")) {
                    methodNode.visibleAnnotations.remove(annotation);
                    return true;
                }
            }
        }
        return false;
    }
}
