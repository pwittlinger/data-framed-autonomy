package translations;

import model.Activity;
import model.Attribute;
import model.Condition;
import model.CostEnum;
import model.DeclareConstraint;
import model.DeclareModel;

import org.deckfour.xes.extension.std.XConceptExtension;
import Automaton.Automaton;
import Automaton.Pair;
import Automaton.State;
import Automaton.Transition;
import Automaton.VariableSubstitution;
import log.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PDDLGenerator {

  private final Map<Pair<Activity, CostEnum>, Integer> costs;
  
  // NOTE Define action costs above ^^^
  private final HashMap<String, Activity> activities;
  private final ArrayList<DeclareConstraint> constraints;
  private final ArrayList<String> params;
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
  
  public PDDLGenerator(DeclareModel model) throws Exception {

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
    this.constraintAutomatons = new ArrayList<>();
    this.goalAutomatonStates = new ArrayList<>();
    this.prepareAutomatonStates();
  }
  private void prepareAutomatonStates() {
    int index = 1;
    for (DeclareConstraint constraint : this.constraints) {
      String prefix = "s" + index++ + "_";
      Automaton newAutomaton = new Automaton(activities.keySet(), prefix, constraint);
      this.constraintAutomatons.add(newAutomaton);
      
      // Automaton might have more than one goal state. In that case, we'll put the goal states with an "or" between them.
      List<State> goalStates = newAutomaton.getStates().stream()
                                  .filter(x -> x.isGoal)
                                  .toList();

      this.goalAutomatonStates.add(goalStates);
    }
  }

  public String defineProblem(ArrayList<Event> listOfEvents, Map<String, Integer> assignments, Set<VariableSubstitution> substitutions, ArrayList<Double> timeStamps) {

    Map<Event, Map<Attribute, String>> attributes = this.parseEvents(listOfEvents);
    List<State> finalAutomatonStates = new ArrayList<>();

    StringBuilder s = new StringBuilder();
    s.append(PDDLGenerator.HEADER_STRING);
    s.append(this.buildObjectsString(attributes, assignments));

    s.append(this.buildSubstitutionValues(assignments, substitutions));
    //s.append(this.buildActionCosts());
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
    return assignments;
  }

  private StringBuilder buildObjectsString(Map<Event, Map<Attribute, String>> attributeAssignments, Map<String, Integer> variables) {
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
    Set<String> attributes = attributeAssignments.values()
      .stream()
      .flatMap(x -> x.keySet().stream())
      .map(x -> x.getName())
      .filter(this.params::contains)
      .collect(Collectors.toSet());
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
    b.append("    (= (current_timestamp) 0)\n\n");
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

    /*/
    for (Map.Entry<Pair<Activity, CostEnum>, Integer> cost : this.costs.entrySet()) {
      switch (cost.getKey().getValue()) {
        case CHANGE:
          b.append("    (= (change_cost " + cost.getKey().getKey().getName() + ") " + cost.getValue() + ")\n");
          break;
        case ADD:
          b.append("    (= (add_cost " + cost.getKey().getKey().getName() + ") " + cost.getValue() + ")\n");
          break;
        case SET:
          b.append("    (= (set_cost " + cost.getKey().getKey().getName() + ") " + cost.getValue() + ")\n");
          break;
        case DELETE:
          b.append("    (= (delete_cost " + cost.getKey().getKey().getName() + ") " + cost.getValue() + ")\n");
          break;
      }
    }
      */
    b.append("\n");

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

        String value = singleAssignment.getValue();
        value = value.replaceAll("[a-zA-Z]", ""); // Remove chars, use as if numbers (in case of enum types)

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

      for (State state : aut.getStates()) {
        b.append("    (associated " + state.name + " " + constraintName + ")\n");

        if (state.isInitial) {
          b.append("    (cur_s_state " + state.name + ")\n");
          b.append("    (initial_state " + state.name + ")\n");
        }
        if (state.isFailure) {
          b.append("    (failure_state " + state.name + ")\n");
        }
        if (state.isGoal) {
          b.append("    (goal_state " + state.name + ")\n");
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

    b.append("    (not (failure))\n" +
            "  ))\n\n"
    );
    return b;
  }

  public String defineDomain() {
    return "(define (domain trace-alignment)\n" + //
        "\n" + //
        "  (:requirements :strips :typing :equality :adl :fluents :action-costs)\n" + //
        "\n" + //
        "  (:types activity automaton_state trace_state parameter_name value_name)\n" + //
        "\n" + //
        "\n" + //
        "  ;; Majority: >=\n" + //
        "  ;; Minority: <=\n" + //
        "  ;; Interval: [, ]\n" + //
        "  ;; Equality: ==\n" + //
        "  ;; Inequality: !=\n" + //
        "  ;; If you want only > x, do conditions >= x && != x\n" + //
        "\n" + //
        "  (:predicates \n" + //
        "    ;; TRACES AND AUTOMATONS\n" + //
        "    (trace ?t1 - trace_state ?a - activity ?t2 - trace_state)\n" + //
        "    (automaton ?s1 - automaton_state ?a - activity ?s2 - automaton_state)\n" + //
        "    (cur_t_state ?t - trace_state)\n" + //
        "    (cur_s_state ?s - automaton_state)\n" + //
        "\n" + //
        "    ;; PARAMETER AND CONSTRAINT DECLARATION\n" + //
        "    (has_parameter ?a - activity ?pn - parameter_name ?t1 - trace_state ?t2 - trace_state)\n" + //
        "    (has_maj_c ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (has_min_c ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (has_interval_c ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (has_eq_c ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (has_ineq_c ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "\n" + //
        "    (invalid ?s1 - automaton_state ?a - activity ?s2 - automaton_state)\n" + //
        "    (complete_sync ?a - activity)\n" + //
        "    (after_sync)\n" + //
        "    (after_change)\n" + //
        "    (adding_value ?a - activity ?t1 - trace_state)\n" + //
        "    (after_add)\n" + //
        "\n" + //
        "    ; Declare this to indicate that such activity-parameter-value assignment exists.\n" + //
        "    (has_substitution_value ?vn - value_name ?a - activity ?pn - parameter_name)\n" + //
        "    ; Indicates that the new activity has a new (defined) parameter.\n" + //
        "    (has_added_parameter ?a - activity ?par - parameter_name ?t1 - trace_state)\n" + //
        "\n" + //
        "    ; Used in the problem definition to indicate that this state must not be reached. In that case, the trace is **automatically** failed.\n" + //
        "    (failure_state ?s - automaton_state)\n" + //
        "    ; Used to indicate that the trace alignment couldn't possibly complete: prune -> less branching -> heap won't kaboom.\n" + //
        "    (failure)\n" + //
        "  )\n" + //
        "\n" + //
        "  (:functions\n" + //
        "    (total_cost)\n" + //
        "\n" + //
        "    ; There exists a value connected to the activity that occures between the two trace states.\n" + //
        "    (trace_parameter ?a - activity ?pn - parameter_name ?t1 - trace_state ?t2 - trace_state)\n" + //
        "\n" + //
        "    (majority_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (minority_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (interval_constraint_lower ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (interval_constraint_higher ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (equality_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "    (inequality_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "\n" + //
        "    ; Costs\n" + //
        "    (change_cost ?a - activity)\n" + //
        "    (add_cost ?a - activity)\n" + //
        "    (set_cost ?a - activity)\n" + //
        "    (delete_cost ?a - activity)\n" + //
        "\n" + //
        "    ;; VARIABLES SUBSTITUTION / ADDITION\n" + //
        "    (variable_value ?var - value_name)\n" + //
        "    (added_parameter ?a - activity ?par - parameter_name ?t1 - trace_state)\n" + //
        "  )\n" + //
        "\n" + //
        "  ;; SYNC OPERATIONS\n" + //
        "  ;; ----------------------------------------------------------------------------------------------------\n" + //
        "  (:action sync\n" + //
        "    :parameters (?t1 - trace_state ?a - activity ?t2 - trace_state)\n" + //
        "    :precondition (and \n" + //
        "      (cur_t_state ?t1) \n" + //
        "      (trace ?t1 ?a ?t2) \n" + //
        "      (not (after_sync))\n" + //
        "      (not (after_add))\n" + //
        "      (not (failure)))\n" + //
        "    :effect (and \n" + //
        "      (increase (total_cost) 0)\n" + //
        "      (not (cur_t_state ?t1)) \n" + //
        "      (cur_t_state ?t2)\n" + //
        "      (not (after_change))\n" + //
        "      (after_sync)\n" + //
        "      (complete_sync ?a)\n" + //
        "\n" + //
        "      ; Check if case parameter is missing\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If parameter is missing\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (not (has_parameter ?a ?pn ?t1 ?t2))\n" + //
        "          (or\n" + //
        "            (has_maj_c ?a ?pn ?s1 ?s2)\n" + //
        "            (has_min_c ?a ?pn ?s1 ?s2)\n" + //
        "            (has_interval_c ?a ?pn ?s1 ?s2)\n" + //
        "            (has_eq_c ?a ?pn ?s1 ?s2)\n" + //
        "            (has_ineq_c ?a ?pn ?s1 ?s2)\n" + //
        "          ))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      ; Check for all conditions if a parameter is missing is not respecting a constraint\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not >=\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_parameter ?a ?pn ?t1 ?t2)\n" + //
        "          (has_maj_c ?a ?pn ?s1 ?s2)\n" + //
        "          (< (trace_parameter ?a ?pn ?t1 ?t2) (majority_constraint ?a ?pn ?s1 ?s2)))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not <=\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_parameter ?a ?pn ?t1 ?t2) \n" + //
        "          (has_min_c ?a ?pn ?s1 ?s2)\n" + //
        "          (> (trace_parameter ?a ?pn ?t1 ?t2) (minority_constraint ?a ?pn ?s1 ?s2)))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not [,]\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_parameter ?a ?pn ?t1 ?t2) \n" + //
        "          (has_interval_c ?a ?pn ?s1 ?s2)\n" + //
        "          (or\n" + //
        "            (< (trace_parameter ?a ?pn ?t1 ?t2) (interval_constraint_lower ?a ?pn ?s1 ?s2))\n" + //
        "            (> (trace_parameter ?a ?pn ?t1 ?t2) (interval_constraint_higher ?a ?pn ?s1 ?s2))\n" + //
        "          ))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not =\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_parameter ?a ?pn ?t1 ?t2) \n" + //
        "          (has_eq_c ?a ?pn ?s1 ?s2)\n" + //
        "          (or \n" + //
        "            (< (trace_parameter ?a ?pn ?t1 ?t2) (equality_constraint ?a ?pn ?s1 ?s2))\n" + //
        "            (> (trace_parameter ?a ?pn ?t1 ?t2) (equality_constraint ?a ?pn ?s1 ?s2))\n" + //
        "          ))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not !=\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_parameter ?a ?pn ?t1 ?t2) \n" + //
        "          (has_ineq_c ?a ?pn ?s1 ?s2)\n" + //
        "          (= (trace_parameter ?a ?pn ?t1 ?t2) (inequality_constraint ?a ?pn ?s1 ?s2)))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "    )\n" + //
        "  )\n" + //
        "\n" + //
        "  (:action move_automatons\n" + //
        "    :parameters (?a - activity)\n" + //
        "    :precondition (and\n" + //
        "      (not (after_change))\n" + //
        "      (after_sync)\n" + //
        "      (complete_sync ?a)\n" + //
        "      (not (failure))\n" + //
        "      (not (after_add))\n" + //
        "    )\n" + //
        "    :effect (and \n" + //
        "      (increase (total_cost) 0)\n" + //
        "      (not (after_sync))\n" + //
        "      (not (complete_sync ?a))\n" + //
        "\n" + //
        "      (forall (?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        (when (and\n" + //
        "          (not (invalid ?s1 ?a ?s2))\n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (cur_s_state ?s1)\n" + //
        "          (not (failure_state ?s2))\n" + //
        "        ) (and\n" + //
        "          (not (cur_s_state ?s1))\n" + //
        "          (cur_s_state ?s2)\n" + //
        "        ))\n" + //
        "      )\n" + //
        "      (forall (?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        (when (and\n" + //
        "          (not (invalid ?s1 ?a ?s2))\n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (cur_s_state ?s1)\n" + //
        "          (failure_state ?s2)\n" + //
        "        ) (and\n" + //
        "          (not (cur_s_state ?s1))\n" + //
        "          (cur_s_state ?s2)\n" + //
        "          (failure)\n" + //
        "        ))\n" + //
        "      )\n" + //
        "\n" + //
        "      ;; Clean invalid arcs. In case the same activity repeats, we might have different values and thus different invalid automatons.\n" + //
        "      (forall (?s1 - automaton_state ?s2 - automaton_state) \n" + //
        "        (when (after_sync) ; Without the when enclosing, it crashes.\n" + //
        "          (not (invalid ?s1 ?a ?s2))))\n" + //
        "      )\n" + //
        "  )\n" + //
        "\n" + //
        "  ;; SUBSTITUTION\n" + //
        "  ;; ----------------------------------------------------------------------------------------------------\n" + //
        "  (:action change_value\n" + //
        "    :parameters (?a - activity ?t1 - trace_state ?t2 - trace_state ?pn - parameter_name ?vn - value_name)\n" + //
        "    :precondition (and \n" + //
        "      (trace ?t1 ?a ?t2)\n" + //
        "      (cur_t_state ?t1)\n" + //
        "      (not (failure))\n" + //
        "      (not (after_sync))\n" + //
        "      (not (after_add))\n" + //
        "      (has_substitution_value ?vn ?a ?pn)\n" + //
        "      (has_parameter ?a ?pn ?t1 ?t2)\n" + //
        "    )\n" + //
        "    :effect (and \n" + //
        "      (after_change)\n" + //
        "      (increase (total_cost) (change_cost ?a))\n" + //
        "      (has_parameter ?a ?pn ?t1 ?t2)\n" + //
        "      (assign (trace_parameter ?a ?pn ?t1 ?t2) (variable_value ?vn))\n" + //
        "  ))\n" + //
        "\n" + //
        "  ;; ADDITION\n" + //
        "  ;; ----------------------------------------------------------------------------------------------------\n" + //
        "  (:action add\n" + //
        "    :parameters (?a - activity ?t1 - trace_state)\n" + //
        "    :precondition (and \n" + //
        "      (cur_t_state ?t1) \n" + //
        "      (not (after_change))\n" + //
        "      (not (after_sync))\n" + //
        "      (not (failure))\n" + //
        "      (not (after_add))\n" + //
        "    )\n" + //
        "    :effect (and \n" + //
        "      (increase (total_cost) (add_cost ?a))\n" + //
        "      (adding_value ?a ?t1)\n" + //
        "      (after_add)\n" + //
        "  ))\n" + //
        "\n" + //
        "  (:action set_value\n" + //
        "    :parameters (?a - activity ?t1 - trace_state ?pn - parameter_name ?vn - value_name)\n" + //
        "    :precondition (and \n" + //
        "      (adding_value ?a ?t1)\n" + //
        "      (cur_t_state ?t1)\n" + //
        "      (not (failure))\n" + //
        "      (not (after_change))\n" + //
        "      (not (after_sync))\n" + //
        "      (after_add)\n" + //
        "      (has_substitution_value ?vn ?a ?pn)\n" + //
        "    )\n" + //
        "    :effect (and \n" + //
        "      (increase (total_cost) (set_cost ?a))\n" + //
        "      (has_added_parameter ?a ?pn ?t1)\n" + //
        "      (assign (added_parameter ?a ?pn ?t1) (variable_value ?vn))\n" + //
        "  ))\n" + //
        "\n" + //
        "  (:action check_added_variables\n" + //
        "    :parameters (?a - activity ?t1 - trace_state)\n" + //
        "    :precondition (and \n" + //
        "      (adding_value ?a ?t1)\n" + //
        "      (cur_t_state ?t1)\n" + //
        "      (not (failure))\n" + //
        "      (not (after_change))\n" + //
        "      (not (after_sync))\n" + //
        "      (after_add)\n" + //
        "    )\n" + //
        "    :effect (and \n" + //
        "\n" + //
        "      (not (adding_value ?a ?t1))\n" + //
        "      (not (after_add))\n" + //
        "      (after_sync)\n" + //
        "      (complete_sync ?a)\n" + //
        "\n" + //
        "      ; Check in case parameter is missing\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If parameter is missing\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (not (has_added_parameter ?a ?pn ?t1))\n" + //
        "          (or\n" + //
        "            (has_maj_c ?a ?pn ?s1 ?s2)\n" + //
        "            (has_min_c ?a ?pn ?s1 ?s2)\n" + //
        "            (has_interval_c ?a ?pn ?s1 ?s2)\n" + //
        "            (has_eq_c ?a ?pn ?s1 ?s2)\n" + //
        "            (has_ineq_c ?a ?pn ?s1 ?s2)\n" + //
        "          ))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      ; Check for all conditions if there is something to look for\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not >=\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_added_parameter ?a ?pn ?t1)\n" + //
        "          (has_maj_c ?a ?pn ?s1 ?s2)\n" + //
        "          (< (added_parameter ?a ?pn ?t1) (majority_constraint ?a ?pn ?s1 ?s2)))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not <=\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_added_parameter ?a ?pn ?t1)\n" + //
        "          (has_min_c ?a ?pn ?s1 ?s2)\n" + //
        "          (> (added_parameter ?a ?pn ?t1) (minority_constraint ?a ?pn ?s1 ?s2)))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not [,]\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_added_parameter ?a ?pn ?t1)\n" + //
        "          (has_interval_c ?a ?pn ?s1 ?s2)\n" + //
        "          (or\n" + //
        "            (< (added_parameter ?a ?pn ?t1) (interval_constraint_lower ?a ?pn ?s1 ?s2))\n" + //
        "            (> (added_parameter ?a ?pn ?t1) (interval_constraint_higher ?a ?pn ?s1 ?s2))\n" + //
        "          ))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not =\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_added_parameter ?a ?pn ?t1)\n" + //
        "          (has_eq_c ?a ?pn ?s1 ?s2)\n" + //
        "          (or \n" + //
        "            (< (added_parameter ?a ?pn ?t1) (equality_constraint ?a ?pn ?s1 ?s2))\n" + //
        "            (> (added_parameter ?a ?pn ?t1) (equality_constraint ?a ?pn ?s1 ?s2))\n" + //
        "          ))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      )\n" + //
        "\n" + //
        "      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)\n" + //
        "        ; If not !=\n" + //
        "        (when (and \n" + //
        "          (automaton ?s1 ?a ?s2)\n" + //
        "          (has_added_parameter ?a ?pn ?t1) \n" + //
        "          (has_ineq_c ?a ?pn ?s1 ?s2)\n" + //
        "          (= (added_parameter ?a ?pn ?t1) (inequality_constraint ?a ?pn ?s1 ?s2)))\n" + //
        "            (invalid ?s1 ?a ?s2))\n" + //
        "      ))\n" + //
        "  )\n" + //
        "\n" + //
        "  ;; DELETION\n" + //
        "  ;; ----------------------------------------------------------------------------------------------------\n" + //
        "  (:action del\n" + //
        "    :parameters (?t1 - trace_state ?a - activity ?t2 - trace_state)\n" + //
        "    :precondition (and \n" + //
        "      (cur_t_state ?t1) \n" + //
        "      (trace ?t1 ?a ?t2) \n" + //
        "      (not (after_change))\n" + //
        "      (not (after_sync))\n" + //
        "      (not (failure))\n" + //
        "      (not (after_add))\n" + //
        "    )\n" + //
        "    :effect (and \n" + //
        "      (increase (total_cost) (delete_cost ?a))\n" + //
        "      (not (cur_t_state ?t1)) \n" + //
        "      (cur_t_state ?t2))\n" + //
        "  )\n" + //
        ")\n" + //
        "";
  }


}