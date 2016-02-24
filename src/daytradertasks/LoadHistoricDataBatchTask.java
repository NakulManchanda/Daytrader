/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BarSizeSettingEnum;
import daytrader.datamodel.DTDurationEnum;
import daytrader.datamodel.Putup;
import daytrader.datamodel.WhatToShowEnum;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.historicRequestSystem.AbstractHDTCallable;
import daytrader.interfaces.ICallback;
import daytrader.utils.DTUtil;
import java.io.IOException;
import java.util.Calendar;
import java.util.TreeSet;

/**
 * This task will load a 30 min batch of data using the historic request
 * queueing system. This is the most basic and useful request for historic
 * data that can be made
 *
 * @author Roy
 */
public class LoadHistoricDataBatchTask extends AbstractHDTCallable {

    private String batchTime;

    /**
     * Constructor that performs initialisation and accepts the minimum additional
     * data to make an historic data request (ie a putup (the market security the 
     * request is for) and a date / time representing the time the data is needed
     * up to)
     * @param newPutup - A Putup representing a stock market security
     * @param newEndTime - A Calendar representing the date / time at which the
     * returned data should finish
     */
    public LoadHistoricDataBatchTask(Putup newPutup, Calendar newEndTime) {
        super(newPutup, newEndTime);
        this.batchTime = DTUtil.convertCalToBrokerTime(this.endDate);
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
    public LoadHistoricDataBatchTask(Putup newPutup, Calendar newEndTime, ICallback newCallback) {
        super(newPutup, newEndTime, newCallback);
        this.batchTime = DTUtil.convertCalToBrokerTime(this.endDate);
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            //Make the connection
            int maxAttempts = 10;
            int currAttempts = 0;
            while (!this.m_client.isConnected() && currAttempts < maxAttempts) {
                currAttempts++;
                try {
                    this.connect();
                } catch (Exception ex) {
                    System.err.println("Connect attempt failed no " + currAttempts + " : Date = " + this.endDate.getTime().toString() + ", Port number: " + this.executingAccount.getPortNo());
                }
            }
            if (this.isConnected()) {
                //String batchTime = DTUtil.convertCalToBrokerTime(this.endDate);
                System.err.println("Submitted batch for time: " + batchTime);
                this.setLoadComplete(false);
                this.setAbort(false);
                this.strAbortMsg = "";
                this.m_client.reqHistoricalData(this.objContract.m_conId,
                        objContract,
                        this.batchTime,
                        DTDurationEnum.S1800.toString(),
                        BarSizeSettingEnum.SEC1.toString(),
                        WhatToShowEnum.TRADES.toString(),
                        0,
                        1);

                //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    this.disconnect();
                    throw new InterruptedException("Thread interrupted while transmitting data request");
                }
                //Set timeout for waiting operation
                long timeOut = System.currentTimeMillis() + (5 * 60 * 1000);        //Timeout after 5 min
                this.setAbortTime(timeOut);
                synchronized (this.monitor) {
                    do {
                        long waitFor = timeOut - System.currentTimeMillis();
                        if (waitFor <= 0) {
                            waitFor = 1;
                        }
                        if (!this.isLoadComplete()) {
                            this.monitor.wait(waitFor);
                        }
                        if (this.isPassedAbortTime() && !this.isLoadComplete()) {
                            this.strAbortMsg = "Timed out waiting for stockbroker server LoadHistoricDataBatchTask";
                            this.setAbort(true);
                            this.disconnect();
                            throw new TWSConnectionException(this.strAbortMsg);
                        }
                    } while (!this.isLoadComplete() && !isAbort());
                }
//                //Wait for data to be delivered
//                while (!this.isLoadComplete() && !isAbort()) {
//                    if (System.currentTimeMillis() < timeOut) {
//                        Thread.yield();
//                        //Thread.sleep(500);
//                    } else {
//                        this.disconnect();
//                        throw new TWSConnectionException("Timed out waiting for stockbroker server LoadHistoricDataBatchTask");
//                    }
//                }
                //Ensure we have not had to abort because of an error
                if (this.isAbort()) {
                    this.disconnect();
                    System.err.println("Aborted batch: " + DTUtil.convertCalToBrokerTime(this.endDate) + " Message: " + this.strAbortMsg);
                    throw new IOException(this.strAbortMsg);
                }
                //If we reach this point all data was loaded and the loadedPoints will be returned
                this.disconnect();
                //However we can sometimes get data Bryn does not want (both before market opening and after market close)
                //Bryn wants this data removed
                this.filterData();
                //Now build the final result
                finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList);
            } else {
                this.disconnect();
                System.err.println("Re-Submitting batch: " + DTUtil.convertCalToBrokerTime(this.endDate));
                //Re-submit yourself to the Historic data engine to try another account if availiable (will queue you for this account if only one avaliable)
                if (this.incrementAndGetResubmitAttempts() < 10) {
                    HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
                    HRSCallableWrapper wrapper = new HRSCallableWrapper(this);
                    HRSys.submitRequest(wrapper);
                } else {
                    System.err.println("Timeout for batch: " + DTUtil.convertCalToBrokerTime(this.endDate));
                    throw new TWSConnectionException("No connection to stock broker is availiable");
                }
            }
        } catch (Exception ex) {
            //An exception has occured. Pass the exception back to the execution system inside a result object so it can do the callback with the error
            System.err.println("Exception loading batch: " + DTUtil.convertCalToBrokerTime(this.endDate));
            ex.printStackTrace();
            finalResult = new LoadHistoricDataPointBatchResult(ex, cbDelegate, this.cbList);
        }
        return finalResult;
    }
}
