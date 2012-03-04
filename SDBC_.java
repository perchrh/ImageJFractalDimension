/* 
 * $Id: SDBC_.java,v 1.8 2005/07/05 12:39:42 perchrh Exp $
 * SDBC version 0.90. 
 * Estimates the fractal dimension of a 2D greylevel image,
 * interpreted as a topolographic surface, using the SDBC algorithm.
 * The SDBC algorithm is described in
 * "Two algorithms to estimate fractal dimension
 * of gray level images" by Wen-Shiung Chen et.al.
 * Published in "Optical Engineering", Vol 42.
 * No. 8, August 2003.
 * 
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

public class SDBC_ implements PlugInFilter {

	ImagePlus imRef;

	boolean noGo = false;

	final int autoDiv = 4;

	final int autoMin = 2;

	// User-changeable defaults :
	boolean plotGraph = false;

	boolean verboseOutput = false;

	int maxBox = 50;

	int minBox = 10;

	boolean autoParam = true;

	public int setup(String arg, ImagePlus imp) {
		imRef = imp;
		if (imp == null)
			noGo = true;

		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		getParams();

		return DOES_8G + DOES_STACKS;
	}

	private void getParams() {
		GenericDialog gd = new GenericDialog("Calculate fractal dimension");

		gd.addCheckbox("Plot results", plotGraph);
		gd.addCheckbox("Verbose output", verboseOutput);
		gd.addCheckbox("Automatic box size (recommended)", autoParam);
		gd.addMessage("");
		gd.addNumericField("Start box size", maxBox, 0);
		gd.addNumericField("End box size", minBox, 0);

		gd.showDialog();

		if (gd.wasCanceled()) {
			if (imRef != null)
				imRef.unlock();
			noGo = true;
		}

		plotGraph = gd.getNextBoolean();
		verboseOutput = gd.getNextBoolean();
		autoParam = gd.getNextBoolean();
		maxBox = (int) gd.getNextNumber();
		minBox = (int) gd.getNextNumber();

	}

	public void run(ImageProcessor ip) {
		if (noGo)
			return;

		try {
			// Fetch data
			final int width = ip.getWidth();
			final int height = ip.getHeight();

			if (width <= 0 || height <= 0) {
				IJ.write("\nError: Empty image. Dimension not defined.");
				return;
			}

			if (autoParam) {
				maxBox = Math.max(width, height) / autoDiv;
				minBox = autoMin;
				if (verboseOutput) {
					IJ.write("Setting maximum box size to " + maxBox);
					IJ.write("Setting minimum box size to " + minBox);
				}
			}

			int min = Integer.MAX_VALUE;
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					int value = ip.getPixel(x, y);
					if (value < min)
						min = value;
				}
			}

			// Create variables we need and set them
			int count;
			ArrayList<Double> xList = new ArrayList<Double>();
			ArrayList<Double> yList = new ArrayList<Double>();
			int boxZMin, boxZMax;

			for (int boxSize = maxBox; boxSize >= minBox; boxSize--) {
				count = 0;

				// Inspect boxes
				for (int i = 0; i < width; i += boxSize) {
					int xStart = i;
					int xEnd = Math.min(xStart + boxSize, width);

					for (int j = 0; j < height; j += boxSize) {
						int yStart = j;
						int yEnd = Math.min(yStart + boxSize, height);

						boxZMin = Integer.MAX_VALUE;
						boxZMax = Integer.MIN_VALUE;

						for (int x = xStart; x < xEnd; x++) {
							for (int y = yStart; y < yEnd; y++) {

								int zValue = ip.getPixel(x, y);
								if (zValue < boxZMin)
									boxZMin = zValue;
								if (zValue > boxZMax)
									boxZMax = zValue;

							}
						}
						count += 1 + (int) ((double) (boxZMax - boxZMin + 1) / (double) boxSize);					
					}
				}

				xList.add(new Double((double) boxSize / (double) width));
				yList.add(new Double(count));
				if (verboseOutput) {
					IJ.write("Box count was " + count + " for box size " + boxSize);
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
						+ maxBox + " to " + minBox);
			}

			CurveFitter cf = new CurveFitter(boxSizes, boxCounts);
			cf.doFit(CurveFitter.STRAIGHT_LINE);
			double[] p = cf.getParams();
			String label = imRef.getTitle() + ": Dimension estimate: " + IJ.d2s(p[1], 4) 
								+ ": Settings: " + maxBox + ":" + minBox;
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
		double inc = (xmax - xmin) / ((double) samples - 1);
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
		String plotLabel = "Slope: " + IJ.d2s(params[1], 4);
		pw.addLabel(0.25, 0.25, plotLabel);
		pw.draw();
	}

	void showAbout() {
		IJ.showMessage("About SDBC..",
				"This plugin estimates the fractal dimension of maps\n "
				+"based on the SDBC algorithm.");
	}

}
