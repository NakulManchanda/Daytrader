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
 * stocks 'Bid' price at a given moment in time.
 * @author Roy
 */
public class BidPriceResponse extends AbstractGraphPoint {
    
    private double bidPrice;
    
    /**
     * Default constructor initialises object with a bid price of zero.
     */
    public BidPriceResponse(){
        //Needed to persist the object
        this.bidPrice = 0.0d;
    }
    
    private BidPriceResponse(long timestamp){
        this();
        createCalendarFromTimestamp(timestamp);
    }
    
    /**
     * Given a timestamp and a new bid price this constructor initialises the 
     * object to reflect the provided price / time point.
     * @param timestamp - long being a timestamp for this price / time point
     * @param newPrice - double being the price for this price / time point
     */
    public BidPriceResponse(long timestamp, double newPrice){
        this(timestamp);
        this.setBidPrice(newPrice * DTConstants.SCALE);
        this.open = this.bidPrice;
        this.high = this.bidPrice;
        this.low = this.bidPrice;
        this.close = this.bidPrice;
        this.WAP = this.bidPrice;
    }
    
    /**
     * Copy constructor, duplicates the provided price / time point but advances 
     * the time element by the incTime parameter
     * @param target - The BidPriceResponse object to duplicate
     * @param incTime - long being the amount to add to the time component.
     */
    public BidPriceResponse(BidPriceResponse target, long incTime) {
        this.calDate = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        long time = target.getCalDate().getTimeInMillis();
        time += incTime;
        this.calDate.setTimeInMillis(time);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
        this.date = formatter.format(this.calDate.getTime());
        double price = target.getBidPrice();
        this.setBidPrice(price);
        this.open = this.bidPrice;
        this.high = this.bidPrice;
        this.low = this.bidPrice;
        this.close = this.bidPrice;
        this.WAP = this.bidPrice;
    }

    @Override
    protected String getTypeAsString() {
        return "BidPriceResponse";
    }

    /**
     * Accessor to retrieve the Bid price of this object
     * @return double being the stored bid price
     */
    public double getBidPrice() {
        return bidPrice;
    }

    /**
     * Accessor to set the Bid price of this object
     * @param bidPrice double being the new bid price to set
     */
    public final void setBidPrice(double bidPrice) {
        this.bidPrice = DTUtil.step1Rounding(bidPrice).doubleValue();
    }
    
    @Override
    protected String customToCSVString(String baseCSV) {
        String result = "";
        if(null != baseCSV){
            //Appends 0 ask and bid prices
            result = baseCSV 
                    + ",0"
                    + "," + Double.toString(this.bidPrice / DTConstants.SCALE);
        } else {
            result = baseCSV;
        }
        return result;
    }

    @Override
    public int getOrderingValue() {
        return 3;
    }
    
}
