/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This class encapsulates all the details needed for a single double pattern one a graph
 * If a valid pattern can be found an object of this class should encapsulate it.
 * Used by SingleDoubleCheck class to store details after it has scanned a graph
 * for the pattern.
 * @author Roy
 */
public class SingleDoublePattern {
    
    private BaseGraph<AbstractGraphPoint> graph;
    private AbstractGraphPoint nowPoint;
    private AbstractGraphPoint lowOfTheGraph;
    private AbstractGraphPoint earliestLow;
    private AbstractGraphPoint singleTipStart;
    private AbstractGraphPoint singleTipEnd;
    private double retracementPrice;
    private AbstractGraphPoint doubleTipStart;
    private AbstractGraphPoint doubleTipEnd;
    private ERETableEntry ereEntry;
    
    /**
     * This is the amount that the securities price in the double part of the 
     * single double pattern must retrace (fall) by to form a valid SingleDoublePattern
     */
    public static final double RETRACEMENT = 0.5;
    
    /**
     * Constructor accepts all data items generated when looking for a Single Double
     * pattern and encapsulates them into this object
     * @param newGraph - The Price / Time graph on which this SingleDoublePattern exists
     * @param newNow - The last point on the Price / Time graph when this pattern was found
     * NB: data may be added in real time to a graph so this will not necessarily be the current
     * last point of the graph
     * @param newLow - The Price / Time point that represents the start of the SingleDoublePattern
     * @param newEarliestLow - The earliest point on this patterns graph with the same price as the new low 
     * @param newSTStart - The Price / Time point that represents the START of the patterns SINGLE TIP
     * @param newSTEnd - The Price / Time point that represents the END of the patterns SINGLE TIP
     * @param newRetracePrice - The price at which the pattern had retraced far enough to be complete
     * @param newDTStart - The Price / Time point that represents the START of the patterns DOUBLE TIP
     * @param newDTEnd - The Price / Time point that represents the END of the patterns DOUBLE TIP
     * @param newEreEntry - The ERETableEntry used to test if this SingleDoublePattern was ERE'd
     */
    public SingleDoublePattern(BaseGraph<AbstractGraphPoint> newGraph, 
            AbstractGraphPoint newNow,
            AbstractGraphPoint newLow,
            AbstractGraphPoint newEarliestLow,
            AbstractGraphPoint newSTStart,
            AbstractGraphPoint newSTEnd,
            double newRetracePrice,
            AbstractGraphPoint newDTStart,
            AbstractGraphPoint newDTEnd,
            ERETableEntry newEreEntry)
    {
        this.graph = newGraph;
        this.nowPoint = newNow;
        this.lowOfTheGraph = newLow;
        this.earliestLow = newEarliestLow;
        this.singleTipStart = newSTStart;
        this.singleTipEnd = newSTEnd;
        this.retracementPrice = newRetracePrice;
        this.doubleTipStart = newDTStart;
        this.doubleTipEnd = newDTEnd;
        this.ereEntry = newEreEntry;
    }

    /**
     * Accessor to retrieve the graph object containing this pattern
     * @return the BaseGraph of Price / Time points on which this pattern appears
     */
    public BaseGraph<AbstractGraphPoint> getGraph() {
        return graph;
    }

    /**
     * Accessor to retrieve the graph's last AbstractGraphPoint AT THE TIME THE
     * SINGLE DOUBLE PATTERN WAS FOUND
     * @return An AbstractGraphPoint representing the last Price / Time point on the graph
     * when this pattern was identified
     */
    public AbstractGraphPoint getNowPoint() {
        return nowPoint;
    }

    /**
     * Accessor to retrieve the graph's Price / Time point with the LOWEST price
     * value
     * @return An AbstractGraphPoint representing the lowest priced,
     * Price / Time point on the graph
     */
    public AbstractGraphPoint getLowOfTheGraph() {
        return lowOfTheGraph;
    }

    /**
     * Accessor method to retrieve the Price / Time point that represents the start
     * of the Single Tip.
     * @return An AbstractGraphPoint representing the Price / Time point at which the 
     * single tip starts
     */
    public AbstractGraphPoint getSingleTipStart() {
        return singleTipStart;
    }

    /**
     * Accessor method to retrieve the Price / Time point that represents the end
     * of the Single Tip.
     * @return An AbstractGraphPoint representing the Price / Time point at which the 
     * single tip ends
     */
    public AbstractGraphPoint getSingleTipEnd() {
        return singleTipEnd;
    }

    /**
     * Accessor to retrieve the price at which the security will have been considered
     * to have retraced far enough to complete the Single Double pattern
     * @return double being the price the security had to retrace to.
     */
    public double getRetracementPrice() {
        return retracementPrice;
    }

    /**
     * Accessor method to retrieve the Price / Time point that represents the start
     * of the Double Tip.
     * @return An AbstractGraphPoint representing the Price / Time point at which the 
     * double tip starts
     */
    public AbstractGraphPoint getDoubleTipStart() {
        return doubleTipStart;
    }

    /**
     * Accessor method to retrieve the Price / Time point that represents the end
     * of the Double Tip.
     * @return An AbstractGraphPoint representing the Price / Time point at which the 
     * double tip ends
     */
    public AbstractGraphPoint getDoubleTipEnd() {
        return doubleTipEnd;
    }

    /**
     * Accessor to retrieve the Price / Time point that represents the first time
     * the security reached the lowest price in this trading day
     * @return An AbstractGraphPoint representing the Price / Time point at which the 
     * the security FIRST reached its lowest price so far today.
     */
    public AbstractGraphPoint getEarliestLow() {
        return earliestLow;
    }

    /**
     * Accessor method to retrieve the entry encapsulating the ERE data used to 
     * test that this Single Double pattern is NOT ERE'd.
     * @return An ERETableEntry that was used to test this pattern
     */
    public ERETableEntry getEreEntry() {
        return ereEntry;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 59 * hash + (this.lowOfTheGraph != null ? this.lowOfTheGraph.hashCode() : 0);
        hash = 59 * hash + (this.singleTipStart != null ? this.singleTipStart.hashCode() : 0);
        hash = 59 * hash + (this.singleTipEnd != null ? this.singleTipEnd.hashCode() : 0);
        hash = 59 * hash + (this.doubleTipStart != null ? this.doubleTipStart.hashCode() : 0);
        hash = 59 * hash + (this.doubleTipEnd != null ? this.doubleTipEnd.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        //To be considered equal a Single Double pattern must have the same low of the graph, single tip start and end plus
        //the same double tip start and end.
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SingleDoublePattern other = (SingleDoublePattern) obj;
        if (this.lowOfTheGraph != other.lowOfTheGraph && (this.lowOfTheGraph == null || !this.lowOfTheGraph.equals(other.lowOfTheGraph))) {
            return false;
        }
        if (this.singleTipStart != other.singleTipStart && (this.singleTipStart == null || !this.singleTipStart.equals(other.singleTipStart))) {
            return false;
        }
        if (this.singleTipEnd != other.singleTipEnd && (this.singleTipEnd == null || !this.singleTipEnd.equals(other.singleTipEnd))) {
            return false;
        }
        if (this.doubleTipStart != other.doubleTipStart && (this.doubleTipStart == null || !this.doubleTipStart.equals(other.doubleTipStart))) {
            return false;
        }
        if (this.doubleTipEnd != other.doubleTipEnd && (this.doubleTipEnd == null || !this.doubleTipEnd.equals(other.doubleTipEnd))) {
            return false;
        }
        return true;
    }
}
