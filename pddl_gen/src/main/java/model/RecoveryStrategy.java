package model;

import java.util.Arrays;

/**
 * What happens once a constraint is permanently violated (its automaton reaches a failure state).
 * Of these, only Discard changes the generated problem files. Reset, No-Recovery and Skip
 * write the same final states and are told apart by the domain.
 * Hard constraints (x in the violation cost file) are never recovered, whatever the strategy;
 * for Skip their failure states are therefore written differently (see skipsFailureStates).
 */
public enum RecoveryStrategy {
  // The violated constraint is reset to its initial state, so it can be monitored again.
  RESET("Reset"),
  // Failure states are non-accepting and cannot be left, a violation makes the problem unsolvable.
  NO_RECOVERY("No-Recovery"),
  // The constraint is ignored once violated: its failure states become accepting.
  DISCARD("Discard"),
  // Events that would violate the constraint are skipped for it, it returns to the state before the violation.
  SKIP("Skip");

  private final String optionName;

  RecoveryStrategy(String optionName) {
    this.optionName = optionName;
  }

  public String getOptionName() {
    return optionName;
  }

  // The domain this strategy plans with, e.g. domain_framed_autonomy_process_reset_time_costs.pddl
  public String getDomainFileName() {
    return "domain_framed_autonomy_process_" + optionName.toLowerCase() + "_time_costs.pddl";
  }

  public boolean acceptsFailureStates() {
    return this == DISCARD;
  }

  // The Skip domain never moves an automaton into a failure_state, it only charges the violation cost.
  public boolean skipsFailureStates() {
    return this == SKIP;
  }

  public static RecoveryStrategy fromOptionName(String optionName) {
    for (RecoveryStrategy strategy : values()) {
      if (strategy.optionName.equalsIgnoreCase(optionName.strip())) {
        return strategy;
      }
    }
    throw new Error("Unknown recovery strategy " + optionName + ", expected one of "
      + Arrays.stream(values()).map(RecoveryStrategy::getOptionName).toList());
  }
}
