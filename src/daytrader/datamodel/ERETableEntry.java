/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * An ERE'd test sets limits on the amount of time that may have passed between
 * specific events of a Single Double pattern. The limits change over the course
 * of a trading day and this class encapsulates the start and end time for a
 * limit and the value of that limit at the start and end of the range.
 * @author Roy
 */
public class ERETableEntry implements Comparable<ERETableEntry>{
    
    private long startTime;
    private long endTime;
    
    private double rangeStart;
    private double rangeEnd;
    
    /**
     * Constructor that defines the start and end of a time range in milliseconds 
     * since the start of the trading day and a start and end value which should 
     * be scaled over the time range
     * @param newStartTime - The time this range starts in milliseconds since the
     * start of the trading day
     * @param newEndTime - The time this range ends in milliseconds since the
     * start of the trading day
     * @param newRangeStart - double being the 'value' at the start of the time range
     * @param newRangeEnd - double being the 'value' at the end of the time range
     */
    public ERETableEntry(long newStartTime, long newEndTime, double newRangeStart, double newRangeEnd){
        if(newStartTime <= newEndTime){
            this.startTime = newStartTime;
            this.endTime = newEndTime;
            if(newRangeStart <= newRangeEnd){
                this.rangeStart = newRangeStart;
                this.rangeEnd = newRangeEnd;
            }
        }
    }

    /**
     * Accessor method to retrieve the start time for this range in milliseconds since the
     * start of the trading day.
     * @return the startTime for this value range in milliseconds since start of trading day
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Accessor method to retrieve the end time for this range in milliseconds since the
     * start of the trading day.
     * @return the endTime for this value range in milliseconds since start of trading day
     */
    public long getEndTime() {
        return endTime;
    }
    
    /**
     * Tests if this ERETableEntry should apply at elapsedMS since the start of the trading day
     * @param elapsedMs - Milliseconds since the start of the trading day
     * @return boolean True if the elapsedMs falls between this ERETableEntries start
     * and end times, False otherwise.
     */
    public boolean appliesAt(long elapsedMs){
        boolean result = false;
        if(this.startTime <= elapsedMs && elapsedMs <= this.endTime){
            result = true;
        }
        return result;
    }
    
    /**
     * Retrieves the permitted maximum number of milliseconds between the low of 
     * the day and the single tip (also applicable to the double tip) in a Single Double
     * Pattern.
     * @param elapsedMs - The number of milliseconds since the start of the trading day
     * @return double being the max number of milliseconds from the low of the day 
     * to the single / double tip.
     */
    public double getSingleEREMilliseconds(long elapsedMs){
        double result = this.rangeStart;
        double dblTotalTimeRange = this.endTime - this.startTime;
        double dblIntoRange = elapsedMs - this.startTime;
        double fract = dblIntoRange / dblTotalTimeRange;
        result += (fract * (this.rangeEnd - this.rangeStart));
        return result;
    }

    @Override
    public int compareTo(ERETableEntry o) {
        int result = 0;
        if(null != o){
            long oEndTime = o.getEndTime();
            if(this.endTime < oEndTime){
                result = -1;
            } else if(this.endTime > oEndTime){
                result = 1;
            }
        }
        return result;
    }

    /**
     * The start value in milliseconds (ie SMALLEST number of milliseconds) that
     * are allowed between the low of the day and the single / double tips of a single
     * double pattern.
     * @return a double being the rangeStart value in milliseconds
     */
    public double getRangeStart() {
        return rangeStart;
    }

    /**
     * The start value in milliseconds (ie SMALLEST number of milliseconds) that
     * are allowed between the low of the day and the single / double tips of a single
     * double pattern.
     * @return a double being the rangeEnd value in milliseconds
     */
    public double getRangeEnd() {
        return rangeEnd;
    }
}
