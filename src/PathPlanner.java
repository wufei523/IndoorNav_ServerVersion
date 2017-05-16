/**
 * This class calculates the optimal path from user's location to an exit
 * 
 * @author fei wu https://github.com/wufei523
 *
 *         Copyright (C) 2016 Fei Wu
 *
 */

import java.awt.Point;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;


/**
 * @author feiwu
 *
 */
public class PathPlanner {

	//final variables
	static final int dangerous_radius = 20;
	static final String LOCATION_FOLDER = "/home/student/location/";
	static final String ROUTE_FOLDER = "/home/student/route/";
	public static final boolean VERBOSE_MODE = false;
	
	//variable for map instance
	static MyMap map = null;
	static int canvasWith = 0;
	static int canvasHeight = 0;
	//variable for D*lite instance
    static DStarLite pf = null;

	//static variable for dangerous points in the map
	static ArrayList<Point> dangerousPoints = new ArrayList<>();
	//static variable for path
	static ArrayList<Point> pathPoints = new ArrayList<>();
	
	static Point shooterPosition = null;
	static Point startPoint = null;
	static Point endPoint = null;
	
	
	static ArrayList<Point> connectedHallwayPoints = new ArrayList<>();
	static ArrayList<Point> checkedPoints = new ArrayList<>();
	static ArrayList<Point> dangerous_temp = new ArrayList<>();

	/*
	 * Each file in the location folder contains the location information for each user
	 * once a user get to a new location, the WiFi localization component would update his location file
	 * The main function keep listening the location folder and read in any update location information
	 * and recalculate the optimal path to an exit
	 */
    public static void main(String[] args) throws IOException {
    	
    	//Build Map
    	map = new MyMap("myMap/");
    	canvasWith= map.getWidth();
    	canvasHeight = map.getHeight();
    	
    	
    	Path myDir = Paths.get(LOCATION_FOLDER);
        while(true){
        	try {
                WatchService watcher = myDir.getFileSystem().newWatchService();               
                myDir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, 
             		   StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                WatchKey watckKey = watcher.take();
                List<WatchEvent<?>> events = watckKey.pollEvents();
              //listen for modified files in the location folder
                for (WatchEvent event : events) {
                     if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE || event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                         if(!event.context().toString().startsWith(".") && !event.context().toString().endsWith("~")){                       	 
                        	 Thread.sleep(100);
                        	 String modified_fileName = event.context().toString();
                             System.out.println();
                             System.out.println("Doing this user: " + modified_fileName);
                        	 //read in new location information if any file modified
                             startPoint = read_current_location(modified_fileName);
                             //for demo purpose, currently it uses random shooter location
                             shooterPosition = random_Point_in_HallwayRoom();
                             //shooterPosition = new Point(290, 13);
                             //shooterPosition = new Point(172, 96);
                             //shooterPosition = new Point(205, 144);
                             System.out.println("Random Shooter at : [" + shooterPosition.x + ", " + shooterPosition.y + "]");
                             System.out.println();
                             
							dangerousPoints.clear();
							dangerous_temp.clear();
							pathPoints.clear();
							// endPoint = null;
							connectedHallwayPoints.clear();
							checkedPoints.clear();
							//calculate optimal path
							findPath();
							writePathtoFile(modified_fileName);

							System.out.println("Found Path Length =" + pathPoints.size());
							break;
                         }
                     }
                 }
                
             } catch (Exception e) {
                 System.out.println("Error: " + e.toString());
                 System.exit(0);
             }
        }
    }
    
    
    /**
     * @param pf: the D* lite instance for calculating shortest paths
     * @param obstaclePoints: the obstacles in the map
     * sets obstacles in shortest path algorithm
     */
    private static void setObstableInDstar(DStarLite pf, ArrayList<Point> obstaclePoints){
    	for (Point p : obstaclePoints){
    		pf.updateCell((int)(p.getX()), (int)(p.getY()), -1);
    	}
    }
    
    
    /**
     * @param low: lower limit
     * @param high: higher limit
     * @return a random number between lower limit and higher limit
     */
    private static int getRanIntBtw(int low, int high){
    	Random r = new Random();
        int result = r.nextInt(high-low) + low;
        return result;
    }
    
    
    /**
	 * @param shooterLoc: shooter's location in the map
	 * @return foundPoints: the dangerous points
	 * If the shooter is in a room, this function finds the points in the room and in the connected hallways
	 * If the shooter is in a hallway, this function finds the points in the hallway
	 * The returned dangerous points will be used in shortest path algorithm as constraints
	 */
	private static ArrayList<Point> connected_roomHallway_points(Point shooterLoc) {
		ArrayList<Point> foundPoints = new ArrayList<>();
		ArrayList<String> connectedHallwayNames = new ArrayList<>();
		// if shooter in room but not in hallway, block the room and connected hallways
		if (map.getPointGroupsHashMap().get("rooms").contains(shooterLoc)
				&& !map.getPointGroupsHashMap().get("allhallway").contains(shooterLoc)) {

			// this findDangerousRoomPoints find points in the shooter's room,
			// and find connected hallway points
			// this function put connected room points to dangerousPoints
			findDangerousRoomPoints(shooterLoc);
			foundPoints.addAll(dangerous_temp);

			// now, find hallways that need to be blocked
			for (Point p : connectedHallwayPoints) {
				ArrayList<String> foundHallwayNames = inWhichHallways(p);
				connectedHallwayNames.removeAll(foundHallwayNames);
				connectedHallwayNames.addAll(foundHallwayNames);
			}

			// add connected hallways to dangerous points
			for (String s : connectedHallwayNames) {
				foundPoints.addAll(map.getPointGroupsHashMap().get(s));
			}
		}

		// else if shooter is in a hallway, block the hallways
		else if (map.getPointGroupsHashMap().get("allhallway").contains(shooterLoc)) {
			ArrayList<String> foundHallwayNames = inWhichHallways(shooterLoc);
			for (String s : foundHallwayNames) {
				foundPoints.addAll(map.getPointGroupsHashMap().get(s));
			}
		}
		return foundPoints;
	}
	
    
    
    /**
     * @param shooterLoc
     * if the shooter is in a room, this function recursively finds all the points in that room
     * as dangerous points which will be considered as constraints in calculating the optimal path
     * This function also find the hallway that connected with the room where shooter is
     */
    private static void findDangerousRoomPoints(Point shooterLoc){
    	//dangerousPoints.add(centroid);
    	dangerous_temp.add(shooterLoc);
    	ArrayList<Point> children = new ArrayList<>();
    	int x = (int)shooterLoc.getX();
    	int y = (int)shooterLoc.getY();
    	children.add(new Point(x+1,y+1));   
    	children.add(new Point(x+1,y));    	
    	children.add(new Point(x+1,y-1));    	
    	children.add(new Point(x,y-1));    	
    	children.add(new Point(x-1,y-1));    	
    	children.add(new Point(x-1,y));    	
    	children.add(new Point(x-1,y+1));    	
    	children.add(new Point(x,y+1));    	
    	//System.out.println(map.getPointGroupsHashMap().get("allhallway").size());
    	//System.out.println(map.getPointGroupsHashMap().get("obstacles").size());   	
    	for (Point c : children){
    		//stop
    		if (map.getPointGroupsHashMap().get("allhallway").contains(c) || map.getPointGroupsHashMap().get("obstacles").contains(c) || checkedPoints.contains(c) || dangerous_temp.contains(c)){
    			//System.out.println("in hallway? " + map.getPointGroupsHashMap().get("allhallway").contains(c));
    			//System.out.println("is obstacle?" + map.getPointGroupsHashMap().get("obstacles").contains(c));
    			if(map.getPointGroupsHashMap().get("allhallway").contains(c)){
    				
    				connectedHallwayPoints.add(c);
    			}
    		}
    		//go on
    		else{	
    			//dangerousPoints.add(c);
    			dangerous_temp.add(c);
    			//System.out.print(checkedPoints.size()  + "      ");
    			//System.out.println(dangerousPoints.size());
    			findDangerousRoomPoints(c);
    		}
    		checkedPoints.add(c);
    	}
    	return;
    }
    
    
    /**
     * @param p: a point in the map
     * @return foundHallways: the names of the hallways that point p is in 
     */
    private static ArrayList<String> inWhichHallways(Point p){
    	ArrayList<String> foundHallways = new ArrayList<String>();	
    	//go through 10 vertical hallways
    	for (int i = 1; i<=10; i++){
    		String thisHallwayName = "hallway_v" + i;
    		if (map.getPointGroupsHashMap().get(thisHallwayName).contains(p) && !foundHallways.contains(thisHallwayName)){
    			foundHallways.add(thisHallwayName);
    		}
    	}
    	//go through 8 horizontal hallways
    	for (int i = 1; i<=8; i++){
    		String thisHallwayName = "hallway_h" + i;
    		if (map.getPointGroupsHashMap().get(thisHallwayName).contains(p) && !foundHallways.contains(thisHallwayName)){
    			foundHallways.add(thisHallwayName);
    		}
    	}  	
    	return foundHallways;
    }
    
    
    /**
     * @param p: a point in the map
     * @return nearPoints: the points that within a certain straight line distances of point p
     */
    private static ArrayList<Point> nearPoints(Point p){
    	ArrayList<Point> nearPoints = new ArrayList<Point>();
    	int xLow = p.x - dangerous_radius;
    	int xUp = p.x + dangerous_radius;
    	int yLow = p.y - dangerous_radius;
    	int yUp = p.y + dangerous_radius;
    	for (int x = xLow ; x <= xUp ; x++){
    		for (int y = yLow ; y <= yUp ; y ++){			
    			if(map.getPointGroupsHashMap().get("rooms").contains(new Point(x,y))){
    				if (isInnerSquare(x,y,p)){
        				nearPoints.add(new Point(x,y));
        			}
        			else if (distance(x, y, p) <= dangerous_radius){
        				nearPoints.add(new Point(x,y));
        			}
    			}
    		}
    	} 	
    	return nearPoints;
    }
    
    
    /**
     * @param x: coordinate x
     * @param y: coordinate y
     * @param p: a point in the map
     * @return whether the point (x,y) is in the inscribe square of the circle where p is the center
     */
    private static boolean isInnerSquare(int x, int y, Point p){
    	int margin = (int) (dangerous_radius / Math.sqrt(2));
    	if (x <= p.x + margin && x >= p.x - margin && y <= p.y + margin && y >= p.y - margin){
    		return true;
    	}
    	else
    		return false;
    }
    
    private static double distance (int x, int y, Point p){
    	return Math.sqrt((x - p.x)*(x - p.x) + (y - p.y)*(y - p.y));
    }
    
    private static double distance (Point a, Point p){    	
    	return Math.sqrt((a.x - p.x)*(a.x - p.x) + (a.y - p.y)*(a.y - p.y));
    }
    
    
    /**
     * @param fileName: the name of the file that contains location information
     * @return startPoint: user's location as start point for calculating optimal path
     */
    private static Point read_current_location(String fileName){
		String[] location_info = get_current_location(LOCATION_FOLDER + fileName);
		// String hallwayName_read_in = location_info[0];
		// String location_index_read_in = location_info[1];
		// //System.out.println(hallwayName_read_in);
		// //System.out.println(location_index_read_in);
		// ArrayList<Point> location_list =
		// map.getPointGroupsHashMap().get("location_hallway_" + hallwayName_read_in);
		// Point startPoint =
		// location_list.get(Integer.parseInt(location_index_read_in));
		// System.out.println("User at " + hallwayName_read_in + "_" +
		// location_index_read_in + " : [" + startPoint.x + ", " + startPoint.y + "]");
		// return startPoint;
        String x = location_info[0];
        String y = location_info[1];
        Point startPoint = new Point(Integer.parseInt(x), (154-Integer.parseInt(y)) );
        System.out.println("User at " + " : [" + startPoint.x + ", " + startPoint.y + "]");
        return startPoint;
    }
    
    
    /**
     * @param fileName: the name of the file that contains location information
     * @return ss: location information in String array
     */
    private static String[] get_current_location(String fileName){    
    	String[] ss= new String[2];
    	 try
    	  {
    	    BufferedReader reader = new BufferedReader(new FileReader(fileName));
    	    String line;
    	    String location_info = "";
    	    while ((line = reader.readLine()) != null)
    	    {
    	    	location_info = line;
    	    }
    	    reader.close(); 	    
    	    //ss = location_info.split("\\.");
    	    ss = location_info.split(",");
    	    return ss;
    	  }
    	  catch (Exception e)
    	  {
    	    System.err.format("Exception occurred trying to read '%s'.", fileName);
    	    e.printStackTrace();
    	    return null;
    	  }
    }
    
    
    /**
     * @return p, a random point in hallways
     */
    private static Point random_Point_in_HallwayRoom(){
        Point p = null;
        while(p == null){
        	Point point = new Point(getRanIntBtw(0, map.getWidth()),getRanIntBtw(0, map.getHeight()) );
        	if (map.getPointGroupsHashMap().get("allhallway").contains(point) || map.getPointGroupsHashMap().get("rooms").contains(point)){
        		p = point;
        	}
        }
        return p;
        
    }

    /**
     * @param exits: a list of exits in the map
     * @param startPoint: starting location
     * This function sorts exits based on straight line distance to startPoint
     */
    private static void SortExitsbyStraightDistance( ArrayList<Point> exits, Point startPoint){
    	Collections.sort(exits, new Comparator<Point>() {
    	    @Override
    	    public int compare(Point o1, Point o2) {
    	    	return (int)(distance(o1, startPoint) - distance(o2, startPoint));
    	    }
    	});
    }
    
    
     /**
     * This function initials D* lit instance by setting up statpoint, endpoint,
     * obstacles and constraints
     */
    private static void initiateDstar(){
    	pf = new DStarLite();
    	pf.init((int)startPoint.getX(),(int)startPoint.getY(),0,0);
    	//set obstacles in D*
        setObstableInDstar(pf, map.getPointGroupsHashMap().get("obstacles"));
        setObstableInDstar(pf, dangerousPoints);
    }
 
    
    
    
    /**
     * This function go though each exit and calculates the optimal path to them.
     * And find the path with smallest cost
     */
    private static void calculatePath(){	
    	Point closestExit = null;
        int smalleastCost = -1;
    	int stepUsedinThisRun = -1;
    	//startEndPoint.clear();
    	pathPoints.clear();
        //calculate path to each exit.
        for (Point this_exit : map.getPointGroupsHashMap().get("exits")){
        	if (dangerousPoints.contains(this_exit)){
        		if(VERBOSE_MODE){
        			System.out.println("Skipping this exit: [" + this_exit.x + ", " + this_exit.y + "] because its in dangerouse area");
            		System.out.println();
        		}	
        		continue;
        	}
        	if (VERBOSE_MODE){
        		System.out.println("Now doing this exit: [" + this_exit.x + ", " + this_exit.y + "] with straight distance = " + distance(this_exit, startPoint));
        	}
        	//update end point as current exit
        	pf.updateGoal((int)this_exit.getX(),(int)this_exit.getY());
        	//recalculate path
            pf.replan();
             
            List<State> path = pf.getPath();
            //check path cost
            int thisPathCost = path.size();
            if (thisPathCost >0){
            	if (VERBOSE_MODE){
            		System.out.println("Path found. Cost = " + thisPathCost + "(smalleast cost so far = " + smalleastCost + ")" + "(used "+ pf.getNumOfSteps() + " steps, while maxStep = " + pf.getMaxSteps() + ")");
            	}	
            }
            
            //thisPathCost > 0 means there is a path found
            if ((smalleastCost == -1 || thisPathCost < smalleastCost) && thisPathCost> 0){
            	pathPoints.clear();
            	endPoint = this_exit;
            	closestExit = this_exit;
            	smalleastCost = thisPathCost;
            	//set max step for next iteration;
            	stepUsedinThisRun = pf.getNumOfSteps();
            	pf.setMaxSteps(stepUsedinThisRun*3);
            	for (State i : path){   
                    //System.out.println("x: " + i.x + " y: " + i.y);
            		 pathPoints.add(new Point(i.x,i.y));
                 }
            }            
            if(VERBOSE_MODE) System.out.println();            
        }
        if(VERBOSE_MODE) System.out.println("Final result is: smallestCost = " + smalleastCost + " with step_used = " + stepUsedinThisRun);
    }
    
    
    
    /**
     * This function finds the optimal path to an exit
     */
    static private void findPath(){
    	
    	//timer
        //Timer.getTimer().start();
        
    	//get connected rooms and hallway as dangerous area
    	ArrayList<Point> connected_rommHallway = connected_roomHallway_points(shooterPosition);
    	ArrayList<Point> nearPoints = nearPoints(shooterPosition);
    	String infoMessage = "";
    	//user in the same hallway or room with shooter, but not near him
    	if(connected_rommHallway.contains(startPoint) && !nearPoints.contains(startPoint)){
    		infoMessage = "User in the same hallway or room with shooter, but not near shooter";
    		if(VERBOSE_MODE) System.out.println(infoMessage);
    		dangerousPoints.addAll(nearPoints);
    	}
        
    	//user in the same hallway or room with shooter, and near him
    	else if (connected_rommHallway.contains(startPoint) && nearPoints.contains(startPoint)){
    		infoMessage = "User in the same hallway or room with shooter, and near shooter";
    		if(VERBOSE_MODE) System.out.println(infoMessage);
    	}
    	
    	//user not in the same hallway or room with shooter, but near him
    	else if (!connected_rommHallway.contains(startPoint) && nearPoints.contains(startPoint)){
    		infoMessage = "User near shooter, but not in the same hallway or room";
    		if(VERBOSE_MODE) System.out.println(infoMessage);
    		dangerousPoints.addAll(connected_rommHallway);
    	}
        
    	else{
    		dangerousPoints.addAll(nearPoints);
    		dangerousPoints.addAll(connected_rommHallway);
    		if(VERBOSE_MODE) System.out.println("Normal case");
    	}
 
       //need to sort the exits by it's distance to the current location
       SortExitsbyStraightDistance(map.getPointGroupsHashMap().get("exits"), startPoint);

    	//this needs to be taken care of afterwards
        if(dangerousPoints.contains(startPoint)){
        	
        	System.out.println("start point in dangerous area, no way to go!");
        	endPoint = new Point(0,0);
        	//System.exit(0);
        }	
        else{
        	endPoint = new Point(0,0);
        	initiateDstar();	
        	//in calculatePath(), starEndpoint is filled, pathPoints is filled
        	calculatePath();  
        }
        
        //end timer
        //System.out.println("time used: " + Timer.getTimer().stop());
    }
    
    
    /**
     * @param fileName: output file name
     * This function writes the path to a file in route folder
     * the andriod app will read this file and show path to user
     */
    private static void writePathtoFile(String fileName){
    	
    	String sb = "";
    	for (Point point : pathPoints) {
			sb += point.x + "," + point.y + "\n"; 
		}
    	try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(ROUTE_FOLDER + fileName), "utf-8"))) {
    		writer.write(sb);
    	} catch (IOException e) {
			e.printStackTrace();
		}
    	
    }
}

