import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import Automaton.VariableSubstitution;
import translations.IOManager;
import translations.PDDLGenerator;
import translations.PDDLGeneratorMixedModel;
import utils.CmdArgsUtils;
import utils.CmdFileUtils;
import utils.JsonParser;
import log.LogFile;
import model.DataPetriNet;
import model.DeclareModel;
import model.MixedModel;
import org.apache.commons.cli.*;

public class Runner {

  public static void main(String[] args) throws Exception {   

    findAlignments(args);
      
  }
  
  public static void findAlignments(String[] args) 
    throws Exception 
  {
     CommandLine cmds = CmdArgsUtils.parseCmdInputs(args);


    String modelString= cmds.getOptionValue("declare");
    String traceString= cmds.getOptionValue("log");

    if (!CmdFileUtils.declareFileExists(modelString)) {
          throw new Exception("Error in accessing DECLARE model.");
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

    if (hasPetri) {
        petriNetString = cmds.getOptionValue("petri");
        if (!CmdFileUtils.petriFileExists(petriNetString)) {
          throw new Exception("Error in accessing Petri Net File.");
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
      if (!costsString.endsWith(".json") & !costsString.endsWith(".txt")) {
        throw new Error("Invalid format for Cost file.\n Accepted are only .txt or .json");
      }
    }
    else {
      costsString = "cost_model.txt";
    }

    // Read model and logs to find ltl formula
    IOManager ioManager = IOManager.getInstance();

    // In case the jar you run is outside the directory in which the project is; Add directory name as prefix.
    ioManager.setProjectPrefix("pddl_gen");

    if (newOutput) {
      ioManager.overrideOutputPath(cmds.getOptionValue("output"));
    }
    
    DeclareModel model = ioManager.readDeclareModel(modelString); // OKAY!
    //model.assignCosts(ioManager.readCostModel(costsString)); // OKAY!
    Map<String, Integer> variableAssignments;
    Set<VariableSubstitution> substitutions;

    if (!ioManager.variableAssignmentsExist(variablesString)) {
      /*2026-01-15 On the current version, only INTEGERS are supported.
        In the DeclareModel.generateVariableValues() method all function calls are cast to int.
        The readVariableAssignments method cannot deal with floats in the form "2.0"
      */
      String varAssignmentString = model.generateVariableValues();
      ioManager.exportVariableAssignments(variablesString, varAssignmentString);
    }

    if (!ioManager.variableSubstitutionExists(substitutionsString)) {
      String varSubstitutionString = model.generateVariableSubstitutions();
      ioManager.exportVariableSubstitution(substitutionsString, varSubstitutionString);
    }

    variableAssignments = ioManager.readVariableAssignments(variablesString);
    substitutions = ioManager.readVariablesSubstitutions(substitutionsString);

    System.out.println("Model: " + model);    

    if ((petriNetString == "") | (petriNetString == null) | !(petriNetString.endsWith(".pnml"))) {

      /*Check if cost model exists and otherwise use standard cost model*/
      if (!ioManager.costModelExists(costsString)) {
        ioManager.exportCostModel(costsString, model.activities.keySet());
      }      

      model.assignCosts(ioManager.readCostModel(costsString)); // OKAY!
      LogFile log = ioManager.readDeclareLog(traceString, model);
      PDDLGenerator pddlGenerator = new PDDLGenerator(model);
      //String domain = pddlGenerator.defineDomain();
      ArrayList<String> problems = log.generateProblems(pddlGenerator, variableAssignments, substitutions);

      int i = 1;
      for (String problem : problems) {
      IOManager.getInstance().exportProblemPDDL(problem, i);
      i++;
      }
      //IOManager.getInstance().exportDomainPDDL(domain);
      }
    else {

      DataPetriNet petriNet = ioManager.readDataPetriNet(petriNetString);
      MixedModel myMixedModel = new MixedModel(petriNet, model);

      if (!ioManager.costModelExists(costsString)) {
        ioManager.exportCostModel(costsString, myMixedModel.activities.keySet());
      }

      myMixedModel.assignCosts(ioManager.readCostModel(costsString)); // OKAY!

      LogFile log = ioManager.readLog(traceString, myMixedModel); // OKAY!
      
      PDDLGeneratorMixedModel pddlGenerator = new PDDLGeneratorMixedModel(myMixedModel);

      //String domain = pddlGenerator.defineDomain();
      ArrayList<String> problems = log.generateProblems(pddlGenerator, variableAssignments, substitutions);

      String pnName = petriNetString;
      if (petriNetString.contains("\\")) {
        String[] s_ = petriNetString.split("\\\\");
        pnName = s_[s_.length-1];
      }
      ioManager.exportActivityMapping(pddlGenerator.activityMapping(), pnName);

        int i = 1;
      
    for (String problem : problems) {
      //IOManager.getInstance().exportProblemPDDL(problem, i);
      ioManager.exportProblemPDDL(problem, i);
      i++;
    }
    //IOManager.getInstance().exportDomainPDDL(domain);
    }

        
    ioManager.exportModel(model);
   
  }
}

