package com.mg.booth.camera;


import java.nio.file.Path;

public interface CameraService {

    /**
     * Capture a single photo and save to targetFile.
     * Must be blocking (return only after file is written).
     */
    void captureTo(Path targetFile) throws Exception;

    /**
     * Get camera service status.
     * Returns null if status check fails.
     */
    CameraStatus getStatus() throws Exception;

    /**
     * Camera service status DTO.
     */
    class CameraStatus {
        public boolean ok;
        public boolean cameraConnected;
        public String error;
        public Integer cameraThreadId;
        public String apartmentState;
        public Integer queueLength;
        public Boolean sdkInitialized;
        public Boolean sessionOpened;
    }
}

