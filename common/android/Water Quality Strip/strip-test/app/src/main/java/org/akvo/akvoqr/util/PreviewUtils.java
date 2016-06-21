package org.akvo.akvoqr.util;

import org.akvo.akvoqr.util.calibration.CalibrationCard;
import org.akvo.akvoqr.util.calibration.CalibrationData;
import org.akvo.akvoqr.util.detector.FinderPatternInfo;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by linda on 10/3/15.
 */
public class PreviewUtils {
    public static double[] getDiffLuminosity(Mat src_gray)
    {
        //find min and max luminosity
        Core.MinMaxLocResult result = Core.minMaxLoc(src_gray);
        return new double[]{ result.minVal, result.maxVal};
    }

    /*method for shadow detection
   * @param Mat : a 'cut-out' of the test card between the centers of the finder patterns.
   * @return :  percentage of the points that deviate more than @link Constant.CONTRAST_DEVIATION_PERCENTAGE from the average luminosity
   *  points with luminosity with a larger difference than Constant.CONTRAST_MAX_DEVIATION_PERCENTAGE count 10 times in the result.
    */
    public static double getShadowPercentage(Mat bgr, CalibrationData data) {

        double sumLum = 0;
        int countDev = 0;
        int countMaxDev = 0;
        double deviation;

        Mat lab = new Mat();
        Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab);

        double[][] points = CalibrationCard.createWhitePointArray(lab, data);

        //get the sum total of luminosity values
        for(int i=0; i< points.length; i++) {
            sumLum += points[i][2];
        }

        double avgLum = sumLum / points.length;
        double avgLumReciproc = 1.0 / avgLum;

        for(int i=0; i < points.length; i++) {
            double lum = points[i][2];
            deviation = Math.abs(lum - avgLum) * avgLumReciproc;

            // count number of points that differ more than CONTRAST_DEVIATION_FRACTION from the average
            if(deviation > Constant.CONTRAST_DEVIATION_FRACTION)
            {
                countDev++;
            }

            // count number of points that differ more than CONTRAST_MAX_DEVIATION_FRACTION from the average
            if(deviation > Constant.CONTRAST_MAX_DEVIATION_FRACTION)
            {
                countMaxDev++;
            }
        }

        // the countMaxDev is already counted once in countDev. The following formula
        // lets points that are way off count 10 times as heavy in the result.
        // maximise to 100%
        double result = Math.min(countDev + 9 * countMaxDev,points.length);

        lab.release();
        return (result / points.length) * 100.0;
    }

    private static float distance(double x1, double y1, double x2, double y2){
      return (float) Math.sqrt(Math.pow(x2 - x1,2) + Math.pow(y2 - y1,2));
    }

    //method to calculate the amount of perspective, based on the difference of distances at the top and sides
    // horizontal and vertical are according to calibration card in landscape view
    public static float[] getTilt(FinderPatternInfo info)
    {
      if(info==null) {
        return null;
      }

      // compute distances
      // in info, we have topleft, topright, bottomleft, bottomright
      float distHtop = distance(info.getBottomLeft().getX(),info.getBottomLeft().getY(),info.getTopLeft().getX(),info.getTopLeft().getY());
      float distHbot = distance(info.getBottomRight().getX(),info.getBottomRight().getY(),info.getTopRight().getX(),info.getTopRight().getY());
      float distVleft = distance(info.getBottomLeft().getX(),info.getBottomLeft().getY(),info.getBottomRight().getX(),info.getBottomRight().getY());
      float distVright = distance(info.getTopRight().getX(),info.getTopRight().getY(),info.getTopLeft().getX(),info.getTopLeft().getY());

      // return ratio of horizontal distances top and bottom and ratio of vertical distances left and right
      return new float[]{distHtop/distHbot,distVleft/distVright};
    }

    public static String fromSecondsToMMSS(int seconds) throws Exception
    {
        if(seconds>3600)
            throw new Exception("more than an hour");

        int m = (int)Math.floor(seconds/60);
        int s = seconds - (m * 60);

        String mm = m>0? String.format("%2d", m) + ":": "";
        String ss = m>0? String.format("%02d",s): String.format("%2d",s);

        return mm + ss;
    }
}
