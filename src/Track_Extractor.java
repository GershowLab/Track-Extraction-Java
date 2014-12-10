//import ij.IJ;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
//import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.text.TextWindow;


public class Track_Extractor implements PlugIn{
	
	//TODO Set extraction parameters EXTRACTIONPARAMETERS
	ExtractionParameters ep; 
	
	//TODO Load the mmfs into an imagestack
	ImageStack IS;
	
	//TODO Build the tracks TRACKBUILDER
	TrackBuilder tb;
	//ep= new ExtractionParameters()
	
	public void run(String arg) {
		
		
		
		IJ.showStatus("Getting stack");
		//IS = WindowManager.getCurrentImage();
		IS = WindowManager.getCurrentWindow().getImagePlus().getImageStack();
		if (IS == null) {
			IJ.showMessage("Null ImagePlus");
			return;
		} 
//		IJ.showMessage("Slices: "+IS.getNSlices()+"; Frames: "+IS.getNFrames());
//		if (IS.getProcessor() != null) {
//			IJ.showMessage("Is an ImageProcessor");
//		} 
//		if (IS.getProcessor(0)==null) {
//			IJ.showMessage("Zero Null ");
//		} 
//		if (IS.getProcessor(1)==null) {
//			IJ.showMessage("One Null ");
//		}
		
		IJ.showStatus("Setting up TrackBuiling");
		ep= new ExtractionParameters();
		IJ.showStatus("Building Tracks");
		tb = new TrackBuilder(IS, ep);
		try {
			tb.buildTracks();
			
			int trackInd = 1;
			tb.comm.message("", messVerb);
			IJ.showStatus("Playing Track "+trackInd);
			tb.finishedTracks.get(trackInd).playMovie();
			
		}
		catch (Exception e) {
			tb.comm.message(e.toString(), VerbLevel.verb_error);
			TextWindow tw = new TextWindow("Error", tb.comm.outString, 500, 500);
		}
		
		
	}
	
	public static void main(String[] args) {
        // set the plugins.dir property to make the plugin appear in the Plugins menu
        Class<?> clazz = Track_Extractor.class;
        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir);

        // start ImageJ
        new ImageJ();


        // run the plugin
        //IJ.runPlugIn(clazz.getName(), "");
}
	
}
