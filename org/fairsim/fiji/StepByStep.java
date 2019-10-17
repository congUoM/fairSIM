/*
This file is part of Free Analysis and Interactive Reconstruction
for Structured Illumination Microscopy (fairSIM).

fairSIM is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or
(at your option) any later version.

fairSIM is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with fairSIM.  If not, see <http://www.gnu.org/licenses/>
*/

package org.fairsim.fiji;

// fairSIM components needed:
import org.fairsim.linalg.Vec2d;
import org.fairsim.utils.Tool;
import org.fairsim.sim_algorithm.SimAlgorithm;
import org.fairsim.sim_algorithm.SimParam;
import org.fairsim.sim_algorithm.SimUtils;
import org.fairsim.sim_algorithm.OtfProvider;

// ImageJ components, just to be able to easily test
import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.FloatProcessor;


/** Running a SIM reconstruction, step by step calling into high-level functions.
    This is not meant to be run unmodified, but as a working example starting point
    for your own scripts.*/
public class StepByStep implements PlugIn {


    static boolean doNotTryToFindK0 = false;


    /** runs the reconstruction step-by-step. Expects short [][] as input,
     *  where each short[] is the image data. 
     *  @param inputImages An array of input images, indexed [ang*MaxPhase + phase]
     *  @return the reconstructed image
     *  */
    static public Vec2d.Real stepByStepReconstruction( short [][] inputImages ) {


    // 1 - estimate an OTF. Alternatively, this could be loaded from an xml file

	double emWavelen = 525;	    // emission wavelength		    
	double otfNA     = 1.4;	    // NA of objective
	double otfCorr   = 0.3;    // OTF correction factor
	
	OtfProvider otf = OtfProvider.fromEstimate( otfNA, emWavelen, otfCorr ); 
	

    // 2 - create a 'SimParam' instance that will hold all the reconstruction parameters
    
	int nrBands  = 3;		    // #bands (2 - two-beam, 3 - three-beam, ...)
	int nrDirs   = 3;		    // #angles or pattern orientations
	int nrPhases = 5;		    // #phases (at least 2*bands -1 )

	double pxSize    = 0.080;	    // pixel size (microns)
	int    imgSize   = 512;		    // size of the image (pixels)

	SimParam simParam = SimParam.create( nrBands, nrDirs, nrPhases, imgSize, pxSize, otf  );


    // 3 - create Vec2d.Cplx objects from the input images, window and fft them

	Vec2d.Cplx [][] rawImages = new Vec2d.Cplx[ simParam.nrDir() ][ simParam.nrPha() ];
	final double background = 0;   
	
	for ( int ang = 0; ang < simParam.nrDir(); ang++) {
	    for ( int pha = 0; pha < simParam.nrPha(); pha++) {
		
		// create the vector
		rawImages[ang][pha] = Vec2d.createCplx( imgSize, imgSize );

		// copy the input
		rawImages[ang][pha].setFrom16bitPixels( inputImages[ ang * simParam.nrPha() + pha ] );

		// subtract background (this clips to 0, and returns percentage of clipped pixels)
		SimUtils.subtractBackground( rawImages[ang][pha], background );
	
		// apply simple windowing
		//SimUtils.fadeBorderCos( rawImages[ang][pha], 15 );

		// fft the vector
		rawImages[ang][pha].fft2d( false );

		Tool.trace( "--> input fft'd "+ang+" "+pha);
	    }
	}

    // 4 - estimate the SIM parameters

	// which band to fit to (on three-beam data typically: 1 - more robust, 2 - more precise)
	final int fitBand = 2;		
	
	// if positive: region to exclude from fit, in relation OTF cutoff freq. 
	// if negative: only run a fine estimate of kx,ky [within +-2px] using the values stored in SimParam as starting point
	final double fitExclude = (doNotTryToFindK0)?(-1):(0.6);

	// how much feedback / output the reconstruction process generated. Only useful if it also
	// has an 'ImageFactory' to write these results into.
	final int visualFeedback = -1;

	// we need some default values (these are for the OMX U2OS example set) if we don't run the coarse estimation
	if (doNotTryToFindK0) {
		// 3D params
		if (false) {
		    simParam.dir(0).setPxPy( 126.656 , -125.144); 
		    simParam.dir(1).setPxPy( -44.8111, -172.567);
		    simParam.dir(2).setPxPy( 171.278 ,   48.6333);
		    simParam.dir(0).setModulation(0, 1.0);
		    simParam.dir(0).setModulation(1, 0.8);
		    simParam.dir(0).setModulation(2, 0.8);
		    simParam.dir(1).setModulation(0, 1.0);
		    simParam.dir(1).setModulation(1, 0.8);
		    simParam.dir(1).setModulation(2, 0.8);
		    simParam.dir(2).setModulation(0, 1.0);
		    simParam.dir(2).setModulation(1, 0.8);
		    simParam.dir(2).setModulation(2, 0.8);
		    
		    simParam.dir(0).setPhaOff(-7.79274e-01);
		    simParam.dir(1).setPhaOff(-1.71138e+00);
		    simParam.dir(2).setPhaOff(-2.19546e+00);
		}
	    
	    // 2D params
	    simParam.dir(0).setPxPy( 1.37411e+02, -1.40922e+02); 
	    simParam.dir(1).setPxPy(-5.28778e+01, -1.89522e+02);
	    simParam.dir(2).setPxPy( 1.90144e+02,  4.99889e+01);
	    simParam.dir(0).setModulation(0, 1.0);
	    simParam.dir(0).setModulation(1, 5.36570e-01);
	    simParam.dir(0).setModulation(2, 6.93214e-01);
	    simParam.dir(1).setModulation(0, 1.0);
	    simParam.dir(1).setModulation(1, 5.14881e-02);
	    simParam.dir(1).setModulation(2, 5.70836e-01);
	    simParam.dir(2).setModulation(0, 1.0);
	    simParam.dir(2).setModulation(1, 9.46642e-02);
	    simParam.dir(2).setModulation(2, 6.01721e-01);
	    
	    simParam.dir(0).setPhaOff( 123.8723);
	    simParam.dir(1).setPhaOff(-58.7706);
	    simParam.dir(2).setPhaOff(-106.6750 );
	} else {
	
		// run the actual parameter estimation
		SimAlgorithm.estimateParameters( simParam, rawImages, fitBand, fitExclude, null, visualFeedback, null);
	}
    
    // 5 -  run the reconstruction

	// apply the OTF before or after Fourier-shifting the signal
	// usually, applying it before the shift should work just fine
	final boolean otfBeforeShift = true;
	
	// clip the output to [0..255] (clip&scale), to [0..max] (clip) or leave neg. values in (none)
	final SimParam.CLIPSCALE clipOutput = SimParam.CLIPSCALE.NONE;

	// Wiener filtering: set filter parameters
	simParam.setWienerFilter( 0.05 );   // wiener filter parameter
	simParam.setApoCutoff( 2.0 );	    // cutoff of apodization
	simParam.setApoBend( 0.9 );	    // exponent of apodization 
	
	otf.setAttenuation( 0.9995, 2.0 );   // set strength (0..1) and FWHM (in 1/micron) of OTF attenuation
	otf.switchAttenuation( true );	    // important: has to be 'true', otherwise no attenuation gets used


	// run the actual reconstruction
	Vec2d.Real result = SimAlgorithm.runReconstruction( simParam, rawImages, null, 
	    visualFeedback, otfBeforeShift, clipOutput, null ); 
	
    // 6 - return our result
     
	// info: if you need the underlying data array, to this
	//
	// Vec2d.Real values = .... ;
	// float [] rawValues = values.vectorData();
	//
	// if you need to modify data in a vector, you can write to 'rawValues', but
	// you have to call 'values.syncBuffer()' afterwards

	return result;
    }




    /** Allow to run as an ImageJ plugin. Converts the currently selected
     *  stack into short [][], and passes it into the reconstruction */
    public void run(String arg) {

	// currently set up for 3 angles, 5 phases, just for testing
	final int nrPhases = 5;
	final int nrDir    = 3;
	final int nrSlices = 53;

	// currently selected stack, some basic checks
	ImageStack inSt = ij.WindowManager.getCurrentImage().getStack();
	final int w=inSt.getWidth(), h=inSt.getHeight();
	if (w!=h) {
	    IJ.showMessage("Image not square (w!=h)");
	    return;
	}
	if (inSt.getSize() != nrPhases*nrDir*nrSlices ) {
	    IJ.showMessage("Stack length != phases*angles*nrSlices: "+inSt.getSize() );
	    return;
	}

	// convert the stack into a short [][] to be compatible with our test function
//	short [][][] imgs = new short[nrSlices][nrDir*nrPhases][];
//
//	Tool.trace("converting input stack to short []");
//	for ( int sliceInd = 0; sliceInd < nrSlices; sliceInd++) {
//		for ( int i=0; i<nrDir*nrPhases; i++) {
//		    imgs[sliceInd][i] = (short[])inSt.getProcessor(i+1).convertToShortProcessor().getPixels();
//		}
//	}
	
	short [][][] imgs = new short[nrSlices][nrDir*nrPhases][];

	Tool.trace("converting input stack to short []");
	int i = 0;
	for ( int dirInd = 0; dirInd < nrDir; dirInd++) {
		for ( int sliceInd = 0; sliceInd < nrSlices; sliceInd++) {
			for ( int phaseInd = 0; phaseInd < nrPhases; phaseInd++) {
				imgs[sliceInd][dirInd*nrPhases+phaseInd] = (short[])inSt.getProcessor(i+1).convertToShortProcessor().getPixels();
			    i++;
			}
		}
	}
	
	ImageStack outStack = new ImageStack();
	
	// start the reconstruction
	for ( int sliceInd = 0; sliceInd < nrSlices; sliceInd++) {
	//for ( int sliceInd = 6; sliceInd < 7; sliceInd++) {
		Vec2d.Real res = stepByStepReconstruction( imgs[sliceInd] );
		// convert the result into an ImageVector for displaying
		ImageVector displayResult = ImageVector.create( res.vectorWidth(), res.vectorHeight());
		displayResult.copy( res );
		FloatProcessor fp = displayResult.img();
		
		// some settings to get a [0...max] scaling
		fp.resetMinAndMax();
		fp.setMinAndMax(0, fp.getMax());
		outStack.addSlice(fp);
	}
    
	// open the actual image display
	ImagePlus ipl = new ImagePlus("reconstruction result", outStack);
	ipl.show();
    
    }


    /** Start from the command line to run the plugin */
    public static void main( String [] arg ) {

	if (arg.length<1) {
	    System.out.println("TIFF-file [NO - don't run coarse peak fit]");
	    return;
	}
 
	if (arg.length>1) {
	    if (arg[1].equals("NO")) {
		doNotTryToFindK0 = true;	    
	    }
	}

	new ij.ImageJ( ij.ImageJ.EMBEDDED );
	ImagePlus ip = IJ.openImage(arg[0]);
	ip.show();

	StepByStep sbs = new StepByStep();
	sbs.run("");
    }

}


