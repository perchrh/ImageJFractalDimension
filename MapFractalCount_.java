/* 
 * $Id: MapFractalCount_.java,v 1.36 2005/05/10 15:06:30 perchrh Exp $
 * Estimates the fractal dimension of a 2D greylevel image,
 * interpreted as a topographic surface.
 * This is an implementation of the SDBC algorithm with translations.
 * Free Software in the Public domain.
 * Created by Per Christian Henden and Jens Bache-Wiig
 */

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PlotWindow;
import ij.measure.CurveFitter;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.util.Tools;

import java.util.ArrayList;

public class MapFractalCount_ implements PlugInFilter {

	ImagePlus imRef;

	boolean noGo = false;

	final int autoDiv = 4;

	final int autoMin = 2;

	// User-changeable defaults :
	boolean plotGraph = false;

	boolean verboseOutput = false;

	int maxBox = 24;

	int minBox = 2;

	int numOffsets = 1;

	double zScale = 1.0;

	boolean subGraph = true;

	public int setup(String arg, ImagePlus imp) {
		imRef = imp;
		if (imp == null)
			noGo = true;

		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		getParams();
		if (!noGo) {
			IJ.showStatus("Estimating dimension..");
			IJ.write("Estimating dimension..");
		}

		return DOES_8G + DOES_16 + DOES_32 + SUPPORTS_MASKING + DOES_STACKS;
	}

	private void getParams() {
		GenericDialog gd = new GenericDialog("Calculate fractal dimension");

		gd.addCheckbox("Plot results", plotGraph);
		gd.addCheckbox("Include subgraph (volume)", subGraph);
		gd.addCheckbox("Verbose output", verboseOutput);
		gd.addMessage("");
		gd.addNumericField("Scale z-axis by", zScale, 1);
		gd.addNumericField("Number of translations", numOffsets, 0);

		gd.showDialog();

		if (gd.wasCanceled()) {
			if (imRef != null)
				imRef.unlock();
			noGo = true;
		}

		plotGraph = gd.getNextBoolean();
		subGraph = gd.getNextBoolean();
		verboseOutput = gd.getNextBoolean();
		zScale = gd.getNextNumber();

		numOffsets = (int) gd.getNextNumber();
		if (numOffsets < 1) {
			IJ.write("Number of offsets must be at least 1. Please select another value.");
			noGo = true;
		}

	}

	public void run(ImageProcessor ip) {
		if (noGo)
			return;

		try {

			// Fetch data
			final int width = ip.getWidth();
			final int height = ip.getHeight();

			maxBox = Math.max(width, height) / autoDiv;
			minBox = autoMin;

			if (width <= 0 || height <= 0) {
				IJ.write("\nError: Empty image. Dimension not defined.");
				return;
			}

			float min = Float.MAX_VALUE;
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					float value = ip.getPixelValue(x, y);
					if (value < min)
						min = value;
				}
			}

			// Create variables we need and set them
			int bestCount;
			int count = 0;
			ArrayList<Double> xList = new ArrayList<Double>();
			ArrayList<Double> yList = new ArrayList<Double>();
			int xPos, yPos;
			int xGrid, yGrid;
			int xStart, yStart;
			int xEnd, yEnd;
			double boxZMin, boxZMax;

			for (int boxSize = maxBox; boxSize >= minBox; boxSize--) {

				bestCount = Integer.MAX_VALUE; // Init count for this boxSize
				final int increment = Math.max(1, boxSize / numOffsets);

				for (int gridOffsetX = 0; (gridOffsetX < boxSize)
						&& (gridOffsetX < width); gridOffsetX += increment) {

					for (int gridOffsetY = 0; (gridOffsetY < boxSize)
							&& (gridOffsetY < height); gridOffsetY += increment) {

						count = 0;

						final int iMax = width + gridOffsetX;
						final int jMax = height + gridOffsetY;

						// Iterate over box-grid
						for (int i = 0; i <= iMax; i += boxSize) {
							xGrid = -gridOffsetX + i;
							for (int j = 0; j <= jMax; j += boxSize) {
								yGrid = -gridOffsetY + j;

								xStart = 0;
								if (xGrid < 0) {
									xStart = -xGrid;
								}
								if ((boxSize + xGrid) >= width) {
									xEnd = Math.min(width, (width - xGrid));
								} else {
									xEnd = boxSize;
								}

								yStart = 0;
								if (yGrid < 0) {
									yStart = -yGrid;
								}
								yEnd = boxSize;
								if ((boxSize + yGrid) >= height) {
									yEnd = Math.min(height, (height - yGrid));
								}

								boxZMin = Float.POSITIVE_INFINITY;
								boxZMax = Float.NEGATIVE_INFINITY;
							
								// Inspect box
								for (int x = xStart; x < xEnd; x++) {
									xPos = x + xGrid;

									for (int y = yStart; y < yEnd; y++) {
										yPos = y + yGrid;

										double zValue = zScale * ip.getPixelValue(xPos, yPos);

										if (zValue < boxZMin)
											boxZMin = zValue;
										if (zValue > boxZMax)
											boxZMax = zValue;
									}
								}

								int boxes = 0;

								// If a box is entirely outside image edges,
								// ignore this box
								if ( boxZMax == Float.NEGATIVE_INFINITY)
									continue;
								// Else, calculate box count of the column with
								// base
								// {x=xStart..xEnd,y=yStart..yEnd} based on the
								// SDBC way.
								// The SDBC algorithm is described in
								// "Two algorithms to estimate fractal dimension
								// of gray level images" by Wen-Shiung Chen et.al.
								// Published in "Optical Engineering", Vol 42.
								// No. 8, August 2003.

								if (subGraph) {
									boxes = 1 + (int) ((boxZMax - min + 1) / boxSize);
								} else {
									boxes = 1 + (int) ((boxZMax - boxZMin + 1) / boxSize);

								}
								count += boxes;
							}
						}
						if (count < bestCount) {
							bestCount = count;
						}
					}
				}

				xList.add(new Double((double) boxSize / (double) width));
				yList.add(new Double(bestCount));

				if (verboseOutput) {
					IJ.write("Box count was " + bestCount + " for box size " + boxSize);
				}
			}

			if (xList.size() == 0) {
				IJ.write("\nError: No boxes!\nMake sure that starting and ending box size and "
								+ "\nreduction rate allow for at least one box size to exist!");
				return;
			}

			double[] boxSizes = new double[xList.size()];
			double[] boxCounts = new double[yList.size()];
			for (int i = 0; i < boxSizes.length; i++) {
				boxSizes[i] = -Math.log(((Double) xList.get(i)).doubleValue());
				boxCounts[i] = Math.log(((Double) yList.get(i)).doubleValue());
			}

			if (verboseOutput) {
				IJ.write("Used " + boxSizes.length + " different box sizes, from " 
						+ maxBox + " to " + minBox +
						"with " + numOffsets + " translations of each box.");
			}

			CurveFitter cf = new CurveFitter(boxSizes, boxCounts);
			cf.doFit(CurveFitter.STRAIGHT_LINE);
			double[] p = cf.getParams();
			final String label = imRef.getTitle() + ": Dimension estimate: " + IJ.d2s(p[1], 4);
			IJ.write(label);

			if (plotGraph)
				doPlotGraph(p, boxSizes, boxCounts);

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (imRef != null)
			imRef.unlock();
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
		PlotWindow pw = new PlotWindow("Plot", "-log(box size)", "log(box count)", px, py);
		pw.setLimits(xmin, xmax * 0.9, ymin, ymax * 1.1);
		pw.addPoints(Tools.toFloat(boxSizes), Tools.toFloat(boxCountSums), PlotWindow.CIRCLE);
		final String plotLabel = "Slope: " + IJ.d2s(params[1], 4);
		pw.addLabel(0.25, 0.25, plotLabel);
		pw.draw();
	}

	void showAbout() {
		IJ.showMessage("About MapFractalCount..",
				"This plugin estimates the fractal dimension of maps\n");
	}

}
