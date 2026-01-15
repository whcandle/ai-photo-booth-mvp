package com.mg.booth.api;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.bytedeco.opencv.global.opencv_highgui.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_videoio.*;

/**
 * test camera capture using OpenCV
 */
public class CameraCapture {

    public static void main(String[] args) {
        // 0 表示默认摄像头；如果你有多个摄像头可试 1、2...
        int cameraIndex = 0;

        // 用 DirectShow 后端在 Windows 上通常更稳（可选）
        VideoCapture cap = new VideoCapture(cameraIndex, CAP_DSHOW);

        if (!cap.isOpened()) {
            System.err.println("❌ 打不开摄像头，检查：是否被占用/权限/索引是否正确。");
            return;
        }

        // 可选：设置分辨率
        cap.set(CAP_PROP_FRAME_WIDTH, 1280);
        cap.set(CAP_PROP_FRAME_HEIGHT, 720);

        // 创建窗口
        String windowName = "Camera Preview - [SPACE] Capture, [ESC] Exit";
        namedWindow(windowName, WINDOW_AUTOSIZE);

        Mat frame = new Mat();

        System.out.println("✅ 摄像头已打开。按空格拍照，按 ESC 退出。");

        while (true) {
            // 读取一帧
            if (!cap.read(frame) || frame.empty()) {
                System.err.println("⚠️ 读取画面失败。");
                break;
            }

            // 显示预览
            imshow(windowName, frame);

            // 等待按键（单位 ms），这里 20ms 让画面流畅
            int key = waitKey(20);

            // ESC 退出（ESC 通常是 27）
            if (key == 27) {
                System.out.println("👋 退出。");
                break;
            }

            // 空格拍照（space ASCII=32）
            if (key == 32) {
                String filename = "photo_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";
                boolean ok = imwrite(filename, frame);
                if (ok) {
                    System.out.println("📸 已保存：" + filename);
                } else {
                    System.err.println("❌ 保存失败：" + filename);
                }
            }
        }

        // 释放资源
        cap.release();
        destroyAllWindows();
    }
}
