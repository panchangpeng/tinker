package com.tencent.tinker.ziputils.zioutil;

/**
 * Created by cpan on 2017/12/19.
 */

public class ZipAlign {


    public native int process(String srcPath, String outPath, int alignment, boolean force);

    public native int verify(String srcPath, int alignment, boolean verbose);


}
