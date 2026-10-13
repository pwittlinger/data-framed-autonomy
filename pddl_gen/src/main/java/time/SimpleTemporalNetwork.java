package time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Simple Temporal Problem and its minimal network (same thing as the stp-minimal-network repo).
 *
 * Constraints are lo <= t_target - t_source <= hi. We turn them into a distance graph (source -> target
 * with weight hi, target -> source with weight -lo) and run Floyd-Warshall. Then:
 * - it's consistent iff there's no negative cycle (d[i][i] >= 0 for every i)
 * - the tightest bounds are -d[target][source] <= t_target - t_source <= d[source][target]
 * - a constraint is redundant if the others already give the same or a tighter bound
 * See Dechter, Meiri and Pearl, "Temporal constraint networks" (1991).
 */
public class SimpleTemporalNetwork {

  public static final double INF = Double.POSITIVE_INFINITY;

  /** lo <= t[target] - t[source] <= hi, use -INF / INF when a bound is missing. */
  public record Constraint(String source, String target, double lo, double hi) {
    public Constraint {
      if (lo > hi) {
        throw new IllegalArgumentException("Empty interval [" + lo + ", " + hi + "] between " + source + " and " + target);
      }
    }

    @Override
    public String toString() {
      return lo + " <= " + target + " - " + source + " <= " + hi;
    }
  }

  /** The tightest bounds between two points. */
  public record Interval(double lo, double hi) {}

  public static class InconsistentException extends RuntimeException {
    public InconsistentException(String message) {
      super(message);
    }
  }

  private final List<String> points = new ArrayList<>();
  private final List<Constraint> constraints = new ArrayList<>();

  public SimpleTemporalNetwork() {}

  public SimpleTemporalNetwork(List<String> points, List<Constraint> constraints) {
    points.forEach(this::addPoint);
    constraints.forEach(this::add);
  }

  public void addPoint(String point) {
    if (!points.contains(point)) {
      points.add(point);
    }
  }

  public Constraint add(String source, String target, double lo, double hi) {
    return add(new Constraint(source, target, lo, hi));
  }

  public Constraint add(Constraint constraint) {
    addPoint(constraint.source());
    addPoint(constraint.target());
    constraints.add(constraint);
    return constraint;
  }

  public List<String> getPoints() {
    return List.copyOf(points);
  }

  public List<Constraint> getConstraints() {
    return List.copyOf(constraints);
  }

  /** All-pairs shortest paths of the distance graph. skip leaves one constraint out (can be null). */
  double[][] distances(Constraint skip) {
    int n = points.size();
    Map<String, Integer> index = indexOfPoints();
    double[][] d = new double[n][n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        d[i][j] = i == j ? 0 : INF;
      }
    }
    for (Constraint c : constraints) {
      if (c == skip) {
        continue;
      }
      int i = index.get(c.source());
      int j = index.get(c.target());
      d[i][j] = Math.min(d[i][j], c.hi());
      d[j][i] = Math.min(d[j][i], -c.lo());
    }
    for (int k = 0; k < n; k++) {
      for (int i = 0; i < n; i++) {
        if (d[i][k] == INF) {
          continue;
        }
        for (int j = 0; j < n; j++) {
          if (d[i][k] + d[k][j] < d[i][j]) {
            d[i][j] = d[i][k] + d[k][j];
          }
        }
      }
    }
    return d;
  }

  private Map<String, Integer> indexOfPoints() {
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < points.size(); i++) {
      index.put(points.get(i), i);
    }
    return index;
  }

  private static boolean hasNegativeCycle(double[][] d) {
    for (int i = 0; i < d.length; i++) {
      if (d[i][i] < 0) {
        return true;
      }
    }
    return false;
  }

  public boolean isConsistent() {
    return !hasNegativeCycle(distances(null));
  }

  /** The tightest interval for every ordered pair of different points. Throws if the constraints contradict each other. */
  public Map<List<String>, Interval> minimalNetwork() {
    double[][] d = distances(null);
    if (hasNegativeCycle(d)) {
      throw new InconsistentException("The time windows contradict each other (negative cycle)");
    }
    Map<List<String>, Interval> network = new LinkedHashMap<>();
    for (int i = 0; i < points.size(); i++) {
      for (int j = 0; j < points.size(); j++) {
        if (i != j) {
          // + 0.0 turns -0.0 into 0.0, otherwise two equal intervals wouldn't be equals()
          network.put(List.of(points.get(i), points.get(j)), new Interval(-d[j][i] + 0.0, d[i][j] + 0.0));
        }
      }
    }
    return network;
  }

  public Interval tightest(String source, String target) {
    return minimalNetwork().get(List.of(source, target));
  }

  /** True if the other constraints already imply this one. */
  public boolean impliedWithout(Constraint constraint) {
    double[][] d = distances(constraint);
    if (hasNegativeCycle(d)) {
      return true; // the rest is already inconsistent, so it implies anything
    }
    Map<String, Integer> index = indexOfPoints();
    int i = index.get(constraint.source());
    int j = index.get(constraint.target());
    return d[i][j] <= constraint.hi() && -d[j][i] >= constraint.lo();
  }

  /**
   * Removes the redundant constraints and gives them back. We go one at a time and check each one
   * against what's left, otherwise two constraints that imply each other would both disappear.
   */
  public List<Constraint> removeRedundant() {
    List<Constraint> dropped = new ArrayList<>();
    for (Constraint constraint : new ArrayList<>(constraints)) {
      if (impliedWithout(constraint)) {
        constraints.remove(constraint);
        dropped.add(constraint);
      }
    }
    return dropped;
  }
}
