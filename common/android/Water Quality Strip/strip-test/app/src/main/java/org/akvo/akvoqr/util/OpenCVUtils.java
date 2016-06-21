package org.akvo.akvoqr.util;

import android.graphics.Color;

import org.akvo.akvoqr.colorimetry_strip.StripTest;
import org.akvo.akvoqr.util.color.ColorDetected;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by linda on 7/23/15.
 */
@SuppressWarnings("HardCodedStringLiteral")
public class OpenCVUtils {

    public static Mat rotateImage(Mat src, RotatedRect rotatedRect, Size brandSize) throws Exception
    {
        Mat rot_mat;
        Mat cropped = new Mat();

        /// Set the dst image the same type and size as src
        // Mat warp_rotate_dst = Mat.zeros(src.rows(), src.cols(), src.type());
        Mat warp_rotate_dst = new Mat(src.rows(), src.cols(), src.type());
        double angle = rotatedRect.angle;
        Size rect_size = rotatedRect.size;
        // thanks to http://felix.abecassis.me/2011/10/opencv-rotation-deskewing/
        // we need to swap height and width if angle is lower than 45 degrees
        if (angle < -45) {
            angle += 90;
            rect_size.set(new double[]{rect_size.height, rect_size.width});
        }
        // get the rotation matrix
        rot_mat = Imgproc.getRotationMatrix2D(rotatedRect.center, angle, 1.0);

        // perform the affine transformation
        Imgproc.warpAffine(src, warp_rotate_dst, rot_mat, src.size(), Imgproc.INTER_CUBIC);

        // crop the resulting image
        if(!warp_rotate_dst.empty()) {
            Point centerBrand = new Point(
                    rotatedRect.center.x + (rotatedRect.size.width - brandSize.width)/2,
                    rotatedRect.center.y - (rotatedRect.size.height - brandSize.height)/4);

            System.out.println("***centerBrand x,y: " + centerBrand.x + ", " + centerBrand.y
                    + " diff width: " + (rotatedRect.size.width - brandSize.width)/2
                    + " diff height: " + (rotatedRect.size.height - brandSize.height)/4);

            Imgproc.getRectSubPix(warp_rotate_dst, brandSize, centerBrand, cropped);
        }
        return cropped;
    }

    /*
    * Computes transform matrix from one set of 4 source points to another set of 4 destination points
    * The points are ordered clockwise
     */
    public static Mat transformMatrix(double[] p1Src, double[] p2Src, double[] p3Src, double[] p4Src, double[] p1Dst, double[] p2Dst, double[] p3Dst, double[] p4Dst){

      //source quad
      Point[] srcQuad = new Point[4];

      //destination quad corresponding with srcQuad
      Point[] dstQuad = new Point[4];

      srcQuad[0] = new Point(p1Src[0],p1Src[1]);
      srcQuad[1] = new Point(p2Src[0],p2Src[1]);
      srcQuad[2] = new Point(p3Src[0],p3Src[1]);
      srcQuad[3] = new Point(p4Src[0],p4Src[1]);

      dstQuad[0] = new Point(p1Dst[0],p1Dst[1]);
      dstQuad[1] = new Point(p2Dst[0],p2Dst[1]);
      dstQuad[2] = new Point(p3Dst[0],p3Dst[1]);
      dstQuad[3] = new Point(p4Dst[0],p4Dst[1]);

      //srcQuad and destQuad to MatOfPoint2f objects, needed in perspective transform
      MatOfPoint2f srcMat2f = new MatOfPoint2f(srcQuad);
      MatOfPoint2f dstMat2f = new MatOfPoint2f(dstQuad);

      //get a perspective transform matrix
      Mat warp_mat = Imgproc.getPerspectiveTransform(srcMat2f, dstMat2f);
      return warp_mat;
    }

    public static Mat perspectiveTransform(double[] topleft, double[] topright,
                                           double[] bottomleft, double[] bottomright, Mat bgr)
            throws Exception {

      // determine the size of the destination Mat: use the positions of the finder patterns to determine the width and height.
      // look out: the horizontal direction now refers again to the actual calibration card
      int verSize = (int) Math.round(Math.sqrt(Math.pow((topleft[0] - topright[0]),2) + Math.pow((topleft[1] - topright[1]),2)));
      int horSize = (int) Math.round(Math.sqrt(Math.pow((topleft[0] - bottomleft[0]),2) + Math.pow((topleft[1] - bottomleft[1]),2)));

      // we rotate the resulting image, so we go from a portrait view to the regular calibration card in landscape
      // so the mapping is:
      // top left source => top right destination
      // top right source => bottom right destination
      // bottom right source => bottom left destination
      // bottom left source => top left destination

      double[] trDest = new double[]{horSize - 1, 0};
      double[] brDest = new double[]{horSize - 1, verSize - 1};
      double[] blDest = new double[]{0, verSize - 1};
      double[] tlDest = new double[]{0,0};

      Mat warp_mat = transformMatrix(topleft, topright, bottomright, bottomleft, trDest, brDest, blDest, tlDest);

      //make a destination mat for a warp
      Mat warp_dst = Mat.zeros(verSize, horSize, bgr.type());

      //do the warp
      Imgproc.warpPerspective(bgr, warp_dst, warp_mat, warp_dst.size());
      return warp_dst;
    }

  // detect strip by multi-step method
  // returns cut-out and rotated resulting strip as mat
    public static Mat detectStrip(Mat striparea, StripTest.Brand brand, double ratioW, double ratioH){
        List<Mat> channels = new ArrayList<>();
        Mat sArea = striparea.clone();

        // Gaussian blurr
        Imgproc.medianBlur(sArea, sArea, 3);
        Core.split(sArea, channels);

        // create binary image
        Mat binary = new Mat();

        // determine min and max NOT USED
        Imgproc.threshold(channels.get(0), binary, 128, 255, Imgproc.THRESH_BINARY);

        // compute first approximation of line through length of the strip
        final WeightedObservedPoints points = new WeightedObservedPoints();
        final WeightedObservedPoints corrPoints = new WeightedObservedPoints();

        double tot, ytot;
        for (int i = 0; i < binary.cols(); i++){ // iterate over cols
            tot = 0;
            ytot = 0;
            for (int j = 0; j < binary.rows(); j++){ // iterate over rows
                if (binary.get(j,i)[0] > 128){
                    ytot += j;
                    tot++;
                }
            }
            if (tot > 0){
                points.add((double) i, ytot / tot);
            }
        }

        // order of coefficients is (b + ax), so [b, a]
        final PolynomialCurveFitter fitter = PolynomialCurveFitter.create(1);
        List<WeightedObservedPoint> pointsList = points.toList();
        final double[] coeff = fitter.fit(pointsList);

        // second pass, remove outliers
        double estimate, actual;

        for (int i = 0; i < pointsList.size(); i++){
            estimate = coeff[1] * pointsList.get(i).getX() + coeff[0];
            actual = pointsList.get(i).getY();
            if (actual > 0.9 * estimate && actual <  1.1 * estimate) { //if the point differs less than +/- 10 %, keep the point
                corrPoints.add(pointsList.get(i).getX(), pointsList.get(i).getY());
            }
        }

        final double[] coeffCorr = fitter.fit(corrPoints.toList());
        double slope = coeffCorr[1];
        double offset = coeffCorr[0];

        // compute rotation angle
        double rotAngleDeg = Math.atan(slope) * 180 / Math.PI;

        //determine a point on the line, in the middle of strip, in the horizontal middle of the whole image
        int midpointx = (int) Math.round(binary.cols() / 2);
        int midpointy = (int) Math.round(midpointx * slope + offset);

        // rotate around the midpoint, to straighten the binary strip
        Mat dstBinary = new Mat(binary.rows(), binary.cols(), binary.type());
        Point center = new Point(midpointx,midpointy);
        Mat rotMat = Imgproc.getRotationMatrix2D(center,rotAngleDeg,1.0);
        Imgproc.warpAffine(binary, dstBinary, rotMat, binary.size(), Imgproc.INTER_CUBIC + Imgproc.WARP_FILL_OUTLIERS);

        // also apply rotation to coloured strip
        Mat dstStrip = new Mat(striparea.rows(), striparea.cols(), striparea.type());
        Imgproc.warpAffine(striparea, dstStrip, rotMat, binary.size(), Imgproc.INTER_CUBIC + Imgproc.WARP_FILL_OUTLIERS);

        // Compute white points in each row
        double[] rowCount = new double[dstBinary.rows()];
        int rowTot;
        for (int i = 0; i < dstBinary.rows(); i++){ // iterate over rows
            rowTot = 0;
            for (int j = 0; j < dstBinary.cols(); j++){ // iterate over cols
                if (dstBinary.get(i,j)[0] > 128){
                    rowTot++;
                }
            }
            rowCount[i] = rowTot;
        }

        // find width by finding rising and dropping edges
        // rising edge  = largest positive difference
        // falling edge = largest negative difference
        int risePos = 0;
        int fallPos = 0;
        double riseVal = 0;
        double fallVal = 0;
        for (int i = 0; i < dstBinary.rows() - 1; i++) {
            if (rowCount[i + 1] - rowCount[i] > riseVal) {
                riseVal = rowCount[i + 1] - rowCount[i];
                risePos = i + 1;
            }
            if (rowCount[i + 1] - rowCount[i] < fallVal) {
                fallVal = rowCount[i + 1] - rowCount[i];
                fallPos = i;
            }
        }

        // cut out binary strip
        Point stripTopLeft = new Point(0,risePos);
        Point stripBottomRight = new Point(dstBinary.cols(),fallPos);

        org.opencv.core.Rect stripArea = new org.opencv.core.Rect(stripTopLeft, stripBottomRight);
        Mat binaryStrip = dstBinary.submat(stripArea);

        // also cut out coloured strip
        Mat colourStrip = dstStrip.submat(stripArea);

        // now right end of strip
        // method: first rising edge

        double[] colCount = new double[binaryStrip.cols()];
        int colTot;
        for (int i = 0; i < binaryStrip.cols(); i++){ // iterate over cols
            colTot = 0;
            for (int j = 0; j < binaryStrip.rows(); j++){ // iterate over rows
                if (binaryStrip.get(j,i)[0] > 128){
                    colTot++;
                }
            }
            colCount[i] = colTot;
        }

        // treshold is that half of the rows in a column should be white
        int treshold = Math.round(binaryStrip.rows() / 2);

        // moving from the right, determine the first point that crosses the treshold
        boolean found = false;
        int posRight = binaryStrip.cols() - 1;
        while(!found && posRight > 0){
            if (colCount[posRight] > treshold){
                found = true;
            } else {
                posRight--;
            }
        }

        // moving from the left, determine the first point that crosses the treshold
//        found = false;
//        int posLeft = 0;
//        while(!found && posLeft < binaryStrip.cols() - 1){
//            if (colCount[posLeft] > treshold){
//                found = true;
//            } else {
//                posLeft++;
//            }
//        }

        // use known length of strip to determine left side
        int length = (int) Math.round(brand.getStripLenght()*ratioW);
        int posLeft = posRight - length;

        // cut out final strip
        stripTopLeft = new Point(posLeft,0);
        stripBottomRight = new Point(posRight,binaryStrip.rows());
        stripArea = new org.opencv.core.Rect(stripTopLeft, stripBottomRight);
        Mat resultStrip = colourStrip.submat(stripArea).clone();

        // release Mat objects
        striparea.release();
        sArea.release();
        binary.release();
        dstBinary.release();
        dstStrip.release();
        binaryStrip.release();
        colourStrip.release();

        // sanity check: the strip should be at least larger than half of the black area
        if (Math.abs(posRight - posLeft) < binaryStrip.cols() * 0.5){
            return null;
        }

        return resultStrip;
    }


    public static ColorDetected detectStripColorBrandKnown(Mat lab)
    {
        // compute mean lab colour. This is the value that will be
        // used for the ppm computation
        Scalar mean = Core.mean(lab);
        ColorDetected colorDetected = new ColorDetected(0);
        colorDetected.setLab(mean);

        // compute rgb colour. This will be used for display only.
        Mat rgb = new Mat();
        Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB);
        mean = Core.mean(rgb);
        colorDetected.setRgb(mean);

        int color = Color.rgb((int)Math.round(mean.val[0]),(int)Math.round(mean.val[1]),
                (int)Math.round(mean.val[2]));

        colorDetected.setColor(color);
//        rgb.release();
        return colorDetected;
    }

    public static int getMinX(List<Point> list)
    {
        int min = Integer.MAX_VALUE;
        for(Point p: list){
            if(p.x < min)
                min = (int) Math.round(p.x);
        }

        return min;
    }
    public static int getMaxX(List<Point> list)
    {
        int max = Integer.MIN_VALUE;
        for(Point p: list){
            if(p.x>max)
                max = (int) Math.round(p.x);
        }

        return max;
    }
    public static int getMinY(List<Point> list)
    {
        int min = Integer.MAX_VALUE;
        for(Point p: list){
            if(p.y < min)
                min = (int) Math.round(p.y);
        }

        return min;
    }
    public static int getMaxY(List<Point> list)
    {
        int max = Integer.MIN_VALUE;
        for(Point p: list){
            if(p.y>max)
                max = (int) Math.round(p.y);
        }

        return max;
    }
}
