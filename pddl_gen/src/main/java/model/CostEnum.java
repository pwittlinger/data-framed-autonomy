package model;

import java.util.EnumMap;
import java.util.Map;

public enum CostEnum {
  ADD,
  SET;

  /**
   * Reads the costs of one line of the cost model: "<activity> <add> <set>".
   * Lines in the old "<activity> <change> <add> <set> <delete>" format are still accepted,
   * their change and delete values are ignored.
   * @param costLine the line split on spaces, activity name first
   */
  public static Map<CostEnum, Integer> parseCostLine(String[] costLine) {
    Map<CostEnum, Integer> parsed = new EnumMap<>(CostEnum.class);

    switch (costLine.length - 1) {
      case 2:
        parsed.put(ADD, Integer.valueOf(costLine[1]));
        parsed.put(SET, Integer.valueOf(costLine[2]));
        break;
      case 4:
        parsed.put(ADD, Integer.valueOf(costLine[2]));
        parsed.put(SET, Integer.valueOf(costLine[3]));
        break;
      default:
        throw new Error("Cost model line for " + costLine[0] + " must contain 2 costs (ADD SET), found " + (costLine.length - 1));
    }
    return parsed;
  }
}
