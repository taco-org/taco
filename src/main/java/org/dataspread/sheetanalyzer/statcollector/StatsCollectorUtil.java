package org.dataspread.sheetanalyzer.statcollector;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class StatsCollectorUtil {

    public static HashMap<String, String> loadCSVtoHashMap(String filePath) {
        HashMap<String, String> hashMap = new HashMap<>();
        try {
            Scanner scanner = new Scanner(new File(filePath));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] columns = line.split(",");
                if (columns.length == 2) {
                    hashMap.put(columns[0], columns[1]);
                } else {
                    System.out.println("Invalid line: " + line);
                }
            }
            scanner.close();
        } catch (FileNotFoundException ignored) {
        }
        return hashMap;
    }

    public static HashMap<String, Integer> convertToStrInt(HashMap<String, String> hashMap) {
        HashMap<String, Integer> retHashMap = new HashMap<>();
        hashMap.forEach((key, value) -> retHashMap.put(key, Integer.parseInt(value)));
        return retHashMap;
    }

    public static HashMap<Integer, Integer> convertToIntInt(HashMap<String, String> hashMap) {
        HashMap<Integer, Integer> retHashMap = new HashMap<>();
        hashMap.forEach((key, value) -> retHashMap.put(Integer.parseInt(key),
                Integer.parseInt(value)));
        return retHashMap;
    }

    public static <K, V> void writeHashMapToCSV(HashMap<K, V> hashMap, String filePath) throws IOException {
        FileWriter fileWriter = new FileWriter(filePath);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

        for (Map.Entry<K, V> entry : hashMap.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();
            bufferedWriter.write(key.toString() + "," + value.toString());
            bufferedWriter.newLine();
        }

        bufferedWriter.close();
    }

    public static String createFilePath(String folderPath, String fileName) {
        // Ensure that the folderPath ends with the file separator if it doesn't already
        if (!folderPath.endsWith(File.separator)) {
            folderPath += File.separator;
        }

        // Concatenate the folderPath and fileName to create the full filePath
        return folderPath + fileName;
    }
}
