import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;


public class ParsePlan {
	
	static DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
	//static Pattern actionPlanPattern = Pattern.compile("([\\d]{1,5}\\.[\\d]{0,5}:\\s\\(add_action\\sa[\\d]+\\))\\n(?:(?!add_action).|\\n)*([\\d]{1,5}\\.[\\d]{0,5}:\\s\\(add_move_automata\\sa[\\d]+\\))");
	//static Pattern actionPlanPattern = Pattern.compile("([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_action\\sa[\\d]+\\))\\n(?:(?!add_action).|\\n)*([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_move_automata\\sa[\\d]+\\))");
	static Pattern actionPlanPattern = Pattern.compile("([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_action[\\s_]a[\\d]+([\\s_][a-zA-Z]+[\\d])?+\\))\\n(?:(?!add_action).|\\n)*([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_move_automata[\\s_]a[\\d]+([\\s_][a-zA-Z]+[\\d])?\\))");
	//static Pattern actionPlanWithResourcePattern = Pattern.compile("([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_action\\sa[\\d]+\\s[a-zA-Z]+[\\d]*\\))\\n(?:(?!add_action).|\\n)*([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_move_automata\\sa[\\d]+\\))");
    static Pattern actionPlanWithResourcePattern = Pattern.compile("([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_action[\\s_]a[\\d]+[\\s_][a-zA-Z]+[\\d]*\\))");
    static Pattern paramPattern = Pattern.compile("\\(add_parameter.*\\)\\n");
	static Pattern nPlansPattern = Pattern.compile("Found\\sPlan");
	
	private static String logHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\r\n" + 
			"<!-- This file has been generated with the OpenXES library. It conforms -->\r\n" + 
			"<!-- to the XML serialization of the XES standard for log storage and -->\r\n" + 
			"<!-- management. -->\r\n" + 
			"<!-- XES standard version: 1.0 -->\r\n" + 
			"<!-- OpenXES library version: 1.0RC7 -->\r\n" + 
			"<!-- OpenXES is available from http://www.openxes.org/ -->\r\n" + 
			"<log xes.version=\"1.0\" xes.features=\"nested-attributes\" openxes.version=\"1.0RC7\" xmlns=\"http://www.xes-standard.org/\">\r\n" + 
			"	<extension name=\"Organizational\" prefix=\"org\" uri=\"http://www.xes-standard.org/org.xesext\"/>\r\n" + 
			"	<extension name=\"Time\" prefix=\"time\" uri=\"http://www.xes-standard.org/time.xesext\"/>\r\n" + 
			"	<extension name=\"Lifecycle\" prefix=\"lifecycle\" uri=\"http://www.xes-standard.org/lifecycle.xesext\"/>\r\n" + 
			"	<extension name=\"Semantic\" prefix=\"semantic\" uri=\"http://www.xes-standard.org/semantic.xesext\"/>\r\n" + 
			"	<extension name=\"Concept\" prefix=\"concept\" uri=\"http://www.xes-standard.org/concept.xesext\"/>\r\n" + 
			"	<global scope=\"trace\">\r\n" + 
			"		<string key=\"concept:name\" value=\"__INVALID__\"/>\r\n" + 
			"	</global>\r\n" + 
			"	<global scope=\"event\">\r\n" + 
			"		<string key=\"concept:name\" value=\"__INVALID__\"/>\r\n" + 
			"	</global>\r\n" + 
			"	<classifier name=\"Event Name\" keys=\"concept:name\"/>\r\n" + 
			"	<string key=\"concept:name\" value=\"Artificial Log\"/>\r\n" + 
			"	<string key=\"lifecycle:model\" value=\"standard\"/>\r\n" + 
			"	<string key=\"source\" value=\"custom_loggen\"/>\r\n";
	
	private static String logEnd = "</log>\r\n";
	
	
	private static String traceStart = "\t<trace>\r\n" + 
			"\t\t<string key=\"concept:name\" value=\"Case No. 01\"/>\r\n";
	
	private static String traceEnd = "\t</trace>\r\n";
	
	private static String outputFile;
	
	static HashMap<String, String> activityMap;
	static HashMap<String, String> varTypes;
	
	public static void main(String[] args) throws Exception {
		// decl model 
		// mapping File
		// Input plan
		// output file	
        
		if (args.length != 4) {
			System.out.println(args+"\n");
		      throw new Error("Pass the following arguments:\n"+
                    "\t1. Path to DECLARE model\n"+
                    "\t2. Path to Activity Mapping file\n"+
                    "\t3. Path to generated Plan file\n"+
                    "\t4. Path to the output XES file.");
		}
		
		String declModelFile = args[0];
		String activityMappingFile = args[1];
		String inputPlanFile = args[2];
		outputFile = args[3];

        if (!declModelFile.endsWith(".decl")) {
            throw new Error("DECLARE model needs to be in .DECL format!");
        }

        if (!outputFile.endsWith(".xes")) {
            throw new Error("Output File needs to be in .XES format!");
        }
		
		// 1. Read the Activity Mapping File to decode the action names
		activityMap = readActivityMapping(activityMappingFile);
		
		// 2. Map all variables occurring in the plan to a pattern
		varTypes = getVariableTypes(declModelFile, inputPlanFile);
		
		try {
            ArrayList<ArrayList<Event>> traceList = getAllSuffixPlans(inputPlanFile);
			
			generateLog(traceList);

			System.out.println("XES successfully generated.");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static ArrayList<ArrayList<Event>> getAllSuffixPlans(String inputPath) throws Exception {
		
		ArrayList<ArrayList<Event>> allSuffix = new ArrayList<ArrayList<Event>>();
		
		String s = readFile(inputPath);
		
		Matcher nPlansMatch = nPlansPattern.matcher(s);
		
		int nPlans = 0;
		
		while(nPlansMatch.find()) {
			nPlans++;
		}
		
		String[] splitPlans = s.split("Found Plan:");
		
		for (int i = 0;i<splitPlans.length;i++) {
			// Go through all plans
			if (i == 0) {
				continue;
			}
			ArrayList<String> cs = getActionPlanFromString(splitPlans[i]); 
			ArrayList<Event> allLocalEvents = new ArrayList<Event>();
			
			
			for (int j =0;j<cs.size();j++) {
				allLocalEvents.add(parseActionPlan(cs.get(j)));
			}
			allSuffix.add(allLocalEvents);
			
		}
		
		return allSuffix;
	}
	
	public static ArrayList<String> getActionPlanFromString(String inputPlan) throws IOException {
		/**
		 * Reads in the .txt file containing a SINGLE action plan.
		 * Returns a List of action blocks.
		 */
		
		// Each event in the suffix consists of at least 3 actions in the resulting plan.
		// Split those into each group to process separately.
		
		ArrayList<String> actionPlan = new ArrayList<String>();

		//String[] splitPlan = inputPlan.split("([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_move_automata\\sa[\\d]+\\))");
		String[] splitPlan = inputPlan.split("([\\d]{1,5}(\\.[\\d]{0,5})?:\\s\\(add_move_automata[\\s_]a[\\d]+([\\s_][a-zA-Z]+[\\d])?\\))");
		for (int i = 0;i<splitPlan.length;i++) {
			if (splitPlan[i].contains("add_action")) {
		    	actionPlan.add(splitPlan[i]);
		    }
			
		}
		

		
		return actionPlan;
	}
	
	public static ArrayList<String> getActionPlan(String inputPath) throws IOException {
		/**
		 * Reads in the .txt file containing a SINGLE action plan.
		 * Returns a List of action blocks.
		 */
		
		// Each event in the suffix consists of at least 3 actions in the resulting plan.
		// Split those into each group to process separately.
		
		ArrayList<String> actionPlan = new ArrayList<String>();
		
		String s = readFile(inputPath);
		
		Matcher actionMatch = actionPlanPattern.matcher(s);
		
		while (actionMatch.find()) {
	    	String curGroup = actionMatch.group();
	    	actionPlan.add(curGroup);
	    }
		
		return actionPlan;
	}
	
	public static Event parseActionPlan(String currentEvent) throws Exception {
		/**
		 * String currentEvent: a multi-line String containing the different actions taken by the planner.
		 */
		
		//Event event = new ArrayList();
		
		LocalDateTime timeStamp;
		String activityName = "";
		HashMap<String, String> payload = new HashMap<String, String>();
		
		//1. The first assumption is that the addition and synchronization of the event happen at the same instance
		//2. The activity name is identical across all actions
		
		int start = currentEvent.indexOf("add_action") + 11;
		int end = currentEvent.indexOf(")", start);
		
		if (start >= end) {
			throw new Exception("Start >= End");
		}

        //Now I need to handle the possible space with the resource.

		int countOfUnderscore = currentEvent.substring(start, end).split("_").length;
		String activity;
		if (countOfUnderscore<2) {
			activity = currentEvent.substring(start, end).split(" ")[0]; // encoded activity: e.g. a7
		}
		else {
			activity = currentEvent.substring(start, end).split("_")[0]; // encoded activity: e.g. a7
		}
		
		activityName = activityMap.get(activity);
		
		// Now get time stamp
		timeStamp = getTimeStamp(currentEvent);

        Matcher resourceMatcher = actionPlanWithResourcePattern.matcher(currentEvent);
        String resource;

        if (resourceMatcher.find()) {
			if (currentEvent.substring(start, end).split("_").length < 2) {
				resource = currentEvent.substring(start, end).split(" ")[1];
			}
			else {
				resource = currentEvent.substring(start, end).split("_")[1];
			}
            
            payload.put("resource", resource);
        }
		
		// Now get payload
		// (add_parameter a12 sDEC2_1 categorical c2)
		if (currentEvent.contains("add_parameter")) {
			// TODO Something about creating the payload
			// currentEvent is the entire multi-line string
			// first find the number of occurrences of "add_parameter".
			Matcher paramMatch = paramPattern.matcher(currentEvent);

			
			while (paramMatch.find()) {
				String line = paramMatch.group();
				line = line.replace(")", "").replace("\n", "");
				String[] lines = line.split("\s");
				//0 is add_parameter
				//1 is activity
				//2 is automaton state
				//3 is parameter
				//4 is parameter value
				
				if (lines.length == 4) {
					/* 2026-03-16: When I fixed the bug to ensure that ALL automata states need to be checked I removed the automaton state input.
					 */

					//payload.put(lines[3], lines[4]);
					//payload.put(lines[2], lines[3]);

					if (lines[2].contains("integer")) {
						payload.put(lines[2], lines[3].substring(1));

					}
					else {
						payload.put(lines[2], lines[3]);
					}


					
				}
				else {
					// Getting into this branch means that the propositionalized version was used.
					// 
					lines = line.split("_");
					// 2026-04-13: Quick and Dirty Fix to remove the "v" infront of the integer value.
					if (lines[3].contains("integer")) {
						payload.put(lines[3], lines[4].substring(1));

					}
					else {
						payload.put(lines[3], lines[4]);
					}
						
				}


				
				//System.out.println(lines[3] + " " + lines[4]);
					
			}	
		}
		
		Event event = new Event(activityName, timeStamp, payload);

		return event;
		
	}
	
	public static LocalDateTime getTimeStamp(String event, String timeunit) throws Exception {
		//https://stackoverflow.com/questions/5175728/how-to-get-the-current-date-time-in-java
		// This should give me the current date time, but in long format.
		// Z.B.: Mon Feb 09 10:06:58 CET 2026
		
		if (!timeunit.equals("h") && !timeunit.equals("m") && !timeunit.equals("s") && !(timeunit.equals("d"))) {
			throw new Exception("Something went wrong with the time units");
		}
			
		//The expected date format for XES is: 2005-10-24T11:57:31.000+01:00
		LocalDateTime curDate = LocalDateTime.now();
		
		// The time interval to wait are the first characters in the plan.
		// Immediately followed by ":"
		int endI = event.indexOf(":");
		
		// Time difference from the first event, assumed to be the current time instant.
		String timeDiffString = event.substring(0, endI);
		Float timeDiffFloat = Float.parseFloat(timeDiffString);
		timeDiffFloat = timeDiffFloat / 10;

		LocalDateTime ts = curDate.plus(differenceInSeconds(timeDiffFloat, timeunit), ChronoUnit.SECONDS);
		
		return ts;
	}
	
	// Use default value of hours if unspecified.
	public static LocalDateTime getTimeStamp(String event) throws Exception {
		/**
		 * Overloaded method of getTimeStamp with the default time unit set to hours ("h")
		 */
		return getTimeStamp(event, "h");
	}
	

	private static long differenceInSeconds(Float timeDiff, String timeUnit) {
		/**
		 * Takes the given time difference and converts it to seconds.
		 * For example, 5.9h returns 2140s.
		 * If the time difference contains a decimal and the time unit is seconds, the nearest second value is returned.
		 */
		long td = 0;
		
		if (timeUnit.equals("d")) {
			float a = timeDiff.floatValue() * (float) (24*60.0 * 60.0);
			td = (long) Math.round(a);
		}
		
		if (timeUnit.equals("h")) {
						
			float a = timeDiff.floatValue() * (float) (60.0 * 60.0);
			td = (long) Math.round(a);
		}
		if (timeUnit.equals("m")) {
			
			float a = timeDiff.floatValue() * (float) (60.0);
			td = (long) Math.round(a);
		}
		
		if (timeUnit.equals("s")) {
			td = (long) Math.round(timeDiff.floatValue());
		}
				
		return td;
		
	}
	
	public static HashMap<String, String> readActivityMapping(String mappingFilePath) {
		/***
		 * Reads the provided activity mapping file into a dictionary.
		 */
		
		HashMap<String, String> mapping = new HashMap<String, String>();
		
		String s = readFile(mappingFilePath);
		
		// Since all items are read in as one big string I split them based on the new-line
		String[] lines = s.split("\n");
		
		// Instantiate the Dictionary Map with all the mappings (in reverse order)
		for (String l:lines) {
			String[] m = l.split(":");
			mapping.put(m[1], m[0]);
		}
		
		return mapping;
	}
	
	
	
	
	private static String readFile(String inputPath) {
		String s = "";
		try {
			s = new String(Files.readAllBytes(Paths.get(inputPath)));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return s;
	}
	
	public static HashMap<String, String> getVariableTypes(String modelPath, String planPath) {
		/**
		 * This function takes the DECLARE model and the plan output
		 * and returns all variables used in the plan alongside their type from <string, float, int>.
		 * Returns: HashMap<String, String> (varName:varType). 
		 * For example "temperature":"float"
		 */
		
		HashMap<String, String> varMap = new HashMap<String,String>();
		
		String m = readFile(modelPath);
		
		ArrayList<String> varNames = getVariableNames(planPath);
		
		String patEnum = "%s:(?=.*,).*";
		String patInt = "%s:\sinteger(?:(?!,).)*";
		String patFloat = "%s:\sfloat(?:(?!,).)*";
		
		for (String v : varNames) {
			Pattern patEnumCompiled = Pattern.compile(String.format(patEnum, v)); 
			Pattern patIntCompiled = Pattern.compile(String.format(patInt, v)); 
			Pattern patFloatCompiled = Pattern.compile(String.format(patFloat, v)); 
			
			Matcher enumMatch = patEnumCompiled.matcher(m);
			Matcher intMatch = patIntCompiled.matcher(m);
			Matcher floatMatch = patFloatCompiled.matcher(m);
			
			if (enumMatch.find()) {
				varMap.put(v, "string");
			}
			else if (intMatch.find()) {
				varMap.put(v, "int");
			}
			
			else if (floatMatch.find()) {
				varMap.put(v, "float");
			}
			
		}
		
		return varMap;
	}
	
	private static ArrayList<String> getVariableNames(String planPath) {
		/**
		 * Returns a list of all variables used in the suffix plan.
		 */
		
		ArrayList<String> var = new ArrayList<String>();
		String p = readFile(planPath);
		
		Matcher paramMatch = paramPattern.matcher(p);
		
		while (paramMatch.find()) {
			String line = paramMatch.group();
			line = line.replace(")", "").replace("\n", "");
			String[] lines = line.split("\s");
			//0 is add_parameter
			//1 is activity
			//2 is automaton state
			//3 is parameter
			//4 is parameter value

			if (lines.length == 4) {
					/* 2026-03-16: When I fixed the bug to ensure that ALL automata states need to be checked I removed the automaton state input.
					 */

				var.add(lines[2]);
					
				}
				else {
					// Getting into this branch means that the propositionalized version was used.
					// 
					lines = line.split("_");
					var.add(lines[3]);

				}	
			
			
		}
		
		return var;
	}
	
private static StringBuilder generateTrace(ArrayList<Event> listOfEvents, int id, HashMap<String, String> variableTypes) {
		
		StringBuilder sb = new StringBuilder();
		
		sb.append(traceStart.replace("Case No. 01", "Suffix No. " + id));
		
		for (int i = 0; i<listOfEvents.size();i++) {
			Event e = listOfEvents.get(i);
			
			Map<String, String> eventPayload = e.getPayload();
			sb.append(e.getLogEventTag());
			
			for (String ep : eventPayload.keySet()) {
                if (ep.equals("resource")) {
                    sb.append(e.getResourceVariable(ep, eventPayload.get(ep)));
                    continue;
                }

				String vt = variableTypes.get(ep);
				sb.append(e.getVariableTag(vt, ep, eventPayload.get(ep)));
				
			}
			
			
			sb.append(e.getTimeEventTag());
			sb.append(e.getEventEndTag());
		}
		
		
		sb.append(traceEnd);
		
		return sb;
	
		
	}
	
	public static void generateLog(ArrayList<ArrayList<Event>> allTraces) {
		/**
		 * Writes all plans to XES format
		 */
		
		File sflog = new File(outputFile);
		if (!sflog.exists()) {
			try {
				sflog.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(logHeader);
		int nTraces = allTraces.size();
		
		for (int i = 0;i<nTraces;i++) {
			ArrayList<Event> curTrace = allTraces.get(i);
			sb.append(generateTrace(curTrace, i, varTypes));
			
		}
		
		sb.append(logEnd);
		
		try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.APPEND)) {
			writer.write(sb.toString());
			writer.flush();
		} catch (IOException e) {
			System.err.println("Unable to write to event log file\n" + e.toString());
		}
		
	}
	

}