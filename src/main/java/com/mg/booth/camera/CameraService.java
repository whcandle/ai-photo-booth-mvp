package com.mg.booth.camera;


import java.nio.file.Path;

public interface CameraService {

    /**
     * Capture a single photo and save to targetFile.
     * Must be blocking (return only after file is written).
     */
    void captureTo(Path targetFile) throws Exception;
}

