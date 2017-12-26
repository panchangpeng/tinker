package com.tencent.tinker.lib.patch;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.tencent.tinker.commons.util.StreamUtil;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.lib.util.ZipUtil;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareBsDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareDexDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.ziputils.zioutil.ZioEntry;
import com.tencent.tinker.ziputils.zioutil.ZipInput;
import com.tencent.tinker.ziputils.zioutil.ZipOutput;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by cpan on 2017/12/10.
 */

public class ApkDiffPatchInternal extends BasePatchInternal {
    private static final String TAG = "Tinker.ApkDiffPatchInternal";

    public static boolean tryRecoverApkFiles(Tinker manager, ShareSecurityCheck checker, Context context,
                                             String diffDirectory, String patchVersionDirectory, File patchFile,
                                             int patchMode, SharePatchInfo sharePatchInfo, File patchInfoFile, File patchInfoLockFile) {

        if (patchMode == ShareConstants.PATCH_MODE_HOT) {
            TinkerLog.i(TAG, "It's not apk patch pack, ignore. mode:%d", patchMode);
            return true;
        }

        String dir = patchVersionDirectory + "/" + ShareConstants.RES_PATH + "/";
        File directory = new File(dir);

        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo == null) {
            // Looks like running on a test Context, so just return without patching.
            TinkerLog.w(TAG, "applicationInfo == null!!!!");
            return true;
        }


        String tinkerId = checker.getPackagePropertiesIfPresent().get(ShareConstants.TINKER_ID);
        TinkerLog.w(TAG, "cpan try to recover apk start. tinkerId:%s", tinkerId);

        String oldApkPath = applicationInfo.sourceDir;
        String newApkPath = directory + File.separator + tinkerId + ".apk";
        String newApkAlignPath = diffDirectory + File.separator + ShareConstants.PATCH_DIRECTORY_NAME + File.separator + tinkerId + "_align.apk";
        TinkerLog.d(TAG, "oldApkPath:%s newApkPath:%s newApkAlignPath:%s", oldApkPath, newApkPath, newApkAlignPath);

        File newApkFile = new File(newApkPath);
        File newApkAlignFile = new File(newApkAlignPath);
        File oldApkFile = new File(oldApkPath);
        // write dex and lib to res apk then make it as real apk

        // if new apk align file has exist
        if (SharePatchFileUtil.isLegalFile(newApkAlignFile)) {
            if (SharePatchFileUtil.verifyFileMd5(newApkAlignFile, sharePatchInfo.apkVersion)) {
                TinkerLog.i(TAG, "new apk align is exist. need no to regen apk file.");
                return true;
            } else {
                newApkAlignFile.delete();
                TinkerLog.w(TAG, "new apk align exist, not match apk version.");
            }
        } else {
            newApkAlignFile.getParentFile().mkdirs();
        }

        ZipOutputStream out = null;
        ZipFile patchZipFile = null;
        ZipFile oldApkZipFile = null;
        ZipFile classNZipFile = null;
        ZipFile resourceZipFile = null;
        InputStream inputStream = null;


        List<String> zipNameAdded = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            if (!newApkFile.exists()) {
                newApkFile.getParentFile().mkdirs();
            } else {
                TinkerLog.d(TAG, "newApkFile exist.");
            }

            out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(newApkFile)));

            patchZipFile = new ZipFile(patchFile);

            // copy AndroidManifest.xml from patch.zip
            ZipEntry manifestZipEntry = patchZipFile.getEntry(ShareConstants.RES_MANIFEST);
            if (manifestZipEntry == null) {
                TinkerLog.w(TAG, "manifest patch entry is null. path:" + ShareConstants.RES_MANIFEST);
                manager.getPatchReporter().onPatchDiffFail(patchFile, newApkFile, newApkAlignFile, ShareConstants.ERROR_DIFF_MISS_MANIFEST);
                return false;
            }

            ZipUtil.extractTinkerEntry(patchZipFile, manifestZipEntry, out);
            TinkerLog.d(TAG, "copy AndroidManifest.xml from patch zip.");

            // copy new apk meta inf
            final Enumeration<? extends ZipEntry> metaInfEntries = patchZipFile.entries();
            while (metaInfEntries.hasMoreElements()) {
                ZipEntry entry = metaInfEntries.nextElement();
                if (entry == null) {
                    manager.getPatchReporter().onPatchDiffFail(patchFile, newApkFile, newApkAlignFile, ShareConstants.ERROR_DIFF_MISS_MATE_INF);
                    throw new TinkerRuntimeException("zipEntry is null when get from patch file.");
                }
                String name = entry.getName();
                if (name.contains("../")) {
                    continue;
                }
                if (!entry.isDirectory() && name.startsWith(ShareConstants.RES_APK_META_FILES)) {
                    String newName = ShareConstants.RES_APK_META_INF + File.separator + name.substring(name.lastIndexOf("/") + 1);
                    ZipUtil.extractTinkerEntry(patchZipFile, entry, newName, out);
                    zipNameAdded.add(newName);
                    TinkerLog.d(TAG, "copy %s from patch zip as %s", name, newName);
                } else {
                    TinkerLog.d(TAG, "skip copy %s from patch zip.", name);
                }
            }

            // copy *.so
            String soDir = patchVersionDirectory + "/" + SO_PATH + "/";
            String libMeta = checker.getMetaContentMap().get(SO_META_FILE);
            ArrayList<ShareBsDiffPatchInfo> patchList = new ArrayList<>();
            ShareBsDiffPatchInfo.parseDiffPatchInfo(libMeta, patchList);

            if (patchList.isEmpty()) {
                TinkerLog.i(TAG, "no so lib need to copy.");
            }
            for (ShareBsDiffPatchInfo info : patchList) {
                String soFilePath = soDir + info.path + "/" + info.name;
                String soName = SO_PATH + File.separator + info.path + File.separator + info.name;
                File soFile = new File(soFilePath);
                if (soFile.exists()) {
                    TinkerLog.d(TAG, "copy %s from %s", info.name, soFilePath);
                    inputStream = new FileInputStream(soFile);
                    ZipEntry z = new ZipEntry(soName);
                    ZipUtil.extractTinkerEntry(z, inputStream, out);
                    zipNameAdded.add(soName);
                } else {
                    TinkerLog.w(TAG, "skip copy %s. file not exist. ", soFilePath);
                }
            }

            // copy class.dex
            String dexDir = patchVersionDirectory + "/" + DEX_PATH + "/";
            String dexMeta = checker.getMetaContentMap().get(DEX_META_FILE);
            ArrayList<ShareDexDiffPatchInfo> dexList = new ArrayList<>();

            File classNFile = new File(dexDir, ShareConstants.CLASS_N_APK_NAME);

            ShareDexDiffPatchInfo.parseDexDiffPatchInfo(dexMeta, dexList);
            if (dexList.isEmpty()) {
                TinkerLog.i(TAG, "no so dex need to copy.");
            }

            if (!classNFile.exists()) {
                TinkerLog.i(TAG, "can not find tinker_classN.apk");
            } else {
                TinkerLog.i(TAG, "start copy dex :%d", dexList.size());
                classNZipFile = new ZipFile(classNFile);
                final Enumeration<? extends ZipEntry> dexEntries = classNZipFile.entries();
                while (dexEntries.hasMoreElements()) {
                    ZipEntry zipEntry = dexEntries.nextElement();
                    if (zipEntry == null) {
                        throw new TinkerRuntimeException("zipEntry is null when get from patch file.");
                    }
                    String name = zipEntry.getName();
                    if (name.contains("../")) {
                        continue;
                    }
                    if (!zipEntry.isDirectory() && !name.equalsIgnoreCase(ShareConstants.TEST_DEX_NAME)) {
                        TinkerLog.d(TAG, "copy %s from %s", name, ShareConstants.CLASS_N_APK_NAME);
                        ZipUtil.extractTinkerEntry(classNZipFile, zipEntry, out);
                        zipNameAdded.add(zipEntry.getName());
                    } else {
                        TinkerLog.w(TAG, "skip copy %s from %s", name, ShareConstants.CLASS_N_APK_NAME);
                    }
                }
            }

            //copy resource
            String resourceDir = patchVersionDirectory + "/" + ShareConstants.RES_PATH + "/";
            File resourceFile = new File(resourceDir, ShareConstants.RES_NAME);
            if (!resourceFile.exists()) {
                TinkerLog.w(TAG, "can not find %s", ShareConstants.RES_NAME);
            } else {
                resourceZipFile = new ZipFile(resourceFile);
                final Enumeration<? extends ZipEntry> resourceEntries = resourceZipFile.entries();
                while (resourceEntries.hasMoreElements()) {
                    ZipEntry zipEntry = resourceEntries.nextElement();
                    if (zipEntry == null) {
                        throw new TinkerRuntimeException("zipEntry is null when get from resource.apk");
                    }
                    String name = zipEntry.getName();
                    if (name.contains("../")) {
                        continue;
                    }

                    if (!zipEntry.isDirectory() && !zipNameAdded.contains(name) && !name.equalsIgnoreCase(ShareConstants.RES_MANIFEST)) {
                        TinkerLog.d(TAG, "copy %s from resource.apk.", name);
                        ZipUtil.extractTinkerEntry(resourceZipFile, zipEntry, out);
                        zipNameAdded.add(name);
                    } else {
                        TinkerLog.d(TAG, "skip copy %s from resource.apk.", name);
                    }
                }
            }

            //copy other file from old apk
            oldApkZipFile = new ZipFile(oldApkFile);
            final Enumeration<? extends ZipEntry> entries = oldApkZipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (zipEntry == null) {
                    throw new TinkerRuntimeException("zipEntry is null when get from oldApk");
                }
                String name = zipEntry.getName();
                if (name.contains("../")) {
                    continue;
                }
                // "res/*", "assets/*", "resources.arsc"
                // copy 除AndroidManifst、dex、lib、meta-inf外的所有方法
                if (!zipNameAdded.contains(name) && !name.equalsIgnoreCase(ShareConstants.RES_MANIFEST)
                        && !name.startsWith(ShareConstants.RES_APK_META_INF)) {
                    ZipUtil.extractTinkerEntry(oldApkZipFile, zipEntry, out);
                    zipNameAdded.add(name);
                    TinkerLog.d(TAG, "copy %s from old apk.", name);
                } else {
                    TinkerLog.d(TAG, "skip copy %s from old apk.", name);
                }
            }


        } catch (Throwable e) {
            manager.getPatchReporter().onDiffException(patchFile, newApkFile, newApkAlignFile, e);
            throw new TinkerRuntimeException("apk diff patch failed. " + e.getStackTrace());
        } finally {
            StreamUtil.closeQuietly(out);
            StreamUtil.closeQuietly(inputStream);
            StreamUtil.closeQuietly(patchFile);
            StreamUtil.closeQuietly(oldApkZipFile);
            StreamUtil.closeQuietly(classNZipFile);
        }

        ZipInput input = null;
        ZipOutput zipOutput = null;

        try {
            TinkerLog.d(TAG, "start align zip");
            long alignTime = System.currentTimeMillis();
            input = ZipInput.read(newApkPath);
            Map<String, ZioEntry> zioEntries = input.getEntries();
            zipOutput = new ZipOutput(new FileOutputStream(newApkAlignFile));
            for (ZioEntry inEntry : zioEntries.values()) {
                zipOutput.write(inEntry);
            }
            TinkerLog.d(TAG, "align zip cost:%s", (System.currentTimeMillis() - alignTime));

        } catch (Throwable e) {
            manager.getPatchReporter().onDiffException(patchFile, newApkFile, newApkAlignFile, e);
            throw new TinkerRuntimeException("apk diff patch failed. " + e.getMessage());
        } finally {
            if (zipOutput != null) {
                try {
                    zipOutput.close();
                } catch (IOException e) {
                    TinkerLog.w(TAG, "align zip cost:%s", e.getMessage());
                }
            }
            if (input != null) {
                input.close();
            }
        }

        if (newApkFile.exists()) {
            newApkFile.delete();
        }

        long costTime = System.currentTimeMillis() - startTime;
        if (SharePatchFileUtil.isLegalFile(newApkAlignFile)) {
            String apkMd5 = SharePatchFileUtil.getMD5(newApkAlignFile);
            sharePatchInfo.apkVersion = apkMd5;
            SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, sharePatchInfo, patchInfoLockFile);
            manager.getPatchReporter().onPatchDiffFail(patchFile, newApkFile, newApkAlignFile, ShareConstants.ERROR_DIFF_OK);
        } else {
            manager.getPatchReporter().onPatchDiffFail(patchFile, newApkFile, newApkAlignFile, ShareConstants.ERROR_DIFF_GEN_APK_FAILED);
        }
        TinkerLog.i(TAG, "apk diff patch cost:" + costTime);
        return true;
    }

}
