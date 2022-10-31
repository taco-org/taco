package org.dataspread.sheetanalyzer.mainTest;

import java.io.*;
import java.util.HashMap;

public class SummarizeRefDist {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Need two arguments: \n" +
                    "the input file of the distribution of number of references \n" +
                    "the output file \n");
            System.exit(-1);
        }

        HashMap<Integer, Long> numRefDist = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(args[0]));
             PrintWriter distPW = new PrintWriter(new FileWriter(args[1], true))) {

            br.lines().forEach(line -> {
                String[] splitStrs = line.split(",");
                if (splitStrs.length == 2) {
                    int numRef = Integer.parseInt(splitStrs[0]);
                    long count = Long.parseLong(splitStrs[1]);
                    long existingCount = numRefDist.getOrDefault(numRef, 0L);
                    numRefDist.put(numRef, count + existingCount);
                }
            });

            numRefDist.forEach((numRefs, count) ->
                    distPW.write(numRefs + "," + count + "\n"));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
