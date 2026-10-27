package translations;

import model.Activity;
import model.Attribute;
import model.Condition;
import model.CostEnum;
import model.DeclareConstraint;
import model.DeclareModel;
import model.RecoveryStrategy;

import org.deckfour.xes.extension.std.XConceptExtension;
import Automaton.Automaton;
import Automaton.Pair;
import Automaton.State;
import Automaton.Transition;
import Automaton.VariableSubstitution;
import log.Event;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PDDLGenerator {

  private final Map<Pair<Activity, CostEnum>, Integer> costs;
  
  // NOTE Define action costs above ^^^
  private final HashMap<String, Activity> activities;
  private final ArrayList<DeclareConstraint> constraints;
  private final ArrayList<String> params;
  protected final DeclareModel declareModel;
  protected final RecoveryStrategy recovery;
  private ArrayList<Automaton> constraintAutomatons;
  private List<List<State>> goalAutomatonStates;
  private State finalTraceState;
  // private final ArrayList<Transition> relevantTransitions;ss

  private static final String HEADER_STRING = 
    "(define (problem prob-trace)\n" + 
    "  (:domain trace-alignment)\n" + 
    "\n";
  private static final String FOOTER_STRING =
    "  (:metric minimize (total_cost))\n" +
    ")\n" +
    "\n";

  // Waiting one time unit costs 10, as the domain's waiting process advances the timestamp by 0.1 per step
  public static final BigDecimal DEFAULT_WAITING_COST = BigDecimal.TEN;
  // Written as waiting_cost: the domain divides the other action costs by it, so waiting costs --waiting_cost per time unit
  protected final BigDecimal waitingCostFactor;

  public PDDLGenerator(DeclareModel model, RecoveryStrategy recovery, BigDecimal waitingCost) throws Exception {

    // Get set costs, or use default ones
    Map<Pair<Activity, CostEnum>, Integer> costs = model.getCosts();
    if (costs != null) {
      this.costs = costs;
    } else {
      this.costs = null;
    }

    this.activities = model.getActivities();
    this.constraints = model.getDeclareConstraints();
    this.params = model.params;
    this.declareModel = model;
    this.recovery = recovery;
    this.waitingCostFactor = waitingCost.divide(DEFAULT_WAITING_COST, MathContext.DECIMAL64).stripTrailingZeros();
    this.constraintAutomatons = new ArrayList<>();
    this.goalAutomatonStates = new ArrayList<>();
    this.prepareAutomatonStates();
  }

  // BigDecimal keeps the scaled value exact (0.7 / 10 = 0.07, not 0.06999999999999999) and rejects NaN and Infinity
  public static BigDecimal parseWaitingCost(String value) {
    BigDecimal cost;
    try {
      cost = new BigDecimal(value.strip());
    } catch (NumberFormatException e) {
      cost = BigDecimal.ONE.negate();
    }
    if (cost.signum() <= 0) {
      throw new Error("--waiting_cost needs a positive number (e.g. 0.5), found: " + value);
    }
    return cost;
  }

  private void prepareAutomatonStates() {
    int index = 1;
    for (DeclareConstraint constraint : this.constraints) {

      String prefix = "s" + index++ + "_";
      Automaton newAutomaton = new Automaton(activities.keySet(), prefix, constraint);
      this.constraintAutomatons.add(newAutomaton);
      boolean hard = this.isHard(constraint.getConstraintName());

      // Automaton might have more than one goal state. In that case, we'll put the goal states with an "or" between them.
      List<State> goalStates = newAutomaton.getStates().stream()
                                  .filter(x -> this.isAccepting(x, hard))
                                  .toList();
     
      this.goalAutomatonStates.add(goalStates);
    }
  }

  // With the Discard strategy a violated constraint is ignored, so its failure states are accepting too.
  // They keep their failure_state fact, so reaching them is still recognised as a violation.
  // A hard constraint is not discarded, its failure states stay non-accepting.
  private boolean isAccepting(State state, boolean hard) {
    return state.isGoal || (state.isFailure && this.recovery.acceptsFailureStates() && !hard);
  }

  // Marked x in the violation cost file (DECLARE-only problems)
  private boolean isHard(String constraintName) {
    return this.declareModel.hardConstraints.contains(constraintName);
  }

  public String defineProblem(ArrayList<Event> listOfEvents, Map<String, Integer> assignments, Set<VariableSubstitution> substitutions, ArrayList<Double> timeStamps) {

    Map<Event, Map<Attribute, String>> attributes = this.parseEvents(listOfEvents);
    List<State> finalAutomatonStates = new ArrayList<>();

    StringBuilder s = new StringBuilder();
    s.append(PDDLGenerator.HEADER_STRING);
    s.append(this.buildObjectsString(attributes, assignments, substitutions));

    s.append(this.buildSubstitutionValues(assignments, substitutions));
    s.append(this.buildActionCosts());
    List<String> constraintNames = this.constraints.stream().map(DeclareConstraint::getConstraintName).toList();
    s.append(buildHardConstraints(constraintNames, this.declareModel.hardConstraints));
    s.append(this.buildTraceDeclaration(listOfEvents, attributes));
    s.append(this.buildTimeStamps(timeStamps));
    s.append(this.buildAutomatons(finalAutomatonStates));

    s.append(this.buildGoals());
    s.append(PDDLGenerator.FOOTER_STRING);
    return s.toString();
  }

  private Map<Event, Map<Attribute, String>> parseEvents(ArrayList<Event> events) {
    
    int index = 0;
    Map<Event, Map<Attribute, String>> assignments = new HashMap<>();
    for(Event event : events) {
      event.setName("t" + index++); // Assign event name that will be put in the PDDL.
      if (index == events.size()) { // If last element
        State finalTraceState = new State("t" + index); // Last trace state is not in the trace, we will need to create it ourselves.
        this.finalTraceState = finalTraceState;
      }
      assignments.put(event, event.getAttributeAssignments());
    }

    // Empty (prefix-) trace: the loop never sets the final trace state, so the start state t0 is also the final one.
    // Checked on the trace rather than on null because the generator is reused across traces.
    if (events.isEmpty()) {
      this.finalTraceState = new State("t0");
    }
    return assignments;
  }

  // Every parameter name used by the model or by a has_substitution_value fact has to be declared as an object,
  // even if the current trace never carries it, otherwise the planner fails with an undefined object.
  protected static Set<String> collectParameterNames(List<String> params, Set<VariableSubstitution> substitutions) {
    Set<String> names = new LinkedHashSet<>(params);
    substitutions.forEach(x -> names.add(x.categoryName));
    return names;
  }

  // Enum trace values are encoded with the value's index in the model, the same numbering that is
  // written to variable_values.txt and used for the constraint conditions. Numeric values stay as they are.
  protected String encodeTraceValue(String attributeName, String value) {
    Attribute attribute = this.declareModel.getAttribute(attributeName);
    if (attribute == null || !"enum".equals(attribute.getType())) {
      return value;
    }
    Double encoded = attribute.variableValueMap.get(value);
    if (encoded == null) {
      throw new Error("Trace value " + value + " is not declared for attribute " + attributeName);
    }
    return String.valueOf(encoded.intValue());
  }

  private StringBuilder buildObjectsString(Map<Event, Map<Attribute, String>> attributeAssignments, Map<String, Integer> variables, Set<VariableSubstitution> substitutions) {
    StringBuilder b = new StringBuilder();
    b.append("  (:objects\n");

    // TRACE STATES
    b.append("    ");
    attributeAssignments.keySet().forEach(x -> b.append(x.getName() + " "));
    b.append(this.finalTraceState.name + " ");
    b.append("- trace_state\n");


    // AUTOMATON STATES
    b.append("    ");
    this.constraintAutomatons.forEach(x -> {
      x.getStates().forEach(y -> {
        b.append(y.name + " ");
      });
    });
    b.append("- automaton_state\n");

    // ACTIVITIES
    b.append("    ");
    this.activities.keySet().forEach(x -> b.append(x + " "));
    b.append("- activity\n");

    // ATTRIBUTES
    Set<String> attributes = collectParameterNames(this.params, substitutions);
    b.append("    ");
    attributes.forEach(x -> b.append(x + " "));
    b.append("- parameter_name\n");

    b.append("    ");
    variables.keySet().forEach(x -> b.append(x + " "));
    b.append("- value_name\n");

    b.append("    ");
    this.constraints.forEach(x -> b.append(x.getConstraintName() + " "));
    b.append("- constraint\n");

    b.append("  )\n");
    return b;
  }

  private StringBuilder buildSubstitutionValues(Map<String, Integer> variables, Set<VariableSubstitution> substitutions) {
    StringBuilder b = new StringBuilder();

    b.append("  (:init\n\n");
    b.append("    ; Initialize plan cost. Some planners might need this explicitly\n");
    b.append("    (= (total_cost) 0)\n\n");
    b.append("    (= (current_timestamp) 0)\n");
    // Plain notation, toString would print small or large values in scientific notation (e.g. 1E-5)
    b.append("    (= (waiting_cost) " + this.waitingCostFactor.toPlainString() + ")\n\n");
    b.append("    ;; SUBSTITUTION VARIABLES\n");

    for (Map.Entry<String, Integer> entry : variables.entrySet()) {
      b.append("    (= (variable_value " + entry.getKey() + ") " + entry.getValue() + ")\n");
    }
    b.append("\n");
    for (VariableSubstitution sub : substitutions) {
      b.append("    (has_substitution_value " + sub.variableName + " " + sub.activityName + " " + sub.categoryName + ")\n");
    }
    b.append("\n");

    return b;
  }
  private StringBuilder buildActionCosts() {
    StringBuilder b = new StringBuilder();
    b.append("    ; Action costs\n");

    this.constraints.forEach(x -> b.append("    (= (violation_cost " + x.getConstraintName() + ") " + this.declareModel.constraintViolation.get(x.getConstraintName()) + ")\n"));

    for (Map.Entry<Pair<Activity, CostEnum>, Integer> cost : this.costs.entrySet()) {
      switch (cost.getKey().getValue()) {
        case ADD:
          b.append("    (= (add_cost " + cost.getKey().getKey().getName() + ") " + cost.getValue() + ")\n");
          break;
        case SET:
          b.append("    (= (set_cost " + cost.getKey().getKey().getName() + ") " + cost.getValue() + ")\n");
          break;
      }
    }
    b.append("\n");

    return b;
  }

  // Marks the constraints set to x in the violation cost file. Nothing is written if there are none.
  protected static StringBuilder buildHardConstraints(List<String> constraintNames, Set<String> hardConstraints) {
    StringBuilder b = new StringBuilder();
    for (String constraintName : constraintNames) {
      if (hardConstraints.contains(constraintName)) {
        b.append("    (is_hard " + constraintName + ")\n");
      }
    }
    if (b.length() > 0) {
      b.insert(0, "    ;; HARD CONSTRAINTS\n");
      b.append("\n");
    }
    return b;
  }
  public StringBuilder buildTimeStamps(ArrayList<Double> timeStamps) {
    // Assuming that the length of timeStamps is equal to the number of trace states-1
    int l = timeStamps.size();
    StringBuilder b = new StringBuilder();
    b.append("    ;; TIMESTAMPS\n");
    for (int i = 0; i < l; i++) {
      int next = i + 1;
      b.append("    (= (timestamp t" + i + " t" + next + ") " + timeStamps.get(i) + ")\n");
    }

    return b;
  }

  private StringBuilder buildTraceDeclaration(List<Event> events, Map<Event, Map<Attribute, String>> assignments) {
    StringBuilder b = new StringBuilder();
    b.append("    ;; TRACE DECLARATION\n");

    if (events.isEmpty()) {
      b.append("    (recovery_finished)\n");
      b.append("    (cur_t_state " + this.finalTraceState.name + ")\n");
      b.append("    (final_t_state " + this.finalTraceState.name + ")\n");
      return b;
    }

    b.append("    (cur_t_state " + events.get(0).getName() + ")\n");
    b.append("    (final_t_state " + this.finalTraceState.name + ")\n");
    Iterator<Event> it1 = events.iterator();
    Event cur;

    Iterator<Event> it2 = events.iterator();
    Event next;
    if (it2.hasNext()) {
      next = it2.next();
    }
    String activity;


    while (it1.hasNext()) {
      cur = it1.next();
      
      // If last element
      String nextName;
      if (!it2.hasNext()) {
        nextName = this.finalTraceState.name;
      } else { // if inside
        next = it2.next();
        nextName = next.getName();
      }

      activity = XConceptExtension.instance().extractName(cur.getXEvent());
      b.append("    (trace " + cur.getName() + " " + activity + " " + nextName + ")\n");
      for(Map.Entry<Attribute, String> singleAssignment : assignments.get(cur).entrySet()) {
        String attName = singleAssignment.getKey().getName();

        if (!this.params.contains(attName)) {
          continue;
        }

        String value = this.encodeTraceValue(attName, singleAssignment.getValue());

        b.append("    (has_parameter " + activity + " " + attName + " " + cur.getName() + " " + nextName + ")\n");
        b.append("    (= (trace_parameter " + activity + " " + attName + " " + cur.getName() + " " + nextName + ") " + value + ")\n");
      }
      b.append("\n");
    }

    return b;
  }

  public StringBuilder buildAutomatons(List<State> finalAutomatonStates) {
    StringBuilder b = new StringBuilder();
    b.append("    ;; AUTOMATON STATES\n");

    for (Automaton aut : this.constraintAutomatons) {

      String constraintName = aut.getConstraint().getConstraintName();
      boolean hard = this.isHard(constraintName);
      

      for (State state : aut.getStates()) {
        b.append("    (associated " + state.name + " " + constraintName + ")\n");

        if (state.isInitial) {
          b.append("    (cur_s_state " + state.name + ")\n");
          // A hard constraint is never reset: without initial_state the reset action has no state to move it back to
          if (!hard) {
            b.append("    (initial_state " + state.name + ")\n");
          }
        }
        // The Skip domain does not move an automaton into a failure_state, it skips the event for that constraint.
        // A hard constraint is not skipped, so its failure states are written as ordinary states: the automaton moves
        // into them and, as they are non-accepting and cannot be left, the violation makes the problem unsolvable.
        if (state.isFailure && !(hard && this.recovery.skipsFailureStates())) {
          b.append("    (failure_state " + state.name + ")\n");
        }
        if (this.isAccepting(state, hard)) {
          b.append("    (goal_state " + state.name + ")\n");
        }

        // If discard or skip
        // create a new predicate to violate the constraint at the end of the trace.
        if (aut.getConstraint().getTemplate().getTemplateName().equals("Existence") & this.recovery.getOptionName().toLowerCase().contains("reset")
        & state.isSpecialExistenceViolation) {
           b.append("    (initial_state " + state.name + ")\n");
        }
          

      }

      for (String clockCondition : aut.getConstraint().getClockConditions()) {
        b.append(clockCondition);
      }

      boolean setClock = false;

      for (Transition t : aut.getTransitions()) {
        b.append("    (automaton " + t.getActiviationState().name + " " + t.getActivity() + " " + t.getTargetState().name + ")\n");

        List<Condition> conditions = t.getReformedConditions();
        if (conditions != null) {
          for (Condition c : conditions) {
            b.append(this.getConditionString(t, c));
            b.append(this.getHasConditionString(t, c));
          }
        }

        if ((t.getMinTimeCondition() > -1.0) && (t.getMaxTimeCondition() > 0.0)) {
          setClock = true;
          b.append("    (= (min_t_condition " + t.getActiviationState().name + " " + t.getActivity() + " " + t.getTargetState().name + ") " + t.getMinTimeCondition() + ")\n");
          b.append("    (= (max_t_condition " + t.getActiviationState().name + " " + t.getActivity() + " " + t.getTargetState().name + ") " + t.getMaxTimeCondition() + ")\n");
        }
      }

      if (setClock) {
        b.append("    (= (start_clock " + constraintName + ") 0)\n");
      }
    
      b.append("\n");
    }

    // Close init
    b.append("  )\n");
    return b;
  }
  private StringBuilder getConditionString(Transition t, Condition c) {
    StringBuilder b = new StringBuilder();

    if (c.operator == null) return b;

    switch (c.operator) {
      case BIGGER_OR_EQUAL:
        b.append("    (has_maj_c " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
        b.append("    (= (majority_constraint " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
        break;
      case LESS_OR_EQUAL:
        b.append("    (has_min_c " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
        b.append("    (= (minority_constraint " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
        break;
      case EQUAL:
        b.append("    (has_eq_c " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
        b.append("    (= (equality_constraint " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
        break;
      case NOT_EQUAL:
        b.append("    (has_ineq_c " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
        b.append("    (= (inequality_constraint " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
        break;

      default:
        break;
    }

    return b;
  }

  private StringBuilder getHasConditionString(Transition t, Condition c) {
    StringBuilder b = new StringBuilder();

    if (c.operator == null) return b;
    b.append("    (has_constraint " + c.activity + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");


  return b;
  }


  private StringBuilder buildGoals() {
    StringBuilder b = new StringBuilder();

    b.append("  ;; GOAL STATES\n");
    b.append("  (:goal (and\n");

    b.append("    (cur_t_state " + this.finalTraceState.name + ")\n");
    for (List<State> goalStates : this.goalAutomatonStates) {
      if (goalStates.size() == 1) {
        b.append("    (cur_s_state " + goalStates.get(0).name + ")\n");
      } else { // In case two or more

        b.append("    (or\n");
        for (State singleGoal : goalStates) {
          b.append("      (cur_s_state " + singleGoal.name + ")\n");
        }
        b.append("    )\n");
      }
    }

    if (this.recovery.getOptionName().toLowerCase().contains("discard")) {
      b.append("    (not (update-cost))\n");
    }

    b.append("    (not (failure))\n" +
            "  ))\n\n"
    );
    return b;
  }

}