package model;

import java.util.*;

import formula.Formula;
import formula.Formula.Comparison;
import formula.Formula.Literal;
import formula.Formulas;

public class DeclareConstraint {
  
  private final DeclareTemplate template;
  private final String activationActivity, targetActivity;
  private String activationConditionString, targetConditionString;
  // The data conditions as formulas (null = no condition). See Guard for what they can contain.
  // We keep the parsed ones around because the enum encoding can change (variable_values.txt)
  private Formula parsedActivationGuard, parsedTargetGuard, activationGuard, targetGuard;
  private double activation_min_t_condidition, activation_max_t_condition, target_min_t_condidition, target_max_t_condition;
  private ArrayList<String> clockConditions;
  private int duplicateIndex = 0;

  public DeclareConstraint(DeclareTemplate template, String activationActivity, String activationCondition, String targetActivity, String targetCondition) {
    this.template = template;
    this.activationActivity = activationActivity;
    this.activationConditionString = activationCondition;
    this.targetActivity = targetActivity;
    this.targetConditionString = targetCondition;

    // Conditions can now be any boolean formula, not only a list of "and"s
    this.parsedActivationGuard = activationActivity == null ? null : Guard.parse(activationCondition);
    this.parsedTargetGuard = targetActivity == null ? null : Guard.parse(targetCondition);
    this.activationGuard = parsedActivationGuard;
    this.targetGuard = parsedTargetGuard;
    this.clockConditions = new ArrayList<String>();
  }
  
  
  //Section: Checking the conditions against the activities
  // Every attribute in a condition has to be bound to the activity, and the value has to make sense
  // for the attribute type (numbers for integer/float, a declared value for enums)
  public boolean assignConditionsToAttributes(Map<String, Activity> activities) {
    return guardFitsActivity(parsedActivationGuard, activities.get(activationActivity), activationConditionString) &&
    guardFitsActivity(parsedTargetGuard, activities.get(targetActivity), targetConditionString);
  }

  private boolean guardFitsActivity(Formula guard, Activity activity, String conditionString) {
    if (guard == null) {
      return true;
    }
    for (Literal literal : Formulas.literals(guard)) {
      if (!(literal instanceof Comparison comparison)) {
        System.out.println("Condition \"" + conditionString + "\" uses " + literal + ", only A.<attribute> / T.<attribute> conditions are allowed");
        return false;
      }
      Attribute attribute = activity == null ? null : activity.getAttribute(comparison.attribute().toLowerCase());
      if (attribute == null) {
        System.out.println("Condition \"" + conditionString + "\": attribute " + comparison.attribute() + " is not bound to " + (activity == null ? "this activity" : activity.getName()));
        return false;
      }
      boolean numericAttribute = attribute.getType().equals("integer") || attribute.getType().equals("float");
      boolean numericValue = comparison.value() instanceof Number;
      boolean equality = comparison.operator().equals("=") || comparison.operator().equals("!=");
      if (numericAttribute != numericValue || (!numericAttribute && !equality)) {
        System.out.println("Condition \"" + conditionString + "\": " + comparison + " doesn't fit the type of " + comparison.attribute() + " (" + attribute.getType() + ")");
        return false;
      }
    }
    return true;
  }

  // Replaces the enum values with their numbers, has to run once the attribute values are known
  public void encodeGuards(Map<String, Attribute> attributes) {
    this.activationGuard = Guard.encodeValues(this.parsedActivationGuard, attributes, getConstraintName());
    this.targetGuard = Guard.encodeValues(this.parsedTargetGuard, attributes, getConstraintName());
  }

  public String getActivation() {
    return this.activationActivity;
  }
  public String getTarget() {
    return this.targetActivity;
  }
  public Formula getActivationGuard() {
    return this.activationGuard;
  }
  public Formula getTargetGuard() {
    return this.targetGuard;
  }
  
  
  @Override
  public String toString() {
    return "[template=" + template + ", activationActivity=" + activationActivity + ", activationCondition=" + activationConditionString
    + ", targetActivity=" + targetActivity + ", targetCondition=" + targetConditionString + "]";
  }

  public String getConstraintName() {
    String baseName = (template.getTemplateName() + "_" + activationActivity + (targetActivity != null ? ("_" + targetActivity) : "")).replaceAll("\s", "_");
    return duplicateIndex > 0 ? baseName + "_" + duplicateIndex : baseName;
  }

  // Assigns the ordinal (2nd, 3rd, ...) of this constraint among others sharing the same
  // template/activation/target, so repeated constraints get distinct PDDL object names.
  public void setDuplicateIndex(int duplicateIndex) {
    this.duplicateIndex = duplicateIndex;
  }

  public DeclareTemplate getTemplate() {
    return template;
  }

  // Parses the trailing per-activity time-window suffix from the .decl file,
  // e.g. "ActivityP,0,100,h/ActivityG,2,5,h", and routes each window to
  // whichever of activation/target it names (order in the file isn't fixed).
  public void assignTimeConditions(String timeConditionsString) {
    if (timeConditionsString == null || timeConditionsString.isBlank()) {
      return;
    }

    for (String segment : timeConditionsString.split("/")) {
      String[] parts = segment.split(",");
      if (parts.length < 3) {
        continue;
      }

      String activityName = parts[0];
      double min = Double.parseDouble(parts[1]);
      double max = Double.parseDouble(parts[2]);

      if (activityName.equals(this.activationActivity)) {
        this.setActivationTimeConditions(min, max);
      }
      if (activityName.equals(this.targetActivity)) {
        this.setTargetTimeConditions(min, max);
      }
    }
  }

  public void setActivationTimeConditions(double min, double max) {
    this.activation_min_t_condidition = min;
    this.activation_max_t_condition = max;
  }

  public void setTargetTimeConditions(double min, double max) {
    this.target_min_t_condidition = min;
    this.target_max_t_condition = max;
  }

  public double[] getActivationTimeConditions() {
    if (this.activation_min_t_condidition == 0 && this.activation_max_t_condition == 0) {
      //return null;
      return new double[] {0, 100};
    }
    return new double[] {this.activation_min_t_condidition, this.activation_max_t_condition};
  }

  public double[] getTargetTimeConditions() {
    if (this.target_min_t_condidition == 0 && this.target_max_t_condition == 0) {
      //return null;
      return new double[] {0, 100};
    }
    return new double[] {this.target_min_t_condidition, this.target_max_t_condition};
  }

  public void addClockCondition(String cCond1, String cCond2) {
    this.clockConditions.add(formatClockCond(cCond1, cCond2));
  }

  public ArrayList<String> getClockConditions() {
    return this.clockConditions;
  }

  private String formatClockCond(String s1, String s2) {
    return "    (clock " + s1 + " " + s2 + ")\n";
  }
}