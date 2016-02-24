/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.utils;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;
import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BarSizeSettingEnum;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.DTDurationEnum;
import daytrader.datamodel.DTPriceEnum;
import daytrader.datamodel.GraphLine;
import daytrader.datamodel.HistoricDataGraph;
import daytrader.datamodel.HistoricDataGraphPoint;
import daytrader.datamodel.MarketEnum;
import daytrader.datamodel.Putup;
import daytrader.datamodel.StockExchangeHours;
import daytrader.datamodel.WhatToShowEnum;
import daytradertasks.LoadHistoricDataBetweenPointsTaskDEPRICATED;
import daytradertasks.LoadHistoricDataPointBatchResult;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import rules.IdentifyPB3Points;

/**
 * DEPRICATED CLASS DO NOT USE IN PRODUCTION CODE
 * This was my initial class for obtaining data from the stock brokers API but accept in 
 * debugging code it is no longer used.
 * 
 * This class provides a means to generate an ArrayList of base graph objects
 * given a start and an end date. Each base graph object will represent a single
 * day of dataStore
 *
 * @author Roy
 */
public class DataGraphLoader implements EWrapper {

    private int intRTH = 1;
    private int intDateFormat = 1;
    private Calendar startDate;
    private Calendar endDate;
    private String ticker;
    private MarketEnum market = MarketEnum.NYSE;
    private int lastClientId = -1;
    private boolean m_bIsFAAccount;
    private String m_FAAcctCodes;
    private static int reqCounter = 0;
    //This attribute serves as a tempory store for the data used to get a previous days close
    private BaseGraph<AbstractGraphPoint> closingPointData;

    public MarketEnum getMarket() {
        return market;
    }

    public void setMarket(MarketEnum market) {
        this.market = market;
    }

    public final void setMarket(String exch) {
        try {
            MarketEnum value = MarketEnum.valueOf(exch);
            this.setMarket(value);
        } catch (IllegalArgumentException ex) {
            this.market = null;
        }
    }
    private EClientSocket m_client = new EClientSocket(this);
    private boolean blnLoading = false;
    private HashMap<Integer, BaseGraph<AbstractGraphPoint>> data;
    //private BaseGraph<AbstractGraphPoint> dataStore;

    public DataGraphLoader() {
        initialise();
    }

    public DataGraphLoader(Date newStartDate, Date newEndDate) {
        initialise();
        this.setStartDate(newStartDate);
        this.setEndDate(newEndDate);
    }

    public DataGraphLoader(String newTicker, String newMarket, Date newStartDate, Date newEndDate) {
        this(newStartDate, newEndDate);
        this.ticker = newTicker;
        this.setMarket(newMarket);
    }

    public Calendar getStartDate() {
        return startDate;
    }

    public void setStartDate(Calendar startDate) {
        this.startDate = startDate;
    }

    public final void setStartDate(Date newDate) {
        this.setStartDate(DataGraphLoader.startOfTradingGivenDay(newDate));
    }

    public Calendar getEndDate() {
        return endDate;
    }

    public void setEndDate(Calendar endDate) {
        this.endDate = endDate;
    }

    public final void setEndDate(Date newDate) {
        this.setEndDate(DataGraphLoader.endOfTradingGivenDay(newDate));
    }

    public boolean isLoading() {
        return this.blnLoading;
    }

    private boolean isUsable() {
        boolean result = false;
        if (null != this.startDate && null != this.endDate && null != this.ticker && null != this.market && null != this.data) {
            if (this.startDate.before(this.endDate)) {
                if (this.ticker.length() > 0) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * Given a date this returns a Calendar representing the start of trading
     * for the same Year, Month and Day in the New York time zone
     *
     * @param aDate - Date to find the start of trading
     * @return A Calendar being the start of trading in the NY time zone
     */
    public static Calendar startOfTradingGivenDay(Date aDate) {
        StockExchangeHours objTradingHrs = new StockExchangeHours(aDate);
        return objTradingHrs.getStartCalendar();
    }

    public static Calendar endOfTradingGivenDay(Date aDate) {
        StockExchangeHours objTradingHrs = new StockExchangeHours(aDate);
        return objTradingHrs.getEndCalendar();
    }
    private AtomicInteger requestCount;

    public BaseGraph<AbstractGraphPoint> loadDataToTime(Calendar cal) throws IOException {
        BaseGraph<AbstractGraphPoint> result = null;
        if (this.isUsable() && null != cal) {
            //This method call gets the last graph point of the previous day
            //It now also caches the downloaded 60 secs of data into
            AbstractGraphPoint prevClose = this.getPrevClose();
            Calendar gmtCal = DTUtil.convertToLondonCal(cal);
            //Make the connection
            this.connect();
            if (this.m_client.isConnected() && this.lastClientId >= 0) {
                Contract objContract = new Contract();
                objContract.m_conId = this.lastClientId;
                objContract.m_symbol = this.ticker;
                objContract.m_secType = "STK";
                objContract.m_exchange = "SMART";
                objContract.m_currency = "USD";
                objContract.m_primaryExch = this.market.toString();
                ArrayList<String> batchesForDateTime = StockExchangeHours.getBatchesForDateTime(gmtCal);
                this.requestCount = new AtomicInteger(0);
                for (String currBatchTime : batchesForDateTime) {
                    if (!this.isConnected()) {
                        throw new IOException("Connection has been lost");
                    }
                    this.requestCount.incrementAndGet();
                    this.reqHistoricalData(this.lastClientId,
                            objContract,
                            currBatchTime,
                            DTDurationEnum.S1800.toString(),
                            BarSizeSettingEnum.SEC1.toString(),
                            WhatToShowEnum.TRADES.toString(),
                            0,
                            intDateFormat);

                    //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        throw new IOException("Thread interrupted while transmitting data request");
                    }
                }
                boolean allLoaded = false;
                int maxWait = 60 * 1000;                  //Wait for no more than 30 secs
                int waitTime = 500;                     //Time for each wait in milliseconds
                //This function is a blocking operation and must block here until all results are received
                for (int i = 0; i < maxWait; i += waitTime) {
                    try {
                        if (0 == this.requestCount.get()) {
                            allLoaded = true;
                            break;
                        }
                        //Not finished loading block for another half second
                        Thread.sleep(waitTime);
                        System.out.println("Request Count = " + this.requestCount.get());
                    } catch (InterruptedException ex) {
                        this.disconnect();
                        throw new IOException("Thread interrupted while loading data");
                    }
                }
                if (allLoaded) {
                    //All results are back release the block and retrieve results
                    result = this.retrieveBaseGraph(this.lastClientId);
                    result.setStockTicker(ticker);
                    result.setExchange(market);
                    //Store Previous days close
                    if (null != prevClose) {
                        result.setPrevDayClose(prevClose);
                    }
                    if (null != this.closingPointData) {
                        result.setGraphClosePrevDayData(this.closingPointData);
                    }
                    this.disconnect();
                } else {
                    this.disconnect();
                    throw new IOException("Timed out waiting for stock broker server");
                }
            } else {
                throw new IOException("Connection failed");
            }
        }
        return result;
    }

    public BaseGraph<AbstractGraphPoint> loadData() throws IOException {
        BaseGraph<AbstractGraphPoint> result = null;
        if (this.isUsable()) {
            AbstractGraphPoint prevClose = this.getPrevClose();

            //Make the connection
            this.connect();
            if (this.m_client.isConnected() && this.lastClientId >= 0) {
                Contract objContract = new Contract();
                objContract.m_conId = this.lastClientId;
                objContract.m_symbol = this.ticker;
                objContract.m_secType = "STK";
                objContract.m_exchange = "SMART";
                objContract.m_currency = "USD";
                objContract.m_primaryExch = this.market.toString();
                long daysBetween = DTUtil.daysBetween(this.startDate.getTimeInMillis(), this.endDate.getTimeInMillis());
                Calendar currDay = Calendar.getInstance();
                currDay.setTime(this.startDate.getTime());
                long dayCounter = 0;
                this.requestCount = new AtomicInteger(0);
                while (dayCounter < daysBetween) {
                    StockExchangeHours smHrs = new StockExchangeHours(currDay.getTime());
                    ArrayList<String> thisDaysBatches = smHrs.get30MinBatches();
                    for (String currBatchTime : thisDaysBatches) {
                        if (!this.isConnected()) {
                            throw new IOException("Connection has been lost");
                        }
                        this.reqHistoricalData(this.lastClientId,
                                objContract,
                                currBatchTime,
                                DTDurationEnum.S1800.toString(),
                                BarSizeSettingEnum.SEC1.toString(),
                                WhatToShowEnum.TRADES.toString(),
                                intRTH,
                                intDateFormat);
                        this.requestCount.incrementAndGet();
                        //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ex) {
                            throw new IOException("Thread interrupted while transmitting data request");
                        }
                    }
                    dayCounter++;
                }
                boolean allLoaded = false;
                int maxWait = 30 * 1000;                  //Wait for no more than 30 secs
                int waitTime = 500;                     //Time for each wait in milliseconds
                //This function is a blocking operation and must block here until all results are received
                for (int i = 0; i < maxWait; i += waitTime) {
                    try {
                        if (0 == this.requestCount.get()) {
                            allLoaded = true;
                            break;
                        }
                        //Not finished loading block for another half second
                        Thread.sleep(waitTime);
                        System.out.println("Request Count = " + this.requestCount.get());
                    } catch (InterruptedException ex) {
                        this.disconnect();
                        throw new IOException("Thread interrupted while loading data");
                    }
                }
                if (allLoaded) {
                    //All results are back release the block and retrieve results
                    result = this.retrieveBaseGraph(this.lastClientId);
                    result.setStockTicker(ticker);
                    result.setExchange(market);
                    //Store Previous days close
                    if (null != prevClose) {
                        result.setPrevDayClose(prevClose);
                    }
                    this.disconnect();
                } else {
                    this.disconnect();
                    throw new IOException("Timed out waiting for stock broker server");
                }
            } else {
                throw new IOException("Connection failed");
            }
        }
        this.disconnect();
        return result;
    }

    private AbstractGraphPoint getPrevClose() throws IOException {
        AbstractGraphPoint result = null;
        if (this.isUsable()) {
            //Clear the closing point cache
            this.closingPointData = null;
            //Make the connection
            this.connect();
            if (this.m_client.isConnected() && this.lastClientId >= 0) {
                Contract objContract = new Contract();
                objContract.m_conId = this.lastClientId;
                objContract.m_symbol = this.ticker;
                objContract.m_secType = "STK";
                objContract.m_exchange = "SMART";
                objContract.m_currency = "USD";
                objContract.m_primaryExch = this.market.toString();
                long daysBetween = DTUtil.daysBetween(this.startDate.getTimeInMillis(), this.endDate.getTimeInMillis());
                Calendar currDay = Calendar.getInstance();
                currDay.setTime(this.startDate.getTime());
                long dayCounter = 0;
                this.requestCount = new AtomicInteger(0);
                //Make one request for last second of last trading day
                Calendar now = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
                now.setTimeInMillis(this.startDate.getTimeInMillis());
                DTUtil.setCalendarTime(now, DTConstants.EXCH_CLOSING_HOUR, DTConstants.EXCH_CLOSING_MIN, DTConstants.EXCH_CLOSING_SEC);
                now.add(Calendar.DAY_OF_MONTH, -1);

                SimpleDateFormat format1 = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                String timeFMString = format1.format(now.getTime()) + " GMT";
                String time = DTUtil.convertCalToBrokerTime(now);
                this.requestCount = new AtomicInteger(0);
                this.requestCount.incrementAndGet();
                this.reqHistoricalData(this.lastClientId,
                        objContract,
                        timeFMString,
                        DTDurationEnum.S60.toString(),
                        BarSizeSettingEnum.SEC1.toString(),
                        WhatToShowEnum.TRADES.toString(),
                        intRTH,
                        intDateFormat);

                boolean allLoaded = false;
                int maxWait = 30 * 1000;                  //Wait for no more than 30 secs
                int waitTime = 500;                     //Time for each wait in milliseconds
                //This function is a blocking operation and must block here until all results are received
                for (int i = 0; i < maxWait; i += waitTime) {
                    try {
                        if (0 == this.requestCount.get()) {
                            allLoaded = true;
                            break;
                        }
                        //Not finished loading block for another half second
                        Thread.sleep(waitTime);
                        System.out.println("Request Count = " + this.requestCount.get());
                    } catch (InterruptedException ex) {
                        this.disconnect();
                        throw new IOException("Thread interrupted while loading data");
                    }
                }
                if (allLoaded) {
                    //All results are back release the block and retrieve results
                    BaseGraph<AbstractGraphPoint> retrieveBaseGraph = this.retrieveBaseGraph(this.lastClientId);
                    if (retrieveBaseGraph.size() > 0) {
                        result = retrieveBaseGraph.last();
                    }
                    retrieveBaseGraph.setStockTicker(ticker);
                    retrieveBaseGraph.setExchange(market);
                    this.closingPointData = retrieveBaseGraph;
                    this.disconnect();
                } else {
                    this.disconnect();
                    throw new IOException("Timed out waiting for stock broker server");
                }
            } else {
                throw new IOException("Connection failed");
            }
        }
        return result;
    }

    public BaseGraph<AbstractGraphPoint> loadPreviousData() throws IOException {
        BaseGraph<AbstractGraphPoint> dataGraph = null;
        if (this.isUsable()) {
            int originalGraphId = this.lastClientId;
            dataGraph = this.retrieveBaseGraph(this.lastClientId);
            Calendar today = dataGraph.last().getCalDate();
            Calendar todayMinus3M = dataGraph.last().getCalDate();
            todayMinus3M.add(Calendar.MONTH, -3);
            BaseGraph<AbstractGraphPoint> highPoints = this.get3Month1DBarsHighPoints(dataGraph, today, null);
            if (null != highPoints) {
                BaseGraph<AbstractGraphPoint> Hr1HighPoints = this.get1HrBarsHighPoints(highPoints, endDate);
            }
            AbstractGraphPoint highestPoint = highPoints.getHighestPointSoFar();
        }
        return dataGraph;
    }

    public BaseGraph<AbstractGraphPoint> get1SecBarsLastPoints(BaseGraph<AbstractGraphPoint> dataGraph, Calendar cal) throws IOException {
        BaseGraph<AbstractGraphPoint> result = null;
        if (dataGraph != null) {
            BaseGraph<AbstractGraphPoint> hourBaseGraph = dataGraph;
            //Now retrieve all 1 sec points between highestHighPoint and end of 5 sec block
            this.connect();
            if (this.m_client.isConnected() && this.lastClientId >= 0) {
                try {
                    //Generate contract
                    String stockTicker = hourBaseGraph.getStockTicker();
                    MarketEnum exchange = hourBaseGraph.getExchange();
                    Contract objContract = new Contract();
                    objContract.m_conId = this.lastClientId;
                    objContract.m_symbol = stockTicker;
                    objContract.m_secType = "STK";
                    objContract.m_exchange = "SMART";
                    objContract.m_currency = "USD";
                    objContract.m_primaryExch = exchange.toString();
                    this.requestCount = new AtomicInteger(0);

                    //Match our contract to the one they use EXACTLY - START
                    objContract.m_expiry = "";
                    objContract.m_right = "";
                    objContract.m_multiplier = "";
                    objContract.m_localSymbol = "";
                    objContract.m_secIdType = "";
                    objContract.m_secId = "";
                    //Match our contract to the one they use EXACTLY - END
                    ArrayList<String> thisDaysBatches = new ArrayList<String>();
                    for (AbstractGraphPoint currPoint : dataGraph) {
                        Calendar calDate = currPoint.getCalDate();
                        calDate.add(Calendar.MINUTE, 15);
                        String time = DTUtil.convertCalToBrokerTime(calDate);
                        thisDaysBatches.add(time);
                    }
                    for (String currBatchTime : thisDaysBatches) {
                        //Make a 1 SEC request for each batch
                        if (!this.isConnected()) {
                            throw new IOException("Connection has been lost");
                        }
                        this.requestCount.incrementAndGet();
                        this.reqHistoricalData(this.lastClientId,
                                objContract,
                                currBatchTime,
                                DTDurationEnum.S900.toString(),
                                BarSizeSettingEnum.SEC1.toString(),
                                WhatToShowEnum.TRADES.toString(),
                                intRTH,
                                intDateFormat);

                        //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ex) {
                            throw new IOException("Thread interrupted while transmitting data request");
                        }
                    }
                    boolean allLoaded = false;
                    int maxWait = 60 * 1000;                  //Wait for no more than 60 secs
                    int waitTime = 500;                     //Time for each wait in milliseconds
                    //This function is a blocking operation and must block here until all results are received
                    for (int i = 0; i < maxWait; i += waitTime) {
                        try {
                            if (0 == this.requestCount.get()) {
                                allLoaded = true;
                                break;
                            }
                            //Not finished loading block for another half second
                            Thread.sleep(waitTime);
                            System.out.println("Request Count = " + this.requestCount.get());
                        } catch (InterruptedException ex) {
                            this.disconnect();
                            throw new IOException("Thread interrupted while loading data");
                        }
                    }
                    if (allLoaded) {
                        //All results are back release the block and retrieve results
                        result = this.retrieveBaseGraph(this.lastClientId);
                        result.setStockTicker(ticker);
                        result.setExchange(market);
                        this.disconnect();
                    } else {
                        this.disconnect();
                        throw new IOException("Timed out waiting for stock broker server");
                    }
                } finally {
                    this.disconnect();
                }
            }
        }
        if (null != result && result.size() > 1) {
            //Now remove non high points
            //Copy dataStore to avoid concurrent modification
            HistoricDataGraph data = new HistoricDataGraph();
            data.addAll(result);
            AbstractGraphPoint currHigh = data.last();
            Iterator<AbstractGraphPoint> descIter = data.descendingIterator();
            while (descIter.hasNext()) {
                AbstractGraphPoint currPoint = descIter.next();
                if (currPoint != currHigh) {
                    if (currPoint.getWAP() <= currHigh.getWAP()) {
                        result.remove(currPoint);
                    } else {
                        currHigh = currPoint;
                    }
                }
            }
        }
        return result;
    }

    public void manuallySetMonthCache(BaseGraph<AbstractGraphPoint> dataGraph) {
        TreeSet<AbstractGraphPoint> tempDaysList = new TreeSet<AbstractGraphPoint>();
        for (AbstractGraphPoint currPoint : dataGraph) {
            tempDaysList.add(currPoint);
        }
        this.monthCache = tempDaysList;
    }

    public ArrayList<GraphLine> generateProvisionalYLines(BaseGraph<AbstractGraphPoint> dataGraph) {
        ArrayList<GraphLine> result = new ArrayList<GraphLine>();
        ArrayList<GraphLine> tempList2 = new ArrayList<GraphLine>();
        if (null != dataGraph && dataGraph.size() > 1) {
            HashMap<Integer, AbstractGraphPoint> dataStore = new HashMap<Integer, AbstractGraphPoint>();
            for (AbstractGraphPoint currPoint : dataGraph) {
                Integer dayCode = currPoint.getDateAsNumber();
                if (!dataStore.containsKey(dayCode)) {
                    //First point for this day add currPoint
                    dataStore.put(dayCode, currPoint);
                } else {
                    //Check to see if currPoint WAP is higher than dataStore point, if it is replace dataStore entry
                    AbstractGraphPoint storedPoint = dataStore.get(dayCode);
                    if (currPoint.getWAP() > storedPoint.getWAP()) {
                        dataStore.put(dayCode, currPoint);
                    }
                }
            }
            //High points for every day
            //Convert to Graph
            HistoricDataGraph cPointGraph = new HistoricDataGraph();
            Set<Integer> keySet = dataStore.keySet();
            for (Integer key : keySet) {
                AbstractGraphPoint currPoint = dataStore.get(key);
                cPointGraph.add(currPoint);
            }
            //Get a graph iterator
            Iterator<AbstractGraphPoint> iterator = cPointGraph.iterator();
            while (iterator.hasNext()) {
                ArrayList<GraphLine> tempList = new ArrayList<GraphLine>();
                AbstractGraphPoint currPoint = iterator.next();
                NavigableSet<AbstractGraphPoint> subSet = dataGraph.subSet(currPoint, false, dataGraph.last(), true);
                for (AbstractGraphPoint subPoint : subSet) {
                    GraphLine graphLine = new GraphLine(currPoint, subPoint, dataGraph);
                    graphLine.setTradingDays(dataGraph.getTradingDays());
                    tempList.add(graphLine);
                }

                //Now go through and find lowest gradient
                GraphLine smallestGradient = null;
                for (GraphLine currLine : tempList) {
                    if (null != smallestGradient) {
                        double currSmallGradient = DTUtil.getGraidentBasedOnTradingDays(smallestGradient, this.getMonthCache());
                        double currLineGradient = DTUtil.getGraidentBasedOnTradingDays(currLine, this.getMonthCache());
                        StringBuilder msg = new StringBuilder("");
                        msg.append(currLine.toString());
                        msg.append(" : Gradient = ");
                        msg.append(Double.toString(currLineGradient));
                        System.out.println(msg.toString());
                        if (Math.abs(currLineGradient) <= Math.abs(currSmallGradient)) {
                            smallestGradient = currLine;
                        }
                    } else {
                        smallestGradient = currLine;
                        double currLineGradient = DTUtil.getGraidentBasedOnTradingDays(currLine, this.getMonthCache());
                        StringBuilder msg = new StringBuilder("");
                        msg.append(currLine.toString());
                        msg.append(" : Gradient = ");
                        msg.append(Double.toString(currLineGradient));
                        System.out.println(msg.toString());
                    }
                }
                //Add smallest gradient to the result
                tempList2.add(smallestGradient);
            }
        }
        //Filter out any Y Lines that start on the 'Current' Day
        //and store to the result
        Calendar todaysDate = dataGraph.getPutup().getTodaysDate();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        String strDate = formatter.format(todaysDate.getTime());
        int dayCode = Integer.parseInt(strDate);
        for (GraphLine currLine : tempList2) {
            AbstractGraphPoint currentC = currLine.getCurrentC();
            //C MUST be on previous day or its not a Y line
            if (currentC.getDateAsNumber() != dayCode) {
                //gradient of the line MUST be less than 0 (ie it must go down on a negative gradient for a long)
                if (currLine.getGradient() < 0) {
                    result.add(currLine);
                }
            }
        }
        return result;
    }

    public ArrayList<GraphLine> checkYLineStandIns2(ArrayList<GraphLine> provLines, BaseGraph<AbstractGraphPoint> dataGraph, boolean isRecursion) {
        ArrayList<GraphLine> result = new ArrayList<GraphLine>();
        if (null != provLines && null != dataGraph && 0 < provLines.size()) {
            if (!isRecursion) {
                //We cannot do this without 1 sec resolution as the graph will not let us work out PB3 or not without being fully loaded
                Putup putup = dataGraph.getPutup();
                //Work out earliest and latest C / E
                AbstractGraphPoint earliestC = null;
                AbstractGraphPoint latestE = null;
                for (GraphLine currLine : provLines) {
                    if (null != earliestC) {
                        long currCTime = currLine.getCurrentC().getTimestamp();
                        long earlyCTime = earliestC.getTimestamp();
                        if (currCTime < earlyCTime) {
                            earliestC = currLine.getCurrentC();
                        }
                    } else {
                        earliestC = currLine.getCurrentC();
                    }
                    if (null != latestE) {
                        long currETime = currLine.getCurrentE().getTimestamp();
                        long latestETime = latestE.getTimestamp();
                        if (currETime < latestETime) {
                            latestE = currLine.getCurrentE();
                        }
                    } else {
                        latestE = currLine.getCurrentE();
                    }
                }
                //We now know the earliest and latest C & E, ensure a 1 sec resolution on the graph between these
                LoadHistoricDataBetweenPointsTaskDEPRICATED loadTask = new LoadHistoricDataBetweenPointsTaskDEPRICATED(earliestC, latestE, putup);
                ExecutorService pool = Executors.newFixedThreadPool(1);
                CompletionService<LoadHistoricDataPointBatchResult> service = new ExecutorCompletionService(pool);
                service.submit(loadTask);
                LoadHistoricDataPointBatchResult data = null;
                try {
                    Future<LoadHistoricDataPointBatchResult> f = service.take();
                    data = f.get();
                } catch (InterruptedException ex) {
                    Logger.getLogger(DataGraphLoader.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ExecutionException ex) {
                    Logger.getLogger(DataGraphLoader.class.getName()).log(Level.SEVERE, null, ex);
                }
                if(null != data){
                    dataGraph.storeHistoricData(data.loadedPoints);
                }
            }
            //At this point the graph has a 1 sec resolution (if it was a recursive call then it already has 1 sec resolution)
            for (GraphLine currLine : provLines) {
                //1) Look for a PB3+ point between the E and C point
                //Get list of points between E and C
                NavigableSet<AbstractGraphPoint> pointList = dataGraph.subSet(currLine.getCurrentC(), true, currLine.getCurrentE(), true);
                boolean pb3Found = false;
                AbstractGraphPoint currPoint;
                Iterator<AbstractGraphPoint> descPoints = pointList.descendingIterator();
                while (descPoints.hasNext()) {
                    currPoint = descPoints.next();
                    //Get the PB3 value for this point if it is a PB3+ then mark found a break out of loop
                    int pbValue = IdentifyPB3Points.findPBValue(dataGraph, currPoint);
                    if (pbValue >= DTConstants.getScaledPBVALUE() / 100d) {
                        pb3Found = true;
                        break;
                    }
                }
                //If a pb3 was found then go no further this is a valid Y line
                if (pb3Found) {
                    result.add(currLine);
                } else {
                    //Look for a stand in
                    //Take the prov e as a new c
                    AbstractGraphPoint newC = currLine.getCurrentE();
                    //Subset the chart from the new C to now and look for potential Y Lines
                    NavigableSet<AbstractGraphPoint> subSet = dataGraph.subSet(newC, true, dataGraph.last(), true);
                    BaseGraph<AbstractGraphPoint> subGraph = dataGraph.replicateGraph();
                    subGraph.clear();
                    subGraph.addAll(subSet);
                    ArrayList<GraphLine> newProvYLines = this.generateProvisionalYLines(subGraph);
                    //If more than 1 line is found continue the loop
                    if (1 < newProvYLines.size()) {
                        //Check for a pb3+ between the C and E of each line
                        ArrayList<GraphLine> yLinesStandIns = this.checkYLineStandIns2(provLines, subGraph, true);
                        result.addAll(yLinesStandIns);
                    } else if (1 == newProvYLines.size()) {
                        //Look for a PB3 point between the C and E of the one line. IF one is found this is our Y line ELSE their is no YLine
                        GraphLine lastLine = newProvYLines.get(0);
                        NavigableSet<AbstractGraphPoint> lastPoints = dataGraph.subSet(lastLine.getCurrentC(), true, lastLine.getCurrentE(), true);
                        boolean pb3LastFound = false;
                        AbstractGraphPoint newCurrPoint;
                        Iterator<AbstractGraphPoint> newDescPoints = lastPoints.descendingIterator();
                        while (newDescPoints.hasNext()) {
                            newCurrPoint = newDescPoints.next();
                            //Get the PB3 value for this point if it is a PB3+ then mark found a break out of loop
                            int pbValue = IdentifyPB3Points.findPBValue(dataGraph, newCurrPoint);
                            if (pbValue >= DTConstants.getScaledPBVALUE() / 100d) {
                                pb3LastFound = true;
                                break;
                            }
                        }
                        //If a PB3 was found add this as the last line else THEIR IS NO Y LINE
                        if (pb3LastFound) {
                            result.add(lastLine);
                        }
                    }
                }
            }
        }
        return result;
    }

    public ArrayList<GraphLine> checkYLineStandIns(ArrayList<GraphLine> provLines, BaseGraph<AbstractGraphPoint> dataGraph) {
        ArrayList<GraphLine> result = new ArrayList<GraphLine>();
        if (null != provLines && null != dataGraph && 0 < provLines.size()) {
            for (GraphLine currLine : provLines) {
                //1) Look for a PB3+ point between the E and C point
                //Get list of points between E and C
                NavigableSet<AbstractGraphPoint> pointList = dataGraph.subSet(currLine.getCurrentC(), true, currLine.getCurrentE(), true);
                boolean pb3Found = false;
                AbstractGraphPoint currPoint;
                Iterator<AbstractGraphPoint> descPoints = pointList.descendingIterator();
                while (descPoints.hasNext()) {
                    currPoint = descPoints.next();
                    //Get the PB3 value for this point if it is a PB3+ then mark found a break out of loop
                    int pbValue = IdentifyPB3Points.findPBValue(dataGraph, currPoint);
                    if (pbValue >= DTConstants.getScaledPBVALUE() / 100d) {
                        pb3Found = true;
                        break;
                    }
                }
                //If a pb3 was found then go no further this is a valid Y line
                if (pb3Found) {
                    result.add(currLine);
                } else {
                    //Look for a stand in
                    //Take the prov e as a new c
                    AbstractGraphPoint newC = currLine.getCurrentE();
                    //Subset the chart from the new C to now and look for potential Y Lines
                    NavigableSet<AbstractGraphPoint> subSet = dataGraph.subSet(newC, true, dataGraph.last(), true);
                    BaseGraph<AbstractGraphPoint> subGraph = dataGraph.replicateGraph();
                    subGraph.clear();
                    subGraph.addAll(subSet);
                    ArrayList<GraphLine> newProvYLines = this.generateProvisionalYLines(subGraph);
                    //If more than 1 line is found continue the loop
                    if (1 < newProvYLines.size()) {
                        //Check for a pb3+ between the C and E of each line
                        ArrayList<GraphLine> yLinesStandIns = this.checkYLineStandIns(provLines, subGraph);
                        result.addAll(yLinesStandIns);
                    } else if (1 == newProvYLines.size()) {
                        //Look for a PB3 point between the C and E of the one line. IF one is found this is our Y line ELSE their is no YLine
                        GraphLine lastLine = newProvYLines.get(0);
                        NavigableSet<AbstractGraphPoint> lastPoints = dataGraph.subSet(lastLine.getCurrentC(), true, lastLine.getCurrentE(), true);
                        boolean pb3LastFound = false;
                        AbstractGraphPoint newCurrPoint;
                        Iterator<AbstractGraphPoint> newDescPoints = lastPoints.descendingIterator();
                        while (newDescPoints.hasNext()) {
                            newCurrPoint = newDescPoints.next();
                            //Get the PB3 value for this point if it is a PB3+ then mark found a break out of loop
                            int pbValue = IdentifyPB3Points.findPBValue(dataGraph, newCurrPoint);
                            if (pbValue >= DTConstants.getScaledPBVALUE() / 100d) {
                                pb3LastFound = true;
                                break;
                            }
                        }
                        //If a PB3 was found add this as the last line else THEIR IS NO Y LINE
                        if (pb3LastFound) {
                            result.add(lastLine);
                        }
                    }
                }
            }
        }
        return result;
    }

    public BaseGraph<AbstractGraphPoint> get15MinBarsHighPoints(BaseGraph<AbstractGraphPoint> dataGraph, Calendar cal) throws IOException {
        BaseGraph<AbstractGraphPoint> result = null;
        if (dataGraph != null) {
            BaseGraph<AbstractGraphPoint> hourBaseGraph = dataGraph;
            //Now retrieve all 1 min bars between highestHighPoint and end of hour
            this.connect();
            if (this.m_client.isConnected() && this.lastClientId >= 0) {
                try {
                    //Generate contract
                    String stockTicker = hourBaseGraph.getStockTicker();
                    MarketEnum exchange = hourBaseGraph.getExchange();
                    Contract objContract = new Contract();
                    objContract.m_conId = this.lastClientId;
                    objContract.m_symbol = stockTicker;
                    objContract.m_secType = "STK";
                    objContract.m_exchange = "SMART";
                    objContract.m_currency = "USD";
                    objContract.m_primaryExch = exchange.toString();
                    this.requestCount = new AtomicInteger(0);

                    //Match our contract to the one they use EXACTLY - START
                    objContract.m_expiry = "";
                    objContract.m_right = "";
                    objContract.m_multiplier = "";
                    objContract.m_localSymbol = "";
                    objContract.m_secIdType = "";
                    objContract.m_secId = "";
                    //Match our contract to the one they use EXACTLY - END
                    ArrayList<String> thisDaysBatches = new ArrayList<String>();
                    for (AbstractGraphPoint currPoint : dataGraph) {
                        Calendar calDate = currPoint.getCalDate();
                        calDate.add(Calendar.HOUR, 1);
                        String time = DTUtil.convertCalToBrokerTime(calDate);
                        thisDaysBatches.add(time);
                    }
                    for (String currBatchTime : thisDaysBatches) {
                        //Make a 1 hour request for each batch
                        if (!this.isConnected()) {
                            throw new IOException("Connection has been lost");
                        }
                        this.requestCount.incrementAndGet();
//                        this.reqHistoricalData(this.lastClientId,
//                                objContract,
//                                currBatchTime,
//                                DTDurationEnum.S3600.toString(),
//                                BarSizeSettingEnum.SEC5.toString(),
//                                WhatToShowEnum.TRADES.toString(),
//                                intRTH,
//                                intDateFormat);

                        this.reqHistoricalData(this.lastClientId,
                                objContract,
                                currBatchTime,
                                DTDurationEnum.S3600.toString(),
                                BarSizeSettingEnum.MIN15.toString(),
                                WhatToShowEnum.TRADES.toString(),
                                intRTH,
                                intDateFormat);

                        //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ex) {
                            throw new IOException("Thread interrupted while transmitting data request");
                        }
                    }
                    boolean allLoaded = false;
                    int maxWait = 30 * 1000;                  //Wait for no more than 30 secs
                    int waitTime = 500;                     //Time for each wait in milliseconds
                    //This function is a blocking operation and must block here until all results are received
                    for (int i = 0; i < maxWait; i += waitTime) {
                        try {
                            if (0 == this.requestCount.get()) {
                                allLoaded = true;
                                break;
                            }
                            //Not finished loading block for another half second
                            Thread.sleep(waitTime);
                            System.out.println("Request Count = " + this.requestCount.get());
                        } catch (InterruptedException ex) {
                            this.disconnect();
                            throw new IOException("Thread interrupted while loading data");
                        }
                    }
                    if (allLoaded) {
                        //All results are back release the block and retrieve results
                        result = this.retrieveBaseGraph(this.lastClientId);
                        result.setStockTicker(ticker);
                        result.setExchange(market);
                        this.disconnect();
                    } else {
                        this.disconnect();
                        throw new IOException("Timed out waiting for stock broker server");
                    }
                } finally {
                    this.disconnect();
                }
            }
        }
        if (null != result && result.size() > 1) {
            //Now remove non high points
            //Copy dataStore to avoid concurrent modification
            HistoricDataGraph dataStore = new HistoricDataGraph();
            dataStore.addAll(result);
            AbstractGraphPoint currHigh = dataStore.last();
            Iterator<AbstractGraphPoint> descIter = dataStore.descendingIterator();
            double tenthPerc = 1.0d - 0.001;
            while (descIter.hasNext()) {
                AbstractGraphPoint currPoint = descIter.next();
                if (currPoint != currHigh) {
                    //To be removed the high must be lower by AT LEAST a tenth of a percent
                    //if (currPoint.getHigh() <= currHigh.getHigh()) {
                    if (currPoint.getHigh() <= (currHigh.getHigh() * tenthPerc)) {
                        Calendar exchCal = DTUtil.convertToExchCal(currHigh.getCalDate());
                        int hour = exchCal.get(Calendar.HOUR_OF_DAY);
                        //We are IGNORING blocking point within first 30 min of the day (for 1 hour & 15 min bars)
                        if (hour != 9) {
                            result.remove(currPoint);
                        }
                    } else {
                        currHigh = currPoint;
                    }
                }
            }
        }
        return result;
    }

    public BaseGraph<AbstractGraphPoint> get1HrBarsHighPoints(BaseGraph<AbstractGraphPoint> dataGraph, Calendar endDate) throws IOException {
        BaseGraph<AbstractGraphPoint> result = null;
        if (dataGraph != null) {
            AbstractGraphPoint highestHighPoint = this.getPointByPrice(dataGraph, DTPriceEnum.HIGH);
            BaseGraph<AbstractGraphPoint> hourBaseGraph = dataGraph;
            //Now retrieve all 1 hour bars between highestHighPoint and start of day
            this.connect();
            if (this.m_client.isConnected() && this.lastClientId >= 0) {
                try {
                    //Generate contract
                    String stockTicker = hourBaseGraph.getStockTicker();
                    MarketEnum exchange = hourBaseGraph.getExchange();
                    Contract objContract = new Contract();
                    objContract.m_conId = this.lastClientId;
                    objContract.m_symbol = stockTicker;
                    objContract.m_secType = "STK";
                    objContract.m_exchange = "SMART";
                    objContract.m_currency = "USD";
                    objContract.m_primaryExch = exchange.toString();
                    this.requestCount = new AtomicInteger(0);

                    //Match our contract to the one they use EXACTLY - START
                    objContract.m_expiry = "";
                    objContract.m_right = "";
                    objContract.m_multiplier = "";
                    objContract.m_localSymbol = "";
                    objContract.m_secIdType = "";
                    objContract.m_secId = "";
                    //Match our contract to the one they use EXACTLY - END

                    Calendar openCal = DTUtil.setCalendarTime(highestHighPoint.getCalDate(), DTConstants.EXCH_OPENING_HOUR, DTConstants.EXCH_OPENING_MIN, DTConstants.EXCH_OPENING_SEC);
                    long daysBetween = DTUtil.daysBetween(openCal.getTimeInMillis(), endDate.getTimeInMillis());
                    Calendar currDay = Calendar.getInstance();
                    currDay.setTime(openCal.getTime());
                    currDay.add(Calendar.DAY_OF_MONTH, 1);
                    ArrayList<String> thisDaysBatches = new ArrayList<String>();

                    for (int i = 0; i < daysBetween; i++) {
                        String time = DTUtil.convertCalToBrokerTime(currDay);
                        thisDaysBatches.add(time);
                        currDay.add(Calendar.DAY_OF_MONTH, 1);
                    }
                    for (String currBatchTime : thisDaysBatches) {
                        //Make a 1 hour request for each batch
                        if (!this.isConnected()) {
                            throw new IOException("Connection has been lost");
                        }
                        this.requestCount.incrementAndGet();
                        this.reqHistoricalData(this.lastClientId,
                                objContract,
                                currBatchTime,
                                DTDurationEnum.D1.toString(),
                                BarSizeSettingEnum.HR1.toString(),
                                WhatToShowEnum.TRADES.toString(),
                                intRTH,
                                intDateFormat);

                        //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ex) {
                            throw new IOException("Thread interrupted while transmitting data request");
                        }
                    }
                    boolean allLoaded = false;
                    int maxWait = 30 * 1000;                  //Wait for no more than 30 secs
                    int waitTime = 500;                     //Time for each wait in milliseconds
                    //This function is a blocking operation and must block here until all results are received
                    for (int i = 0; i < maxWait; i += waitTime) {
                        try {
                            if (0 == this.requestCount.get()) {
                                allLoaded = true;
                                break;
                            }
                            //Not finished loading block for another half second
                            Thread.sleep(waitTime);
                            System.out.println("Request Count = " + this.requestCount.get());
                        } catch (InterruptedException ex) {
                            this.disconnect();
                            throw new IOException("Thread interrupted while loading data");
                        }
                    }
                    if (allLoaded) {
                        //All results are back release the block and retrieve results
                        result = this.retrieveBaseGraph(this.lastClientId);
                        result.setStockTicker(ticker);
                        result.setExchange(market);
                        this.disconnect();
                    } else {
                        this.disconnect();
                        throw new IOException("Timed out waiting for stock broker server");
                    }
                } finally {
                    this.disconnect();
                }
            }
        }
        if (null != result && result.size() > 1) {
            //Now remove non high points
            //Copy dataStore to avoid concurrent modification
            HistoricDataGraph dataStore = new HistoricDataGraph();
            dataStore.addAll(result);
            AbstractGraphPoint currHigh = dataStore.last();
            Iterator<AbstractGraphPoint> descIter = dataStore.descendingIterator();
            double tenthPerc = 1.0d - 0.001;
            while (descIter.hasNext()) {
                AbstractGraphPoint currPoint = descIter.next();
                if (currPoint != currHigh) {
                    //if (currPoint.getHigh() <= currHigh.getHigh()) {
                    //To be removed the high must be lower by AT LEAST a tenth of a percent
                    if (currPoint.getHigh() <= (currHigh.getHigh() * tenthPerc)) {
                        Calendar exchCal = DTUtil.convertToExchCal(currHigh.getCalDate());
                        int hour = exchCal.get(Calendar.HOUR_OF_DAY);
                        //We are IGNORING blocking point within first 30 min of the day (for 1 hour & 15 min bars)
                        if (hour != 9) {
                            result.remove(currPoint);
                        }
                    } else {
                        currHigh = currPoint;
                    }
                }
            }
        }
        return result;
    }
    private TreeSet<AbstractGraphPoint> monthCache;

    private TreeSet<AbstractGraphPoint> getMonthCache() {
        TreeSet<AbstractGraphPoint> result = new TreeSet<AbstractGraphPoint>();
        result.addAll(this.monthCache);
        return result;
    }

    public BaseGraph<AbstractGraphPoint> get3Month1DBarsHighPoints(BaseGraph<AbstractGraphPoint> dataGraph, Calendar endDate, Calendar timeLimit) throws IOException {
        BaseGraph<AbstractGraphPoint> result = null;
        if (null != dataGraph && null != endDate) {
            this.connect();
            if (this.m_client.isConnected() && this.lastClientId >= 0) {
                try {
                    //Generate contract
                    String stockTicker = dataGraph.getStockTicker();
                    MarketEnum exchange = dataGraph.getExchange();
                    Contract objContract = new Contract();
                    objContract.m_conId = this.lastClientId;
                    objContract.m_symbol = stockTicker;
                    objContract.m_secType = "STK";
                    objContract.m_exchange = "SMART";
                    objContract.m_currency = "USD";
                    objContract.m_primaryExch = exchange.toString();
                    this.requestCount = new AtomicInteger(0);

                    //Match our contract to the one they use EXACTLY - START
                    objContract.m_expiry = "";
                    objContract.m_right = "";
                    objContract.m_multiplier = "";
                    objContract.m_localSymbol = "";
                    objContract.m_secIdType = "";
                    objContract.m_secId = "";
                    //Match our contract to the one they use EXACTLY - END

                    //Make the request
                    StockExchangeHours smHrs = new StockExchangeHours(endDate.getTime());
                    Calendar gmtEndCal = smHrs.getEndCalendarInGMT();
                    String currBatchTime = DTUtil.convertCalToBrokerTime(gmtEndCal);
                    DTDurationEnum durationToUse = DTDurationEnum.M3;
                    if (null != timeLimit) {
                        durationToUse = DTDurationEnum.getDurationToCover(endDate, timeLimit);
                    }
                    String request = "Client ID: " + this.lastClientId
                            + ", Time: " + currBatchTime
                            + ", Duration: " + durationToUse.toString()
                            + ", BarSize: " + BarSizeSettingEnum.DAY1.toString()
                            + ", WhatToShow: " + WhatToShowEnum.TRADES.toString()
                            + ", RTH: " + intRTH
                            + ", DateFormat: " + intDateFormat;
                    System.out.println(request);
                    this.reqHistoricalData(this.lastClientId,
                            objContract,
                            currBatchTime,
                            durationToUse.toString(),
                            BarSizeSettingEnum.DAY1.toString(),
                            WhatToShowEnum.TRADES.toString(),
                            intRTH,
                            intDateFormat);
                    this.requestCount.incrementAndGet();

                    boolean allLoaded = false;
                    int maxWait = 30 * 1000;                  //Wait for no more than 30 secs
                    int waitTime = 500;                     //Time for each wait in milliseconds
                    //This function is a blocking operation and must block here until all results are received
                    for (int i = 0; i < maxWait; i += waitTime) {
                        try {
                            if (0 == this.requestCount.get()) {
                                allLoaded = true;
                                break;
                            }
                            //Not finished loading block for another half second
                            Thread.sleep(waitTime);
                            System.out.println("Request Count = " + this.requestCount.get());
                        } catch (InterruptedException ex) {
                            throw new IOException("Thread interrupted while loading data");
                        }
                    }
                    //Check we have not timed out and all dataStore was loaded
                    if (allLoaded) {
                        //All results are back release the block and retrieve results
                        result = this.retrieveBaseGraph(this.lastClientId);
                        result.setStockTicker(stockTicker);
                        result.setExchange(exchange);
                    } else {
                        throw new IOException("Timed out waiting for stock broker server");
                    }
                } finally {
                    this.disconnect();
                }
            }
        }
        if (null != result && 0 < result.size()) {
            TreeSet<AbstractGraphPoint> tempDaysList = new TreeSet<AbstractGraphPoint>();
            for (AbstractGraphPoint currPoint : result) {
                tempDaysList.add(currPoint);
            }
            this.monthCache = tempDaysList;
        }
        return result;
    }

    private AbstractGraphPoint getPointByPrice(BaseGraph<AbstractGraphPoint> graph, DTPriceEnum pointType) {
        AbstractGraphPoint result = null;
        if (null != graph && null != pointType) {
            result = DTPriceEnum.getBestPrice(graph, pointType);
        }
        return result;
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        throw new UnsupportedOperationException("Not supported yet. tickPrice"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        throw new UnsupportedOperationException("Not supported yet. tickSize"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        throw new UnsupportedOperationException("Not supported yet. tickOptionComputation"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        throw new UnsupportedOperationException("Not supported yet. tickGeneric"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        throw new UnsupportedOperationException("Not supported yet. tickString"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
        throw new UnsupportedOperationException("Not supported yet. tickEFP"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        throw new UnsupportedOperationException("Not supported yet. orderStatus"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        throw new UnsupportedOperationException("Not supported yet. openOrder"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void openOrderEnd() {
        throw new UnsupportedOperationException("Not supported yet. openOrderEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        throw new UnsupportedOperationException("Not supported yet. updateAccountValue"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
        throw new UnsupportedOperationException("Not supported yet. updatePortfolio"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateAccountTime(String timeStamp) {
        throw new UnsupportedOperationException("Not supported yet. updateAccountTime"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountDownloadEnd(String accountName) {
        throw new UnsupportedOperationException("Not supported yet. accountDownloadEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void nextValidId(int orderId) {
        BaseGraph<AbstractGraphPoint> aGraph = this.retrieveBaseGraph(this.lastClientId);
        aGraph.setNextValidOrderId(orderId);
        System.out.println("Order ID set to: " + orderId);
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        throw new UnsupportedOperationException("Not supported yet. contractDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        throw new UnsupportedOperationException("Not supported yet. bondContractDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void contractDetailsEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet. contractDetailsEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        throw new UnsupportedOperationException("Not supported yet. execDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void execDetailsEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet. execDetailsEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        throw new UnsupportedOperationException("Not supported yet. updateMktDepth"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size) {
        throw new UnsupportedOperationException("Not supported yet. updateMktDepthL2"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        throw new UnsupportedOperationException("Not supported yet. updateNewsBulletin"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void managedAccounts(String accountsList) {
        //This just prints the account list on connection (need to code something for this callback and I can see it working with this)
        m_bIsFAAccount = true;
        m_FAAcctCodes = accountsList;
        String msg = EWrapperMsgGenerator.managedAccounts(accountsList);
        System.out.println(msg);
    }

    @Override
    public void receiveFA(int faDataType, String xml) {
        throw new UnsupportedOperationException("Not supported yet. receiveFA"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        if (date.contains("finished")) {
            this.requestCount.decrementAndGet();
        }
        //While I work on getting the whole day just spam the dataStore to the console
        BaseGraph<AbstractGraphPoint> aGraph = this.retrieveBaseGraph(reqId);
        System.out.println("ID=" + reqId
                + ", " + date
                + ", open=" + open
                + ", high=" + high
                + ", low=" + low
                + ", close=" + close
                + ", volume=" + volume
                + ", count=" + count
                + ", WAP=" + WAP
                + ", hasGaps=" + hasGaps);
        if (null != aGraph) {
            if (!date.contains("finished")) {
                HistoricDataGraphPoint newItem = new HistoricDataGraphPoint(reqId, date, open, high, low, close, volume, count, WAP, hasGaps);
                aGraph.add(newItem);
            }
        } else {
            System.err.println("Data LOST for : " + date);
        }
    }

    @Override
    public void scannerParameters(String xml) {
        throw new UnsupportedOperationException("Not supported yet. scannerParameters"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
        throw new UnsupportedOperationException("Not supported yet. scannerData"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void scannerDataEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet. scannerDataEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        throw new UnsupportedOperationException("Not supported yet. realtimeBar"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void currentTime(long time) {
        throw new UnsupportedOperationException("Not supported yet. currentTime"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fundamentalData(int reqId, String data) {
        throw new UnsupportedOperationException("Not supported yet. fundamentalData"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {
        throw new UnsupportedOperationException("Not supported yet. deltaNeutralValidation"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet. tickSnapshotEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
        throw new UnsupportedOperationException("Not supported yet. marketDataType"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {
        throw new UnsupportedOperationException("Not supported yet. commissionReport"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(Exception e) {
        e.printStackTrace();
        System.err.println("Error invoked: " + e.getMessage());
    }

    @Override
    public void error(String str) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        switch (errorCode) {
            case 502:
                System.err.println("Not Connected to stock broker");
                break;
            case 162:
                if (null != this.requestCount && this.requestCount.get() > 0) {
                    this.requestCount.decrementAndGet();
                }
                System.err.println("No: Requests " + DataGraphLoader.reqCounter + " Error No: " + errorCode + " Message = " + errorMsg);
                break;
            default:
                System.err.println("Error No: " + errorCode + " Message = " + errorMsg);
        }
    }

    @Override
    public void connectionClosed() {
        System.err.println("THE CONNECTION HAS BEEN CLOSED!!!!");
    }

    private void initialise() {
        this.data = new HashMap<Integer, BaseGraph<AbstractGraphPoint>>();
    }

    public void connect() throws IOException {
        if (!this.isConnected()) {
            int clientId = DTConstants.getConId();
            HistoricDataGraph newGraph = new HistoricDataGraph();

            this.m_client.eConnect("", DTConstants.CONNECTION_PORT_ACC_2, clientId);
            if (m_client.isConnected()) {
                this.data.put(clientId, newGraph);
                this.lastClientId = clientId;
            } else {
                throw new IOException("Could not connect to stock broker");
            }
        }
    }

    public void disconnect() {
        m_client.eDisconnect();
    }

    /**
     * @return the ticker
     */
    public String getTicker() {
        return ticker;
    }

    /**
     * @param ticker the ticker to set
     */
    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    private BaseGraph<AbstractGraphPoint> retrieveBaseGraph(int id) {
        return this.data.get(id);
    }

    public boolean isConnected() {
        boolean result = false;
        if (null != this.m_client) {
            result = this.m_client.isConnected();
        }
        return result;
    }

    public synchronized void reqHistoricalData(int tickerId, Contract contract,
            String endDateTime, String durationStr,
            String barSizeSetting, String whatToShow,
            int useRTH, int formatDate) {
        this.m_client.reqHistoricalData(tickerId,
                contract,
                endDateTime,
                durationStr,
                barSizeSetting,
                whatToShow,
                useRTH,
                formatDate);
        DataGraphLoader.reqCounter++;
    }

    @Override
    public void position(String account, Contract contract, int pos, double avgCost) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void positionEnd() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
