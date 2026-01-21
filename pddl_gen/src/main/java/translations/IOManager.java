package translations;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.deckfour.xes.in.XesXmlParser;
import org.deckfour.xes.model.XLog;
import org.processmining.datapetrinets.io.DPNIOException;
import org.processmining.ltl2automaton.plugins.automaton.Automaton;
import org.processmining.ltl2automaton.plugins.automaton.DOTExporter;

import Automaton.VariableSubstitution;
import log.LogFile;
import model.DataPetriNet;
import model.DeclareModel;
import model.MixedModel;

public class IOManager {
  
  private static IOManager ioManager;
  private String projectPrefix = "";
  private String resourcesFolder;
  private String inputFolder;
  private String outputFolder;
  private String pddlFolder;
  private String currentPath;
  //private String alignmentFolder = outputFolder + "alignments" + File.separator;
  
  private IOManager() {
    this.setPaths();
  }

  public static IOManager getInstance() {
    if (ioManager == null) {
      ioManager = new IOManager();
    }
    return ioManager;
  }

  private void setPaths() {
    this.currentPath = System.getProperty("user.dir") + File.separator;
    this.outputFolder = currentPath + "output" + File.separator;
    this.pddlFolder = outputFolder + "pddl" + File.separator;

    /*
    
    this.resourcesFolder = this.projectPrefix + "src" + File.separator + "main" + File.separator + "resources" + File.separator;
    this.inputFolder = resourcesFolder + "input" + File.separator;
    this.outputFolder = resourcesFolder + "output" + File.separator;
    this.pddlFolder = outputFolder + "pddl" + File.separator;

    */

    // Create folders if these don't exist.
    /* 
    File inputDir = new File(inputFolder);
    if (!inputDir.exists()) {
      inputDir.mkdirs();
    }
    */
    File outputDir = new File(outputFolder);
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }
    File pddlDir = new File(pddlFolder);
    if (!pddlDir.exists()) {
      pddlDir.mkdirs();
    }
  }

  public void setProjectPrefix(String projectPrefix) {
    this.projectPrefix = projectPrefix + File.separator;
    this.setPaths();
  }
  
  //Section: Reading declare model
  public DeclareModel readDeclareModel(String modelFileName) {

    File declareFile = new File(modelFileName);
    if (!declareFile.isFile()) {
      declareFile = new File(currentPath + modelFileName);
    }

    HashMap<String, ArrayList<String[]>> parsedLines = readFile(declareFile);
    return new DeclareModel(parsedLines);
  }
  
  private HashMap<String, ArrayList<String[]>> readFile(File declareFile) {
    HashMap<String, ArrayList<String[]>> lines = initializeSortingMap();
    Pattern[] declarePatterns = getCompiledPatterns();
    try (Scanner scanner = new Scanner(declareFile)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        addToMap(line, lines, declarePatterns);
      }
    } catch (IOException e) {
      System.err.println(declareFile.getAbsolutePath());
      System.out.println("Error reading the declare file");
    }
    return lines;
  }

  public boolean variableAssignmentsExist(String fileName) {

    File variablesFile = new File(fileName);

    if (!variablesFile.isFile()) {
      variablesFile = new File(this.currentPath + fileName);
    }

    return variablesFile.exists();
  }

  public void exportVariableAssignments(String fileName, String content) {
    //File variablesFile = new File(this.inputFolder + fileName);

    try (FileWriter fileWriter = new FileWriter(this.currentPath + fileName)) {
      fileWriter.write(content);
    } catch (IOException e) {
      System.out.println("Error exporting variable assignment file");
    }

  }

  public Map<String, Integer> readVariableAssignments(String fileName) {
    HashMap<String, Integer> map = new HashMap<>();
    File variablesFile = new File(fileName);

    if (!variablesFile.isFile()) {
      variablesFile = new File(this.currentPath + fileName);
    }

    try (Scanner scanner = new Scanner(variablesFile)) {
      while (scanner.hasNextLine()) {

        String line = scanner.nextLine();
        if (line.isEmpty()) continue;

        String[] read = line.split(" ");
        if (read.length != 2) {
          throw new Error("Parsing error!");
        }

        map.put(read[0], Integer.valueOf(read[1]));
      }
    } catch (IOException e) {
      System.err.println(e);
    }

    return map;
  }
  public Set<VariableSubstitution> readVariablesSubstitutions(String fileName) {
    HashSet<VariableSubstitution> set = new HashSet<>();

    File assignmentFile = new File(fileName);

    if (!assignmentFile.isFile()) {
      assignmentFile = new File(this.currentPath + fileName);
    }

    try (Scanner scanner = new Scanner(assignmentFile)) {
      while (scanner.hasNextLine()) {

        String line = scanner.nextLine();
        if (line.isEmpty()) continue;

        String[] read = line.split(" ");
        if (read.length != 3) {
          throw new Error("Parsing error!");
        }

        VariableSubstitution va = new VariableSubstitution();
        va.variableName = read[0];
        va.activityName = read[1];
        va.categoryName = read[2];

        set.add(va);
      }
    } catch (IOException e) {
      System.err.println(e);
    }
    
    return set;
  }

  public boolean variableSubstitutionExists(String fileName) {
    File assignmentFile = new File(fileName);
    
    if (!assignmentFile.isFile()) {
      assignmentFile = new File(this.currentPath + fileName);
    }

    return assignmentFile.exists();
  }

  public void exportVariableSubstitution(String fileName, String content) {
    //File variablesFile = new File(this.inputFolder + fileName);

    try (FileWriter fileWriter = new FileWriter(this.currentPath + fileName)) {
      fileWriter.write(content);
    } catch (IOException e) {
      System.out.println("Error exporting variable substitution file");
    }

  }
  
  private HashMap<String, ArrayList<String[]>> initializeSortingMap() {
    HashMap<String, ArrayList<String[]>> lines = new HashMap<>();
    String[] lineDefinitions = {"activityLines", "bindingLines", "intAttributeLines", "floatAttributeLines", "enumAttributeLines", "unaryConstraintLines", "binaryConstraintLines"};
    for (String container : lineDefinitions) {
      lines.put(container, new ArrayList<>());
    }
    return lines;
  }
  
  private Pattern[] getCompiledPatterns() {
    Pattern activityPattern = Pattern.compile("^\\s*activity\\s+([a-zA-Z]+[a-zA-Z\\d]*)\\s*$");
    Pattern bindingPattern = Pattern.compile("^\\s*bind\\s+([a-zA-Z]+[a-zA-Z\\d]*(,\\s+[a-zA-Z]+[a-zA-Z\\d]*)*)\\s*:\\s+([a-zA-Z]+[a-zA-Z\\d]*(,\\s+[a-zA-Z]+[a-zA-Z\\d]*)*)\\s*$");
    
    Pattern intPattern = Pattern.compile("^\\s*([a-zA-Z]+[a-zA-Z\\d]*(,\\s+[a-zA-Z]+[a-zA-Z\\d]*)*)\\s*:\\s+integer\\s+between\\s+(-?\\d+)\\s+and\\s+(-?\\d+)\\s*$");
    Pattern floatPattern = Pattern.compile("^\\s*([a-zA-Z]+[a-zA-Z\\d]*(,\\s+[a-zA-Z]+[a-zA-Z\\d]*)*)\\s*:\\s+float\\s+between\\s+(-?\\d+\\.?\\d*)\\s+and\\s+(-?\\d+\\.?\\d*)\\s*$");
    Pattern enumPattern = Pattern.compile("^\\s*([a-zA-Z]+[a-zA-Z\\d]*(,\\s+[a-zA-Z]+[a-zA-Z\\d]*)*)\\s*:\\s+([a-zA-Z]+[a-zA-Z\\d]*(,\\s+[a-zA-Z]+[a-zA-Z\\d]*)*)\\s*$");
    
    Pattern unaryPattern = Pattern.compile("^([A-Za-z\\d]+)\\[([a-zA-Z]+[a-zA-Z\\d]*)]\\s+\\|\\s*([Aa-z\\d!=(),.<> -]*)\\|\\s*$");
    //Pattern binaryPattern = Pattern.compile("^([A-Za-z\\d -]+)\\[([a-zA-Z]+[a-zA-Z\\d]*),\\s*([a-zA-Z]+[a-zA-Z\\d]*)]\\s+\\|\\s*([Aa-zA-Z\\d!=(),.<> -]*)\\|\\s*([Ta-zA-Z\\d!=(),.<> -]*)\\|\\s*$");
    Pattern binaryPattern = Pattern.compile("^([A-Za-z\\d -]+)\\[([a-zA-Z]+[a-zA-Z\\d]*),\\s*([a-zA-Z]+[a-zA-Z\\d]*)]\\s+\\|\\s*([Aa-zA-Z\\d!=(),.<> -]*)\\|\\s*([Ta-zA-Z\\d!=(),.<> -]*)\\|\\s*$");
    
    Pattern numericConditionPattern = Pattern.compile("^\\s*[atAT].[a-zA-Z]+[a-zA-Z\\d]*\\s+(>=|<=|>|<|=|!=)\\s+-?\\d+.?\\d*\\s*$");
    Pattern enumConditionPattern = Pattern.compile("^\\s*[atAT].[a-zA-Z]+[a-zA-Z\\d]*\\s+(is not|is)\\s+[a-zA-Z]+[a-zA-Z\\d]*\\s*$*");
    Pattern listConditionPattern = Pattern.compile("^\\s*[atAT].[a-zA-Z]+[a-zA-Z\\d]*\\s+(not in|in)\\s+[a-zA-Z]+[a-zA-Z\\d]*(,\\s+[a-zA-Z]+[a-zA-Z\\d]*)*\\s*$");
    
    /*Pattern numericConditionPattern = Pattern.compile("^\\s{0,1}[atAT].[a-zA-Z]+[a-zA-Z\\d]*\\s+(>=|<=|>|<|=|!=)\\s+-?\\d+.?\\d*\\s{0,1}$");
    Pattern enumConditionPattern = Pattern.compile("^\\s{0,1}[atAT].[a-zA-Z]+[a-zA-Z\\d]*\\s+(is not|is)\\s+[a-zA-Z]+[a-zA-Z\\d]*\\s{0,1}$*");
    Pattern listConditionPattern = Pattern.compile("^\\s{0,1}[atAT].[a-zA-Z]+[a-zA-Z\\d]*\\s+(not in|in)\\s+[a-zA-Z]+[a-zA-Z\\d]*(,\\s+[a-zA-Z]+[a-zA-Z\\d]*)*\\s{0,1}$");*/
    return new Pattern[] {
      activityPattern, 
      bindingPattern, 
      intPattern, 
      floatPattern, 
      enumPattern, 
      unaryPattern, 
      binaryPattern,
      numericConditionPattern, 
      enumConditionPattern, 
      listConditionPattern
    };
  }
    
    
  //Section: Lines validation and addition
  private void addToMap(String line, HashMap<String, ArrayList<String[]>> lines, Pattern[] patterns) {
    Matcher activityMatcher = patterns[0].matcher(line);
    Matcher bindingMatcher = patterns[1].matcher(line);
    Matcher intMatcher = patterns[2].matcher(line);
    Matcher floatMatcher = patterns[3].matcher(line);
    Matcher enumMatcher = patterns[4].matcher(line);
    Matcher unaryMatcher = patterns[5].matcher(line);
    Matcher binaryMatcher = patterns[6].matcher(line);
    
    if (activityMatcher.find()) {
      lines.get("activityLines").add(new String[] {activityMatcher.group(1)});
    } else if (bindingMatcher.find()) {
      lines.get("bindingLines").add(tokenizeBinding(bindingMatcher));
    } else if (enumMatcher.find()) {
      lines.get("enumAttributeLines").add(tokenizeEnumAttribute(enumMatcher));
    } else if (floatMatcher.find()) {
      lines.get("floatAttributeLines").add(tokenizeNumericAttribute(floatMatcher));
    } else if (intMatcher.find()) {
      lines.get("intAttributeLines").add(tokenizeNumericAttribute(intMatcher));
    } else if (binaryMatcher.find()) {
      if (isConditionValid(binaryMatcher.group(4), patterns) && isConditionValid(binaryMatcher.group(5), patterns)) {
        lines.get("binaryConstraintLines").add(tokenizeBinaryConstraint(binaryMatcher));
      } else {
        System.out.println("Error parsing condition: " + binaryMatcher.group(4) + " or " + binaryMatcher.group(5));
      }
    } else if (unaryMatcher.find()) {
      if (isConditionValid(unaryMatcher.group(3), patterns)) {
        lines.get("unaryConstraintLines").add(tokenizeUnaryConstraint(unaryMatcher));
      } else {
        System.out.println("Error parsing condition: " + unaryMatcher.group(3));
      }
    } else {
      System.out.println("Error parsing line: " + line);
    }
  }
  
  
  //Section: Tokenization of valid lines
  private String[] tokenizeBinding(Matcher matcher) {
    return new String[] {matcher.group(1), matcher.group(3)};
  }
  
  private String[] tokenizeEnumAttribute(Matcher matcher) {
    return new String[] {matcher.group(1), matcher.group(3)};
  }
  
  private String[] tokenizeNumericAttribute(Matcher matcher) {
    return new String[] {matcher.group(1), matcher.group(3), matcher.group(4)};
  }
  
  private String[] tokenizeUnaryConstraint(Matcher matcher) {
    return new String[] {matcher.group(1), matcher.group(2), matcher.group(3)};
  }
  
  private String[] tokenizeBinaryConstraint(Matcher matcher) {
    return new String[] {matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5)};
  }
  
  
  //Section: Condition syntax validation
  private boolean isConditionValid(String condition, Pattern[] patterns) {
    if (condition != null && !condition.isBlank()) {
      String conditionToCheck = condition.replaceAll("[)(]", "");
      String[] singleConditions = conditionToCheck.split(" and | or | AND | OR ");
      for (String subCondition : singleConditions) {
        if (!isSubConditionValid(subCondition, patterns)) {
          return false;
        }
      }
      return true;
    }
    return true;
  }
  
  private boolean isSubConditionValid(String condition, Pattern[] patterns) {
    Matcher numericMatcher = patterns[7].matcher(condition);
    Matcher enumMatcher = patterns[8].matcher(condition);
    Matcher listMatcher = patterns[9].matcher(condition);
    return numericMatcher.find() || enumMatcher.find() || listMatcher.find();
  }
  
   
  //Section: Exporting model
  public void exportModel(DeclareModel model) {
    try (FileWriter fileWriter = new FileWriter(outputFolder + "model.txt")) {
      fileWriter.write(model.toString());
    } catch (IOException e) {
      System.out.println("Error creating the model file");
    }
  }
  
  
  //Section: Reading log file
  public LogFile readLog(String logFileName, MixedModel myMixedModel) {
    File logFile = new File(logFileName);

    if (!logFile.isFile()) {
      logFile = new File(this.currentPath + logFileName);
    }
    XLog xlog = null;
    XesXmlParser parser = new XesXmlParser();
    if (parser.canParse(logFile)) {
      try {
        xlog = parser.parse(logFile).get(0);
      } catch (Exception e) {
        System.out.println("Error reading the log file");
      }
    }
    //return new LogFile(xlog, myMixedModel.declareModel);
    return new LogFile(xlog, myMixedModel);
  }

  
  public LogFile readDeclareLog(String logFileName, DeclareModel model) {
    File logFile = new File(logFileName);
    
    if (!logFile.isFile()) {
      logFile = new File(this.currentPath + logFileName);
    }
    XLog xlog = null;
    XesXmlParser parser = new XesXmlParser();
    if (parser.canParse(logFile)) {
      try {
        xlog = parser.parse(logFile).get(0);
      } catch (Exception e) {
        System.out.println("Error reading the log file");
      }
    }
    return new LogFile(xlog, model);
  }
  
  
  //Section: Exporting log file
  public void exportLog(LogFile log) {
    try (FileWriter fileWriter = new FileWriter(outputFolder + "log.txt")) {
      fileWriter.write(log.toString());
    } catch (IOException e) {
      System.out.println("Error creating the model file");
    }
  }
  
  
  //Section: Exporting PDDL files
  public void exportProblemPDDL(String pddlContent, int traceNr) {
    try (FileWriter fileWriter = new FileWriter(pddlFolder + "problem" + traceNr + ".pddl")) {
      fileWriter.write(pddlContent);
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Error creating the problem pddl");
    }
  }
  
  public void exportDomainPDDL(String pddlContent) {
    try (FileWriter fileWriter = new FileWriter(pddlFolder + "domain.pddl")) {
      fileWriter.write(pddlContent);
    } catch (IOException e) {
      System.out.println("Error creating the domain pddl");
    }
  }
  
  public boolean costModelExists(String costsFileName) {
    File costModel = new File(costsFileName);
  
    if (!costModel.isFile()) {
      costModel = new File(this.currentPath + costsFileName);
    }

    return costModel.exists();
  }

  public void exportCostModel(String fileName, Set<String> actKeys) {
    //File variablesFile = new File(this.inputFolder + fileName);
    StringBuilder sb = new StringBuilder();

    for (String aK : actKeys) {
      sb.append(aK + " 1 1 1 1\n");
    }

    try (FileWriter fileWriter = new FileWriter(this.currentPath + fileName)) {
      fileWriter.write(sb.toString());
    } catch (IOException e) {
      System.out.println("Error exporting variable substitution file");
    }

  }
  
  //Section: Reading cost model
  public ArrayList<String[]> readCostModel(String costsFileName) {
    File costModel = new File(costsFileName);

    if (!costModel.isFile()) {
      costModel = new File(currentPath + costsFileName);
    }

    ArrayList<String[]> costsList = new ArrayList<>();

    try (Scanner scanner = new Scanner(costModel)) {
      while (scanner.hasNextLine()) {
        costsList.add(scanner.nextLine().split(" "));
      }
    } catch (IOException e) {
      System.out.println("Error reading the cost model");
    }
    return costsList;
  }
  
  public void exportEquivalenceClasses(HashMap<String, ArrayList<String>> classes) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, ArrayList<String>> eqClass : classes.entrySet()) {
      builder.append(eqClass.getKey()).append(" ");
      for (String classPart : eqClass.getValue()) {
        builder.append(classPart).append(" ");
      }
      builder.append("\\n");
    }
    
    try (FileWriter fileWriter = new FileWriter(outputFolder + "equivalenceClasses.txt")) {
      fileWriter.write(builder.toString());
    } catch (IOException e) {
      System.out.println("Error creating the model file");
    }
  }
  
  public void exportToDot(Automaton automaton) throws IOException {
    DOTExporter.exportToDot(automaton, "MyAutomata", new FileWriter(outputFolder + File.separator + "automaton.dot"));
  }

    //Section: Reading declare model
    public DataPetriNet readDataPetriNet(String modelFileName) throws FileNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, InvocationTargetException, DPNIOException {
      File dpnFilepath = new File(modelFileName);

    if (!dpnFilepath.isFile()) {
      dpnFilepath = new File(currentPath + modelFileName);
    }

      String dpnFile = dpnFilepath.getAbsolutePath();
      return new DataPetriNet(dpnFile);
    }
  
  public void exportActivityMapping(String activityMapping, String modelName) {
    modelName = findFileNameFromPath(modelName);
    try (FileWriter fileWriter = new FileWriter(outputFolder + "activityMapping_" + modelName + ".txt")) {
      fileWriter.write(activityMapping);
    } catch (IOException e) {
      System.out.println("Error creating the activityMapping file. \n" + e.toString());
    }
  }

  private static String findFileNameFromPath(String inputPath) {
    if (!inputPath.contains("/") && (!inputPath.contains("\\"))) {
      return inputPath;
    }

    if (inputPath.contains("/")) {
      
      int lastIndex = inputPath.lastIndexOf("/");
      return inputPath.substring(lastIndex+1);
    }

    if (inputPath.contains("\\")) {
      int lastIndex = inputPath.lastIndexOf("\\");
      return inputPath.substring(lastIndex+1);
    }
    return inputPath;
  }

}