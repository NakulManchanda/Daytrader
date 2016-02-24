/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphPoint;
import daytrader.interfaces.IRoundFunction;
import daytrader.utils.DTUtil;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import rules.BrynRoundingStep2;
import rules.RoundToTenthCent;

/**
 * This class encapsulates a piece of historic data returned by the Stock
 * Brokers API. Note: this class has a natural ordering that is inconsistent
 * with equals. Natural ordering is based on timestamp but equals compares the
 * closing price
 *
 * @author Roy
 */
public class HistoricDataGraphPoint extends AbstractGraphPoint implements IGraphPoint {
    
    /**
     * Default Constructor - Creates empty object
     */
    public HistoricDataGraphPoint(){
    }
    
    /**
     * Constructor builds an historic data graph point using a timestamp and price
     * value. Hence this object represents a point on a Price / Time graph
     * @param timestamp - The timestap to be used to initialise the objects date
     * and time values
     * @param value - The price of a security at the point in time represented by the
     * timestamp
     */
    public HistoricDataGraphPoint(long timestamp, double value){
        RoundToTenthCent rounder = new RoundToTenthCent();
        this.reqId = 0;
        Date theDate = new Date(timestamp);
        DTUtil.dateToCalendar(theDate, TimeZone.getTimeZone("Europe/London"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
        this.date = formatter.format(theDate);
        this.open = (rounder.performRounding(value)).doubleValue();//value;
        this.high = (rounder.performRounding(value)).doubleValue();
        this.low = (rounder.performRounding(value)).doubleValue();
        this.close = (rounder.performRounding(value)).doubleValue();
        this.volume = 0;
        this.count = 0;
        this.WAP = (rounder.performRounding(value)).doubleValue();
        this.hasGaps = true;
        
        this.createCalendarFromTimestamp(timestamp);
        
    }

    /**
     * Constructor which accepts the data returned from the stock brokers API for 
     * an historic data request and builds a price / time point encapsulating this data.
     * 
     * @param reqId - The integer number identifying the historic data request
     * @param date - A String containing the data & time information for this point
     * @param open - The securities 'open' price at this data and time
     * @param high - The securities 'highest' price at this data and time
     * @param low - The securities 'lowest' price at this data and time
     * @param close - The securities 'closing' price at this data and time
     * @param volume - The volume of shares traded at this data and time
     * @param count - The number of trades made at this data and time
     * @param WAP - The Average Weighted Price over the time period (usually 1 sec for historic data)
     * @param hasGaps - boolean flag, True if their are gaps in the data, False otherwise
     */
    public HistoricDataGraphPoint(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        //RoundToTenthCent rounder = new RoundToTenthCent();
        this.reqId = reqId;
        this.date = date;
        //this.open = (rounder.performRounding(open * DTConstants.SCALE)).doubleValue();
        //this.high = (rounder.performRounding(high * DTConstants.SCALE)).doubleValue();
        //this.low = (rounder.performRounding(low * DTConstants.SCALE)).doubleValue();
        //this.close = (rounder.performRounding(close * DTConstants.SCALE)).doubleValue();
        this.volume = volume;
        this.count = count;
        //this.WAP = (rounder.performRounding(WAP * DTConstants.SCALE)).doubleValue();
        this.hasGaps = hasGaps;
        
        this.setValues(open * DTConstants.SCALE, high * DTConstants.SCALE, low * DTConstants.SCALE, close * DTConstants.SCALE, WAP * DTConstants.SCALE);

        createCalendar();
    }

    @Override
    public int getReqId() {
        return this.reqId;
    }

    /**
     * Accessor method to retrieve the string representing this objects date and time
     * component in a format acceptable to the stockbrokers API
     * @return A String representing this points date and time data.
     */
    public String getDate() {
        return date;
    }

    @Override
    public long getVolume() {
        return this.volume;
    }

    @Override
    public int getCount() {
        return this.count;
    }

    @Override
    public boolean isHasGaps() {
        return this.hasGaps;
    }

    @Override
    protected void setValues(double open, double high, double low, double close, double WAP) {
        super.setValues(open, high, low, close, WAP);
        //Now apply stage two rounding
        BrynRoundingStep2 rounder = new BrynRoundingStep2();
        this.open = rounder.performRounding(this.open).doubleValue();
        this.high = rounder.performRounding(this.high).doubleValue();
        this.low = rounder.performRounding(this.low).doubleValue();
        this.close = rounder.performRounding(this.close).doubleValue();
        this.WAP = rounder.performRounding(this.WAP).doubleValue();
    }
    
    

    @Override
    public Calendar getCalDate() {
        Calendar result = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        result.setTime(calDate.getTime());
        return result;
    }

    @Override
    public long getTimestamp() {
        return this.calDate.getTimeInMillis();
    }

//    @Override
//    public boolean equals(Object obj) {
//        boolean result = false;
//        if (obj instanceof HistoricDataGraphPoint) {
//            HistoricDataGraphPoint target = (HistoricDataGraphPoint) obj;
//            if (this.close == target.getClose()) {
//                result = true;
//            }
//        }
//        return result;
//    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + (int) (Double.doubleToLongBits(this.close) ^ (Double.doubleToLongBits(this.close) >>> 32));
        return hash;
    }

    @Override
    public int compareTo(IGraphPoint o) {
        int result = 0;
        long lngDiff = o.getTimestamp() - this.getTimestamp();
        if (0 < lngDiff) {
            result = -1;
        } else if (0 > lngDiff) {
            result = 1;
        }
        return result;
    }

    @Override
    public double getLastPrice() {
        double wap = this.getWAP();
        return wap;
    }
    
    /**
     * The 'Last' price for an historic data item is defined as the weighted average price
     * This allows you to retrieve that price and specify a rounding function that 
     * should be applied to the value.
     * @param rounder - The rounding function to use
     * @return double being the Weighted Average Price after rounding using the 
     * provided function
     */
    public double getLastPrice(IRoundFunction<Double> rounder) {
        double wap = this.getWAP();
        return rounder.performRounding(wap).doubleValue();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("");
        DateFormat gmtFormatter = DateFormat.getDateTimeInstance();
        DateFormat usFormatter = DateFormat.getDateTimeInstance();
        usFormatter.setTimeZone(DTConstants.EXCH_TIME_ZONE);
        gmtFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        String gmtFormat = gmtFormatter.format(this.calDate.getTime());
        String usFormat = usFormatter.format(this.calDate.getTime());
        result.append(gmtFormat);
        result.append(" GMT, ");
        result.append(usFormat);
        result.append(" Exch Time, Last Price = ");
        result.append(this.getLastPrice());
        //return this.date + ", Last Price = " + this.getLastPrice();
        return result.toString();
    }

    @Override
    protected String getTypeAsString() {
        return "HistoricDataGraphPoint";
    }

    @Override
    public int getOrderingValue() {
        return 5;
    }

}
