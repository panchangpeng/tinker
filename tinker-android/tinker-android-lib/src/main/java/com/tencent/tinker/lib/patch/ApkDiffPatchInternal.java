package com.tencent.tinker.lib.patch;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.tencent.tinker.commons.util.StreamUtil;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareBsDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareDexDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.ziputils.ziputil.TinkerZipEntry;
import com.tencent.tinker.ziputils.ziputil.TinkerZipFile;
import com.tencent.tinker.ziputils.ziputil.TinkerZipOutputStream;
import com.tencent.tinker.ziputils.ziputil.TinkerZipUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by cpan on 2017/12/10.
 */

public class ApkDiffPatchInternal extends BasePatchInternal {
    private static final String TAG = "Tinker.ApkDiffPatchInternal";

    public static boolean tryRecoverApk(Tinker manager, ShareSecurityCheck checker, Context context,
                                        String patchVersionDirectory, File patchFile) {

        int patchMode = checker.getPatchMetaPropertiesIfPresent().get(ShareConstants.PATCH_MODE);
        if (patchMode == 0) {
            TinkerLog.i(TAG, "It's not apk patch pack, ignore. mode:%d", patchMode);
            return false;
        }

        String dir = patchVersionDirectory + "/" + ShareConstants.RES_PATH + "/";
        File directory = new File(dir);

        ApplicationInfo applicationInfo = context.getApplicationInfo();
        if (applicationInfo == null) {
            // Looks like running on a test Context, so just return without patching.
            TinkerLog.w(TAG, "applicationInfo == null!!!!");
            return false;
        }
        TinkerLog.w(TAG, "cpan try to recover apk start.");

        String oldApkPath = applicationInfo.sourceDir;
        String newApkPath = directory + File.separator + ShareConstants.RES_APK_NAME;
        TinkerLog.d(TAG, "oldApkPath:%s newApkPath:%s", oldApkPath, newApkPath);

        File newApkFile = new File(directory, ShareConstants.RES_APK_NAME);
        File oldApkFile = new File(oldApkPath);
        // write dex and lib to res apk then make it as real apk

        TinkerZipOutputStream out = null;
        TinkerZipFile patchZipFile = null;
        TinkerZipFile oldApkZipFile = null;
        TinkerZipFile classNZipFile = null;
        InputStream inputStream = null;
        List<String> zipNameAdded = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            if (!newApkFile.exists()) {
                newApkFile.getParentFile().mkdirs();
            } else {
                TinkerLog.d(TAG, "newApkFile exist.");
            }

            out = new TinkerZipOutputStream(new BufferedOutputStream(new FileOutputStream(newApkFile)));

            patchZipFile = new TinkerZipFile(patchFile);


            // copy AndroidManifest.xml from patch.zip
            TinkerZipEntry manifestZipEntry = patchZipFile.getEntry(ShareConstants.RES_MANIFEST);
            if (manifestZipEntry == null) {
                TinkerLog.w(TAG, "manifest patch entry is null. path:" + ShareConstants.RES_MANIFEST);
                manager.getPatchReporter().onPatchTypeExtractFail(patchFile, newApkFile, ShareConstants.RES_MANIFEST, TYPE_RESOURCE);
                return false;
            }
            TinkerZipUtil.extractTinkerEntry(patchZipFile, manifestZipEntry, out);
            TinkerLog.d(TAG, "copy AndroidManifest finish.");

            // copy new apk meta inf
            final Enumeration<? extends TinkerZipEntry> metaEntries = patchZipFile.entries();
            while (metaEntries.hasMoreElements()) {
                TinkerZipEntry zipEntry = metaEntries.nextElement();
                if (zipEntry == null) {
                    throw new TinkerRuntimeException("zipEntry is null when get from patch file.");
                }
                String name = zipEntry.getName();
                TinkerLog.d(TAG, "patch zip file name:%s", name);
                if (name.contains("../")) {
                    continue;
                }
                if (!zipEntry.isDirectory() && name.startsWith(ShareConstants.RES_APK_META_FILES)) {
                    String newName = ShareConstants.RES_APK_META_INF + File.separator + name.substring(name.lastIndexOf("/") + 1);
                    TinkerLog.d(TAG, "zip entry name:%s new name:%s", name, newName);
                    TinkerZipUtil.extractTinkerEntry(patchZipFile, zipEntry, newName, out);
                    zipNameAdded.add(newName);
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
                TinkerLog.d(TAG, "soFilePath:%s soInputPath:%s", soFilePath, soName);
                File soFile = new File(soFilePath);
                if (soFile.exists()) {
                    inputStream = new FileInputStream(soFile);
                    TinkerZipEntry z = new TinkerZipEntry(soName);
                    TinkerZipUtil.extractTinkerEntry(z, inputStream, out);
                    zipNameAdded.add(soName);
                } else {
                    TinkerLog.w(TAG, "can not find so file. path:%s", soFilePath);
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
                TinkerLog.i(TAG, "no tinker_classN.apk find..");
            } else {
                classNZipFile = new TinkerZipFile(classNFile);
                final Enumeration<? extends TinkerZipEntry> dexEntries = classNZipFile.entries();
                while (dexEntries.hasMoreElements()) {
                    TinkerZipEntry zipEntry = dexEntries.nextElement();
                    if (zipEntry == null) {
                        throw new TinkerRuntimeException("zipEntry is null when get from patch file.");
                    }
                    String name = zipEntry.getName();
                    TinkerLog.d(TAG, "classN file name:%s", name);
                    if (name.contains("../")) {
                        continue;
                    }
                    if (!zipEntry.isDirectory() && !name.equalsIgnoreCase(ShareConstants.TEST_DEX_NAME)) {
                        TinkerZipUtil.extractTinkerEntry(classNZipFile, zipEntry, out);
                        zipNameAdded.add(zipEntry.getName());
                    }
                }
            }

            //copy other file
            oldApkZipFile = new TinkerZipFile(oldApkFile);
            final Enumeration<? extends TinkerZipEntry> entries = oldApkZipFile.entries();
            while (entries.hasMoreElements()) {
                TinkerZipEntry zipEntry = entries.nextElement();
                if (zipEntry == null) {
                    throw new TinkerRuntimeException("zipEntry is null when get from oldApk");
                }
                String name = zipEntry.getName();
                if (name.contains("../")) {
                    continue;
                }
                // "res/*", "assets/*", "resources.arsc"
                // copy 除AndroidManifst、dex、lib、meta-inf外的所有方法
                if (!zipNameAdded.contains(name) && !name.equalsIgnoreCase(ShareConstants.RES_MANIFEST)) {
                    TinkerZipUtil.extractTinkerEntry(oldApkZipFile, zipEntry, out);
                    TinkerLog.d(TAG, "copy file to zip. file name:%s", name);
                } else {
                    TinkerLog.d(TAG, "ignore copy file to zip. file name:%s", name);
                }
            }


        } catch (Throwable e) {
            throw new TinkerRuntimeException("apk diff patch failed. " + e.getMessage());
        } finally {
            StreamUtil.closeQuietly(out);
            StreamUtil.closeQuietly(oldApkZipFile);
//            StreamUtil.closeQuietly(newApkZipFile);
            StreamUtil.closeQuietly(patchFile);
            StreamUtil.closeQuietly(inputStream);
            StreamUtil.closeQuietly(classNZipFile);
        }

        TinkerLog.i(TAG, "use time:" + (System.currentTimeMillis() - startTime));

        return true;
    }

}
