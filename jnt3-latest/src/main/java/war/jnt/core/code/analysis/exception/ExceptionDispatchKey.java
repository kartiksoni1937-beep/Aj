package war.jnt.core.code.analysis.exception;

import java.util.List;

public record ExceptionDispatchKey(List<CatchTarget> catches) {

    public ExceptionDispatchKey {
        catches = List.copyOf(catches);
    }

    public boolean hasLocalCatches() {
        return !catches.isEmpty();
    }

    public record CatchTarget(String handlerLabel, String type) {}
}
