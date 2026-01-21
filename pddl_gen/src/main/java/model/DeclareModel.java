package model;
import java.util.*;
//import org.w3c.dom.Attr;
import Automaton.Pair;

public class DeclareModel {
  
  public final HashMap<String, Activity> activities;
  private final ArrayList<DeclareConstraint> declareConstraints;
  private Map<Pair<Activity, CostEnum>, Integer> costs;
  public ArrayList<String> params;
  private Map<String, Attribute> attr;
  
  public DeclareModel(Map<String, ArrayList<String[]>> parsedLines) {
    this.activities = addActivities(parsedLines.get("activityLines")); // ok
    this.params = new ArrayList<>();
    Map<String, Attribute> attributes = bindAttributes(parsedLines.get("bindingLines")); 
    initializeAttributes(parsedLines.get("intAttributeLines"), parsedLines.get("floatAttributeLines"), parsedLines.get("enumAttributeLines"), attributes);
    this.declareConstraints = addConstraints(parsedLines.get("binaryConstraintLines"), parsedLines.get("unaryConstraintLines"));
    initializeNumericAttributeVariableValues(attributes);
    this.attr = attributes;
  }
  
  
  //Section: Evaluation of each Activity and their Attribute bindings
  private HashMap<String, Activity> addActivities(ArrayList<String[]> activityLines) {
    HashMap<String, Activity> newActivities = new HashMap<>();
    for (String[] line : activityLines) {
      newActivities.put(line[0], new Activity(line[0]));
    }
    return newActivities;
  }
  
  private Map<String, Attribute> bindAttributes(ArrayList<String[]> bindingLines) {
    Map<String, Attribute> overAllAttributes = new HashMap<>();
    for (String[] line : bindingLines) {
      for (String activityName : extractList(line[0])) {
        Activity involvedActivity = activities.get(activityName);
        if (involvedActivity != null) {
          bindAttributesToActivity(involvedActivity, extractList(line[1]), overAllAttributes);
        } else {
          System.out.println("Activity " + activityName + " not found");
        }
      }
    }
    return overAllAttributes;
  }
  
  private void bindAttributesToActivity(Activity activity, String[] attributeNames, Map<String, Attribute> attributes) {
    for (String name : attributeNames) {
      if (!this.params.contains(name)){
          this.params.add(name);
      }
      Attribute existentAttribute = attributes.get(name);
      if (existentAttribute != null) {
        activity.addAttribute(existentAttribute);

      } else {
        Attribute newAttribute = new Attribute(name);
        activity.addAttribute(newAttribute);
        attributes.put(name, newAttribute);
      }
    }
  }

  // SECTION - Costs
  /**
   * Assigns costs to the respective operations
   * @param costsList 
   */
  public void assignCosts(List<String[]> costsList) {
    Activity a;
    Integer[] costsArray = new Integer[4];

    this.costs = new HashMap<>();
    Set<Activity> seenActivities = new HashSet<>();

    for (String[] costs : costsList) {
      a = this.activities.get(costs[0]);
      if (a == null) {
        throw new Error("Activity not found! What I parsed: " + costs[0]);
      }
      seenActivities.add(a);
      for (int i = 1; i < costs.length; i++) {
        costsArray[i-1] = Integer.valueOf(costs[i]);
      }
      this.costs.put(new Pair<Activity, CostEnum>(a, CostEnum.CHANGE), costsArray[0]);
      this.costs.put(new Pair<Activity, CostEnum>(a, CostEnum.ADD), costsArray[1]);
      this.costs.put(new Pair<Activity, CostEnum>(a, CostEnum.SET), costsArray[2]);
      this.costs.put(new Pair<Activity, CostEnum>(a, CostEnum.DELETE), costsArray[3]);
    }

    // TODO Implement handling of missing activities
    // Set<Activity> undefinedActivities = new HashSet<>();
  }

  //Section: Initialization of each attribute and finally removing missing initializations
  /**
   * Assigns each of the parsed attriutes to their activities.
   * @param integers
   * @param floats
   * @param enums
   * @param attributes
   */
  private void initializeAttributes(ArrayList<String[]> integers, ArrayList<String[]> floats, ArrayList<String[]> enums, Map<String, Attribute> attributes) {
    initializeNumericAttributes("integer", integers, attributes);
    initializeNumericAttributes("float", floats, attributes);
    initializeEnumAttributes(enums, attributes);
    removeNotInitAttributes();
  }
  
  private void initializeNumericAttributes(String type, ArrayList<String[]> attributeLines, Map<String, Attribute> attributesMap) {
    for (String[] line : attributeLines) {
      for (String attributeName : extractList(line[0])) {
        Attribute attribute = attributesMap.get(attributeName);
        if (attribute != null && attribute.getType() == null) {
          attribute.setNumericAttribute(type, Double.parseDouble(line[1]), Double.parseDouble(line[2]));
        }
      }
    }
  }
  
  private void initializeEnumAttributes(ArrayList<String[]> attributeLines, Map<String, Attribute> attributesMap) {
    for (String[] line : attributeLines) {
      for (String attributeName : extractList(line[0])) {
        Attribute attribute = attributesMap.get(attributeName);
        if (attribute != null && attribute.getType() == null) {
          attribute.setEnumAttribute(extractList(line[1]));
        } else {
          System.out.println("Attribute " + attributeName + " not found");
        }
      }
    }
  }
  
  /**
   * @param subString list written all as string
   * @return Array of parsed list
   */
  private String[] extractList(String subString) {
    return subString.replaceAll(" ", "").split(",");
  }
  
  /**
   * Cleanup
   */
  private void removeNotInitAttributes() {
    for (Map.Entry<String, Activity> activity : activities.entrySet()) {
      activity.getValue().getAttributes().removeIf(attribute -> attribute.getType() == null);
    }
  }
  
  
  //Section: Evaluation of each Constraint
  private ArrayList<DeclareConstraint> addConstraints(ArrayList<String[]> binaryConstraints, ArrayList<String[]> unaryConstraints) {
    ArrayList<DeclareConstraint> newConstraints = new ArrayList<>();
    addUnaryConstraints(newConstraints, unaryConstraints);
    addBinaryConstraints(newConstraints, binaryConstraints);
    return newConstraints;
  }
  
  private void addUnaryConstraints(ArrayList<DeclareConstraint> constraints, ArrayList<String[]> unaryConstraints) {
    for (String[] line : unaryConstraints) {
      DeclareConstraint constraint = constructUnaryConstraint(line);
      if (constraint != null && constraint.assignConditionsToAttributes(activities)) {
        constraints.add(constraint);
      }
    }
  }
  
  private void addBinaryConstraints(ArrayList<DeclareConstraint> constraints, ArrayList<String[]> binaryConstraints) {
    for (String[] line : binaryConstraints) {
      DeclareConstraint constraint = constructBinaryConstraint(line);
      if (constraint != null && constraint.assignConditionsToAttributes(activities)) {
        constraints.add(constraint);
      }
    }
  }
  
  private DeclareConstraint constructUnaryConstraint(String[] constraintTokens) {
    DeclareTemplate template = DeclareTemplate.getByTemplateName(constraintTokens[0]);
    String activity = constraintTokens[1];
    if (template != null && activities.get(activity) != null) {
      String activationCondition = constraintTokens[2] == null? null : constraintTokens[2];
      return new DeclareConstraint(template, activity, activationCondition, null,null);
    }
    return null;
  }
  
  private DeclareConstraint constructBinaryConstraint(String[] constraintTokens) {
    DeclareTemplate template = DeclareTemplate.getByTemplateName(constraintTokens[0]);
    String activationActivity, targetActivity;
    
    if (template != null) {
      if (template.getReverseActivationTarget()) {
        targetActivity = constraintTokens[1];
        activationActivity = constraintTokens[2];
      } else {
        activationActivity = constraintTokens[1];
        targetActivity = constraintTokens[2];
      }
      
      if (activities.get(targetActivity) != null && activities.get(activationActivity) != null) {
        String activationCondition = constraintTokens[3].isBlank()? null : constraintTokens[3];

        String targetCondition = constraintTokens[4].isBlank()? null : constraintTokens[4];
        /*
        if (constraintTokens[4] == "") {
          targetCondition = null;
        }
        */
        return new DeclareConstraint(template, activationActivity, activationCondition, targetActivity, targetCondition);
      }
    }
    return null;
  }
  
  
  //Section: Utils
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("This model contains " + activities.size() + " activities:\n\n");
    for (Map.Entry<String, Activity> activity : activities.entrySet()) {
      builder.append(activity).append("\n");
    }
    builder.append("This model contains following constraints:\n");
    for (DeclareConstraint constraint : declareConstraints) {
      builder.append(constraint).append("\n");
    }
    return builder.toString();
  }
  
  public HashMap<String, Activity> getActivities() {
    return activities;
  }
  
  public ArrayList<DeclareConstraint> getDeclareConstraints()  {
    return declareConstraints;
  }

  // Costs
  public Map<Pair<Activity, CostEnum>, Integer> getCosts() {
    return this.costs;
  }

  private void initializeNumericAttributeVariableValues(Map<String, Attribute> attributesMap) {
    /* 
    FIRST ADD THE VALUE RANGE DEFINED IN THE DECLARE FILE
    INTEGER / FLOAT ONLY
     */
    for (Attribute a : attributesMap.values()) {
      if (a.getType().equals("enum")) {
        continue;
      }
      a.getCriticalValues().add(a.getMinValue());
      a.getCriticalValues().add(a.getMaxValue());
    }

    /*
    THEN ADD ALL VALUES MENTIONED IN A CONSTRAINT
    E.G. x < 10 or x > 20
    */
    // first find all possible values mentioned in the declare model
    // assuming the min and max value is already given
    for (DeclareConstraint dc : this.declareConstraints) {
      List<Condition> conditionsList = dc.getActivationConditions();
      // If the constraint has an AND relation there can be multiple parameters and values
      for (Condition cond : conditionsList) {
          String localAttrib = cond.parameterName;
          Attribute globalAttribute = attributesMap.get(localAttrib);
          String localType = globalAttribute.getType();
          if (localType.equals("enum")) {
            continue;
          }
          globalAttribute.getCriticalValues().add((double) cond.value);

      }

    }

    /*
    At this point, all numeric variables have been processed for the first time.
    The next stage is to add the median values between the critical points to the set of critical points,
    which is relevant if there are any inequality constraints or lt/gt
    */

    for (Attribute a : attributesMap.values()) {
      if (a.getType().equals("enum")) {
        continue;
      }
      
      TreeSet<Double> initCritVal = a.getCriticalValues();

      List<Double> hp = new ArrayList<>();

      Iterator<Double> it = initCritVal.iterator();

      if (it.hasNext()) {
        Double prev = it.next();
        while (it.hasNext()) {
          Double curr = it.next();
          hp.add((prev+curr)/2.0);
          prev = curr;
        }
      }

      if (a.getType().equals("integer")) {
        hp = hp.stream()
          .map(d -> Math.floor(d))
          .toList(); 
      }
      a.getCriticalValues().addAll(hp);
      a.setVariableValueMap();
    
    }

  }  

  public String generateVariableSubstitutions(){
    StringBuilder sb = new StringBuilder();

    for (Activity act : this.activities.values()) {
      // Default case if the activity has no attributes
      if (act.getAttributes().isEmpty()) {
        continue;
      }

      for (Attribute attr : act.getAttributes()) {
        Set<String> attributeValues = attr.variableValueMap.keySet();

        for (String attrValName : attributeValues) {
          sb.append(attrValName + " "+ act.getName() + " " + attr.getName()+"\n");
        }
      }

      sb.append("\n");

    }

    return sb.toString();
  }

  public String generateVariableValues() {
    StringBuilder sb = new StringBuilder();

    for (Attribute att : this.attr.values()) {
        for (String sAtt : att.variableValueMap.keySet()) {
          Integer sAttNumVal = (int) Math.round(att.variableValueMap.get(sAtt));
          //sb.append(sAtt + " " + att.variableValueMap.get(sAtt).toString() + "\n");
          sb.append(sAtt + " " + sAttNumVal.toString() + "\n");
        }
    }

    return sb.toString();

  }
  
}