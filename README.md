ImageJ plugins::Fractal dimension
=================================

These are ImageJ plugins for estimating fractal dimension from images using box counting methods.
The following image types are supported: 2D binary (black and white), 2D greyscale (map), 3D (black and white).

The plugins were written by Jens Bache-Wiig and myself and are free software in the public domain.

Java 1.5 or later is required.

To install the plugins, copy the .class files to the ImageJ plugin folder. 

Fractal Count (FractalCount\_.java)
----------------------------------

Estimates the fractal dimension of 2D and 3D binary images. 
Supports offsets for added reliability and reproduceability of results.

[Ready to run plugin](http://www.pvv.org/~perchrh/imagej/FractalCount_.class)


Fractal Count for maps, improved SDBC (MapFractalCount\_.java)
-------------------------------------------------------------

Estimates the fractal dimension of 2D greylevel images interpreted as as topographic surface (maps).
Supports offsets for added reliability and reproduceability of results. 
Algorithm based on SDBC, see documentation and source code for more details.

[Ready to run plugin](http://www.pvv.org/~perchrh/imagej/MapFractalCount_.class)

[Documentation](http://github.com/perchrh/ImageJFractalDimension/blob/master/mapfractalcount.html)


Fractal Count for maps, original SDBC (SDBC\_.java)
--------------------------------------------------

The original SDBC algorithm.

[Ready to run plugin](http://www.pvv.org/~perchrh/imagej/SDBC_.class)

[Documentation](http://github.com/perchrh/ImageJFractalDimension/blob/master/sdbc.html)
