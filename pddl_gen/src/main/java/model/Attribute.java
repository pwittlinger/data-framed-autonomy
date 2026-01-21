package model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeSet;

public class Attribute {
  
  private final String name;
  private String type;
  private double minValue, maxValue;
  private String[] values;
  private TreeSet<Double> criticalValues;
  public HashMap<String, Double> variableValueMap;
  public HashMap<Integer, String> enumMapInverse;
  
  public Attribute(String name) {
    this.name = name;
  }
  
  
  //Section: Evaluation and set up the attribute
  public void setEnumAttribute(String[] values) {
    this.type = "enum";
    this.values = values;
    this.variableValueMap = new HashMap<String, Double>();
    this.enumMapInverse = new HashMap<Integer, String>();
    this.criticalValues = new TreeSet<Double>();

    for (int i = 0; i < values.length; i++) {
      this.variableValueMap.put(values[i], (double) i);
      this.enumMapInverse.put(i, values[i]);
      this.criticalValues.add((double) i);
    }
 

  }
  
  public void setNumericAttribute(String type, double min, double max) {
    this.type = type;
    this.minValue = min;
    this.maxValue = max;
    this.criticalValues = new TreeSet<Double>();
    this.variableValueMap = new HashMap<String, Double>();
  }
  
  @Override
  public boolean equals(Object o) {
    return o instanceof Attribute attribute && this.name.equals(attribute.getName());
  }
  
  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder(name + " of type " + type + " with ");
    if (type.equals("enum")) {
      stringBuilder.append("values: ").append(Arrays.toString(values)).append("\n");
    } else {
      stringBuilder.append("bounds: [").append(minValue).append(", ").append(maxValue).append("]\n");
    }
    return stringBuilder.append("\n").toString();
  }
  
  public String getName() {
    return name;
  }
  
  public String getType() {
    return type;
  }

  public TreeSet<Double> getCriticalValues() {
    return this.criticalValues;
  }

  public void setCriticalValues(TreeSet<Double> ts) {
    this.criticalValues = ts;
  }

  /**
  * 
  */
  public void setVariableValueMap() {
    if (!(this.variableValueMap.isEmpty())) {
      return;
    }
    for (Double cv : this.criticalValues) {
      String varName = this.name + cv.toString().replace(".", "");
      this.variableValueMap.put(varName, cv);
    }
  }

  public double getMinValue() {
    return this.minValue;
  }

  public double getMaxValue() {
    return this.maxValue;
  }
  
  public String[] getValues() {
    return this.values;
  }
}