package com.mg.booth.camera;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class UsbCameraService implements CameraService {

    private static final int CAMERA_INDEX = 0;

    static {
        // ✅ 强制加载 native DLL（不依赖 IDEA 的 Libraries 设置）
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.out.println("OpenCV native loaded, version=" + Core.VERSION
                + ", lib=" + Core.NATIVE_LIBRARY_NAME);
    }

    @Override
    public void captureTo(Path targetFile) throws Exception {
        Files.createDirectories(targetFile.getParent());

        VideoCapture camera = new VideoCapture();
        try {
            if (!camera.open(CAMERA_INDEX)) {
                throw new RuntimeException("Camera open failed (index=" + CAMERA_INDEX + ")");
            }

            Thread.sleep(500);

            Mat frame = new Mat();
            boolean ok = camera.read(frame);
            if (!ok || frame.empty()) {
                throw new RuntimeException("Failed to read frame from camera");
            }

            boolean saved = Imgcodecs.imwrite(targetFile.toAbsolutePath().toString(), frame);
            if (!saved) {
                throw new RuntimeException("Failed to write image: " + targetFile);
            }
        }catch (Exception e){
            System.out.println("captureTo exception:"+e.getMessage());
        }
        finally {
            camera.release();
        }
    }
}
