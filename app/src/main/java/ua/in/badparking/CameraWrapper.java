package ua.in.badparking;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;

import java.util.List;

@SuppressWarnings("deprecation")
public class CameraWrapper {

    private static final String TAG = CameraWrapper.class.getSimpleName();
    private Activity activity;
    private Camera camera;
    private SurfaceCamCallback surfaceCamCallback;

    public CameraWrapper(Activity activity) {
        this.activity = activity;
        camera = getCameraInstance();
        surfaceCamCallback = new SurfaceCamCallback(camera);
    }

    private Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera: " + e.getMessage());
        }
        return c;
    }

    private void setFlashMode(Camera camera, boolean enabled) {
        if (activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            Camera.Parameters parameters = camera.getParameters();
            List<String> mSupportedFlashModes = parameters.getSupportedFlashModes();
            if (mSupportedFlashModes != null && mSupportedFlashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                if (enabled) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                } else {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                }
                camera.setParameters(parameters);
            }
        }
    }

    private void setFocusMode(Camera camera, boolean enabled) {
        if (activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            Camera.Parameters parameters = camera.getParameters();
            List<String> mSupportedFocusModes = parameters.getSupportedFocusModes();
            if (mSupportedFocusModes != null) {
                if (mSupportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
                camera.setParameters(parameters);
            }
        }
    }

    private void setPreviewParameters(Camera camera, int width, int height) {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();

        Camera.Size previewSize = getOptimalPreviewSize(sizes, Math.max(width, height), Math.min(width, height));
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        parameters.setPreviewFormat(ImageFormat.NV21);
        setAcceptableFrameRate(parameters);
        camera.setParameters(parameters);
    }

    private void setCameraOrientation(Camera camera, CameraOrientation cameraOrientation) {
        Camera.Parameters parameters = camera.getParameters();
        switch (cameraOrientation) {
            case PORTRAIT:
                parameters.set("orientation", "portrait");
                break;
            case LANDSCAPE:
                parameters.set("orientation", "landscape");
                break;
        }
        camera.setParameters(parameters);
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) targetWidth / targetHeight;
        if (sizes == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        return optimalSize;
    }

    private void setAcceptableFrameRate(Camera.Parameters params) {
        List<int[]> ranges = params.getSupportedPreviewFpsRange();
        int[] frameRate = {0, 0};
        for (int[] range : ranges) {
            if (range[0] > frameRate[0]) {
                frameRate[0] = range[0];
                frameRate[1] = range[1];
            }
        }
        params.setPreviewFpsRange(frameRate[0], frameRate[1]);
    }

    public enum CameraOrientation {
        PORTRAIT, LANDSCAPE
    }

    public Camera.Size getCameraContainerSize() {
        Camera.Parameters parameters = camera.getParameters();
        Display display = activity.getWindowManager().getDefaultDisplay();
        List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
        return getOptimalPreviewSize(sizes, display.getWidth(), display.getHeight());
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    private class SurfaceCamCallback implements SurfaceHolder.Callback, Camera.PreviewCallback {
        private Camera camera;

        public SurfaceCamCallback(Camera camera) {
            this.camera = camera;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            //do nothing
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (camera != null && holder != null) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                try {
                    // restart camera preview
                    configureCamera(width, height);
                    camera.setDisplayOrientation(90);
                    camera.setPreviewDisplay(holder);
                    camera.setPreviewCallback(this);
                    camera.startPreview();
                } catch (Exception e) {
                    Log.e(TAG, "Exception raised configuring camera: " + e.getMessage());
                }
            }
        }

        private void configureCamera(int width, int height) {
            if (camera == null) {
                return;
            }

            setCameraOrientation(camera, CameraWrapper.CameraOrientation.PORTRAIT);
            setPreviewParameters(camera, width, height);
            setFocusMode(camera, true);
            setFlashMode(camera, true);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (camera != null) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
        }
    }

    public SurfaceCamCallback getSurfaceCamCallback() {
        return surfaceCamCallback;
    }

    public void setSurfaceCamCallback(SurfaceCamCallback surfaceCamCallback) {
        this.surfaceCamCallback = surfaceCamCallback;
    }
}