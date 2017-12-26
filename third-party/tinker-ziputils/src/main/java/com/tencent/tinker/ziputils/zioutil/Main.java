package com.tencent.tinker.ziputils.zioutil;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by cpan on 2017/12/19.
 */

public class Main {
    private static final String TAG = "ZipAlign";

    public static void main(String[] args) {
        String src = "/Users/cpan/Downloads/tinkerdiff/rebuild.apk";
        String out = "/Users/cpan/Downloads/tinkerdiff/rebuild-apk-align.apk";
        try {
            process(src, out, 4, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static final String CERT_SF_NAME = "META-INF/CERT.SF";
    private static final String CERT_RSA_NAME = "META-INF/CERT.RSA";
    private static Pattern stripPattern =
            Pattern.compile("^META-INF/(.*)[.](SF|RSA|DSA)$");

    public static int process(String srcPath, String destPath, int alignment, boolean force) throws IOException {
        long start = System.currentTimeMillis();

        File inFile = new File(srcPath).getCanonicalFile();
        File outFile = new File(destPath).getCanonicalFile();

        ZipInput input = ZipInput.read(inFile.getPath());

        //signZip( input.getEntries(), new FileOutputStream(outFile), outFile);
        Map<String, ZioEntry> zioEntries = input.getEntries();


        ZipOutput zipOutput = null;

        try {


            zipOutput = new ZipOutput(new FileOutputStream(outFile));
            int i = 1;
            for (ZioEntry inEntry : zioEntries.values()) {
                i += 1;
                zipOutput.write(inEntry);
            }
        } finally {

        }
        zipOutput.close();

        return -1;
    }

}
