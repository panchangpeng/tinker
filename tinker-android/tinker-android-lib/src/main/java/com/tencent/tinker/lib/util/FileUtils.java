package com.tencent.tinker.lib.util;

import com.tencent.tinker.commons.util.StreamUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by cpan on 2017/12/10.
 */

public class FileUtils {

    public static void copyFileWithStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            StreamUtil.closeQuietly(is);
            StreamUtil.closeQuietly(os);
        }
    }

    public static boolean extractApk(String zipPath, String descPath) throws IOException {
        boolean isSuccessful = true;
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipPath));
            ZipInputStream zis = new ZipInputStream(bis);

            BufferedOutputStream bos = null;
            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (!entry.isDirectory()) {
                    String outPutName = descPath + File.separator + entryName;
                    System.out.println("entryName:" + entryName);
                    System.out.println("outPutName:" + outPutName);
                    ensureCreateParentFolder(outPutName);
                    bos = new BufferedOutputStream(new FileOutputStream(outPutName));
                    int b = 0;
                    while ((b = zis.read()) != -1) {
                        bos.write(b);
                    }
                    bos.flush();
                    bos.close();
                } else {
                    System.err.println("entryName:" + entryName);
                }
            }
            zis.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            isSuccessful = false;
        }
        return isSuccessful;
    }

    public static void extractPatch(String zipPath, String descPath) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipPath));
        ZipInputStream zis = new ZipInputStream(bis);

        BufferedOutputStream bos = null;
        ZipEntry entry = null;
        while ((entry = zis.getNextEntry()) != null) {
            String entryName = entry.getName();
            if (!entry.isDirectory() && !(entryName.equalsIgnoreCase("test.dex") || entryName.contains("dex_meta.txt")
                    || entryName.contains("package_meta.txt") || entryName.contains("so_meta.txt")
                    || entryName.contains("res_meta.tet") || entryName.contains("only_use_to_test_tinker_resource.txt"))

                    ) {
                System.out.println("copy from patch:" + entryName);
                String ouputPath = descPath + File.separator + entryName;
                ensureCreateParentFolder(ouputPath);
                ensureClearCurrentFile(ouputPath);
                bos = new BufferedOutputStream(new FileOutputStream(ouputPath));
                int b = 0;
                while (((b = zis.read()) != -1)) {
                    bos.write(b);
                }
                bos.flush();
                bos.close();
            } else {
                System.err.println("no need copy:" + entryName);
            }
        }
        zis.close();
    }

    public static void ensureClearCurrentFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void ensureCreateParentFolder(String path) {
        File file = new File(path);
        if (!file.getParentFile().getAbsoluteFile().exists()) {
            file.getParentFile().getAbsoluteFile().mkdirs();
        }
    }



}
