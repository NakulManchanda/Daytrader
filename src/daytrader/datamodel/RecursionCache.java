/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphLine;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 * This cache object can be used to associate a 'Master Point' (usually a 'C' point) with a list of 
 * IGraphLine objects that where considered for the point. It is used to keep a record of the points
 * considered by the recursive Y-Line algorithm 
 * @author Roy
 */
public class RecursionCache {
    
    private AbstractGraphPoint masterPoint;
    private ArrayList<IGraphLine> consideredLines;
    private boolean validTerminationLine;
    
    /**
     * Constructor that accepts a Price / Time point with which the data cache is associated
     * @param newMasterPoint - A Price / Time point to associate with this data cache
     */
    public RecursionCache(AbstractGraphPoint newMasterPoint){
        this.masterPoint = newMasterPoint;
        this.consideredLines = new ArrayList<IGraphLine>();
        this.validTerminationLine = false;
    }
    
    /**
     * Copy Constructor that creates a deep copy of a RecursionCache object
     * @param toCopy - The RecursionCache object to deep copy
     */
    public RecursionCache(RecursionCache toCopy){
        this(toCopy.getMasterPoint());
        for(IGraphLine currLine : toCopy.getConsideredLines()){
            this.consideredLines.add(currLine);
        }
        this.validTerminationLine = toCopy.isValidTerminationLine();
    }

    /**
     * Retrieves the 'Master C point' being the first point used in generating the
     * potential Y-Lines by the recursive algorithm
     * @return the masterPoint
     */
    public AbstractGraphPoint getMasterPoint() {
        return masterPoint;
    }

    /**
     * Retrieves a list of all the IGraphLine objects considered as potential Y-Lines
     * for the master 'C' point
     * @return the consideredLines
     */
    public ArrayList<IGraphLine> getConsideredLines() {
        return consideredLines;
    }
    
    
    /**
     * Appends a new IGraphLine to the list of IGraphLine object generated while
     * doing the recursion
     * @param aLine - A new line to store into the cache
     * @return Boolean true if the line was stored, false otherwise.
     */
    public boolean addNewLineToCache(IGraphLine aLine){
        boolean result = false;
        if(null != aLine){
            result = this.consideredLines.add(aLine);
        }
        return result;
    }
    
    /**
     * The recursive algorithm will consider multiple 'E' points that could match to 
     * the master 'C' point to create a Y-Line. This function retrieves from the
     * cache a list of all to 'E' points that where considered
     * @return A TreeSet representing a time ordered list of all 'E' points
     * considered by the recursive algorithm
     */
    public TreeSet<AbstractGraphPoint> getAllEPointsInCache(){
        TreeSet<AbstractGraphPoint> result = new TreeSet<AbstractGraphPoint>();
        for(IGraphLine currLine : this.consideredLines){
            //NB the tree set structure will automatically prevent duplicate points from appearing twice
            result.add(currLine.getCurrentC());
            result.add(currLine.getCurrentE());
        }
        //Remove the original 'C' from the list it cannot be an 'E' point to itself
        result.remove(this.masterPoint);
        return result;
    }
    
    /**
     * Accessor to retrieve from the cache the first potential Y-Line it considered
     * @return An IGraphLine object representing the first potential Y-Line considered
     * by the recursive algorithm
     */
    public IGraphLine getOriginalLine(){
        IGraphLine result = null;
        if(null != this.consideredLines && 0 < this.consideredLines.size()){
            result = this.consideredLines.get(0);
        }
        return result;
    }
    
    /**
     * Accessor to retrieve from the cache the last potential Y-Line it considered.
     * This Y-Line will be the one actually used in the provisional Y-Line calculations
     * @return An IGraphLine object representing the last potential Y-Line considered
     * by the recursive algorithm
     */
    public IGraphLine getFinalLine(){
        IGraphLine result = null;
        if(null != this.consideredLines && 0 < this.consideredLines.size()){
            result = this.consideredLines.get(this.consideredLines.size() - 1);
        }
        return result;
    }

    /**
     * Defines whether the last line added to the cache contains a valid 'E' point
     * and therefore whether it may be used as a valid line
     * @return boolean True if the last line in the cache can be accepted as a valid Y-Line,
     * False otherwise
     */
    public boolean isValidTerminationLine() {
        return validTerminationLine;
    }

    /**
     * Accessor method to set the flag defining whether the last line in the 
     * cache can be used as a valid Y-Line
     * @param validTerminationLine - boolean True if the last line in the cache can be accepted as a valid Y-Line,
     * False otherwise
     */
    public void setValidTerminationLine(boolean validTerminationLine) {
        this.validTerminationLine = validTerminationLine;
    }
    
}
