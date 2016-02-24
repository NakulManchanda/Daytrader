/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphFlat;
import java.util.NavigableSet;

/**
 * This groups flats into pairs so that a price score can be calculated between them.
 * A pair is defined as two flats and the graph on which they appear
 * @author Roy
 */
public class GraphFlatPair implements Comparable<GraphFlatPair>{
    
    private IGraphFlat firstFlat;
    private IGraphFlat secondFlat;
    private BaseGraph<AbstractGraphPoint> graph;
    
    /**
     * Constructor for a GraphFlatPair the FIRST point will be the latest flat in time while the SECOND 
     * flat will be the earliest point in time
     * @param newFirst - A GraphFlat object
     * @param newSecond - A GraphFlat object
     * @param newGraph - A Graph object containing both these flats
     */
    public GraphFlatPair(IGraphFlat newFirst, IGraphFlat newSecond, BaseGraph<AbstractGraphPoint> newGraph){
        this.firstFlat = newFirst;
        this.secondFlat = newSecond;
        this.graph = newGraph;
        if(this.firstFlat.getLatestPoint().getTimestamp() < this.secondFlat.getLatestPoint().getTimestamp()){
            //They are the wrong way around switch them
            this.firstFlat = newSecond;
            this.secondFlat = newFirst;
        }
    }
    
    /**
     * Each GraphFlatPair is assigned a score that is the difference between the highest and lowest price
     * in the section of the price / time graph that contains the pair of flats.
     * @return double being the difference between the highest and lowest prices of
     * the section of the price / time graph that contains the GraphFlatPair.
     */
    public double getPairScore(){
        double result = 0;
        //subset graph to give range to examine
        NavigableSet<AbstractGraphPoint> subSet = this.graph.subSet(this.secondFlat.getEarliestPoint(), true, this.firstFlat.getLatestPoint(), true);
        BaseGraph<AbstractGraphPoint> graphSection = new BaseGraph<AbstractGraphPoint>(subSet);
        result = graphSection.getHighestPointSoFar().getLastPrice() - graphSection.getLowestPointSoFar().getLastPrice();
        return result;
    }
    
    

    @Override
    public int compareTo(GraphFlatPair o) {
        int result = 0;
        if(null != o){
            long lngTime = o.getFirstFlat().getLatestPoint().getTimestamp();
            Long timeDiff = this.firstFlat.getLatestPoint().getTimestamp() - lngTime;
            result = timeDiff.intValue();
        }
        return result;
    }

    /**
     * Retrieves the earliest flat in this pair
     * @return The earliest flat (in terms of time)
     */
    public IGraphFlat getFirstFlat() {
        return firstFlat;
    }

    /**
     * Retrieves the latest flat in this pair
     * @return The latest flat (in terms of time)
     */
    public IGraphFlat getSecondFlat() {
        return secondFlat;
    }

    /**
     * Accessor method to retrieve the price / time graph that contains this
     * GraphFlatPair.
     * @return The BaseGraph on which this flat pair was found (A price / time graph)
     */
    public BaseGraph<AbstractGraphPoint> getGraph() {
        return graph;
    }
}
