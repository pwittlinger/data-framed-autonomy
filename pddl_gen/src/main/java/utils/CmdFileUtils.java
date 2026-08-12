package utils;

import java.io.File;

public class CmdFileUtils {

    //String currentPath;
    //String outputFolder;
    //String pddlFolder;

    private CmdFileUtils(){
        //this.currentPath = System.getProperty("user.dir") + File.separator;
        //this.outputFolder = currentPath + "output" + File.separator;
        //this.pddlFolder = outputFolder + "pddl" + File.separator;

    }

    public static boolean petriFileExists(String filePath) {
        String currentPath = System.getProperty("user.dir") + File.separator;
        if (!filePath.endsWith("pnml")) {
            return false;
        }

        File petriFile = new File(filePath);

        // Test first absolute path
        if (!petriFile.isFile()) {
            petriFile = new File(currentPath + filePath);
        }

        return petriFile.exists();

    }

    public static boolean declareFileExists(String filePath) {

        String currentPath = System.getProperty("user.dir") + File.separator;
    

        return ((filePath.endsWith("decl")) && (new File(filePath).isFile() || new File(currentPath + filePath).isFile()));
    }

    public static boolean logFileExists(String filePath) {

        String currentPath = System.getProperty("user.dir") + File.separator;
    

        return ((filePath.endsWith("xes")) && (new File(filePath).isFile() || new File(currentPath + filePath).isFile()));
    }


}
