package model;

public class Condition {
  
  public String activity;
  public String parameterName;
  public OperatorType operator;
  public Integer value;
  public String rawValue; // Value as written in the model, e.g. "c2" for enums

  public Condition(String activityName, String parameterName, OperatorType operator, Integer value) {
    this.activity = activityName;
    this.parameterName = parameterName;
    this.operator = operator;
    this.value = value;
  }

  public OperatorType getNegatedCondition() {
    if (this.operator == null) return null;
    
    switch (this.operator) {
      case BIGGER:
        return OperatorType.LESS_OR_EQUAL;
      case BIGGER_OR_EQUAL:
        return OperatorType.LESS;
      case EQUAL:
        return OperatorType.NOT_EQUAL;
      case NOT_EQUAL:
        return OperatorType.EQUAL;
      case LESS:
        return OperatorType.BIGGER_OR_EQUAL;
      case LESS_OR_EQUAL:
        return OperatorType.BIGGER;
      default:
          throw new IllegalArgumentException("Unknown operator: " + operator);
    }
  }
}