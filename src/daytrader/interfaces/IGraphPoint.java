/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

import daytrader.datamodel.RecursionCache;
import java.util.Calendar;

/**
 * This interface represents the abstract concept of a point on a stock market
 * line graph (a stock PRICE at a given TIME)
 *
 * @author Roy
 */
public interface IGraphPoint extends Comparable<IGraphPoint>, ICSVPersistable, XMLPersistable<IGraphPoint> {

    /**
     * Retrieves the date time of the point as a Java Calendar object
     *
     * @return Calendar object being the date & time of this event.
     */
    Calendar getCalDate();

    /**
     * Turns this points date into a integer number suitable for sorting on in
     * the format YYYYMMDD So 20th April 2013 would become 20130420
     *
     * @return An integer number representing the date component of this point
     */
    int getDateAsNumber();

    /**
     * Returns the day as a CSV string in the format YYYY,MM,DD
     *
     * @return the formated string
     */
    String getDayAsCSVString();

    /**
     * Accessor retrieves the year component of the IGraphPoints time element
     * @return integer being the year that applies to this graph point
     */
    int getYear();

    /**
     * Accessor retrieves the month component of the IGraphPoints time element
     * @return integer being the month that applies to this graph point 1 to 12
     */
    int getMonth();

    /**
     * Accessor retrieves the day component of the IGraphPoints time element
     * @return integer being the day of the month that applies to this graph point
     */
    int getDay();

    /**
     * Retrieves the date time of the point as a UNIX timestamp
     *
     * @return long being the UNIX timestamp
     */
    long getTimestamp();

    /**
     * Retrieves the "Last" price for this point
     *
     * @return double being the stock price at this time
     */
    double getLastPrice();

    /**
     * Retrieves the opening price at this graph point
     *
     * @return double being the market price
     */
    double getOpen();

    /**
     * Retrieves the opening price at this graph point
     *
     * @param rounder - A rounding function to apply to the value.
     * @return double being the market price rounded using the function provided
     */
    double getOpen(IRoundFunction rounder);

    /**
     * Retrieves the highest price at this graph point
     *
     * @return double being the market price
     */
    double getHigh();

    /**
     * Retrieves the highest price at this graph point
     *
     * @param rounder - A rounding function to apply to the value.
     * @return double being the market price
     */
    double getHigh(IRoundFunction rounder);

    /**
     * Retrieves the lowest price at this graph point
     *
     * @return double being the market price
     */
    double getLow();

    /**
     * Retrieves the lowest price at this graph point
     *
     * @param rounder - A rounding function to apply to the value.
     * @return double being the market price
     */
    double getLow(IRoundFunction rounder);

    /**
     * Retrieves the closing price at this graph point
     *
     * @return double being the market price
     */
    double getClose();

    /**
     * Retrieves the closing price at this graph point
     *
     * @param rounder - A rounding function to apply to the value.
     * @return double being the market price
     */
    double getClose(IRoundFunction rounder);

    /**
     * Retrieves the WAP price at this graph point (As used on Stock Brokers
     * Line Graphs)
     *
     * @return double being the market price
     */
    double getWAP();

    /**
     * Retrieves the WAP price at this graph point (As used on Stock Brokers
     * Line Graphs)
     *
     * @param rounder - A rounding function to apply to the value.
     * @return double being the market price
     */
    double getWAP(IRoundFunction rounder);

    /**
     * Retrieves the number of milliseconds since the exchange opened and the
     * time of this point on the price / time graph
     *
     * @return long being the number of milliseconds between the start of
     * trading and this graph point
     */
    long getMSElapsedSinceStartOfTrading();

    /**
     * Accessor to set the ID of the request that generated this graph point
     * @param newId - Integer being the new ID for this graph point
     */
    void setReqId(int newId);

    /**
     * Accessor to set the open price for this graph point
     * @param newOpen - A double price for this Open value of this graph point
     */
    void setOpen(double newOpen);

    /**
     * Accessor to set the high price for this graph point
     * @param newHigh - A double price for this High value of this graph point
     */
    void setHigh(double newHigh);

    /**
     * Accessor to set the low price for this graph point
     * @param newLow - A double price for this Low value of this graph point
     */
    void setLow(double newLow);

    /**
     * Accessor to set the close price for this graph point
     * @param newClose - A double price for this Close value of this graph point
     */
    void setClose(double newClose);

    /**
     * Accessor to set the volume quantity for this graph point
     * @param newVol - A long denoting the volume of shares traded
     */
    void setVolume(long newVol);

    /**
     * Accessor to set the count quantity for this graph point
     * @param newCount  - A integer being the new count value
     */
    void setCount(int newCount);

    /**
     * Accessor to set the WAP price (Weighted Average Price) for this graph point.
     * This is the key value for Bryns pricing calculations
     * @param newWap  - A double price being the new WAP value of this graph point
     */
    void setWAP(double newWap);

    /**
     * Accessor to set the has gaps flag
     * @param newHasGaps - Boolean being a True or False value for the flag
     */
    void setHasGaps(boolean newHasGaps);

    /**
     * Accessor to set the timestamp to use for this graph points 
     * @param newTimestamp - A long being the timestamp to use for this graph point.
     */
    void setCalDate(long newTimestamp);
    
    /**
     * In the event that a data graph includes two graph points with the same price and time values
     * this integer should be used to order them (Occurs automatically for TreeSets the standard
     * ordered set that stores Graph Points. Each concrete implementation of this interface should
     * return a different integer value.
     * @return The integer number identifying this concrete implementation of the interface.
     */
    int getOrderingValue();
    
    /**
     * During Y-Line calculations a recursive algorithm is used and the data needs to be cached on a 
     * point by point basis. This accessor provides access to this points cache record.
     * @return A Recursion Cache object
     */
    RecursionCache getRecursionCache();
    
    /**
     * During Y-Line calculations a recursive algorithm is used and the data needs to be cached on a 
     * point by point basis. This accessor sets this points cache record.
     * @param newCache - A Recursion Cache generated by the Y-Line calculations
     * @return boolean True if the cache was accepted and stored, False otherwise.
     */
    boolean setRecursionCache(RecursionCache newCache);
}
