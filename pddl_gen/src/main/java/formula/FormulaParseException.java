package formula;

/** Thrown when a condition can't be parsed. The message says what we expected and where. */
public class FormulaParseException extends IllegalArgumentException {

  public FormulaParseException(String message) {
    super(message);
  }
}
