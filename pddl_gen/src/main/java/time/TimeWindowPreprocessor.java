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
 * Preprocessing of the time windows with the STP minimal network (option --stp of the generator).
 *
 * Only the target windows of the "response" templates go in the STP, because those are the ones that
 * really constrain the trace: Response[A, B] with B in [2, 5]h means every A needs a B 2 to 5 hours
 * later. (A window on the activation is more like part of the activation condition, it says when the
 * constraint kicks in, not when something has to happen.) Every activity is one time point, so this
 * assumes each activity happens once. That's an approximation, which is why it's opt-in.
 *
 * With the minimal network we can then:
 * - warn if the windows contradict each other (no trace can satisfy all of them)
 * - tighten every window to what the other windows already imply
 * - drop the windows the others already imply, so the PDDL has fewer clocks and time facts
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

  public static String describe(Report report) {
    StringBuilder b = new StringBuilder("STP preprocessing of the time windows:\n");
    if (!report.consistent()) {
      b.append("  the target windows contradict each other, so no trace can satisfy all of them (left unchanged)\n");
      return b.toString();
    }
    b.append("  " + report.windowsBefore() + " target windows, " + report.tightened().size() + " tightened, "
        + report.redundant().size() + " dropped because the others already imply them\n");
    report.tightened().forEach((dc, interval) ->
        b.append("  tightened " + dc.getConstraintName() + " to [" + interval.lo() + ", " + interval.hi() + "]\n"));
    report.redundant().forEach(dc -> b.append("  dropped the window of " + dc.getConstraintName() + "\n"));
    return b.toString();
  }
}
