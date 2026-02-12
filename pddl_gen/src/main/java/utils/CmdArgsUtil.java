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

        Option declareParam = new Option("d", "declare", true, "Path to (MP-)DECLARE model ");
		declareParam.setRequired(true);
		options.addOption(declareParam);

        Option petriParam = new Option("p", "petri", true, "Path to Petri-net model");
        options.addOption(petriParam);

		Option logParam = new Option("o", "log", true, "Input event log path");
		logParam.setRequired(true);
		options.addOption(logParam);

        Option costParam = new Option("c", "cost", true, "Path to cost model file (contains cost for each activity in the process frame)");
        options.addOption(costParam);

        Option varAssignment = new Option("a", "varAssign",true,  "Path to the variable mapping (which activity in frame is associated with which variable value)");
        options.addOption(varAssignment);

        Option varSubs = new Option("s", "varSub",true,  "Path to the variable substitution");
        options.addOption(varSubs);

        Option multiInstance = new Option("i","multiinstance",false,  "Flatten the Eventlog into a single PDDL file");
        options.addOption(multiInstance);


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
