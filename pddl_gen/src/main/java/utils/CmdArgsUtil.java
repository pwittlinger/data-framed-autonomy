package utils;
import org.apache.commons.cli.*;

public class CmdArgsUtil {

    private CmdArgsUtil() {

            /*String modelString, 
    String petriNetString,
    String traceString, 
    String variablesString, 
    String substitutionsString, 
    String costsString*/
    }

    public static CommandLine parseCmdInputs(String[] args) {
        Options options = new Options();

        Option declareParam = new Option("d", "declare", true, "Path to (MP-)DECLARE model(s), comma-separated for multiple");
		declareParam.setRequired(true);
		options.addOption(declareParam);

        Option petriParam = new Option("p", "petri", true, "Path to Petri-net model(s), comma-separated for multiple");
        options.addOption(petriParam);

		Option logParam = new Option("l", "log", true, "Input event log path");
		logParam.setRequired(true);
		options.addOption(logParam);

        Option costParam = new Option("c", "cost", true, "Path to cost model file (contains cost for each activity in the process frame)");
        options.addOption(costParam);

        Option varAssignment = new Option("a", "varAssign",true,  "Path to the variable mapping (which activity in frame is associated with which variable value)");
        options.addOption(varAssignment);

        Option varSubs = new Option("s", "varSub",true, "Path to the variable substitution");
        options.addOption(varSubs);

        Option multiInstance = new Option("i","multiinstance",false,  "Also write one problem with all the traces as separate instances (problem_multi.pddl, with the _multi domain)");
        options.addOption(multiInstance);

        Option stp = new Option("t", "stp", false, "Check the time windows with the STP minimal network: says if they contradict each other and which ones the others imply (changes nothing)");
        options.addOption(stp);

        Option stpApply = new Option(null, "stp-apply", false, "Also tighten the windows and drop the implied ones. Careful: the planner can violate constraints, and then the result changes (fewer violations counted), so don't use it for experiments");
        options.addOption(stpApply);

        Option intervals = new Option(null, "intervals", false, "Minimal intervals on the data values: simplifies the conditions and gives the planner one value per region the conditions tell apart (exact, the plans cost the same)");
        options.addOption(intervals);

        Option outputPath = new Option("o", "output",true,  "Path to the output directory (default in current path)");
        options.addOption(outputPath);


		CommandLineParser parser = new org.apache.commons.cli.DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;

		try {
			cmd = parser.parse(options, args, true);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("java -jar .\\pddl_gen-1.0-SNAPSHOT-launcher.jar ", options);
			System.exit(1);
		}

		return cmd;
    }
    


}
