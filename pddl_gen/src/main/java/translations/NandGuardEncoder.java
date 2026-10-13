package translations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

/**
 * Writes the nand clause guards of the transitions as PDDL facts.
 *
 * Every nand becomes a guard_clause object and every argument of it a guard_atom object. The
 * clauses domain (domain_framed_autonomy_process_reset_time_clauses.pddl) then blocks an arc when
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

  public static final String CLAUSES_DOMAIN = "domain_framed_autonomy_process_reset_time_clauses.pddl";

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

  /** Copies the clauses domain (packaged in the jar) into the given folder. */
  public static Path exportClausesDomain(Path folder) throws IOException {
    Path target = folder.resolve(CLAUSES_DOMAIN);
    try (InputStream in = NandGuardEncoder.class.getResourceAsStream("/domains/" + CLAUSES_DOMAIN)) {
      if (in == null) {
        throw new IOException("The clauses domain is missing from the jar (/domains/" + CLAUSES_DOMAIN + ")");
      }
      Files.createDirectories(folder);
      Files.writeString(target, new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
    return target;
  }
}
