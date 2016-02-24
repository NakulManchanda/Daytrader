/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

import daytrader.datamodel.AbstractGraphPoint;

/**
 * This interface represents the abstract concept of a graph 'flat'.
 * A flat is defined as a time sequential group of points 
 * @param <T> Any object that extends from AbstractGraphPoint
 * @author Roy
 */
public interface IGraphFlat<T extends AbstractGraphPoint> extends Comparable<IGraphFlat>{
    
    /**
     * Adds a new point to this flat if and only if it has the same value (Last Price)
     * as the other points in the flat
     * @param item - The new graph point to add
     * @return - boolean true if point was added, false otherwise.
     */
    boolean addPoint(T item);
    
    /**
     * Retrieves the number of milliseconds between the first and last point in the flat
     * @return int being the difference in milliseconds between the start and end points
     */
    int getFlatLength();
    
    /**
     * The last price of all the points that are part of this flat
     * @return double being the price of the graph points in this flat
     */
    double getFlatPrice();
    
    /**
     * Tests to see if the flat length is equal to the given length
     * @param x - the length to test
     * @return boolean true if getFlatLength is equal to X, false otherwise
     */
    boolean isXLong(int x);
    
    /**
     * Tests to see if the flat length is equal to OR greater than the given length
     * @param x - the length (in SECONDS) to test
     * @return  boolean true if getFlatLength is equal OR greater than X, false otherwise
     */
    boolean isAtLeastXLong(int x);
    
    /**
     * Retrieves the graph point with the latest time value
     * @return The graph point at the far right of a price / time graph for this flat
     */
    T getLatestPoint();
    
    /**
     * Retrieves the graph point with the earliest time value
     * @return The graph point at the far left of a price / time graph for this flat
     */
    T getEarliestPoint();
    
    /**
     * Test to see if the provided point is part of the points making up this flat
     * @param aPoint
     * @return boolean true if the point lies on the flat, false otherwise
     */
    boolean isOnFlat(AbstractGraphPoint aPoint);
    
}
