// package java_helpers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class was used to collect summary data of the plans that were found. This class was tested more often, however it is not immune to errors when handling edge ases or different planners.
 * 
 * Please feel free to adapt it however you like.
 */
public class StatsExtractor {
  
  private int numOfTraces;

  private ArrayList<Double> costs;
  private ArrayList<Integer> planLengths;
  private ArrayList<Integer> statesNumbers;
  private ArrayList<Double> timesSpent;
  private ArrayList<Integer> maximumResidentSizes;
  private ArrayList<Integer> searchTimes;

  private double costAvg;
  private double planLengthAvg;
  private double statesNumberAvg;
  private double timeAvg;
  private double mrsAvg;
  private double completionRate;
  private double searchTimeAvg;

  List<Pattern> costPatterns;
  List<Pattern> planLengthPatterns;
  List<Pattern> statesPatterns;
  List<Pattern> timesSpentPatterns;
  List<Pattern> mrsPatterns;
  List<Pattern> searchTimePatterns;

  /** Check this pattern to ignore partial readings: won't be considered in the calculation. */
  List<Pattern> terminatedPatterns;
  private boolean ignoreCurrent = false;

  public static void main(String[] args) {

    String errString = new String(
      "Pass the following arguments:\n" +
      "1: file to read\n" +
      "2: file to write to\n" +
      "3: number of (expected traces)\n"
    );

    if (args.length != 3) {
      throw new Error(errString);
    }
    int value;
    try {
      value = Integer.parseInt(args[2]);
    } catch (NumberFormatException e) {
      throw new Error("Could not parse third argument!");
    }
    
    File file = new File(args[0]);

    StatsExtractor se = new StatsExtractor(value);
    
    try (Scanner s = new Scanner(file)) {
      se.parseFile(s);
      se.calculateStats();
      se.writeToFile(args[1]);
    } catch (FileNotFoundException e) {
      
      System.err.println("Could not parse entry file!");
      e.printStackTrace();
    }
  }

  public StatsExtractor(int value) {
    this.numOfTraces = value;
    this.terminatedPatterns = List.of(
      Pattern.compile("Command terminated by signal 9"),
      Pattern.compile("Command exited with non-zero status")
    );

    this.costs = new ArrayList<>(this.numOfTraces);
    this.planLengths = new ArrayList<>(this.numOfTraces);
    this.statesNumbers = new ArrayList<>(this.numOfTraces);
    this.timesSpent = new ArrayList<>(this.numOfTraces);
    this.maximumResidentSizes = new ArrayList<>(this.numOfTraces);
    this.searchTimes = new ArrayList<>(this.numOfTraces);

    // Order for all is: ff, enhsp, pddl4j
    this.costPatterns = List.of(
      Pattern.compile("plan cost: (\\d+\\.\\d+)"),
      Pattern.compile("Metric \\(Search\\):(\\d+\\.\\d+)")
      // PDDL4J
    );
    this.planLengthPatterns = List.of(
      Pattern.compile("^\\s+(\\d+):[\\s\\w]+$"),
      //Pattern.compile("(\\d+\\.\\d+):\\s\\([\\w\\s\\-]+\\)")
      Pattern.compile("(\\d+[\\.\\d]*):\\s(\\([\\w\\s\\-]+\\)|[-\\w]*\\s\\[\\d+\\.\\d\\])")
    );
    this.statesPatterns = List.of(
      Pattern.compile("\\sevaluating (\\d+) states"),
      Pattern.compile("States Evaluated:(\\d+)")
    );
    this.timesSpentPatterns = List.of(
      Pattern.compile("\\s*Elapsed \\(wall clock\\) time \\(h:mm:ss or m:ss\\):\\s(\\d{0,2}:?\\d+\\.\\d+)")
    );
    this.mrsPatterns = List.of(
      Pattern.compile("\\s*Maximum resident set size \\(kbytes\\):\\s(\\d+)")
    );
    this.searchTimePatterns = List.of(
      Pattern.compile("Search\\sTime\\s\\(msec\\):\\s(\\d*)")
    );
  }

  private void parseFile(Scanner s) {
    int lengthIndex = -1;
    while (s.hasNextLine()) {
      String line = s.nextLine();

      // If this is ENHSP that crashed. Ignore following lines until we cleanup
      if (this.crashed(line)) {
        this.ignoreCurrent = true;
      }

      lengthIndex = this.getIndex(line, lengthIndex);

      this.handleParse(line, this.costPatterns, this.costs, Double.class, false, false);
      // planLength done differently
      this.handleParse(line, this.statesPatterns, this.statesNumbers, Integer.class, false, false);
      this.handleParse(line, this.timesSpentPatterns, this.timesSpent, Double.class, true, false);
      this.handleParse(line, this.mrsPatterns, this.maximumResidentSizes, Integer.class, false, true);
      this.handleParse(line, this.searchTimePatterns, this.searchTimes, Integer.class, false, true);
    }
  }
  private boolean crashed(String line) {
    return this.terminatedPatterns.stream()
      .map(x -> x.matcher(line))
      .filter(mat -> mat.find())
      .count()
      != 0;
  }
  // Finds plan in a different way.
  private int getIndex(String line, int lengthIndex) {
    Matcher m = this.planLengthPatterns.stream()
      .map(x -> x.matcher(line))
      .filter(mat -> mat.find())
      .findFirst()
      .orElse(null);

    if (m != null) {
      return Double.valueOf(m.group(1)).intValue() + 1;
    } else if (lengthIndex != -1) { // End of plan, add to plan lengths
      this.planLengths.add(lengthIndex);
      return -1;
    }
    return lengthIndex;
  }
  // I HATE JAVA GENERICS
  @SuppressWarnings("unchecked")
  private <T> void handleParse(String line, List<Pattern> patterns, ArrayList<T> destinationList, Class<T> className, boolean manageTime, boolean clearIgnoreCurrent) {
    Matcher m = patterns.stream()
      .map(x -> x.matcher(line))
      .filter(mat -> mat.find())
      .findFirst()
      .orElse(null);

    // If match
    if (m != null) {

      if (this.ignoreCurrent) {

        if (clearIgnoreCurrent) {
          this.ignoreCurrent = false;
        }
        return;
      }

      // If match, and not to ignore, take.
      String str = m.group(1);
      if (manageTime) {
        String[] splitted = str.split(":");
        Double value = Double.valueOf(splitted[1]);
        value += 60 * Double.valueOf(splitted[0]);

        if (className.equals(Double.class)) {
          destinationList.add((T) value);
          return;
        }
      }

      if (className.equals(Double.class)) {
        destinationList.add((T) Double.valueOf(str));
      } else {
        destinationList.add((T) Integer.valueOf(str));
      }
    }
  }
  private void calculateStats() {
    if (this.costs.size() != this.planLengths.size() || 
    this.costs.size() != this.timesSpent.size() || 
    this.costs.size() != this.maximumResidentSizes.size()) {
      System.err.println(
        this.costs.size() + ";" + 
        this.planLengths.size() + ";" + 
        this.statesNumbers.size() + ";" +
        this.timesSpent.size() + ";" + 
        this.maximumResidentSizes.size() + ";" +
        this.searchTimes.size()
      );
      throw new Error("Sizes of arrays don't match! Check parsing!");
    } else {
      System.out.println("Calculating summary stats of " + this.costs.size() + " traces!");
    }

    this.costAvg = this.costs.stream()
      .mapToDouble(v -> v)
      .average()
      .orElse(-1);

    this.planLengthAvg = this.planLengths.stream()
      .mapToInt(v -> v)
      .average()
      .orElse(-1);

    this.statesNumberAvg = this.statesNumbers.stream()
      .mapToInt(v -> v)
      .average()
      .orElse(-1);

    this.timeAvg = this.timesSpent.stream()
      .mapToDouble(v -> v)
      .average()
      .orElse(-1);

    this.mrsAvg = this.maximumResidentSizes.stream()
      .mapToInt(v -> v)
      .map(x -> x/1024) // Transform into MBs.
      .average()
      .orElse(-1);

    this.completionRate = (this.costs.size() / (double) this.numOfTraces) * 100;

    this.searchTimeAvg = this.searchTimes.stream()
      .mapToInt(v -> v)
      .average()
      .orElse(-1);

  }
  private void writeToFile(String fileName) {
    
    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
      bw.write("Number of traces: " + this.costs.size() + "\n");
      bw.write(String.valueOf(this.costAvg) + "\n");
      bw.write(String.valueOf(this.planLengthAvg) + "\n");
      bw.write(String.valueOf(this.statesNumberAvg) + "\n");
      bw.write(String.valueOf(this.timeAvg) + "\n");
      bw.write(String.valueOf(this.mrsAvg) + "\n");
      bw.write(String.valueOf(this.completionRate) + "%\n");
      bw.write(String.valueOf(this.searchTimeAvg) + "\n");
      bw.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
