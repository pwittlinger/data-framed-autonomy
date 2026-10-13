package formula;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import formula.Formula.And;
import formula.Formula.Comparison;
import formula.Formula.Const;
import formula.Formula.Iff;
import formula.Formula.Implies;
import formula.Formula.Nand;
import formula.Formula.Nor;
import formula.Formula.Not;
import formula.Formula.Or;
import formula.Formula.Var;
import formula.Formula.Xor;

/**
 * Parser for the data conditions we write in the .decl files, but with any boolean formula allowed
 * (not just "and"). It's a plain recursive descent parser: one method per precedence level.
 *
 * Grammar (keywords are case-insensitive), from the loosest to the tightest binding:
 * <pre>
 *   iff      := implies (("iff" | "&lt;-&gt;") implies)*
 *   implies  := disj (("implies" | "-&gt;") implies)?          right-associative
 *   disj     := xor (("or" | "|" | "||" | "nor") xor)*
 *   xor      := conj ("xor" conj)*
 *   conj     := unary (("and" | "&amp;" | "&amp;&amp;" | "nand") unary)*
 *   unary    := ("not" | "!" | "~") unary | primary
 *   primary  := "(" iff ")" | ("nand" | "nor") "(" iff ("," iff)* ")" | "true" | "false" | atom
 *   atom     := ATTR cmp value | ATTR "is" ["not"] value | ATTR ["not"] "in" list | IDENT
 *   ATTR     := ("A" | "T") "." IDENT
 *   cmp      := "&gt;" | "&gt;=" | "&lt;" | "&lt;=" | "=" | "==" | "!=" | "&lt;&gt;"
 *   list     := "(" value ("," value)* ")" | value ("," value)*
 * </pre>
 * Two things to keep in mind:
 * - infix nand/nor only take two operands and group to the left ({@code a nand b nand c} is
 *   {@code (a nand b) nand c}), because nand isn't associative. If you want more operands use
 *   {@code nand(a, b, c)}.
 * - a list after {@code in} without parentheses stops at the first thing that isn't a value, so inside
 *   {@code nand(...)} better write {@code A.cat in (c1, c2)}.
 */
public class FormulaParser {

  private static final Pattern TOKEN_PATTERN = Pattern.compile(
      "(?<ws>\\s+)"
      + "|(?<attr>[AaTt]\\.[A-Za-z_][A-Za-z0-9_]*)"
      + "|(?<number>-?\\d+(?:\\.\\d+)?)"
      + "|(?<op><->|->|<=|>=|!=|<>|==|&&|\\|\\||[=<>&|!~(),])"
      + "|(?<ident>[A-Za-z_][A-Za-z0-9_]*)");

  private static final Set<String> KEYWORDS = Set.of(
      "and", "or", "not", "xor", "nand", "nor", "implies", "iff", "is", "in", "true", "false");
  private static final Set<String> COMPARISON_OPERATORS = Set.of(">", ">=", "<", "<=", "=", "==", "!=", "<>");
  private static final Map<String, String> OPERATOR_ALIASES = Map.of("==", "=", "<>", "!=");

  private record Token(String kind, String text, int position) {
    /** Lowercase text for words, so "AND" and "and" are the same keyword. Null for everything else. */
    String word() {
      return kind.equals("ident") ? text.toLowerCase(Locale.ROOT) : null;
    }
  }

  private final String text;
  private final List<Token> tokens;
  private int index = 0;

  private FormulaParser(String text) {
    this.text = text;
    this.tokens = tokenize(text);
  }

  public static Formula parse(String text) {
    return new FormulaParser(text).parseAll();
  }

  private static List<Token> tokenize(String text) {
    List<Token> tokens = new ArrayList<>();
    Matcher matcher = TOKEN_PATTERN.matcher(text);
    int position = 0;
    while (position < text.length()) {
      matcher.region(position, text.length());
      if (!matcher.lookingAt()) {
        throw new FormulaParseException("Unexpected character '" + text.charAt(position) + "' at position " + position);
      }
      for (String kind : List.of("attr", "number", "op", "ident")) {
        if (matcher.group(kind) != null) {
          tokens.add(new Token(kind, matcher.group(), position));
        }
      }
      position = matcher.end();
    }
    tokens.add(new Token("end", "", text.length()));
    return tokens;
  }

  //Section: token helpers
  private Token peek(int offset) {
    return tokens.get(Math.min(index + offset, tokens.size() - 1));
  }

  private Token peek() {
    return peek(0);
  }

  private Token advance() {
    Token token = peek();
    index++;
    return token;
  }

  private boolean atKeyword(int offset, String... words) {
    String word = peek(offset).word();
    return word != null && List.of(words).contains(word);
  }

  private boolean atKeyword(String... words) {
    return atKeyword(0, words);
  }

  private boolean atOp(int offset, String... ops) {
    Token token = peek(offset);
    return token.kind().equals("op") && List.of(ops).contains(token.text());
  }

  private boolean atOp(String... ops) {
    return atOp(0, ops);
  }

  private void expectOp(String op) {
    if (!atOp(op)) {
      throw error("expected '" + op + "'");
    }
    advance();
  }

  private FormulaParseException error(String message) {
    Token token = peek();
    String found = token.text().isEmpty() ? "end of input" : token.text();
    return new FormulaParseException(message + ", found '" + found + "' at position " + token.position() + " in '" + text + "'");
  }

  //Section: grammar
  private Formula parseAll() {
    Formula formula = parseIff();
    if (!peek().kind().equals("end")) {
      throw error("unexpected trailing input");
    }
    return formula;
  }

  private Formula parseIff() {
    Formula left = parseImplies();
    while (atKeyword("iff") || atOp("<->")) {
      advance();
      left = new Iff(left, parseImplies());
    }
    return left;
  }

  private Formula parseImplies() {
    Formula left = parseDisj();
    if (atKeyword("implies") || atOp("->")) {
      advance();
      return new Implies(left, parseImplies());
    }
    return left;
  }

  private Formula parseDisj() {
    Formula left = parseXor();
    while (atKeyword("or", "nor") || atOp("|", "||")) {
      boolean isNor = atKeyword("nor");
      advance();
      Formula right = parseXor();
      left = isNor ? new Nor(List.of(left, right)) : flatten(false, left, right);
    }
    return left;
  }

  private Formula parseXor() {
    Formula left = parseConj();
    while (atKeyword("xor")) {
      advance();
      left = new Xor(left, parseConj());
    }
    return left;
  }

  private Formula parseConj() {
    Formula left = parseUnary();
    // If we already have a left operand, "nand" has to be the infix one, even when a "(" comes after it
    while (atKeyword("and", "nand") || atOp("&", "&&")) {
      boolean isNand = atKeyword("nand");
      advance();
      Formula right = parseUnary();
      left = isNand ? new Nand(List.of(left, right)) : flatten(true, left, right);
    }
    return left;
  }

  private Formula parseUnary() {
    if (atKeyword("not") || atOp("!", "~")) {
      advance();
      return new Not(parseUnary());
    }
    return parsePrimary();
  }

  private Formula parsePrimary() {
    Token token = peek();
    if (atOp("(")) {
      advance();
      Formula formula = parseIff();
      expectOp(")");
      return formula;
    }
    if (atKeyword("nand", "nor") && atOp(1, "(")) {
      boolean isNand = token.word().equals("nand");
      advance();
      advance();
      List<Formula> operands = new ArrayList<>();
      operands.add(parseIff());
      while (atOp(",")) {
        advance();
        operands.add(parseIff());
      }
      expectOp(")");
      return isNand ? new Nand(operands) : new Nor(operands);
    }
    if (atKeyword("true", "false")) {
      advance();
      return new Const(token.word().equals("true"));
    }
    if (token.kind().equals("attr")) {
      return parseComparison();
    }
    if (token.kind().equals("ident") && !KEYWORDS.contains(token.word())) {
      advance();
      return new Var(token.text());
    }
    throw error("expected a condition, a variable or '('");
  }

  private Comparison parseComparison() {
    String[] parts = advance().text().split("\\.", 2);
    String scope = parts[0].toUpperCase(Locale.ROOT);
    String attribute = parts[1];

    if (peek().kind().equals("op") && COMPARISON_OPERATORS.contains(peek().text())) {
      String operator = OPERATOR_ALIASES.getOrDefault(peek().text(), peek().text());
      advance();
      return new Comparison(scope, attribute, operator, parseValue());
    }
    if (atKeyword("is")) {
      advance();
      String operator = "is";
      if (atKeyword("not")) {
        advance();
        operator = "is not";
      }
      return new Comparison(scope, attribute, operator, parseValue());
    }
    if (atKeyword("in") || (atKeyword("not") && atKeyword(1, "in"))) {
      String operator = "in";
      if (atKeyword("not")) {
        advance();
        operator = "not in";
      }
      advance();
      return new Comparison(scope, attribute, operator, parseList());
    }
    throw error("expected a comparison after " + scope + "." + attribute);
  }

  private Object parseValue() {
    Token token = peek();
    if (token.kind().equals("number")) {
      advance();
      return token.text().contains(".") ? (Object) Double.valueOf(token.text()) : (Object) Integer.valueOf(token.text());
    }
    if (token.kind().equals("ident") && !KEYWORDS.contains(token.word())) {
      advance();
      return token.text();
    }
    throw error("expected a value");
  }

  private boolean atListValue(int offset) {
    Token token = peek(offset);
    return token.kind().equals("number") || (token.kind().equals("ident") && !KEYWORDS.contains(token.word()));
  }

  private List<Object> parseList() {
    // Sorted and without duplicates, so "in c2, c1" and "in c1, c2" end up being the same atom
    TreeSet<Object> values = new TreeSet<>(Comparator.comparing(String::valueOf));
    if (atOp("(")) {
      advance();
      values.add(parseValue());
      while (atOp(",")) {
        advance();
        values.add(parseValue());
      }
      expectOp(")");
    } else {
      values.add(parseValue());
      while (atOp(",") && atListValue(1)) {
        advance();
        values.add(parseValue());
      }
    }
    return List.copyOf(values);
  }

  private static Formula flatten(boolean conjunction, Formula left, Formula right) {
    List<Formula> operands = new ArrayList<>();
    for (Formula operand : List.of(left, right)) {
      if (conjunction && operand instanceof And and) {
        operands.addAll(and.operands());
      } else if (!conjunction && operand instanceof Or or) {
        operands.addAll(or.operands());
      } else {
        operands.add(operand);
      }
    }
    return conjunction ? new And(operands) : new Or(operands);
  }
}
