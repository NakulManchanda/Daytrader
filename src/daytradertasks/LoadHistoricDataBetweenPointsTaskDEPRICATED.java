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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TreeSet;
import java.util.concurrent.Callable;

/**
 * DEPRACATED MOVING OVER TO HISTORICAL REQUEST QUEUE SYSTEM - REMOVE WHEN DATAGRAPHLOADER IS DEPRICATED
 * @author Roy
 */
public class LoadHistoricDataBetweenPointsTaskDEPRICATED implements Callable<LoadHistoricDataPointBatchResult>, EWrapper {

    private AbstractGraphPoint startPoint;
    private AbstractGraphPoint endPoint;
    private Putup putup;
    private Contract objContract;
    private EClientSocket m_client;
    private boolean loadComplete;
    private boolean abort;
    private String strAbortMsg;
    private ArrayList<String> batches;
    //This attribute will hold the final loadedPoints.
    private TreeSet<AbstractGraphPoint> loadedPoints;
    //Attribute to count completed jobs
    private int intJobs;
    private static final long MSPERBATCH = 30 * 60 * 1000;                          //Each batch is 30 min
    //These are needed by API and are set with data but are not used in this task
    private boolean m_bIsFAAccount;
    private String m_FAAcctCodes;

    public LoadHistoricDataBetweenPointsTaskDEPRICATED(AbstractGraphPoint newStart, AbstractGraphPoint newEnd, Putup newPutup) {
        this.startPoint = newStart;
        this.endPoint = newEnd;
        this.putup = newPutup;

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

        this.generateBatches();
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        //Make the connection
        this.connect();
        if (this.isConnected()) {
            //Variables for timeout (allows 5 sec per a request with 2 secs of sleep between requests so 7 secs total)
            long startedRequestsAt = System.currentTimeMillis();
            long timeOutAt = startedRequestsAt + (7000 * this.batches.size());
            this.loadComplete = false;
            this.abort = false;
            this.strAbortMsg = "";
            this.intJobs = 0;
            for (String currBatchTime : this.batches) {
                if (this.isConnected()) {
                    this.intJobs++;
                    this.m_client.reqHistoricalData(this.objContract.m_conId,
                            objContract,
                            currBatchTime,
                            DTDurationEnum.S1800.toString(),
                            BarSizeSettingEnum.SEC1.toString(),
                            WhatToShowEnum.TRADES.toString(),
                            0,
                            1);
                    //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ex) {
                        throw new IOException("Thread interrupted while transmitting data request");
                    }
                } else {
                    throw new IOException("Connection to stock broker lost");
                }
            }
            //Wait for data to be delivered
            while (!this.loadComplete && !abort) {
                if (System.currentTimeMillis() < timeOutAt) {
                    Thread.yield();
                } else {
                    throw new IOException("Timed out waiting for stockbroker server LoadHistoricDataBetweenPointsTaskDEPRICATED");
                }
            }
            //Ensure we have not had to abort because of an error
            if (this.abort) {
                throw new IOException(this.strAbortMsg);
            }
            //If we reach this point all data was loaded and the loadedPoints will be returned
            //However we can sometimes get data Bryn does not want (both before market opening and after market close)
            //Bryn wants this data removed
            this.filterData();
            //Now build the final result
            finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints);
        } else {
            throw new IOException("No connection to stock broker is availiable");
        }
        return finalResult;
    }

    public void connect() throws IOException {
        if (!this.isConnected()) {
            int clientId = this.objContract.m_conId;
            this.m_client.eConnect("", DTConstants.CONNECTION_PORT, clientId);
            if (!m_client.isConnected()) {
                throw new IOException("Could not connect to stock broker");
            }
        }
    }

    public boolean isConnected() {
        boolean result = false;
        if (null != this.m_client) {
            result = this.m_client.isConnected();
        }
        return result;
    }

    private void filterData() {
        TreeSet<AbstractGraphPoint> tempData = new TreeSet<AbstractGraphPoint>();
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
        this.loadedPoints = tempData;
    }

    private void generateBatches() {
        this.batches = new ArrayList<String>();
        long currTime = this.endPoint.getTimestamp() + LoadHistoricDataBetweenPointsTaskDEPRICATED.MSPERBATCH;
        long startTime = this.startPoint.getTimestamp();
        boolean abort = false;
        while (!abort) {
            currTime -= LoadHistoricDataBetweenPointsTaskDEPRICATED.MSPERBATCH;
            Calendar cal = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
            cal.setTimeInMillis(currTime);
            String batchTime = DTUtil.convertCalToBrokerTime(cal);
            this.batches.add(batchTime);
            if (currTime - LoadHistoricDataBetweenPointsTaskDEPRICATED.MSPERBATCH <= startTime) {
                abort = true;
            }
        }
    }

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
        //The next valid order ID is irrelevent for this task. ignore this callback.
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        //This just stores the account list on connection (need to code something for this callback and I can see it working with this)
        this.m_bIsFAAccount = true;
        this.m_FAAcctCodes = accountsList;
    }

    @Override
    public void receiveFA(int faDataType, String xml) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        if (date.contains("finished")) {
            this.intJobs--;
            if(0 == this.intJobs){
                //All data has arrived
                this.loadComplete = true;
            }
        } else {
            //This is a new point to add to the results
            HistoricDataGraphPoint newItem = new HistoricDataGraphPoint(reqId, date, open, high, low, close, volume, count, WAP, hasGaps);
            this.loadedPoints.add(newItem);
        }
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        System.err.println("Error invoked: " + e.getMessage());
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void error(String str) {
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
