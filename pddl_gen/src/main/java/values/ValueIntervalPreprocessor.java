package values;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import Automaton.VariableSubstitution;
import formula.Formula;
import formula.Formula.Comparison;
import formula.Formula.Literal;
import formula.Formulas;
import formula.IntervalSimplifier;
import formula.NandNormalForm;
import model.Activity;
import model.Attribute;
import model.DeclareConstraint;

/**
 * Minimal intervals on the data values (option --intervals of the generator). Two things, both exact:
 *
 * 1. Every guard is simplified with IntervalSimplifier: the comparisons on the same attribute that are in
 *    AND become their minimal interval, so there are fewer atoms (and nand clauses that can never block
 *    go away). The guard allows the same values as before.
 * 2. When the planner adds an event it picks every value from a list (variable_substitutions.txt). The
 *    conditions on an activity's attribute only compare it with a few numbers, which split the line into
 *    regions; two values in the same region make every condition on that attribute true or false in the
 *    same way. So one value per region is enough, instead of all the critical values of the attribute.
 *    A condition always belongs to the edges of its own activity (activation condition -> activation,
 *    target condition -> target), so the regions are per activity.
 *
 * It also reports the conditions that no value the planner can pick satisfies (with the declared
 * domain), which is most likely a modelling mistake. Those are only reported, since the events in the
 * log can still have values outside the domain.
 *
 * Unlike the STP on the time windows (--stp-apply), nothing here changes which traces violate what: every
 * condition is replaced by an equivalent one, so the plans cost the same.
 */
public final class ValueIntervalPreprocessor {

  // Above this many value combinations we don't check a condition for the report
  private static final int MAX_COMBINATIONS = 100_000;

  private ValueIntervalPreprocessor() {}

  public record GuardChange(String constraint, String role, int atomsBefore, int atomsAfter) {}

  public record Report(List<GuardChange> simplified, List<String> neverHold, Map<String, int[]> candidates) {}

  /** The comparisons with a number on each "activity.attribute", from the conditions of all the constraints. */
  public static Map<String, Set<Comparison>> comparisons(List<DeclareConstraint> constraints) {
    Map<String, Set<Comparison>> comparisons = new HashMap<>();
    for (DeclareConstraint dc : constraints) {
      addComparisons(comparisons, dc.getActivation(), dc.getActivationGuard());
      addComparisons(comparisons, dc.getTarget(), dc.getTargetGuard());
    }
    return comparisons;
  }

  private static void addComparisons(Map<String, Set<Comparison>> comparisons, String activity, Formula guard) {
    if (activity == null || guard == null) {
      return;
    }
    for (Comparison c : Formulas.comparisons(guard)) {
      if (c.value() instanceof Number) {
        comparisons.computeIfAbsent(activity + "." + c.attribute(), k -> new LinkedHashSet<>()).add(c);
      }
    }
  }

  /**
   * Of the values (name -> number), the first one (by number) of every region: values are in the same
   * region when every comparison is true for both or false for both.
   */
  public static List<String> representatives(Map<String, Double> values, Set<Comparison> comparisons) {
    Map<String, String> byRegion = new LinkedHashMap<>();
    values.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
        .forEach(e -> byRegion.putIfAbsent(region(e.getValue(), comparisons), e.getKey()));
    return new ArrayList<>(byRegion.values());
  }

  private static String region(double x, Set<Comparison> comparisons) {
    StringBuilder b = new StringBuilder();
    for (Comparison c : comparisons) {
      b.append(IntervalSimplifier.holds(c, x) ? '1' : '0');
    }
    return b.toString();
  }

  /** The value names the planner can pick for an attribute of an activity, as in variable_substitutions.txt. */
  public static List<String> candidates(Activity activity, Attribute attribute, Map<String, Set<Comparison>> comparisons) {
    if ("enum".equals(attribute.getType())) {
      return new ArrayList<>(attribute.variableValueMap.keySet());
    }
    Set<Comparison> onAttribute = comparisons.getOrDefault(activity.getName() + "." + attribute.getName(), Set.of());
    return representatives(attribute.variableValueMap, onAttribute);
  }

  /**
   * Simplifies the guards of the constraints (they must be encoded already, see
   * DeclareConstraint.encodeGuards) and says what changed. substitutions and values are what the
   * planner gets (variable_substitutions.txt and variable_values.txt).
   */
  public static Report apply(List<DeclareConstraint> constraints, Map<String, Activity> activities,
      Set<VariableSubstitution> substitutions, Map<String, Integer> values) {
    List<GuardChange> simplified = new ArrayList<>();
    List<String> neverHold = new ArrayList<>();
    Map<String, List<Double>> pickable = pickableValues(substitutions, values);

    for (DeclareConstraint dc : constraints) {
      Formula activation = simplify(dc, "activation", dc.getActivationGuard(), simplified);
      Formula target = simplify(dc, "target", dc.getTargetGuard(), simplified);
      dc.setGuards(activation, target);

      if (!canHold(activation, dc.getActivation(), pickable)) {
        neverHold.add(dc.getConstraintName() + " activation condition");
      }
      if (!canHold(target, dc.getTarget(), pickable)) {
        neverHold.add(dc.getConstraintName() + " target condition");
      }
    }

    Map<String, int[]> candidates = new TreeMap<>();
    for (Activity activity : activities.values()) {
      for (Attribute attribute : activity.getAttributes()) {
        if ("enum".equals(attribute.getType())) {
          continue;
        }
        String key = activity.getName() + "." + attribute.getName();
        int after = pickable.getOrDefault(key, List.of()).size();
        candidates.put(key, new int[] {attribute.variableValueMap.size(), after});
      }
    }
    return new Report(simplified, neverHold, candidates);
  }

  private static Formula simplify(DeclareConstraint dc, String role, Formula guard, List<GuardChange> changes) {
    if (guard == null) {
      return null;
    }
    List<List<Literal>> clauses = NandNormalForm.clauses(guard);
    Optional<List<List<Literal>>> result = IntervalSimplifier.clauses(clauses);
    if (result.isEmpty()) {
      return guard; // it can never hold, we leave it as it is (the report says so)
    }
    int before = atoms(clauses);
    int after = atoms(result.get());
    if (after == before && result.get().size() == clauses.size()) {
      return guard;
    }
    changes.add(new GuardChange(dc.getConstraintName(), role, before, after));
    return IntervalSimplifier.toFormula(result.get());
  }

  private static int atoms(List<List<Literal>> clauses) {
    return clauses.stream().mapToInt(List::size).sum();
  }

  private static Map<String, List<Double>> pickableValues(Set<VariableSubstitution> substitutions, Map<String, Integer> values) {
    Map<String, List<Double>> pickable = new HashMap<>();
    for (VariableSubstitution s : substitutions) {
      Integer value = values.get(s.variableName);
      if (value != null) {
        pickable.computeIfAbsent(s.activityName + "." + s.categoryName, k -> new ArrayList<>()).add(value.doubleValue());
      }
    }
    return pickable;
  }

  /** Whether some combination of pickable values satisfies the guard (true when we can't tell). */
  private static boolean canHold(Formula guard, String activity, Map<String, List<Double>> pickable) {
    if (guard == null || activity == null) {
      return true;
    }
    List<String> attributes = Formulas.comparisons(guard).stream().map(Comparison::attribute).distinct().toList();
    List<List<Double>> options = new ArrayList<>();
    long combinations = 1;
    for (String attribute : attributes) {
      List<Double> values = pickable.get(activity + "." + attribute);
      if (values == null || values.isEmpty()) {
        return true;
      }
      options.add(values);
      combinations *= values.size();
      if (combinations > MAX_COMBINATIONS) {
        return true;
      }
    }
    double[] assignment = new double[attributes.size()];
    return search(guard, attributes, options, assignment, 0);
  }

  private static boolean search(Formula guard, List<String> attributes, List<List<Double>> options, double[] assignment, int i) {
    if (i == attributes.size()) {
      return Formulas.evaluate(guard, literal -> literal instanceof Comparison c && c.value() instanceof Number
          && IntervalSimplifier.holds(c, assignment[attributes.indexOf(c.attribute())]));
    }
    for (double value : options.get(i)) {
      assignment[i] = value;
      if (search(guard, attributes, options, assignment, i + 1)) {
        return true;
      }
    }
    return false;
  }

  public static String describe(Report report) {
    StringBuilder b = new StringBuilder("Minimal intervals on the data values:\n");
    if (report.simplified().isEmpty()) {
      b.append("  no condition could be simplified\n");
    }
    for (GuardChange change : report.simplified()) {
      b.append("  " + change.constraint() + " " + change.role() + " condition: " + change.atomsBefore()
          + " -> " + change.atomsAfter() + " comparisons\n");
    }
    report.candidates().forEach((key, counts) ->
        b.append("  " + key + ": " + counts[1] + " values to pick from (were " + counts[0] + ")\n"));
    report.neverHold().forEach(what ->
        b.append("  " + what + " holds for no value the planner can pick (check the model)\n"));
    return b.toString();
  }
}
