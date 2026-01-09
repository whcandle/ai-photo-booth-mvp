package com.mg.booth.api;

import org.opencv.core.Core;

public class OpenCvCheck {
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    public static void main(String[] args) {
        System.out.println("OpenCV loaded. version=" + Core.VERSION);
    }
}
