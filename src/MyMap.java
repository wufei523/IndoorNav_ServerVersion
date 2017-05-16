/**
 * This class encodes the map information
 * 
 * @author fei wu https://github.com/wufei523
 *
 *         Copyright (C) 2016 Fei Wu
 *
 */

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.imageio.ImageIO;

public class MyMap {
	
	// Private member variables
	private HashMap<String, ArrayList<Point>> pointGroupsHashMap = new HashMap<String, ArrayList<Point>>();
	private int width;
	private int height;
	
	/**
	 * Constructor
	 * @param path to the map folder
	 * read in (0,1) text representation maps into matrices
	 * extract the index of 1s in matrices and save them into pointGroupsHashMap
	 */
	public MyMap(String pathToMapFolder){
		final File folder = new File(pathToMapFolder);
		FilenameFilter fileNameFilter = new FilenameFilter() {		   
            @Override
            public boolean accept(File dir, String name) {
               if(name.lastIndexOf('.')>0)
               {
                  // get last index for '.' char
                  int lastIndex = name.lastIndexOf('.');
                  
                  // get extension
                  String str = name.substring(lastIndex);
                  
                  // match path name extension
                  if(str.equals(".txt"))
                  {
                     return true;
                  }
               }
               return false;
            }
         };
		
		for (final File eachMap : folder.listFiles(fileNameFilter)) {
			int[][] mapInMatrix = readTextMaptoMatrix(eachMap);
			ArrayList<Point> value = readOnes(mapInMatrix);
			String key = eachMap.getName().split("\\.")[0].toLowerCase();
			pointGroupsHashMap.put(key, value);
		}
		
		//Set<String> keyset=pointGroupsHashMap.keySet();
		//System.out.println("Key set values are: " + keyset);
	}
	
	
	
	/**
	 * @param file of (0,1) representation map
	 * @return a matrix
	 * converts a (0,1) representation map file into a (0,1)matrix
	 */
	private int[][] readTextMaptoMatrix(File textMapFile){
		BufferedImage buffered_img = null;
		//use this image just to get map size
		try {
			buffered_img = ImageIO.read(new File("wholeMap.png"));
		} 
		catch (IOException e) {
			 System.out.println(e.getMessage());
		}
		this.width= buffered_img.getWidth();
		this.height = buffered_img.getHeight();
		
		//System.out.println(width);
		//System.out.println(height);
		
		
		int[][] mapMatrix = new int[this.height][this.width];
		
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(textMapFile));
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
		}
		for (int i = 0; i < mapMatrix.length; i++) {
			String[] st = null;
			try {
				st = br.readLine().trim().split("");
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
			for (int j = 0; j < mapMatrix[i].length; j++) {
				mapMatrix[i][j] = Integer.parseInt(st[j]);
			}
		}
		return mapMatrix;
	}
	
	
	/**
	 * @param map Matrix
	 * @return a list of coordinates of 1s
	 * extracts the coordinates of 1s in the map matrix
	 */
	private ArrayList<Point> readOnes(int mapMatrix[][]){
		ArrayList<Point> aList = new ArrayList<>();
		
        for (int i = 0; i < mapMatrix.length; i++) {
    	    for (int j = 0; j < mapMatrix[i].length; j++) {
    	    	int xValue = j;
    	    	int yValue = mapMatrix.length - i - 1;
    	    	if (mapMatrix[i][j]==1){
    	    		aList.add(new Point(xValue, yValue));
    	    	}	
    	    }//end inner for
    	}//end outter for
        return aList;
	}
	
	public int getHeight(){
		return this.height;
	}
	public int getWidth(){
		return this.width;
	}
	
	public HashMap<String, ArrayList<Point>> getPointGroupsHashMap(){
		return this.pointGroupsHashMap;
	}
}
