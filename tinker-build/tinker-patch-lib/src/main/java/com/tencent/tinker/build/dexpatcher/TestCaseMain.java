package com.tencent.tinker.build.dexpatcher;

import com.tencent.tinker.build.util.MD5;
import com.tencent.tinker.commons.dexpatcher.DexPatchApplier;

import java.io.File;

/**
 * Created by cpan on 2018/1/11.
 */

public class TestCaseMain {

    public static void main(String[] args) {

        try {
            File dexDir = new File("/Users/cpan/Downloads/dx_testcases_out");
            File[] dexList = dexDir.listFiles();

            int size = dexList.length;
            int index = 0;
            int successCount = 0;
            int errorCount = 0;
            for (int i = 0; i < size - 1; i++) {
                for (int j = i + 1; j < size; j++) {
                    index++;
//                    System.out.println(index + " ->" + i + ":" + dexList[i].getName() + "  " + j + ":" + dexList[j].getName());
                    File out = new File("/Users/cpan/Downloads/dex_hot_patch/" + index);
                    new DexPatchGenerator(dexList[i], dexList[j]).executeAndSaveTo(out);

                    File result = new File("/Users/cpan/Downloads/dex_hot_patch_result/" + dexList[j].getName());
                    new DexPatchApplier(dexList[i], out).executeAndSaveTo(result);

                    String oldMd5 = MD5.getMD5(dexList[j]);
                    String newMd5 = MD5.getMD5(result);
                    if (oldMd5.equalsIgnoreCase(newMd5)) {
                        System.out.println(result.getName() + "old md5:" + oldMd5 + "  new md5:" + newMd5);
                        successCount++;
                    } else {
                        System.err.println(result.getName() + "old md5:" + oldMd5 + "  new md5:" + newMd5);
                        errorCount++;
                    }

                }
            }
            System.err.println("hot patch success count:" + successCount + "  error count:" + errorCount);

        } catch (Exception e) {

            System.err.println(e.getMessage());

        }


//        String a = "/Users/cpan/Downloads/dalvik-a9ac3a9d1f8de71bcdc39d1f4827c04a952a0c29-dx-tests.tar/044-dex-math-ops/44reBlort.dex";
//        String b = "/Users/cpan/Downloads/dalvik-a9ac3a9d1f8de71bcdc39d1f4827c04a952a0c29-dx-tests.tar/044-dex-math-ops/45reBlort.dex";
//
//        String c = "/Users/cpan/Downloads/dalvik-a9ac3a9d1f8de71bcdc39d1f4827c04a952a0c29-dx-tests.tar/044-dex-math-ops/Blort_HOTPATCH_re.dex";
//        try {
//            new DexPatchGenerator(new File(a), new File(b)).executeAndSaveTo(new File(c));
//        } catch (Exception e) {
//            System.err.println(e.getMessage());
//        }
    }
}
