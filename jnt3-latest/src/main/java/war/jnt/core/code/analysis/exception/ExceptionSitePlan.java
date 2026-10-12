package war.jnt.core.code.analysis.exception;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record ExceptionSitePlan(
        ExceptionSiteKind kind,
        ExceptionDispatchGroup dispatchGroup,
        Set<ExceptionRaiseKind> explicitRaises
) {

    private static final ExceptionSitePlan NONE_PLAN = new ExceptionSitePlan(
            ExceptionSiteKind.NONE,
            null,
            Set.of()
    );

    public ExceptionSitePlan {
        if (explicitRaises == null || explicitRaises.isEmpty()) {
            explicitRaises = Set.of();
        } else {
            explicitRaises = Collections.unmodifiableSet(EnumSet.copyOf(explicitRaises));
        }
    }

    public static ExceptionSitePlan none() {
        return NONE_PLAN;
    }

    public boolean needsPoll() {
        return kind.pollsPendingException();
    }

    public String handlerLabelOr(String fallbackLabel) {
        return dispatchGroup != null ? dispatchGroup.dispatchLabel() : fallbackLabel;
    }
}
