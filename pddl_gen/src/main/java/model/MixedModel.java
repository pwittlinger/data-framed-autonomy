package model;
import model.DeclareModel;
import model.Activity;
import model.Attribute;
import model.Condition;
import model.CostEnum;
import model.DeclareConstraint;
import model.DeclareModel;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    public ArrayList<ArrayList<String>> allFailureStatesByAutomaton; // Same order as allAcceptingStates
    public ArrayList<Automaton> constraintAutomatons;
    public ArrayList<String> conditionStrings;
    public ArrayList<ArrayList<String>> allAutomatonStrings;
    public ArrayList<String> allAutomatonStates;
    public HashMap<String, Activity> activityObjects;
    public ArrayList<String> allPetriNetStates;
    public ArrayList<ArrayList<String>> allPetriNetStatesByDpn;
    private Map<Pair<Activity, CostEnum>, Integer> costs;
	public Map<Pair<Activity, String>, Integer> resourceMap;
	public Map<String, Integer> constraintViolation;
	public Set<String> hardConstraints; // Marked x in the violation cost file, no recovery strategy applies to them
	private Map<String, String> stateConstraints; // Automaton state -> name of the constraint it belongs to
	public Set<String> allResources;
	public boolean isJsonCostFile = false;

    

    public MixedModel(DataPetriNet dataPetriNet, DeclareModel declare) {
        this(List.of(dataPetriNet), declare);
    }

    public MixedModel(List<DataPetriNet> dataPetriNets, DeclareModel declare) {
      /* Constructor for Mixed Model
       */
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
        this.allFailureStatesByAutomaton = new ArrayList<>();
        this.allAutomatonStrings = new ArrayList<>();
        this.conditionStrings = new ArrayList<>();
        this.activities = new HashMap<>();
        this.allAutomatonStates = new ArrayList<>();
        this.allPetriNetStates = new ArrayList<>();
        this.allPetriNetStatesByDpn = new ArrayList<>();		
        this.hardConstraints = new HashSet<>();
        this.stateConstraints = new HashMap<>();
        this.allResources = new HashSet<String>();


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
            HashMap<String,String> mappedLocalActivities = new HashMap<String,String>();
            for (String act : dpnModel.activities) {
                if (!this.activities.containsKey(act)) {
                    this.activities.put(act, "a" + this.activityCounter);
                    this.activityCounter += 1;
                    
                }
                mappedLocalActivities.put(act, this.activities.get(act));
                
            }
            // Updating the mapped activities for the local Petri Net
            dpnModel.dpnMappedActivities = mappedLocalActivities;
        }

        HashMap<String,String> mappedDECLActivities = new HashMap<String,String>();
        for (String act : declareActivities.keySet()){
          
            if (!this.activities.containsKey(act)){
                this.activities.put(act, "a"+this.activityCounter);
                this.activityCounter += 1;
            }
            mappedDECLActivities.put(act, this.activities.get(act));
        }
        this.declareModel.mappedActivities = mappedDECLActivities;

    }



    public void parseAutomatonStatesIntoList(){

        // First handling Petri Net states — one automaton per DPN
        int dpnIndex = 1;
        for (DataPetriNet dpnModel : this.dpnModels) {
            String statePrefix = dpnStatePrefix(dpnIndex);
            String dpnConstraint = dpnConstraintName(dpnIndex);
            this.dpnConstraintNames.add(dpnConstraint);
            ArrayList<String> dpnStates = new ArrayList<>();

            dpnModel.executableAutomaton.ini();
            PossibleNodes initialState = dpnModel.executableAutomaton.currentState();

            for (State stt : initialState) {
                String stateName = statePrefix + stt.toString();
                this.allInitialStates.add(stateName);
            }

            ArrayList<String> dpnAccepting = new ArrayList<>();
            ArrayList<String> dpnFailure = new ArrayList<>();

            for (State st : dpnModel.executableAutomaton.states()) {
                String stateName = statePrefix + st.toString();
                if (st.isAccepting()) {
                    dpnAccepting.add(stateName);
                }
                if (dpnModel.isNonAcceptingTrap(st)) {
                    this.allFailureStates.add(stateName);
                    dpnFailure.add(stateName);
                }
                this.allAutomatonStates.add(stateName);
                this.allPetriNetStates.add(stateName);
                this.stateConstraints.put(stateName, dpnConstraint);
                dpnStates.add(stateName);
            }
            this.allAcceptingStates.add(dpnAccepting);
            this.allFailureStatesByAutomaton.add(dpnFailure);
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
		  String constraintName = aut.getConstraint().getConstraintName();
		  ArrayList<String> declareAccepting = new ArrayList<>();
		  ArrayList<String> declareFailure = new ArrayList<>();

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
  	    		  declareFailure.add(g.name);
  	    	  }
			  this.allAutomatonStates.add(g.name);
			  this.stateConstraints.put(g.name, constraintName);

  	      }
		  this.allAcceptingStates.add(declareAccepting);
		  this.allFailureStatesByAutomaton.add(declareFailure);
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
		  b.append("    (= (majority_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
		  break;
		case LESS_OR_EQUAL:
		  b.append("    (= (minority_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
		  break;
		case EQUAL:
		  b.append("    (= (equality_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
		  break;
		case NOT_EQUAL:
		  b.append("    (= (inequality_constraint " + this.activities.get(c.activity) + " " + c.parameterName + " " + t.getActiviationState().name + " " + t.getTargetState().name + ") " + c.value + ")\n");
		  break;
  
		default:
		  break;
	  }

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

  /**
   * Gives every DECLARE and Petri net constraint the default violation cost, then applies the
   * lines of the violation cost file in order (a later line overrides an earlier one).
   * A cost of x marks the constraint as hard: no recovery strategy applies to it, so its violation is permanent.
   * @param violationCostsList {identifier, cost} pairs, identifier "<decl file>|<constraint>" or "<pnml file>"
   */
  public void assignViolationCosts(int defaultCost, List<String[]> violationCostsList) {
    this.constraintViolation = new LinkedHashMap<>();
    this.hardConstraints.clear();
    for (DeclareConstraint dc : this.declareModel.getDeclareConstraints()) {
      this.constraintViolation.put(dc.getConstraintName(), defaultCost);
    }
    for (String dpnConstraint : this.dpnConstraintNames) {
      this.constraintViolation.put(dpnConstraint, defaultCost);
    }

    DeclareModel.applyViolationCosts(violationCostsList, this::resolveViolationCostTarget, this.constraintViolation, this.hardConstraints);
  }

  // True if the automaton state belongs to a constraint marked as hard in the violation cost file
  public boolean isHardConstraintState(String state) {
    return this.hardConstraints.contains(this.stateConstraints.get(state));
  }

  // "<pnml file>" matches the Petri net read from that file, "<decl file>|<constraint>" is resolved by the DECLARE model.
  private List<String> resolveViolationCostTarget(String identifier) {
    if (identifier.indexOf('|') >= 0) {
      return this.declareModel.resolveViolationCostTarget(identifier);
    }

    List<String> constraintNames = new ArrayList<>();
    String fileName = new File(identifier).getName();
    for (int i = 0; i < this.dpnModels.size(); i++) {
      if (this.dpnModels.get(i).netName.equals(fileName)) {
        constraintNames.add(this.dpnConstraintNames.get(i));
      }
    }
    if (constraintNames.isEmpty()) {
      throw new Error("Petri net " + fileName + " from the violation cost file is not one of the input Petri nets");
    }
    return constraintNames;
  }

  public void checkCostFileFormat(String costsFileName) {
    this.isJsonCostFile = costsFileName != null && costsFileName.contains(".json");
  }

  public void assignCosts(List<String[]> costsList) {
    //Activity a;
	String sa;
	Activity a;
	String resource;


	// Rows of the JSON resource file are "<task> <resource> <duration>". A text cost model row "<activity> <add> <set>"
	// has the same length, so the file type decides which one we have, not the row length.
	if (this.isJsonCostFile) {

    	Set<Activity> seenActivities = new HashSet<>();
		this.resourceMap = new HashMap<>();

		for (String[] costs : costsList) {
			sa = this.activities.get(costs[0]);
			resource = costs[1];
			this.allResources.add(resource);
			if (sa == null) {
			throw new Error("Activity not found! What I parsed: " + costs[0]);
			}	
			a = new Activity(sa);
			seenActivities.add(a);
			this.resourceMap.put(new Pair<Activity, String>(a, resource), Integer.parseInt(costs[2]));
    }

	}
	else {

	

    this.costs = new HashMap<>();
    Set<Activity> seenActivities = new HashSet<>();

    for (String[] costs : costsList) {
      sa = this.activities.get(costs[0]);
      if (sa == null) {
        throw new Error("Activity not found! What I parsed: " + costs[0]);
      }
	  a = new Activity(sa);
      seenActivities.add(a);
      for (Map.Entry<CostEnum, Integer> cost : CostEnum.parseCostLine(costs).entrySet()) {
        this.costs.put(new Pair<Activity, CostEnum>(a, cost.getKey()), cost.getValue());
      }
    }

    // TODO Implement handling of missing activities
    // Set<Activity> undefinedActivities = new HashSet<>();
  }
}
}
