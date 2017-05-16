/**
 * This class record the computing time of this program
 * 
 * @author fei wu https://github.com/wufei523
 *
 *         Copyright (C) 2016 Fei Wu
 *
 */

public class Timer{
	
	private long startTime;
	private long endTime;
	boolean isRunning;
	private static Timer instance = null;
	
	private Timer(){
		this.startTime = 0;
		this.endTime = 0;
		isRunning = false;
	}
	
	public static Timer getTimer(){
		if (instance == null){
			instance = new Timer();
		}
		return instance;
	}
	
	public void start(){
		if (isRunning){
			System.out.println(" is alredy running");
		}
		else {
			isRunning = true;
			startTime = System.nanoTime();
		}
	}
	
	public double stop(){
		if (!isRunning){
			System.out.println("start timer first");
			return -1;
		}
		else{
			this.endTime = System.nanoTime();
			isRunning = false;
	        long duration = (this.endTime - this.startTime); 
	        endTime = 0;
	        startTime = 0;
	        
	        return duration/1000000/1000d;
			
		}
		
	}
}