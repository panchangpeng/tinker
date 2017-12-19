package com.tencent.tinker.build.apkpatcher;

import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.FileOperation;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.commons.util.StreamUtil;
import com.tencent.tinker.ziputils.ziputil.TinkerZipEntry;
import com.tencent.tinker.ziputils.ziputil.TinkerZipFile;
import com.tencent.tinker.ziputils.ziputil.TinkerZipOutputStream;
import com.tencent.tinker.ziputils.ziputil.TinkerZipUtil;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by cpan on 2017/12/7.
 */

public class ApkPatchGenerator {
    private static final String TAG = "ApkPatchGenerator";

    private Configuration configuration;
    private File oldApk;
    private File newApk;

    private File unSignedApk;
    private File signedApk;

    public ApkPatchGenerator(Configuration config) throws IOException {
        configuration = config;
        oldApk = configuration.mOldApkFile;
        newApk = configuration.mNewApkFile;

        unSignedApk = new File(config.mTempUnzipMergeDir, config.mNewApkFile.getName() + "_unsigned.apk");
        signedApk = new File(config.mTempUnzipMergeDir, config.mNewApkFile.getName() + "_signed.apk");
        // signedWith7zApk = new File(config.mTempUnzipMergeDir, config.mNewApkFile.getName() + "_signed_7zip.apk");
        // sevenZipOutPutDir = new File(config.mOutFolder, TypedValue.OUT_7ZIP_FILE_PATH);
    }


    public void executeAndSave() throws Exception {
        Logger.d("executeAndSave start.");
        unzipNewApk(configuration.mNewApkFile, configuration.mTempUnzipMergeDir);
        removeMetaInfo();
        copyDexes();
//        copyOldResourceARSC();
//        copyAndroidManifestToMergeDir();
        unsignApk();
        signApk(unSignedApk, signedApk);

        copyMetaInfoFromMergeApk();
        copyAndroidManifest(configuration.mTempUnzipMergeDir.getAbsolutePath() + File.separator + TypedValue.RES_MANIFEST, configuration.mTempResultDir.getAbsolutePath() + File.separator + TypedValue.RES_MANIFEST);
        Logger.d("executeAndSave finish.");

    }

    private void unzipNewApk(File file, File destFile) throws TinkerPatchException, IOException {
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

    private void removeMetaInfo() {
        File mateInfoDir = new File(configuration.mTempUnzipMergeDir.getAbsolutePath() + File.separator + "META-INF");
        Logger.d("mate path %s", mateInfoDir.getAbsolutePath());
        Logger.d("mate path %s", mateInfoDir.getPath());
        FileOperation.deleteDir(mateInfoDir);

    }

    /**
     * copy new dex
     * copy new res drawable so resource.ars
     * 分散在各个 pathcInternal 去处理
     */
    private void copyDexes() throws IOException {
        File tempFullPatchDexPath = new File(configuration.mOutFolder + File.separator + TypedValue.DEX_TEMP_PATCH_DIR);
        File[] dexFiles = tempFullPatchDexPath.listFiles();
        if (dexFiles != null && dexFiles.length > 0) {
            for (File f : dexFiles) {
                //File srcFile = new File(tempFullPatchDexPath, f.getAbsolutePath());
                //File descFile = new File(configuration.mTempUnzipMergeDir, f.getAbsolutePath());
                File descFile = new File(configuration.mTempUnzipMergeDir, f.getName());
                FileOperation.copyFileUsingStream(f, descFile);
            }
        } else {
            Logger.d("Empty dex");
        }
    }

    private void copyOldResourceARSC() throws IOException {
        File src = new File(configuration.mTempUnzipOldDir + File.separator + TypedValue.RES_ARSC);
        File desc = new File(configuration.mTempUnzipMergeDir + File.separator + TypedValue.RES_ARSC);
        FileOperation.copyFileUsingStream(src, desc);
    }

    private void unsignApk() throws IOException {
        FileOperation.zipInputDir(configuration.mTempUnzipMergeDir, unSignedApk, null);
    }

    private void signApk(File input, File output) throws Exception {
        //sign apk
//        if (config.mUseSignAPk) {
        Logger.d("Signing apk: %s", output.getName());
        String signatureAlgorithm = getSignatureAlgorithm();
        Logger.d("Signing key algorithm is %s", signatureAlgorithm);

        if (output.exists()) {
            output.delete();
        }
        ArrayList<String> command = new ArrayList<>();
        command.add("jarsigner");
        // issue https://github.com/Tencent/tinker/issues/118
        command.add("-sigalg");
        command.add(signatureAlgorithm);
        command.add("-digestalg");
        command.add("SHA1");
        command.add("-keystore");
        command.add(configuration.mSignatureFile.getAbsolutePath());
        command.add("-storepass");
        command.add(configuration.mStorePass);
        command.add("-keypass");
        command.add(configuration.mKeyPass);
        command.add("-signedjar");
        command.add(output.getAbsolutePath());
        command.add(input.getAbsolutePath());
        command.add(configuration.mStoreAlias);

        Process process = new ProcessBuilder(command).start();
        process.waitFor();
        process.destroy();
        if (!output.exists()) {
            throw new IOException("Can't Generate signed APK. Please check if your sign info is correct.");
        }
//        }
    }

    private void use7zApk(File inputSignedFile, File out7zipFile, File tempFilesDir) throws IOException {
//        if (!config.mUseSignAPk) {
//            return;
//        }
        if (!inputSignedFile.exists()) {
            throw new IOException(
                    String.format("can not found the signed apk file to 7z, if you want to use 7z, "
                            + "you must fill the sign data in the config file path=%s", inputSignedFile.getAbsolutePath())
            );
        }
        Logger.d("Try use 7za to compress the patch file: %s, will cost much more time", out7zipFile.getName());
        Logger.d("Current 7za path:%s", configuration.mSevenZipPath);

        FileOperation.unZipAPk(inputSignedFile.getAbsolutePath(), tempFilesDir.getAbsolutePath());
        //7zip may not enable
        if (!FileOperation.sevenZipInputDir(tempFilesDir, out7zipFile, configuration)) {
            return;
        }
        FileOperation.deleteDir(tempFilesDir);
        if (!out7zipFile.exists()) {
            throw new IOException(String.format(
                    "[use7zApk]7z repackage signed apk fail,you must install 7z command line version first, linux: p7zip, window: 7za, path=%s",
                    out7zipFile.getAbsolutePath()));
        }
    }

    private void copyMetaInfoFromMergeApk() throws IOException {

        String apkMateInfo = configuration.mTempResultDir.getAbsolutePath() + File.separator + TypedValue.FILE_ASSETS + File.separator + "APK-META-INF";
        FileInputStream fileInputStream = new FileInputStream(signedApk);
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

    private void copyManifest() throws IOException {
        TinkerZipFile newApkZip = new TinkerZipFile(configuration.mNewApkPath);
        TinkerZipEntry manifestZipEntry = newApkZip.getEntry(TypedValue.RES_MANIFEST);
        if (manifestZipEntry == null) {
            Logger.d("manifest patch entry is null. path:" + TypedValue.RES_MANIFEST);
        }
        String outPath = (configuration.mTempResultDir + File.separator + TypedValue.RES_MANIFEST);
        TinkerZipOutputStream out = new TinkerZipOutputStream(new FileOutputStream(outPath));
        TinkerZipUtil.extractTinkerEntry(newApkZip, manifestZipEntry, out);
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
