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
  // Set by the STP preprocessing when the other windows already imply this one
  private boolean targetWindowDropped = false;
  private String sourceFile;
  private boolean isHard;

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

  // Replaces the (encoded) guards with equivalent ones, see values.ValueIntervalPreprocessor
  public void setGuards(Formula activationGuard, Formula targetGuard) {
    this.activationGuard = activationGuard;
    this.targetGuard = targetGuard;
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
    String baseName = this.getBaseConstraintName();
    return duplicateIndex > 0 ? baseName + "_" + duplicateIndex : baseName;
  }

  // Name without the duplicate suffix, e.g. "Response_ActivityG_ActivityP".
  public String getBaseConstraintName() {
    return (template.getTemplateName() + "_" + activationActivity + (targetActivity != null ? ("_" + targetActivity) : "")).replaceAll("\s", "_");
  }

  // Assigns the ordinal (2nd, 3rd, ...) of this constraint among others sharing the same
  // template/activation/target, so repeated constraints get distinct PDDL object names.
  public void setDuplicateIndex(int duplicateIndex) {
    this.duplicateIndex = duplicateIndex;
  }

  // File name (without directories) of the DECLARE model the constraint was read from.
  public String getSourceFile() {
    return sourceFile;
  }

  public void setSourceFile(String sourceFile) {
    this.sourceFile = sourceFile;
  }

  public DeclareTemplate getTemplate() {
    return template;
  }

  // Parses the trailing per-activity time-window suffix from the .decl file,
  // It should handle both the named format, e.g. ActivityP,0,100,h/ActivityG,2,5,h
  // Or the simple format 2,5,h
  // Activity name is an artifact and is not considered in this part at all.
  public void assignTimeConditions(String timeConditionsString) {
    if (timeConditionsString == null || timeConditionsString.isBlank()) {
      return;
    }

    int numberOfConditions = timeConditionsString.split("/").length;

    if (numberOfConditions != 2 && numberOfConditions < 2 & numberOfConditions>0) {
      // At this point I am assuming that the simple format is used.
      // Activation condition will get max values
      // Target will get entered numbers.

      //this.setActivationTimeConditions(0,100);
      String[] parts = timeConditionsString.split(",");



      if(parts.length == 3) { // length 3 should be 2,5,h
        double min = Double.parseDouble(parts[0]);
        double max = Double.parseDouble(parts[1]);

        this.setTargetTimeConditions(min, max);
      }

      if(parts.length == 4) { // length 4 should be ActivityG,2,5,h
        double min = Double.parseDouble(parts[1]);
        double max = Double.parseDouble(parts[2]);

        this.setTargetTimeConditions(min, max);
      }
      

    }

    String[] segments = timeConditionsString.split("/");

    for (int i = 0; i <numberOfConditions; i++) {
      String[] parts = segments[i].split(",");
      if (parts.length < 3) {
        continue;
      }
      //String activityName = parts[0];
      double min = Double.parseDouble(parts[1]);
      double max = Double.parseDouble(parts[2]);

      if (i == 0) {
        this.setActivationTimeConditions(min, max);
      }

      if (i == 1) {
        this.setTargetTimeConditions(min, max);
      }

    }
  }

  public void setActivationTimeConditions(double min, double max) {
    this.activation_min_t_condidition = min;
    this.activation_max_t_condition = max;
  }

  // True if the .decl file gives a target window (otherwise the getter falls back to [0, 100])
  public boolean hasExplicitTargetWindow() {
    return !this.targetWindowDropped && !(this.target_min_t_condidition == 0 && this.target_max_t_condition == 0);
  }

  public void dropTargetTimeConditions() {
    this.targetWindowDropped = true;
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
    if (this.targetWindowDropped) {
      return null; // no window at all, so the automaton doesn't need a clock for it
    }
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

  public void setHard(boolean isHardConstraint) {
    this.isHard = isHardConstraint;
  }

  public boolean getHard() {
    return this.isHard;
  }
}