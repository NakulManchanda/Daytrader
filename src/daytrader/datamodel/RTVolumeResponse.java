/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * This class encapsulates the data returned by an RTVolume response from the
 * Stock brokers API after a call to reqMktData. This function returns 'live'
 * real time data as trades are happening.
 *
 * @author Roy
 */
public class RTVolumeResponse extends AbstractGraphPoint {
    
    /**
     * Default Constructor - Creates empty object
     */
    public RTVolumeResponse(){
    }
    
    /**
     * Copy Constructor used to create a deep copy of an existing object of this class
     * @param target - The instance of the RTVolumeResponse to deep copy
     */
    public RTVolumeResponse(RTVolumeResponse target){
        this(target, 0);
    }

    /**
     * Constructor creates a new instance of this class containing the data provided by 
     * the target AbstractGraphPoint but increments the time component of the 
     * Price / Time point by the incTime parameter
     * @param target - An AbstractGraphPoint the data for which shall be deep copied
     * @param incTime - An amount to increment the time element by from the target point.
     */
    public RTVolumeResponse(AbstractGraphPoint target, long incTime) {
        this.open = target.getOpen();
        this.high = target.getHigh();
        this.low = target.getLow();
        this.close = target.getClose();
        this.volume = target.getVolume();
        this.count = target.getCount();
        this.WAP = target.getWAP();
        this.hasGaps = target.isHasGaps();
        this.calDate = Calendar.getInstance();
        long time = target.getCalDate().getTimeInMillis();
        time += incTime;
        this.calDate.setTimeInMillis(time);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
        this.date = formatter.format(this.calDate.getTime());
        this.reqId = target.getReqId();
    }

    /**
     * Constructor, the stock brokers API will deliver this objects data as a 
     * semi-colon separated string. This constructor will parse the provided
     * string and build a new instance of this class from it.
     * @param value - The string containing the data to parse.
     */
    public RTVolumeResponse(String value) {
        try {
            String[] data = value.split(";");
            double lastPrice = Double.parseDouble(data[0]);
            lastPrice *= DTConstants.SCALE;
            long timestamp = Long.parseLong(data[2]);
            Date aDate = new Date(timestamp);
            //this.open = lastPrice;
            //this.high = lastPrice;
            //this.low = lastPrice;
            //this.close = lastPrice;
            this.volume = 0;
            this.count = 0;
            //this.WAP = lastPrice;
            this.setValues(lastPrice, lastPrice, lastPrice, lastPrice, lastPrice);
            this.hasGaps = false;
            this.calDate = Calendar.getInstance();
            this.calDate.setTime(aDate);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
            this.date = formatter.format(aDate);
            this.reqId = 0;
        } catch (Exception ex) {
            this.reqId = 0;
            this.date = "";
            this.open = 0;
            this.high = 0;
            this.low = 0;
            this.close = 0;
            this.volume = 0;
            this.count = 0;
            this.WAP = 0;
            this.hasGaps = true;
            this.calDate = null;
        }
    }

    /**
     * Constructor, the stock brokers API will deliver this objects data as a 
     * semi-colon separated string. This constructor will parse the provided
     * string and build a new instance of this class from it.
     * @param newId - Unique request ID that generated the data for this object
     * @param value - The string containing the data to parse.
     */
    public RTVolumeResponse(int newId, String value) {
        this(value);
        this.reqId = newId;
    }

    @Override
    public Calendar getCalDate() {
        return this.calDate;
    }

    @Override
    public long getTimestamp() {
        return this.calDate.getTimeInMillis();
    }

    @Override
    public double getLastPrice() {
        return this.WAP;
    }

    @Override
    protected String getTypeAsString() {
        return "RequestMarketData";
    }

    @Override
    public int getOrderingValue() {
        return 0;
    }
}
