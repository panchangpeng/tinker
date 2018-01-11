package com.tencent.tinker.lib.util;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by cpan on 2017/12/20.
 */

public class ZipUtil {
    private static final int BUFFER_SIZE = 16384;

    public static void extractTinkerEntry(ZipFile apk, ZipEntry zipEntry, ZipOutputStream outputStream) throws IOException {
        InputStream in = null;
        try {
            in = apk.getInputStream(zipEntry);
            outputStream.putNextEntry(new ZipEntry(zipEntry.getName()));
            zipEntry.setMethod(ZipEntry.DEFLATED);
            byte[] buffer = new byte[BUFFER_SIZE];

            for (int length = in.read(buffer); length != -1; length = in.read(buffer)) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.closeEntry();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    public static void extractTinkerEntry(ZipEntry zipEntry, InputStream inputStream, ZipOutputStream outputStream) throws IOException {
        outputStream.putNextEntry(zipEntry);
        byte[] buffer = new byte[BUFFER_SIZE];

        for (int length = inputStream.read(buffer); length != -1; length = inputStream.read(buffer)) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.closeEntry();
    }

    public static void extractLargeModifyFile(ZipEntry sourceArscEntry, File newFile, long newFileCrc, ZipOutputStream outputStream) throws IOException {
        ZipEntry newArscZipEntry = new ZipEntry(sourceArscEntry);

        newArscZipEntry.setMethod(ZipEntry.STORED);
        newArscZipEntry.setSize(newFile.length());
        newArscZipEntry.setCompressedSize(newFile.length());
        newArscZipEntry.setCrc(newFileCrc);
        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(newFile));
            outputStream.putNextEntry(new ZipEntry(newArscZipEntry));
            byte[] buffer = new byte[BUFFER_SIZE];

            for (int length = in.read(buffer); length != -1; length = in.read(buffer)) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.closeEntry();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    public static void extractTinkerEntry(ZipFile apk, ZipEntry zipEntry, String name, ZipOutputStream outputStream) throws IOException {
        InputStream in = null;
        try {
            in = apk.getInputStream(zipEntry);
            ZipEntry z = new ZipEntry(name);
            //z.setMethod(ZipEntry.DEFLATED);
            outputStream.putNextEntry(z);
            byte[] buffer = new byte[BUFFER_SIZE];

            for (int length = in.read(buffer); length != -1; length = in.read(buffer)) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.closeEntry();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
