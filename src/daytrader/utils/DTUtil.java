/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.utils;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.GraphFlat;
import daytrader.datamodel.GraphFlatCollection;
import daytrader.datamodel.GraphFlatPair;
import daytrader.datamodel.Putup;
import daytrader.datamodel.RealTimeRunManager;
import daytrader.interfaces.IGraphFlat;
import daytrader.interfaces.IGraphLine;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TimeZone;
import java.util.TreeSet;
import rules.BrynRounding;

/**
 * This will be a collection of static functions that can be called on by any
 * part of the day trader application
 *
 * @author Roy
 */
public class DTUtil {
    
    private static final BrynRounding rounder = new BrynRounding();

    /**
     * Converts a date into a Java Calendar object using the system time zone.
     * Only used by debug display consider removing after development
     * @param date - The Date object to convert
     * @return A java.util.Calendar object that represents the date in the local time zone
     */
    public static Calendar dateToCalendar(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setTimeInMillis(date.getTime());
        return cal;
    }

    /**
     * Converts a date into a Java Calendar object using the specified time zone.
     * @param dteStart The Date object to convert
     * @param tz The TimeZone to be used for the final Calendar object
     * @return A java.util.Calendar object that represents the date in the specified Time Zone
     */
    public static Calendar dateToCalendar(Date dteStart, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.clear();
        int year = dteStart.getYear() + 1900;
        int month = dteStart.getMonth();
        DateFormat formatter = new SimpleDateFormat("dd");
        String strDay = formatter.format(dteStart);
        int day = Integer.parseInt(strDay);
        int hrs = dteStart.getHours();
        int min = dteStart.getMinutes();
        int secs = dteStart.getSeconds();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, hrs);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, secs);
        return cal;
    }

    /**
     * Sets the hours, min and seconds of the provided Calendar without changing the
     * day or time zone
     * @param cal - The calendar object to set
     * @param hrs - The hour value (24 hr clock) to set
     * @param min - The min value to set
     * @param sec - The sec value to set
     * @return The Calendar object modified to the given time of day
     */
    public static Calendar setCalendarTime(Calendar cal, int hrs, int min, int sec) {
        cal.set(Calendar.HOUR_OF_DAY, hrs);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, sec);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    /**
     * Creates a new Calendar object initialised with the values provided
     * @param year - The year value to set
     * @param month - The month value to set
     * @param day - The day value to set
     * @param hrs - The hour value (24 hr clock) to set
     * @param min - The min value to set
     * @param secs - The sec value to set
     * @param tz - The Time Zone to use
     * @return A Calendar object for the specified date and time in the given time zone
     */
    public static Calendar createCalendar(int year, int month, int day, int hrs, int min, int secs, TimeZone tz) {
        Calendar usaCal = Calendar.getInstance(tz);
        usaCal.clear();
        usaCal.set(year, month, day, hrs, min, secs);
        return usaCal;
    }

    /**
     * The Stockbroker requires dates to be provided in a certain format as a string.
     * This function takes in any calendar and converts its date time to the format
     * used by the stockbroker's API.
     * WARNING the stockbrokers API documentation says that all date+time values
     * should be in GMT (Greenwich Mean Time) but THIS IS NOT TRUE. In fact the time value
     * needs to be expressed as London local time (That is Java's "Europe/London" time zone).
     * Despite having to append GMT to the end of the string the actual time value is given for
     * the "Europe/London" which MAY NOT BE GMT during British Summer Time
     * @param cal - The Calendar to convert to a string
     * @return A String representing the Calendars date and time in a format acceptable
     * to the stockbroker's API.
     */
    public static String convertCalToBrokerTime(Calendar cal) {
        String result = "";
        Calendar gmtCal = DTUtil.convertToLondonCal(cal);
        SimpleDateFormat format1 = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        String timeFMString = format1.format(gmtCal.getTime()) + " GMT";
        result = timeFMString;
        return result;
    }

    /**
     * Given a Java Calendar object this function returns a new Calendar initialised
     * to the same date in the New York / NASDAQUE Time Zone and with its time
     * component set to the instant the stock market would open (Nominally this is
     * 09:30:00 in the "America/New_York" Time Zone).
     * @param aDate - A Calendar object initialised to the date for which the exchange opening time is required
     * @return A new Calendar object representing the date and time on which the New York stock market should
     * open on the day specified by the parameter
     */
    public static Calendar getExchOpeningCalendar(Calendar aDate) {
        Calendar result = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        if (null != aDate) {
            result.setTimeInMillis(aDate.getTimeInMillis());
            result.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
            result.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
            result.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
            result.set(Calendar.MILLISECOND, 0);
        }
        return result;
    }
    
    /**
     * Creates a Calendar object representing the time at which the New York stock market closes today
     * @return A Calendar initialised to today and set to the "America/New_York" which stores
     * the time that the stock market will close at.
     */
    public static Calendar getExchClosingTime() {
        Calendar result = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        result.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_CLOSING_HOUR);
        result.set(Calendar.MINUTE, DTConstants.EXCH_CLOSING_MIN);
        result.set(Calendar.SECOND, DTConstants.EXCH_CLOSING_SEC);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    /**
     * Given a Java Calendar object this function returns a new Calendar initialised
     * to the same date in the New York / NASDAQUE Time Zone and with its time
     * component set to the instant the stock market would CLOSE (Nominally this is
     * 16:00:00 in the "America/New_York" Time Zone).
     * @param aDate - A Calendar object initialised to the date for which the exchange closing time is required
     * @return A new Calendar object representing the date and time on which the New York stock market should
     * close on the day specified by the parameter
     */
    public static Calendar getExchClosingCalendar(Calendar aDate) {
        Calendar result = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        if (null != aDate) {
            result.setTimeInMillis(aDate.getTimeInMillis());
            result.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_CLOSING_HOUR);
            result.set(Calendar.MINUTE, DTConstants.EXCH_CLOSING_MIN);
            result.set(Calendar.SECOND, DTConstants.EXCH_CLOSING_SEC);
            result.set(Calendar.MILLISECOND, 0);
        }
        return result;
    }

    /**
     * Given a Java Calendar object this function creates a new Calendar object
     * initalised to the same date and time but in the "Europe/London" time zone.
     * NB API Documentation says stockbroker uses GMT but THIS IS WRONG the server
     * uses local London time.
     * 
     * @param cal - A Calendar object containing the date and time to convert to 
     * the London Time Zone
     * @return A new Calendar object representing the parameters date and time in the
     * "Europe/London" time zone
     */
    public static Calendar convertToLondonCal(Calendar cal) {
        long timeInMillis = cal.getTimeInMillis();
        Calendar gmtCal = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        gmtCal.clear();
        gmtCal.setTimeInMillis(timeInMillis);
        return gmtCal;
    }

    /**
     * Given a Java Calendar object this function creates a new Calendar object
     * initalised to the same date and time but in the "America/New_York" time zone.
     * @param cal - A Calendar object containing the date and time to convert to 
     * the New York Time Zone
     * @return A new Calendar object representing the parameters date and time in the
     * "America/New_York" time zone
     */
    public static Calendar convertToExchCal(Calendar cal) {
        long timeInMillis = cal.getTimeInMillis();
        Calendar exchCal = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        exchCal.clear();
        exchCal.setTimeInMillis(timeInMillis);
        return exchCal;
    }

    /**
     * Calculates the number of days between two timestamps
     * Used by DEPRICATED DataGraphLoader class remove when it is deleted
     * @param start the initial timestamp
     * @param end the final timestamp
     * @return long integer being the number of days between the two timestamps
     * part days round up
     */
    public static long daysBetween(long start, long end) {
        long result = 1l;
        if (end > start) {
            Long diff = end - start;
            Double days = diff / DTConstants.MILLSECS_PER_DAY;
            Integer wholeDays = days.intValue();
            if (0 == wholeDays) {
                wholeDays = 1;
            } else {
                if (0 != (days - wholeDays)) {
                    wholeDays++;
                }
            }
            result = wholeDays.longValue();
        }
        return result;
    }

    /**
     * This function makes a DEEP COPY of the provided Java Calendar
     * @param cal - The Calendar to copy
     * @return A NEW Java Calendar object initialised with the same date, time and time zone
     * as the parameter but independent of it
     */
    public static Calendar deepCopyCalendar(Calendar cal) {
        Calendar result = null;
        if (null != cal) {
            Calendar newCal = Calendar.getInstance(cal.getTimeZone());
            newCal.clear();
            newCal.set(Calendar.YEAR, cal.get(Calendar.YEAR));
            newCal.set(Calendar.MONTH, cal.get(Calendar.MONTH));
            newCal.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH));
            newCal.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY));
            newCal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE));
            newCal.set(Calendar.SECOND, cal.get(Calendar.SECOND));
            newCal.set(Calendar.MILLISECOND, cal.get(Calendar.MILLISECOND));
            result = newCal;
        }
        return result;
    }

    /**
     * This method calculates the gradient of a line across multiple days. The time 
     * component does not include days when no trading occurred on the market
     * @param aLine - The line for which the gradient is required
     * @param listOfTradingDays - A TreeSet providing the list of days when trading occurred
     * @return A double being the calculated gradient for this line.
     */
    public static double getGraidentBasedOnTradingDays(IGraphLine aLine, TreeSet<AbstractGraphPoint> listOfTradingDays) {
        double result = 0;
        if (null != aLine && null != listOfTradingDays) {
            AbstractGraphPoint myCurrStart = aLine.getCurrentC();
            AbstractGraphPoint myCurrEnd = aLine.getCurrentE();
            if (myCurrStart.getDateAsNumber() != myCurrEnd.getDateAsNumber()) {
//                TreeSet<AbstractGraphPoint> tDay = new TreeSet<AbstractGraphPoint>(AbstractGraphPoint.TimeComparator);
//                tDay.addAll(listOfTradingDays);
                //Calc P1
                //Get Market close time on start day
                Calendar endTrading = DTUtil.deepCopyCalendar(myCurrStart.getCalDate());
                endTrading.setTimeZone(DTConstants.EXCH_TIME_ZONE);
                endTrading.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_CLOSING_HOUR);
                endTrading.set(Calendar.MINUTE, DTConstants.EXCH_CLOSING_MIN);
                endTrading.set(Calendar.SECOND, DTConstants.EXCH_CLOSING_SEC);
                double p1 = endTrading.getTimeInMillis() - myCurrStart.getCalDate().getTimeInMillis();
                //double p1 = endTrading.getTimeInMillis() - (myCurrStart.getCalDate().getTimeInMillis() - DTConstants.MILLSECS_PER_HOUR);
                //Now calculate P2
                //Get Market open time on end day
                Calendar startTrading = DTUtil.deepCopyCalendar(myCurrEnd.getCalDate());
                startTrading.setTimeZone(DTConstants.EXCH_TIME_ZONE);
                startTrading.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
                startTrading.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
                startTrading.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
                double p2 = (myCurrEnd.getCalDate().getTimeInMillis() - startTrading.getTimeInMillis());
                //double p2 = (myCurrEnd.getCalDate().getTimeInMillis() - DTConstants.MILLSECS_PER_HOUR) - startTrading.getTimeInMillis();
                //Now calc P3
                //Get count of trading days from list
//                int currStartDay = myCurrStart.getDateAsNumber();
//                int currEndDay = myCurrEnd.getDateAsNumber();
//                NavigableSet<AbstractGraphPoint> subSet = new TreeSet<AbstractGraphPoint>();
//                for(AbstractGraphPoint currPoint : tDay){
//                    int currDay = currPoint.getDateAsNumber();
//                    if(currDay > currStartDay && currDay < currEndDay){
//                        subSet.add(currPoint);
//                    }
//                }
                NavigableSet<AbstractGraphPoint> subSet = listOfTradingDays.subSet(myCurrStart, false, myCurrEnd, false);
                ArrayList<AbstractGraphPoint> theSet = new ArrayList<AbstractGraphPoint>();
                theSet.addAll(subSet);
                for (AbstractGraphPoint currPoint : theSet) {
                    if (currPoint.getDateAsNumber() == myCurrStart.getDateAsNumber() || currPoint.getDateAsNumber() == myCurrEnd.getDateAsNumber()) {
                        subSet.remove(currPoint);
                    }
                }
                double dayCount = subSet.size();
                double p3 = dayCount * DTUtil.msPerTradingDay();

                //Sum all three parts as deltaX
                double deltaX = p1 + p2 + p3;
                double deltaY = myCurrEnd.getLastPrice() - myCurrStart.getLastPrice();

                //Gradient is deltaY / deltaX
                result = deltaY / deltaX;

                System.out.println("Delta Y = " + deltaY);
                System.out.println("Delta X = " + deltaX);
                System.out.println(aLine.toString());
            } else {
                result = aLine.getGradient();
                System.out.println(aLine.toString());
            }
        }
        return result;
    }

    /**
     * Calculates the total number of milliseconds in a trading day based on the
     * Exchange opening and Closing constants in DTConstants
     * @return long being the number of milliseconds in the trading day.
     */
    public static long msPerTradingDay() {
        long result = 0;
        Calendar dateStart = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        Calendar dateEnd = DTUtil.deepCopyCalendar(dateStart);
        dateStart.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
        dateStart.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
        dateStart.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
        dateEnd.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_CLOSING_HOUR);
        dateEnd.set(Calendar.MINUTE, DTConstants.EXCH_CLOSING_MIN);
        dateEnd.set(Calendar.SECOND, DTConstants.EXCH_CLOSING_SEC);
        result = dateEnd.getTimeInMillis() - dateStart.getTimeInMillis();
        return result;
    }

    /**
     * Given a data graph this function identifies all 'flats' on the graph. For the definition of a flat see
     * the findAllFlatsOfAtLeastLength(int length, BaseGraph<AbstractGraphPoint> graph) function in
     * this class
     * @param graph - The data graph to scan for 'flats'
     * @return - A collection of IGraphFlat objects representing the 'Flats' found on the graph
     */
    public static GraphFlatCollection<IGraphFlat> findAllFlats(BaseGraph<AbstractGraphPoint> graph) {
        //NB a 'FLAT' must be at least 2 seconds long
        GraphFlatCollection<IGraphFlat> result = DTUtil.findAllFlatsOfAtLeastLength(2, graph);
        return result;
    }

    /**
     * Given a data graph and a minimum 'length' this function identifies all 'flats' on the graph.
     * A 'Flat' occurs when stocks price has not changed for a specified period of time (in seconds). By default 
     * Bryn uses a two second flat and the findAllFlats(BaseGraph<AbstractGraphPoint> graph) function
     * in this class may be used to automatically apply the default; for other length values use this method
     * @param length - The time in seconds that the stocks price must be unchanged for a valid flat to exist
     * @param graph - A data graph of the stocks price movements
     * @return - A collection of IGraphFlat objects representing the 'Flats' found on the graph
     */
    public static GraphFlatCollection<IGraphFlat> findAllFlatsOfAtLeastLength(int length, BaseGraph<AbstractGraphPoint> graph) {
        GraphFlatCollection<IGraphFlat> result = new GraphFlatCollection<IGraphFlat>(graph);
        if (0 < length && graph != null) {
            if (1 < graph.size()) {
                //Get a descending iterator of graph points
                Iterator<AbstractGraphPoint> descIter = graph.descendingIterator();
                AbstractGraphPoint currPoint = descIter.next();
                GraphFlat currFlat = new GraphFlat(currPoint);
                while (descIter.hasNext()) {
                    currPoint = descIter.next();
                    if (!currFlat.addPoint(currPoint)) {
                        //Value has changed add currFlat to collection
                        if (currFlat.isAtLeastXLong(length)) {
                            result.add(currFlat);
                        }
                        //This point is the start of a new flat
                        currFlat = new GraphFlat(currPoint);
                    }
                }
                //If the last flat is at least the required length add it
                if (currFlat.isAtLeastXLong(length)) {
                    result.add(currFlat);
                }
            }
        }
        return result;
    }

    /**
     * This function generates a Java Calendar object for today with the time
     * values initialised to the time the stock exchange opens
     * @return A Java Calendar object
     */
    public static Calendar getExchOpeningTime() {
        Calendar result = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        result.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
        result.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
        result.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    /**
     * This function will create a Java Calendar object initialised to the date and
     * time of the first point in a data graph or to the stock market opening time if 
     * their is no data in the graph.
     * NB: We have observed that data does not always become available for a stock at
     * the expected stock market opening time. This function may be used to retrieve the
     * actual date & time a stock started to trade from its data graph
     * @param graph - A data graph of historic prices
     * @return A Java Calendar object initalised to the date and time of the first point 
     * of data in the provided graph or if no data exists to the stock market opening time
     * for today.
     */
    public static Calendar getExchOpeningTimeFromGraph(BaseGraph<AbstractGraphPoint> graph) {
        Calendar result = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        result.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
        result.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
        result.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
        if (null != graph && 0 < graph.size()) {
            AbstractGraphPoint first = graph.first();
            result.setTimeInMillis(first.getTimestamp());
            result.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
            result.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
            result.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
        }
        return result;
    }

    /**
     * Given any data point this function will construct a Java Calendar initialised to 
     * the data and time the stock market would have opened on the day the data point
     * relates to.
     * @param aPoint - Any graph data point
     * @return A Java Calendar object.
     */
    public static Calendar getExchOpeningTimeFromPoint(AbstractGraphPoint aPoint) {
        Calendar result = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        result.setTimeInMillis(aPoint.getTimestamp());
        result.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
        result.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
        result.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
        return result;
    }

    /**
     * Given a timestamp this function identifies the 'Pair' of flats so that the first flat starts before
     * the timestamp and the second ends after it and their are no flats between the two.
     * Simply given a timestamp it finds the pair of flats that 'straddle' that point. 
     * @param timestamp The point in time that must be straddled by the flat pair
     * @param graph A data graph to use to search for flats
     * @return A GraphFlatPair containing the two flats that straddle the time point or NULL if no
     * such pair can be found.
     */
    public static GraphFlatPair getFlatPairAroundTimePoint(long timestamp, BaseGraph<AbstractGraphPoint> graph) {
        GraphFlatPair result = null;
        if (null != graph && 0 < graph.size()) {
            GraphFlatCollection<IGraphFlat> allFlats = DTUtil.findAllFlats(graph);
            TreeSet<GraphFlatPair> flatPairs = allFlats.getFlatPairs();
            for (GraphFlatPair currPair : flatPairs) {
                long startTime = currPair.getFirstFlat().getEarliestPoint().getTimestamp();
                long endTime = currPair.getSecondFlat().getLatestPoint().getTimestamp();
                if (startTime <= timestamp && endTime >= timestamp) {
                    result = currPair;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Bryn has defined a two step rounding process to handle all numbers received from the
     * stock brokers API. Given any numeric value this function applies the first step of the 
     * rounding process and returns the result.
     * @param value - Any numeric value.
     * @return The rounded numeric value.
     */
    public static Number step1Rounding(Number value) {
        return rounder.performRounding(value);
    }

    /**
     *
     */
//    public static void saveAllGraphs() {
//        ArrayList<RealTimeRunRecord> runningRecords = DTConstants.getRunningRecords();
//        if (null != runningRecords && 0 < runningRecords.size()) {
//            ArrayList<BaseGraph<AbstractGraphPoint>> graphList = new ArrayList<BaseGraph<AbstractGraphPoint>>();
//            for (RealTimeRunRecord currRec : runningRecords) {
//                BaseGraph<AbstractGraphPoint> graph = currRec.getLoader().getGraph();
//                graphList.add(graph);
//            }
//
//            boolean blnError = false;
//            for (BaseGraph<AbstractGraphPoint> currGraph : graphList) {
//                if (null != currGraph) {
//                    String tickerCode = currGraph.getPutup().getTickerCode();
//                    String fName = "RTGraph_" + tickerCode + ".csv";
//                    File myFile = new File(fName);
//                    if (myFile.exists()) {
//                        myFile.delete();
//                    }
//                    BufferedWriter writer = null;
//                    try {
//                        myFile.createNewFile();
//                        FileWriter fos = new FileWriter(myFile);
//                        writer = new BufferedWriter(fos);
//                        writer.write(currGraph.toCSVString());
//                    } catch (FileNotFoundException ex) {
//                        Logger.getLogger(DebugDisplay.class.getName()).log(Level.SEVERE, null, ex);
//                        blnError = true;
//                        JOptionPane.showMessageDialog(null, ex.getMessage() + ": " + tickerCode, "File not found...", JOptionPane.ERROR_MESSAGE);
//                    } catch (IOException ex) {
//                        Logger.getLogger(DebugDisplay.class.getName()).log(Level.SEVERE, null, ex);
//                        blnError = true;
//                        JOptionPane.showMessageDialog(null, "Data Not Saved For: " + tickerCode, "Error...", JOptionPane.INFORMATION_MESSAGE);
//                    } finally {
//                        if (null != writer) {
//                            try {
//                                writer.close();
//                            } catch (IOException ex) {
//                                Logger.getLogger(DebugDisplay.class.getName()).log(Level.SEVERE, null, ex);
//                                blnError = true;
//                            }
//                        }
//                    }
//                } else {
//                    blnError = true;
//                    JOptionPane.showMessageDialog(null, "No Graph to save", "No Graph", JOptionPane.ERROR_MESSAGE);
//                }
//            }
//            if (!blnError) {
//                JOptionPane.showMessageDialog(null, "All data was saved", "Saved...", JOptionPane.INFORMATION_MESSAGE);
//            } else {
//                JOptionPane.showMessageDialog(null, "Error occured while saving real time data", "Error saving...", JOptionPane.ERROR_MESSAGE);
//            }
//        } else {
//            if (null == runningRecords) {
//                JOptionPane.showMessageDialog(null, "RunningRecords is NULL", "NULL", JOptionPane.ERROR_MESSAGE);
//            } else if (0 < runningRecords.size()) {
//                JOptionPane.showMessageDialog(null, "RunningRecords has 0 size", "ZERO", JOptionPane.ERROR_MESSAGE);
//            }
//        }
//    }

    /**
     * At various points it is useful to express a date as an integer number in the 
     * format YYYYMMDD. This is convenient for sorting and searching operations. This
     * utility function converts the integer number into a Java Calendar object. As
     * this format does not include a time element the calendars time is initialised
     * as 00:00:00.
     * @param intDate An integer number representing a date in the format YYYYMMDD
     * @param tz The Time Zone to use for the final calendar object
     * @return A Calendar initialised to the date represented by the integer number in the given time zone.
     * The time element is set to zero hundred hours.
     */
    public static Calendar convertIntDateToCalendar(Integer intDate, TimeZone tz) {
        Calendar result = null;
        if (null != intDate && null != tz) {
            result = Calendar.getInstance(tz);
            //Strings format should be YYYYMMDD
            String strDate = intDate.toString();
            //Convert to CSV string in format YYYY,MM,DD
            StringBuilder sb = new StringBuilder(strDate);
            sb.insert(strDate.length() - 2, ",");
            sb.insert(strDate.length() - 4, ",");
            String csvString = sb.toString();
            String[] split = csvString.split(",");
            if (3 == split.length) {
                //Extract Year, month, day data
                Integer year = Integer.parseInt(split[0]);
                Integer month = Integer.parseInt(split[1]);
                Integer day = Integer.parseInt(split[2]);
                //Java uses 0 based month so decriment by one
                month--;
                //Now build result for midnight (zero hundred hours) of the day
                result.clear();
                result.set(year, month, day, 0, 0, 0);
                result.set(Calendar.MILLISECOND, 0);
            }
        }
        return result;
    }

    /**
     * This utility function converts a Java Calendar object into an integer number
     * expressed in the format YYYYMMDD. This is often useful for sorting operations.
     * Any time element will be ignored.
     * @param calDate - The Calendar to convert to an integer number
     * @return - An integer number representing the date portion of the provided calendar
     * in the format YYYYMMDD. So for example the 26th August 2013 would be converted to
     * the integer number 20130826.
     */
    public static int convertCalendarToIntDate(Calendar calDate) {
        int result = 0;
        StringBuilder data = new StringBuilder("");
        int year = calDate.get(Calendar.YEAR);
        int month = calDate.get(Calendar.MONTH);
        month++;
        int day = calDate.get(Calendar.DAY_OF_MONTH);
        data.append(Integer.toString(year));
        switch (month) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                String strMonth = "0" + Integer.toString(month);
                data.append(strMonth);
                break;
            default:
                data.append(Integer.toString(month));
        }
        switch (day) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                String strDay = "0" + Integer.toString(day);
                data.append(strDay);
                break;
            default:
                data.append(Integer.toString(day));
        }
        result = Integer.parseInt(data.toString());
        return result;
    }

    /**
     * Given a start and end point and a graph to scan this function looks for a
     * point between the start and end that has a PB Value of at least
     * DTConstants.getScaledPBVALUE();
     * @param startPoint - A Graph point defining the point at which to start scanning the graph
     * @param endPoint  - A Graph Point defining the point at which to end scanning the graph
     * @param graph - The Graph of data to scan 
     * @return boolean True if a PB Point with a value of at least DTConstants.getScaledPBVALUE() was found.
     */
    public static boolean hasPBPointBetween(AbstractGraphPoint startPoint, AbstractGraphPoint endPoint, BaseGraph<AbstractGraphPoint> graph) {
        boolean result = false;
        if (null != startPoint && null != endPoint && null != graph && 0 < graph.size()) {
            //Ensure endPoint is after startPoint
            if (startPoint.getTimestamp() < endPoint.getTimestamp()) {
                //Get target value
                double pbValue = DTConstants.getScaledPBVALUE();
                double dblHighestWAP = endPoint.getWAP();
                double currWAP;
                double dblDiff;
                AbstractGraphPoint currPoint;
                //Subset the graph to deal only with points between the start and end
                NavigableSet<AbstractGraphPoint> subSet = graph.subSet(startPoint, true, endPoint, false);
                //Iterate through the set in reverse order (backwards in time) looking for a point with a WAP 
                //at least pbValue LESS than the highest WAP so far.
                Iterator<AbstractGraphPoint> descIter = subSet.descendingIterator();
                while(descIter.hasNext()){
                    //Get data for point
                    currPoint = descIter.next();
                    currWAP = currPoint.getWAP();
                    //Update highest WAP so far if needed
                    if(currWAP > dblHighestWAP){
                        dblHighestWAP = currWAP;
                    }
                    //Calculate diff between highest WAP so far and currWAP
                    dblDiff = dblHighestWAP - currWAP;
                    //Is the difference >= pbValue if so we have validated that there is a PB+ point between the two points
                    if(dblDiff >= pbValue){
                        result = true;
                        break;
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * Alias for the hasPBPointBetween(AbstractGraphPoint startPoint, AbstractGraphPoint endPoint, BaseGraph<AbstractGraphPoint> graph) that
     * takes its start and end points from the provided IGraphLine object.
     * @param aLine - The IGraphLine interface that provides the start and end points for scanning the graph
     * @param graph - The Graph of data to scan 
     * @return boolean True if a PB Point with a value of at least DTConstants.getScaledPBVALUE() was found.
     */
     public static boolean hasPBPointBetween(IGraphLine aLine, BaseGraph<AbstractGraphPoint> graph){
         return DTUtil.hasPBPointBetween(aLine.getStartPoint(), aLine.getEndPoint(), graph);
     }
     
     /**
      * Given a RealTimeRunManager this function builds a graph object that contains all the loaded historic
      * data points and all the historic points from the pre-loaded Y line graph (if one exists). The result is
      * a single graph that covers multiple days and can be associated with a Y-Line.
      * @param manager A RealTimeRun manager containing a putups pre-loaded Y-Line data and the historic data graph for today
      * @return A Data Graph spanning multiple days that includes the pre-loaded Y-Line data and todays historic data.
      * This is the UNION of the two data graphs
      */
     public static BaseGraph<AbstractGraphPoint> buildYLinePlusHistDataGraph(RealTimeRunManager manager){
         BaseGraph<AbstractGraphPoint> result = null;
         if(null != manager){
             //GetPutup
             Putup myPutup = manager.getMyPutup();
             if(null != myPutup){
                 BaseGraph<AbstractGraphPoint> preLoadedYLineGraph = myPutup.getPreLoadedYLineGraph();
                 BaseGraph<AbstractGraphPoint> graphHistoricData = manager.getGraphHistoricData();
                 result = graphHistoricData.replicateGraph();
                 result.clear();
                 result.addAll(graphHistoricData);
                 if(null != preLoadedYLineGraph && 0 < preLoadedYLineGraph.size()){
                     result.addAll(preLoadedYLineGraph);
                 }
             }
         }
         return result;
     }
}
