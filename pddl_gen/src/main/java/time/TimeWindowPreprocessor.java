package time;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import model.DeclareConstraint;
import model.DeclareTemplate;
import time.SimpleTemporalNetwork.Constraint;
import time.SimpleTemporalNetwork.Interval;

/**
 * Looks at the time windows with the STP minimal network (options --stp and --stp-apply of the generator).
 *
 * Only the target windows of the "response" templates go in the STP, because those are the ones that
 * really constrain the trace: Response[A, B] with B in [2, 5]h means every A needs a B 2 to 5 hours
 * later. (A window on the activation is more like part of the activation condition, it says when the
 * constraint kicks in, not when something has to happen.) Every activity is one time point.
 *
 * What it can tell:
 * - the windows contradict each other, so every trace will need at least one reset
 * - how much a window could be tightened, and which windows the others already imply
 *
 * Why --stp only reports by default (checked on small models):
 * - one time point per activity is wrong when an activity shows up more than once. With Response[A, B]
 *   and Response[C, B] the STP thinks the B after A and the B after C are the same B, and "derives" a
 *   window between A and C that a valid trace can break.
 * - even when an implication is right, it only holds for traces that satisfy everything. The planner
 *   can violate constraints (reset, cost 1000), so dropping an "implied" window removes a violation
 *   the original model would have counted, and the optimal cost changes.
 * So --stp-apply (tighten and drop) gives a lighter PDDL but not the same answers.
 */
public class TimeWindowPreprocessor {

  // The templates where the target window is "the target has to come within [min, max] after the activation"
  private static final Set<DeclareTemplate> RESPONSE_TEMPLATES = Set.of(
      DeclareTemplate.Response, DeclareTemplate.Chain_Response, DeclareTemplate.Alternate_Response);

  public record Report(boolean consistent, Map<DeclareConstraint, Interval> tightened, List<DeclareConstraint> redundant,
      int windowsBefore) {}

  /** Looks at the windows without changing anything. */
  public static Report analyse(List<DeclareConstraint> constraints) {
    SimpleTemporalNetwork stp = new SimpleTemporalNetwork();
    Map<Constraint, DeclareConstraint> origin = new IdentityHashMap<>();
    for (DeclareConstraint dc : constraints) {
      if (!RESPONSE_TEMPLATES.contains(dc.getTemplate()) || !dc.hasExplicitTargetWindow()) {
        continue;
      }
      double[] window = dc.getTargetTimeConditions();
      origin.put(stp.add(dc.getActivation(), dc.getTarget(), window[0], window[1]), dc);
    }

    int windowsBefore = origin.size();
    if (!stp.isConsistent()) {
      return new Report(false, Map.of(), List.of(), windowsBefore);
    }

    Map<DeclareConstraint, Interval> tightened = new IdentityHashMap<>();
    for (Map.Entry<Constraint, DeclareConstraint> entry : origin.entrySet()) {
      Constraint c = entry.getKey();
      Interval tightest = stp.tightest(c.source(), c.target());
      if (tightest.lo() > c.lo() || tightest.hi() < c.hi()) {
        tightened.put(entry.getValue(), tightest);
      }
    }

    List<DeclareConstraint> redundant = new ArrayList<>();
    for (Constraint dropped : stp.removeRedundant()) {
      redundant.add(origin.get(dropped));
    }
    return new Report(true, tightened, redundant, windowsBefore);
  }

  /** Analyses the windows, then tightens and drops them in the constraints. Gives back the report. */
  public static Report apply(List<DeclareConstraint> constraints) {
    Report report = analyse(constraints);
    if (!report.consistent()) {
      // We don't touch anything, the planner will just have to violate something
      return report;
    }
    for (Map.Entry<DeclareConstraint, Interval> entry : report.tightened().entrySet()) {
      entry.getKey().setTargetTimeConditions(entry.getValue().lo(), entry.getValue().hi());
    }
    for (DeclareConstraint dc : report.redundant()) {
      dc.dropTargetTimeConditions();
    }
    return report;
  }

  public static String describe(Report report, boolean applied) {
    StringBuilder b = new StringBuilder(applied ? "STP preprocessing of the time windows (applied):\n"
        : "STP check of the time windows (nothing changed):\n");
    if (!report.consistent()) {
      b.append("  the target windows contradict each other, so no trace can satisfy all of them (left unchanged)\n");
      return b.toString();
    }
    // A window that goes away anyway isn't "tightened" too
    List<DeclareConstraint> tightened = report.tightened().keySet().stream().filter(dc -> !report.redundant().contains(dc)).toList();
    b.append("  " + report.windowsBefore() + " target windows, " + tightened.size() + " can be tightened, "
        + report.redundant().size() + " are implied by the others\n");
    for (DeclareConstraint dc : tightened) {
      Interval interval = report.tightened().get(dc);
      b.append("  " + dc.getConstraintName() + " can be tightened to [" + interval.lo() + ", " + interval.hi() + "]\n");
    }
    report.redundant().forEach(dc -> b.append("  " + dc.getConstraintName() + " is implied by the others\n"));
    return b.toString();
  }
}
