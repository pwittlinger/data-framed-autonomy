import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import Automaton.VariableSubstitution;
import translations.DomainVariants;
import translations.IOManager;
import translations.MultiInstanceMerger;
import translations.NandGuardEncoder;
import time.TimeWindowPreprocessor;
import values.ValueIntervalPreprocessor;
import translations.PDDLGenerator;
import translations.PDDLGeneratorMixedModel;
import utils.CmdArgsUtil;
//import utils.CmdArgsUtils;
import utils.CmdFileUtils;
import utils.JsonParser;
import log.LogFile;
import model.DataPetriNet;
import model.DeclareModel;
import model.MixedModel;
import model.RecoveryStrategy;
import org.apache.commons.cli.*;

public class Runner {

  public static void main(String[] args) throws Exception {   

    findAlignments(args);
      
  }
  
  public static void findAlignments(String[] args) 
    throws Exception 
  {
    CommandLine cmds = CmdArgsUtil.parseCmdInputs(args);


    String modelString= cmds.getOptionValue("declare");
    String traceString= cmds.getOptionValue("log");

    String[] declareModelPaths = modelString.split(",");
    for (String declarePath : declareModelPaths) {
      if (!CmdFileUtils.declareFileExists(declarePath.trim())) {
        throw new Exception("Error in accessing DECLARE model: " + declarePath.trim());
      }
    }

    if (!CmdFileUtils.logFileExists(traceString)) {
      throw new Exception("Error in accessing XES file." + "\nGiven path:" + traceString);
    }

    String petriNetString = null;
    String variablesString= "";
    String substitutionsString= ""; 
    String costsString = "";

    boolean hasPetri = cmds.hasOption("petri");
    boolean hasVarAssign = cmds.hasOption("varAssign");
    boolean hasVarSub = cmds.hasOption("varSub");
    boolean hasCost = cmds.hasOption("cost");
    boolean newOutput = cmds.hasOption("output");
    boolean hasViolationCost = cmds.hasOption("violation_cost");
    boolean hasViolationCostFile = cmds.hasOption("violation_cost_file");
    boolean hasWaitingCost = cmds.hasOption("waiting_cost");

    if (hasPetri) {
        petriNetString = cmds.getOptionValue("petri");
        for (String petriPath : petriNetString.split(",")) {
          if (!CmdFileUtils.petriFileExists(petriPath.trim())) {
            throw new Exception("Error in accessing Petri Net File: " + petriPath.trim());
          }
        }
    }
    
    if (hasVarAssign) {
      variablesString = cmds.getOptionValue("varAssign");
    }
    else {
      variablesString = "variable_values.txt";
    }

    if (hasVarSub) {
      substitutionsString = cmds.getOptionValue("varSub");
    }
    else {
      substitutionsString = "variable_substitutions.txt";
    }

    if (hasCost) {
      costsString = cmds.getOptionValue("cost");
    }
    else {
      costsString = "cost_model.txt";
    }

    // Default violation cost of every constraint, the violation cost file overrides it per constraint
    int violationCost = 1;
    if (hasViolationCost) {
      violationCost = DeclareModel.parseViolationCost(cmds.getOptionValue("violation_cost"), "--violation_cost");
    }

    String violationCostsString = null;
    if (hasViolationCostFile) {
      violationCostsString = cmds.getOptionValue("violation_cost_file");
      if (!CmdFileUtils.violationCostFileExists(violationCostsString)) {
        throw new Exception("Error in accessing violation cost file: " + violationCostsString);
      }
    }

    // Cost of waiting one time unit, the problem files get it relative to the default as waiting_cost
    BigDecimal waitingCost = PDDLGenerator.DEFAULT_WAITING_COST;
    if (hasWaitingCost) {
      waitingCost = PDDLGenerator.parseWaitingCost(cmds.getOptionValue("waiting_cost"));
    }

    RecoveryStrategy recovery = RecoveryStrategy.RESET;
    if (cmds.hasOption("recovery")) {
      recovery = RecoveryStrategy.fromOptionName(cmds.getOptionValue("recovery"));
    }


    // Read model and logs to find ltl formula
    IOManager ioManager = IOManager.getInstance();

    // In case the jar you run is outside the directory in which the project is; Add directory name as prefix.
    //ioManager.setProjectPrefix("pddl_gen");
    ioManager.setProjectPrefix();

    if (newOutput) {
      ioManager.overrideOutputPath(cmds.getOptionValue("output"));
    }
    ioManager.setProjectPrefix();
    
    DeclareModel model = ioManager.readDeclareModel(declareModelPaths); // OKAY!
    
    Map<String, Integer> variableAssignments;
    Set<VariableSubstitution> substitutions;

    if (!ioManager.variableAssignmentsExist(variablesString) || (!hasVarAssign)) {
      /*2026-01-15 On the current version, only INTEGERS are supported.
        In the DeclareModel.generateVariableValues() method all function calls are cast to int.
        The readVariableAssignments method cannot deal with floats in the form "2.0"
      */
      String varAssignmentString = model.generateVariableValues();
      ioManager.exportVariableAssignments(variablesString, varAssignmentString);
    }

    if (!ioManager.variableSubstitutionExists(substitutionsString) || (!hasVarSub)) {
      String varSubstitutionString = model.generateVariableSubstitutions(cmds.hasOption("intervals"));
      ioManager.exportVariableSubstitution(substitutionsString, varSubstitutionString);
    }

    variableAssignments = ioManager.readVariableAssignments(variablesString);
    substitutions = ioManager.readVariablesSubstitutions(substitutionsString);

    // Must happen before the generators build their automata, which copy the encoded condition values.
    model.applyVariableValues(variableAssignments);

    // Optional: no time at all. Before the automata get built (and before the STP options, which then find no windows)
    if (cmds.hasOption("ignore_time_conditions")) {
      model.ignoreTimeConditions();
      System.out.println("Ignoring the time conditions: no time windows or clocks in the problems, every event at timestamp 0.0");
    }

    // Optional: tighten / drop the time windows before the automata get built
    // --stp only looks at the windows. --stp-apply also changes them, but since the planner can violate
    // constraints that changes the optimal cost (a dropped window can't be violated anymore), see the help
    if (cmds.hasOption("stp-apply")) {
      System.out.print(TimeWindowPreprocessor.describe(TimeWindowPreprocessor.apply(model.getDeclareConstraints()), true));
    } else if (cmds.hasOption("stp")) {
      System.out.print(TimeWindowPreprocessor.describe(TimeWindowPreprocessor.analyse(model.getDeclareConstraints()), false));
    }
    // Optional: the same idea on the data values. This one is exact, the conditions get equivalent ones
    if (cmds.hasOption("intervals")) {
      System.out.print(ValueIntervalPreprocessor.describe(ValueIntervalPreprocessor.apply(
          model.getDeclareConstraints(), model.getActivities(), substitutions, variableAssignments)));
    }

    System.out.println("Model: " + model);    

    // Set to true when some data condition needs nand clauses, then the problems need the clauses domain
    boolean usesNandGuards;
    // The problems of all the traces, for -i (one problem with every trace as its own instance)
    ArrayList<String> problems;

    if (!hasPetri) {
      //Only DECLARE model present
      // Using the pre-existing implementation for the 

      /*Check if cost model exists and otherwise use standard cost model*/
      if (!ioManager.costModelExists(costsString) || (!hasCost)) {
        ioManager.exportCostModel(costsString, model.activities.keySet());
      }

      model.assignCosts(ioManager.readCostModel(costsString)); // OKAY!
      model.assignViolationCosts(violationCost, hasViolationCostFile ? ioManager.readViolationCosts(violationCostsString) : new ArrayList<>());
      model.setDeclareConstraintsToHard();
      LogFile log = ioManager.readDeclareLog(traceString, model);
      PDDLGenerator pddlGenerator = new PDDLGenerator(model, recovery, waitingCost);
      //String domain = pddlGenerator.defineDomain();
      problems = log.generateProblems(pddlGenerator, variableAssignments, substitutions);

      int i = 1;
      for (String problem : problems) {
        IOManager.getInstance().exportProblemPDDL(problem, i);
        i++;
      }
      //IOManager.getInstance().exportDomainPDDL(domain);
      usesNandGuards = pddlGenerator.usesNandGuards();
      }

    else {

      String[] petriNetPaths = petriNetString.split(",");
      ArrayList<DataPetriNet> petriNets = ioManager.readDataPetriNets(petriNetPaths);
      MixedModel myMixedModel = new MixedModel(petriNets, model);
      

      if (!ioManager.costModelExists(costsString) || (!hasCost)) {
        ioManager.exportCostModel(costsString, myMixedModel.activities.keySet());
      }

      myMixedModel.checkCostFileFormat(costsString);

      myMixedModel.assignCosts(ioManager.readCostModel(costsString)); // OKAY!

      myMixedModel.assignViolationCosts(violationCost, hasViolationCostFile ? ioManager.readViolationCosts(violationCostsString) : new ArrayList<>());
      myMixedModel.declareModel.setDeclareConstraintsToHard();

      LogFile log = ioManager.readLog(traceString, myMixedModel); // OKAY!
      
      PDDLGeneratorMixedModel pddlGenerator = new PDDLGeneratorMixedModel(myMixedModel, recovery, waitingCost);

      //String domain = pddlGenerator.defineDomain();
      problems = log.generateProblems(pddlGenerator, variableAssignments, substitutions);


      for (DataPetriNet dpn : myMixedModel.dpnModels) {
        ioManager.exportActivityMapping(dpn.activityMapping(), dpn.netName);
      }

      ioManager.exportActivityMapping(myMixedModel.declareModel.activityMapping(), "DECLARE");




        int i = 1;
      
    for (String problem : problems) {
      //IOManager.getInstance().exportProblemPDDL(problem, i);
      ioManager.exportProblemPDDL(problem, i);
      i++;
    }
    //IOManager.getInstance().exportDomainPDDL(domain);
    usesNandGuards = myMixedModel.nandGuards.isUsed();
    }

    // The problems only work with the domain that knows about the nand clauses, so we put it next to them.
    // If they don't need it we remove an old copy, so nobody picks up the wrong domain by mistake.
    Path pddlFolder = Path.of(ioManager.getPddlFolder());
    for (RecoveryStrategy strategy : RecoveryStrategy.values()) {
      Files.deleteIfExists(pddlFolder.resolve(DomainVariants.fileName(strategy, true, false)));
    }
    if (usesNandGuards) {
      Path domain = NandGuardEncoder.exportClausesDomain(pddlFolder, recovery, !hasPetri);
      System.out.println("Some data conditions use or/not, the problems need the domain " + domain);
    }

    // -i: also one problem with all the traces as separate instances, with its own domain variant
    if (cmds.hasOption("multiinstance")) {
      Files.writeString(pddlFolder.resolve(MultiInstanceMerger.PROBLEM_FILE), MultiInstanceMerger.merge(problems));
      Path domain = MultiInstanceMerger.exportDomain(pddlFolder, usesNandGuards, recovery, !hasPetri);
      System.out.println("All " + problems.size() + " traces in " + pddlFolder.resolve(MultiInstanceMerger.PROBLEM_FILE) + ", domain " + domain);
    }

    ioManager.exportModel(model);
   
  }
}

