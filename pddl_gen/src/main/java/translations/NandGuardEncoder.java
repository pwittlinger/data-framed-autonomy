package translations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import Automaton.Transition;
import formula.Formula.Comparison;
import model.RecoveryStrategy;

/**
 * Writes the nand clause guards of the transitions as PDDL facts.
 *
 * Every nand becomes a guard_clause object and every argument of it a guard_atom object. The
 * clauses variant of the domain (see DomainVariants) then blocks an arc when
 * all the arguments of one of its clauses hold, which is exactly when the nand is false.
 *
 * For example the guard nand(A.x <= 10, A.y >= 5) on the arc s1 -a-> s2 turns into:
 * <pre>
 *   (clause_guarded s1 a s2)
 *   (has_constraint a x s1 s2) (has_constraint a y s1 s2)
 *   (clause_of g1 s1 s2 a)
 *   (atom_of g1_1 g1) (atom_param g1_1 x) (atom_le g1_1) (= (atom_value g1_1) 10)
 *   (atom_of g1_2 g1) (atom_param g1_2 y) (atom_ge g1_2) (= (atom_value g1_2) 5)
 * </pre>
 */
public class NandGuardEncoder {

  private final List<String> clauseObjects = new ArrayList<>();
  private final List<String> atomObjects = new ArrayList<>();
  // Facts per transition, so the names stay the same when we write more than one problem
  private final Map<Transition, String> factsByTransition = new IdentityHashMap<>();

  /** True if at least one transition needed nand clauses (and so the clauses domain). */
  public boolean isUsed() {
    return !clauseObjects.isEmpty() || !factsByTransition.isEmpty();
  }

  /** The facts for a transition with nand clauses. activity is the name the activity has in the PDDL. */
  public String encode(Transition t, String activity) {
    return factsByTransition.computeIfAbsent(t, key -> buildFacts(t, activity));
  }

  private String buildFacts(Transition t, String activity) {
    String s1 = t.getActiviationState().name;
    String s2 = t.getTargetState().name;
    StringBuilder b = new StringBuilder();
    b.append("    (clause_guarded " + s1 + " " + activity + " " + s2 + ")\n");

    // has_constraint tells the domain the arc reads this parameter, so a missing value blocks the
    // arc (like in the old encoding) and the planner is allowed to add a value for it
    Set<String> parameters = new LinkedHashSet<>();
    t.getNandClauses().forEach(clause -> clause.forEach(atom -> parameters.add(atom.attribute())));
    for (String parameter : parameters) {
      b.append("    (has_constraint " + activity + " " + parameter + " " + s1 + " " + s2 + ")\n");
    }

    for (List<Comparison> clause : t.getNandClauses()) {
      String clauseName = "g" + (clauseObjects.size() + 1);
      clauseObjects.add(clauseName);
      b.append("    (clause_of " + clauseName + " " + s1 + " " + s2 + " " + activity + ")\n");
      int index = 1;
      for (Comparison atom : clause) {
        String atomName = clauseName + "_" + index++;
        atomObjects.add(atomName);
        b.append("    (atom_of " + atomName + " " + clauseName + ") (atom_param " + atomName + " " + atom.attribute() + ") ("
            + operatorPredicate(atom.operator()) + " " + atomName + ") (= (atom_value " + atomName + ") " + atom.value() + ")\n");
      }
    }
    return b.toString();
  }

  private static String operatorPredicate(String operator) {
    switch (operator) {
      case ">": return "atom_gt";
      case ">=": return "atom_ge";
      case "<": return "atom_lt";
      case "<=": return "atom_le";
      case "=": return "atom_eq";
      case "!=": return "atom_ne";
      default: throw new IllegalArgumentException("No PDDL operator for " + operator);
    }
  }

  /** The lines to put in (:objects ...), empty if no transition uses clauses. */
  public String objectDeclarations() {
    StringBuilder b = new StringBuilder();
    if (!clauseObjects.isEmpty()) {
      b.append("    " + String.join(" ", clauseObjects) + " - guard_clause\n");
    }
    if (!atomObjects.isEmpty()) {
      b.append("    " + String.join(" ", atomObjects) + " - guard_atom\n");
    }
    return b.toString();
  }

  /** Writes the clauses variant of the strategy's domain into the given folder. */
  public static Path exportClausesDomain(Path folder, RecoveryStrategy strategy, boolean declareOnly) throws IOException {
    Path target = folder.resolve(DomainVariants.fileName(strategy, true, false));
    String domain = DomainVariants.withClauses(DomainVariants.base(strategy));
    Files.createDirectories(folder);
    Files.writeString(target, declareOnly ? allowDeclareReplay(domain) : domain);
    return target;
  }

  /**
   * DECLARE automata start in goal states. Requiring a non-goal state in validate-payload therefore
   * prevents a DECLARE-only problem from replaying its first event. Mixed models keep the guard because
   * their Petri-net automata provide the non-goal state expected by the original domain.
   */
  static String allowDeclareReplay(String domain) throws IOException {
    String actionMarker = "(:action validate-payload";
    String effectMarker = ":effect";
    String guard = "(not (goal_state ?s1))";
    int action = domain.indexOf(actionMarker);
    int effect = action < 0 ? -1 : domain.indexOf(effectMarker, action);
    int position = action < 0 ? -1 : domain.indexOf(guard, action);
    if (position < 0 || effect < 0 || position > effect) {
      throw new IOException("Could not find the goal-state guard in validate-payload");
    }
    return domain.substring(0, position) + "; DECLARE-only: goal states may replay events\n            ; " + guard
        + domain.substring(position + guard.length());
  }
}
