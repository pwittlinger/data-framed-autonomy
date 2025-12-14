import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import Automaton.VariableSubstitution;
import translations.IOManager;
import translations.PDDLGenerator;
import translations.PDDLGeneratorMixedModel;
import log.LogFile;
import model.DataPetriNet;
import model.DeclareModel;
import model.MixedModel;

public class Runner {

  public static void main(String[] args) throws Exception {

    /*
    args = new String[6];
    args[0] = "models\\model2_30.decl";
    args[1] = "test05_VT_DPN.pnml";

    
    if (args.length != 6) {
      String errString = new String(
        "Pass as args the names of the following files:\n" +
        "1: declare model\n" +
        "2: petri net\n" + 
        "3: trace\n" +
        "4: variables\n" +
        "5: variable substitutions\n" +
        "6: cost model"
      );
      System.err.println(args.length);
      throw new Error(errString);
    }
    */
    // args = new String[5];

    //findAlignments(args[0], args[1], args[2], args[3], args[4], args[5]);

    
    
    findAlignments(
      //"declare/a20g6/a20g6_7_timed_data_parsed.decl",
      "declare/a20g6/a20g6_7_timed_parsed.decl",
       "petrinet/a20g6.pnml",
       "logs/a20g6-prefix-non-conforming-4.xes",
       "variable_values_multi_model.txt", 
       "variable_subs/variable_substitutions_a20g6_7.decl.txt", 
       "cost_models/cost_model-a20g6.txt");
    
    /*
    findAlignments(
      //"declare/a20g6/a20g6_7_timed_data_parsed.decl",
      "declare/a40g17AND/a40g17AND_7_timed_data_parsed.decl",
       "petrinet/a40g17AND.pnml",
       "logs/a40g17AND-prefix-non-conforming-4.xes",
       "variable_values_multi_model.txt", 
       "variable_subs/variable_substitutions_a40g17AND_7.decl.txt", 
       "cost_models/cost_model-a40g17AND.txt");
    */

      
  }
  
  public static void findAlignments(
    String modelString, 
    String petriNetString,
    String traceString, 
    String variablesString, 
    String substitutionsString, 
    String costsString) 
    throws Exception 
  {

    // Read model and logs to find ltl formula
    IOManager ioManager = IOManager.getInstance();

    // In case the jar you run is outside the directory in which the project is; Add directory name as prefix.
    ioManager.setProjectPrefix("pddl_gen");
    
    DeclareModel model = ioManager.readDeclareModel(modelString); // OKAY!
    //model.assignCosts(ioManager.readCostModel(costsString)); // OKAY!

    Map<String, Integer> variableAssignments = ioManager.readVariableAssignments(variablesString);
    Set<VariableSubstitution> substitutions = ioManager.readVariablesSubstitutions(substitutionsString);

    System.out.println("Model: " + model);

    

    if ((petriNetString == "") | (petriNetString == null) | !(petriNetString.endsWith(".pnml"))) {
      model.assignCosts(ioManager.readCostModel(costsString)); // OKAY!
      LogFile log = ioManager.readDeclareLog(traceString, model);
      PDDLGenerator pddlGenerator = new PDDLGenerator(model);
    String domain = pddlGenerator.defineDomain();
    ArrayList<String> problems = log.generateProblems(pddlGenerator, variableAssignments, substitutions);

        int i = 1;
    for (String problem : problems) {
      IOManager.getInstance().exportProblemPDDL(problem, i);
      i++;
    }
    IOManager.getInstance().exportDomainPDDL(domain);
    }
    else {

      DataPetriNet petriNet = ioManager.readDataPetriNet(petriNetString);
      MixedModel myMixedModel = new MixedModel(petriNet, model);
      myMixedModel.assignCosts(ioManager.readCostModel(costsString)); // OKAY!

      LogFile log = ioManager.readLog(traceString, myMixedModel); // OKAY!
      
      PDDLGeneratorMixedModel pddlGenerator = new PDDLGeneratorMixedModel(myMixedModel);

      String domain = pddlGenerator.defineDomain();
      ArrayList<String> problems = log.generateProblems(pddlGenerator, variableAssignments, substitutions);

      String pnName = petriNetString;
      if (petriNetString.contains("\\")) {
        String[] s_ = petriNetString.split("\\\\");
        pnName = s_[s_.length-1];
      }
      ioManager.exportActivityMapping(pddlGenerator.activityMapping(), pnName);

        int i = 1;
    for (String problem : problems) {
      IOManager.getInstance().exportProblemPDDL(problem, i);
      i++;
    }
    IOManager.getInstance().exportDomainPDDL(domain);
    }

    
    //System.out.println(myMixedModel.declareModel.getActivities());

    
    ioManager.exportModel(model);
   

    // If formula exists, define and write PDDL problems.
    // PDDLGenerator pddlGenerator = new PDDLGenerator(model, ltlFormula);
    //PDDLGenerator pddlGenerator = new PDDLGenerator(model);
    //PDDLGeneratorMixedModel pddlGenerator = new PDDLGeneratorMixedModel(myMixedModel);
    //String domain = pddlGenerator.defineDomain();
    //ArrayList<String> problems = log.generateProblems(pddlGenerator, variableAssignments, substitutions);
    /*
    int i = 1;
    for (String problem : problems) {
      IOManager.getInstance().exportProblemPDDL(problem, i);
      i++;
    }
    IOManager.getInstance().exportDomainPDDL(domain);
    */
  }
}

