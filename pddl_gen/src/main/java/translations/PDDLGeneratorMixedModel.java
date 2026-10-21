package translations;

import model.Activity;
import model.Attribute;
import model.Condition;
import model.CostEnum;
import model.DeclareConstraint;
import model.DeclareModel;
import model.MixedModel;
import model.RecoveryStrategy;


import org.deckfour.xes.extension.std.XConceptExtension;
import Automaton.Automaton;
import Automaton.State;
import Automaton.StateEC;
import Automaton.Transition;
import Automaton.VariableSubstitution;
import Automaton.Pair;
import log.Event;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PDDLGeneratorMixedModel extends PDDLGenerator{

  //private final Map<CostEnum, Integer> costs;
  private final Map<Pair<Activity, CostEnum>, Integer> costs;
  // NOTE Define action costs above ^^^
  private final HashMap<String, Activity> activities;
  private final ArrayList<DeclareConstraint> constraints;
  private ArrayList<Automaton> constraintAutomatons;
  private List<List<State>> goalAutomatonStates;
  private State finalTraceState;
  public MixedModel mixedModel;
  // private final ArrayList<Transition> relevantTransitions;

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
  private final BigDecimal waitingCostFactor;

  public PDDLGeneratorMixedModel(MixedModel model, RecoveryStrategy recovery, BigDecimal waitingCost) throws Exception {

    super(model.declareModel, recovery);
    this.waitingCostFactor = waitingCost.divide(DEFAULT_WAITING_COST, MathContext.DECIMAL64).stripTrailingZeros();
    // Get set costs, or use default ones
    //Map<CostEnum, Integer> costs = model.declareModel.getCosts();
    Map<Pair<Activity, CostEnum>, Integer> costs = model.getCosts();
    if (costs != null) {
      this.costs = costs;
    } else {
      this.costs = null;
    }

    this.mixedModel = model;
    //this.activities = model.getActivities();
    this.activities = model.declareModel.getActivities();
    this.constraints = model.declareModel.getDeclareConstraints();
    this.constraintAutomatons = model.constraintAutomatons;
    //this.constraints = model.getDeclareConstraints();
    //this.constraintAutomatons = new ArrayList<>();
    //this.goalAutomatonStates = new ArrayList<>();
    //this.prepareAutomatonStates();
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
    s.append(PDDLGeneratorMixedModel.HEADER_STRING);
    s.append(this.buildObjectsString(attributes, assignments, substitutions));

    s.append(this.buildSubstitutionValues(assignments, substitutions));
    s.append(this.buildActionCosts());
    s.append(this.buildHardConstraints());
    s.append(this.buildTraceDeclaration(listOfEvents, attributes));
    if (!this.mixedModel.isJsonCostFile) {
      s.append(this.buildTimeStamps(timeStamps));
    }

    if (!this.mixedModel.allResources.isEmpty()) {
      s.append(this.buildResourceAlloc());
    }
    
    s.append(this.buildAutomatons(finalAutomatonStates));
   

    s.append(this.buildGoals());
    s.append(PDDLGeneratorMixedModel.FOOTER_STRING);
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

  private StringBuilder buildObjectsString(Map<Event, Map<Attribute, String>> attributeAssignments, Map<String, Integer> variables, Set<VariableSubstitution> substitutions) {
    StringBuilder b = new StringBuilder();
    b.append("  (:objects\n");

    // TRACE STATES
    b.append("    ");
    attributeAssignments.keySet().forEach(x -> b.append(x.getName() + " "));
    b.append(this.finalTraceState.name + " ");
    b.append("- trace_state\n");


    // AUTOMATON STATES

    // Adding all Automaton States into the header
    b.append("    ");
    this.mixedModel.allAutomatonStates.forEach(x -> {
      b.append(x + " ");
    });

    b.append("- automaton_state\n");

    // ACTIVITIES
    Set<String> activitySet = new HashSet<>(this.mixedModel.activities.values());
    b.append("    ");
    activitySet.forEach(x -> b.append(x + " "));
    b.append("- activity\n");

    // ATTRIBUTES
    /*
    Set<String> attributes = attributeAssignments.values()
      .stream()
      .flatMap(x -> x.keySet().stream())
      .map(x -> x.getName())
      .collect(Collectors.toSet());
    */
    Set<String> attributes = collectParameterNames(this.mixedModel.declareModel.params, substitutions);
    b.append("    ");
    attributes.forEach(x -> b.append(x + " "));
    if (attributes.size() > 0) {
      b.append("- parameter_name\n"); 
    }
    

    b.append("    ");
    variables.keySet().forEach(x -> b.append(x + " "));
    b.append("- value_name\n");

    b.append("    ");
    this.constraints.forEach(x -> b.append(x.getConstraintName() + " "));
    for (String dpnConstraint : this.mixedModel.dpnConstraintNames) {
      b.append(dpnConstraint + " ");
    }
    b.append("- constraint\n");

    if (this.mixedModel.allResources.size() > 0) {
      b.append("    ");
      this.mixedModel.allResources.forEach(x -> b.append(x + " "));
      b.append("- resource\n");
    }

    b.append("  )\n");
    return b;
  }

  private StringBuilder buildSubstitutionValues(Map<String, Integer> variables, Set<VariableSubstitution> substitutions) {
    StringBuilder b = new StringBuilder();

    b.append("  (:init\n\n");
    b.append("    ; Initialize plan cost. Some planners might need this explicitly\n");
    b.append("    (= (total_cost) 0)\n\n");

    if (!this.mixedModel.isJsonCostFile) {
      b.append("    (= (current_timestamp) 0)\n");
      // Plain notation, toString would print small or large values in scientific notation (e.g. 1E-5)
      b.append("    (= (waiting_cost) " + this.waitingCostFactor.toPlainString() + ")\n\n");
    }
    
    b.append("    ;; SUBSTITUTION VARIABLES\n");

    for (Map.Entry<String, Integer> entry : variables.entrySet()) {
      b.append("    (= (variable_value " + entry.getKey() + ") " + entry.getValue() + ")\n");
    }
    b.append("\n");
    for (VariableSubstitution sub : substitutions) {
      if (this.mixedModel.activities.get(sub.activityName) != null) {
        b.append("    (has_substitution_value " + sub.variableName + " " + this.mixedModel.activities.get(sub.activityName) + " " + sub.categoryName + ")\n");
      }
      //b.append("    (has_substitution_value " + sub.variableName + " " + sub.activityName + " " + sub.categoryName + ")\n");
      //b.append("    (has_substitution_value " + sub.variableName + " " + this.mixedModel.activities.get(sub.activityName) + " " + sub.categoryName + ")\n");
      //b.append("    (has_substitution_value " + sub.variableName + " " + this.mixedModel.activities.get(sub.activityName) + " " + sub.categoryName + ")\n");
    }
    b.append("\n");

    return b;
  }
  private StringBuilder buildActionCosts() {
    StringBuilder b = new StringBuilder();
    b.append("    ; Action costs\n");

    this.constraints.forEach(x -> b.append("    (= (violation_cost " + x.getConstraintName() + ") " + this.mixedModel.constraintViolation.get(x.getConstraintName()) + ")\n"));
    for (String dpnConstraint : this.mixedModel.dpnConstraintNames) {
      b.append("    (= (violation_cost " + dpnConstraint + ") " + this.mixedModel.constraintViolation.get(dpnConstraint) + ")\n");
    }

    // Only runs when no resource allocation is given
    if (this.mixedModel.allResources.isEmpty() && !this.costs.isEmpty()) {
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
    }

    // This already has a check for empty resource allocation in the definition.
    // Not a pretty check, but it works.
    b.append(this.buildResourceCosts());

    

    
    b.append("\n");

    return b;
  }

  // Marks the constraints set to x in the violation cost file. Nothing is written if there are none.
  private StringBuilder buildHardConstraints() {
    StringBuilder b = new StringBuilder();
    List<String> constraintNames = new ArrayList<>();
    this.constraints.forEach(x -> constraintNames.add(x.getConstraintName()));
    constraintNames.addAll(this.mixedModel.dpnConstraintNames);

    for (String constraintName : constraintNames) {
      if (this.mixedModel.hardConstraints.contains(constraintName)) {
        b.append("    (is_hard " + constraintName + ")\n");
      }
    }
    if (b.length() > 0) {
      b.insert(0, "    ;; HARD CONSTRAINTS\n");
      b.append("\n");
    }
    return b;
  }

  private StringBuilder buildTraceDeclaration(List<Event> events, Map<Event, Map<Attribute, String>> assignments) {
    StringBuilder b = new StringBuilder();
    b.append("    ;; TRACE DECLARATION\n");

    if (events.isEmpty()) {
       b.append("    (recovery_finished)\n");
       b.append("    (cur_t_state " + this.finalTraceState.name + ")\n");
       b.append("    (final_t_state " +  this.finalTraceState.name + ")\n");
      return b;
    }

    b.append("    (cur_t_state " + events.get(0).getName() + ")\n");
    b.append("    (final_t_state " +  this.finalTraceState.name + ")\n");
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
      //b.append("    (trace " + cur.getName() + " " + activity + " " + nextName + ")\n");
      b.append("    (trace " + cur.getName() + " " + this.mixedModel.activities.get(activity) + " " + nextName + ")\n");
      for(Map.Entry<Attribute, String> singleAssignment : assignments.get(cur).entrySet()) {
        String value = singleAssignment.getValue();
        String attName = singleAssignment.getKey().getName();

        if (!this.mixedModel.declareModel.params.contains(attName)) {
          continue;
        }
        value = this.encodeTraceValue(attName, value);

        //b.append("    (has_parameter " + activity + " " + singleAssignment.getKey().getName() + " " + cur.getName() + " " + nextName + ")\n");
        //b.append("    (= (trace_parameter " + activity + " " + singleAssignment.getKey().getName() + " " + cur.getName() + " " + nextName + ") " + value + ")\n");

        b.append("    (has_parameter " + this.mixedModel.activities.get(activity) + " " + singleAssignment.getKey().getName() + " " + cur.getName() + " " + nextName + ")\n");
        b.append("    (= (trace_parameter " + this.mixedModel.activities.get(activity) + " " + singleAssignment.getKey().getName() + " " + cur.getName() + " " + nextName + ") " + value + ")\n");
      }
      b.append("\n");
    }

    return b;
  }

  public StringBuilder buildAutomatons(List<State> finalAutomatonStates) {
    StringBuilder b = new StringBuilder();
    b.append("    ;; AUTOMATON STATES\n");

    for (String s : this.mixedModel.allInitialStates) {
      b.append("    (cur_s_state " + s + ")\n");
      // A hard constraint is never reset: without initial_state the reset action has no state to move it back to
      if (!this.mixedModel.isHardConstraintState(s)) {
        b.append("    (initial_state " + s + ")\n");
      }
      b.append("\n");
    }

    b.append("\n");

    for (String s : this.mixedModel.allFailureStates) {
      if (this.isDeclaredFailureState(s)) {
        b.append("    (failure_state " + s + ")\n");
      }
    }

    b.append("\n");

    for (int i = 0; i < this.mixedModel.allAcceptingStates.size(); i++) {
          List<String> acceptingStates = this.acceptingStates(i);
          if (acceptingStates.size() == 1) {
            b.append("    (goal_state " + acceptingStates.get(0) + ")\n");
          } else { // In case two or more

            
            for (String singleGoal : acceptingStates) {
              b.append("    (goal_state " + singleGoal+ ")\n");

            }
           
          }
        }

    b.append("\n");

    for (ArrayList<String> automatonElement : this.mixedModel.allAutomatonStrings){
      String activationState = automatonElement.get(0);
      String activity = automatonElement.get(1);
      String targetState = automatonElement.get(2);

      if (activity == null || activity.isEmpty()) {
        continue;
      }

      b.append("    (automaton " + activationState + " " + activity + " " + targetState + ")\n");

    }


  
     for (Automaton aut : this.mixedModel.constraintAutomatons) {
        // Set the "associated" relation between states and constraints
        b.append("\n");
  	    List<StateEC> allStates = aut.getStatesEC();

        String aName = aut.getConstraint().getConstraintName();
        ArrayList<String> clocks =  aut.getConstraint().getClockConditions();
  	      
  	    for (StateEC g : allStates) {
  	    	  b.append("    (associated " + g.name + " " + aName + ")\n");
        }


        if (!this.mixedModel.isJsonCostFile) {
          for(String cString : clocks) {
            if (cString.contains("sDEC")) {
              b.append(cString);
            }
          }

          boolean setClock = false;

          for (Transition transition : aut.getTransitions()) {
            if ((transition.getMinTimeCondition() > -1.0) && (transition.getMaxTimeCondition() > 0.0)) {
              setClock = true;
              b.append("    (= (min_t_condition " + transition.getActiviationState().name + " " + this.mixedModel.activities.get(transition.getActivity()) + " " + transition.getTargetState().name + ") " + transition.getMinTimeCondition() + ")\n");
              b.append("    (= (max_t_condition " + transition.getActiviationState().name + " " + this.mixedModel.activities.get(transition.getActivity()) + " " + transition.getTargetState().name + ") " + transition.getMaxTimeCondition() + ")\n");
            }

          }

          if (setClock == true) {
            b.append("    (= (start_clock " + aName + ") 0)\n");
          }
        }

      }

    b.append("\n");
    for (int i = 0; i < this.mixedModel.allPetriNetStatesByDpn.size(); i++) {
      String dpnConstraint = this.mixedModel.dpnConstraintNames.get(i);
      for (String pnState : this.mixedModel.allPetriNetStatesByDpn.get(i)) {
        b.append("    (associated " + pnState + " " + dpnConstraint + ")\n");
      }
    }
    b.append("\n");

    for (String condition : this.mixedModel.conditionStrings) {
      b.append(condition);
      b.append("\n");
    }

    // Close init
    b.append("  )\n");
    return b;
  }

  private StringBuilder buildGoals() {
    StringBuilder b = new StringBuilder();

    b.append("  ;; GOAL STATES\n");
    b.append("  (:goal (and\n");

    b.append("    (cur_t_state " + this.finalTraceState.name + ")\n");

    for (int i = 0; i < this.mixedModel.allAcceptingStates.size(); i++) {
      List<String> acceptingStates = this.acceptingStates(i);
      if (acceptingStates.size() == 1) {
        b.append("    (cur_s_state " + acceptingStates.get(0) + ")\n");
      } else { // In case two or more

        b.append("    (or\n");
        for (String singleGoal : acceptingStates) {
          b.append("      (cur_s_state " + singleGoal+ ")\n");
        }
        b.append("    )\n");
      }
    }

  
    b.append("    (not (failure))\n" +
            //"    (not (after_change))\n" + //
            //"    (not (after_add))\n" + //
            //"    (not (after_sync))\n" + //
            "  ))\n\n"
    );
    return b;
  }


  // Accepting states of the i-th automaton (Petri nets first, then DECLARE constraints). With the Discard strategy
  // a violated constraint is ignored, so its failure states are accepting too. They keep their failure_state fact,
  // so reaching them is still recognised as a violation. A hard constraint is not discarded, its failure states
  // stay non-accepting.
  private List<String> acceptingStates(int automatonIndex) {
    List<String> acceptingStates = new ArrayList<>(this.mixedModel.allAcceptingStates.get(automatonIndex));
    if (this.recovery.acceptsFailureStates()) {
      for (String failureState : this.mixedModel.allFailureStatesByAutomaton.get(automatonIndex)) {
        if (!acceptingStates.contains(failureState) && !this.mixedModel.isHardConstraintState(failureState)) {
          acceptingStates.add(failureState);
        }
      }
    }
    return acceptingStates;
  }

  // The Skip domain does not move an automaton into a failure_state, it skips the event for that constraint.
  // A hard constraint is not skipped, so its failure states are written as ordinary states: the automaton moves
  // into them and, as they are non-accepting and cannot be left, the violation makes the problem unsolvable.
  private boolean isDeclaredFailureState(String state) {
    return !(this.recovery.skipsFailureStates() && this.mixedModel.isHardConstraintState(state));
  }

   public String activityMapping() {
    StringBuilder s = new StringBuilder();
    for (String act :this.mixedModel.activities.keySet()) {
        s.append(act + ":"+this.mixedModel.activities.get(act)+"\n");
    }

    return s.toString(); //
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

  public StringBuilder buildResourceCosts() {
    StringBuilder b = new StringBuilder();

    if (this.mixedModel.allResources.isEmpty()){
      return b;
    }

    for (Pair<Activity, String> a: this.mixedModel.resourceMap.keySet()) {
        //System.out.println("buildResourceCosts"+this.mixedModel.activities.get(a.getKey().getName()));

        b.append("    (= (activity_cost " + a.getKey().getName() + " " + a.getValue() + ") " + this.mixedModel.resourceMap.get(a)+ ")\n");
    }

    return b;
  }

  public StringBuilder buildResourceAlloc() {
    StringBuilder b = new StringBuilder();

    if (this.mixedModel.allResources.isEmpty()){
      return b;
    }

    for (Pair<Activity, String> a: this.mixedModel.resourceMap.keySet()) {
        //System.out.println("buildResourceCosts"+this.mixedModel.activities.get(a.getKey().getName()));

        b.append("    (can_work " + a.getKey().getName() + " " + a.getValue() + ")\n");
    }

    return b;
  }



}