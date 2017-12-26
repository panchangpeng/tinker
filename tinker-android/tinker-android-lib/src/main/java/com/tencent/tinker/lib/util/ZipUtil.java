package com.tencent.tinker.lib.util;

import com.tencent.tinker.ziputils.ziputil.TinkerZipEntry;
import com.tencent.tinker.ziputils.ziputil.TinkerZipFile;
import com.tencent.tinker.ziputils.ziputil.TinkerZipOutputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by cpan on 2017/12/20.
 */

public class ZipUtils {
    private static final int BUFFER_SIZE = 16384;
    
    public static void extractTinkerEntry(TinkerZipFile apk, TinkerZipEntry zipEntry, TinkerZipOutputStream outputStream) throws IOException {
        InputStream in = null;
        try {
            in = apk.getInputStream(zipEntry);
            outputStream.putNextEntry(new TinkerZipEntry(zipEntry));
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

    public static void extractTinkerEntry(TinkerZipEntry zipEntry, InputStream inputStream, TinkerZipOutputStream outputStream) throws IOException {
        outputStream.putNextEntry(zipEntry);
        byte[] buffer = new byte[BUFFER_SIZE];

        for (int length = inputStream.read(buffer); length != -1; length = inputStream.read(buffer)) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.closeEntry();
    }

    public static void extractLargeModifyFile(TinkerZipEntry sourceArscEntry, File newFile, long newFileCrc, TinkerZipOutputStream outputStream) throws IOException {
        TinkerZipEntry newArscZipEntry = new TinkerZipEntry(sourceArscEntry);

        newArscZipEntry.setMethod(TinkerZipEntry.STORED);
        newArscZipEntry.setSize(newFile.length());
        newArscZipEntry.setCompressedSize(newFile.length());
        newArscZipEntry.setCrc(newFileCrc);
        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(newFile));
            outputStream.putNextEntry(new TinkerZipEntry(newArscZipEntry));
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

    public static void extractTinkerEntry(TinkerZipFile apk, TinkerZipEntry zipEntry, String name, TinkerZipOutputStream outputStream) throws IOException {
        InputStream in = null;
        try {
            in = apk.getInputStream(zipEntry);
            outputStream.putNextEntry(new TinkerZipEntry(zipEntry, name));
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
