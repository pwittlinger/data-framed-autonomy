package utils;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class JsonParser {

    public static ArrayList<String[]> parseJsonFile(String filePath) throws Exception {
        // Read the file content
        String content = new String(Files.readAllBytes(Paths.get(filePath)));

        // Wrap in array brackets if not already an array
        String jsonContent = content.trim();
        if (!jsonContent.startsWith("[")) {
            jsonContent = "[" + jsonContent + "]";
        }

        // Parse JSON array
        JSONArray jsonArray = new JSONArray(jsonContent);
        ArrayList<String[]> result = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject entry = jsonArray.getJSONObject(i);
            String task = entry.getString("task");
            String resource = entry.getString("resource");
            int duration = entry.getInt("duration");

            result.add((task + " " + resource + " " + duration).split(" "));
        }

        return result;
    }

    /*public static void main(String[] args) throws Exception {
        List<String> entries = parseJsonFile("data.json");
        entries.forEach(System.out::println);
    }*/
}