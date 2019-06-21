package com.tencent.tinker.build.tinkerdiff;

import com.tencent.tinker.build.info.InfoWriter;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.MD5;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.commons.util.StreamUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by cpan on 2017/12/7.
 */

public class TinkerDiffGenerator {
    private static final String TAG = "TinkerDiffGenerator";

    private Configuration configuration;
//    private File unSignedApk;
//    private File signedApk;
//    private File zipalignApk;

    private final InfoWriter metaWriter;

    public TinkerDiffGenerator(Configuration config) throws IOException {
        configuration = config;

        String fileName = config.mNewApkFile.getName().substring(0, config.mNewApkFile.getName().lastIndexOf("."));

//        unSignedApk = new File(config.mTempUnzipMergeDir, fileName + "_unsigned.apk");
//        signedApk = new File(config.mTempUnzipMergeDir, fileName + "_signed.apk");
//        zipalignApk = new File(config.mTempUnzipMergeDir, fileName + "_signed_align.apk");

        String prePath = TypedValue.FILE_ASSETS + File.separator;
        String metaPath = prePath + TypedValue.PATCH_META_FILE;
        if (metaPath != null) {
            metaWriter = new InfoWriter(config, config.mTempResultDir + File.separator + metaPath);
        } else {
            metaWriter = null;
        }
    }


    public void executeAndSave() throws Exception {
        Logger.d("executeAndSave start.");

        unzipNewApk(configuration.mNewApkFile, configuration.mTempUnzipMergeDir);
//        removeMetaInfo();
//        copyDexes();

        Iterator<Pattern> iterator = configuration.mResIgnoreChangePattern.iterator();
        while (iterator.hasNext()) {
            Pattern p = iterator.next();
            Logger.d(p.toString());
        }

        //do not keep old resource, this make resign
//        copyOldIgnoreRes(configuration.mTempUnzipOldDir);
        //only for weixin
//        regenMultixDexMate();

//        unsignApk();
//        signApk(unSignedApk, signedApk);
//        zipalignApk(signedApk, zipalignApk);

        if (metaWriter != null) {
            ArrayList<String> lines = new ArrayList<>();
            lines.add("mode="+ Configuration.PACKING_MODE_TKDIFF);
            lines.add("md5=" + MD5.getMD5(configuration.mNewApkFile));
            metaWriter.writeLinesToInfoFile(lines);
//            Logger.d("md5:%s", MD5.getMD5(signedApk));
        } else {
            Logger.d("metaWriter is null");
        }

//        1.AndroidManifest
//        2.META-INF
//        3.*.SO patch
//        4.*.dex patch
//        5.resource patch

        copyAndroidManifest(configuration.mTempUnzipMergeDir.getAbsolutePath() + File.separator + TypedValue.RES_MANIFEST, configuration.mTempResultDir.getAbsolutePath() + File.separator + TypedValue.RES_MANIFEST);
        copyMetaInfoFromNewApk();
//        String clazzMerge = configuration.mTempUnzipMergeDir.getAbsolutePath() + File.separator + TypedValue.FILE_ASSETS + File.separator + "secondary-program-dex-jars" + File.separator + "metadata.txt";
//        String clazzMetaDest = configuration.mTempResultDir.getAbsolutePath() + File.separator + TypedValue.FILE_ASSETS + File.separator + "secondary-program-dex-jars" + File.separator + "metadata.txt";
//        copyClazzMetaInfoFromMerge(clazzMerge, clazzMetaDest);
        Logger.d("executeAndSave finish.");

    }

    private void unzipNewApk(File file, File destFile) throws TinkerPatchException, IOException {
        Logger.d("unzipNewApk");
        String apkName = file.getName();
        if (!apkName.endsWith(TypedValue.FILE_APK)) {
            throw new TinkerPatchException(
                    String.format("input apk file path must end with .apk, yours %s\n", apkName)
            );
        }

        String destPath = destFile.getAbsolutePath();
        Logger.d("UnZipping apk to %s", destPath);
        FileOperation.unZipAPk(file.getAbsoluteFile().getAbsolutePath(), destPath);
    }

    private void copyAndroidManifest(String src, String dest) throws IOException {
        File srcFile = new File(src);
        File destFile = new File(dest);
        FileOperation.copyFileUsingStream(srcFile, destFile);
    }

//    private void copyClazzMetaInfoFromMerge(String scr, String dest) throws IOException {
//        File srcFile = new File(scr);
//        File destFile = new File(dest);
//        if (srcFile.exists()) {
//            FileOperation.copyFileUsingStream(srcFile, destFile);
//        } else {
//            Logger.d("copyClazzMetaInfoFromMerge failed.");
//        }
//    }

    private void removeMetaInfo() {
        File mateInfoDir = new File(configuration.mTempUnzipMergeDir.getAbsolutePath() + File.separator + "META-INF");
        Logger.d("mate path %s", mateInfoDir.getAbsolutePath());
        Logger.d("mate path %s", mateInfoDir.getPath());
        FileOperation.deleteDir(mateInfoDir);

    }

    /**
     * copy new dex
     * copy new res drawable so resource.ars
     * 分散在各个 patchInternal 去处理
     */
    private void copyDexes() throws IOException {
        Logger.d("copyDexes");
        File tempFullPatchDexPath = new File(configuration.mOutFolder + File.separator + TypedValue.DEX_TEMP_PATCH_DIR);


        File[] dexFiles = tempFullPatchDexPath.listFiles();

        if (dexFiles != null && dexFiles.length > 0) {
            for (File f : dexFiles) {
                //File srcFile = new File(tempFullPatchDexPath, f.getAbsolutePath());
                //File descFile = new File(configuration.mTempUnzipMergeDir, f.getAbsolutePath());
                File descFile = new File(configuration.mTempUnzipMergeDir, f.getName());
                FileOperation.copyFileUsingStream(f, descFile);
                Logger.d("copyDexes from:%s to:%s", f.getPath(), descFile);

            }

        } else {
            Logger.d("Empty dex");
        }
    }

    // this is only for wexin tinker diff
//    private void regenMultixDexMate() throws IOException {
//        Logger.d("regenMultixDexMate");
//        InfoWriter writer = new InfoWriter(configuration, configuration.mTempUnzipMergeDir.getAbsolutePath() + File.separator + TypedValue.FILE_ASSETS + File.separator + "secondary-program-dex-jars" + File.separator + "metadata.txt");
//        File[] dexFiles = configuration.mTempUnzipMergeDir.listFiles();
//
//        if (dexFiles != null && dexFiles.length > 0) {
//            for (File f : dexFiles) {
//                if (f.getName().endsWith(".dex")) {
//                    try {
//                        Dex dexIn = new Dex(f);
//                        String dexName = f.getName();
//                        String dexMd5 = MD5.getMD5(f);
//                        String tempClazzName = dexIn.typeNames().get(dexIn.classDefs().iterator().next().typeIndex);
//                        String clazzName = tempClazzName.replaceAll("/", ".").substring(1).replaceAll(";", "");
//                        Logger.d("dexName:" + dexName);
//                        Logger.d("tempClazzName:" + tempClazzName);
//                        Logger.d("clazzName:" + clazzName);
//                        Logger.d("dex md5:" + dexMd5);
//                        writer.writeLineToInfoFile(dexName + " " + dexMd5 + " " + clazzName);
//                    } catch (Exception e) {
//                        Logger.e("Get classes.dex md5 failed.");
//                    }
//                }
//            }
//
//        } else {
//            Logger.d("Empty dex");
//        }
//        writer.close();
//    }
//
//    private void copyOldIgnoreRes(File inputFile) throws IOException {
//        File[] resFilesList = inputFile.listFiles();
//        for (File file : resFilesList) {
//            if (file.isDirectory()) {
//                copyOldIgnoreRes(file);
//            } else {
//                String name = getRelativePathStringToOldFile(file);
//                if (Utils.checkFileInPattern(configuration.mResIgnoreChangePattern, name)) {
//                    File descFile = new File(configuration.mTempUnzipMergeDir, name);
//                    Logger.d("copy :%s to :%s", file.getPath(), descFile.getPath());
//                    if (!descFile.exists()) {
//                        descFile.getParentFile().mkdirs();
//                    }
//                    FileOperation.copyFileUsingStream(file, descFile);
//                }
//            }
//
//        }
//    }

    public String getRelativePathStringToOldFile(File oldFile) {
        return configuration.mTempUnzipOldDir.toPath().relativize(oldFile.toPath()).toString().replace("\\", "/");
    }

//    private void unsignApk() throws IOException {
//        Logger.d("unsignApk");
//        long start = System.currentTimeMillis();
//        FileOperation.zipInputDir(configuration.mTempUnzipMergeDir, unSignedApk, null);
//        Logger.d("unsignApk cost:" + (System.currentTimeMillis() - start));
//    }

//    private void signApk(File input, File output) throws Exception {
//        //sign apk
//        Logger.d("Signing apk: %s", output.getName());
//        String signatureAlgorithm = getSignatureAlgorithm();
//        Logger.d("Signing key algorithm is %s", signatureAlgorithm);
//
//        if (output.exists()) {
//            output.delete();
//        }
//        ArrayList<String> command = new ArrayList<>();
//        command.add("jarsigner");
//        command.add("-sigalg");
//        command.add(signatureAlgorithm);
//        command.add("-digestalg");
//        command.add("SHA1");
//        command.add("-keystore");
//        command.add(configuration.mSignatureFile.getAbsolutePath());
//        command.add("-storepass");
//        command.add(configuration.mStorePass);
//        command.add("-keypass");
//        command.add(configuration.mKeyPass);
//        command.add("-signedjar");
//        command.add(output.getAbsolutePath());
//        command.add(input.getAbsolutePath());
//        command.add(configuration.mStoreAlias);
//
//        Process process = new ProcessBuilder(command).start();
//        process.waitFor();
//        process.destroy();
//        if (!output.exists()) {
//            throw new IOException("Can't Generate signed APK. Please check if your sign info is correct.");
//        }
//    }

//    private void zipalignApk(File input, File output) throws Exception {
//        //sign apk
//        Logger.d("zipalign apk: %s", input.getName());
//
//        if (output.exists()) {
//            output.delete();
//        }
//        String cmd = configuration.mZipAlignPath;
//        Logger.d("zipalign path:" + cmd);
//        if (cmd == null || cmd.length() < -0) {
//            cmd = "zipalign";
//        }
//
////        ArrayList<String> command = new ArrayList<>();
////        command.add(cmd);
////        command.add("-f");
////        command.add("-v");
////        command.add("4");
////        command.add(input.getAbsolutePath());
////        command.add(output.getAbsolutePath());
////
////        Process process = new ProcessBuilder(command).start();
////        process.waitFor();
////        process.destroy();
////        if (!output.exists()) {
////            throw new IOException("Can't zipalign APK. Please check if your sign info is correct.");
////        }
//
//        ProcessBuilder pb = new ProcessBuilder(cmd, "-f", "-v", "4", input.getAbsolutePath(), output.getAbsolutePath());
//        pb.redirectErrorStream(true);
//        Process pro = null;
//
//        LineNumberReader reader = null;
//        try {
//            pro = pb.start();
//            reader = new LineNumberReader(new InputStreamReader(pro.getInputStream()));
//            while (reader.readLine() != null) {
//            }
//        } catch (IOException e) {
//            FileOperation.deleteFile(output);
//            Logger.e("zip align file failed, you should set the zip align, or set the path directly");
//        } finally {
//            try {
//                pro.waitFor();
//            } catch (Throwable ignored) {
//                // Ignored.
//            }
//            try {
//                pro.destroy();
//            } catch (Throwable ignored) {
//                // Ignored.
//            }
//            StreamUtil.closeQuietly(reader);
//        }
//    }

    private void copyMetaInfoFromNewApk() throws IOException {
        Logger.d(TAG, "copyMetaInfoFromNewApk");

        String apkMateInfo = configuration.mTempResultDir.getAbsolutePath() + File.separator + TypedValue.FILE_ASSETS + File.separator + "APK-META-INF";
        FileInputStream fileInputStream = new FileInputStream(configuration.mNewApkFile);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
        ZipInputStream zin = new ZipInputStream(bufferedInputStream);
        ZipEntry ze = null;

        File outPathDir = new File(apkMateInfo);
        if (!outPathDir.exists()) {
            outPathDir.mkdirs();
        }

        while ((ze = zin.getNextEntry()) != null) {
            if (ze.getName().startsWith("META-INF")) {
                String outPath = (apkMateInfo + File.separator + ze.getName().substring(ze.getName().lastIndexOf("/") + 1));
                OutputStream out = new FileOutputStream(outPath);
                byte[] buffer = new byte[9000];
                int len;
                while ((len = zin.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                StreamUtil.closeQuietly(out);
            }
        }
        StreamUtil.closeQuietly(zin);
    }

    private String getSignatureAlgorithm() throws Exception {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(configuration.mSignatureFile));
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(is, configuration.mStorePass.toCharArray());
            Key key = keyStore.getKey(configuration.mStoreAlias, configuration.mKeyPass.toCharArray());
            String keyAlgorithm = key.getAlgorithm();
            String signatureAlgorithm;
            if (keyAlgorithm.equalsIgnoreCase("DSA")) {
                signatureAlgorithm = "SHA1withDSA";
            } else if (keyAlgorithm.equalsIgnoreCase("RSA")) {
                signatureAlgorithm = "SHA1withRSA";
            } else if (keyAlgorithm.equalsIgnoreCase("EC")) {
                signatureAlgorithm = "SHA1withECDSA";
            } else {
                throw new RuntimeException("private key is not a DSA or "
                        + "RSA key");
            }
            return signatureAlgorithm;
        } finally {
            StreamUtil.closeQuietly(is);
        }
    }

}
