/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.utils.DTUtil;
import java.util.Calendar;
import java.util.Date;

/**
 * This class can be used to create dummy point for comparison purposes in
 * TreeSet operations. It is also used to fill a TWSAccounts processing queue
 * when a 'pacing violation' occurs. Forcing a 10 minute wait before further
 * historic requests can be made using that account.
 * @author Roy
 */
public class DummyGraphPoint extends AbstractGraphPoint {
    
    private double dblLastPrice;
    
    /**
     * Default constructor initialises object to 'now' with a zero price
     */
    public DummyGraphPoint(){
        this.dblLastPrice = 0.0d;
        this.calDate = Calendar.getInstance();
    }
    
    /**
     * Constructor initialises the object to a zero price value at the given time
     * @param timestamp - The timestamp used to initialise the date / time values
     */
    public DummyGraphPoint(long timestamp){
        this.calDate = Calendar.getInstance();
        this.calDate.clear();
        Date objDateTime = new Date(timestamp);
        this.calDate.setTime(objDateTime);
        this.dblLastPrice = 0.0d;
    }
    
    /**
     * Constructor initialises object to a give price / time value
     * @param timestamp - The timestamp used to initialise the date / time values
     * @param newPrice - double being the price value at the given time
     */
    public DummyGraphPoint(long timestamp, double newPrice){
        this(timestamp);
        this.dblLastPrice = DTUtil.step1Rounding(newPrice).doubleValue();
        this.open = this.dblLastPrice;
        this.high = this.dblLastPrice;
        this.low = this.dblLastPrice;
        this.close = this.dblLastPrice;
        this.WAP = this.dblLastPrice;
    }
    
    /**
     * Constructor initialises the object to a zero price value at the given date
     * and time
     * @param objNewCal - Java Calendar encapsulating a date / time value
     */
    public DummyGraphPoint(Calendar objNewCal){
        this(objNewCal.getTimeInMillis());
    }
    
    /**
     * Constructor initialises object to a give price / time value
     * @param objNewCal  - Java Calendar encapsulating a date / time value
     * @param newPrice - double being the price value at the given time
     */
    public DummyGraphPoint(Calendar objNewCal, double newPrice){
        this(objNewCal.getTimeInMillis(), newPrice);
    }
    
    /**
     * Constructor initialises the object to a zero price value at the given time
     * @param newDate - A Date object encapsulating a date / time value
     */
    public DummyGraphPoint(Date newDate){
        this(newDate.getTime());
    }
    
    /**
     * Constructor initialises object to a give price / time value
     * @param newDate - A Date object encapsulating a date / time value
     * @param newPrice - double being the price value at the given time
     */
    public DummyGraphPoint(Date newDate, double newPrice){
        this(newDate.getTime(), newPrice);
    }

    @Override
    public double getLastPrice() {
        return this.dblLastPrice;
    }

    @Override
    protected String getTypeAsString() {
        return "DummyGraphPoint";
    }

    @Override
    public int getOrderingValue() {
        return 4;
    }
}