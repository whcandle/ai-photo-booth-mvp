package com.mg.booth.camera;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_DSHOW;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH;

@Service
public class UsbCameraService implements CameraService {

    private static final int CAMERA_INDEX = 0;

    @Override
    public void captureTo(Path targetFile) throws Exception {
        // 确保目录存在
        Files.createDirectories(targetFile.getParent());

        VideoCapture camera = null;
        Mat frame = new Mat();

        try {
            // Windows 下用 DirectShow 更稳
            camera = new VideoCapture(CAMERA_INDEX, CAP_DSHOW);

            if (!camera.isOpened()) {
                throw new RuntimeException("Camera open failed (index=" + CAMERA_INDEX + ")");
            }

            // 可选：设置分辨率
            camera.set(CAP_PROP_FRAME_WIDTH, 1280);
            camera.set(CAP_PROP_FRAME_HEIGHT, 720);

            // 给摄像头一点启动时间（非常重要）
            Thread.sleep(300);

            boolean ok = camera.read(frame);
            if (!ok || frame.empty()) {
                throw new RuntimeException("Failed to read frame from camera");
            }

            boolean saved = imwrite(targetFile.toAbsolutePath().toString(), frame);
            if (!saved) {
                throw new RuntimeException("Failed to write image: " + targetFile);
            }

        } finally {
            // 释放资源（服务端一定要做）
            if (camera != null) {
                camera.release();
            }
            frame.release();
        }
    }
}
