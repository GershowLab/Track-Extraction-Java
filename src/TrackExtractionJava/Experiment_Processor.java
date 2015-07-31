package TrackExtractionJava;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import edu.nyu.physics.gershowlab.mmf.mmf_Reader;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.text.TextWindow;


public class Experiment_Processor implements PlugIn{

	ProcessingParameters prParams; 
	private String srcDir;
	private String srcName;
	private String dstDir;
	private PrintWriter processLog;
	
	
	private ImageWindow mmfWin;
	private ImagePlus mmfStack;
	private BackboneFitter bbf;
	private Experiment ex;
	
	private boolean runningFromMain = false;
	private TicToc runTime;
	private int indentLevel;
	
	
	//TODO command line invocation
	public static void main(String[] args){
		
        // set the plugins.dir property to make the plugin appear in the Plugins menu
//		Class<?> clazz = Experiment_Processor.class; 
//        String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
//        String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
//        System.setProperty("plugins.dir", pluginsDir);
		/*
        Class<?> clazz2 =  mmf_Reader.class; 
        String url2 = clazz2.getResource("/" + clazz2.getName().replace('.', '/') + ".class").toString();
        String pluginsDir2 = url2.substring(5, url2.length() - clazz2.getName().length() - 6);
        System.setProperty("plugins.dir", pluginsDir2);
        */
		
//		System.out.println(args[0]);
//		System.out.println(args[1]);
		
		ImageJ imj = new ImageJ();
		
		Experiment_Processor ep = new Experiment_Processor();
		ep.runningFromMain = true;
		
		if (args!=null && args.length>=1){
			
			if (args.length>=2){
				ep.dstDir = args[1];
			}
			
			ep.run(args[0]);
		}
		
		imj.quit();
	}


	public Experiment_Processor(){
		
	}
	/**
	 * Opens a file (via argument or dialog); extracts if needed, and fits tracks; saves extracted and fit tracks
	 */
	public void run(String arg0) {
		indentLevel=0;
		runTime = new TicToc();
		
		if (prParams==null){
			init();
		}
		
		boolean success = (loadFile(arg0) );
		try {
			runTime.tic();
			String logpathname = setupLog();
			System.out.println("Experiment Processing Log initiated at "+logpathname);
			log("Initiated log entry");
			if(success){
				if (ex==null){
					
					log("Loaded mmf; Extracting tracks...");
					if (!extractTracks()) {
						log("Error extracting tracks; aborting experiment_processor.");
						return;
					}
					log("...done extracting tracks");
					if (prParams.closeMMF && mmfWin!=null) {
						log("Closing MMF Window");
						mmfWin.close();
						mmfWin=null;
					}
					if (prParams.saveMagEx) {
						log("Saving Maggot Tracks...");
						saveOldTracks();
						log("...done saving Maggot Tracks");
					}
					IJ.showStatus("Done Saving MaggotTrackPoint Experiment");
					//TODO release memory to OS? System.gc
				}
				
				/*
				try{
					log("Testing MagEx.fromDisk...");
					testFromDisk(false, processLog);
					log("...MagEx.fromDisk complete");
				} catch (Exception exc){
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					exc.printStackTrace(pw);
					log ("...Error in MTP Experiment fromDisk:\n"+sw.toString());
				}
				*/
				System.gc();
				if (prParams.doFitting){
//					ex = new Experiment(ex);
					log("Fitting "+ex.getNumTracks()+" Tracks...");
					fitTracks();
					log("...done fitting tracks");
					if (prParams.saveFitEx) {
						log("Saving backbone tracks...");
						if (saveNewTracks()){
							log("...done saving backbone tracks");
						} else {
							log("Error saving tracks");
						}
						IJ.showStatus("Done Saving BackboneTrackPoint Experiment");
					}
					
				}
				
				/*try{
					log("Testing FitEx.fromDisk...");
					testFromDisk(true, processLog);
					log("...FitEx.fromDisk complete");
				} catch (Exception exc){
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					exc.printStackTrace(pw);
					log ("...Error in BTP Experiment fromDisk:\n"+sw.toString());
				}*/
				
				//TODO release memory to OS? System.gc
			} else {
				log("...no success");
			}
			
			log("Done Processing");
			
		} catch (Exception e){
			
		} finally {
			if (processLog!=null) processLog.close();
			if (prParams.closeMMF && mmfWin!=null) {
				mmfWin.close();
				mmfWin=null;
			}
			
		}
		
		
		IJ.showStatus("Done Processing");
		
	}
	
	private void testFromDisk(boolean btpData, PrintWriter pw){
		
		String[] pathParts;
		if (btpData){
			pathParts = prParams.setFitExPath(srcDir, srcName);
		} else {
			pathParts = prParams.setMagExPath(srcDir, srcName);
		}
		File f;
		if (dstDir==null || dstDir.equals("")){
			f = new File(pathParts[0]+File.separator+pathParts[1]);
		}else {
			f = new File(dstDir+File.separator+pathParts[1]);
		}
		IJ.showStatus("Loading Experiment...");
		System.out.println("Testing fromDisk method on file "+f.getPath());
		if (pw!=null) pw.println("Loading experiment "+f.getPath());
		try{
			 
			DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
			Experiment newEx = Experiment.fromDisk(dis, f.getPath(), new ExtractionParameters(), new FittingParameters(), pw);
			dis.close();
			IJ.showStatus("...done loading experiment (showing in frame)");

			if (pw!=null) pw.println("Opening frame...");
			ExperimentFrame exFrame = new ExperimentFrame(newEx);
			exFrame.run(null);

			if (pw!=null) pw.println("...Frame open");
			
			
		} catch (Exception e){
			if(pw!=null) pw.println("Error loading experiment");
			System.out.println("Experiment_Processor.testFromDisk error");
			return;
			
		}
		
	}
	
	/**
	 * Set ups the processing objects
	 */
	private void init(){
		prParams = new ProcessingParameters();
		bbf = new BackboneFitter();
	}
	
	
	/**
	 * Loads whichever object (experiment or imageStack) is indicated
	 * @param arg0 The complete filename, or null to open a file chooser dialog
	 */
	private boolean loadFile(String arg0){
		indentLevel++;
		
		String fileName=null;
		String dir=null;
		
		boolean success;
		
		//Get the path name...
		if (arg0==null || arg0.equals("")){ //...from a dialog
			OpenDialog od = new OpenDialog("Choose an experiment (.mmf or .jav)", null);
			dir = od.getDirectory();
			fileName = od.getFileName();
			
		} else {//...from the passed argument
			System.out.println("Loading file "+arg0);
			IJ.showStatus("Loading file "+arg0);
			File f = new File(arg0);
			fileName = f.getName();
			dir = f.getParent();
//			StringBuilder sb = new StringBuilder(arg0);
//			int sep = sb.lastIndexOf(System.getProperty("path.separator"));
//			if (sep>0){
//				fileName = sb.substring(sep+1);
//				dir = sb.substring(0, sep-1);
//			}
		}
		
		//Open the file with the appropriate method
		if (dir!=null){
			
			srcDir = dir;
			srcName = fileName;
			
//			System.out.println("Loading file "+fileName);
			IJ.showStatus("Loading file "+fileName);
			
			if (fileName.substring(fileName.length()-4).equalsIgnoreCase(".mmf")){
//				System.out.println("Recognized as mmf");
				success = openMMF(dir, fileName);
				//Read MMF metadata 
				
			} else if (fileName.substring(fileName.length()-4).equalsIgnoreCase(".jav")){
				success = openExp(dir, fileName);
				
			} else {
				System.out.println("Experiment_Processor.loadFile error: did not recognize file type"); 
				IJ.showMessage("File not recognized as a .mmf or a .jav");
				success = false;
			}
		} else {
//			IJ.showMessage("Could not load file; null directory");
			System.out.println("Experiment_Processor.loadFile error: could not parse file name"); 
			success = false;
		}
		
		indentLevel--;
		return success;
	}
	/**
	 * Loads an imageStack into the processor from a .mmf file
	 * @param dir
	 * @param filename
	 * @return Status of the file opening: true=successful
	 */
	private boolean openMMF(String dir, String filename){
		indentLevel++;
		try{
			IJ.showStatus("Opening MMF...");		
			
			if (!runningFromMain){
				IJ.run("Import MMF", "path=["+new File(dir, filename).getPath()+"]");
				mmfWin = WindowManager.getCurrentWindow();
				mmfStack = mmfWin.getImagePlus();
			} else {
//				System.out.println("Opening mmf from code..");
				mmf_Reader mr = new mmf_Reader();
				String path = new File(dir, filename).getPath();
//				System.out.println(path);
				mr.loadStack(path);
				if (mr.getMmfStack()==null) {
					System.out.println("null stack");
					return false;
				}
				mmfStack = new ImagePlus(path, mr.getMmfStack());
			}
			IJ.showStatus("MMF open");
			return true;
		} catch (Exception e){
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			new TextWindow("Error opening mmf", sw.toString(), 500, 500);
			System.out.println("Experiment_processor.openMMF error");
			return false;
		} finally{
			indentLevel--;
		}
	}
	/**
	 * Loads an experiment into the processor from a .ser file
	 * @param dir
	 * @param filename
	 * @return Status of the file opening: true=successful
	 */
	private boolean openExp(String dir, String filename){
		indentLevel++;
		try {
			/*IJ.showStatus("Opening Experiment...");
			ex = new Experiment(Experiment.deserialize(new File(dir, filename).getPath())); 
			IJ.showStatus("Experiment open");*/
			IJ.showStatus("Opening Experiment...");
			ex = new Experiment(Experiment.fromPath(new File(dir, filename).getPath())); 
			IJ.showStatus("Experiment open");
			return true;
		} catch (Exception e){
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			System.out.println("Experiment_processor.openExp error");
			new TextWindow("Error opening serialized experiment", sw.toString(), 500, 500);
			return false;
		} finally{
			indentLevel--;
		}
	}
	
	
	/**
	 * Sets up the Processing Log (A text file containing output RE processing events)
	 * <p>
	 * Log is saved in a txt file named according to the naming convention defined in the Processing Parameters. If the 
	 * file already exists, this log is appended to the end of that file
	 * @return Success of the creation of the log 
	 */
	private String setupLog(){
		
		if (srcDir==null || srcName==null){
			return "";
		}
		
		indentLevel++;
		
		try{
			String[] logPathParts = prParams.setLogPath(srcDir, srcName);
			String fname = new File(logPathParts[0], logPathParts[1]).getPath();
			processLog = new PrintWriter(new FileWriter(fname, true));
			
			processLog.println();
			processLog.println("----------------------------------------------");
			DateFormat df = new SimpleDateFormat("MM-dd-yyyy hh:mm z");
			processLog.println("Log entry at "+df.format(new Date()));
			processLog.println("----------------------------------------------");
			processLog.println();
			
			return fname;
		} catch (Exception e){
			new TextWindow("Log error", "Unable to create Processing Log file '"+srcName+"' in '"+srcDir+"'", 500, 500);
			return "";
		} finally{
			indentLevel--;
		}
		
		
	}
	
	
	/**
	 * Runs track extraction on the imageStack
	 */
	private boolean extractTracks(){
		indentLevel++;
		String status = "";
		IJ.showStatus("Extracting tracks");
		status+="Running trackbuilder...\n";
		MaggotTrackBuilder tb = new MaggotTrackBuilder(mmfStack.getImageStack(), new ExtractionParameters());

		try {
			System.out.println("Extracting tracks");
			//Extract the tracks
			tb.run();
			status+="...Running complete! \n Converting to experiment... \n";
			ex = new Experiment(tb.toExperiment());
			status+="...Converted to Experiment\n";
			
			
			//Show the extracted tracks
			if(prParams.showMagEx){
				IJ.showStatus("Showing Experiment");
				status+="Showing experiment...\n";
				ExperimentFrame exFrame = new ExperimentFrame(ex);
				exFrame.run(null);
				status+="...Experiment shown\n";
			}
			
			System.out.println("Extracted "+ex.getNumTracks()+" tracks");
			
		} catch  (Exception e){
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			log("Error opening experiment: \n"+sw.toString());
			System.out.println("Experiment_Processor.extractTracks error");
			new TextWindow("Error extracting tracks", status+"\n"+sw.toString(), 500, 500);
			return false;
		} finally{
			tb.showCommOutput();
			indentLevel--;
		}
		return true;
	}
	/**
	 * Replaces each track in the experiment with a backbone-fitted track
	 */
	private void fitTracks(){
		indentLevel++;
		IJ.showStatus("Fitting Tracks...");
		System.out.println("Fitting "+ex.getNumTracks()+" Tracks");
		Track tr;
		Track newTr = null;
		Vector<Track> toRemove = new Vector<Track>();

		//Fit each track that is long enough for the course passes
		for (int i=0; i<ex.getNumTracks(); i++){
			
			if (i%bbf.params.GCInterval==0){
				System.gc();
			}
			
			IJ.showStatus("Fitting Track "+(i+1)+"/"+ex.getNumTracks());
			tr = ex.getTrackFromInd(i);
			if (tr.getNumPoints()>prParams.minTrackLen) {//Check track length
				newTr = fitTrack(tr);
				if (newTr!=null){
					ex.replaceTrack(newTr, i);
				} else {
					tr.setValid(false);
					System.out.println("Error fitting Track "+tr.getTrackID()+" (#"+i+")");
					toRemove.add(tr);
				}
			} else {
				tr.setValid(false);
				System.out.println("Track "+tr.getTrackID()+" (#"+i+") too short to fit");
				toRemove.add(tr);
			}
		}
		
		bbf.showCommOutput();
		
		//Remove the tracks that couldn't be fit
//		for(Track t : toRemove){
//			ex.removeTrack(t);
//		}
		
		IJ.showStatus("Done fitting tracks");
		System.out.println("Done fitting tracks: "+(ex.getNumTracks()-toRemove.size())+"/"+ex.getNumTracks()+" were fit successfully");
		
		//Show the fitted tracks
		if (prParams.showFitEx){
			IJ.showStatus("Making experiment frame");
			ExperimentFrame exFrame = new ExperimentFrame(ex);
			IJ.showStatus("Experiment shown in frame");
			exFrame.run(null);
		} 
		indentLevel--;
	}
	/**
	 * Fits backbones to a Track of MaggotTrackPoints
	 * @param tr The track of MaggotTrackPoints to be fit
	 * @return A track fit by the BackboneFitter
	 */
	private Track fitTrack(Track tr){
		indentLevel++;
		bbf.fitTrack(tr);
		indentLevel--;
		return bbf.getTrack();
		
	}

	
	/**
	 * Saves the maggotTracks according to the naming convention defined in the Processing Parameters
	 */
	private boolean saveOldTracks(){
		indentLevel++;
		File f;
		String[] pathParts = prParams.setMagExPath(srcDir, srcName);
		if (dstDir==null || dstDir.equals("")){
			f = new File(pathParts[0]+File.separator+pathParts[1]);
		}else {
			f = new File(dstDir+File.separator+pathParts[1]);
		}
		System.out.println("Saving MaggotTrack experiment to "+f.getPath());
		log("Saving MaggotTrack experiment to "+f.getPath());
		boolean status;
		try{
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f))); 
			ex.toDisk(dos, processLog);
			status=true;
			dos.close();
		} catch(Exception e){
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			log("Error saving experiment: \n"+sw.toString());
			status=false;
			System.out.println("Experiment_processor.saveOldTracks error");
		}
		
		indentLevel--;
		return status;
	}
	/**
	 * Saves the backboneTracks according to the naming convention defined in the Processing Parameters
	 */
	private boolean saveNewTracks(){
		indentLevel++;
		
		String[] pathParts = prParams.setFitExPath(srcDir, srcName);
		File f;
		if (dstDir==null || dstDir.equals("")){
			f = new File(pathParts[0]+File.separator+pathParts[1]);
		}else {
			f = new File(dstDir+File.separator+pathParts[1]);
		}
		System.out.println("Saving LarvaTrack experiment to "+f.getPath());
		boolean status;
		try{
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f))); 
			ex.toDisk(dos, processLog);
			status=true;
			dos.close();
		} catch(Exception e){
			status=false;
			System.out.println("Experiment_processor.saveNewTracks error");
		}
		
		indentLevel--;
		return status;
	}
	
	private void log(String message){
//		System.out.println(message);
		
		
		String indent = "";
		for (int i=0;i<indentLevel; i++) indent+="----";
		processLog.println(runTime.tocSec()+indent+" "+message);
	}
	
}




class TicToc{
	
	private long startTime;
	
	public TicToc(){
		
	}
	
	public void tic(){
		startTime = System.currentTimeMillis();
	}
	
	public long toc(){
		return System.currentTimeMillis()-startTime;
	}
	
	public long tocSec(){
		return (toc())/1000;
	}
	
	
}

