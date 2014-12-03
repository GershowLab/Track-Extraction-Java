import ij.ImageStack;

import java.util.Arrays;
import java.util.ListIterator;
import java.util.Vector;


public class TrackBuilder {

		
	////////////////////////////
	// Ongoing track objects 
	////////////////////////////
	/**
	 * Tracks which have not yet been ended
	 */
	Vector<Track> activeTracks;
	/**
	 * Tracks which have been ended
	 */
	Vector<Track> finishedTracks;
	/**
	 * All (active? TBD) collision events  
	 */
	Vector<Collision> activeCollisions;
	/**
	 * Finished collision events  
	 */
	Vector<Collision> finishedCollisions;
	/**
	 * Number of tracks, including active tracks and tracks in collisions 
	 */
	int numTracks;
	
	////////////////////////////
	// Frame-by-frame objects
	////////////////////////////
	/**
	 * TrackPoints which have not yet been added to tracks
	 * <p>
	 * Set when the points are activated
	 */
	Vector<TrackPoint> activePts;
	/**
	 * Matches between tracks and their (nearest/nearby? TBD) points
	 * <p>
	 * Set when the tracks are activated
	 */
	Vector<TrackMatch> matches;
	/*
	 * Cross-reference table for point matches, showing how many primary matches contain this point
	 * <p>
	 * each row contains the pointID, and the number of TrackMatches with that point (in that order)
	 */
	//int[][] pointMatchTable;
	/**
	 * Index of frame being processed
	 */
	int frameNum;
	
	
	////////////////////////////
	// Auxiliary  objects
	////////////////////////////
	/**
	 * Parameters used for extracting tracks
	 */
	ExtractionParameters ep;
	/**
	* An object which provides points from an image 
	* <p>
	* Because of the point extractor, the TrackBuilder doesn't interact directly with the stack of images 
	*/
	PointExtractor pe;
	/**
	 * Object for displaying messages
	 */
	Communicator comm;
	
	
	////////////////////////////
	// Driver and Constructors
	////////////////////////////

	/**
	 * Constructs a TrackBuilder object
	 */
	public TrackBuilder(ImageStack IS, ExtractionParameters ep){
		
		this.ep = ep;
		init(0, IS);
	}
	
	/**
	 * Initialization of objects
	 */
	public void init(int frameNum, ImageStack IS){
		
		//Set Auxillary objects
		comm = new Communicator();
		pe = new PointExtractor(IS, comm);
		
		//Set track-building objects
		activeTracks = new Vector<Track>();
		finishedTracks  = new Vector<Track>();
		activeCollisions  = new Vector<Collision>();
		finishedCollisions  = new Vector<Collision>();
		
		//Set status parameters
		this.frameNum = frameNum;
		
		//TODO Put this here?
		//Build the tracks 
		//buildTracks();
		
		
		
	}
	
	////////////////////////////
	// Track Building methods 
	////////////////////////////
	
	/**
	 * Constructs tracks from a series of images
	 */
	public void buildTracks(){
		
		//Add frames to track objects
		while (pe.nextFrameNum() <= pe.endFrameNum) {
			frameNum = pe.nextFrameNum();
			if (addFrame(frameNum)>0) {
				comm.message("Error adding frame "+pe.nextFrameNum(), VerbLevel.verb_error);
				return;
			}
			
		}
		
		//Comb out collisions
		//TODO resolveCollisions();
	}
	
	
	/**
	 * Adds the points from the specified image frame to the track structures
	 * @param frameNum Index of the frame to be added
	 * @return status: 0 means all is well, >0 means there's an error
	 */
	private int addFrame(int frameNum) {
				
		if (loadPoints(frameNum)>0) {
			comm.message("Error loading points in frame "+frameNum, VerbLevel.verb_error);
			return 1;
		}
				
		if (updateTracks()>0) {
			comm.message("Error extending tracks in frame "+frameNum, VerbLevel.verb_error);
			return 1;
		}
		
		return 0;
	}

	
	/**
	 * Loads points from the specified frame into the activePts object
	 * @param frameNum Index of the frame to load
	 * @return status: 0 means all is well, >0 means there's an error
	 */
	private int loadPoints(int frameNum){
		
		if(pe.extractFramePoints(frameNum)>0){
			comm.message("Error extracting points from frame "+frameNum, VerbLevel.verb_error);
			return 1;
		}
		activePts = pe.getPoints();
		if (activePts.size()==0){
			comm.message("No points were extracted from frame "+frameNum, VerbLevel.verb_warning);
		}
//		pointMatchTable = new int[2][activePts.size()];
//		for (int i=0; i<activePts.size(); i++) {
//			pointMatchTable[0][i] = activePts.get(i).pointID;
//			pointMatchTable[1][i] = 0; 
//		}
		return 0;
	}
	
	//For each point:
		//Add point to activePts 
		//Count point
	//Set lookup table length = number of points
//	public int activatePoints(TrackPoint[] points) {
//		activePts = points;
//		
//		return 0;
//	}
	
	
	
	
	/**
	 * Extend the tracks to include points extracted from the current frame 
	 * @return status: 0 means all is well, >0 means there's an error
	 */
	private int updateTracks(){
		
		//Build matches
		makeMatches();
		if (matches.size()==0){
			comm.message("No matches were made in frame "+frameNum, VerbLevel.verb_warning);
			return 1;
		}
		
		//Modify matches
		modifyMatches();
		
		//Fuse matches to tracks
		if (extendOrEndTracks()>0){
			comm.message("Error attaching new points to tracks", VerbLevel.verb_error);
			return 1;
		}
		
		//Start new tracks from remaining activePts
		startNewTracks();
		
		return 0;
	}
	
	
	/**
	 * Matches the newly added points to the tracks 
	 */
	private void makeMatches(){
		
		matches = new Vector<TrackMatch>();

		//Match points to Tracks
		for(int i=0; i<activeTracks.size(); i++){
			TrackMatch newMatch = new TrackMatch(activeTracks.get(i), activePts, ep.numPtsInTrackMatch);
			matches.add(newMatch);
			//addMatchToPointTable(newMatch);
		}
		//Match points to Collisions
		for(int j=0; j<activeCollisions.size(); j++){
			//TODO matches.add(new CollisionMatch(activeCollisions.get(i), activePts, ep.numPtsInTrackMatch));
			TrackMatch newMatch = new CollisionMatch(activeTracks.get(j), activePts, ep.numPtsInTrackMatch); 
			matches.add(newMatch);
			//addMatchToPointTable(newMatch);
			
		}
		
	}
	
	
//	private void addMatchToPointTable (TrackMatch newMatch) {
//		TrackPoint[] pts = newMatch.getPoints();//An array so that Collision matches don't need to be handled seperately
//		for (int i=0; i<pts.length; i++) {
//			int ID = pts[i].pointID;
//			int ind = Arrays.asList(pointMatchTable[0]).indexOf(ID);
//			pointMatchTable[1][ind]++;
//			
//		}
//	}
	
	/**
	 * Corrects initial matching errors
	 */
	private void modifyMatches(){
		
		int numCutByDist = cutMatchesByDistance();
		comm.message("Number of matches cut from frame "+frameNum+" by distance: "+numCutByDist, VerbLevel.verb_debug);
		
		manageCollisions();
		
	}
	
	
	/**
	 * Marks as invalid any TrackMatch that is too far away 
	 * @return Total number of matches  
	 */
	private int cutMatchesByDistance(){
		 
		ListIterator<TrackMatch> it = matches.listIterator();
		int numRemoved = 0;
		while (it.hasNext()) {
			numRemoved += it.next().cutPointsByDistance(ep.maxMatchDist);
		}
		return numRemoved;
	}
	
	
	/**
	 * Maintains the collision structures by adding new collisions, trying basic methods of fixing current colllions, and ending finished collisions 
	 */
	private void manageCollisions(){
		 
		//if a point is assigned to more than one track, start a collision event
		int numNewColl = detectNewCollisions();
		comm.message("Number of new collisions in frame "+frameNum+": "+numNewColl, VerbLevel.verb_debug);
		
		//Try to maintain number of incoming tracks in each collision by grabbing nearby tracks and splitting points 
		conserveCollisionNums();
		
		//if number incoming = number outgoing, finish
		int numFinishedCollisions = releaseFinishedCollisions();
		comm.message("Number of collisions ended in frame "+frameNum+": "+numFinishedCollisions, VerbLevel.verb_debug);
		
		
	}
	
	//TODO
	private int detectNewCollisions(){
		
		//TODO make new 
		Vector<Collision> newCollisions = new Vector<Collision>;
		boolean[] matchInCollision = new boolean[matches.size()];
		for (int i=0;i<matches.size();i++) {
			
			if (!matchInCollision[i] && matches.get(i).checkTopMatchForCollision()>0){
				Vector<Track> colTracks = new Vector<Track>();
				colTracks.add(matches.get(i).track);
				matchInCollision[i] = true;
				
				int numColliding = matches.get(i).getTopMatchPoint().getNumMatches();
				//Find the colliding track(s)
				int startInd = i+1;
				for (int j=0; j<(numColliding-1); j++) {
					int colInd = matches.get(i).findCollidingTrackMatch(matches, startInd);
					//TODO add matches to colMatches vetor
					colTracks.add(matches.get(colInd).track);
					matchInCollision[colInd] = true;
					startInd = colInd+1;
				}
				
				Collision newCol = new Collision(colTracks, matches.get(i).getTopMatchPoint(), frameNum);
				
				//Try to fix the collision 
				
				if (newCol.fixCollision(colMatches)) {
					activeCollisions.addElement(newCol);
					newCollisions.add(newCol);
					//TODO end tracks
				}
				//WHAT TO DO WITH MATCHES? CONVERT TO COLLISIONMATCH?
			}

		}
	}
	
	
	
	
	private void conserveCollisionNums(){
		
		
		//TODO
		
		matchCollisionsToEmptyTracks();
		
		splitCollisionPoints();
		
		
	}
	
	//TODO 
	private void matchCollisionsToEmptyTracks(){
		
		//Check each trackMatch to see if it has any validmatches 
		
	}

	//TODO
	private void splitCollisionPoints(){
		
	}
	

	
	/**
	 * Finds and releases collision events which have finished
	 * @return number of collisions released
	 */
	private int releaseFinishedCollisions(){
		
		Vector<Collision> finished = detectFinishedCollisions();
		int numFinishedCollisions = finished.size();
		ListIterator<Collision> cIter = finished.listIterator();
		while(cIter.hasNext()){
			releaseCollision(cIter.next());
		}
		
		return numFinishedCollisions;
		
	}
	
	
	/**
	 * Checks the active collisions for any finished events
	 * @return Vector of finished collision objects
	 */
	private Vector<Collision> detectFinishedCollisions(){
		Vector<Collision> finished = new Vector<Collision>();
		
		ListIterator<Collision> cIt = activeCollisions.listIterator();
		while(cIt.hasNext()){
			Collision col = cIt.next();
			if (col.hasFinsihed()){
				finished.add(col);
			}
		}
		
		return finished;
	}
	
	/**
	 * Releases a collision by ending the event, adding the outgoing tracks to activeTracks, and storing the collision event for later processing 
	 * @param col The collision to be released
	 */
	private void releaseCollision(Collision col){
		
		//Tell the collision object that this is where to end it
		col.finishCollision(frameNum-pe.increment);
		//Add the newly started tracks to active tracks
		Vector<Track> newTracks = col.getOutTracks();
		activeTracks.addAll(newTracks);
		//Store the collision event for later processing
		finishedCollisions.add(col);
	}


	

	//TODO extendOrEndTracks
		//Extend 1-1 matches and good collision matches
		//Start new tracks (unmatched points), move to active
		//End dead tracks (unmatched tracks), move to finished
		//Update track number
	private int extendOrEndTracks(){
		
		ListIterator<TrackMatch> mIt = matches.listIterator();
		while (mIt.hasNext()) {
			TrackMatch match = mIt.next();
			if (match.numMatches==0){
				//move track from activeTracks to finishedTracks
				finishedTracks.addElement(match.track);
				activeTracks.remove(match.track);
			} else if (match.numMatches==1){
				//add point to track
				//TODO error check the matchPt choice with match.validPts
				int ind = 0; 
				match.track.extendTrack(match.matchPts[ind]);
				activePts.remove(match.matchPts[ind]);				
				//remove point from active points 
			} else {
				comm.message("Track ID number "+match.track.trackID+"was matched to multiple points but not converted into a collision. Deleting matches.", VerbLevel.verb_warning);
			}
			
		}
		
		return 0;
	}
	
	//TODO
	private int startNewTracks(){
		
		ListIterator<TrackPoint> tpIt = activePts.listIterator();
		while (tpIt.hasNext()) {
			TrackPoint pt = tpIt.next();
			activeTracks.addElement(new Track(pt));
		}
		
		return 0;
	}
	
	
	
	//TODO resolveCollisions	
	private int resolveCollisions(){
		
		
		return 0;
		
	}

}
