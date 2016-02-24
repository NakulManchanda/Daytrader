/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.utils.DTUtil;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Given a year, month and day this class can be used to create a Java Calender
 * object for the start and end of the trading day and every 30 min block
 * between. It can also create ArrayLists of strings suitable for submission to
 * the stock brokers API to request the historic data batches covering these
 * times.
 * @author Roy
 */
public final class StockExchangeHours {

    private Calendar startCalendar;
    private Calendar endCalendar;

    /**
     * Constructor that specifies the date to be used in generating Calendar objects
     * as integer numbers
     * @param year - integer representing the year
     * @param month  - integer representing the month
     * @param day - integer representing the day
     */
    public StockExchangeHours(int year, int month, int day) {
        this.setDate(year, month, day);
    }

    /**
     * Constructor that specifies the date to be used in generating Calendar objects
     * as a Java Date object
     * @param aDate - A Date object encapsulating the data required.
     */
    public StockExchangeHours(Date aDate) {
        Calendar aCalDate = Calendar.getInstance();
        aCalDate.setTime(aDate);
        this.setDate(aCalDate.get(Calendar.YEAR), aCalDate.get(Calendar.MONTH), aCalDate.get(Calendar.DATE));
    }

    /**
     * Accessor method to change the date value being worked with
     * @param year - integer representing the year
     * @param month - integer representing the month
     * @param day - integer representing the day
     */
    public void setDate(int year, int month, int day) {
        this.startCalendar = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        this.endCalendar = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        this.startCalendar.clear();
        this.endCalendar.clear();

        //Initialise to start of market trading
        this.startCalendar.set(Calendar.YEAR, year);
        this.startCalendar.set(Calendar.MONTH, month);
        this.startCalendar.set(Calendar.DATE, day);
        this.startCalendar.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
        this.startCalendar.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
        this.startCalendar.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);

        //Initialise to end of market trading
        this.endCalendar.set(Calendar.YEAR, year);
        this.endCalendar.set(Calendar.MONTH, month);
        this.endCalendar.set(Calendar.DATE, day);
        this.endCalendar.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_CLOSING_HOUR);
        this.endCalendar.set(Calendar.MINUTE, DTConstants.EXCH_CLOSING_MIN);
        this.endCalendar.set(Calendar.SECOND, DTConstants.EXCH_CLOSING_SEC);
    }

    /**
     * Retrieves a calendar object with the time trading starts on the market
     *
     * @return A Calendar encapsulating the date and time trading starts on the 
     * New York stock markets
     */
    public Calendar getStartCalendar() {
        return startCalendar;
    }

    /**
     * A Calendar encapsulating the date and time trading starts on the market
     * using the GMT time zone
     * @return A Calendar encapsulating the date and time trading starts on the 
     * New York stock markets
     */
    public Calendar getStartCalendarInGMT() {
        Calendar result = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        result.setTimeInMillis(this.startCalendar.getTimeInMillis());
        return result;
    }

    /**
     * Retrieves a calendar object with the time trading ends on the market
     *
     * @return A Calendar encapsulating the date and time trading ends on the 
     * New York stock markets
     */
    public Calendar getEndCalendar() {
        return endCalendar;
    }

    /**
     * A Calendar encapsulating the date and time trading ends on the market
     * using the GMT time zone
     * @return A Calendar encapsulating the date and time trading ends on the 
     * New York stock markets
     */
    public Calendar getEndCalendarInGMT() {
        Calendar result = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        result.setTimeInMillis(this.endCalendar.getTimeInMillis());
        return result;
    }
    
//    public ArrayList<String> get1HrsBatches(){
//        ArrayList<String> result = new ArrayList<String>();
//        Calendar gmtEndTime = this.getEndCalendarInGMT();
//        Calendar gmtCurrTime = this.getStartCalendarInGMT();
//        int intHr = 60 * 60 * 1000;
//        int intThirtyMin = 30 * 60 * 1000;                            //30 Min as milliseconds
//        //Advance 30 min (our first 1hr batch ends 30 min into the day)
//        gmtCurrTime.add(Calendar.MILLISECOND, intThirtyMin);
//        while(gmtCurrTime.compareTo(gmtEndTime) <= 0){
//            StringBuilder currString = new StringBuilder();
//            currString.append(gmtCurrTime.get(Calendar.YEAR));
//            Integer month = gmtCurrTime.get(Calendar.MONTH);
//            month++;
//            String strMonth;
//            switch (month) {
//                case 0:
//                case 1:
//                case 2:
//                case 3:
//                case 4:
//                case 5:
//                case 6:
//                case 7:
//                case 8:
//                case 9:
//                    strMonth = "0" + month.toString();
//                    break;
//                default:
//                    strMonth = month.toString();
//            }
//            currString.append(strMonth);
//            Integer day = gmtCurrTime.get(Calendar.DATE);
//            String strDay;
//            switch (day) {
//                case 0:
//                case 1:
//                case 2:
//                case 3:
//                case 4:
//                case 5:
//                case 6:
//                case 7:
//                case 8:
//                case 9:
//                    strDay = "0" + day.toString();
//                    break;
//                default:
//                    strDay = day.toString();
//            }
//            currString.append(strDay);
//            currString.append("  ");
//            Integer hours = gmtCurrTime.get(Calendar.HOUR_OF_DAY);
//            boolean inDaylightTime = DTConstants.EXCH_TIME_ZONE.inDaylightTime(gmtCurrTime.getTime());
//            if(inDaylightTime){
//                int dstSavings = DTConstants.EXCH_TIME_ZONE.getDSTSavings();
//                Double hrsAdded = dstSavings / DTConstants.MILLSECS_PER_HOUR;
//                hours += hrsAdded.intValue();
//            }
//            String strHrs;
//            switch (hours) {
//                case 0:
//                case 1:
//                case 2:
//                case 3:
//                case 4:
//                case 5:
//                case 6:
//                case 7:
//                case 8:
//                case 9:
//                    strHrs = "0" + hours.toString() + ":";
//                    break;
//                default:
//                    strHrs = hours.toString() + ":";
//            }
//            currString.append(strHrs);
//            Integer min = gmtCurrTime.get(Calendar.MINUTE);
//            String strMin;
//            switch (min) {
//                case 0:
//                case 1:
//                case 2:
//                case 3:
//                case 4:
//                case 5:
//                case 6:
//                case 7:
//                case 8:
//                case 9:
//                    strMin = "0" + min.toString() + ":";
//                    break;
//                default:
//                    strMin = min.toString() + ":";
//            }
//            currString.append(strMin);
//            Integer secs = gmtCurrTime.get(Calendar.SECOND);
//            String strSecs;
//            switch (secs) {
//                case 0:
//                case 1:
//                case 2:
//                case 3:
//                case 4:
//                case 5:
//                case 6:
//                case 7:
//                case 8:
//                case 9:
//                    strSecs = "0" + secs.toString();
//                    break;
//                default:
//                    strSecs = secs.toString();
//            }
//            currString.append(strSecs);
//            currString.append(" GMT");
//            //Write to results the stock broker time string
//            result.add(currString.toString());
//            //Advance 30 min
//            gmtCurrTime.add(Calendar.MILLISECOND, intHr);
//        }
//        return result;
//    }

    /**
     * The maximum length of time that we can get Historic Data from the 
     * stock broker API with a resolution of 1 second is 30 minutes. This method
     * generates an ArrayList of strings that contain the data needed to 
     * request ALL thirty minute batches in a trading day.
     * 
     * @return An ArrayList of strings containing the date and time information
     * to be sent to the stock broker to load the whole trading days data at a
     * resolution of 1 second. This will necessitate multiple requests
     */
    public ArrayList<String> get30MinBatches() {
        ArrayList<String> result = new ArrayList<String>();
        Calendar gmtEndTime = this.getEndCalendarInGMT();
        Calendar gmtCurrTime = this.getStartCalendarInGMT();
        int intThirtyMin = 30 * 60 * 1000;                            //30 Min as milliseconds
        //Advance 30 min (our first 30 min batch ends 30 min into the day)
        gmtCurrTime.add(Calendar.MILLISECOND, intThirtyMin);
        while (gmtCurrTime.compareTo(gmtEndTime) <= 0) {
            StringBuilder currString = new StringBuilder();
            currString.append(gmtCurrTime.get(Calendar.YEAR));
            Integer month = gmtCurrTime.get(Calendar.MONTH);
            month++;
            String strMonth;
            switch (month) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    strMonth = "0" + month.toString();
                    break;
                default:
                    strMonth = month.toString();
            }
            currString.append(strMonth);
            Integer day = gmtCurrTime.get(Calendar.DATE);
            String strDay;
            switch (day) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    strDay = "0" + day.toString();
                    break;
                default:
                    strDay = day.toString();
            }
            currString.append(strDay);
            currString.append("  ");
            Integer hours = gmtCurrTime.get(Calendar.HOUR_OF_DAY);
            boolean inDaylightTime = DTConstants.EXCH_TIME_ZONE.inDaylightTime(gmtCurrTime.getTime());
            if(inDaylightTime){
                int dstSavings = DTConstants.EXCH_TIME_ZONE.getDSTSavings();
                Double hrsAdded = dstSavings / DTConstants.MILLSECS_PER_HOUR;
                hours += hrsAdded.intValue();
            }
            String strHrs;
            switch (hours) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    strHrs = "0" + hours.toString() + ":";
                    break;
                default:
                    strHrs = hours.toString() + ":";
            }
            currString.append(strHrs);
            Integer min = gmtCurrTime.get(Calendar.MINUTE);
            String strMin;
            switch (min) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    strMin = "0" + min.toString() + ":";
                    break;
                default:
                    strMin = min.toString() + ":";
            }
            currString.append(strMin);
            Integer secs = gmtCurrTime.get(Calendar.SECOND);
            String strSecs;
            switch (secs) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    strSecs = "0" + secs.toString();
                    break;
                default:
                    strSecs = secs.toString();
            }
            currString.append(strSecs);
            currString.append(" GMT");
            //Write to results the stock broker time string
            result.add(currString.toString());
            //Advance 30 min
            gmtCurrTime.add(Calendar.MILLISECOND, intThirtyMin);
        }
        return result;
    }
    
    /**
     * The maximum length of time that we can get Historic Data from the 
     * stock broker API with a resolution of 1 second is 30 minutes. This method
     * generates an ArrayList of strings that contain the data needed to 
     * request ALL thirty minute batches in a trading day.
     * @return An ArrayList of strings containing the date and time information
     * to be sent to the stock broker to load the whole trading days data at a
     * resolution of 1 second. This will necessitate multiple requests
     * 
     * @param dateTime - A Calendar identifying the trading day for which data is required
     */
    public static ArrayList<String> getBatchesForDateTime(Calendar dateTime){
        ArrayList<String> result = new ArrayList<String>();
        if(null != dateTime){
            Calendar gmtEndCal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            gmtEndCal.setTimeInMillis(dateTime.getTimeInMillis());
            //Work out start of trading day
            Calendar gmtStartCal = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
            gmtStartCal.setTimeInMillis(dateTime.getTimeInMillis());
            gmtStartCal.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
            gmtStartCal.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
            gmtStartCal.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
            long currTime = gmtStartCal.getTimeInMillis();
            long endTime = gmtEndCal.getTimeInMillis();
            long lngThirtyMin = 30*60*1000;
            long diff = endTime - currTime;
            while(diff > lngThirtyMin){
                //Work out a 30 min batch
                //Increment curr time
                currTime += lngThirtyMin;
                //Create Calendar
                Calendar myCal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
                myCal.setTimeInMillis(currTime);
                //Convert to broker time
                String currBatch = DTUtil.convertCalToBrokerTime(myCal);
                //Add to batches
                result.add(currBatch);
                //recalc diff
                diff = endTime - currTime;
            }
            //Now add the last batch
            String lastBatch = DTUtil.convertCalToBrokerTime(gmtEndCal);
            result.add(lastBatch);
        }
        return result;
    }
}
