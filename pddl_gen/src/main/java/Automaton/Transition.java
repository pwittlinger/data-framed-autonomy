package Automaton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import formula.Formula;
import formula.Formula.Comparison;
import formula.Formula.Literal;
import formula.NandNormalForm;
import model.Condition;
import model.OperatorType;

public class Transition {
  private State activationState;
  private State targetState;
  private String activity;
  private List<Condition> reformedConditions;
  // The guard as an AND of NANDs, each NAND is the list of its arguments. Only set when the old
  // encoding (one value per parameter and operator) can't express the guard.
  private List<List<Comparison>> nandClauses;
  private Double minTimeCondition = -1.0; // -1 means no condition
  private Double maxTimeCondition = -1.0;

  public Transition(State state1, State state2, String activity, Formula guard) {
    this.activationState = state1;
    this.targetState = state2;
    this.activity = activity;

    List<List<Literal>> clauses = guard == null ? List.of() : NandNormalForm.clauses(guard);
    // If the guard is just a bunch of simple comparisons in AND we keep the old encoding, so the
    // PDDL for the models we already have doesn't change. Otherwise we go with the nand clauses.
    this.reformedConditions = this.toOldConditions(clauses);
    if (this.reformedConditions == null) {
      this.nandClauses = toNandClauses(clauses);
    }
  }

  // A clause (l1 or ... or lk) is nand(not l1, ..., not lk), so we store the negated atoms
  private static List<List<Comparison>> toNandClauses(List<List<Literal>> clauses) {
    List<List<Comparison>> nands = new ArrayList<>();
    for (List<Literal> clause : clauses) {
      List<Comparison> arguments = new ArrayList<>();
      for (Literal literal : clause) {
        arguments.add((Comparison) literal.negate());
      }
      nands.add(arguments);
    }
    return nands;
  }

  /**
   * The guard in the old format, or null if it doesn't fit. It fits when every clause is a single
   * comparison with an integer value, and after splitting > and < (see below) we never need two
   * values for the same parameter and operator, since the PDDL only has one slot for that.
   */
  private List<Condition> toOldConditions(List<List<Literal>> clauses) {
    List<Condition> conditions = new ArrayList<>();
    for (List<Literal> clause : clauses) {
      if (clause.size() != 1 || !(clause.get(0) instanceof Comparison comparison) || !(comparison.value() instanceof Integer value)) {
        return null;
      }
      OperatorType operator = switch (comparison.operator()) {
        case ">" -> OperatorType.BIGGER;
        case ">=" -> OperatorType.BIGGER_OR_EQUAL;
        case "<" -> OperatorType.LESS;
        case "<=" -> OperatorType.LESS_OR_EQUAL;
        case "=" -> OperatorType.EQUAL;
        case "!=" -> OperatorType.NOT_EQUAL;
        default -> null;
      };
      if (operator == null) {
        return null;
      }
      conditions.add(new Condition(this.activity, comparison.attribute(), operator, value));
    }

    List<Condition> reformed = this.reformConditions(conditions);
    Map<String, Integer> slots = new HashMap<>();
    List<Condition> deduplicated = new ArrayList<>();
    for (Condition condition : reformed) {
      Integer previous = slots.putIfAbsent(condition.parameterName + "|" + condition.operator, condition.value);
      if (previous == null) {
        deduplicated.add(condition);
      } else if (!previous.equals(condition.value)) {
        return null;
      }
    }
    return deduplicated;
  }

  private List<Condition> reformConditions(List<Condition> conditions) {
    if (conditions == null) return null;

    List<Condition> newList = new ArrayList<>();
    for (Condition condition : conditions) {

      if (condition.operator == null) {
        newList.add(condition);
        continue;
      }

      // BIGGER OR LESS NEED TWO CONDITIONS IN OUR PDDL
      switch (condition.operator) {
        case BIGGER:
          // > is equal to >= && !=
          newList.add(new Condition(condition.activity, condition.parameterName, OperatorType.BIGGER_OR_EQUAL, condition.value));
          newList.add(new Condition(condition.activity, condition.parameterName, OperatorType.NOT_EQUAL, condition.value));
          break;
        case LESS:
          newList.add(new Condition(condition.activity, condition.parameterName, OperatorType.LESS_OR_EQUAL, condition.value));
          newList.add(new Condition(condition.activity, condition.parameterName, OperatorType.NOT_EQUAL, condition.value));
          break;
        default:
          newList.add(condition);
      }
    }

    return newList;
  }
  public State getActiviationState() {
    return this.activationState;
  }
  public State getTargetState() {
    return this.targetState;
  }
  public String getActivity() {
    return this.activity;
  }
  /** The guard in the old format, null when the transition uses nand clauses instead. */
  public List<Condition> getReformedConditions() {
    return reformedConditions;
  }
  /** The guard as nand clauses, null when the old format is enough. */
  public List<List<Comparison>> getNandClauses() {
    return nandClauses;
  }

  public void setMinTimeCondition(Double minTime) {
    this.minTimeCondition = minTime;
  }

  public void setMaxTimeCondition(Double maxTime) {
    this.maxTimeCondition = maxTime;
  }

  public double getMinTimeCondition() {
    return this.minTimeCondition;
  }

  public double getMaxTimeCondition() {
    return this.maxTimeCondition;
  }

}
