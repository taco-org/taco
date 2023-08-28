package org.dataspread.sheetanalyzer.mainTest;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class SimpleDepRefTest {

    public static void main(String[] args) {
        String fileDir = args[0];
        String fileName = args[1];
        String depLoc = "Trade:N2";
        boolean isDollar = false;
        boolean isGap = false;
        boolean isTypeSensitive = false;
        String outputPath = fileDir + "/output";

        try (PrintWriter statPW = new PrintWriter(new FileWriter(outputPath, true))) {
        MainTestUtil.TestRefDependent(statPW, fileDir, fileName, depLoc, isDollar, isGap, isTypeSensitive);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
