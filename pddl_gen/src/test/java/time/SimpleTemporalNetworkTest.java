package time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import model.DeclareConstraint;
import model.DeclareTemplate;
import time.SimpleTemporalNetwork.Constraint;
import time.SimpleTemporalNetwork.Interval;

class SimpleTemporalNetworkTest {

  private static final double INF = SimpleTemporalNetwork.INF;

  //Section: small examples we can check by hand

  @Test
  void chainTightensTheLongEdge() {
    // a -> b in [10, 20], b -> c in [30, 40], so a -> c can only be in [40, 60]
    SimpleTemporalNetwork stp = new SimpleTemporalNetwork();
    stp.add("a", "b", 10, 20);
    stp.add("b", "c", 30, 40);
    stp.add("a", "c", 0, 100);
    assertEquals(new Interval(40, 60), stp.tightest("a", "c"));
    assertEquals(new Interval(-60, -40), stp.tightest("c", "a"));
  }

  @Test
  void longEdgeIsRedundant() {
    SimpleTemporalNetwork stp = new SimpleTemporalNetwork();
    stp.add("a", "b", 10, 20);
    stp.add("b", "c", 30, 40);
    Constraint loose = stp.add("a", "c", 0, 100);
    assertEquals(List.of(loose), stp.removeRedundant());
    assertEquals(2, stp.getConstraints().size());
  }

  @Test
  void contradictionIsDetected() {
    SimpleTemporalNetwork stp = new SimpleTemporalNetwork();
    stp.add("a", "b", 10, 20);
    stp.add("b", "c", 10, 20);
    stp.add("a", "c", 0, 5);
    assertFalse(stp.isConsistent());
    assertThrows(SimpleTemporalNetwork.InconsistentException.class, stp::minimalNetwork);
  }

  @Test
  void twoConstraintsThatImplyEachOtherDontBothGo() {
    SimpleTemporalNetwork stp = new SimpleTemporalNetwork();
    stp.add("a", "b", 1, 2);
    stp.add("a", "b", 1, 2);
    assertEquals(1, stp.removeRedundant().size());
    assertEquals(1, stp.getConstraints().size());
  }

  //Section: random networks against Bellman-Ford and against forcing the bounds

  private static SimpleTemporalNetwork randomNetwork(Random rng) {
    SimpleTemporalNetwork stp = new SimpleTemporalNetwork();
    List<String> points = List.of("p0", "p1", "p2", "p3", "p4");
    points.forEach(stp::addPoint);
    for (int k = 0; k < 7; k++) {
      int i = rng.nextInt(points.size());
      int j = (i + 1 + rng.nextInt(points.size() - 1)) % points.size();
      double lo = rng.nextInt(13) - 6;
      double hi = lo + rng.nextInt(7);
      stp.add(points.get(i), points.get(j), rng.nextDouble() < 0.15 ? -INF : lo, rng.nextDouble() < 0.15 ? INF : hi);
    }
    return stp;
  }

  // Shortest paths from one point, written separately from Floyd-Warshall on purpose. Null on a negative cycle.
  private static Map<String, Double> bellmanFord(SimpleTemporalNetwork stp, String source) {
    Map<String, Double> dist = new HashMap<>();
    stp.getPoints().forEach(p -> dist.put(p, INF));
    dist.put(source, 0.0);
    List<Object[]> edges = new ArrayList<>();
    for (Constraint c : stp.getConstraints()) {
      edges.add(new Object[] {c.source(), c.target(), c.hi()});
      edges.add(new Object[] {c.target(), c.source(), -c.lo()});
    }
    for (int round = 0; round < stp.getPoints().size(); round++) {
      boolean changed = false;
      for (Object[] e : edges) {
        double candidate = dist.get((String) e[0]) + (double) e[2];
        if (candidate < dist.get((String) e[1])) {
          dist.put((String) e[1], candidate);
          changed = true;
        }
      }
      if (!changed) {
        return dist;
      }
    }
    return null;
  }

  @Test
  void floydWarshallMatchesBellmanFord() {
    Random rng = new Random(7);
    for (int run = 0; run < 300; run++) {
      SimpleTemporalNetwork stp = randomNetwork(rng);
      double[][] d = stp.distances(null);
      List<String> points = stp.getPoints();
      for (int i = 0; i < points.size(); i++) {
        Map<String, Double> dist = bellmanFord(stp, points.get(i));
        if (dist == null) {
          assertFalse(stp.isConsistent());
          break;
        }
        for (int j = 0; j < points.size(); j++) {
          assertEquals(dist.get(points.get(j)), d[i][j], 1e-9);
        }
      }
    }
  }

  @Test
  void minimalBoundsAreReachableAndDroppingKeepsTheNetwork() {
    Random rng = new Random(11);
    for (int run = 0; run < 300; run++) {
      SimpleTemporalNetwork stp = randomNetwork(rng);
      if (!stp.isConsistent()) {
        continue;
      }
      Map<List<String>, Interval> network = stp.minimalNetwork();
      for (Map.Entry<List<String>, Interval> entry : network.entrySet()) {
        for (double bound : new double[] {entry.getValue().lo(), entry.getValue().hi()}) {
          if (Double.isInfinite(bound)) {
            continue;
          }
          SimpleTemporalNetwork forced = new SimpleTemporalNetwork(stp.getPoints(), stp.getConstraints());
          forced.add(entry.getKey().get(0), entry.getKey().get(1), bound, bound);
          assertTrue(forced.isConsistent(), "bound " + bound + " of " + entry.getKey() + " is not reachable");
        }
      }
      SimpleTemporalNetwork reduced = new SimpleTemporalNetwork(stp.getPoints(), stp.getConstraints());
      reduced.removeRedundant();
      assertEquals(network, reduced.minimalNetwork());
    }
  }

  //Section: the preprocessing on DECLARE constraints

  private static DeclareConstraint response(String a, String b, double min, double max) {
    DeclareConstraint dc = new DeclareConstraint(DeclareTemplate.Response, a, null, b, null);
    dc.setTargetTimeConditions(min, max);
    return dc;
  }

  @Test
  void preprocessingTightensAndDropsTargetWindows() {
    DeclareConstraint ab = response("A", "B", 2, 5);
    DeclareConstraint bc = response("B", "C", 1, 3);
    DeclareConstraint ac = response("A", "C", 0, 50); // implied by the two above (3 to 8)
    DeclareConstraint noWindow = new DeclareConstraint(DeclareTemplate.Response, "C", null, "D", null);

    TimeWindowPreprocessor.Report report = TimeWindowPreprocessor.apply(Arrays.asList(ab, bc, ac, noWindow));
    assertTrue(report.consistent());
    assertEquals(3, report.windowsBefore());
    assertEquals(List.of(ac), report.redundant());
    assertNull(ac.getTargetTimeConditions());
    // The others stay as they were, and a constraint without a window keeps the default
    assertEquals(List.of(2.0, 5.0), Arrays.stream(ab.getTargetTimeConditions()).boxed().toList());
    assertEquals(List.of(0.0, 100.0), Arrays.stream(noWindow.getTargetTimeConditions()).boxed().toList());
  }

  @Test
  void contradictingWindowsAreLeftAlone() {
    DeclareConstraint ab = response("A", "B", 10, 20);
    DeclareConstraint bc = response("B", "C", 10, 20);
    DeclareConstraint ac = response("A", "C", 0, 5);
    TimeWindowPreprocessor.Report report = TimeWindowPreprocessor.apply(List.of(ab, bc, ac));
    assertFalse(report.consistent());
    assertEquals(List.of(0.0, 5.0), Arrays.stream(ac.getTargetTimeConditions()).boxed().toList());
  }
}
