/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

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
import daytrader.datamodel.BarSizeSettingEnum;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.DTDurationEnum;
import daytrader.datamodel.HistoricDataGraphPoint;
import daytrader.datamodel.Putup;
import daytrader.datamodel.WhatToShowEnum;
import daytrader.utils.DTUtil;
import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * DEPRICATED - USE LoadHistoricDataBatchTask INSTEAD AND SUBMIT TO HISTORICAL DATA QUEUE SYSTEM
 * This class loads a 30 minute batch of historic data points up to the given
 * time.
 *
 * @author Roy
 */
public class LoadHistoricDataPointBatchTaskDEPRICATED implements Callable<LoadHistoricDataPointBatchResult>, EWrapper {

    private Putup putup;
    private Calendar endDate;
    private Contract objContract;
    private EClientSocket m_client;
    private int port;
    private volatile boolean loadComplete;
    private volatile boolean abort;
    private String strAbortMsg;
    //This attribute will hold the final loadedPoints.
    private TreeSet<AbstractGraphPoint> loadedPoints;
    
    //These are needed by API and are set with data but are not used in this task
    private boolean m_bIsFAAccount;
    private String m_FAAcctCodes;

    public LoadHistoricDataPointBatchTaskDEPRICATED(Putup newPutup, Calendar newEndTime) {
        this.putup = newPutup;
        this.endDate = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        this.endDate.setTimeInMillis(newEndTime.getTimeInMillis());

        //Create a standard contract to use when issuing the API call
        this.objContract = new Contract();
        objContract.m_conId = DTConstants.getConId();
        objContract.m_symbol = this.putup.getTickerCode();
        objContract.m_secType = "STK";
        objContract.m_exchange = "SMART";
        objContract.m_currency = "USD";
        objContract.m_primaryExch = this.putup.getMarket().toString();

        //Create socket connection
        this.m_client = new EClientSocket(this);
        //Set connection port
        this.port = DTConstants.CONNECTION_PORT;
    }
    
     public LoadHistoricDataPointBatchTaskDEPRICATED(Putup newPutup, Calendar newEndTime, int portNo) {
         this(newPutup, newEndTime);
         this.port = portNo;
     }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        //Make the connection
        int maxAttempts = 10;
        int currAttempts = 0;
        while(!this.m_client.isConnected() && currAttempts < maxAttempts){
            currAttempts++;
            try{
            this.connect();
            } catch(Exception ex){
                System.err.println("Connect attempt failed no " + currAttempts + " : Date = " + this.endDate.getTime().toString() + ", Port number: " + this.port);
            }
        }
        if (this.isConnected()) {
            String batchTime = DTUtil.convertCalToBrokerTime(this.endDate);
            this.m_client.reqHistoricalData(this.objContract.m_conId,
                    objContract,
                    batchTime,
                    DTDurationEnum.S1800.toString(),
                    BarSizeSettingEnum.SEC1.toString(),
                    WhatToShowEnum.TRADES.toString(),
                    0,
                    1);
            this.loadComplete = false;
            this.abort = false;
            this.strAbortMsg = "";
            //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                this.disconnect();
                throw new IOException("Thread interrupted while transmitting data request");
            }
            //Set timeout for waiting operation
            long timeOut = System.currentTimeMillis() + (30 * 1000);        //Timeout after 30 seconds
            //Wait for data to be delivered
            while (!this.loadComplete && !abort) {
                if (System.currentTimeMillis() < timeOut) {
                    Thread.yield();
                } else {
                    this.disconnect();
                    throw new IOException("Timed out waiting for stockbroker server LoadHistoricDataPointBatchTaskDEPRICATED");
                }
            }
            //Ensure we have not had to abort because of an error
            if (this.abort) {
                this.disconnect();
                throw new IOException(this.strAbortMsg);
            }
            //If we reach this point all data was loaded and the loadedPoints will be returned
            this.disconnect();
            //However we can sometimes get data Bryn does not want (both before market opening and after market close)
            //Bryn wants this data removed
            this.filterData();
            //Now build the final result
            finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints);
        } else {
            this.disconnect();
            throw new IOException("No connection to stock broker is availiable");
        }
        return finalResult;
    }

    public void connect() throws IOException {
        if (!this.isConnected()) {
            int clientId = this.objContract.m_conId;
            this.m_client.eConnect("", this.port, clientId);
            if (!m_client.isConnected()) {
                throw new IOException("Could not connect to stock broker");
            }
        }
    }
    
    public void disconnect() {
        m_client.eDisconnect();
    }

    public boolean isConnected() {
        boolean result = false;
        if (null != this.m_client) {
            result = this.m_client.isConnected();
        }
        return result;
    }
    
    private void filterData() {
        if(null != this.loadedPoints && 0 < this.loadedPoints.size()){
            TreeSet<AbstractGraphPoint> tempData = new TreeSet<AbstractGraphPoint>();
            Calendar exchOpen = DTUtil.getExchOpeningCalendar(this.endDate);
            long lngExchOpen = exchOpen.getTimeInMillis();
            Calendar exchClose = DTUtil.getExchClosingCalendar(this.endDate);
            long lngExchClose = exchClose.getTimeInMillis();
            for(AbstractGraphPoint currPoint : this.loadedPoints){
                long timestamp = currPoint.getTimestamp();
                if(lngExchOpen <= timestamp && lngExchClose >= timestamp){
                    tempData.add(currPoint);
                }
            }
            //Now store filtered data as final result
            this.loadedPoints = tempData;
        }
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void openOrderEnd() {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateAccountTime(String timeStamp) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountDownloadEnd(String accountName) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void nextValidId(int orderId) {
        //The next valid order ID is irrelevent for this task. ignore this callback.
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void contractDetailsEnd(int reqId) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void execDetailsEnd(int reqId) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void managedAccounts(String accountsList) {
        //This just stores the account list on connection (need to code something for this callback and I can see it working with this)
        this.m_bIsFAAccount = true;
        this.m_FAAcctCodes = accountsList;
    }

    @Override
    public void receiveFA(int faDataType, String xml) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        if (date.contains("finished")) {
            //All data has arrived
            this.loadComplete = true;
        } else {
            //This is a new point to add to the results
            HistoricDataGraphPoint newItem = new HistoricDataGraphPoint(reqId, date, open, high, low, close, volume, count, WAP, hasGaps);
            this.loadedPoints.add(newItem);
        }
    }

    @Override
    public void scannerParameters(String xml) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void scannerDataEnd(int reqId) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void currentTime(long time) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void fundamentalData(int reqId, String data) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(Exception e) {
        e.printStackTrace();
        System.err.println("Error invoked: " + e.getMessage());
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(String str) {
        this.disconnect();
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        switch (errorCode) {
            case 162:
                this.abort = true;
                this.strAbortMsg = "Historical Service Error: " + errorMsg;
                break;
            case 502:
                this.abort = true;
                this.strAbortMsg = "Not Connected to stock broker: " + errorMsg;
                break;
            case 2104:
                //This indicates that you have connected not sure why thats an error but ignore it
                break;
            case 2106:
                //This indicates that you have connected not sure why thats an error but ignore it
                break;
            default:
                this.abort = true;
                this.strAbortMsg = errorMsg;
                System.err.println("ID = "+ id +", Error no: " + errorCode + ", Message = " + errorMsg);
                this.disconnect();
        }
    }

    @Override
    public void connectionClosed() {
        this.abort = true;
        this.strAbortMsg = "Connection to stock brokers server was closed";
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
