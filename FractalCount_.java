/*  $Id: FractalCount_.java,v 1.42 2005/05/30 07:52:59 perchrh Exp $
 Estimates the fractal dimension of 2D and 3D binary images.
 
  Free Software in the Public domain. 
  Created by Per Christian Henden and Jens Bache-Wiig

 */

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.measure.CurveFitter;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.util.Tools;

import java.util.ArrayList;
import java.util.List;

public class FractalCount_ implements PlugInFilter {

    private ImagePlus imRef;

    private boolean noGo = false;

    private final static int AUTO_DIV = 4;

    // User-changeable defaults:
    private boolean plotGraph = true;

    private boolean verboseOutput = false;

    private int threshold = 70;

    private int maxBox = 24;

    private static final int DEFAULT_MIN_BOX = 6;

    private int minBox = DEFAULT_MIN_BOX;

    private double divBox = 1.2;

    private int numOffsets = 3;

    private boolean autoParam = true;

    public int setup(String arg, ImagePlus imp) {
        imRef = imp;

        if (arg.equals("about")) {
            showAbout();
            return DONE;
        }

        getParams();

        return DOES_8G;
    }

    private void getParams() {
        GenericDialog gd = new GenericDialog("Calculate fractal dimension");

        gd.addCheckbox("Plot results", plotGraph);
        gd.addCheckbox("Verbose output", verboseOutput);
        gd.addCheckbox("Automatic start box size", autoParam);
        gd.addMessage("");
        gd.addNumericField("Threshold", threshold, 0);
        gd.addNumericField("Start box size", maxBox, 0);
        gd.addNumericField("Min box size", minBox, 0);
        gd.addNumericField("Box division factor", divBox, 1);
        gd.addNumericField("Number of translations", numOffsets, 0);

        gd.showDialog();

        if (gd.wasCanceled()) {
            if (imRef != null) {
                imRef.unlock();
                imRef = null;
            }
            noGo = true;
        }

        plotGraph = gd.getNextBoolean();
        verboseOutput = gd.getNextBoolean();
        autoParam = gd.getNextBoolean();

        threshold = (int) gd.getNextNumber();
        maxBox = (int) gd.getNextNumber();
        minBox = (int) gd.getNextNumber();
        divBox = gd.getNextNumber();
        numOffsets = (int) gd.getNextNumber();
        if (numOffsets < 1) {
            IJ.log("Number of offsets must be at least 1. Please select another value");
            noGo = true;
        }
    }

    public void run(ImageProcessor ip) {
        if (noGo) {
            return;
        }

        // Fetch data
        final int width = ip.getWidth();
        final int height = ip.getHeight();
        final int depth = imRef.getStackSize();

        if (width <= 0 || height <= 0 || depth <= 0) {
            IJ.log("\nNo black pixels in image. Dimension not defined."
                    + "\nThis can be caused by an empty image or a"
                    + "\n wrong threshold value.");
            return;
        }

        if (autoParam) {
            maxBox = Math.max(width, Math.max(height, depth)) / AUTO_DIV;
            minBox = Math.min(DEFAULT_MIN_BOX, maxBox);
            if (verboseOutput) {
                IJ.log("Automatic max box size " + maxBox + " selected");
            }
        }

        IJ.showStatus("Estimating dimension..");

        // Start timer
        long startTime = System.currentTimeMillis();

        // Do the box count
        List<Long> xList = new ArrayList<Long>();
        List<Long> yList = new ArrayList<Long>();
        doBoxCount(width, height, depth, xList, yList);

        if (verboseOutput) {
            IJ.log("\nTime used: "
                    + (System.currentTimeMillis() - startTime) / 1000.0
                    + "seconds \n");
        }

        // Prepare estimation of fractal dimension
        double[] boxSizes = new double[xList.size()];
        double[] boxCountSums = new double[yList.size()];
        for (int i = 0; i < boxSizes.length; i++) {
            boxSizes[i] = -Math.log(xList.get(i));
            boxCountSums[i] = Math.log(yList.get(i));
        }

        if (verboseOutput) {
            IJ.log("Used " + boxSizes.length
                    + " different box sizes, from " + maxBox + " to "
                    + minBox);
            IJ.log("with a reduction rate of " + divBox + " and "
                    + numOffsets + " translations of each box.");
        }

        if (boxSizes.length == 0) {
            IJ.log("\nError: No boxes!\nMake sure that starting and ending box size and "
                    + "\nreduction rate allow for at least one box size to exist!");
            return;
        }

        if (plotGraph) {
            CurveFitter cf = new CurveFitter(boxSizes, boxCountSums);
            cf.doFit(CurveFitter.STRAIGHT_LINE);
            double[] p = cf.getParams();
            final String label = imRef.getTitle()
                    + ": Dimension estimate: " + IJ.d2s(p[1], 4)
                    + ": Settings: " + maxBox + ":" + minBox + ":" + divBox
                    + ":" + numOffsets;
            IJ.log(label);

            doPlotGraph(p, boxSizes, boxCountSums);
        }


        if (imRef != null) {
            imRef.unlock();
        }
    }

    private void doBoxCount(int width, int height, int depth, List<Long> boxSizes, List<Long> boxCounts) {
        long bestCount; // keeps track of best count so far
        long count = 0; // current count
        int xPos, yPos, zPos, yPart;
        int xGrid, yGrid, zGrid;
        int xStart, yStart, zStart;
        int xEnd, yEnd, zEnd;

        for (int boxSize = maxBox; boxSize >= minBox; boxSize /= divBox) {
            if (verboseOutput) {
                String message = "Current boxsize: " + boxSize + " x " + boxSize;
                if (depth > 1) {
                    message += " x " + boxSize;
                }
                IJ.log(message);
            }

            bestCount = Long.MAX_VALUE; // initial count for this boxSize

            final int increment = Math.max(1, boxSize / numOffsets);

            for (int gridOffsetX = 0; (gridOffsetX < boxSize)
                    && (gridOffsetX < width); gridOffsetX += increment) {

                for (int gridOffsetY = 0; (gridOffsetY < boxSize)
                        && (gridOffsetY < height); gridOffsetY += increment) {

                    for (int gridOffsetZ = 0; (gridOffsetZ < boxSize)
                            && (gridOffsetZ < depth); gridOffsetZ += increment) {

                        count = 0;

                        final int iMax = width + gridOffsetX;
                        final int jMax = height + gridOffsetY;
                        final int kMax = depth + gridOffsetZ;

                        // Iterate over box-grid
                        for (int i = 0; i <= iMax; i += boxSize) {
                            xGrid = -gridOffsetX + i;
                            for (int j = 0; j <= jMax; j += boxSize) {
                                yGrid = -gridOffsetY + j;
                                for (int k = 0; k <= kMax; k += boxSize) {
                                    zGrid = -gridOffsetZ + k;

                                    xStart = 0;
                                    if (xGrid < 0) {
                                        xStart = -xGrid;
                                    }
                                    if ((boxSize + xGrid) >= width) {
                                        xEnd = Math.min(width, width - xGrid);
                                    } else {
                                        xEnd = boxSize;
                                    }

                                    yStart = 0;
                                    if (yGrid < 0) {
                                        yStart = -yGrid;
                                    }
                                    if ((boxSize + yGrid) >= height) {
                                        yEnd = Math.min(height, height - yGrid);
                                    } else {
                                        yEnd = boxSize;
                                    }

                                    zStart = 0;
                                    if (zGrid < 0) {
                                        zStart = -zGrid;
                                    }
                                    if ((boxSize + zGrid) >= depth) {
                                        zEnd = Math.min(depth, depth - zGrid);
                                    } else {
                                        zEnd = boxSize;
                                    }

                                    for (int x = xStart; x < xEnd; x++) {
                                        xPos = x + xGrid;

                                        for (int y = yStart; y < yEnd; y++) {
                                            yPos = (y + yGrid);
                                            yPart = yPos * width;

                                            for (int z = zStart; z < zEnd; z++) {
                                                zPos = z + zGrid;

                                                // If there is a foreground pixel inside the box, count the box
                                                int pixelValue = 0xff & ((byte[]) imRef
                                                        .getStack()
                                                        .getPixels(zPos + 1))[xPos + yPart];
                                                if (pixelValue >= threshold) {
                                                    count++;
                                                    z = x = y = boxSize; // stop looking through this box
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (count < bestCount) {
                            bestCount = count;
                        }

                    }
                }
            }
            boxSizes.add((long) boxSize);
            boxCounts.add(bestCount);
        }
    }

    void doPlotGraph(double[] params, double[] boxSizes, double[] boxCountSums) {
        final int samples = 100;
        float[] px = new float[samples];
        float[] py = new float[samples];
        double[] a = Tools.getMinMax(boxSizes);
        double xmin = a[0], xmax = a[1];

        a = Tools.getMinMax(boxCountSums);
        double ymin = a[0], ymax = a[1];
        final double inc = (xmax - xmin) / ((double) samples - 1);
        double tmp = xmin;

        for (int i = 0; i < samples; i++) {
            px[i] = (float) tmp;
            tmp += inc;
        }
        for (int i = 0; i < samples; i++) {
            py[i] = (float) CurveFitter.f(CurveFitter.STRAIGHT_LINE, params, px[i]);
        }
        a = Tools.getMinMax(py);
        ymin = Math.min(ymin, a[0]);
        ymax = Math.max(ymax, a[1]);

        Plot plot = new Plot("Plot", "-log(box size)", "log(box count)", px, py);
        plot.setLimits(xmin, xmax * 0.9, ymin, ymax * 1.1);
        plot.addPoints(Tools.toFloat(boxSizes), Tools.toFloat(boxCountSums), PlotWindow.CIRCLE);
        plot.addLabel(0.25, 0.25, "Slope: " + IJ.d2s(params[1], 4));
        plot.draw();
    }

    void showAbout() {
        IJ.showMessage(
                "About FractalCount..",
                "This plugin calculates the boxing dimension (an estimate of the fractal dimension) \n "
                        + " of 2D and 3D images\n.");
    }

}
