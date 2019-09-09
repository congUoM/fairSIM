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

import org.fairsim.linalg.*;
import org.fairsim.fiji.ImageVector;
import org.fairsim.fiji.DisplayWrapper;
import org.fairsim.utils.Tool;
import org.fairsim.utils.Conf;
import org.fairsim.utils.ImageDisplay;
import org.fairsim.sim_algorithm.*;

import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.FloatProcessor;

import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;

/** Small Fiji plugin, running all parameter estimation and reconstruction
 *  steps. Good starting point to look at the code w/o going through all the
 *  GUI components. */
public class Test3d implements PlugIn {

    /** Global variables */
    boolean showDialog =  false;    // if set, dialog to set parameters is shown at plugin start 

    int nrBands  = 3;		    // #bands (2 - two-beam, 3 - three-beam, ...)
    int nrDirs   = 3;		    // #angles or pattern orientations
    int nrPhases = 5;		    // #phases (at least 2*bands -1 )

    double emWavelen = 525;	    // emission wavelength		    
    double otfNA     = 1.4;	    // NA of objective
    double otfCorr   = 0.3;	    // OTF correction factor
    double pxSize    = 0.080;	    // pixel size (microns)

    double wienParam   = 0.05;	    // Wiener filter parameter

    boolean otfBeforeShift = true;  // multiply the OTF before or after shift to px,py

    boolean findPeak    = true;    // run localization and fit of shfit vector
    boolean refinePhase = true;    // run auto-correlation phase estimation (Wicker et. al)
    boolean doTheReconstruction = true; // if to run the reconstruction (for debug, mostly)
	
    final int visualFeedback = -1;   // amount of intermediate results to create (-1,0,1,2,3,4)

    final double apoB=.9, apoF=2; // Bend and mag. factor of APO

    /** Step-by-step reconstruction process. */
    public void runReconstruction( ImageStack inSt, String cfgfile ) {
	// ----- Parameters -----
	final int w=inSt.getWidth(), h=inSt.getHeight();
	final int d = inSt.getSize() / (nrPhases*nrDirs);
	ImageDisplay pwSt = new DisplayWrapper(2*w,2*h,"result");

	Conf cfg=null;
	OtfProvider3D otfPr    = null; 
	OtfProvider   otfPr2D  = null;	// for parameter estimation

	try {
	    cfg	    = Conf.loadFile(cfgfile);
	    otfPr   = OtfProvider3D.loadFromConfig( cfg );
	    otfPr2D = OtfProvider.loadFromConfig( cfg );
	} catch (Exception e) {
	    Tool.trace(e.toString());
	    return;
	}

	Tool.trace("successfully loaded OTF");

	
	// Reconstruction parameters: #bands, #directions, #phases, size, microns/pxl, the OTF 
//	final SimParam param = 
//	    SimParam.create3d(nrBands, nrDirs, nrPhases, w, d, 4.88281e-02, 1.23077e-01, otfPr2D, otfPr );
	SimParam param = null;
	try {
		param = SimParam.loadConfig(cfg.cd("fairsim"));
		param = SimParam.append3d(param, nrBands, nrDirs, nrPhases, w, d, 4.88281e-02, 1.23077e-01, otfPr2D, otfPr );
	} catch (Exception e) {
		System.out.println("Error!" + e.getMessage());
		return;
	}
	
	// Copy current stack into vectors, apotize borders 
		Vec2d.Real [] imgs = new Vec2d.Real[ inSt.getSize() ]; 
		for (int i=0; i<inSt.getSize();i++) { 
		    imgs[i]  = ImageVector.copy( inSt.getProcessor(i+1) );
		    imgs[i].addConst( -0 ); 
		}
	Vec3d.Cplx ret = SimAlgorithm3D.runReconstruction(imgs, param, -1, 3, false);
	pwSt.addImage(SimUtils.spatial( ret), "");
	pwSt.display();
    }


  
    /** Start from the command line to run the plugin */
    public static void main( String [] arg ) {

	if (arg.length<2) {
	    System.out.println("TIFF-file config-otf.xml");
	    return;
	}
	
	//SimpleMT.useParallel( false );
	ImagePlus ip = IJ.openImage(arg[0]);
	//ip.show();

	Test3d tp = new Test3d();
	new ij.ImageJ( ij.ImageJ.EMBEDDED );
	tp.runReconstruction( ip.getStack(), arg[1] );
    }



	@Override
	public void run(String arg) {
		// TODO Auto-generated method stub
		
	}
}