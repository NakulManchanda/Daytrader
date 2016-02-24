/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;
import daytrader.datamodel.AbstractDataRequest;
import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.RealTimeBarGraphPoint;
import daytrader.historicRequestSystem.TWSAccount;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.utils.DTUtil;
import java.util.Calendar;

/**
 * This class starts a real time data run for the given put up by requesting
 * real time five second bars. All running putups should ALWAYS have an active
 * real time data request of this type.
 *
 * @author Roy
 */
public class RealTimeFiveSecBarRequest extends AbstractDataRequest {

    /**
     * Constructor defining the BaseGraph that should receive the 5 sec data bars and a TWSAccount
     * to be used when communicating with the server.
     * @param newGraph - The BaseGraph used to store the real time data as it arrives
     * @param newConnectionAcc - A TWSAccount used to communicate with the data server
     */
    public RealTimeFiveSecBarRequest(BaseGraph<AbstractGraphPoint> newGraph, TWSAccount newConnectionAcc) {
        this.myPutup = newGraph.getPutup();
        this.graph = newGraph;
        this.connectionAcc = newConnectionAcc;
        this.initialise();
    }

    @Override
    public void disconnect() {
        this.connectionAcc.getConnectionSocket().cancelRealTimeBars(this.lastClientId);
        this.shouldDisconnect = true;
        //Socket Disconnect to be managed by TWS Account
        //m_client.eDisconnect();
    }

    @Override
    public Void call() throws Exception {
        System.out.println("Starting real time bars for putup: " + this.myPutup.getTickerCode());
        this.running.set(true);
        try {
            //Make the connection
            //I do not like it but the stockbrokers server does not alway connect on first attempt, retry until it does.
            long connectFailTime = System.currentTimeMillis() + (5 * 60 * 1000);    //Attempt to connect for 5 min tops
            while (!this.isConnected() && System.currentTimeMillis() < connectFailTime) {
                try {
                    this.connect();
                } catch (Exception ex) {
                    Thread.sleep(SLEEP_TIME);
                }
            }
            if (this.isConnected()) {
                System.out.println("CONNECTED to stock broker on startup Real Time Bars: " + this.myPutup.getTickerCode() + " : Object = " + this.toString());
                //Record end of trading day so I know when to shutdown
                Calendar exchClosingCalendar = DTUtil.getExchClosingCalendar(this.myPutup.getTodaysDate());
                long shutdownAt = exchClosingCalendar.getTimeInMillis();
                //Make request for real time data (5sec bars) Parameters are:
                //1 - An ID to identify the request
                //2 - A Contract to use
                //3 - ONLY 5 SEC BARS supported by API MUST be the value 5
                //4 - Type of data requested, Bryn ALWAYS wants "TRADES"
                //5 - Boolean true means only return data from within market trading hours
                this.connectionAcc.getConnectionSocket().reqRealTimeBars(this.lastClientId, objContract, 5, "TRADES", true);
                while (!this.shouldDisconnect) {
                    if (System.currentTimeMillis() > shutdownAt) {
                        this.disconnect();
                    }
                    if (null != this.lastException) {
                        this.shouldDisconnect = true;
                        this.running.set(false);
                        this.disconnect();
                        throw this.lastException;
                    }
                    Thread.sleep(SLEEP_TIME);
                }
                this.running.set(false);
            } else {
                this.running.set(false);
                throw new TWSConnectionException("Failed to connect to stock broker on startup Real Time Bars: " + this.myPutup.getTickerCode());
            }
        } catch (Exception ex) {
            this.running.set(false);
            ex.printStackTrace();
            this.lastException = ex;
            if (this.isConnected()) {
                this.disconnect();
            }
        }
        return null;
    }

    //EWrapper - Stockbrokers API - START
    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        //Ignored we do not need the next valid order id
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
        //A call back always comes to this method from the API but we do not need to use / keep the account list so it is ignored
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        this.graph.add(newPoint);
        if (DEBUG) {
            System.out.println("Stock (RTB): " + this.myPutup.getTickerCode() + " now has " + this.graph.size() + " entries." + ", Thread ID: " + Thread.currentThread().getId());
        }
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
    public void error(String str) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        switch (errorCode) {
            case 502:
                //For some reason the stock broker API spams this error code with the error message:
                //Confirm that "Enable ActiveX and Socket Clients" is enabled on the TWS "Configure->API" menu.
                //As this will be pre-configured I am ignoring this message. Any other message will throw an exception
                if (!errorMsg.contains("Enable ActiveX and Socket Clients")) {
                    TWSConnectionException newEx =
                            new TWSConnectionException("Not Connected to stock broker. Stock ticker: "
                            + this.myPutup.getTickerCode()
                            + ", Server Message = "
                            + errorMsg);
                    this.lastException = newEx;
                }
                break;
            case 300:
                System.err.println("Error No: " + errorCode + " Message = " + errorMsg);
                break;
            default:
                System.err.println("Error No: " + errorCode + " Message = " + errorMsg);
        }
    }
}
