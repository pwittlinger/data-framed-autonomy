package log;

import model.Activity;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.extension.std.XTimeExtension;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Trace {
  
  private final ArrayList<Event> originalEventsList;
  private final String name;
  private final ArrayList<Double> timeStamps;
  
  public Trace(XTrace trace, Map<String, Activity> activityMap) {
    this.name = XConceptExtension.instance().extractName(trace);
    this.originalEventsList = readTrace(trace, activityMap);
    this.timeStamps = getTimeStamps(trace);
  }
  
  private ArrayList<Event> readTrace(XTrace trace, Map<String, Activity> activityMap) {
    ArrayList<Event> newEvents = new ArrayList<>();
    for (XEvent event : trace) {
      Activity relatedActivity = activityMap.get(XConceptExtension.instance().extractName(event));
      if (relatedActivity != null) {
        newEvents.add(new Event(event, relatedActivity));
      } else {
        System.out.println("Activity " + XConceptExtension.instance().extractName(event) + " not found");
      }
    }
    return newEvents;
  }
  public ArrayList<Event> getTrace() {
    return this.originalEventsList;
  }
  
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("\tTrace " + name + " contains " + originalEventsList.size() + " events:\n");
    for (Event event : originalEventsList) {
      builder.append(event);
    }
    builder.append(this.getTrace()).append("\n");
    // builder.append("\tTrace ").append(name).append(" after alignment contains ").append(alignedEventsList.size()).append(" events:\n");
    // for (Event event : alignedEventsList) {
    //   builder.append(event);
    // }
    // builder.append(getSequence("repaired")).append("\n");
    return builder.toString();
  }


  private ArrayList<Double> getTimeStamps(XTrace trace) {
    int l = trace.size();

    if (l < 1) {
      return new ArrayList<>();
    }

    XEvent initialEvent = trace.get(0);
    Date iTimeStamp = XTimeExtension.instance().extractTimestamp(initialEvent);

    ArrayList<Double> allTimeStamps = new ArrayList<>();

    allTimeStamps.add(0.0);


    for (int i = 1; i < l; i++) {
      XEvent event = trace.get(i);

      Date timestamp = XTimeExtension.instance().extractTimestamp(event);

      allTimeStamps.add(getDateDiff(iTimeStamp, timestamp, TimeUnit.HOURS));
      //System.out.println(getDateDiff(iTimeStamp, timestamp, TimeUnit.HOURS));
    }
    
    return allTimeStamps;
  }


  //https://stackoverflow.com/questions/1555262/calculating-the-difference-between-two-java-date-instances
  /**
 * Get a diff between two dates
 * @param date1 the oldest date
 * @param date2 the newest date
 * @param timeUnit the unit in which you want the diff
 * @return the diff value, in the provided unit
 */
  private static double getDateDiff(Date date1, Date date2, TimeUnit timeUnit) {
      long diffInMillies = date2.getTime() - date1.getTime();
      //double r = timeUnit.convert(diffInMillies,TimeUnit.MILLISECONDS);
      return (double)Math.round(diffInMillies*10d / (1000.0*60.0*60.0))/10d; // convert to hours
  }


  public ArrayList<Double> getTimeStamps() {
    return this.timeStamps;
  }
}