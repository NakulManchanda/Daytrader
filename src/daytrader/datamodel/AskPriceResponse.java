/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.utils.DTUtil;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * A Concrete implementation of the AbstractGraphPoint class that represents the 
 * stocks 'Ask' price at a given moment in time.
 * @author Roy
 */
public class AskPriceResponse extends AbstractGraphPoint {
    
    private double askPrice = 0.0d;
    
    /**
     * Default constructor required for object persistence
     */
    public AskPriceResponse(){
        //Needed to persist the object
    }
    
    private AskPriceResponse(long timestamp){
        createCalendarFromTimestamp(timestamp);
    }
    
    /**
     * Constructor which creates an Ask Price point at the specified price and 
     * time.
     * @param timestamp - The time at which the stock had this Ask price.
     * @param newPrice - The 'Ask' price for the stock at the given time.
     */
    public AskPriceResponse(long timestamp, double newPrice){
        this(timestamp);
        this.setAskPrice(newPrice * DTConstants.SCALE);
        this.open = this.askPrice;
        this.high = this.askPrice;
        this.low = this.askPrice;
        this.close = this.askPrice;
        this.WAP = this.askPrice;
    }
    
    /**
     * A constructor that produces a new AskPriceResponse object at the same 
     * price as its parameter 'target' but at a later time (how much later is specified by
     * the time parameter. 
     * This is useful as the stockbroker ONLY SENDS OUT CHANGES in the ask price. Bryn requires
     * a point every second and this constructor is used to create the next point when no 
     * data has been received from the stockbroker (ie. the ask price is unchanged).
     * @param target - The AskPriceRespons from which to draw the initial price and timestamp data
     * @param incTime - The amount to add to the initial timestamp in milliseconds. Typically
     * this is 1000 to advance the time point by a second.
     */
    public AskPriceResponse(AskPriceResponse target, long incTime) {
        this.calDate = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        long time = target.getCalDate().getTimeInMillis();
        time += incTime;
        this.calDate.setTimeInMillis(time);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
        this.date = formatter.format(this.calDate.getTime());
        double price = target.getAskPrice();
        this.setAskPrice(price);
        this.open = this.askPrice;
        this.high = this.askPrice;
        this.low = this.askPrice;
        this.close = this.askPrice;
        this.WAP = this.askPrice;
    }

    @Override
    protected String getTypeAsString() {
        return "AskPriceResponse";
    }

    /**
     * @return the askPrice
     */
    public double getAskPrice() {
        return askPrice;
    }

    /**
     * @param askPrice the askPrice to set
     */
    public final void setAskPrice(double askPrice) {
        this.askPrice = DTUtil.step1Rounding(askPrice).doubleValue();
    }

    @Override
    protected String customToCSVString(String baseCSV) {
        String result = "";
        if(null != baseCSV){
            //Appends 0 ask and actual bid price
            result = baseCSV 
                    + "," + Double.toString(this.askPrice / DTConstants.SCALE) 
                    + ",0";
        } else {
            result = baseCSV;
        }
        return result;
    }

    @Override
    public int getOrderingValue() {
        return 2;
    }
    
    
    
}
