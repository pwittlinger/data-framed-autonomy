package model;
import model.DeclareModel;
import model.Activity;
import model.Attribute;
import model.Condition;
import model.CostEnum;
import model.DeclareConstraint;
import model.DeclareModel;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
//import java.util.concurrent.locks.Condition;

import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.plugins.declareminer.PossibleNodes;

import Automaton.*;
//import Automaton.State;

import org.processmining.ltl2automaton.plugins.automaton.State;

import model.DataPetriNet;
import model.Attribute;


public class MixedModel {
    public DeclareModel declareModel;
    public ArrayList<DataPetriNet> dpnModels;
    public ArrayList<String> dpnConstraintNames;
    public HashMap<String, String> activities; //Mapping from activity/transition name :--> pddl name
    private Integer activityCounter = 0;
    public ArrayList<String> allInitialStates;
    public ArrayList<ArrayList<String>> allAcceptingStates;
    public ArrayList<String> allFailureStates;
	public ArrayList<Automaton> constraintAutomatons;
	public ArrayList<String> conditionStrings;
	public ArrayList<ArrayList<String>> allAutomatonStrings;
	public ArrayList<String> allAutomatonStates;
	public HashMap<String, Activity> activityObjects;
	public ArrayList<String> allPetriNetStates;
	public ArrayList<ArrayList<String>> allPetriNetStatesByDpn;
	private Map<Pair<Activity, CostEnum>, Integer> costs;
	
    

    public MixedModel(DataPetriNet dataPetriNet, DeclareModel declare) {
        this(List.of(dataPetriNet), declare);
    }

    public MixedModel(List<DataPetriNet> dataPetriNets, DeclareModel declare) {
        if (dataPetriNets == null || dataPetriNets.isEmpty()) {
            throw new IllegalArgumentException("At least one DataPetriNet is required.");
        }
        this.dpnModels = new ArrayList<>(dataPetriNets);
        this.declareModel = declare;
		this.constraintAutomatons = new ArrayList<>();
		this.dpnConstraintNames = new ArrayList<>();
		this.allInitialStates = new ArrayList<>();
		this.allAcceptingStates = new ArrayList<>();
		this.allFailureStates = new ArrayList<>();
		this.allAutomatonStrings = new ArrayList<>();
		this.conditionStrings = new ArrayList<>();
		this.activities = new HashMap<>();
		this.allAutomatonStates = new ArrayList<>();
		this.allPetriNetStates = new ArrayList<>();
		this.allPetriNetStatesByDpn = new ArrayList<>();

		this.mapAllActivities();
		this.prepareAutomatonStates();
		this.parseAutomatonStatesIntoList();
		this.buildAutomatons();
		this.activityObjects = this.mapActivityObjects();
    }

    private String dpnStatePrefix(int dpnIndex) {
        return this.dpnModels.size() == 1 ? "" : ("sPN" + dpnIndex + "_");
    }

    private String dpnConstraintName(int dpnIndex) {
        return this.dpnModels.size() == 1 ? "pn" : ("pn" + dpnIndex);
    }

    private String mappedActivity(String label) {
        if (label == null || label.isEmpty()) {
            return null;
        }
        return this.activities.get(label);
    }

    private void addAutomatonTransition(String sourceState, String label, String targetState) {
        String mappedActivity = mappedActivity(label);
        if (mappedActivity == null) {
            return;
        }
        ArrayList<String> automatonItem = new ArrayList<>();
        automatonItem.add(sourceState);
        automatonItem.add(mappedActivity);
        automatonItem.add(targetState);
        this.allAutomatonStrings.add(automatonItem);
    }

    public void mapAllActivities(){
		// Map activities to strings
		// Shared activities between the PN and DECLARE models have the same index
        HashMap<String, Activity> declareActivities = this.declareModel.getActivities();

        for (DataPetriNet dpnModel : this.dpnModels) {
            for (String act : dpnModel.activities) {
                if (!this.activities.containsKey(act)) {
                    this.activities.put(act, "a" + this.activityCounter);
                    this.activityCounter += 1;
                }
            }
        }

        for (String act : declareActivities.keySet()){
            if (!this.activities.containsKey(act)){
                this.activities.put(act, "a"+this.activityCounter);
                this.activityCounter += 1;
            }
        }

    }

    public void mapAllVariables(){
		// Variables from the DECLARE model are already mapped in the 
        for (DataPetriNet dpnModel : this.dpnModels) {
            Collection<DataElement> dpnVars = dpnModel.dataPetriNet.getVariables();
        }

    }


    public void parseAutomatonStatesIntoList(){

        // First handling Petri Net states — one automaton per DPN
        int dpnIndex = 1;
        for (DataPetriNet dpnModel : this.dpnModels) {
            String statePrefix = dpnStatePrefix(dpnIndex);
            this.dpnConstraintNames.add(dpnConstraintName(dpnIndex));
            ArrayList<String> dpnStates = new ArrayList<>();

            dpnModel.executableAutomaton.ini();
            PossibleNodes initialState = dpnModel.executableAutomaton.currentState();

            for (State stt : initialState) {
                String stateName = statePrefix + stt.toString();
                this.allInitialStates.add(stateName);
            }

            ArrayList<String> dpnAccepting = new ArrayList<>();

            for (State st : dpnModel.executableAutomaton.states()) {
                String stateName = statePrefix + st.toString();
                if (st.isAccepting()) {
                    dpnAccepting.add(stateName);
                }
                if (dpnModel.isNonAcceptingTrap(st)) {
                    this.allFailureStates.add(stateName);
                }
                this.allAutomatonStates.add(stateName);
                this.allPetriNetStates.add(stateName);
                dpnStates.add(stateName);
            }
            this.allAcceptingStates.add(dpnAccepting);
            this.allPetriNetStatesByDpn.add(dpnStates);

            for (State stt : dpnModel.executableAutomaton.states()) {
                String sourcePrefix = statePrefix;
                for (org.processmining.ltl2automaton.plugins.automaton.Transition t : stt.getOutput()) {
                    if (t.isPositive()) {
                        String label = t.getPositiveLabel();
                        State source = t.getSource();
                        State target = t.getTarget();
                        addAutomatonTransition(
                            sourcePrefix + source.toString(),
                            label,
                            sourcePrefix + target.toString()
                        );
                    }
                }
            }
            dpnIndex++;
        }

		//Next adding all Goal states for the Declarative Automaton
        
        for (Automaton aut : this.constraintAutomatons) {
  	      // Automaton might have more than one goal state. In that case, we'll put the goal states with an "or" between them.
  	      List<StateEC> allStates = aut.getStatesEC();
		  ArrayList<String> declareAccepting = new ArrayList<>();
  	      
  	      for (StateEC g : allStates) {
  	    	  if (g.isInitial) {
  	    		  this.allInitialStates.add(g.name);
  	    	  }
  	    	  if (g.isGoal) {
  	    		//this.allAcceptingStates.add(g.name);  
				declareAccepting.add(g.name);

  	    	  }
  	    	  if (g.isFailure) {
  	    		  this.allFailureStates.add(g.name);
  	    	  }
			  this.allAutomatonStates.add(g.name);
  	    	  
  	      }
		  this.allAcceptingStates.add(declareAccepting);
        }
    }

      private void prepareAutomatonStates() {
		
	    int index = 1;
	    
	    for (DeclareConstraint constraint : this.declareModel.getDeclareConstraints()) {
	      String prefix = "sDEC" + index++ + "_";
	      Automaton newAutomaton = new Automaton(this.declareModel.activities.keySet(), prefix, constraint);
	      this.constraintAutomatons.add(newAutomaton);
	      
	    }

  }

  public void buildAutomatons() {

    for (Automaton aut : this.constraintAutomatons) {

      for (Transition t : aut.getTransitions()) {
        String mappedActivity = mappedActivity(t.getActivity());
        if (mappedActivity == null) {
          continue;
        }

		ArrayList<String> automatonItem = new ArrayList<>();

		automatonItem.add(t.getActiviationState().name);
		automatonItem.add(mappedActivity);
		automatonItem.add(t.getTargetState().name);

		this.allAutomatonStrings.add(automatonItem);


        List<Condition> conditions = t.getReformedConditions();
        if (conditions != null) {
          for (Condition c : conditions) {
			this.conditionStrings.add(this.getConditionString(t, c).toString());
			if (!this.conditionStrings.contains(this.getHasConstraintConditionString(t, c).toString())){
				this.conditionStrings.add(this.getHasConstraintConditionString(t, c).toString());
			};
          }

        }
      }

    }

  }
  private StringBuilder getConditionString(Transition t, Condition c) {
    StringBuilder b = new StringBuilder();

    if (c.operator == null) return b;

	switch (c.operator) {
		case BIGGER_OR_EQUAL:
		  b.append("    (has_maj_c " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
		  b.append("    (= (majority_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
		  break;
		case LESS_OR_EQUAL:
		  b.append("    (has_min_c " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
		  b.append("    (= (minority_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
		  break;
		case EQUAL:
		  b.append("    (has_eq_c " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
		  b.append("    (= (equality_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
		  break;
		case NOT_EQUAL:
		  b.append("    (has_ineq_c " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
		  b.append("    (= (inequality_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
		  break;
  
		default:
		  break;
	  }
	  /*
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
	*/

    return b;
  }

  private StringBuilder getHasConstraintConditionString(Transition t, Condition c) {
    StringBuilder b = new StringBuilder();

    if (c.operator == null) return b;

	b.append("    (has_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ")\n");
	
	return b;
  }



  public HashMap<String, Activity> mapActivityObjects(){
	HashMap<String, Activity> activityObjectMap = new HashMap<String, Activity>() ;

	HashMap<String, Activity> declActs = this.declareModel.getActivities();

	for (String act : declActs.keySet()){
		Activity actObj = declActs.get(act);
		activityObjectMap.put(act, actObj);
	}

	for (DataPetriNet dpnModel : this.dpnModels) {
		HashMap<String, Activity> dpnActs = dpnModel.activityMap;
		for (String act : dpnActs.keySet()) {
			Activity dpnAct = dpnActs.get(act);
			boolean ins = true;

			for (Activity objs : activityObjectMap.values()) {
				if (objs.getName() == dpnAct.getName()) {
					ins = false;
					break;
				}
			}

			if (ins) {
				activityObjectMap.put(act, dpnAct);
			} else {
				System.out.println("TODO: Activity already in list.");
			}
		}
	}

	return activityObjectMap;
  }
    
  public Map<Pair<Activity, CostEnum>, Integer> getCosts() {
    return this.costs;
  }

  public void assignCosts(List<String[]> costsList) {
    //Activity a;
	String sa;
	Activity a;
    Integer[] costsArray = new Integer[4];

    this.costs = new HashMap<>();
    Set<Activity> seenActivities = new HashSet<>();

    for (String[] costs : costsList) {
      sa = this.activities.get(costs[0]);
      if (sa == null) {
        throw new Error("Activity not found! What I parsed: " + costs[0]);
      }
	  a = new Activity(sa);
      seenActivities.add(a);
      for (int i = 1; i < costs.length; i++) {
        costsArray[i-1] = Integer.valueOf(costs[i]);
      }
      this.costs.put(new Pair<Activity, CostEnum>(a, CostEnum.CHANGE), costsArray[0]);
      this.costs.put(new Pair<Activity, CostEnum>(a, CostEnum.ADD), costsArray[1]);
      this.costs.put(new Pair<Activity, CostEnum>(a, CostEnum.SET), costsArray[2]);
      this.costs.put(new Pair<Activity, CostEnum>(a, CostEnum.DELETE), costsArray[3]);
    }

    // TODO Implement handling of missing activities
    // Set<Activity> undefinedActivities = new HashSet<>();
  }
}
