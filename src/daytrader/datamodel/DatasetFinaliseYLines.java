/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphLine;

/**
 * This class provides a data store for use in finalising the put ups current Y-Lines
 * based on the provisional Y-Lines. The data is used to update the provisional to the
 * final Y-Lines. The class associates an original 'C' point with a graph line. The
 * existence of 'stand-ins' for the original 'C' point might mean that the final line does
 * not contain the original 'C' point and this class maintains that association.
 * @author Roy
 */
public class DatasetFinaliseYLines implements Comparable<DatasetFinaliseYLines> {
    
    private AbstractGraphPoint originalCPoint;
    private IGraphLine line;
    private double gradient;

    /**
     * Initialises an object with an 'original C point' but no associated line
     * @param aCPoint - A Price / Time point
     */
    public DatasetFinaliseYLines(AbstractGraphPoint aCPoint){
        this.originalCPoint = aCPoint;
        this.gradient = 0;
    }
    
    /**
     * Initialises an object with an 'original C point' and an associated line
     * @param aCPoint  - A Price / Time point
     * @param aLine - An IGraphLine interface representing a line on the price time graph
     */
    public DatasetFinaliseYLines(AbstractGraphPoint aCPoint, IGraphLine aLine){
        this(aCPoint);
        this.line = aLine;
        this.gradient = this.line.getGradient();
    }
    
    @Override
    public int compareTo(DatasetFinaliseYLines o) {
        int result = 0;
        if(null != o){
            double wapDiff = this.originalCPoint.getWAP() - o.getOriginalCPoint().getWAP();
            if(wapDiff < 0){
                result = -1;
            } else if(wapDiff > 0){
                result = 1;
            }
        }
        return result;
    }

    /**
     * Retrieves the original 'C' point to which this data relates
     * @return the originalCPoint a price / time point
     */
    public AbstractGraphPoint getOriginalCPoint() {
        return originalCPoint;
    }

    /**
     * Retrieves the original (provisional) Y-Line which contained the 'C' point
     * @return the IGraphLine interface to the provisional Y-Line
     */
    public IGraphLine getLine() {
        return line;
    }
    
    /**
     * Accessor to retrieve the gradient of the original line 
     * @return - double being the gradient of the provisional Y-Line
     */
    public double getGradient(){
        return this.gradient;
    }
    
    /**
     * The Y-Line finalisation process uses a recursive algorithum that caches
     * data about the original 'C' point in a Recursion cache object as it 
     * executes. This accessor method retrieves that recursion cache.
     * @return - The Recursion Cache object for the original 'C' point.
     */
    public RecursionCache getRecursionCache(){
        return this.originalCPoint.getRecursionCache();
    }
}
