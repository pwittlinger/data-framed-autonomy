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
    public DataPetriNet dpnModel;
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
	private Map<Pair<Activity, CostEnum>, Integer> costs;
	public Map<Pair<Activity, String>, Integer> resourceMap;
	public Map<String, Integer> constraintViolation;
	public Set<String> allResources;
	
    

    public MixedModel(DataPetriNet dataPetriNet, DeclareModel declare){
        this.dpnModel = dataPetriNet;
        this.declareModel = declare;
		this.constraintAutomatons = new ArrayList<>();
		this.allInitialStates = new ArrayList<>();
		this.allAcceptingStates = new ArrayList<>();
		this.allFailureStates = new ArrayList<>();
		this.allAutomatonStrings = new ArrayList<>();
		this.conditionStrings = new ArrayList<>();
		this.activities = new HashMap<>();
		this.allAutomatonStates = new ArrayList<>();
		this.allPetriNetStates = new ArrayList<>();
		this.allResources = new HashSet<String>();


        //this.dpnModel.dataPetriNet.getVariables();
		this.mapAllActivities();
		this.prepareAutomatonStates();
		this.parseAutomatonStatesIntoList();
		this.buildAutomatons();
		this.activityObjects = this.mapActivityObjects();
		
    }

    public void mapAllActivities(){
		// Map activities to strings
		// Shared activities between the PN and DECLARE models have the same index
        Set<String> dpnActivities = this.dpnModel.activities;
        HashMap<String, Activity> declareActivities = this.declareModel.getActivities();

        for (String act : dpnActivities){
            if (!this.activities.containsKey(act)){
                this.activities.put(act, "a"+this.activityCounter);
                this.activityCounter += 1;
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
        Collection<DataElement> dpnVars = this.dpnModel.dataPetriNet.getVariables();

    }


    public void parseAutomatonStatesIntoList(){

        // First handling Petri Net states
        // Checking the initial state of the PN automaton
        // Working with Deterministic Automatons -> exactly one initial state
        this.dpnModel.executableAutomaton.ini();
        PossibleNodes initialState = this.dpnModel.executableAutomaton.currentState();

        for (State stt : initialState){
            this.allInitialStates.add(stt.toString());
            //this.allAutomatonStates.add(stt.toString());
        }
        
		ArrayList<String> dpnAccepting = new ArrayList<>();

        for (State st : dpnModel.executableAutomaton.states()) {
        	if (st.isAccepting()) {
        		//this.allAcceptingStates.add(st.toString());
				dpnAccepting.add(st.toString());
        	}
        	if (this.dpnModel.isNonAcceptingTrap(st)) {
        		this.allFailureStates.add(st.toString());
        	}
			this.allAutomatonStates.add(st.toString());
			this.allPetriNetStates.add(st.toString());

        	
        }
		this.allAcceptingStates.add(dpnAccepting);
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
        
        for (State stt : this.dpnModel.executableAutomaton.states()) {

			for (org.processmining.ltl2automaton.plugins.automaton.Transition t : stt.getOutput()) {
				if (t.isPositive()) {
					ArrayList <String> automatonItem = new ArrayList<>();
					String label = t.getPositiveLabel();
					State source = t.getSource();
					State target = t.getTarget();

					automatonItem.add(source.toString());
					//automatonItem.add(label);
					automatonItem.add(this.activities.get(label));
					automatonItem.add(target.toString());
					
					this.allAutomatonStrings.add(automatonItem);
				}		

			}
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
		ArrayList<String> automatonItem = new ArrayList<>();

		automatonItem.add(t.getActiviationState().name);
		//automatonItem.add(t.getActivity());
		automatonItem.add(this.activities.get(t.getActivity()));
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
	HashMap<String, Activity> dpnActs = this.dpnModel.activityMap;

	for (String act : declActs.keySet()){
		//String mappedAct = this.activities.get(act);
		Activity actObj = declActs.get(act);

		//activityObjectMap.put(mappedAct, actObj);
		activityObjectMap.put(act, actObj);
	}

	for (String act : dpnActs.keySet()){
		Activity dpnAct = dpnActs.get(act);
		//this.activities;
		boolean ins = true;
		//Activity tmp = null;




		for (Activity objs : activityObjectMap.values()) {
			if (objs.getName() == dpnAct.getName()) {
				ins = false;
				break;
			}
		}

		if (ins) {
			activityObjectMap.put(act, dpnAct);
		}
		else {
			System.out.println("TODO: Activity already in list.");
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
	String resource;


	if (costsList.get(0).length == 3) {
		
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
	}

    // TODO Implement handling of missing activities
    // Set<Activity> undefinedActivities = new HashSet<>();
  }
}
