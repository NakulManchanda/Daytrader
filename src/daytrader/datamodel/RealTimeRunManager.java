/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytradertasks.RealTimeFiveSecBarRequest;
import static daytrader.datamodel.CallbackType.HISTORICDATATRADINGDAYS;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.PriorityEnum;
import daytrader.historicRequestSystem.TWSAccount;
import daytrader.historicRequestSystem.TWSAccountList;
import daytrader.interfaces.ICallback;
import daytrader.interfaces.XMLPersistable;
import daytrader.utils.DTUtil;
import daytradertasks.LoadHistoricDataPointBatchResult;
import daytradertasks.LoadPrevDayClose;
import daytradertasks.PreLoadYLinesTask;
import daytradertasks.RequestMarketDataTask;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import rules.FTGBreachPoint;
import rules.SingleDoubleCheck;
import rules.ThreeMLRanging;

/**
 * Objects of this class accept a put up, start the real time data run and
 * manage the application of the trading rules to the returned data. This is
 * a wrapper class around a Putup object that adds the needed attributes and
 * functions for that putup to operate with the real time processing system.
 *
 * @author Roy
 */
public class RealTimeRunManager implements Callable<Void>, ICallback, XMLPersistable<RealTimeRunManager> {

    private static final long SLEEP_TIME = 750;
    private ReentrantLock lock;
    private Putup myPutup;
    private BaseGraph<AbstractGraphPoint> graph5SecBars;
    private BaseGraph<AbstractGraphPoint> graphReqMarketData;
    private BaseGraph<AbstractGraphPoint> graphHistoricData;
    private TWSAccount genAcc;
    private ExecutorService exec5SecBar;
    private ExecutorCompletionService execService5SecBar;
    private ExecutorService execReqMktData;
    private ExecutorCompletionService execServiceReqMktData;
    private RealTimeFiveSecBarRequest initialReq;
    private RequestMarketDataTask reqMrkDataReq;
    private RulesStateManager rulesManager;
    //I need a central point to manage the SingleDouble patters from. Created a SingleDoubleCheck object here
    //and delegating to it.
    private SingleDoubleCheck patternChecker;
    //Also need 1 FTGBreach checker per a putup
    private FTGBreachPoint ftgChecker;
    //Also need a checker for the final 3m range test
    private ThreeMLRanging threeMChecker;
    private ShowJOptionWinError errorWin;

    private RealTimeRunManager() {
        this.lock = new ReentrantLock();
        this.ftgChecker = new FTGBreachPoint();
        this.ftgChecker.setRealTimeRunManager(this);
        this.threeMChecker = new ThreeMLRanging();
        this.threeMChecker.setRealTimeRunManager(this);

        //Initialise 5 Sec bar graph
        this.graph5SecBars = new BaseGraph<AbstractGraphPoint>();
        //Initialise Request Market Data graph
        this.graphReqMarketData = new BaseGraph<AbstractGraphPoint>();
        //Initialise Historic Data graph
        this.graphHistoricData = new BaseGraph<AbstractGraphPoint>();

        this.exec5SecBar = Executors.newFixedThreadPool(1);
        this.execService5SecBar = new ExecutorCompletionService(this.exec5SecBar);

        TWSAccountList accountList = HistoricRequestProcessingSystem.getInstance().getAccounts();
        //Use the first account on the list for non historic data requests
        if (0 < accountList.size()) {
            this.genAcc = accountList.getGeneralAcc();
        }
    }

    /**
     * Constructor that accepts the putup to wrap and a boolean flag indicating whether
     * it should immediately request Real Time 5 second bars from the stock brokers API
     * (Normally always true but you may want to skip this step in debugging)
     * @param newPutup - A Putup object to wrap
     * @param makeInitReq - A boolean flag True triggers the delivery of real time 
     * 5 sec bar data, False skips this step.
     */
    public RealTimeRunManager(Putup newPutup, boolean makeInitReq) {
        this();
        this.myPutup = newPutup;
        this.myPutup.setRunManager(this);

        //Initialise 5 Sec bar graph
        this.graph5SecBars.setPutup(this.myPutup);
        this.graph5SecBars.setTradingDays(DTConstants.TRADINGDAYSLASTWEEK);

        //Initialise Request Market Data graph
        this.graphReqMarketData.setPutup(this.myPutup);
        this.graphReqMarketData.setTradingDays(DTConstants.TRADINGDAYSLASTWEEK);

        //Initialise Historic Data graph
        this.graphHistoricData.setPutup(this.myPutup);
        this.graphHistoricData.setTradingDays(DTConstants.TRADINGDAYSLASTWEEK);

        this.myPutup.setTodaysDate(Calendar.getInstance(DTConstants.EXCH_TIME_ZONE));
        this.rulesManager = new RulesStateManager(this.myPutup.getPutupType(), this);
        this.patternChecker = new SingleDoubleCheck(this.graphHistoricData);

        if (makeInitReq) {
            this.makeInitialRequest();
        }
    }

    /**
     * This method parses the provided file looking for XML Data describing a Real Time Run manager
     * and if it is found creates an instance of this class initialised using the data provided
     * by the file
     * @param target - A File containing saved XML data describing a RealTimeRunManager object
     * @return The loaded RealTimeRunManager object or NULL if no data could be found
     */
    public static RealTimeRunManager loadManagerFromXMLFile(File target) {
        RealTimeRunManager result = null;
        if (null != target && target.canRead()) {
            //Create Stream reader
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(target);
                XMLEventReader in = XMLInputFactory.newInstance().createXMLEventReader(stream);
                result = new RealTimeRunManager();
                if (!result.loadFromXMLStream(in, result)) {
                    result = null;
                }
            } catch (FileNotFoundException ex) {
                Logger.getLogger(RealTimeRunManager.class.getName()).log(Level.SEVERE, null, ex);
            } catch (XMLStreamException ex) {
                Logger.getLogger(RealTimeRunManager.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (null != stream) {
                    try {
                        stream.close();
                    } catch (IOException ex) {
                        Logger.getLogger(RealTimeRunManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        return result;
    }

    private void makeInitialRequest() {
        lock.lock();
        try {
            if (null != this.genAcc) {
                this.initialReq = new RealTimeFiveSecBarRequest(this.graph5SecBars, this.genAcc);
                this.execService5SecBar.submit(initialReq);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Each running putup may be in a number of states that define the actions that
     * need to be taken for that putup. The actions depend on Bryn's rules, but this
     * class contains an instance of a RulesStateManager that defines this.
     * This Accessor Method retrieves the current rules state for this putup
     * @return A RulesStateEnum reflecting the current state of this putups RulesStateManager
     */
    public RulesStateEnum getCurrentState() {
        RulesStateEnum result = null;
        lock.lock();
        try {
            result = this.rulesManager.getCurrState();
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public Void call() throws Exception {
        //Store this running manager as the owner for the rules group
        this.rulesManager.setRealTimeRunManager(this);
        //From now until the end of the day test the rules every second against this putup
        //Determine end of trading day
        long endTime = DTUtil.getExchClosingCalendar(this.myPutup.getTodaysDate()).getTimeInMillis();
        long nextCheckTime = System.currentTimeMillis() + 1000;                 //One second after the start of running
        boolean blnEnd = false;
        while (!blnEnd) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis > endTime) {
                blnEnd = true;
            } else {
                if (currentTimeMillis >= nextCheckTime) {
                    nextCheckTime += 1000;                                          //Advance another second
                    //Test rules group to see if we should proceed
                    try {
                        if (this.rulesManager.checkCurrentRules()) {
                            this.changeToNextState();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        this.printToConsole("Exception in rules testing, " + ex.getMessage());
                    }
                }
            }
            //Yield any remaining CPU time in this time slice - Changed to sleep to avoid constantly holding a CPU
            //Thread.yield();
            Thread.sleep(SLEEP_TIME);
        }
        return null;
    }

    /**
     * Test whether the putup wrapped by this object is the target putup provided
     * as an argument
     * @param target - The putup to compare with the putup wrapped by this object
     * @return boolean True if this is the RealTimeRunManager wrapping the target 
     * putup, False otherwise.
     */
    public boolean isThisPutup(Putup target) {
        boolean result = false;
        lock.lock();
        try {
            if (null != target) {
                if (target.get3mCode().equals(this.myPutup.get3mCode())) {
                    if (target.getMarket() == this.myPutup.getMarket()) {
                        if (target.getPutupType() == this.myPutup.getPutupType()) {
                            result = true;
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * This method performs the data pre-load for the wrapped putup. This is defined as 
     * loading the last 30 min of historical data for the previous trading day and 
     * pre-loading any Y-Line data + calculating the provisional Y-Lines
     */
    public void preLoadData() {
        BaseGraph<AbstractGraphPoint> prevDayGraph = this.graphHistoricData.getPrevDayGraph();
        HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
        if (null == prevDayGraph) {
            //Pre load previous days close
            LoadPrevDayClose task = new LoadPrevDayClose(myPutup, this.myPutup.getTodaysDate(), this);
            HRSCallableWrapper wrapper = new HRSCallableWrapper(task, PriorityEnum.IMMEDIATE);
            HRSys.submitRequest(wrapper);
        }
        //Check to see if we should do a Y-Lines pre-load of Y Line data
        if (null != this.myPutup.getMaxYLineDate() && !this.myPutup.hasLoadedYLines()) {
            PreLoadYLinesTask yLineLoad = new PreLoadYLinesTask(myPutup);
            HRSCallableWrapper yLineWrapper = new HRSCallableWrapper(yLineLoad, PriorityEnum.LOW);
            HRSys.submitRequest(yLineWrapper);
        }
    }

    /**
     * Loads the last 30 min of data for this putup from the previous trading day.
     * This is an asynchronous operation.
     */
    public void loadPrevDayClose() {
        LoadPrevDayClose task = new LoadPrevDayClose(myPutup, this.myPutup.getTodaysDate(), this);
        HRSCallableWrapper wrapper = new HRSCallableWrapper(task, PriorityEnum.IMMEDIATE);
        HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
        HRSys.submitRequest(wrapper);
        try {
            Thread.sleep(50);
        } catch (InterruptedException ex) {
            Logger.getLogger(RealTimeRunManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean isValid() {
        boolean result = false;
        lock.lock();
        try {
            if (null != this.genAcc && null != this.graph5SecBars && null != this.myPutup) {
                result = true;
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public void callback(CallbackType type, Object data) {
        lock.lock();
        try {
            switch (type) {
                case HISTORICDATACLOSEPREVDAY:
                    if (null != this.graph5SecBars && null != data && data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult aResult = (LoadHistoricDataPointBatchResult) data;
                        if (null != aResult.loadedPoints && 0 < aResult.loadedPoints.size()) {
                            AbstractGraphPoint prevDayClose = aResult.loadedPoints.last();
                            this.graph5SecBars.setPrevDayClose(prevDayClose);
                            this.graphReqMarketData.setPrevDayClose(prevDayClose);
                            this.graphHistoricData.setPrevDayClose(prevDayClose);
                            BaseGraph<AbstractGraphPoint> replicateGraph = this.graph5SecBars.replicateGraph();
                            replicateGraph.clear();
                            replicateGraph.setPrevDayClose(null);
                            replicateGraph.storeHistoricData(aResult.loadedPoints);
                            this.graph5SecBars.addPreviousGraph(prevDayClose.getDateAsNumber(), replicateGraph);
                            this.graphReqMarketData.addPreviousGraph(prevDayClose.getDateAsNumber(), replicateGraph);
                            this.graphHistoricData.addPreviousGraph(prevDayClose.getDateAsNumber(), replicateGraph);
                        }
                    }
                    break;
                case HISTORICDATAFINISHED:
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult aResult = (LoadHistoricDataPointBatchResult) data;
                        AbstractGraphPoint lastPoint = aResult.loadedPoints.last();
                        //Is the data for 'today' or a previous day
                        Calendar today = this.myPutup.getTodaysDate();
                        int intToday = DTUtil.convertCalendarToIntDate(today);
                        if (intToday == lastPoint.getDateAsNumber()) {
                            //This is for today
                            this.graphHistoricData.addAll(aResult.loadedPoints);
                        } else {
                            //This is for previous day
                            int key = lastPoint.getDateAsNumber();
                            //See if the graph already exists
                            if (this.graphHistoricData.getPreviousGraphs().containsKey(key)) {
                                //Get existing Graph and add the new points
                                BaseGraph prevDayGraph = this.graphHistoricData.getPreviousGraphs().get(key);
                                prevDayGraph.addAll(aResult.loadedPoints);
                            } else {
                                //No graph exists add this as a new graph
                                BaseGraph<AbstractGraphPoint> pointsAsGraph = aResult.getPointsAsGraph();
                                this.graphHistoricData.addPreviousGraph(key, pointsAsGraph);
                            }
                        }
                    }
                    break;
                case HISTORICDATAPREVIOUSDAYS:
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult aResult = (LoadHistoricDataPointBatchResult) data;
                        BaseGraph<AbstractGraphPoint> pointsAsGraph = aResult.getPointsAsGraph();
                        AbstractGraphPoint lastPoint = aResult.loadedPoints.last();
                        int key = lastPoint.getDateAsNumber();
                        //See if the graph already exists
                        if (this.graphHistoricData.getPreviousGraphs().containsKey(key)) {
                            //Get existing Graph and add the new points
                            BaseGraph prevDayGraph = this.graphHistoricData.getPreviousGraphs().get(key);
                            prevDayGraph.addAll(aResult.loadedPoints);
                        } else {
                            //No graph exists add this as a new graph
                            this.graphHistoricData.addPreviousGraph(key, pointsAsGraph);
                        }
                    }
                    break;
                case HISTORICDATATODAY:
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                        //This data should be added to todays graph
                        this.graphHistoricData.addAll(result.loadedPoints);
                    }
                    break;
                case HISTORICDATAERROR:
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult aResult = (LoadHistoricDataPointBatchResult) data;
                        Exception execException = aResult.getExecException();
                        ShowJOptionWinError warning = new ShowJOptionWinError("Error on putup " + this.myPutup.getTickerCode() + ": " + execException.getMessage());
                        if (null == this.errorWin) {
                            this.errorWin = warning;
                            java.awt.EventQueue.invokeLater(warning);
                        }
                    }
                    break;
                case HISTORICDATATRADINGDAYS:
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                        //Each data point represents one trading day in the last week
                        TreeSet<Integer> newTradingDays = new TreeSet<Integer>();
                        for (AbstractGraphPoint currPoint : result.loadedPoints) {
                            int dateAsNumber = currPoint.getDateAsNumber();
                            newTradingDays.add(dateAsNumber);
                        }
                        //Now store this as the trading days
                        DTConstants.TRADINGDAYSLASTWEEK = newTradingDays;
                    }
                    break;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return the myPutup
     */
    public Putup getMyPutup() {
        //This object is thread safe and can be returned safely
        return myPutup;
    }

    /**
     * @return the graph5SecBars
     */
    public BaseGraph<AbstractGraphPoint> getGraph5SecBars() {
        //This object is thread safe and can be returned safely
        return graph5SecBars;
    }

    private void printToConsole(String strMsg) {
        java.awt.EventQueue.invokeLater(new ConMsg(strMsg));
    }

    /**
     * @return the graphReqMarketData
     */
    public BaseGraph<AbstractGraphPoint> getGraphReqMarketData() {
        //This object is thread safe and can be returned safely
        return graphReqMarketData;
    }

    /**
     * @return the graphHistoricData
     */
    public BaseGraph<AbstractGraphPoint> getGraphHistoricData() {
        //This object is thread safe and can be returned safely
        return graphHistoricData;
    }

    private void changeToNextState() {
        RulesStateEnum currState = this.rulesManager.getCurrState();
        //Lock out rules testing while we change state
        switch (currState) {
            case FIVESECBARONLY:
                //This means that both the FTG and 3M Ranges are passed in the 'clue' version
                this.changeToState(RulesStateEnum.FIVESECBARPLUSMARKETDATA);
                break;
            case FIVESECBARPLUSMARKETDATA:
                //This means that a new Single / Double clue has been found and is being tested for validity
                this.changeToState(RulesStateEnum.TESTINGSINGLEDOUBLE);
                break;
        }
    }

    private void changeToState(RulesStateEnum newState) {
        lock.lock();
        try {
            switch (newState) {
                case FIVESECBARONLY:
                    this.rulesManager.changeRuleStateTo(RulesStateEnum.FIVESECBARONLY);
                    break;
                case FIVESECBARPLUSMARKETDATA:
                    this.rulesManager.changeRuleStateTo(RulesStateEnum.FIVESECBARPLUSMARKETDATA);
                    //Make the request for market data
                    if (null == this.reqMrkDataReq || (null != this.reqMrkDataReq && !this.reqMrkDataReq.isRunning())) {
                        this.reqMrkDataReq = null;
                        this.execReqMktData = Executors.newFixedThreadPool(1);
                        //this.execServiceReqMktData = new ExecutorCompletionService(DTConstants.THREAD_POOL);
                        RequestMarketDataTask newReq = new RequestMarketDataTask(this.graphReqMarketData, this.genAcc);
                        this.reqMrkDataReq = newReq;
                        //this.execServiceReqMktData.submit(newReq);
                        CallableToRunnable task = new CallableToRunnable(newReq);
                        Thread newThread = new Thread(task);
                        newThread.start();
                    }
                    break;
                case TESTINGSINGLEDOUBLE:
                    //NB the rules to run in this state are the same as the rules for FIVESECBARPLUSMARKETDATA but we also need to start another thread to test the
                    //newly found Single Double pattern. This is done by the rulesManager.
                    this.rulesManager.changeRuleStateTo(RulesStateEnum.TESTINGSINGLEDOUBLE);
                    break;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Scans the historic data graph to identify the latest Single Double pattern on the
     * graph. This is a check for a full and complete pattern (NOT the clue version)
     * @return The latest Single Double pattern found or NULL if none can be found.
     */
    public SingleDoublePattern lookForNewSingleDoublePatternsOnGraph() {
        return this.patternChecker.getSingleDoublePatternsOnGraph(this.graphHistoricData);
    }

    /**
     * The real ie non-clue version of the FTG Rule check used for this putup
     * @return boolean True if this rule has been passed at any point in the day
     * False otherwise
     */
    public boolean isFTGBreached() {
        boolean result = false;
        result = this.ftgChecker.runRule();
        return result;
    }

    /**
     * This function takes the currently loaded HISTORIC data graph for this putup
     * and tests if it is 'in 3M Range' using the full ie non-clue version of this rule
     * @return boolean True if the current historic data graph for this putup passes 
     * the rule, False otherwise.
     */
    public boolean isIn3MRange() {
        boolean result = false;
        result = this.threeMChecker.runRule();
        return result;
    }

    /**
     * Test to confirm the the putup is still receiving 5 sec bar data from the 
     * stock brokers API.
     * @return boolean True if the task requesting 5 sec bar data is still running in 
     * its thread, False otherwise.
     */
    public boolean isGetting5SecBars() {
        boolean result = false;
        if (null != this.initialReq) {
            result = this.initialReq.isRunning();
        }
        return result;
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, RealTimeRunManager dest) {
        boolean result = false;
        if (null != reader) {
            boolean abort = false;
            if (null == dest) {
                dest = this;
            }
            //Define temp holding variables
            Putup tempPutup = null;
            BaseGraph<AbstractGraphPoint> tempGraph5SecBars = null;
            BaseGraph<AbstractGraphPoint> tempGraphReqMarketData = null;
            BaseGraph<AbstractGraphPoint> tempGraphHistoricData = null;
            RulesStateEnum prevState = null;
            HistoricDataGraphPoint prevDayClose = null;
            BaseGraph<AbstractGraphPoint> tempGraphPrevDayData = null;

            while (!abort) {
                try {
                    //Load the file here
                    XMLEvent nextEvent = reader.nextEvent();
                    if (nextEvent.isStartElement() || nextEvent.isEndElement()) {

                        if (nextEvent.isStartElement()) {
                            StartElement xmlStartEvent = nextEvent.asStartElement();
                            String name = xmlStartEvent.getName().getLocalPart();
                            if (name.equals("Putup")) {
                                //Load the Putup
                                tempPutup = new Putup();
                                if (!tempPutup.loadFromXMLStream(reader, tempPutup)) {
                                    tempPutup = null;
                                }
                            }
                            if (name.equals("RulesState")) {
                                //Load the rules state
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                prevState = RulesStateEnum.parseFromString(strData);
                            }
                            if (name.equals("FiveSecBars")) {
                                //Load the 5 sec bar graph
                                tempGraph5SecBars = new BaseGraph<AbstractGraphPoint>();
                                if (!tempGraph5SecBars.loadFromXMLStream(reader, tempGraph5SecBars)) {
                                    tempGraph5SecBars = null;
                                }
                            }
                            if (name.equals("MarketDataBars")) {
                                //Load the Market Data Bars graph
                                tempGraphReqMarketData = new BaseGraph<AbstractGraphPoint>();
                                if (!tempGraphReqMarketData.loadFromXMLStream(reader, tempGraphReqMarketData)) {
                                    tempGraphReqMarketData = null;
                                }
                            }
                            if (name.equals("HistoricData")) {
                                //Load the Historic Data Graph
                                tempGraphHistoricData = new BaseGraph<AbstractGraphPoint>();
                                if (!tempGraphHistoricData.loadFromXMLStream(reader, tempGraphHistoricData)) {
                                    tempGraphHistoricData = null;
                                }
                            }
                            if (name.equals("PrevDayClose")) {
                                //Load the Prev Day Close value
                                prevDayClose = new HistoricDataGraphPoint();
                                if (!prevDayClose.loadFromXMLStream(reader, prevDayClose)) {
                                    prevDayClose = null;
                                }
                            }
                            if (name.equals("PrevDayData")) {
                                //Load the PrevDayData graph
                                tempGraphPrevDayData = new BaseGraph<AbstractGraphPoint>();
                                if (!tempGraphPrevDayData.loadFromXMLStream(reader, tempGraphPrevDayData)) {
                                    tempGraphPrevDayData = null;
                                }
                            }
                        }

                        if (nextEvent.isEndElement()) {
                            EndElement xmlEndEvent = nextEvent.asEndElement();
                            String name = xmlEndEvent.getName().getLocalPart();
                            if (name.equals("RealTimeRunManger")) {
                                abort = true;
                            }
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(RealTimeRunManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            //Ensure we have valid results for all temp variables
            if (null != tempPutup
                    && null != tempGraph5SecBars
                    && null != tempGraphReqMarketData
                    && null != tempGraphHistoricData
                    && null != tempGraphPrevDayData
                    && null != prevDayClose) {
                //We have valid data setup the relationships for the manager to work
                dest.myPutup = tempPutup;
                dest.myPutup.setRunManager(dest);
                dest.graph5SecBars = tempGraph5SecBars;
                dest.graphReqMarketData = tempGraphReqMarketData;
                dest.graphHistoricData = tempGraphHistoricData;

                //Initialise 5 Sec bar graph
                dest.graph5SecBars.setPutup(dest.myPutup);
                dest.graph5SecBars.setTradingDays(DTConstants.TRADINGDAYSLASTWEEK);

                //Initialise Request Market Data graph
                dest.graphReqMarketData.setPutup(dest.myPutup);
                dest.graphReqMarketData.setTradingDays(DTConstants.TRADINGDAYSLASTWEEK);

                //Initialise Historic Data graph
                dest.graphHistoricData.setPutup(dest.myPutup);
                dest.graphHistoricData.setTradingDays(DTConstants.TRADINGDAYSLASTWEEK);

                dest.graph5SecBars.setPrevDayClose(prevDayClose);
                dest.graphReqMarketData.setPrevDayClose(prevDayClose);
                dest.graphHistoricData.setPrevDayClose(prevDayClose);

                dest.myPutup.setTodaysDate(Calendar.getInstance(DTConstants.EXCH_TIME_ZONE));
                dest.rulesManager = new RulesStateManager(dest.myPutup.getPutupType(), dest);
                dest.patternChecker = new SingleDoubleCheck(dest.graphHistoricData);
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        if (null != writer) {
            try {
                writer.writeStartDocument();
                writer.writeStartElement("RealTimeRunManger");

                //writer.writeStartElement("Putup");
                this.myPutup.acquireObjectLock();
                try {
                    this.myPutup.writeAsXMLToStream(writer);
                } finally {
                    this.myPutup.releaseObjectLock();
                }
                //writer.writeEndElement();

                writer.writeStartElement("RulesState");
                writer.writeCharacters(this.rulesManager.getCurrState().toString());
                writer.writeEndElement();

                writer.writeStartElement("FiveSecBars");
                this.graph5SecBars.acquireObjectLock();
                try {
                    this.graph5SecBars.writeAsXMLToStream(writer);
                } finally {
                    this.graph5SecBars.releaseObjectLock();
                }
                writer.writeEndElement();

                writer.writeStartElement("MarketDataBars");
                this.graphReqMarketData.acquireObjectLock();
                try {
                    this.graphReqMarketData.writeAsXMLToStream(writer);
                } finally {
                    this.graphReqMarketData.releaseObjectLock();
                }
                writer.writeEndElement();

                writer.writeStartElement("HistoricData");
                this.graphHistoricData.acquireObjectLock();
                try {
                    this.graphHistoricData.writeAsXMLToStream(writer);
                } finally {
                    this.graphHistoricData.releaseObjectLock();
                }
                writer.writeEndElement();

                writer.writeStartElement("PrevDayClose");
                AbstractGraphPoint prevDayClose = this.graph5SecBars.getPrevDayClose();
                if (null != prevDayClose) {
                    prevDayClose.writeAsXMLToStream(writer);
                }
                writer.writeEndElement();

                writer.writeStartElement("PrevDayData");
                BaseGraph<AbstractGraphPoint> prevDayGraph = this.graphHistoricData.getPrevDayGraph();
                if (null != prevDayGraph) {
                    prevDayGraph.acquireObjectLock();
                    try {
                        prevDayGraph.writeAsXMLToStream(writer);
                    } finally {
                        prevDayGraph.releaseObjectLock();
                    }
                }
                writer.writeEndElement();

                writer.writeEndElement();
                writer.writeEndDocument();
            } catch (XMLStreamException ex) {
                Logger.getLogger(RealTimeRunManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }

    /**
     * A Runnable that shows an Error message box with the specified message on the GUI
     */
    private class ShowJOptionWinError implements Runnable {

        private String msg;

        public ShowJOptionWinError(String newMsg) {
            this.msg = newMsg;
        }

        @Override
        public void run() {
            JOptionPane.showMessageDialog(null, this.msg, "Error on putup", JOptionPane.ERROR_MESSAGE);
            errorWin = null;
        }
    }

    /**
     * A Java Runnable that outputs a message to the console
     */
    private class ConMsg implements Runnable {

        private String strMsg;

        public ConMsg(String strNewMsg) {
            this.strMsg = strNewMsg;
        }

        @Override
        public void run() {
            System.out.println(this.strMsg);
        }
    }

    /**
     * Wrapper class to convert a Java Callable to a Java Runnable
     */
    private class CallableToRunnable implements Runnable {

        private Callable callable;

        public CallableToRunnable(Callable target) {
            this.callable = target;
        }

        @Override
        public void run() {
            try {
                this.callable.call();
            } catch (Exception ex) {
                Logger.getLogger(RealTimeRunManager.class.getName()).log(Level.SEVERE, null, ex);
                System.err.println("Callable error was: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
}
