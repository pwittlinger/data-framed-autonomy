package formula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import formula.Formula.And;
import formula.Formula.Comparison;
import formula.Formula.Const;
import formula.Formula.Iff;
import formula.Formula.Implies;
import formula.Formula.Literal;
import formula.Formula.Nand;
import formula.Formula.Nor;
import formula.Formula.Not;
import formula.Formula.Or;
import formula.Formula.Var;
import formula.Formula.Xor;

class NandNormalFormTest {

  /** Exhaustive truth-table check, treating every atom as an independent boolean variable. */
  private static void assertEquivalent(Formula f, Formula g) {
    Set<String> keySet = new TreeSet<>(Formulas.literalKeys(f));
    keySet.addAll(Formulas.literalKeys(g));
    List<String> keys = new ArrayList<>(keySet);
    for (int bits = 0; bits < (1 << keys.size()); bits++) {
      Map<String, Boolean> env = new HashMap<>();
      for (int i = 0; i < keys.size(); i++) {
        env.put(keys.get(i), (bits & (1 << i)) != 0);
      }
      assertEquals(Formulas.evaluate(f, env), Formulas.evaluate(g, env),
          () -> "differ on " + env + " for " + Formulas.render(f) + " vs " + Formulas.render(g));
    }
  }

  /** And of (atom | nand(atoms)); no not/or anywhere. */
  private static void assertNandShape(Formula f) {
    if (f instanceof Const) {
      return;
    }
    List<Formula> conjuncts = f instanceof And and ? and.operands() : List.of(f);
    for (Formula conjunct : conjuncts) {
      if (conjunct instanceof Nand nand) {
        assertTrue(nand.operands().size() >= 2, Formulas.render(conjunct));
        assertTrue(nand.operands().stream().allMatch(o -> o instanceof Literal), Formulas.render(conjunct));
      } else {
        assertTrue(conjunct instanceof Literal, Formulas.render(conjunct));
      }
    }
  }

  private static Var p = new Var("p");
  private static Var q = new Var("q");
  private static Var r = new Var("r");

  //Section: parsing

  @Test
  void parsesDeclStyleConditions() {
    assertEquals(new Comparison("A", "amount", ">", 10), FormulaParser.parse("A.amount > 10"));
    assertEquals(new Comparison("T", "cat", "is not", "c1"), FormulaParser.parse("T.cat is not c1"));
    assertEquals(new Comparison("A", "cat", "in", List.of("c1", "c2")), FormulaParser.parse("A.cat in c2, c1"));
    assertEquals(new Comparison("A", "cat", "not in", List.of("c1", "c2")), FormulaParser.parse("A.cat not in (c1, c2)"));
    assertEquals(new Comparison("A", "x", "!=", 3), FormulaParser.parse("A.x <> 3"));
    assertEquals(new Comparison("A", "x", ">=", -1.5), FormulaParser.parse("a.x >= -1.5"));
  }

  @Test
  void precedenceAndAssociativity() {
    assertEquals(new Or(List.of(p, new And(List.of(q, r)))), FormulaParser.parse("p or q and r"));
    assertEquals(new And(List.of(new Not(p), q)), FormulaParser.parse("not p and q"));
    assertEquals(new Implies(p, new Implies(q, r)), FormulaParser.parse("p -> q -> r"));
    assertEquals(new Iff(p, new Or(List.of(q, r))), FormulaParser.parse("p <-> q or r"));
    assertEquals(new Nand(List.of(new Nand(List.of(p, q)), r)), FormulaParser.parse("p nand q nand r"));
    assertEquals(new Nand(List.of(p, q)), FormulaParser.parse("p nand (q)"));
    assertEquals(new Nand(List.of(p, q, r)), FormulaParser.parse("nand(p, q, r)"));
    assertEquals(new Xor(p, q), FormulaParser.parse("p xor q"));
    assertEquals(new Nor(List.of(p, q)), FormulaParser.parse("p NOR q"));
    assertEquals(new Or(List.of(new And(List.of(new Not(p), q)), new Not(r))), FormulaParser.parse("!p && q || ~r"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "A.x >", "(p and q", "p and", "A.x is", "p q", "A.x ? 3", "and"})
  void rejectsMalformedInput(String text) {
    assertThrows(FormulaParseException.class, () -> FormulaParser.parse(text));
  }

  //Section: conversion

  @Test
  void negationIsFoldedIntoAtoms() {
    assertEquals("A.x <= 10", NandNormalForm.convert("not A.x > 10"));
    assertEquals("(A.x < 1 and T.c is not c1)", NandNormalForm.convert("not (A.x >= 1 or T.c is c1)"));
    assertEquals("A.c not in (c1, c2)", NandNormalForm.convert("not A.c in (c1, c2)"));
  }

  @Test
  void disjunctionBecomesNandOfNegatedAtoms() {
    assertEquals("nand(A.x <= 10, T.y >= 5)", NandNormalForm.convert("A.x > 10 or T.y < 5"));
  }

  @Test
  void andsStayOutermost() {
    Formula expected = new And(List.of(
        new Nand(List.of(p.negate(), r.negate())),
        new Nand(List.of(q.negate(), r.negate()))));
    assertEquals(expected, NandNormalForm.normalize(FormulaParser.parse("(p and q) or r")));
  }

  @Test
  void constantsAndTrivialCases() {
    assertEquals("true", NandNormalForm.convert("p or not p"));
    assertEquals("false", NandNormalForm.convert("p and not p"));
    assertEquals("true", NandNormalForm.convert("A.x > 1 or A.x <= 1"));
    assertEquals("p", NandNormalForm.convert("p and (p or q)")); // subsumption
  }

  @ParameterizedTest
  @ValueSource(strings = {"nand(A.x <= 10, T.y >= 5)", "(A.x > 1 and nand(p, q))"})
  void alreadyNormalFormIsStable(String text) {
    String once = NandNormalForm.convert(text);
    assertEquals(once, NandNormalForm.convert(once));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "(A.x > 10 and T.y is c1) or not (A.z < 3 -> T.w in (a, b))",
      "nand(p, nor(q, r), p xor s) <-> not (q -> s)",
      "((p or q) and (r or s)) or ((not p and r) or (q and not s))",
      "not not not (A.x != 4 or (T.c is not c2 and p))",
  })
  void nestedFormulasAreEquivalent(String text) {
    Formula original = FormulaParser.parse(text);
    Formula result = NandNormalForm.normalize(original);
    assertNandShape(result);
    assertEquivalent(original, result);
    assertEquivalent(original, FormulaParser.parse(Formulas.render(result))); // rendering round-trips
  }

  private static Formula randomFormula(Random rng, List<Literal> atoms, int depth) {
    if (depth == 0 || rng.nextDouble() < 0.2) {
      if (rng.nextDouble() < 0.05) {
        return new Const(rng.nextBoolean());
      }
      Literal atom = atoms.get(rng.nextInt(atoms.size()));
      return rng.nextDouble() < 0.3 ? atom.negate() : atom;
    }
    switch (rng.nextInt(8)) {
      case 0: return new Not(randomFormula(rng, atoms, depth - 1));
      case 1: return new And(randomOperands(rng, atoms, depth));
      case 2: return new Or(randomOperands(rng, atoms, depth));
      case 3: return new Nand(randomOperands(rng, atoms, depth));
      case 4: return new Nor(randomOperands(rng, atoms, depth));
      case 5: return new Xor(randomFormula(rng, atoms, depth - 1), randomFormula(rng, atoms, depth - 1));
      case 6: return new Implies(randomFormula(rng, atoms, depth - 1), randomFormula(rng, atoms, depth - 1));
      default: return new Iff(randomFormula(rng, atoms, depth - 1), randomFormula(rng, atoms, depth - 1));
    }
  }

  private static List<Formula> randomOperands(Random rng, List<Literal> atoms, int depth) {
    List<Formula> operands = new ArrayList<>();
    for (int i = 0, n = 2 + rng.nextInt(2); i < n; i++) {
      operands.add(randomFormula(rng, atoms, depth - 1));
    }
    return operands;
  }

  @Test
  void randomFormulasAreEquivalent() {
    Random rng = new Random(42);
    List<Literal> atoms = List.of(p, q, new Comparison("A", "x", ">", 10), new Comparison("T", "c", "is", "c1"),
        new Comparison("A", "k", "in", List.of("a", "b")));
    for (int i = 0; i < 500; i++) {
      Formula original = randomFormula(rng, atoms, 4);
      Formula result = NandNormalForm.normalize(original);
      assertNandShape(result);
      assertEquivalent(original, result);
      assertEquivalent(original, FormulaParser.parse(Formulas.render(result)));
    }
  }
}
