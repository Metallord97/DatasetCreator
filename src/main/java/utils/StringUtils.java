package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StringUtils {
    public static String getExtension(String path) {
        int lastIndexOf = path.lastIndexOf(".");
        if(lastIndexOf == -1) {
            return "";
        }
        return path.substring(lastIndexOf+1);
    }

    public static String removeSubstring (String str, String substring) {
        return str.replace(substring, "");
    }

    public static boolean hasMatchingSubstring(String str, String... substrings) {
        return Arrays.asList(substrings).stream().anyMatch(str::contains);
    }

    public static List<String> removePathWithSubstring(List<String> paths, String... substrings) {
        List<String> newList = new ArrayList<>();
        for(String path: paths) {
            if(!hasMatchingSubstring(path, substrings)) {
                newList.add(path);
            }
        }
        return newList;
    }

    public static String getFileName(String path) {
        File file = new File(path);
        return file.getName();
    }

}
