package model;

import java.util.*;

public class DeclareConstraint {
  
  private final DeclareTemplate template;
  private final String activationActivity, targetActivity;
  private String activationConditionString, targetConditionString;
  private ArrayList<Condition> activationConditionsList, targetConditionsList;
  private double activation_min_t_condidition, activation_max_t_condition, target_min_t_condidition, target_max_t_condition;
  private ArrayList<String> clockConditions;
  private int duplicateIndex = 0;
  private String sourceFile;
  private boolean isHard;

  public DeclareConstraint(DeclareTemplate template, String activationActivity, String activationCondition, String targetActivity, String targetCondition) {
    this.template = template;
    this.activationActivity = activationActivity;
    this.activationConditionString = activationCondition;
    this.targetActivity = targetActivity;
    this.targetConditionString = targetCondition;

    this.activationConditionsList = this.parseConditionList(activationActivity, activationCondition);
    this.targetConditionsList = this.parseConditionList(targetActivity, targetCondition);
    this.clockConditions = new ArrayList<String>();
  }

  private ArrayList<Condition> parseConditionList(String activityName, String conditionsString) {
    if (activityName == null || conditionsString == null) return null; // For unary constraints

    ArrayList<Condition> conditions = new ArrayList<>();

    if (conditionsString.contains(" or ")) { // leave spaces _or_!
      throw new Error("Conditions with OR currently unsupported!");
    }

    String[] strings = conditionsString.split("and");
    System.out.println(conditionsString);
    
    if (!conditionsString.contains("A.") && !conditionsString.contains("T.") ){
      return conditions;
    }
    

    for (String str : strings) {
      str = str.strip();
      String[] stuff = str.split(" ");

      stuff[0] = stuff[0].replace("A.", "");
      stuff[0] = stuff[0].replace("T.", "");

      OperatorType o = switch(stuff[1]) {
        case ">" -> OperatorType.BIGGER;
        case ">=" -> OperatorType.BIGGER_OR_EQUAL;
        case "is" -> OperatorType.EQUAL;
        case "=" -> OperatorType.EQUAL;
        case "<>" -> OperatorType.NOT_EQUAL; // TODO Check
        case "<" -> OperatorType.LESS;
        case "<=" -> OperatorType.LESS_OR_EQUAL;
        default -> null;
      };

      // Enum literals (e.g. "c2") are encoded later by DeclareModel, once the attribute's value list is known.
      String digits = stuff[2].replaceAll("[a-zA-Z]", "");

      Condition c = new Condition(activityName, stuff[0], o, digits.isEmpty() ? null : Integer.valueOf(digits));
      c.rawValue = stuff[2];
      conditions.add(c);
    }

    return conditions;
  }
  
  
  //Section: Setting up the constraint and the condition lists
  public boolean assignConditionsToAttributes(Map<String, Activity> activities) {
    Activity activator = activities.get(activationActivity);
    Activity target = activities.get(targetActivity);
    return assignToAttribute(activator, activationConditionString, activationConditionsList, "activation") &&
    assignToAttribute(target, targetConditionString, targetConditionsList, "target");
  }
  
  private void addConditionToList(String condition, ArrayList<String> list, String flag) {
    if (condition.contains("!=")) {
      String combinedCondition = addNotEqualCondition(condition, list);
      updateConditionString(condition, combinedCondition, flag);
    } else {
      list.add(condition);
    }
  }
  
  
  //Section: Evaluation and addition of not equal condition
  private String addNotEqualCondition(String condition, ArrayList<String> list) {
    String conditionLeft = condition.replace("!=", "<");
    String conditionRight = condition.replace("!=", ">");
    list.add(conditionLeft);
    list.add(conditionRight);
    return "(" + conditionLeft + " or " + conditionRight + ")";
  }
  
  private void updateConditionString(String oldString, String newString, String flag) {
    if (flag.equals("activation")) {
      activationConditionString = activationConditionString.toLowerCase().replace(oldString, newString);
    } else {
      targetConditionString = targetConditionString.replace(oldString, newString);
    }
  }
  
  
  //Section: Validation of each condition
  private boolean assignToAttribute(Activity activity, String conditionString, ArrayList<Condition> conditionList, String flag) {
    ArrayList<String> subConditionsStringsList = createConditionsStringsList(conditionString, flag);
    for (String subConditionString : subConditionsStringsList) {
      if (!subConditionString.isBlank() && !validateAttribute(activity, subConditionString, conditionList, flag)) {
        return false;
      }
    }
    return true;
  }
  
  private ArrayList<String> createConditionsStringsList(String conditionString, String flag) {
    ArrayList<String> stringsList = new ArrayList<>();
    if (conditionString != null) {
      String[] conditionStrings = conditionString.replaceAll("[)(]", "").toLowerCase().split(" and | or ");
      for (String subCondition : conditionStrings) {
        addConditionToList(subCondition, stringsList, flag);
      }
    }
    return stringsList;
  }
  
  private boolean validateAttribute(Activity activity, String subConditionString, ArrayList<Condition> conditionList, String flag) {
    Attribute attribute = activity.getAttribute(subConditionString.split("[. ]")[1]);
    if (attribute != null && conditionMatchesType(attribute.getType(), subConditionString)) {
      // validateCondition(attribute, subConditionString, conditionList, flag);
      return true;
    } else {
      return false;
    }
  }
  
  private boolean conditionMatchesType(String type, String conditionString) {
    return ((type.equals("integer") || type.equals("float")) && Arrays.stream(new String[]{"=","<",">"}).anyMatch(conditionString::contains)) ||
    (type.equals("enum") && Arrays.stream(new String[]{" is "," in "}).anyMatch(conditionString::contains));
  }

  public String getActivation() {
    return this.activationActivity;
  }
  public String getTarget() {
    return this.targetActivity;
  }
  public List<Condition> getActivationConditions() {
    return this.activationConditionsList;
  }
  public List<Condition> getTargetConditions() {
    return this.targetConditionsList;
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

  public void setHard(boolean isHardConstraint) {
    this.isHard = isHardConstraint;
  }

  public boolean getHard() {
    return this.isHard;
  }
}