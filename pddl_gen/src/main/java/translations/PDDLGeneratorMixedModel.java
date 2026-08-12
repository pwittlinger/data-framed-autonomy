package translations;

import model.Activity;
import model.Attribute;
import model.Condition;
import model.CostEnum;
import model.DeclareConstraint;
import model.DeclareModel;
import model.MixedModel;


import org.deckfour.xes.extension.std.XConceptExtension;
import Automaton.Automaton;
import Automaton.State;
import Automaton.StateEC;
import Automaton.Transition;
import Automaton.VariableSubstitution;
import Automaton.Pair;
import log.Event;

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
  
  public PDDLGeneratorMixedModel(MixedModel model) throws Exception {

    super(model.declareModel);
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
    s.append(this.buildObjectsString(attributes, assignments));

    s.append(this.buildSubstitutionValues(assignments, substitutions));
    s.append(this.buildActionCosts());
    s.append(this.buildTraceDeclaration(listOfEvents, attributes));
     s.append(this.buildTimeStamps(timeStamps));
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

    if (this.finalTraceState == null) {
      this.finalTraceState = new State("t0");
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
    ArrayList<String> attributes = this.mixedModel.declareModel.params;
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

    this.constraints.forEach(x -> b.append("    (= (violation_cost " + x.getConstraintName() + ") 1)\n"));
    for (String dpnConstraint : this.mixedModel.dpnConstraintNames) {
      b.append("    (= (violation_cost " + dpnConstraint + ") 1)\n");
    }

    /*
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
        value = value.replaceAll("[a-zA-Z]", ""); // Remove chars, use as if numbers (in case of enum types)

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
      b.append("    (initial_state " + s + ")\n");
      b.append("\n");
    }

    b.append("\n");

    for (String s : this.mixedModel.allFailureStates) {
      b.append("    (failure_state " + s + ")\n");
    }

    b.append("\n");
    
    for ( ArrayList<String> acceptingStates : this.mixedModel.allAcceptingStates) {
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
          //if (aut.getConstraint().getActivationTimeConditions() != null) {
           // b.append("    (has_time_conditions " + aName + " activation " + aut.getConstraint().getActivationTimeConditions()[0] + " " + aut.getConstraint().getActivationTimeConditions()[1] + ")\n");
          //}
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

    for ( ArrayList<String> acceptingStates : this.mixedModel.allAcceptingStates) {
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

}