package model;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.processmining.datapetrinets.DataPetriNetsWithMarkings;
import org.processmining.datapetrinets.io.DPNIOException;
import org.processmining.datapetrinets.io.DataPetriNetImporter;
import org.processmining.ltl2automaton.plugins.automaton.Automaton;
import org.processmining.ltl2automaton.plugins.automaton.DefaultAutomatonFactory;
import org.processmining.ltl2automaton.plugins.automaton.DeterministicAutomaton;
import org.processmining.ltl2automaton.plugins.automaton.State;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.IllegalTransitionException;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.PetrinetSemantics;
import org.processmining.models.semantics.petrinet.impl.PetrinetSemanticsFactory;
import org.processmining.plugins.declareminer.ExecutableAutomaton;
import org.processmining.plugins.declareminer.PossibleNodes;


public class DataPetriNet {
    
	public Set<String> activities = new HashSet<String>();

	private DeterministicAutomaton automaton;
	public ExecutableAutomaton executableAutomaton;
	public DataPetriNetsWithMarkings dataPetriNet;
	public HashMap<String, String> guardStrings;
	public static DataPetriNetImporter dataPetriNetImporter = new DataPetriNetImporter();
	public List automatonStrings;
	public HashMap<String, Activity> activityMap;
	public HashMap<String,String> dpnMappedActivities;
	public String netName;
	//private ExecutableAutomaton executableAutomaton;
	//private DataPetriNetsWithMarkings dataPetriNet;
	//DataPetriNetImporter dataPetriNetImporter = new DataPetriNetImporter();
	
	public DataPetriNet(String inputDPN) throws DPNIOException, FileNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, InvocationTargetException{
		//DataPetriNetImporter dataPetriNetImporter = new DataPetriNetImporter();
		InputStream inputStream = new BufferedInputStream(new FileInputStream(inputDPN));
		File f = new File(inputDPN);
		netName = f.getName();

		this.dataPetriNet = dataPetriNetImporter.importFromStream(inputStream).getDPN();
		this.activities = new HashSet<String>();

		for (Transition t : dataPetriNet.getTransitions()) {
			if (!t.isInvisible() && !activities.contains(t.getLabel())) {
				this.activities.add(t.getLabel());
		}
		}
		
		this.guardStrings = new HashMap<String, String>();
		this.initializeAutomaton(this.dataPetriNet);
		this.executableAutomaton = new ExecutableAutomaton(automaton);
		this.mapAllActivities();
		this.activityMap = this.addActivities();

	}



	//Starts the PN traversal, does all the pre- and post-processing 
	public void initializeAutomaton(DataPetriNetsWithMarkings dataPetriNet) {
		//Allows to execute transitions and manipulate the state of the underlying Petri Net
		PetrinetSemantics petrinetSemantics = PetrinetSemanticsFactory.regularPetrinetSemantics(Petrinet.class); //The class argument is actually unused
		petrinetSemantics.initialize(dataPetriNet.getTransitions(), dataPetriNet.getInitialMarking());

		//Data structures and factory for automata creation
		DefaultAutomatonFactory automatonFactory = new DefaultAutomatonFactory();
		List<Marking> visitedMarkings = new ArrayList<Marking>();
		Map<Marking, State> markingToAutomatonState = new HashMap<Marking, State>(); //For setting the accepting states of the automaton
		Stack<Marking> currentMarkingPath = new Stack<Marking>();

		//Processing the initial state
		Marking initialMarking = petrinetSemantics.getCurrentState();
		processVisitedState(initialMarking, automatonFactory, visitedMarkings, markingToAutomatonState, currentMarkingPath);
		automatonFactory.initialState(markingToAutomatonState.get(initialMarking));

		//Petri Net traversal and automata construction
		visitNextState(petrinetSemantics, currentMarkingPath, automatonFactory, visitedMarkings, markingToAutomatonState);

		//Setting the accepting states of the automaton
		for (Marking marking : dataPetriNet.getFinalMarkings()) {
			State state = markingToAutomatonState.get(marking);
			automatonFactory.updateState(state, state.getId(), true);
			//automatonFactory.addAllTransition(state, state); //This would mean that DPN is considered permanently satisfied once the final marking has been reached
		}

		//Creating fail state and self loops
		State failState = new State(automatonFactory.getAutomaton().getStateCount());
		automatonFactory.addState(failState);
		automatonFactory.addAllTransition(failState, failState);



		for (Marking marking : visitedMarkings) {
			//Adding a transition from current state to fail state for each proposition that is unhandled by existing outgoing transitions 
			Set<String> outgoingActivities = new HashSet<String>();
			for (org.processmining.ltl2automaton.plugins.automaton.Transition transition :  markingToAutomatonState.get(marking).getOutput()) {
				outgoingActivities.add(transition.getPositiveLabel());
			}
			Set<String> unhandeledActivities = new HashSet<String>();
			unhandeledActivities.addAll(activities);
			unhandeledActivities.removeAll(outgoingActivities);
			for (String unhandeledActivity : unhandeledActivities) {
				automatonFactory.addPropositionTransition(markingToAutomatonState.get(marking), failState, unhandeledActivity);
			}

			//Adding a self-loop to each state of the automaton (matches all propositions that are not part of the alphabet of this DPN)
			automatonFactory.addNegativePropositionsTransition( markingToAutomatonState.get(marking),  markingToAutomatonState.get(marking), activities);
		}

		//Setting the automaton state ids and creating the executable automaton
		this.automaton = automatonFactory.getAutomaton().op.determinize().op.complete().op.renumber().op.minimize();
		this.executableAutomaton = new ExecutableAutomaton(automaton);
	}

	//PN traversal + constructing the automata transitions
	private static void visitNextState(PetrinetSemantics petrinetSemantics, Stack<Marking> currentMarkingPath, DefaultAutomatonFactory automatonFactory, List<Marking> visitedMarkings, Map<Marking, State> markingToAutomatonState) {
		Marking currentMarking = currentMarkingPath.peek();

		for (Transition transition : petrinetSemantics.getExecutableTransitions()) {
			/*
			if (transition.isInvisible()) { //TODO: Silent transitions
				continue;
			}
			*/

			try {
				petrinetSemantics.setCurrentState(currentMarking);
				petrinetSemantics.executeExecutableTransition(transition); //Moves the PN to a new marking, but the variable currentMarking remains unchanged

				if (!visitedMarkings.contains(petrinetSemantics.getCurrentState())) {
					processVisitedState(petrinetSemantics.getCurrentState(), automatonFactory, visitedMarkings, markingToAutomatonState, currentMarkingPath);
					automatonFactory.addPropositionTransition(markingToAutomatonState.get(currentMarking), markingToAutomatonState.get(petrinetSemantics.getCurrentState()), transition.getLabel());
					visitNextState(petrinetSemantics, currentMarkingPath, automatonFactory, visitedMarkings, markingToAutomatonState);
				} else {
					automatonFactory.addPropositionTransition(markingToAutomatonState.get(currentMarking), markingToAutomatonState.get(petrinetSemantics.getCurrentState()), transition.getLabel());
				}

			} catch (IllegalTransitionException e) {
				//This should never happen because the loop is over executable transitions
				System.err.println(e.getMessage());
			}
		}

		//Backtracking
		currentMarkingPath.pop();
	}

	//Creates an automaton state for a given marking (should be called once per each unique marking)
	private static void processVisitedState(Marking marking, DefaultAutomatonFactory automatonFactory, List<Marking> visitedStates, Map<Marking, State> markingToAutomatonState, Stack<Marking> currentStatePath) {
		State automatonState = new State(automatonFactory.getAutomaton().getStateCount()); //State class refers to the automaton state
		automatonFactory.addState(automatonState);
		visitedStates.add(marking);
		currentStatePath.add(marking);
		markingToAutomatonState.put(marking, automatonState);
	}


	//For building a DOT string of the automaton
	public String createAutomatonVisualizationString(Automaton aut, boolean alternativeLayout) {
		//Adopted from org.processmining.ltl2automaton.plugins.automaton.DOTExporter, but significantly modified
		StringBuilder sb = new StringBuilder();
		sb.append("digraph \"\" {");

		if (aut==null) {
			sb.append("}");
			return sb.toString();
		} else {
			sb.append(" init [shape=none, label=\"\"];");
			if (!alternativeLayout)
				sb.append(" rankdir = \"LR\"");

			for (final State s : aut) {
				if (!this.isNonAcceptingTrap(s)) {
					sb.append(" s");
					sb.append(s.getId());
					if (s.isAccepting()) {
						sb.append("[shape=doublecircle]");
					} else {
						sb.append("[shape=circle]");
					}
					sb.append(';');
				}
			}

			for (final State s : aut) {
				if (this.isNonAcceptingTrap(s)) {
					continue; //Skipping the fail-state
				}
				Map<State, List<String>> outStateToLabels = new HashMap<State, List<String>>();
				for (final org.processmining.ltl2automaton.plugins.automaton.Transition t : s.getOutput()) {
					if (this.isNonAcceptingTrap(t.getTarget())) {
						continue; //Skipping the edges that lead to the fail-state
					}
					//Replacing encodings with activity names and merging same labeled transitions (not the most efficient code)
					if (!outStateToLabels.containsKey(t.getTarget())) {
						outStateToLabels.put(t.getTarget(), new ArrayList<String>());
					}

					String transitionLabel = t.toString();
					if (t.isNegative()) { //Only one outgoing negative transitions per state
						List<String> negLabels = Arrays.asList(transitionLabel.split("&"));
						if (negLabels.size() == this.activities.size()) { //Not adding the edge if all activities were negated
							outStateToLabels.remove(t.getTarget());
							continue;
						}
						for (String activity : this.activities) {
							if (!negLabels.contains("!"+activity)) {
								outStateToLabels.get(t.getTarget()).add(activity);
							}
						}
					} else if (t.isPositive()) { //Only one activity name per positive transition
						outStateToLabels.get(t.getTarget()).add(t.toString());
					} else { //The "any" transition remains as-is
						outStateToLabels.get(t.getTarget()).add(transitionLabel);
					}

				}

				//Adding the edge to dot string
				for (State outState : outStateToLabels.keySet()) {
					sb.append(" s");
					sb.append(s.getId());
					sb.append(" -> s");
					sb.append(outState.getId());
					sb.append("[label=\"");
					sb.append(String.join("\n", outStateToLabels.get(outState)).replaceAll("\"", "\\\""));
					sb.append("\"];");
				}
			}

			if (aut.getInit() != null) {
				sb.append(" init -> s");
				sb.append(aut.getInit().getId());
				sb.append(';');
			}

			sb.append("}");

			return sb.toString();
		}


	}
	//A helper method for building the DOT string
	public boolean isNonAcceptingTrap(State s) {
		if (!s.isAccepting() && s.getOutputSize()==1) {
			org.processmining.ltl2automaton.plugins.automaton.Transition t = s.getOutput().iterator().next();
			if (t.isAll() && t.getTarget()==s) {
				return true;
			}
		}
		return false;
	}


	//Gets the monitoring state that corresponds to the current state of the automaton (referred to as truthValue in original implementation)
	public static void printMonitoringState(PossibleNodes currentState) {
		if (currentState.isAccepting()) {
			for (State state : currentState) {
				for (org.processmining.ltl2automaton.plugins.automaton.Transition t : state.getOutput()) {
					if (t.isAll()) {
						System.out.println("Accepting trap-state");
						return;
					}
				}
			}
			System.out.println("Accepting state");
		} else {
			for (State state : currentState) {
				if (!currentState.acceptingReachable(state)) {
					System.out.println("Non-accepting trap-state");
					return;
				}
			}
			System.out.println("Non-accepting state");
		}
	}
	
	public List<HashMap<State, List<String>>> listAutomatonActivities(Automaton aut) {
		
		List<HashMap<State, List<String>>> outputList = new ArrayList<HashMap<State, List<String>>>();
		//HashMap<State, Collection<List<String>>> outputList = new HashMap<State, Collection<List<String>>>();
		
		for (final State s : aut) {
			if (this.isNonAcceptingTrap(s)) {
				continue; //Skipping the fail-state
			}
			HashMap<State, List<String>> outStateToLabels = new HashMap<State, List<String>>();
			for (final org.processmining.ltl2automaton.plugins.automaton.Transition t : s.getOutput()) {
				if (this.isNonAcceptingTrap(t.getTarget())) {
					continue; //Skipping the edges that lead to the fail-state
				}
				//Replacing encodings with activity names and merging same labeled transitions (not the most efficient code)
				if (!outStateToLabels.containsKey(t.getTarget())) {
					outStateToLabels.put(t.getTarget(), new ArrayList<String>());
				}

				String transitionLabel = t.toString();
				if (t.isNegative()) { //Only one outgoing negative transitions per state
					List<String> negLabels = Arrays.asList(transitionLabel.split("&"));
					if (negLabels.size() == this.activities.size()) { //Not adding the edge if all activities were negated
						outStateToLabels.remove(t.getTarget());
						continue;
					}
					for (String activity : this.activities) {
						if (!negLabels.contains("!"+activity)) {
							outStateToLabels.get(t.getTarget()).add(activity);
						}
					}
				} else if (t.isPositive()) { //Only one activity name per positive transition
					outStateToLabels.get(t.getTarget()).add(t.toString());
				} else { //The "any" transition remains as-is
					outStateToLabels.get(t.getTarget()).add(transitionLabel);
				}

			}

		outputList.add(outStateToLabels);
		}
		return outputList;
	}
	
	public static HashMap<State, List<String>> flattenList(List<HashMap<State, List<String>>> inputList) {
		HashMap<State, List<String>> resultMap = new HashMap<>();
		
		for (HashMap<State, List<String>> mapIn : inputList) {
			Set<State> mkeys = mapIn.keySet();
			for (State sk : mkeys) {
				resultMap.put(sk, mapIn.get(sk));
			}
		}
		
		return resultMap;
	}

	public void mapAllActivities(){
		Collection<Transition> trans = this.dataPetriNet.getTransitions();

		for (Transition t : trans) {
			if (!this.activities.contains(t.toString())) {
				this.activities.add(t.toString());
			}
		}
	}

	private HashMap<String, Activity> addActivities() {
		HashMap<String, Activity> newActivities = new HashMap<>();

		for (String act : this.activities){
			newActivities.put(act, new Activity(act));
		}
		/*
		for (String[] line : activityLines) {
		  newActivities.put(line[0], new Activity(line[0]));
		}
		 */
		return newActivities;
	  }
	

	public String activityMapping() {
		StringBuilder s = new StringBuilder();
		for (String act :this.dpnMappedActivities.keySet()) {
			s.append(act + ":"+this.dpnMappedActivities.get(act)+"\n");
		}

		return s.toString(); //
	}

}


