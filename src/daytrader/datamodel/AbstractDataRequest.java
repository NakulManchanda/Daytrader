/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;
import daytrader.historicRequestSystem.TWSAccount;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This abstract class provides a base from which a request for ongoing data from the
 * Stock brokers API should extend (ie. RealTime Data not Historical Requests)
 *
 * @author Roy
 */
public abstract class AbstractDataRequest implements Callable<Void>, EWrapper {
    
    /**
     * A default value for the time that the executing thread should sleep
     */
    protected long SLEEP_TIME = 750;

    /**
     * DEBUG attribute to turn on / off debug output
     */
    protected static final boolean DEBUG = true;
    /**
     * Reference to the Putup for which the data request is being made
     */
    protected Putup myPutup;
    /**
     * A reference to the Price / Time graph into which the returned data will be stored.
     */
    protected BaseGraph<AbstractGraphPoint> graph;
    /**
     * A reference to the TWSAccount object that manages the connection to the stockbroker's
     * server. 
     */
    protected TWSAccount connectionAcc;
    /**
     * The ID number that identifies this request to the stockbrokers server
     */
    protected int lastClientId = -1;
    /**
     * Boolean flag that indicates if this real time data request should disconnect and stop
     * receiving data
     */
    protected boolean shouldDisconnect;
    /**
     * The Contract object required by the stockbroker's server to use for this data request
     */
    protected Contract objContract;
    /**
     * Boolean flag that indicates if this request is currently 'running' ie. receiving data 
     * from the stockbroker's server.
     */
    protected AtomicBoolean running;
    /**
     * In the event of an exception occurring this attribute stores it for processing / reference
     */
    protected Exception lastException;

    /**
     * Performs initialisation of the Data Request object. Creates empty price / time graph, obtains an
     * ID value for this request, initalises the flags and if an executing account is assigned registers
     * this as a data handler for the data.
     */
    protected final void initialise() {
        //Create Graph
        if (null == this.graph) {
            this.graph = new BaseGraph<AbstractGraphPoint>();
        }
        //Create Socket
        //this.m_client = new EClientSocket(this);
        //this.m_client = this.connectionAcc.getConnectionSocket();
        //Set flags
        this.lastClientId = DTConstants.getConId();
        this.shouldDisconnect = false;
        this.running = new AtomicBoolean(false);
        this.lastException = null;
        if(null != this.connectionAcc){
            this.connectionAcc.registerDataHandler(this);
        }
        //Create Contract
        this.generateContract();
    }
    
    /**
     * Accessor to retrieve the unique ID assigned to the data request
     * @return - integer being the ID number that identifies this request when communicating with the server
     */
    public int getReqId(){
        return this.lastClientId;
    }

    
    /**
     * Accessot to test if the TWSAccount assigned to make this data request is connected 
     * to the stockbrokers server.
     * @return boolean True if the account is connected, False otherwise.
     */
    public boolean isConnected() {
        boolean result = false;
        if (null != this.connectionAcc.getConnectionSocket()) {
            result = this.connectionAcc.getConnectionSocket().isConnected();
        }
        return result;
    }

    /**
     * If the TWS account is not connected to the server this method will connect it
     */
    public void connect() {
        if (!this.isConnected()) {
            this.connectionAcc.getConnectionSocket().eConnect("127.0.0.1", this.connectionAcc.getPortNo(), this.lastClientId);
        }
    }

    /**
     * Disconnection is now managed by the TWSAccount class hence this method no longer disconnects the socket 
     * from the server. However it does still set the flag to disconnect that is used to terminate the data 
     * request.
     */
    public void disconnect() {
        this.shouldDisconnect = true;
        //The TWS Account class will manage final disconnect
        //this.connectionAcc.getConnectionSocket().eDisconnect();
    }

    /**
     * Generates a standard Contract object to use when communicating with the stockbroker.
     */
    public final void generateContract() {
        //Generate contract
        this.objContract = new Contract();
        //this.objContract.m_conId = this.lastClientId;     //Not needed and causes live system to crash
        this.objContract.m_symbol = this.myPutup.getTickerCode();
        this.objContract.m_secType = "STK";
        this.objContract.m_exchange = "SMART";
        this.objContract.m_currency = "USD";
        this.objContract.m_primaryExch = this.myPutup.getMarket().toString();
        this.objContract.m_expiry = "";
        this.objContract.m_right = "";
        this.objContract.m_multiplier = "";
        this.objContract.m_localSymbol = "";
        this.objContract.m_secIdType = "";
        this.objContract.m_secId = "";
    }

    /**
     * Accessor retrieves the flag indicating that this request is running 
     * @return the running
     */
    public boolean isRunning() {
        return this.running.get();
    }

    /**
     * Accessor to retrieve the last exception that occurred
     * @return the lastException
     */
    public Exception getLastException() {
        return lastException;
    }

    //CALLABLE INTERFACE - START
    @Override
    public abstract Void call() throws Exception;
    //CALLABLE INTERFACE - END

    //EWRAPPER INTERFACE - START
    /**
     * For documentation see page 265 - 266 of stockbroker API documentation
     * @param tickerId
     * @param field
     * @param price
     * @param canAutoExecute
     */
    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
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
        throw new UnsupportedOperationException("Not supported yet. openOrder"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * NO DOCUMENTATION AVAILABLE IN STOCKBROKERS PDF
     */
    @Override
    public void openOrderEnd() {
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
        throw new UnsupportedOperationException("Not supported yet. updatePortfolio"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param timeStamp
     */
    @Override
    public void updateAccountTime(String timeStamp) {
        throw new UnsupportedOperationException("Not supported yet. updateAccountTime"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * NO DOCUMENTATION AVAILABLE IN STOCKBROKERS PDF
     * @param accountName
     */
    @Override
    public void accountDownloadEnd(String accountName) {
        throw new UnsupportedOperationException("Not supported yet. accountDownloadEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 271 of stockbroker API documentation
     * @param orderId
     */
    @Override
    public void nextValidId(int orderId) {
        //Most real time data calls do not need to know what the next valid order ID is. just ignore this by default
        //Override this method if you do need to know
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     * @param contractDetails
     */
    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        throw new UnsupportedOperationException("Not supported yet. contractDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     * @param contractDetails
     */
    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        throw new UnsupportedOperationException("Not supported yet. bondContractDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 273 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void contractDetailsEnd(int reqId) {
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
        throw new UnsupportedOperationException("Not supported yet. execDetails"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 274 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void execDetailsEnd(int reqId) {
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
        throw new UnsupportedOperationException("Not supported yet. updateNewsBulletin"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 276 of stockbroker API documentation
     * @param accountsList
     */
    @Override
    public void managedAccounts(String accountsList) {
        //A call back always comes to this method from the API but we do not need to use / keep the account list so it is ignored
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 276 of stockbroker API documentation
     * @param faDataType
     * @param xml
     */
    @Override
    public void receiveFA(int faDataType, String xml) {
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
        throw new UnsupportedOperationException("Not supported yet. historicalData"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 280 of stockbroker API documentation
     * @param xml
     */
    @Override
    public void scannerParameters(String xml) {
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
        throw new UnsupportedOperationException("Not supported yet. scannerData"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 281 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void scannerDataEnd(int reqId) {
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
        throw new UnsupportedOperationException("Not supported yet. realtimeBar"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 264 - 265 of stockbroker API documentation
     * @param time
     */
    @Override
    public void currentTime(long time) {
        throw new UnsupportedOperationException("Not supported yet. currentTime"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 282 of stockbroker API documentation
     * @param reqId
     * @param data
     */
    @Override
    public void fundamentalData(int reqId, String data) {
        throw new UnsupportedOperationException("Not supported yet. fundamentalData"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * See p 437 of stockbroker's documentation
     * @param reqId
     * @param underComp
     */
    @Override
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {
        throw new UnsupportedOperationException("Not supported yet. deltaNeutralValidation"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 269 of stockbroker API documentation
     * @param reqId
     */
    @Override
    public void tickSnapshotEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet. tickSnapshotEnd"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 269 of stockbroker API documentation
     * @param reqId
     * @param marketDataType
     */
    @Override
    public void marketDataType(int reqId, int marketDataType) {
        throw new UnsupportedOperationException("Not supported yet. marketDataType"); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * For documentation see page 274 of stockbroker API documentation
     * @param commissionReport
     */
    @Override
    public void commissionReport(CommissionReport commissionReport) {
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
        this.lastException = e;
    }

    /**
     * For documentation see page 265 of stockbroker API documentation
     * @param str
     */
    @Override
    public void error(String str) {
        throw new UnsupportedOperationException("Not supported yet. error(String)"); //To change body of generated methods, choose Tools | Templates.
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
            case 502:
                //For some reason the stock broker API spams this error code with the error message:
                //Confirm that "Enable ActiveX and Socket Clients" is enabled on the TWS "Configure->API" menu.
                //As this will be pre-configured I am ignoring this message. Any other message will throw an exception
                if(!errorMsg.contains("Enable ActiveX and Socket Clients")){
                    TWSConnectionException newEx =
                            new TWSConnectionException("Not Connected to stock broker. Stock ticker: "
                            + this.myPutup.getTickerCode()
                            + ", Server Message = "
                            + errorMsg);
                    this.lastException = newEx;
                }
                break;
            case 504:
                //We have lost connection to the server attempt to re-connect
                System.err.println("reqMrkData Error, Code = " + errorCode + " with message: " + errorMsg);
                this.connect();
                if(!this.isConnected()){
                    System.err.println("Reconnect attempt failed");
                }
                break;
            case 2104:
                //If this error says the farm is OK then we can ignore it (always received at connection) else throw exception
                if (!errorMsg.contains("data farm connection is OK")) {
                    this.shouldDisconnect = true;
                    this.running.set(false);
                    this.lastException = new TWSConnectionException(errorMsg);
                    this.disconnect();
                }
                break;
            case 2106:
                //If this error says the farm is OK then we can ignore it (always received at connection) else throw exception
                //if(!(errorMsg.equalsIgnoreCase("Market data farm connection is OK:cashfarm") || errorMsg.equalsIgnoreCase("Market data farm connection is OK:usfarm"))){
                if(!errorMsg.contains("data farm connection is OK")){
                    this.shouldDisconnect = true;
                    this.running.set(false);
                    this.lastException = new TWSConnectionException(errorMsg);
                    this.disconnect();
                }
                break;
            default:
                System.err.println("Error, Code = " + errorCode + " with message: " + errorMsg);
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    /**
     * For documentation see page 265 of stockbroker API documentation
     */
    @Override
    public void connectionClosed() {
        this.disconnect();
    }
    //EWRAPPER INTERFACE - END
}
