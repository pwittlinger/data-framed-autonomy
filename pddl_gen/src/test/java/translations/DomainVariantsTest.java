package translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import model.RecoveryStrategy;

class DomainVariantsTest {

  @ParameterizedTest
  @EnumSource(RecoveryStrategy.class)
  void everyStrategyHasItsVariants(RecoveryStrategy strategy) throws Exception {
    String base = DomainVariants.base(strategy);
    String clauses = DomainVariants.withClauses(base);
    String multi = DomainVariants.withMulti(base);
    String both = DomainVariants.withMulti(clauses);

    assertTrue(clauses.contains("(clause_of ?c ?s1 ?s2 ?a)") && clauses.contains("(atom_value ?g - guard_atom)"));
    assertTrue(multi.contains("(recovery_finished ?i - instance)") && !multi.contains("(recovery_finished)"));
    assertTrue(both.contains(";; NAND") && both.contains(";; MULTI"));
    assertEquals("domain_framed_autonomy_process_" + strategy.getOptionName().toLowerCase() + "_time_costs_clauses_multi.pddl",
        DomainVariants.fileName(strategy, true, true));
  }
}
