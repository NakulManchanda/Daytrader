/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.TickType;
import com.ib.client.UnderComp;
import java.io.IOException;
import java.util.Date;
import java.util.NavigableSet;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DEPRICATED DO NOT USE - Original experimental class for communicating with the
 * stock broker API and loading data. Not used in the production code but occasionally 
 * used for debugging.
 * Objects of this class load the real time data for a put up into a graph
 *
 * @author Roy
 */
public class RealTimeDataLoader implements Callable, EWrapper {

    private Putup myPutup;
    private BaseGraph<AbstractGraphPoint> graph;
    private int lastClientId = -1;
    private int reqRealTimeBarsId = -1;
    private EClientSocket m_client = new EClientSocket(this);
    private Contract objContract;
    private boolean shouldDisconnect = false;
    private static final long SLEEP_TIME = 750;
    private boolean m_bIsFAAccount;
    private String m_FAAcctCodes;
    private int intNextOrderId = -1;
    private AtomicBoolean updated;
    private AtomicBoolean validTimestamp;
    private AtomicLong lastCurrTimestamp;
    //Last items added for BID, ASK, RT5Bar HIGH / LOW
    private BidPriceResponse lastBidResponse = null;
    private AskPriceResponse lastAskResponse = null;
    private RealTimeBarGraphPoint last5SBarResponse = null;
    //Multiple threads need access to above three variables need a lock
    private Lock lock = new ReentrantLock();

    public RealTimeDataLoader(Putup newPutup) {
        this.myPutup = newPutup;
        this.graph = new BaseGraph<AbstractGraphPoint>();
        this.graph.setPutup(this.myPutup);
        this.lastClientId = DTConstants.getConId();
        this.reqRealTimeBarsId = DTConstants.getConId();
        this.updated = new AtomicBoolean(false);
        this.lastCurrTimestamp = new AtomicLong(0);

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

        this.validTimestamp = new AtomicBoolean(false);
    }

    @Override
    public BaseGraph<AbstractGraphPoint> call() throws Exception {
        this.connect();
        if (this.isConnected()) {
            try {
                //Make request for market data
                String tickType = "233";
                this.m_client.reqMktData(lastClientId, objContract, tickType, false);
                Thread.sleep(SLEEP_TIME);
                //Make request for real time data (5sec bars)
                this.m_client.reqRealTimeBars(this.reqRealTimeBarsId, objContract, 5, "TRADES", true);
                Thread.sleep(SLEEP_TIME);

                long oldTimestamp = System.currentTimeMillis();
                long currTimestamp;
                //Begin a loop that will last as long as we have an open connection to the data server
                while (!shouldDisconnect) {
                    currTimestamp = System.currentTimeMillis();
                    //System.out.println("Set curr time = " + lastCurrTimestamp + ", target is = " + (oldTimestamp + SLEEP_TIME));
                    if (currTimestamp >= (oldTimestamp + SLEEP_TIME)) {
                        oldTimestamp = currTimestamp;
                        //If no update has been received then I must add a point at same price as last item
                        //If update HAS been received then reset update flag and go to sleep
                        if (!this.updated.get()) {
                            //Lock the graph
                            this.graph.acquireObjectLock();
                            try {
                                //Add a point
                                AbstractGraphPoint last = this.graph.last();
                                if (null != last) {
                                    RTVolumeResponse item = new RTVolumeResponse(last, SLEEP_TIME);
                                    this.graph.add(item);
                                    System.out.println("Manual " + this.myPutup.getTickerCode() + " Item added. Graph size = " + this.graph.size() + ", Thread ID: " + Thread.currentThread().getId());
                                }
                            } finally {
                                this.graph.releaseObjectLock();
                            }
                        } else {
                            //Reset the flag
                            this.updated.set(false);
                        }
                        //The RTVolume 1 sec update is dealt with by the above code
                        //The code below updates the BID, ASK and Real Time 5 Sec bars
                        lock.lock();
                        try {
                            if (this.validTimestamp.get()) {
                                BidPriceResponse lastBid = this.getLastBidResponse();
                                if (null != lastBid && (lastBid.getTimestamp() + SLEEP_TIME) < currTimestamp) {
                                    BidPriceResponse newItem = new BidPriceResponse(lastBid, SLEEP_TIME);
                                    this.setLastBidResponse(newItem);
                                    this.graph.add(newItem);
                                    System.out.println("Manual BID " + this.myPutup.getTickerCode() + " Item added. Graph size = " + this.graph.size() + ", Thread ID: " + Thread.currentThread().getId());
                                }
                                AskPriceResponse lastAsk = this.getLastAskResponse();
                                if (null != lastAsk && (lastAsk.getTimestamp() + SLEEP_TIME) < currTimestamp) {
                                    AskPriceResponse newItem = new AskPriceResponse(lastAsk, SLEEP_TIME);
                                    this.setLastAskResponse(newItem);
                                    this.graph.add(newItem);
                                    System.out.println("Manual ASK " + this.myPutup.getTickerCode() + " Item added. Graph size = " + this.graph.size() + ", Thread ID: " + Thread.currentThread().getId());
                                }
//                                RealTimeBarGraphPoint lastRTBar = this.getLast5SBarResponse();
//                                if (null != lastRTBar && (lastRTBar.getTimestamp() + SLEEP_TIME) < currTimestamp) {
//                                    RealTimeBarGraphPoint newItem = new RealTimeBarGraphPoint(lastRTBar, SLEEP_TIME);
//                                    this.setLast5SBarResponse(newItem);
//                                    this.graph.add(newItem);
//                                    System.out.println("Manual 5SBar " + this.myPutup.getTickerCode() + " Item added. Graph size = " + this.graph.size() + ", Thread ID: " + Thread.currentThread().getId());
//                                }
                            }
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        Thread.yield();
                    }
                }
                System.out.println("While has exited");
            } catch (Exception ex) {
                System.err.println(ex.getMessage());
                ex.printStackTrace();
            } finally {
                //If still connected cancel requests for real time data and disconnect
                if (this.isConnected()) {
                    this.disconnect();
                }
            }
        }
        return this.graph;
    }

    public boolean isConnected() {
        boolean result = false;
        if (null != this.m_client) {
            result = this.m_client.isConnected();
        }
        return result;
    }

    public void connect() throws IOException {
        if (!this.isConnected()) {
            this.m_client.eConnect("", DTConstants.CONNECTION_PORT, this.lastClientId);
            if (!m_client.isConnected()) {
                throw new IOException("Could not connect to stock broker");
            }
        }
    }

    public void disconnect() {
        this.m_client.cancelMktData(this.lastClientId);
        this.m_client.cancelRealTimeBars(this.reqRealTimeBarsId);
        this.shouldDisconnect = true;
        m_client.eDisconnect();
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        //NB this method does not deliver the time it is delivered to the tickString() method
        //BEFORE this data is delivered. I cache it into this.lastCurrTimestamp
        if (this.validTimestamp.get()) {
            String strField = TickType.getField(field);
            switch (field) {
                case 1:
                    //This is a bid price
                    BidPriceResponse bidItem = new BidPriceResponse(this.lastCurrTimestamp.get(), price);
                    this.setLastBidResponse(bidItem);
                    this.graph.add(bidItem);
                    System.out.println("Stock (BID): " + this.myPutup.getTickerCode() + " now has " + this.graph.size() + " entries." + ", Thread ID: " + Thread.currentThread().getId());
                    break;
                case 2:
                    //This is an ask price
                    AskPriceResponse askItem = new AskPriceResponse(this.lastCurrTimestamp.get(), price);
                    this.setLastAskResponse(askItem);
                    this.graph.add(askItem);
                    System.out.println("Stock (ASK): " + this.myPutup.getTickerCode() + " now has " + this.graph.size() + " entries." + ", Thread ID: " + Thread.currentThread().getId());
                    break;
                default:
                    System.out.println("tickPrice, Stock: " + this.myPutup.getTickerCode() + ", Type = " + "(" + field + ") " + strField + ", Price = " + price);
            }
        } else {
            System.out.println("Stock: "+ this.myPutup.getTickerCode() +" Invalid time: " + this.lastCurrTimestamp.get());
        }
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        String strField = TickType.getField(field);
        //System.out.println("tickSize, Stock: " + this.myPutup.getTickerCode() + ", Type = " + strField + ", Size = " + size);
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        String strField = TickType.getField(tickType);
        //System.out.println("tickGeneric, Stock: " + this.myPutup.getTickerCode() + ", Type = " + strField + ", Value = " + value);
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        String strField = TickType.getField(tickType);
        //System.out.println("tickString, Stock: " + this.myPutup.getTickerCode() + ", Type = " + strField + ", Value = " + value);
        switch (tickType) {
            case 45:
                //This updates the timestamp to use when no timestamp is provided
                long newTime = Long.parseLong(value);
                newTime *= 1000;
                Date aDate = new Date(newTime);
                Date now = new Date();
                this.lastCurrTimestamp.set(aDate.getTime());
                if (!this.validTimestamp.get()) {
                    this.validTimestamp.set(true);
                }
                System.out.println("Timestamp = " + aDate.getTime() + ", Now = " + now.getTime());
                break;
            case 48:
                //RTvolume response
                RTVolumeResponse item = new RTVolumeResponse(tickerId, value);
                this.graph.add(item);
                this.updated.set(true);
                System.out.println("Stock: " + this.myPutup.getTickerCode() + " now has " + this.graph.size() + " entries." + ", Thread ID: " + Thread.currentThread().getId());
                break;
            default:
                System.out.println("Tick String Type = " + strField);
        }
    }

    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void openOrderEnd() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateAccountTime(String timeStamp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountDownloadEnd(String accountName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void nextValidId(int orderId) {
        this.intNextOrderId = orderId;
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void contractDetailsEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void execDetailsEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void managedAccounts(String accountsList) {
        //This just prints the account list on connection (need to code something for this callback and I can see it working with this)
        m_bIsFAAccount = true;
        m_FAAcctCodes = accountsList;
        String msg = EWrapperMsgGenerator.managedAccounts(accountsList);
        //System.out.println(msg);
    }

    @Override
    public void receiveFA(int faDataType, String xml) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void scannerParameters(String xml) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void scannerDataEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        //Add the 5 second real time bar point to the graph
        long timestamp = time * 1000;
        RealTimeBarGraphPoint newPoint = new RealTimeBarGraphPoint(reqId, timestamp, open, high, low, close, volume, wap, count);
        this.setLast5SBarResponse(newPoint);
        this.graph.add(newPoint);
        System.out.println("Stock (RTB): " + this.myPutup.getTickerCode() + " now has " + this.graph.size() + " entries." + ", Thread ID: " + Thread.currentThread().getId());
    }

    @Override
    public void currentTime(long time) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fundamentalData(int reqId, String data) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(Exception e) {
        e.printStackTrace();
    }

    @Override
    public void error(String str) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        switch (errorCode) {
            case 502:
                System.err.println("Not Connected to stock broker. Stock ticker: " + this.myPutup.getTickerCode());
                break;
            case 300:
                System.err.println("Error No: " + errorCode + " Message = " + errorMsg);
                break;
            default:
                System.err.println("Error No: " + errorCode + " Message = " + errorMsg);
        }
    }

    @Override
    public void connectionClosed() {
        this.shouldDisconnect = true;
    }

    /**
     * @return the graph
     */
    public BaseGraph<AbstractGraphPoint> getGraph() {
        BaseGraph<AbstractGraphPoint> result = null;
        Putup putup = null;
        NavigableSet<AbstractGraphPoint> subSet = null;
        AbstractGraphPoint prevDayClose = null;
        graph.acquireObjectLock();
        try {
            putup = graph.getPutup();
            subSet = graph.subSet(graph.first(), true, graph.last(), true);
            prevDayClose = this.graph.getPrevDayClose();
        } catch (Exception ex) {
            result = null;
            putup = null;
            subSet = null;
            prevDayClose = null;
        } finally {
            graph.releaseObjectLock();
        }
        if (null != putup && null != subSet) {
            result = new BaseGraph<AbstractGraphPoint>(subSet);
            result.setPutup(putup);
            result.setPrevDayClose(prevDayClose);
        }
        return result;
    }
    
    /**
     * This accessor allows direct access to the graph object (rather than returning
     * a new graph object containing a shallow copy of graph points). The graph object
     * is thread safe however use getGraph where you can. You CANNOT use getGraph for a
     * callback from an historic update as that needs to update the actual graph please
     * use this method in that one instance.
     */
    public BaseGraph<AbstractGraphPoint> getRawGraph(){
        return this.graph;
    }

    /**
     * @return the lastBidResponse
     */
    public BidPriceResponse getLastBidResponse() {
        return lastBidResponse;
    }

    /**
     * @param lastBidResponse the lastBidResponse to set
     */
    public void setLastBidResponse(BidPriceResponse lastBidResponse) {
        if (null != lastBidResponse) {
            lock.lock();
            try {
                this.lastBidResponse = lastBidResponse;
            } finally {
                lock.unlock();
            }
        }
        this.lastBidResponse = lastBidResponse;
    }

    /**
     * @return the lastAskResponse
     */
    public AskPriceResponse getLastAskResponse() {
        return lastAskResponse;
    }

    /**
     * @param lastAskResponse the lastAskResponse to set
     */
    public void setLastAskResponse(AskPriceResponse lastAskResponse) {
        if (null != lastAskResponse) {
            lock.lock();
            try {
                this.lastAskResponse = lastAskResponse;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * @return the last5SBarResponse
     */
    public RealTimeBarGraphPoint getLast5SBarResponse() {
        return last5SBarResponse;
    }

    /**
     * @param last5SBarResponse the last5SBarResponse to set
     */
    public void setLast5SBarResponse(RealTimeBarGraphPoint last5SBarResponse) {
        if (null != last5SBarResponse) {
            lock.lock();
            try {
                this.last5SBarResponse = last5SBarResponse;
            } finally {
                lock.unlock();
            }
        }
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
    
}
