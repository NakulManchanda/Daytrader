/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractDataRequest;
import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.RTVolumeResponse;
import daytrader.historicRequestSystem.TWSAccount;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.utils.DTUtil;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class starts the requests for market data. (REAL TIME DATA)
 * 
 * When a security moves into the price range(s) where an entry might be considered
 * it needs to request real time second by second market data. This class makes that request
 *
 * @author Roy
 */
public class RequestMarketDataTask extends AbstractDataRequest {

    private static final boolean DEBUG = true;
    private AtomicBoolean updated;

    /**
     * Constructor
     * When a security moves into the price range(s) where an entry might be considered
     * it needs to request real time second by second market data. This class makes that request
     * @param newGraph - The BaseGraph used to store the real time data as it arrives
     * @param newConnectionAcc - A TWSAccount used to communicate with the data server
     */
    public RequestMarketDataTask(BaseGraph<AbstractGraphPoint> newGraph, TWSAccount newConnectionAcc) {
        this.myPutup = newGraph.getPutup();
        this.graph = newGraph;
        this.connectionAcc = newConnectionAcc;
        this.updated = new AtomicBoolean(false);
        this.initialise();
    }

    @Override
    public void disconnect() {
        this.connectionAcc.getConnectionSocket().cancelMktData(this.lastClientId);
        super.disconnect();
    }

    @Override
    public Void call() throws Exception {
        try {
            //Make the connection
//            int maxAttempts = 100;
//            int currAttempts = 0;
//            while (!this.m_client.isConnected() && currAttempts < maxAttempts) {
//                currAttempts++;
//                try {
//                    this.connect();
//                } catch (Exception ex) {
//                }
//            }
            //I do not like it but the stockbrokers server does not alway connect on first attempt, retry until it does.
            long connectFailTime = System.currentTimeMillis() + (5*60*1000);    //Attemp to connect for 5 min tops
            while (!this.isConnected() && System.currentTimeMillis() < connectFailTime) {
                try {
                    this.connect();
                } catch (Exception ex) {
                    Thread.sleep(SLEEP_TIME);
                }
            }
            if (this.isConnected()) {
                //Record end of trading day so I know when to shutdown
                Calendar exchClosingCalendar = DTUtil.getExchClosingCalendar(this.myPutup.getTodaysDate());
                long shutdownAt = exchClosingCalendar.getTimeInMillis();
                //Make the request for market data
                String tickType = "233";
                this.connectionAcc.getConnectionSocket().reqMktData(lastClientId, objContract, tickType, false);
                this.running.set(true);
                long oldTimestamp = System.currentTimeMillis();
                long currTimestamp;
                //Begin a loop that will last as long as we have an open connection to the data server
                while (!shouldDisconnect) {
                    currTimestamp = System.currentTimeMillis();
                    if (currTimestamp >= (oldTimestamp + SLEEP_TIME)) {
                        oldTimestamp = currTimestamp;
                        //If no update has been received then I must add a point at same price as last item
                        //If update HAS been received then reset update flag and yield the time slice
                        if (!this.updated.get()) {
                            //Lock the graph
                            this.graph.acquireObjectLock();
                            try {
                                if (null != this.graph && 0 < this.graph.size()) {
                                    //Add a point
                                    AbstractGraphPoint last = this.graph.last();
                                    if (null != last) {
                                        RTVolumeResponse item = new RTVolumeResponse(last, SLEEP_TIME);
                                        this.graph.add(item);
                                        if (DEBUG) {
                                            System.out.println("Manual " + this.myPutup.getTickerCode() + " Item added. Graph size = " + this.graph.size() + ", Thread ID: " + Thread.currentThread().getId());
                                        }
                                    }
                                }
                            } finally {
                                this.graph.releaseObjectLock();
                            }
                        } else {
                            //Reset the flag
                            this.updated.set(false);
                        }
                    }
                    //Standard code to shutdown at end of day or on error
                    if (System.currentTimeMillis() > shutdownAt) {
                        this.disconnect();
                    }
                    if (null != this.lastException) {
                        this.shouldDisconnect = true;
                        this.running.set(false);
                        this.disconnect();
                        throw this.lastException;
                    }
                    //Thread.yield();
                    Thread.sleep(SLEEP_TIME);
                }
                this.running.set(false);
            } else {
                throw new TWSConnectionException("Failed to connect to stock broker on startup Request Market Data: " + this.myPutup.getTickerCode());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            this.lastException = ex;
            if (this.isConnected()) {
                this.disconnect();
            }
        }
        return null;
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        //Data is received to this call back but we are only insterested in RTVolumeResponses
        //Method overriden to discard these responses
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        //Data is received to this call back but we are only insterested in RTVolumeResponses
        //Method overriden to discard these responses
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        //Data is received to this call back but we are only insterested in RTVolumeResponses
        //Method overriden to discard these responses
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        switch (tickType) {
            case 45:
                System.err.println("tickString - Time - Ignored");
                break;
            case 48:
                //RTvolume response
                RTVolumeResponse item = new RTVolumeResponse(tickerId, value);
                this.graph.add(item);
                this.updated.set(true);
                if (DEBUG) {
                    System.out.println("(reqMktData) Stock: " + this.myPutup.getTickerCode() + " now has " + this.graph.size() + " entries." + ", Thread ID: " + Thread.currentThread().getId());
                }
                break;
        }
    }
}
