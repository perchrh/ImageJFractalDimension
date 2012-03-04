/* 
 * $Id: SBC_.java,v 1.3 2005/04/20 17:22:23 perchrh Exp $
 * SBC version 0.40. 
 * Estimates the fractal dimension of a 2D greylevel image,
 * interpreted as a topolographic surface.
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

public class SBC_ implements PlugInFilter {

	ImagePlus imRef;
	boolean noGo = false;
	final int autoDiv = 4;
	final int autoMin = 2;

	final byte V = 1;
	final byte H = 0;

	// User-changeable defaults :
	boolean plotGraph = false;
	boolean verboseOutput = false;
	int maxBox = 50;
	int minBox = 10;
	boolean autoParam = true;

	public int setup(String arg, ImagePlus imp) {
		imRef = imp;
		if(imp == null) noGo = true;

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
		gd.addCheckbox("Automatic box size", autoParam);
		gd.addMessage("");
		gd.addNumericField("Start box size", maxBox, 0);
		gd.addNumericField("End box size", minBox, 0);

		gd.showDialog();

		if (gd.wasCanceled()) {
			if (imRef != null) imRef.unlock();
			noGo = true;
		}

		plotGraph = gd.getNextBoolean();
		verboseOutput = gd.getNextBoolean();
		autoParam = gd.getNextBoolean();

		maxBox = (int) gd.getNextNumber();
		minBox = (int) gd.getNextNumber();

	}

	public void run(ImageProcessor ip) {
		if(noGo) return;

		try {
			// Fetch data
			final int width = ip.getWidth();
			final int height = ip.getHeight();

			if (width <= 0 || height <= 0) {
				IJ.write("\nError: Empty image. Dimension not defined.");
				return;
			}

			// Create variables we need and set them
			int count[] = new int[2];
			ArrayList xList = new ArrayList();
			ArrayList yList = new ArrayList();
			ArrayList subsegments;
			ArrayList boxes;
			int subCount; //dummy for now..
			boolean condition = true; //dummy for now..

			for (int boxSize = maxBox; boxSize >= minBox; boxSize-- ) {
				count[H] = 0;
				count[V] = 0;
				subsegments = new ArrayList();
				boxes = new ArrayList();

				//Count boxes for this box size!

				for(int orient = 0; orient <2; orient++){


					//TODO bruk horizontale og verticale strips, annenhver gang..
					for(int x = 0; x<width;x++){
						for(int y = 0; y<height;y++){	

							if(x==0 && y==0){
								//Create a box containing pixel
								//save location of box
								count[orient]++;
								continue;
							}
							else if ( y == 0){
								//create segment
								//split segment based on boxSize
								subCount = 1;
							}
							else if ( x == 0){
								//create segment
								//split segment based on boxSize
								subCount = 1;
							}
							else {
								//create segment
								//split segment based on boxSize
								subCount = 1;
							}

							while( subsegments.size() != 0){
								//extract subsegment
								if(condition){
									//create and record box
									//and count it
									count[orient]++;
								}
								
							}


						}//end y
					}//end x

				} //end cases

				// Finished counting boxes	
				xList.add(new Double((double)boxSize/(double)width));
				yList.add(new Double( ((double)count[H]+count[V])/2.0));
				if (verboseOutput) {
					IJ.write("Box count was " + ((double)(count[H]+count[V])/2.0) + " for box size " + boxSize);
				}

			} //end boxsizes

			if (xList.size() == 0) {
				IJ.write("\nError: No boxes!\nMake sure that starting and ending box size and "
						+ "\nreduction rate allow for at least one box size to exist!");
				return;
			}

			double[] boxSizes = new double[xList.size()];
			double[] boxCountSums = new double[yList.size()];
			for (int i = 0; i < boxSizes.length; i++) {
				boxSizes[i] = -Math.log(((Double) xList.get(i)).doubleValue());
				boxCountSums[i] = Math.log(((Double) yList.get(i)).doubleValue());
			}

			if (verboseOutput) {
				IJ.write("Used " + boxSizes.length
						+ " different box sizes, from " + maxBox + " to "
						+ minBox);

			}

			CurveFitter cf = new CurveFitter(boxSizes, boxCountSums);
			cf.doFit(CurveFitter.STRAIGHT_LINE);
			double[] p = cf.getParams();
			final String label = imRef.getTitle() + ": Dimension estimate: "
				+ IJ.d2s(p[1], 4) + ": Settings: " + maxBox + ":" + minBox;
			IJ.write(label);

			if (plotGraph)
				doPlotGraph(p, boxSizes, boxCountSums);

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (imRef != null) imRef.unlock();
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
			py[i] = (float) CurveFitter.f(CurveFitter.STRAIGHT_LINE, params,
					px[i]);
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
		IJ.showMessage( "About MapFractalCount..",
				"This plugin estimates the fractal dimension of maps\n");
	}

}

