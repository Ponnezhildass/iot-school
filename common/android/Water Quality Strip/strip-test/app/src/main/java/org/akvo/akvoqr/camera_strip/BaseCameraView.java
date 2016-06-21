package org.akvo.akvoqr.camera_strip;

import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.akvo.akvoqr.util.detector.CameraConfigurationUtils;

import java.io.IOException;
import java.util.List;

/**
 * Created by linda on 7/7/15.
 */
public class BaseCameraView extends SurfaceView implements SurfaceHolder.Callback{

    private final Camera mCamera;
    private CameraActivity activity;
    private Camera.Parameters parameters;

    public BaseCameraView(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        try {
            activity = (CameraActivity) context;
        }
        catch (ClassCastException e)
        {
            throw new ClassCastException("must have CameraActivity as Context.");
        }
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        /* A basic Camera preview class */
        SurfaceHolder mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);

        } catch (Exception e) {
            Log.d("", "Error setting camera preview: " + e.getMessage());
        }
    }


    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (holder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        if(mCamera == null)
        {
            //Camera was released
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        try {
            parameters = mCamera.getParameters();
        }
        catch (Exception e)
        {
            e.printStackTrace();

        }
        if(parameters == null)
        {
            return;
        }


        Camera.Size bestSize = null;
        List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
        int maxWidth = 0;
        for(Camera.Size size: sizes) {
            System.out.println("***supported preview sizes w, h: " + size.width + ", " + size.height);
            if(size.width>1300)
                continue;
            if (size.width > maxWidth) {
                bestSize = size;
                maxWidth = size.width;
            }
        }

        //portrait mode
        mCamera.setDisplayOrientation(90);

        //preview size
       // System.out.println("***best preview size w, h: " + bestSize.width + ", " + bestSize.height);
        assert bestSize != null;
        parameters.setPreviewSize(bestSize.width, bestSize.height);

        //parameters.setPreviewFormat(ImageFormat.NV21);

        boolean canAutoFocus = false;
        boolean disableContinuousFocus = true;
        List<String> modes = mCamera.getParameters().getSupportedFocusModes();
        for(String s: modes) {

            System.out.println("***supported focus modes: " + s);

            if(s.equals(Camera.Parameters.FOCUS_MODE_AUTO))
            {
                canAutoFocus = true;

            }
            if(s.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            {
                disableContinuousFocus = false;
            }
        }

        try {
            CameraConfigurationUtils.setFocus(parameters, canAutoFocus, disableContinuousFocus, false);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        //white balance
        if(parameters.getWhiteBalance()!=null)
        {
            //TODO check if this optimise the code
            parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        }

        try {

            mCamera.setParameters(parameters);

        } catch (Exception e){
            Log.d("", "Error setting camera parameters: " + e.getMessage());
        }

        try {
            mCamera.setPreviewDisplay(holder);
            activity.setPreviewProperties();
            mCamera.startPreview();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void switchFlashMode()
    {
        if(mCamera==null)
            return;
        parameters = mCamera.getParameters();

        String flashmode = mCamera.getParameters().getFlashMode().equals(Camera.Parameters.FLASH_MODE_OFF)?
                Camera.Parameters.FLASH_MODE_TORCH: Camera.Parameters.FLASH_MODE_OFF;
        parameters.setFlashMode(flashmode);

        mCamera.setParameters(parameters);
    }

    //exposure compensation
    public void adjustExposure(int direction) throws RuntimeException
    {
        if(mCamera==null)
            return;

        //parameters = mCamera.getParameters();
        mCamera.cancelAutoFocus();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {

                if(!parameters.getAutoExposureLock()) {
                    parameters.setAutoExposureLock(true);
                    mCamera.setParameters(parameters);
                    System.out.println("***locking auto-exposure. ");
                }
            }


        int compPlus = Math.min(parameters.getMaxExposureCompensation(), Math.round(parameters.getExposureCompensation() + 1));
        int compMinus = Math.max(parameters.getMinExposureCompensation(), Math.round(parameters.getExposureCompensation() - 1));

        if(direction > 0)
        {
            parameters.setExposureCompensation(compPlus);
        }
        else if(direction < 0)
        {
            parameters.setExposureCompensation(compMinus);
        }
        else if(direction == 0) {
            parameters.setExposureCompensation(0);
        }

        //System.out.println("***Exposure compensation index: " + parameters.getExposureCompensation());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {

            if(parameters.getAutoExposureLock()) {
                parameters.setAutoExposureLock(false);
                mCamera.setParameters(parameters);
                System.out.println("***unlocking auto-exposure. ");
            }
        }else {

            mCamera.setParameters(parameters);
        }
    }

    public void setFocusAreas(List<Camera.Area> areas)
    {
        if(mCamera==null)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {

            if(parameters.getMaxNumFocusAreas() > 0 && areas != null && areas.size() > 0) {
                try {
                    //make sure area list does not exceed max num areas allowed
                    int length = Math.min(areas.size(), mCamera.getParameters().getMaxNumFocusAreas());
                    List<Camera.Area> subAreas = areas.subList(0, length);

                    mCamera.cancelAutoFocus();

                    //parameters = mCamera.getParameters();
                    parameters.setFocusAreas(subAreas);
                    mCamera.setParameters(parameters);

                } catch (Exception e) {
                    System.out.println("***Exception setting parameters for focus areas.");
                    e.printStackTrace();

                }
            }
        }
    }
}

