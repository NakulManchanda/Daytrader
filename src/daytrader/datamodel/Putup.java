/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import static daytrader.datamodel.TVL.FALSE;
import static daytrader.datamodel.TVL.INDETERMINATE;
import static daytrader.datamodel.TVL.TRUE;
import daytrader.gui.YLineLoadingDisplay;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.PriorityEnum;
import daytrader.interfaces.ICallback;
import daytrader.interfaces.IGraphLine;
import daytrader.interfaces.Lockable;
import daytrader.interfaces.XMLPersistable;
import daytrader.interfaces.observerpattern.IObserver;
import daytrader.interfaces.observerpattern.ISubject;
import daytrader.interfaces.observerpattern.ISubjectDelegate;
import daytrader.utils.DTUtil;
import daytradertasks.LoadHistoricDataBatchTask;
import daytradertasks.LoadHistoricDataPointBatchResult;
import daytradertasks.PreLoadYLinesTask;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import rules.IdentifyPB3Points;

/**
 * A class providing the details of a 'Putup' i.e. a stock being monitored.
 * This is a thread safe class
 *
 * @author Roy
 */
public class Putup implements XMLPersistable<Putup>, Lockable, ICallback, ISubject {

    private boolean active;
    private PutupTypeEnum putupType;
    private String tickerCode;
    private MarketEnum market;
    private int threeMlPrice;
    private AtrClassEnum atrClass;
    private ReentrantLock lock;
    private Calendar maxYLineDate;
    private Calendar todaysDate;
    private BaseGraph<AbstractGraphPoint> preLoadedYLineGraph;
    private TreeSet<AbstractGraphPoint> monthCache;
    private ArrayList<IGraphLine> initialYLines;
    private YLineLoadStatus YLineStatus;
    private ISubjectDelegate subjectDelegate;
    private YLineLoadingDisplay progressDisplayYLines;
    private RealTimeRunManager runManager;
    //This stores the C points used in Y-Line calc regadless of whether they where included in a provisional Y-Line
    private TreeSet<AbstractGraphPoint> yLineCs;

    /**
     * Default constructor that creates an inactive Putup for an UNKNOWN stock
     * ticker on the New York Stock Exchange with a 3ml price of 0 and an
     * AtrClass of UU
     */
    public Putup() {
        this.subjectDelegate = new ISubjectDelegate();
        this.YLineStatus = YLineLoadStatus.YLINESNOTLOADED;
        active = false;
        tickerCode = "UNKNOWN";
        market = MarketEnum.NYSE;
        this.putupType = PutupTypeEnum.LONGS;
        threeMlPrice = 0;
        atrClass = AtrClassEnum.UU;
        todaysDate = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        lock = new ReentrantLock();
        this.yLineCs = new TreeSet<AbstractGraphPoint>();
        this.initDisplay();
    }

    /**
     * Constructor that creates an inactive Putup for the provided stock ticker,
     * stock market, 3ml Price and AtrClass.
     *
     * @param newTicker - String being the ticker code of the stock to be
     * monitored
     * @param newMarket - MarketEnum for the stock market that trades this stock
     * @param newType - PutupTypeEnum the type of putup (Long or Short)
     * @param newThreeMlPrice - The price component of the 3ML code
     * @param newClass - The AtrClass used for this Putup
     */
    public Putup(String newTicker, MarketEnum newMarket, PutupTypeEnum newType, int newThreeMlPrice, AtrClassEnum newClass) {
        this.subjectDelegate = new ISubjectDelegate();
        this.YLineStatus = YLineLoadStatus.YLINESNOTLOADED;
        this.active = false;
        this.tickerCode = newTicker;
        this.market = newMarket;
        this.putupType = newType;
        this.threeMlPrice = newThreeMlPrice;
        this.atrClass = newClass;
        todaysDate = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
        lock = new ReentrantLock();
        this.yLineCs = new TreeSet<AbstractGraphPoint>();
        this.initDisplay();
    }

    private void initDisplay() {
        this.progressDisplayYLines = new YLineLoadingDisplay();
        this.progressDisplayYLines.setModel(this);
    }

    /**
     * Accessor to test if this putup is currently going to be used when
     * monitoring starts
     *
     * @return - boolean true if this stock will be monitored, false otherwise.
     */
    public boolean isActive() {
        boolean result = false;
        lock.lock();
        try {
            result = this.active;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set whether this stock should be monitored when monitoring
     * begins
     * @param active The True / False value to set for the active flag
     */
    public void setActive(boolean active) {
        lock.lock();
        try {
            this.active = active;
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor to retrieve the stocks ticker code
     *
     * @return String being the ticker code used to identify this stock on the
     * market
     */
    public String getTickerCode() {
        //Java String is immutable object no lock needed
        return tickerCode;
    }

    /**
     * Accessor to set the stocks ticker code used to identify this stock on the
     * market
     *
     * @param tickerCode String being the new ticker code to use
     */
    public void setTickerCode(String tickerCode) {
        //Java String is immutable object no lock needed
        this.tickerCode = tickerCode;
        this.notifyObservers();
    }

    /**
     * Accessor to retrieve the price component of the 3ml code
     *
     * @return integer being the 3ml price code for this stock item
     */
    public int getThreeMlPrice() {
        int result = 0;
        lock.lock();
        try {
            result = this.threeMlPrice;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the price component of the 3ml code
     *
     * @param threeMlPrice - integer being the new 3ml price to store
     */
    public void setThreeMlPrice(int threeMlPrice) {
        lock.lock();
        try {
            this.threeMlPrice = threeMlPrice;
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor to retrieve the AtrClass of this putup.
     *
     * @return AtrClassEnum being the AtrClass of this putup
     */
    public AtrClassEnum getAtrClass() {
        AtrClassEnum result = null;
        lock.lock();
        try {
            result = this.atrClass;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the AtrClass of this putup.
     *
     * @param atrClass - The new AtrClassEnum value for this putup to use
     */
    public void setAtrClass(AtrClassEnum atrClass) {
        lock.lock();
        try {
            this.atrClass = atrClass;
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the '3ML Code' for this putup the code is composed of the price
     * value concatenated with the string version of the AtrClass. Used for
     * display on the GUI.
     *
     * @return String being the '3ML Code'
     */
    public String get3mCode() {
        String result = "UNKNOWN";
        lock.lock();
        try {
            result = Integer.toString(this.threeMlPrice);
            result += this.atrClass.toString();
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to retrieve the market on which this stock is traded
     *
     * @return the market enumeration for the market on which the stock is
     * traded
     */
    public MarketEnum getMarket() {
        MarketEnum result = null;
        lock.lock();
        try {
            result = this.market;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the market on which this stock is traded
     *
     * @param market the market enumeration for the market on which this stock
     * trades
     */
    public void setMarket(MarketEnum market) {
        lock.lock();
        try {
            this.market = market;
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void acquireObjectLock() {
        lock.lock();
    }

    @Override
    public void releaseObjectLock() {
        lock.unlock();
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, Putup dest) {
        boolean result = false;
        if (null != reader) {
            boolean abort = false;
            while (!abort) {
                try {
                    XMLEvent nextEvent = reader.nextEvent();
                    if (nextEvent.isStartElement() || nextEvent.isEndElement()) {
                        if (nextEvent.isStartElement()) {
                            StartElement xmlStartEvent = nextEvent.asStartElement();
                            String name = xmlStartEvent.getName().getLocalPart();
                            if (name.equals("Ticker")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                if (null != dest) {
                                    dest.setTickerCode(strData);
                                } else {
                                    this.setTickerCode(strData);
                                }
                            }
                            if (name.equals("Market")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                MarketEnum newMarket = MarketEnum.getMarketFromString(strData);
                                if (null != dest) {
                                    dest.setMarket(newMarket);
                                } else {
                                    this.setMarket(newMarket);
                                }
                            }
                            if (name.equals("Type")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                PutupTypeEnum newType = PutupTypeEnum.getTypeFromString(strData);
                                if (null != dest) {
                                    dest.setPutupType(newType);
                                } else {
                                    this.setPutupType(newType);
                                }
                            }
                            if (name.equals("ThreeMlPrice")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                int newPrice = Integer.parseInt(strData);
                                if (null != dest) {
                                    dest.setThreeMlPrice(newPrice);
                                } else {
                                    this.setThreeMlPrice(newPrice);
                                }
                            }
                            if (name.equals("AtrClass")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                AtrClassEnum newClass = AtrClassEnum.getAtrClassFromString(strData);
                                if (null != dest) {
                                    dest.setAtrClass(newClass);
                                } else {
                                    this.setAtrClass(newClass);
                                }
                            }
                            if (name.equals("Active")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                boolean newActive = Boolean.parseBoolean(strData);
                                if (null != dest) {
                                    dest.setActive(newActive);
                                } else {
                                    this.setActive(newActive);
                                }
                            }
                            if (name.equals("YLineDate")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                try {
                                    long timestamp = Long.parseLong(strData);
                                    this.maxYLineDate = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
                                    this.maxYLineDate.setTimeInMillis(timestamp);
                                } catch (NumberFormatException ex) {
                                    this.maxYLineDate = null;
                                }
                            }
                        }
                        if (nextEvent.isEndElement()) {
                            EndElement xmlEndEvent = nextEvent.asEndElement();
                            String name = xmlEndEvent.getName().getLocalPart();
                            if (name.equals("Putup")) {
                                result = true;
                                abort = true;
                            }
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(Putup.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        if (null != writer) {
            try {
                writer.writeStartElement("Putup");

                writer.writeStartElement("Ticker");
                writer.writeCharacters(this.tickerCode);
                writer.writeEndElement();

                writer.writeStartElement("Market");
                writer.writeCharacters(this.market.toString());
                writer.writeEndElement();

                writer.writeStartElement("Type");
                writer.writeCharacters(this.putupType.toString());
                writer.writeEndElement();

                writer.writeStartElement("ThreeMlPrice");
                writer.writeCharacters(Integer.toString(this.threeMlPrice));
                writer.writeEndElement();

                writer.writeStartElement("AtrClass");
                writer.writeCharacters(this.atrClass.toString());
                writer.writeEndElement();

                writer.writeStartElement("Active");
                if (this.active) {
                    writer.writeCharacters("TRUE");
                } else {
                    writer.writeCharacters("FALSE");
                }
                writer.writeEndElement();

                writer.writeStartElement("YLineDate");
                if (null != this.maxYLineDate) {
                    Long timestamp = this.maxYLineDate.getTimeInMillis();
                    writer.writeCharacters(timestamp.toString());
                } else {
                    writer.writeCharacters("NULL");
                }
                writer.writeEndElement();

                writer.writeEndElement();
                result = true;
            } catch (XMLStreamException ex) {
                Logger.getLogger(Putup.class.getName()).log(Level.SEVERE, null, ex);
                result = false;
            }
        }
        return result;
    }

    /**
     * Retrieves a Java Calendar containing the latest date on which a Y-Line 
     * may start
     * @return A Java Calendar defining how far back in time to look for Y-Lines or
     * NULL if the Y-Line calculation is not to be done.
     */
    public Calendar getMaxYLineDate() {
        return maxYLineDate;
    }

    /**
     * Accessor to set the latest date to look back to when searching for Y-Lines
     * @param maxYLineDate A Java Calendar defining how far back in time to look for Y-Lines or
     * NULL if the Y-Line calculation is not to be done.
     */
    public void setMaxYLineDate(Calendar maxYLineDate) {
        if (null != maxYLineDate) {
            Calendar newCalendar = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
            newCalendar.setTimeInMillis(maxYLineDate.getTimeInMillis());
            newCalendar.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
            newCalendar.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
            newCalendar.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
            newCalendar.set(Calendar.MILLISECOND, 0);
            this.maxYLineDate = newCalendar;
        } else {
            this.maxYLineDate = null;
        }
        this.notifyObservers();
    }

    /**
     * Retrieves todays date (the day on which the Putup started to run)
     * @return A Java calendar object encapsulating a date and time
     */
    public Calendar getTodaysDate() {
        return todaysDate;
    }

    /**
     * Sets todays date (the day on which the Putup started to run)
     * @param todaysDate A Java calendar object encapsulating a date and time
     */
    public void setTodaysDate(Calendar todaysDate) {
        this.todaysDate = todaysDate;
        this.notifyObservers();
    }

    /**
     * Retrieves an enumeration defining the type of the putup. 
     * @return A PutupTypeEnum value for this putup or NULL if none is set.
     */
    public PutupTypeEnum getPutupType() {
        PutupTypeEnum result = null;
        lock.lock();
        try {
            switch (this.putupType) {
                case LONGS:
                    result = PutupTypeEnum.LONGS;
                    break;
                case SHORTS:
                    result = PutupTypeEnum.SHORTS;
                    break;
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Sets the enumeration defining the type of the putup. 
     * @param putupType A PutupTypeEnum to use for this putup
     */
    public void setPutupType(PutupTypeEnum putupType) {
        if (null != putupType) {
            lock.lock();
            try {
                this.putupType = putupType;
                this.notifyObservers();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Accessor to retrieve the pre-loaded graph of data used for Y-Line calculations
     * @return A BaseGraph of data points used to calculate this Putups Y-Lines or NULL
     * if no data has been loaded
     */
    public BaseGraph<AbstractGraphPoint> getPreLoadedYLineGraph() {
        BaseGraph<AbstractGraphPoint> result = null;
        lock.lock();
        try {
            //BaseGraph is threadsafe so if can 'leak' here
            result = preLoadedYLineGraph;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the pre-loaded graph of data used for Y-Line calculations
     * @param preLoadedYLineGraph A BaseGraph of data points used to calculate this Putups Y-Lines
     */
    public void setPreLoadedYLineGraph(BaseGraph<AbstractGraphPoint> preLoadedYLineGraph) {
        lock.lock();
        try {
            this.preLoadedYLineGraph = preLoadedYLineGraph;
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Tests if the Y-Line data graph has been populated with data
     * @return boolean True if the data exists, False otherwise.
     */
    public boolean hasLoadedYLines() {
        boolean result = false;
        lock.lock();
        try {
            if (null != this.preLoadedYLineGraph && 0 < this.preLoadedYLineGraph.size()) {
                result = true;
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * This method will begin the process of loading Y-Line data based on the date 
     * entered by the user. It is a non blocking function but the actual process
     * of loading the data may take some time.
     */
    public void preLoadYLines() {
        //Check to see if we should do a Y-Lines pre-load of Y Line data
        if (null != this.getMaxYLineDate()) {
            PreLoadYLinesTask yLineLoad = new PreLoadYLinesTask(this);
            HRSCallableWrapper yLineWrapper = new HRSCallableWrapper(yLineLoad, PriorityEnum.LOW);
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            this.setYLineStatus(YLineLoadStatus.YLINESLOADING1DAYBARS);
            HRSys.submitRequest(yLineWrapper);
        }
    }

    @Override
    public void callback(CallbackType type, Object data) {
        lock.lock();
        try {
            switch (type) {
                case YLINESCOMPLETE:
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                        BaseGraph<AbstractGraphPoint> pointsAsGraph = result.getPointsAsGraph();
                        this.setPreLoadedYLineGraph(pointsAsGraph);
                        //FOR TESTING SAVE THE GRAPH TO A FILE - START
//                        String fileName = this.getTickerCode() + "_PRELOAD.xml";
//                        File file = new File(fileName);
//                        pointsAsGraph.saveToXMLFile(file);
                        //FOR TESTING SAVE THE GRAPH TO A FILE - END
                        //Now we have a complete graph generate the YLines
                        //YOU CANNOT DO THIS CALC Y LINES IS A LONG RUNNING TASK AND YOU ARE HOLDING THE LOCK
                        this.setYLineStatus(YLineLoadStatus.YLINECALCULATING);
                        //Start a task to compute the provisional y lines now all data is loaded.
                        ExecutorCompletionService serv = new ExecutorCompletionService(DTConstants.THREAD_POOL);
                        ProvYLinesCalculator task = new ProvYLinesCalculator(this);
                        serv.submit(task);
                    }
                    break;
                case YLINEMONTHCACHE:
                    if (data instanceof TreeSet) {
                        TreeSet<AbstractGraphPoint> result = (TreeSet<AbstractGraphPoint>) data;
                        this.setMonthCache(result);
                        this.setYLineStatus(YLineLoadStatus.YLINELOADING1HOURBARS);
                    }
                    break;
                case YLINESLOADERROR:
                    this.setPreLoadedYLineGraph(null);
                    this.setYLineStatus(YLineLoadStatus.YLINESNOTLOADED);
                    break;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor to retrieve the cache of trading day data from this putup (usually 1 month)
     * it is generated when loading Y-Line data
     * @return A TreeSet of AbstractGraphPoint's representing the last month of trading days
     */
    public TreeSet<AbstractGraphPoint> getMonthCache() {
        TreeSet<AbstractGraphPoint> result = null;
        lock.lock();
        try {
            if (null != this.monthCache && 0 < this.monthCache.size()) {
                result = new TreeSet<AbstractGraphPoint>(this.monthCache);
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the cache of trading day data from this putup (usually 1 month)
     * @param monthCache A TreeSet of AbstractGraphPoint's representing the last month of trading days
     */
    public void setMonthCache(TreeSet<AbstractGraphPoint> monthCache) {
        lock.lock();
        try {
            if (null == monthCache) {
                this.monthCache = null;
            } else {
                TreeSet<AbstractGraphPoint> newValue = new TreeSet<AbstractGraphPoint>(monthCache);
                this.monthCache = newValue;
            }
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Given a data graph of loaded Y-Line data this function will calculate the 
     * PROVISIONAL Y-LINES WITHOUT STAND INS. The result of this function should be
     * processed to search for stand ins. Please see the ProvYLinesCalculator class
     * for an example of Y-Line generation using this function
     * @param dataGraph - A BaseGraph of Price / Time points to use in Y-Line calculations
     * @return An ArrayList of provisional Y-Lines that have not been tested for stand ins
     */
    public ArrayList<IGraphLine> generateProvisionalYLines(BaseGraph<AbstractGraphPoint> dataGraph) {
        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
        ArrayList<IGraphLine> tempList2 = new ArrayList<IGraphLine>();
        if (null != dataGraph && dataGraph.size() > 1) {
            HashMap<Integer, AbstractGraphPoint> dataStore = new HashMap<Integer, AbstractGraphPoint>();
            //Identify the high points for every day we have data for
            for (AbstractGraphPoint currPoint : dataGraph) {
                Integer dayCode = currPoint.getDateAsNumber();
                if (!dataStore.containsKey(dayCode)) {
                    //First point for this day add currPoint
                    dataStore.put(dayCode, currPoint);
                } else {
                    //Check to see if currPoint WAP is higher than dataStore point, if it is replace dataStore entry
                    AbstractGraphPoint storedPoint = dataStore.get(dayCode);
                    if (currPoint.getWAP() >= storedPoint.getWAP()) {
                        dataStore.put(dayCode, currPoint);
                    }
                }
            }
            //High points for every day found convert to graph and CACHE this data for use in real time update of Y-Lines
            //Convert to Graph
            HistoricDataGraph cPointGraph = new HistoricDataGraph();
            //Putup has to be thread safe so need to lock while modifying cache
            this.lock.lock();
            try {
                this.yLineCs = new TreeSet<AbstractGraphPoint>();
                Set<Integer> keySet = dataStore.keySet();
                for (Integer key : keySet) {
                    AbstractGraphPoint currPoint = dataStore.get(key);
                    //Add to Graph
                    cPointGraph.add(currPoint);
                    //Store to Y-Lines 'C' cache
                    this.yLineCs.add(currPoint);
                }
            } finally {
                this.lock.unlock();
            }
            //For each high point generate a line from it to every other point between it and end of the graph
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

                System.out.println("Starting gradients");
                //Now go through generated lines and find the line with the lowest gradient
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
                //Add smallest gradient to the result (last point of the graph generates a null do not add this)
                if (null != smallestGradient) {
                    tempList2.add(smallestGradient);
                }
            }
        }
        System.out.println("Finished gradients");
        //Filter out any Y Lines that start on the 'Current' Day
        //and store to the result
        Calendar todaysDate = dataGraph.getPutup().getTodaysDate();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        String strDate = formatter.format(todaysDate.getTime());
        int dayCode = Integer.parseInt(strDate);
        for (IGraphLine currLine : tempList2) {
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

    /**
     * DEPRICATED
     */
//    public ArrayList<IGraphLine> checkYLineStandIns2(ArrayList<IGraphLine> provLines, BaseGraph<AbstractGraphPoint> dataGraph, boolean isRecursion) {
//        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
//        if (null != provLines && null != dataGraph && 0 < provLines.size()) {
//            if (!isRecursion) {
//                //We cannot do this without 1 sec resolution as the graph will not let us work out PB3 or not without being fully loaded
//                Putup putup = dataGraph.getPutup();
//                //Work out earliest and latest C / E
//                AbstractGraphPoint earliestC = null;
//                AbstractGraphPoint latestE = null;
//                for (IGraphLine currLine : provLines) {
//                    if (null != earliestC) {
//                        long currCTime = currLine.getCurrentC().getTimestamp();
//                        long earlyCTime = earliestC.getTimestamp();
//                        if (currCTime < earlyCTime) {
//                            earliestC = currLine.getCurrentC();
//                        }
//                    } else {
//                        earliestC = currLine.getCurrentC();
//                    }
//                    if (null != latestE) {
//                        long currETime = currLine.getCurrentE().getTimestamp();
//                        long latestETime = latestE.getTimestamp();
//                        if (currETime < latestETime) {
//                            latestE = currLine.getCurrentE();
//                        }
//                    } else {
//                        latestE = currLine.getCurrentE();
//                    }
//                }
//                //We now know the earliest and latest C & E, ensure a 1 sec resolution on the graph between these
//                LoadHistoricDataBetweenPointsTaskDEPRICATED loadTask = new LoadHistoricDataBetweenPointsTaskDEPRICATED(earliestC, latestE, putup);
//                ExecutorService pool = Executors.newFixedThreadPool(1);
//                CompletionService<LoadHistoricDataPointBatchResult> service = new ExecutorCompletionService(pool);
//                service.submit(loadTask);
//                LoadHistoricDataPointBatchResult data = null;
//                try {
//                    Future<LoadHistoricDataPointBatchResult> f = service.take();
//                    data = f.get();
//
//
//
//
//                } catch (InterruptedException ex) {
//                    Logger.getLogger(Putup.class
//                            .getName()).log(Level.SEVERE, null, ex);
//                } catch (ExecutionException ex) {
//                    Logger.getLogger(Putup.class
//                            .getName()).log(Level.SEVERE, null, ex);
//                }
//                if (null != data) {
//                    dataGraph.storeHistoricData(data.loadedPoints);
//                }
//            }
//            //At this point the graph has a 1 sec resolution (if it was a recursive call then it already has 1 sec resolution)
//            for (IGraphLine currLine : provLines) {
//                //1) Look for a PB3+ point between the E and C point
//                //Get list of points between E and C
//                NavigableSet<AbstractGraphPoint> pointList = dataGraph.subSet(currLine.getCurrentC(), true, currLine.getCurrentE(), true);
//                boolean pb3Found = false;
//                AbstractGraphPoint currPoint;
//                Iterator<AbstractGraphPoint> descPoints = pointList.descendingIterator();
//                while (descPoints.hasNext()) {
//                    currPoint = descPoints.next();
//                    //Get the PB3 value for this point if it is a PB3+ then mark found a break out of loop
//                    int pbValue = IdentifyPB3Points.findPBValue(dataGraph, currPoint);
//                    if (pbValue >= DTConstants.getScaledPBVALUE() / 100d) {
//                        pb3Found = true;
//                        break;
//                    }
//                }
//                //If a pb3 was found then go no further this is a valid Y line
//                if (pb3Found) {
//                    result.add(currLine);
//                } else {
//                    //Look for a stand in
//                    //Take the prov e as a new c
//                    AbstractGraphPoint newC = currLine.getCurrentE();
//                    //Subset the chart from the new C to now and look for potential Y Lines
//                    NavigableSet<AbstractGraphPoint> subSet = dataGraph.subSet(newC, true, dataGraph.last(), true);
//                    BaseGraph<AbstractGraphPoint> subGraph = dataGraph.replicateGraph();
//                    subGraph.clear();
//                    subGraph.addAll(subSet);
//                    ArrayList<IGraphLine> newProvYLines = this.generateProvisionalYLines(subGraph);
//                    //If more than 1 line is found continue the loop
//                    if (1 < newProvYLines.size()) {
//                        //Check for a pb3+ between the C and E of each line
//                        ArrayList<IGraphLine> yLinesStandIns = this.checkYLineStandIns2(provLines, subGraph, true);
//                        result.addAll(yLinesStandIns);
//                    } else if (1 == newProvYLines.size()) {
//                        //Look for a PB3 point between the C and E of the one line. IF one is found this is our Y line ELSE their is no YLine
//                        IGraphLine lastLine = newProvYLines.get(0);
//                        NavigableSet<AbstractGraphPoint> lastPoints = dataGraph.subSet(lastLine.getCurrentC(), true, lastLine.getCurrentE(), true);
//                        boolean pb3LastFound = false;
//                        AbstractGraphPoint newCurrPoint;
//                        Iterator<AbstractGraphPoint> newDescPoints = lastPoints.descendingIterator();
//                        while (newDescPoints.hasNext()) {
//                            newCurrPoint = newDescPoints.next();
//                            //Get the PB3 value for this point if it is a PB3+ then mark found a break out of loop
//                            int pbValue = IdentifyPB3Points.findPBValue(dataGraph, newCurrPoint);
//                            if (pbValue >= DTConstants.getScaledPBVALUE() / 100d) {
//                                pb3LastFound = true;
//                                break;
//                            }
//                        }
//                        //If a PB3 was found add this as the last line else THEIR IS NO Y LINE
//                        if (pb3LastFound) {
//                            result.add(lastLine);
//                        }
//                    }
//                }
//            }
//        }
//        return result;
//    }
    /**
     * Accessor to retrieve the result of the initial Y-Lines calculation. The retrieved
     * Y-Lines will be examined to determine if they are still valid before use.
     * @return An ArrayList of IGraphLine objects representing the initial Y-Lines
     */
    public ArrayList<IGraphLine> getInitialYLines() {
        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
        lock.lock();
        try {
            if (null != this.initialYLines) {
                for (IGraphLine currLine : this.initialYLines) {
                    result.add(currLine.deepCopyLine());
                }
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the result of the initial Y-Lines calculation. The cached
     * Y-Lines will be examined to determine if they are still valid before use.
     * @param initialYLines An ArrayList of IGraphLine objects representing the initial Y-Lines
     */
    public void setInitialYLines(ArrayList<IGraphLine> initialYLines) {
        lock.lock();
        try {
            if (null == initialYLines) {
                this.initialYLines = null;
            } else {
                this.initialYLines = new ArrayList<IGraphLine>();
                for (IGraphLine currLine : initialYLines) {
                    this.initialYLines.add(currLine.deepCopyLine());
                }
            }
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor to retrieve the enumeration that identifies what stage the generation
     * of this putups initial Y-Lines has reached.
     * @return A YLineLoadStatus enumeration identifying progress.
     */
    public YLineLoadStatus getYLineStatus() {
        YLineLoadStatus result = null;
        lock.lock();
        try {
            result = this.YLineStatus;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the enumeration that identifies what stage the generation
     * of this putups inital Y-Lines has reached.
     * @param YLineStatus A YLineLoadStatus enumeration identifying progress.
     */
    public void setYLineStatus(YLineLoadStatus YLineStatus) {
        lock.lock();
        try {
            this.YLineStatus = YLineStatus;
            this.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean registerObserver(IObserver newObserver) {
        boolean result = false;
        lock.lock();
        try {
            result = this.subjectDelegate.registerObserver(newObserver);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean removeObserver(IObserver oldObserver) {
        boolean result = false;
        lock.lock();
        try {
            result = this.removeObserver(oldObserver);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public void notifyObservers() {
        lock.lock();
        try {
            this.subjectDelegate.notifyObservers();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the JPanel object that shows progress in loading the Y-Lines
     * @return The JPanel component that shows progress in loading the putups
     * Y-Line data.
     */
    public YLineLoadingDisplay getProgressDisplayYLines() {
        return progressDisplayYLines;
    }

    /**
     * This method takes in a collection of provisional Y Lines and tests their
     * 'e' to ensure it is a BP3+, if it is not then the 'e' becomes a new 'c'
     * and it tries to find a new 'e' that IS a PB3+ (loading more data if
     * needed). This version of the method creates a cache of the potential
     * Y-Lines that are generated and associates it with each 'C' point.
     * @param provLines An ArrayList of IGraphLine objects that represent the
     * provisional Y-Lines 
     * @param sourceGraph - The Price / Time graph representing all the data used 
     * in calculating the provisional Y-Lines
     * @return An ArrayList of IGraphLine objects representing a new set of provisional
     * Y-Lines where the 'C' and 'E' points have been replaced by 'Stand in' points if
     * needed.
     */
    public ArrayList<IGraphLine> checkYLinesForStandIns(ArrayList<IGraphLine> provLines, BaseGraph<AbstractGraphPoint> sourceGraph) {
        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
        if (null != provLines && null != sourceGraph) {
            if (0 < provLines.size() && 0 < sourceGraph.size()) {
                //We have Data!
                //For each provisional Y Line get its current 'e' point
                for (IGraphLine currLine : provLines) {
                    AbstractGraphPoint currentC = currLine.getCurrentC();
                    AbstractGraphPoint currentE = currLine.getCurrentE();
                    //Prepare a subset of the source graph between C and E
                    NavigableSet<AbstractGraphPoint> subSet = sourceGraph.subSet(currentC, true, currentE, true);
                    BaseGraph<AbstractGraphPoint> subGraph = sourceGraph.replicateGraph();
                    subGraph.clear();
                    subGraph.addAll(subSet);
                    boolean repeat = false;
                    do {
                        TVL tvlPB3Plus = IdentifyPB3Points.isPB3Plus(currentE, subGraph);
                        switch (tvlPB3Plus) {
                            case TRUE:
                                //CurrLine is an acceptable Y Line store into results
                                result.add(currLine);
                                repeat = false;
                                break;
                            case FALSE:
                                System.out.println("NEED TO LOOK FOR A NEW E");
                                //Setup cache to store recursion record
                                RecursionCache cache = new RecursionCache(currentC);
                                cache.addNewLineToCache(currLine);
                                currentC.setRecursionCache(cache);
                                IGraphLine alternateYLineForPoint = this.getAlternateYLineForPoint(currentE, sourceGraph);
                                if (null != alternateYLineForPoint) {
                                    ArrayList<IGraphLine> newProvYLine = new ArrayList<IGraphLine>();
                                    cache.addNewLineToCache(alternateYLineForPoint);
                                    newProvYLine.add(alternateYLineForPoint);
                                    ArrayList<IGraphLine> checkYLinesForStandIns = this.checkYLinesForStandIns(newProvYLine, sourceGraph, cache);
                                    if (checkYLinesForStandIns.size() == 1) {
                                        result.add(checkYLinesForStandIns.get(0));
                                    }
                                }
                                repeat = false;
                                break;
                            case INDETERMINATE:
                                System.out.println("LOAD MORE DATA");
                                repeat = true;
                                //Blocking function that will fill sub-graph with another 30 min of data
                                this.loadMoreGraphData(subGraph);
                                break;
                        }
                    } while (repeat);
                }
            }
        }
        return result;
    }

    /**
     * This method takes in a collection of provisional Y Lines and tests their
     * 'e' to ensure it is a BP3+, if it is not then the 'e' becomes a new 'c'
     * and it tries to find a new 'e' that IS a PB3+ (loading more data if
     * needed). This version of the method is called recursively from the public
     * version. The cache of lines generated for this point will be updated with
     * each new line to be considered before the next iteration of the
     * recursion. Other than this the code is the same as the public version of
     * this method.
     */
    private ArrayList<IGraphLine> checkYLinesForStandIns(ArrayList<IGraphLine> provLines, BaseGraph<AbstractGraphPoint> sourceGraph, RecursionCache cache) {
        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
        if (null != provLines && null != sourceGraph && null != cache) {
            if (0 < provLines.size() && 0 < sourceGraph.size()) {
                //We have Data!
                //For each provisional Y Line get its current 'e' point
                for (IGraphLine currLine : provLines) {
                    AbstractGraphPoint currentC = currLine.getCurrentC();
                    AbstractGraphPoint currentE = currLine.getCurrentE();
                    //Prepare a subset of the source graph between C and E
                    NavigableSet<AbstractGraphPoint> subSet = sourceGraph.subSet(currentC, true, currentE, true);
                    BaseGraph<AbstractGraphPoint> subGraph = sourceGraph.replicateGraph();
                    subGraph.clear();
                    subGraph.addAll(subSet);
                    boolean repeat = false;
                    do {
                        TVL tvlPB3Plus = IdentifyPB3Points.isPB3Plus(currentE, subGraph);
                        switch (tvlPB3Plus) {
                            case TRUE:
                                //CurrLine is an acceptable Y Line store into results
                                result.add(currLine);
                                cache.setValidTerminationLine(true);
                                repeat = false;
                                break;
                            case FALSE:
                                System.out.println("NEED TO LOOK FOR A NEW E");
                                IGraphLine alternateYLineForPoint = this.getAlternateYLineForPoint(currentE, sourceGraph);
                                if (null != alternateYLineForPoint) {
                                    cache.addNewLineToCache(alternateYLineForPoint);
                                    ArrayList<IGraphLine> newProvYLine = new ArrayList<IGraphLine>();
                                    newProvYLine.add(alternateYLineForPoint);
                                    ArrayList<IGraphLine> checkYLinesForStandIns = this.checkYLinesForStandIns(newProvYLine, sourceGraph, cache);
                                    if (checkYLinesForStandIns.size() == 1) {
                                        result.add(checkYLinesForStandIns.get(0));
                                        cache.setValidTerminationLine(true);
                                    } else {
                                        cache.setValidTerminationLine(false);
                                    }
                                }
                                repeat = false;
                                break;
                            case INDETERMINATE:
                                System.out.println("LOAD MORE DATA");
                                repeat = true;
                                //Blocking function that will fill sub-graph with another 30 min of data
                                this.loadMoreGraphData(subGraph);
                                break;
                        }
                    } while (repeat);
                }
            }
        }
        return result;
    }

    private IGraphLine getAlternateYLineForPoint(AbstractGraphPoint aPoint, BaseGraph<AbstractGraphPoint> graph) {
        IGraphLine result = null;
        if (null != aPoint && null != graph) {
            if (0 < graph.size()) {
                //Create a subset of all points from aPoint to end of graph
                NavigableSet<AbstractGraphPoint> subSet = graph.subSet(aPoint, false, graph.last(), true);
                BaseGraph<AbstractGraphPoint> subGraph = graph.replicateGraph();
                subGraph.clear();
                subGraph.addAll(subSet);
                ArrayList<IGraphLine> tempList = new ArrayList<IGraphLine>();
                for (AbstractGraphPoint subPoint : subGraph) {
                    IGraphLine graphLine = new GraphLine(aPoint, subPoint, subGraph);
                    graphLine.setTradingDays(graph.getTradingDays());
                    tempList.add(graphLine);
                }
                //For all these lines find the one with the smallest gradient
                IGraphLine smallestGradient = null;
                for (IGraphLine currLine : tempList) {
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
                result = smallestGradient;
            }
        }
        return result;
    }

    /**
     * This is a blocking function that identifies the first gap in a graphs
     * data (1 sec resolution) and loads a 30 min batch of data. It is used to
     * populate a graph and have the system wait until the data is loaded
     */
    private void loadMoreGraphData(BaseGraph<AbstractGraphPoint> graph) {
        if (null != graph && 0 < graph.size()) {
            //Find first gap in 1 sec data
            AbstractGraphPoint prevPoint = graph.last();
            Iterator<AbstractGraphPoint> descIterator = graph.descendingIterator();
            AbstractGraphPoint currPoint;
            long timeDiff = 0;
            while (descIterator.hasNext()) {
                currPoint = descIterator.next();
                timeDiff = prevPoint.getTimestamp() - currPoint.getTimestamp();
                //Should always be 1000 millisecs between historic data
                if (timeDiff != 1000) {
                    //If the previous point was NOT the start of a day we have found a gap break and do the load
                    if (!prevPoint.isStartOfDay()) {
                        break;
                    }
                }
                prevPoint = currPoint;
            }
            //At this point the previous point marks where we need to load data to. Now submit a request to load 30 min of data
            LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(this, prevPoint.getCalDate());
            HRSCallableWrapper wrapper = new HRSCallableWrapper(loadTask, PriorityEnum.IMMEDIATE);
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            HRSys.submitRequest(wrapper);
            //Now block until complete
            while (!loadTask.isLoadComplete()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Putup.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            Object resultObject = wrapper.getResultObject();
            if (resultObject instanceof LoadHistoricDataPointBatchResult) {
                LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) resultObject;
                switch (result.getCallbackType()) {
                    case HISTORICDATAFINISHED:
                        graph.addAll(result.loadedPoints);
                        break;
                }
            }
        }

    }

    /**
     * Accessor method to retrieve the RealTimeRunManager that wraps this putup while
     * a live data run is in progress.
     * @return The RealTimeRunManager that wraps this Putup or NULL if one has not
     * yet been created and assigned
     */
    public RealTimeRunManager getRunManager() {
        return runManager;
    }

    /**
     * Accessor method to set the RealTimeRunManager that wraps this putup while
     * a live data run is in progress.
     * @param runManager A RealTimeRunManager that wraps this object.
     */
    public void setRunManager(RealTimeRunManager runManager) {
        this.runManager = runManager;
    }

    /**
     * A list of all potential 'C' points that where considered in creating the Y-Lines.
     * This is used to test if a new Y-Line has come into existence when Y-Lines are finalised
     * @return A TreeSet of AbstractGraphPoint's that could be a potential 'C' point
     * for a Y-Line.
     */
    public TreeSet<AbstractGraphPoint> getyLineCs() {
        return yLineCs;
    }
}