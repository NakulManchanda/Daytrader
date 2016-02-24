/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * This point represents a piece of real time data received from the stock brokers API
 * (The real time data is delivered at 5 second intervals)
 * @author Roy
 */
public class RealTimeBarGraphPoint extends AbstractGraphPoint {
    
    /**
     * Default Constructor - Creates empty object
     */
    public RealTimeBarGraphPoint(){
    }
    
    /**
     * Constructor which accepts the data returned from the stock brokers API for 
     * a real time data request (5 Sec Bars) and builds a price / time point encapsulating this data.
     * @param reqId - The integer number identifying the historic data request
     * @param time - The timestamp for this Price / Time point
     * @param open - The securities 'open' price at this data and time
     * @param high - The securities 'highest' price at this data and time
     * @param low - The securities 'lowest' price at this data and time
     * @param close - The securities 'closing' price at this data and time
     * @param volume - The volume of shares traded at this data and time
     * @param wap - The Average Weighted Price over the time period (usually 5 sec for this classes data)
     * @param count - The number of trades made at this data and time
     */
    public RealTimeBarGraphPoint(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count){
        this.reqId = reqId;
        this.volume = volume;
        this.count = count;
        this.hasGaps = false;
        
        this.setValues(open * DTConstants.SCALE, high * DTConstants.SCALE, low * DTConstants.SCALE, close * DTConstants.SCALE, wap * DTConstants.SCALE);

        createCalendarFromTimestamp(time);
    }
    
    /**
     * Copy Constructor that deep copies the data in the target Price / Time point
     * but advances the time element by the incTime parameter
     * @param target - An AbstractGraphPoint from which to copy this objects data
     * @param incTime - the amount in milliseconds to add to the data's timestamp value
     */
    public RealTimeBarGraphPoint(AbstractGraphPoint target, long incTime) {
        this.open = target.getOpen();
        this.high = target.getHigh();
        this.low = target.getLow();
        this.close = target.getClose();
        this.volume = target.getVolume();
        this.count = target.getCount();
        this.WAP = target.getWAP();
        this.hasGaps = target.isHasGaps();
        this.calDate = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        long time = target.getCalDate().getTimeInMillis();
        time += incTime;
        this.calDate.setTimeInMillis(time);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
        this.date = formatter.format(this.calDate.getTime());
        this.reqId = target.getReqId();
    }

    @Override
    protected String getTypeAsString() {
        return "RealTimeBarGraphPoint";
    }

    @Override
    public int getOrderingValue() {
        return 1;
    }
}
