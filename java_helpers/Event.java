import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Event {
	
	static DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
	//private static String eventStart = "\t\t<event>\r\n";
	private static String eventEnd = "\t\t</event>\r\n";
	
	private static String activityStart = "\t\t<event>\r\n" + 
			"			<string key=\"concept:name\" value=\"";
	private static String activityEnd = "\"/>\r\n" + 
			"			<string key=\"lifecycle:transition\" value=\"complete\"/>\r\n";
	
	//<date key="time:timestamp" value="2022-08-20T02:13:30"/>
	private static String timeStart = "\t\t\t<date key=\"time:timestamp\" value=";
	private static String timeEnd = "\"/>\r\n";
	
	private static String strAttributeStart = "			<string key=\"";
	private static String intAttributeStart = "			<int key=\"";
	private static String floatAttributeStart = "			<float key=\"";
	private static String attributeValueStart = "\" value=\"";
	private static String attributeEnd = "\"/>\r\n";

    private static String resourceStart = "\t\t\t<string key=\"org:resource\" value=\"";
    private static String resourceEnd = "\"/>\r\n";
	
	private String activityName;
	private LocalDateTime timestamp;
	private Map<String, String> payload;
	
	public Event(String name, LocalDateTime ts, Map<String,String> pay) {
		this.activityName = name;
		this.timestamp = ts;
		this.payload = pay;
	}
	
	public String getActivityName() {
		return activityName;
	}
	
	public LocalDateTime getTimestamp() {
		return timestamp;
	}
	
	public Map<String, String> getPayload() {
		return payload;
	}
	
	public String getLogEventTag() {
		return activityStart+activityName+activityEnd;
	}
	
	public String getTimeEventTag() {
		return timeStart+"\""+timestamp.format(myFormatObj).toString()+timeEnd;
	}
	
	public String getVariableTag(String type, String name, String value) {
		if (type.equals("float")) {
			return getFloatVariable(name, value);
		}
		if (type.equals("int")) {
			return getIntegerVariable(name, value);
		}
		if (type.equals("string")) {
			return getStringVariable(name, value);
		}
		return "";
	}
	
	public String getFloatVariable(String name, String value) {
		return floatAttributeStart+name+attributeValueStart+value+attributeEnd;
		
	}
	
	public String getIntegerVariable(String name, String value) {
		return intAttributeStart+name+attributeValueStart+value+attributeEnd;
	}
	
	public String getStringVariable(String name, String value) {
		return strAttributeStart+name+attributeValueStart+value+attributeEnd;
	}

    public String getResourceVariable(String name, String value) {
		return resourceStart+value+resourceEnd;
	}
	
	public String getEventEndTag() {
		return eventEnd;
	}
	

}