package war.jnt.core.code.analysis.exception;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public final class MethodExceptionPlan {

    private final String owner;
    private final MethodNode method;
    private final ExceptionSitePlan[] sitePlans;
    private final List<ExceptionDispatchGroup> dispatchGroups;
    private final int pollSiteCount;
    private final int explicitRaiseSiteCount;

    public MethodExceptionPlan(
            String owner,
            MethodNode method,
            ExceptionSitePlan[] sitePlans,
            List<ExceptionDispatchGroup> dispatchGroups
    ) {
        this.owner = owner;
        this.method = method;
        this.sitePlans = sitePlans;
        this.dispatchGroups = List.copyOf(dispatchGroups);

        int pollCount = 0;
        int explicitCount = 0;
        for (ExceptionSitePlan sitePlan : sitePlans) {
            if (sitePlan == null) {
                continue;
            }
            if (sitePlan.needsPoll()) {
                pollCount++;
            }
            if (sitePlan.kind().hasExplicitRaisePath()) {
                explicitCount++;
            }
        }
        this.pollSiteCount = pollCount;
        this.explicitRaiseSiteCount = explicitCount;
    }

    public String owner() {
        return owner;
    }

    public MethodNode method() {
        return method;
    }

    public List<ExceptionDispatchGroup> dispatchGroups() {
        return dispatchGroups;
    }

    public int pollSiteCount() {
        return pollSiteCount;
    }

    public int explicitRaiseSiteCount() {
        return explicitRaiseSiteCount;
    }

    public ExceptionSitePlan getSitePlan(AbstractInsnNode insn) {
        if (insn == null) {
            return ExceptionSitePlan.none();
        }
        int index = insn.index;
        if (index < 0 || index >= sitePlans.length) {
            return ExceptionSitePlan.none();
        }
        ExceptionSitePlan sitePlan = sitePlans[index];
        return sitePlan != null ? sitePlan : ExceptionSitePlan.none();
    }
}
