package war.jnt.core.code.analysis.exception;

public enum ExceptionSiteKind {
    NONE,
    EXPLICIT_RAISE,
    JNI_POLL,
    EXPLICIT_RAISE_AND_JNI_POLL,
    ATHROW_DIRECT;

    public boolean pollsPendingException() {
        return this == JNI_POLL || this == EXPLICIT_RAISE_AND_JNI_POLL;
    }

    public boolean hasExplicitRaisePath() {
        return this == EXPLICIT_RAISE
                || this == EXPLICIT_RAISE_AND_JNI_POLL
                || this == ATHROW_DIRECT;
    }
}
