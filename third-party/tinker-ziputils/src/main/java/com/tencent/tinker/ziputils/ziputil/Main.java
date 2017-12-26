package com.tencent.tinker.ziputils.ziputil;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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

    public static int process(String srcPath, String destPath, int alignment, boolean force) throws IOException {
        long start = System.currentTimeMillis();
        ZipFile zin;
        zin = new ZipFile(srcPath);
        int result = copyAndAlign(zin, destPath, alignment);
        System.out.println("cost time:" + (System.currentTimeMillis() - start));
        return result;
    }

    private static final int BUFFER_SIZE = 16384;

    private static int copyAndAlign(ZipFile in, String out, int alignment) throws IOException {

        File file = new File(out);

        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }

        ZipOutputStream tinkerZipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        final Enumeration<? extends ZipEntry> entries = in.entries();
        while (entries.hasMoreElements()) {
            InputStream inputStream = null;
            ZipEntry newEntry;
            int padding = 0;
            ZipEntry zipEntry = entries.nextElement();
            if (zipEntry == null) {
                System.out.println("zip entry is null.");
                return -1;
            }
            System.out.println(zipEntry.getName());
            inputStream = in.getInputStream(zipEntry);
            newEntry = new ZipEntry(zipEntry.getName());
            tinkerZipOutputStream.putNextEntry(newEntry);
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int length = inputStream.read(buffer); length != -1; length = inputStream.read(buffer)) {
                tinkerZipOutputStream.write(buffer, 0, length);
            }
            tinkerZipOutputStream.closeEntry();
            if (inputStream != null) {
                inputStream.close();
            }
        }
        tinkerZipOutputStream.close();

        return -1;
    }

}

//    private static int copyAndAlign(TinkerZipFile in, String out, int alignment) throws IOException {
//        TinkerZipOutputStream tinkerZipOutputStream = new TinkerZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
//
//        final Enumeration<? extends TinkerZipEntry> entries = in.entries();
//        while (entries.hasMoreElements()) {
//            InputStream inputStream = null;
//            TinkerZipEntry newEntry;
//            int padding = 0;
//            TinkerZipEntry zipEntry = entries.nextElement();
//            if (zipEntry == null) {
//                System.out.println("zip entry is null.");
//                return -1;
//            }
//            if (zipEntry.getMethod() == ZipEntry.DEFLATED) {
//
//            } else {
//                long newOffest = zipEntry.getDataOffset() + bias;
//                padding = (int) ((alignment - (newOffest % alignment)) % alignment);
////                System.out.println("newOffest:" + newOffest + " padding:" + padding);
//            }
//            if (!zipEntry.isDirectory()) {
//
//                System.out.println(zipEntry.getName());
//                inputStream = in.getInputStream(zipEntry);
//
//                newEntry = new TinkerZipEntry(zipEntry);
//                //newEntry.setDataOffset(padding);
//                newEntry.setMethod(ZipEntry.DEFLATED);
//                tinkerZipOutputStream.putNextEntry(newEntry);
//                byte[] buffer = new byte[BUFFER_SIZE];
//                for (int length = inputStream.read(buffer); length != -1; length = inputStream.read(buffer)) {
//                    tinkerZipOutputStream.write(buffer, 0, length);
//                }
//                tinkerZipOutputStream.closeEntry();
//            }
//            if (inputStream != null) {
//                inputStream.close();
//            }
//
//            bias += padding;
//
//        }