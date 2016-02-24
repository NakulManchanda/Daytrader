/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.LineDirectionEnum;
import java.util.TreeSet;

/**
 * An interface representing the abstract concept of a straight line drawn on a
 * price / time graph. Equivalent to the trend lines drawn on the Trader Workstation
 * graphs.
 * 
 * Please note the following:
 * A straight line requires two points to define it a start and end point.
 * Bryn refers to the start point as the 'C' point and the end as the 'E' point
 * and this class uses that convention. Additionally Bryn has the concept of a 
 * 'stand in point' that replaces the original 'C' or 'E' point in some cases.
 * This interface supports that concept by providing accessors for the original
 * 'C' and 'E' points as well as the 'StandIn C or E' points. Finally it provides
 * accessors for the 'Current C or E' points which provide the original point 
 * UNLESS a StandIn exists in which case they provide the stand in point.
 * @author Roy
 */
public interface IGraphLine extends Comparable<IGraphLine>{

    /**
     * Accessor method to retrieve the 'Current C point' for this GraphLine.
     * @return An AbstractGraphPoint representing the point on the price / time
     * graph at which this line starts
     */
    AbstractGraphPoint getCurrentC();

    /**
     * Accessor method to retrieve the 'Current E point' for this GraphLine.
     * @return An AbstractGraphPoint representing the point on the price / time
     * graph at which this line ends
     */
    AbstractGraphPoint getCurrentE();

    /**
     * Accessor to retrieve the 'original E point' of the line.
     * @return An AbstractGraphPoint representing the point on the price / time
     * graph at which this line 'originally' ends
     */
    AbstractGraphPoint getEndPoint();

    /**
     * The gradient of the line. For lines with start and end points on the same day
     * this is the classical change in price / change in time. However for lines that
     * extend across multiple trading days (such as Y-Lines) the non-trading time 
     * period does not count towards the change in time value.
     * @return double being the gradient (delta Y / delta X) of this line on the 
     * Price / Time graph
     */
    double getGradient();

    /**
     * Accessor to retrieve the price / time graph on which this line should be drawn
     * @return A BaseGraph of price / time points on which the line is drawn
     */
    BaseGraph<AbstractGraphPoint> getGraph();

    /**
     * This method retrieves a LineDirectionEnum that defines if the GraphLine is
     * rising, falling or horizontal
     * @return A LineDirectionEnum defining if the line rises, falls or is horizontal
     * on its price / time graph
     */
    LineDirectionEnum getLinedirection();

    /**
     * Given a point in time this method retrieves the price / time point that lies
     * on the line at that instant of time
     * @param timestamp - long being a timestamp for the point on the line
     * @return An AbstractGraphPoint (a price / time point) that encapsulates the
     * specified time and the price at that time that would lie on the line
     */
    AbstractGraphPoint getPointAtTime(long timestamp);

    /**
     * Given a point in time this method retrieves the price that lies
     * on the line at that instant of time
     * @param timestamp - long being a timestamp for the point on the line
     * @return double being the price value that lies on the line at the given time.
     */
    double getPriceAtTime(long timestamp);

    /**
     * Accessor to retrieve the price / time point that 'stands in' for the original
     * 'C' point.
     * @return The AbstractGraphPoint that has replaced the 'original C point' or NULL
     * if no such point exists
     */
    AbstractGraphPoint getStandInC();

    /**
     * Accessor to retrieve the price / time point that 'stands in' for the original
     * 'E' point.
     * @return The AbstractGraphPoint that has replaced the 'original E point' or NULL
     * if no such point exists
     */
    AbstractGraphPoint getStandInE();

    /**
     * Accessor to retrieve the price / time point which is the 'original C point'
     * @return The AbstractGraphPoint that originally represented the start of the line
     */
    AbstractGraphPoint getStartPoint();

    /**
     * Gradient calculations for lines that cross multiple days require a record of
     * the days on which the stock market was open for trading. This accessor retrieves
     * the current list of trading days in use for gradient calculations
     * @return A TreeSet listing the trading days (usually for the last week but may be longer)
     * Each day is represented by an integer number in the format YYYYMMDD
     */
    TreeSet<Integer> getTradingDays();

    /**
     * Method to test id the data encapsulated in the object represents a usable graph line
     * Test to ensure a start and end point exists and that a reference to the 
     * price / time graph on which this line is 'drawn' has been set
     * @return boolean True if the minimum data listed above is present and correct,
     * False otherwise.
     */
    boolean isValid();

    /**
     * Accessor method to set the 'original' end point for this line
     * @param endPoint - An AbstractGraphPoint representing the end of the Graph Line
     */
    void setEndPoint(AbstractGraphPoint endPoint);

    /**
     * Accessor method to set the price / time graph on which this line is 'drawn'
     * @param graph - A base graph to which this line applies
     */
    void setGraph(BaseGraph<AbstractGraphPoint> graph);

    /**
     * Accessor method to set the price / time point that should be used as the
     * start point of this line INSTEAD of its original start point.
     * @param standInC - the price / time point to be used in place of the original
     */
    void setStandInC(AbstractGraphPoint standInC);

    /**
     * Accessor method to set the price / time point that should be used as the
     * end point of this line INSTEAD of its original end point.
     * @param standInE - the price / time point to be used in place of the original
     */
    void setStandInE(AbstractGraphPoint standInE);

    /**
     * Accessor method to set the price / time point that should be used as the
     * start point of this line
     * @param startPoint - the price / time point to be used as the start point 
     * for the line
     */
    void setStartPoint(AbstractGraphPoint startPoint);

    /**
     * Accessor method to set the record of the stock market trading days. Needed 
     * for gradient calculations.
     * @param tradingDays A TreeSet of integer numbers representing the days on which
     * trading occurred on the market. Days are given as integer numbers in the format
     * YYYYMMDD
     */
    void setTradingDays(TreeSet<Integer> tradingDays);

    /**
     * Represents this object as a String giving details of the lines start, end and
     * stand in points
     * @return A String encapsulating the data described above.
     */
    @Override
    String toString();

    /**
     * All non-horizontal lines cross (intercept) the X axis of a price / time graph
     * This method identifies the timestamp at which the line crosses the X (time) axis.
     * @return a double being the timestamp, expressed as a double as it may be fractional
     */
    double xInterceptTime();

    /**
     * Retrieves the price at the start point of the line 
     * @return A double being the price of the start point of the line
     */
    double yInterceptPrice();
    
    /**
     * Factory method that makes a deep copy of this line for use by multiple threads
     * @return A new GraphLine object that encapsulates the same data as this line 
     * but is independent of the original
     */
    IGraphLine deepCopyLine();
    
    /**
     * Tests to see if the provided point is either the 'original C' or 'stand in C'
     * point for this line
     * @param potentialCPoint - The potential 'C' point to test.
     * @return Boolean True if the potential 'C' is either the 'original C' or 'stand in C',
     * False otherwise
     */
    boolean isACPoint(AbstractGraphPoint potentialCPoint);
    
    /**
     * Determines the point at which two graph lines intersect. 
     * @param targetLine An IGraphLine interface of the line to find the intersection with
     * @return An AbstractGraphPoint representing the point at which the two lines will intersect
     */
    AbstractGraphPoint getIntersect(IGraphLine targetLine);
}
