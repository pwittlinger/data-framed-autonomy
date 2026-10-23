package translations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts the problems of all the traces of a log in one problem, one instance per trace (option -i).
 *
 * The instances don't interact, so we just take the problem of every trace and rename its objects with
 * _i1, _i2, ... (trace states, automaton states, activities, constraints, guard clauses). Parameter and
 * value names stay shared, they mean the same in every instance. The few flags that were global in the
 * domain are per instance in the _multi domain variant, which needs the facts we add here:
 * (activity_of a i) and (trace_state_of t i), and (recovery_finished i) instead of (recovery_finished).
 */
public final class MultiInstanceMerger {

  public static final String MULTI_DOMAIN = "domain_framed_autonomy_process_reset_time_multi.pddl";
  public static final String CLAUSES_MULTI_DOMAIN = "domain_framed_autonomy_process_reset_time_clauses_multi.pddl";
  public static final String PROBLEM_FILE = "problem_multi.pddl";

  // These mean the same thing in every instance, so they're not renamed
  private static final Set<String> SHARED_TYPES = Set.of("parameter_name", "value_name");
  private static final Pattern TOKEN = Pattern.compile("[^\\s()]+");

  private MultiInstanceMerger() {}

  private record Problem(Map<String, List<String>> objects, List<String> init, List<String> goal) {}

  /** One problem with every input problem as its own instance. */
  public static String merge(List<String> problems) {
    Map<String, Set<String>> objects = new LinkedHashMap<>();
    // Lines that are the same in every instance (like the total cost) only go in once
    Set<String> sharedInit = new LinkedHashSet<>();
    Set<String> sharedGoal = new LinkedHashSet<>();
    List<String> init = new ArrayList<>();
    List<String> goal = new ArrayList<>();

    for (int k = 1; k <= problems.size(); k++) {
      String instance = "i" + k;
      Problem problem = parse(problems.get(k - 1));
      Map<String, String> renamed = new HashMap<>();

      for (Map.Entry<String, List<String>> entry : problem.objects().entrySet()) {
        String type = entry.getKey();
        Set<String> names = objects.computeIfAbsent(type, t -> new LinkedHashSet<>());
        for (String name : entry.getValue()) {
          if (SHARED_TYPES.contains(type)) {
            names.add(name);
          } else {
            renamed.put(name, name + "_" + instance);
            names.add(name + "_" + instance);
            if (type.equals("activity")) {
              init.add("    (activity_of " + name + "_" + instance + " " + instance + ")");
            } else if (type.equals("trace_state")) {
              init.add("    (trace_state_of " + name + "_" + instance + " " + instance + ")");
            }
          }
        }
      }

      for (String line : problem.init()) {
        // A trace without events starts with its prefix already over
        line = line.replace("(recovery_finished)", "(recovery_finished " + instance + ")");
        addLine(rename(line, renamed), line, renamed, init, sharedInit);
      }
      for (String line : problem.goal()) {
        addLine(rename(line, renamed), line, renamed, goal, sharedGoal);
      }
    }
    objects.computeIfAbsent("instance", t -> new LinkedHashSet<>());
    for (int k = 1; k <= problems.size(); k++) {
      objects.get("instance").add("i" + k);
    }

    StringBuilder b = new StringBuilder();
    b.append("(define (problem prob-multi)\n  (:domain trace-alignment)\n\n");
    b.append("  ;; " + problems.size() + " instances (one per trace), objects renamed with _i<k>\n");
    b.append("  (:objects\n");
    objects.forEach((type, names) -> {
      if (!names.isEmpty()) {
        b.append("    " + String.join(" ", names) + " - " + type + "\n");
      }
    });
    b.append("  )\n  (:init\n");
    sharedInit.forEach(line -> b.append(line).append("\n"));
    init.forEach(line -> b.append(line).append("\n"));
    b.append("  )\n  (:goal (and\n");
    sharedGoal.forEach(line -> b.append(line).append("\n"));
    goal.forEach(line -> b.append(line).append("\n"));
    b.append("  ))\n\n  (:metric minimize (total_cost))\n)\n");
    return b.toString();
  }

  // A complete fact that doesn't mention anything of the instance is the same everywhere, we keep it once.
  // Everything else (also the pieces of a fact over more lines, like an (or ...) in the goal) stays as it is.
  private static void addLine(String renamedLine, String original, Map<String, String> renamed,
      List<String> lines, Set<String> shared) {
    if (renamedLine.isBlank()) {
      return;
    }
    boolean ownsSomething = !renamedLine.equals(original) || original.contains("(recovery_finished ");
    if (!ownsSomething && balanced(renamedLine) && renamedLine.trim().startsWith("(")) {
      shared.add(renamedLine);
    } else {
      lines.add(renamedLine);
    }
  }

  private static boolean balanced(String line) {
    int depth = 0;
    for (char c : line.toCharArray()) {
      depth += c == '(' ? 1 : c == ')' ? -1 : 0;
      if (depth < 0) {
        return false;
      }
    }
    return depth == 0;
  }

  private static String rename(String line, Map<String, String> renamed) {
    Matcher m = TOKEN.matcher(line);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(out, Matcher.quoteReplacement(renamed.getOrDefault(m.group(), m.group())));
    }
    m.appendTail(out);
    return out.toString();
  }

  // The problems all come from our generators, so we can rely on their layout
  private static Problem parse(String problem) {
    problem = problem.replace("\r\n", "\n");
    int objectsStart = problem.indexOf("(:objects") + "(:objects".length();
    int initStart = problem.indexOf("(:init");
    int goalStart = problem.indexOf("(:goal (and");
    int metric = problem.indexOf("(:metric");
    if (objectsStart < "(:objects".length() || initStart < 0 || goalStart < 0 || metric < 0) {
      throw new IllegalArgumentException("Not a problem made by our generator");
    }

    Map<String, List<String>> objects = new LinkedHashMap<>();
    String objectBlock = problem.substring(objectsStart, problem.lastIndexOf(')', initStart));
    for (String line : objectBlock.split("\n")) {
      int dash = line.lastIndexOf(" - ");
      if (dash < 0) {
        continue;
      }
      String type = line.substring(dash + 3).trim();
      List<String> names = objects.computeIfAbsent(type, t -> new ArrayList<>());
      for (String name : line.substring(0, dash).trim().split("\\s+")) {
        if (!name.isEmpty()) {
          names.add(name);
        }
      }
    }

    String initBlock = problem.substring(initStart + "(:init".length(), problem.lastIndexOf(')', goalStart));
    String goalBlock = problem.substring(goalStart + "(:goal (and".length(), problem.lastIndexOf("))", metric));
    return new Problem(objects, List.of(initBlock.split("\n")), List.of(goalBlock.split("\n")));
  }

  /** Copies the multi domain (with or without the nand clauses) from the jar into the folder. */
  public static Path exportDomain(Path folder, boolean clauses, boolean declareOnly) throws IOException {
    String name = clauses ? CLAUSES_MULTI_DOMAIN : MULTI_DOMAIN;
    try (InputStream in = MultiInstanceMerger.class.getResourceAsStream("/domains/" + name)) {
      if (in == null) {
        throw new IOException("The domain is missing from the jar (/domains/" + name + ")");
      }
      Files.createDirectories(folder);
      Path target = folder.resolve(name);
      String domain = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Files.writeString(target, declareOnly ? NandGuardEncoder.allowDeclareReplay(domain) : domain);
      return target;
    }
  }
}
