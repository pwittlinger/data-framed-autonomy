package translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import formula.Formula.Comparison;
import formula.Formulas;
import model.Guard;
import model.RecoveryStrategy;

/**
 * Hand-picked conditions, solved end to end: generator, then ENHSP. Skipped when ENHSP isn't found
 * (-Denhsp.jar=..., ENHSP_JAR, the local Maven repository or ../../framedinterface/dependencies/enhsp.jar).
 *
 * Activation: Not Response[A, B] with the condition on A, traces "A(x, c) then B" at the same time. The
 * constraint is violated exactly when the condition holds for A, so the optimal cost is the violation
 * cost or 0 (No-Recovery: unsolvable or 0). The truth values are written by hand and also checked
 * against the parser.
 * Target: Response[A, B] with the condition on B, trace "A" only. The planner has to add B, the optimal
 * cost is 0, and the values it picks for B must satisfy the condition (Reset, No-Recovery, Skip).
 */
class HandpickedFormulasPlanTest {

  private static final int VIOLATION_COST = 1000;

  // x, c of A in the four traces
  private static final Object[][] TRACES = {{5, "c1"}, {30, "c2"}, {95, "c1"}, {50, "c3"}};

  // Condition, and whether it holds in each trace (by hand)
  private static final Object[][] FORMULAS = {
      {"A.x > 10 or A.c is c2", new boolean[] {false, true, true, true}},
      {"not (A.x >= 10 and A.x <= 50)", new boolean[] {true, false, true, false}},
      {"A.c in (c2, c3) implies A.x < 40", new boolean[] {true, true, true, false}},
      {"(A.x < 10 or A.x > 90) and A.c is not c3", new boolean[] {true, false, true, false}},
      {"A.x > 20 xor A.c is c1", new boolean[] {true, true, false, true}},
      {"A.x <= 90 iff A.c not in (c1)", new boolean[] {false, true, true, true}},
      {"not (A.x > 20 and (A.c is c2 or A.c is c3))", new boolean[] {true, false, true, false}},
      {"A.x > 10 and A.x > 40", new boolean[] {false, false, true, true}},
      {"A.x > 60 and A.x < 40", new boolean[] {false, false, false, false}},
      {"A.x > 60 or A.x <= 60", new boolean[] {true, true, true, true}},
  };

  private static final Pattern ADDED = Pattern.compile("\\(add_parameter B (\\w+) (\\w+)\\)");

  @TempDir
  static Path temp;

  private static final ExecutorService POOL = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

  @AfterAll
  static void shutdown() {
    POOL.shutdownNow();
  }

  record Run(String output, Map<String, Integer> values, List<String> plans) {}

  @TestFactory
  Stream<DynamicTest> activationConditions() {
    Path enhsp = enhsp();
    List<DynamicTest> tests = new ArrayList<>();
    for (Object[] formula : FORMULAS) {
      String condition = (String) formula[0];
      boolean[] holds = (boolean[]) formula[1];
      tests.add(DynamicTest.dynamicTest("by hand = parser: " + condition, () -> {
        for (int i = 0; i < TRACES.length; i++) {
          assertEquals(holds[i], evaluate(condition, TRACES[i][0], TRACES[i][1]), condition + " on trace " + (i + 1));
        }
      }));
      for (RecoveryStrategy strategy : RecoveryStrategy.values()) {
        for (boolean intervals : strategy == RecoveryStrategy.RESET ? new boolean[] {false, true} : new boolean[] {false}) {
          // Not Response[B, A] never fires here (no A after B). It's there because the domain skips trace
          // events for free when all the arcs of their activity lead into failure states (skip-unused)
          String model = "Not Response[A, B] |" + condition + " | |\nNot Response[B, A] | | |";
          Future<Run> run = POOL.submit(() -> solve(enhsp, model, true, strategy, intervals));
          tests.add(DynamicTest.dynamicTest(strategy.getOptionName() + (intervals ? " --intervals" : "") + ": " + condition, () -> {
            List<String> plans = run.get(10, TimeUnit.MINUTES).plans();
            for (int i = 0; i < TRACES.length; i++) {
              String expected = !holds[i] ? "0.0"
                  : strategy == RecoveryStrategy.NO_RECOVERY ? "unsolvable" : VIOLATION_COST + ".0";
              assertEquals(expected, cost(plans.get(i)), condition + " on trace " + (i + 1));
            }
          }));
        }
      }
    }
    return tests.stream();
  }

  @TestFactory
  Stream<DynamicTest> targetConditions() {
    Path enhsp = enhsp();
    List<DynamicTest> tests = new ArrayList<>();
    for (Object[] formula : FORMULAS) {
      String condition = ((String) formula[0]).replace("A.", "T.");
      if (condition.equals("T.x > 60 and T.x < 40")) {
        continue; // no value satisfies it, the activation tests cover it
      }
      for (RecoveryStrategy strategy : RecoveryStrategy.values()) {
        if (strategy == RecoveryStrategy.DISCARD) {
          // The Discard domain's skip action can drop A for free here: its second condition (every failure
          // state of a soft constraint is current) is vacuously true when no constraint has a failure state
          continue;
        }
        for (boolean intervals : strategy == RecoveryStrategy.RESET ? new boolean[] {false, true} : new boolean[] {false}) {
          String model = "Response[A, B] | |" + condition + " |";
          Future<Run> run = POOL.submit(() -> solve(enhsp, model, false, strategy, intervals));
          tests.add(DynamicTest.dynamicTest(strategy.getOptionName() + (intervals ? " --intervals" : "") + ": " + condition, () -> {
            Run result = run.get(10, TimeUnit.MINUTES);
            String plan = result.plans().get(0);
            assertEquals("0.0", cost(plan), condition);
            Map<String, Object> picked = new HashMap<>();
            Matcher m = ADDED.matcher(plan);
            while (m.find()) {
              picked.put(m.group(1), m.group(1).equals("c") ? m.group(2) : result.values().get(m.group(2)));
            }
            // A value the planner didn't set doesn't matter for the condition, so it has to hold for any of them
            for (Object[] other : TRACES) {
              Object x = picked.getOrDefault("x", other[0]);
              Object c = picked.getOrDefault("c", other[1]);
              assertTrue(evaluate(condition.replace("T.", "A."), x, c), condition + " doesn't hold for the added B " + picked);
            }
          }));
        }
      }
    }
    return tests.stream();
  }

  // The condition on x and c, evaluated with the parser (no PDDL involved)
  private static boolean evaluate(String condition, Object x, Object c) {
    return Formulas.evaluate(Guard.parse(condition), literal -> {
      Comparison comparison = (Comparison) literal;
      Object actual = comparison.attribute().equals("x") ? x : c;
      if (actual == null) {
        return false;
      }
      if (comparison.value() instanceof Number expected) {
        int compare = Double.compare(((Number) actual).doubleValue(), expected.doubleValue());
        return switch (comparison.operator()) {
          case ">" -> compare > 0;
          case ">=" -> compare >= 0;
          case "<" -> compare < 0;
          case "<=" -> compare <= 0;
          case "=" -> compare == 0;
          default -> compare != 0;
        };
      }
      boolean equal = actual.equals(comparison.value());
      return comparison.operator().equals("=") == equal;
    });
  }

  private static Run solve(Path enhsp, String constraint, boolean withB, RecoveryStrategy strategy, boolean intervals) throws Exception {
    Path dir = Files.createTempDirectory(temp, "case");
    Files.writeString(dir.resolve("model.decl"), String.join("\n",
        "activity A", "bind A: x, c", "activity B", "bind B: x, c",
        "x: integer between 0 and 100", "c: c1, c2, c3", constraint, ""));
    Files.writeString(dir.resolve("log.xes"), log(withB));

    List<String> generator = new ArrayList<>(List.of(java(), "-cp", System.getProperty("java.class.path"), "Runner",
        "-d", "model.decl", "-l", "log.xes", "-a", "output/variable_values.txt", "-s", "output/variable_substitutions.txt",
        "-c", "output/cost_model.txt", "--recovery", strategy.getOptionName(), "--violation_cost", String.valueOf(VIOLATION_COST)));
    if (intervals) {
      generator.add("--intervals");
    }
    String output = run(generator, dir);
    Path pddl = dir.resolve("output").resolve("pddl");
    Path domain = pddl.resolve(DomainVariants.fileName(strategy, true, false));
    if (!Files.isRegularFile(domain)) {
      // Plain AND conditions use the normal domain. These models are DECLARE only, so they need the replay fix too
      domain = dir.resolve("domain.pddl");
      Files.writeString(domain, NandGuardEncoder.allowDeclareReplay(DomainVariants.base(strategy)));
    }

    Map<String, Integer> values = new HashMap<>();
    for (String line : Files.readAllLines(dir.resolve("output").resolve("variable_values.txt"))) {
      String[] parts = line.trim().split("\\s+");
      if (parts.length == 2) {
        values.put(parts[0], Integer.valueOf(parts[1]));
      }
    }

    List<String> plans = new ArrayList<>();
    for (int i = 1; Files.isRegularFile(pddl.resolve("problem" + i + ".pddl")); i++) {
      List<String> planner = new ArrayList<>(List.of(java(), "-Xmx1g", "-jar", enhsp.toString(), "-o", domain.toString(),
          "-f", pddl.resolve("problem" + i + ".pddl").toString(), "-planner", "opt-blind"));
      if (strategy == RecoveryStrategy.DISCARD || strategy == RecoveryStrategy.SKIP) {
        planner.add("-sdac"); // like FrAIm
      }
      plans.add(run(planner, dir));
    }
    assertEquals(withB ? TRACES.length : 1, plans.size(), "problems written, generator said:\n" + output);
    return new Run(output, values, plans);
  }

  private static String log(boolean withB) {
    StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<log xes.version=\"1.0\">\n");
    Object[][] traces = withB ? TRACES : new Object[][] {TRACES[0]};
    for (int i = 0; i < traces.length; i++) {
      b.append("  <trace>\n    <string key=\"concept:name\" value=\"t").append(i + 1).append("\"/>\n");
      b.append(event("A", traces[i][0], traces[i][1]));
      if (withB) {
        b.append(event("B", 0, "c1"));
      }
      b.append("  </trace>\n");
    }
    return b.append("</log>\n").toString();
  }

  private static String event(String activity, Object x, Object c) {
    return "    <event>\n      <string key=\"concept:name\" value=\"" + activity + "\"/>\n"
        + "      <date key=\"time:timestamp\" value=\"2026-01-01T00:00:00\"/>\n"
        + "      <int key=\"x\" value=\"" + x + "\"/>\n      <string key=\"c\" value=\"" + c + "\"/>\n    </event>\n";
  }

  private static String cost(String plan) {
    Matcher m = Pattern.compile("Metric \\(Search\\):(-?[\\d.]+)").matcher(plan);
    if (plan.contains("Problem Solved") && m.find()) {
      return m.group(1);
    }
    return plan.contains("Problem unsolvable") ? "unsolvable" : "error: " + plan.lines().reduce((a, b) -> b).orElse("");
  }

  private static String run(List<String> command, Path dir) throws Exception {
    Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes());
    if (!process.waitFor(5, TimeUnit.MINUTES)) {
      process.destroyForcibly();
    }
    return output;
  }

  private static String java() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  private static Path enhsp() {
    List<String> candidates = new ArrayList<>();
    candidates.add(System.getProperty("enhsp.jar"));
    candidates.add(System.getenv("ENHSP_JAR"));
    candidates.add(Path.of(System.getProperty("user.home"), ".m2", "repository", "it", "unibs", "enhsp", "enhsp-planner",
        "20-bb0415c", "enhsp-planner-20-bb0415c.jar").toString());
    candidates.add(Path.of("..", "..", "framedinterface", "dependencies", "enhsp.jar").toString());
    for (String candidate : candidates) {
      if (candidate != null && Files.isRegularFile(Path.of(candidate))) {
        return Path.of(candidate).toAbsolutePath();
      }
    }
    Assumptions.assumeTrue(false, "ENHSP not found, skipping the planner tests");
    return null;
  }
}
