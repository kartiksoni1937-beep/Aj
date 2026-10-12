package war.jnt.core.code.analysis.exception;

import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.List;

public record ExceptionDispatchGroup(int id, ExceptionDispatchKey key, List<TryCatchBlockNode> catches) {

    public ExceptionDispatchGroup {
        catches = List.copyOf(catches);
    }

    public String dispatchLabel() {
        return "ex_dispatch_" + id;
    }
}
