/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;
import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.HistoricDataGraphPoint;
import daytrader.datamodel.Putup;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.interfaces.ICallback;
import daytrader.interfaces.IHDTCallable;
import daytrader.utils.DTUtil;
import daytradertasks.LoadHistoricDataPointBatchResult;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.JOptionPane;

/**
 * This class will be the foundation from which classes that load historic data
 * should extend.
 *
 * @author Roy
 */
public abstract class AbstractHDTCallable implements IHDTCallable, EWrapper {

    private static final boolean DEBUG = true;
    /**
     * The TWSAccount that is executing this historic request
     */
    protected TWSAccount executingAccount;
    /**
     * The Contract object used by the Stock brokers API to identify the user
     */
    protected Contract objContract;
    /**
     * A Putup object encapsulating the details of the stock market security to
     * which this request relates
     */
    protected Putup putup;
    /**
     * A Calendar encapsulating a date / time representing the point in time
     * UP TO WHICH the historic data is required. For example a request for 30 min
     * of historic trading data covering the period from the start of trading at
     * 09:30 to 10:00 would have an end time of 10:00.
     */
    protected Calendar endDate;
    /**
     * An instance of the stock brokers API socket class (provided by the executing
     * TWSAccount object).
     */
    protected EClientSocket m_client;
    private boolean loadComplete;
    private boolean abort;
    /**
     * A string used to store a message to return to the user in the event that 
     * the operation is aborted
     */
    protected String strAbortMsg;
    //This attribute will hold the final loadedPoints.
    /**
     * All historic data request produce a collection of Price / Time points
     * (ie some form of AbstractGraphPoint) this TreeSet is used to store
     * these points in a time ordered sequence
     */
    protected TreeSet<AbstractGraphPoint> loadedPoints;
    /**
     * When an historic data request completes it must pass the loaded data points
     * to some code that handles the response. This is achieved via a callback 
     * to the handling code (usually in the class that made the request). This 
     * object is a reference to the result handler that processes the callback.
     */
    protected ICallback cbDelegate;
    /**
     * The user assigns one callback to handle the result BUT when testing Bryn's
     * rules it is possible that a rule may have to wait for data to be loaded before
     * it can complete its test. This attribute is a cache of the rules that are currently 
     * waiting for this task to complete. These rules will always report test failure
     * (without actually running their test) until they receive a callback to indicate
     * that the task has completed and the required data is available. This process is
     * completely automated by the historic request system and the rules framework.
     */
    protected List<ICallback> cbList;
    private int resubmitAttempts;
    /**
     * This attribute is needed by the stock brokers API but is not currently used
     * except to store data required by the API
     */
    protected boolean m_bIsFAAccount;
    /**
     * This attribute is needed by the stock brokers API but is not currently used
     * except to store data required by the API
     */
    protected String m_FAAcctCodes;
    /**
     * This attribute is needed by the stock brokers API but is not currently used
     * except to store data required by the API
     */
    protected boolean doesConnect;
    /**
     *Moving to a wait / notifyAll system rather than a blocking while loop this is the monitor object to wait on
     */
    protected final Object monitor = new Object();
    /**
     * An attribute to get/set with the abort time (as a timestamp)
     */
    private long abortTime;
    /**
     * A lock to manage multi-threaded access to objects of this class
     */
    private ReentrantLock hdtCallableLock;

    /**
     * Default Constructor initialises attributes and the stock broker API
     */
    public AbstractHDTCallable() {
        this.init();
    }

    /**
     * Constructor that performs initialisation and accepts the minimum additional
     * data to make an historic data request (ie a putup (the market security the 
     * request is for) and a date / time representing the time the data is needed
     * up to)
     * @param newPutup - A Putup representing a stock market security
     * @param newEndTime - A Calendar representing the date / time at which the
     * returned data should finish
     */
    public AbstractHDTCallable(Putup newPutup, Calendar newEndTime) {
        this.putup = newPutup;
        this.endDate = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        this.endDate.setTimeInMillis(newEndTime.getTimeInMillis());
        this.init();
    }

    /**
     * Constructor that performs initialisation and accepts the minimum additional
     * data to make an historic data request (ie a putup (the market security the 
     * request is for) and a date / time representing the time the data is needed
     * up to). In addition it also accepts an object to callback to with the loaded
     * data. This object should 'do something' with the data that has been loaded.
     * @param newPutup - A Putup representing a stock market security
     * @param newEndTime - A Calendar representing the date / time at which the
     * returned data should finish
     * @param newCallback - A callback to be made after the requested data has been 
     * loaded to perform further processing
     */
    public AbstractHDTCallable(Putup newPutup, Calendar newEndTime, ICallback newCallback) {
        this(newPutup, newEndTime);
        this.cbDelegate = newCallback;
    }

    @Override
    public TWSAccount getExecutingAcc() {
        return this.executingAccount;
    }

    @Override
    public boolean setExecutingAcc(TWSAccount newAcc) {
        boolean result = false;
        if (null != newAcc) {
            this.executingAccount = newAcc;
            this.m_client = this.executingAccount.getConnectionSocket();
            this.executingAccount.getConManager().addAbstractHDTCallable(this);
            result = true;
        }
        return result;
    }

    @Override
    public ICallback getCallback() {
        return this.cbDelegate;
    }

    @Override
    public boolean setCallback(ICallback newCallback) {
        boolean result = false;
        if (null != newCallback) {
            this.cbDelegate = newCallback;
            result = true;
        }
        return result;
    }

    @Override
    public List<ICallback> getCallBackList() {
        List<ICallback> result = new ArrayList<ICallback>();
        if (null != this.cbDelegate) {
            result.add(cbDelegate);
        }
        result.addAll(this.cbList);
        return result;
    }

    @Override
    public boolean addCallBack(ICallback newCallBack) {
        boolean result = false;
        if (null != newCallBack) {
            result = this.cbList.add(newCallBack);
        }
        return result;
    }

    @Override
    public abstract LoadHistoricDataPointBatchResult call() throws Exception;

    /**
     * Initialises the attributes used by the Historic Request Processing Framework
     * and the Stock brokers API
     */
    protected final void init() {
        this.hdtCallableLock = new ReentrantLock();
        this.resubmitAttempts = 0;
        this.cbList = new LinkedList<ICallback>();
        this.doesConnect = true;
        this.createContract();
        this.initialiseAPI();
    }

    /**
     * Connects to the stock brokers server if the executing account has not already 
     * done so.
     * @throws TWSConnectionException is thrown if the connection fails to be made.
     */
    protected void connect() throws TWSConnectionException {
        if (!this.isConnected()) {
            int clientId = this.objContract.m_conId;
            this.m_client.eConnect("127.0.0.1", this.executingAccount.getPortNo(), clientId);
            if (!m_client.isConnected()) {
                throw new TWSConnectionException("Could not connect to stock broker");
            }
        }
    }

    /**
     * Originally used to disconnect from stock brokers server but this is now 
     * managed by the TWSAccount object. This method remains as a hook which can
     * be used to add code (by overriding) that should run on task completion or
     * failure.
     */
    protected void disconnect() {
        //Refactored code the TWS Account is now responsible for disconnecting the socket
        //m_client.eDisconnect();
    }

    /**
     * Tests to confirm that the connection to the stock brokers server is alive
     * @return boolean True if the socket object is connected, False otherwise.
     */
    protected boolean isConnected() {
        boolean result = false;
        if (null != this.m_client) {
            result = this.m_client.isConnected();
        }
        return result;
    }

    /**
     * Each historic request must include a Contract object (class defined in
     * stock brokers API). This defines a unique ID for this request the 
     * individual security for which the request is made and the market on which
     * that security is traded.
     */
    public void createContract() {
        //Create a standard contract to use when issuing the API call
        this.objContract = new Contract();
        objContract.m_conId = DTConstants.getConId();
        objContract.m_symbol = this.putup.getTickerCode();
        objContract.m_secType = "STK";
        objContract.m_exchange = "SMART";
        objContract.m_currency = "USD";
        //objContract.m_primaryExch = this.putup.getMarket().toString();
        
//<editor-fold defaultstate="collapsed" desc="New Stuff to match sample program">
        objContract.m_expiry = "";
        objContract.m_right = "";
        objContract.m_multiplier = "";
        objContract.m_localSymbol = "";
        objContract.m_tradingClass = "";
        objContract.m_primaryExch = null;
        objContract.m_secIdType = "";
//</editor-fold>
    }

    @Override
    public int getReqId() {
        return this.objContract.m_conId;
    }

    /**
     * Originally used to create the socket connection to the stock brokers server
     * this function has been moved into the TWSAccount class but this method 
     * remains as a 'hook' which can be overridden to add code to run when a 
     * historic data request is created.
     */
    protected void initialiseAPI() {
        //Create socket connection
        //Removed this refactored code so that the TWS Account assigned to execute the callable provides
        //a socket connection
        //this.m_client = new EClientSocket(this);
    }

    /**
     * Strictly the stock market should not trade before its opening time or after its closing time.
     * However some securities have been seen to do so (starting shortly before the 09:30 opening time
     * and possibly continuing AFTER the 16:00 close time). This data if returned as part of a 
     * request is to be IGNORED (per Bryn). ONLY data between open and close times
     * is valid for inclusion in the data model. This method scans the loaded data 
     * and eliminates any data points outside of trading hours.
     */
    protected void filterData() {
        if (null != this.loadedPoints && 0 < this.loadedPoints.size()) {
            TreeSet<AbstractGraphPoint> tempData = new TreeSet<AbstractGraphPoint>();
//            Calendar exchOpen = DTUtil.getExchOpeningCalendar(this.endDate);
//            long lngExchOpen = exchOpen.getTimeInMillis();
//            Calendar exchClose = DTUtil.getExchClosingCalendar(this.endDate);
//            long lngExchClose = exchClose.getTimeInMillis();
            for (AbstractGraphPoint currPoint : this.loadedPoints) {
                Calendar calDate = currPoint.getCalDate();
                Calendar exchOpen = DTUtil.getExchOpeningCalendar(calDate);
                long lngExchOpen = exchOpen.getTimeInMillis();
                Calendar exchClose = DTUtil.getExchClosingCalendar(calDate);
                long lngExchClose = exchClose.getTimeInMillis();
                long timestamp = currPoint.getTimestamp();
                if (lngExchOpen <= timestamp && lngExchClose >= timestamp) {
                    tempData.add(currPoint);
                }
            }
            //Now store filtered data as final result
            this.loadedPoints = tempData;
        }
    }

    private void abortOperation(String msg) {
        if (!this.isAbort()) {
            this.strAbortMsg = msg;
            this.setAbort(true);
            this.setLoadComplete(true);
        }
    }

    /**
     * For documentation see page 265 - 266 of stockbroker API documentation
     * @param tickerId
     * @param field
     * @param price
     * @param canAutoExecute
     */
    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. tickPrice"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 266 of stockbroker API documentation
     * @param tickerId
     * @param field
     * @param size
     */
    @Override
    public void tickSize(int tickerId, int field, int size) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. tickSize"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 267 of stockbroker API documentation
     * @param tickerId
     * @param field
     * @param impliedVol
     * @param delta
     * @param optPrice
     * @param pvDividend
     * @param gamma
     * @param vega
     * @param theta
     * @param undPrice
     */
    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. tickOptionComputation"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 267 of stockbroker API documentation
     * @param tickerId
     * @param tickType
     * @param value
     */
    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. tickGeneric"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 268 of stockbroker API documentation
     * @param tickerId
     * @param tickType
     * @param value
     */
    @Override
    public void tickString(int tickerId, int tickType, String value) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. tickString"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 268 of stockbroker API documentation
     * @param tickerId
     * @param tickType
     * @param basisPoints
     * @param formattedBasisPoints
     * @param impliedFuture
     * @param holdDays
     * @param futureExpiry
     * @param dividendImpact
     * @param dividendsToExpiry
     */
    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. tickEFP"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 269 - 270 of stockbroker API documentation
     * @param orderId
     * @param status
     * @param filled
     * @param remaining
     * @param avgFillPrice
     * @param permId
     * @param parentId
     * @param lastFillPrice
     * @param clientId
     * @param whyHeld
     */
    @Override
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. orderStatus"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 271 of stockbroker API documentation
     * @param orderId
     * @param contract
     * @param order
     * @param orderState
     */
    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. openOrder"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * NO DOCUMENTATION AVAILABLE IN STOCKBROKERS PDF
     */
    @Override
    public void openOrderEnd() {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. openOrderEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 271 - 272 of stockbroker API documentation
     * @param key
     * @param value
     * @param currency
     * @param accountName
     */
    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. updateAccountValue"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 272 of stockbroker API documentation
     * @param contract
     * @param position
     * @param marketPrice
     * @param marketValue
     * @param averageCost
     * @param unrealizedPNL
     * @param realizedPNL
     * @param accountName
     */
    @Override
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. updatePortfolio"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param timeStamp
     */
    @Override
    public void updateAccountTime(String timeStamp) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. updateAccountTime"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * NO DOCUMENTATION AVAILABLE IN STOCKBROKERS PDF
     * @param accountName
     */
    @Override
    public void accountDownloadEnd(String accountName) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. accountDownloadEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 271 of stockbroker API documentation
     * @param orderId
     */
    @Override
    public void nextValidId(int orderId) {
        //The next valid order ID is irrelevent for this task. ignore this callback.
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     * @param contractDetails
     */
    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. contractDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     * @param contractDetails
     */
    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. bondContractDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void contractDetailsEnd(int reqId) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. contractDetailsEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 - 274 of stockbroker API documentation
     * @param reqId
     * @param contract
     * @param execution
     */
    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. execDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 274 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void execDetailsEnd(int reqId) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. execDetailsEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 274 - 275 of stockbroker API documentation
     * @param tickerId
     * @param position
     * @param operation
     * @param side
     * @param price
     * @param size
     */
    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. updateMktDepth"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 275 of stockbroker API documentation
     * @param tickerId
     * @param position
     * @param marketMaker
     * @param operation
     * @param side
     * @param price
     * @param size
     */
    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. updateMktDepthL2"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 275 - 276 of stockbroker API documentation
     * @param msgId
     * @param msgType
     * @param message
     * @param origExchange
     */
    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. updateNewsBulletin"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 276 of stockbroker API documentation
     * @param accountsList
     */
    @Override
    public void managedAccounts(String accountsList) {
        //This just stores the account list on connection (need to code something for this callback and I can see it working with this)
        this.m_bIsFAAccount = true;
        this.m_FAAcctCodes = accountsList;
    }

    /**
     * For documentation see page 276 of stockbroker API documentation
     * @param faDataType
     * @param xml
     */
    @Override
    public void receiveFA(int faDataType, String xml) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. receiveFA"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 280 of stockbroker API documentation
     * @param reqId
     * @param date
     * @param open
     * @param high
     * @param low
     * @param close
     * @param volume
     * @param count
     * @param WAP
     * @param hasGaps
     */
    @Override
    public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        if (date.contains("finished")) {
            //All data has arrived
            this.setLoadComplete(true);
        } else {
            //This is a new point to add to the results
            HistoricDataGraphPoint newItem = new HistoricDataGraphPoint(reqId, date, open, high, low, close, volume, count, WAP, hasGaps);
            this.loadedPoints.add(newItem);
            if (DEBUG) {
                System.out.println("Added data point to: " + this.toString());
            }
        }
    }

    /**
     * For documentation see page 280 of stockbroker API documentation
     * @param xml
     */
    @Override
    public void scannerParameters(String xml) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. scannerParameters"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 280 - 281 of stockbroker API documentation
     * @param reqId
     * @param rank
     * @param contractDetails
     * @param distance
     * @param benchmark
     * @param projection
     * @param legsStr
     */
    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. scannerData"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 281 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void scannerDataEnd(int reqId) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. scannerDataEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 281 - 282 of stockbroker API documentation
     * @param reqId
     * @param time
     * @param open
     * @param high
     * @param low
     * @param close
     * @param volume
     * @param wap
     * @param count
     */
    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. realtimeBar"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 264 - 265 of stockbroker API documentation
     * @param time
     */
    @Override
    public void currentTime(long time) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. currentTime"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 282 of stockbroker API documentation
     * @param reqId
     * @param data
     */
    @Override
    public void fundamentalData(int reqId, String data) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. fundamentalData"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * See p 437 of stock broker's documentation
     * @param reqId
     * @param underComp
     */
    @Override
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. deltaNeutralValidation"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 269 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void tickSnapshotEnd(int reqId) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. tickSnapshotEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 269 of stockbroker API documentation
     * @param reqId
     * @param marketDataType
     */
    @Override
    public void marketDataType(int reqId, int marketDataType) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. marketDataType"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 274 of stockbroker API documentation
     * @param commissionReport
     */
    @Override
    public void commissionReport(CommissionReport commissionReport) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet. commissionReport"); //To change body of generated methods, choose Tools | Templates.
    }
    
//<editor-fold defaultstate="collapsed" desc="New interface methods">
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
//</editor-fold>

    /**
     * For documentation see page 265 of stockbroker API documentation
     * @param e
     */
    @Override
    public void error(Exception e) {
        this.strAbortMsg = "Exception from stockbrokers server: " + e.getMessage();
        this.abortOperation(this.strAbortMsg);
        this.disconnect();
    }

    /**
     * For documentation see page 265 of stockbroker API documentation
     * @param str
     */
    @Override
    public void error(String str) {
        this.strAbortMsg = str;
        this.abortOperation(this.strAbortMsg);
        this.disconnect();
    }

    /**
     * For documentation see page 265 of stockbroker API documentation and error codes
     * on pages 374 to 386
     * @param id
     * @param errorCode
     * @param errorMsg
     */
    @Override
    public void error(int id, int errorCode, String errorMsg) {
        switch (errorCode) {
            case 162:
                if (!errorMsg.contains("query returned no data")) {
                    if (errorMsg.contains("pacing violation")) {
                        //Record a Pacing Violation
                        this.executingAccount.recordPacingViolation();
                        //Create a new task to re-run this failed task
                        HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
                        HRSCallableWrapper wrapper = new HRSCallableWrapper(this, PriorityEnum.IMMEDIATE);
                        HRSys.submitRequest(wrapper);
                        //Now abort this operation and warn user of pacing violation
                        this.abortOperation("A Pacing violation has occured a " + this.getClass().getName() + " task has been re-submitted");
                        if(DEBUG){
                            EventQueue.invokeLater(new Runnable() {

                                @Override
                                public void run() {
                                    JOptionPane.showMessageDialog(null, "A Pacing Violation occured task resubmitted", "Pacing Violation", JOptionPane.WARNING_MESSAGE);
                                }
                            });
                        }
                    } else if (errorMsg.contains("TWS session is connected from a different IP address")) {
                        String msg = "";
                        if (null != this.executingAccount) {
                            msg = this.executingAccount.getAccName();
                        }
                        DiffIPMessage alert = new DiffIPMessage(msg);
                        EventQueue.invokeLater(alert);
                        this.abortOperation(errorMsg);
                    } else {
                        this.abortOperation("Error Code 162: Error Message: " + errorMsg);
                    }
                } else {
                    //No data was returned the task is complete
                    this.setLoadComplete(true);
                }
                break;
            case 502:
//                this.abort = true;
//                this.strAbortMsg = "Not Connected to stock broker(502): " + errorMsg;
                this.abortOperation("Not Connected to stock broker(502): " + errorMsg);
                break;
            case 504:
//                this.abort = true;
//                this.strAbortMsg = "Not Connected to stock broker(504): " + errorMsg;
                this.abortOperation("Not Connected to stock broker(504): " + errorMsg);
                break;
            case 2103:
                //The connection to the stock brokers server has been lost
//                this.abort = true;
//                this.strAbortMsg = "Connection lost to stock broker(2103): " + errorMsg;
                this.abortOperation("Connection lost to stock broker(2103): " + errorMsg);
                break;
            case 2104:
                //This indicates that you have connected not sure why thats an error but ignore it
                break;
            case 2105:
                //The connection to the stock brokers server has been lost
//                this.abort = true;
//                this.strAbortMsg = "Connection lost to stock broker(2105): " + errorMsg;
                this.abortOperation("Connection lost to stock broker(2105): " + errorMsg);
                break;
            case 2106:
                //This indicates that you have connected not sure why thats an error but ignore it
                break;
            case 2107:
                //HMDS data farm connection is inactive but should be available upon demand.ushmds
                //This indicates a tempory (10s) disconnect. TWS manages these and trys to reconnect so 
                //we can ignore this error. If reconnecting is not made then a connection lost error
                //will be generated
                break;
            case 2110:
                //The connection to the stock brokers server has been lost
//                this.abort = true;
//                this.strAbortMsg = "Connection lost to stock broker(2110): " + errorMsg;
                this.abortOperation("Connection lost to stock broker(2110): " + errorMsg);
                break;
            default:
//                this.abort = true;
//                this.strAbortMsg = errorMsg;
//                System.err.println("ID = " + id + ", Error no: " + errorCode + ", Message = " + errorMsg);
                this.abortOperation("ID = " + id + ", Error no: " + errorCode + ", Message = " + errorMsg);
                this.disconnect();
        }
    }

    /**
     * For documentation see page 265 of stockbroker API documentation
     */
    @Override
    public void connectionClosed() {
        this.strAbortMsg = "Connection to stock brokers server was closed";
        this.abortOperation(this.strAbortMsg);
    }

    /**
     * Accessor method to retrieve the number of times this request has been re-submitted
     * @return integer being the number of times the historic processing system has
     * tried to re-submit this request to the stock broker
     */
    public int getResubmitAttempts() {
        return resubmitAttempts;
    }

    /**
     * Accessor method to increment and then retrieve the number of times the historic
     * processing system has re-submitted this request.
     * @return integer being the number of times the historic processing system has
     * tried to re-submit this request to the stock broker 
     */
    public int incrementAndGetResubmitAttempts() {
        this.resubmitAttempts++;
        return this.resubmitAttempts;
    }

    /**
     * This method may be used to determine if the task completed successfully
     *
     * @return the loadComplete - boolean True if this task has successfully completed,
     * False otherwise.
     */
    public boolean isLoadComplete() {
        boolean result = false;
        hdtCallableLock.lock();
        try {
            result = this.loadComplete;
        } finally {
            hdtCallableLock.unlock();
        }
        return result;
    }

    /**
     * Accessor method to retrieve the flag that defines if this request needs a 
     * connection to the stock brokers server. NB: some requests need to 'generate'
     * multiple sub requests to load all the data they need. Such requests do not
     * connect directly to the stockbroker server but collate the result of other requests
     * Use this flag to identify such requests.
     * @return boolean True if this request uses a connection to the stock brokers 
     * server, False otherwise
     */
    public boolean makesConnection() {
        return this.doesConnect;
    }

    /**
     * Accessor method to retrieve a timestamp defining the date / time at which
     * this request should abort. No request should wait for a response forever 
     * @return long being the timestamp of the time at which if not complete already
     * this request should fail.
     */
    public long getAbortTime() {
        return abortTime;
    }

    /**
     * Accessor method to set a timestamp defining the date / time at which
     * this request should abort.
     * @param abortTime long the timestamp to use for aborting this request
     */
    public void setAbortTime(long abortTime) {
        this.abortTime = abortTime;
    }

    /**
     * Tests to determine if the current system time is greater than or equal to
     * the specified abort time
     * @return boolean True if the abort time has been reached or has passed, False
     * otherwise.
     */
    public boolean isPassedAbortTime() {
        boolean result = false;
        long now = System.currentTimeMillis();
        if (now >= this.abortTime) {
            result = true;
        }
        return result;
    }

    /**
     * Accessor method to test if the flag has been set indicating that an abort
     * operation should occur.
     * @return boolean True if this task has been flagged as failed and it should
     * abort its operation, False otherwise.
     */
    public boolean isAbort() {
        boolean result = true;
        hdtCallableLock.lock();
        try {
            result = this.abort;
        } finally {
            hdtCallableLock.unlock();
        }
        return result;
    }

    /**
     * Accessor method to set the flag indicating that an abort
     * operation should occur.
     * @param abort - boolean True if this request should abort at the next 
     * earliest opportunity, False otherwise
     */
    public void setAbort(boolean abort) {
        hdtCallableLock.lock();
        try {
            this.abort = abort;
            if (this.abort) {
                synchronized (this.monitor) {
                    this.monitor.notifyAll();
                }
            }
        } finally {
            hdtCallableLock.unlock();
        }
    }

    /**
     * Accessor Method used to mark this request as successfully completed.
     * @param loadComplete boolean new value for the flag
     */
    public void setLoadComplete(boolean loadComplete) {
        hdtCallableLock.lock();
        try {
            this.loadComplete = loadComplete;
            if (this.loadComplete) {
                synchronized (this.monitor) {
                    this.monitor.notifyAll();
                }
            }
        } finally {
            hdtCallableLock.unlock();
        }
    }

    /**
     * A Java Runnable to show a warning message that the account executing this request
     * is also in use by another computer.
     */
    private class DiffIPMessage implements Runnable {

        private String strMsg;

        public DiffIPMessage(String msg) {
            this.strMsg = msg;
        }

        @Override
        public void run() {
            JOptionPane.showMessageDialog(null, "Account " + this.strMsg + " connected from a different IP Address", "Multiple Login's detected", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    //Original code to here
}