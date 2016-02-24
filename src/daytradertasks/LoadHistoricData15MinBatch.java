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
 * This class loads 15 min of historic data. It is used in the YLines
 * calculation to load the minimum number of points needed at the 1 sec
 * resolution.
 *
 * @author Roy
 */
public class LoadHistoricData15MinBatch extends AbstractHDTCallable {

    /**
     * Constructor that performs initialisation and accepts the minimum additional
     * data to make an historic data request (ie a putup (the market security the 
     * request is for) and a date / time representing the time the data is needed
     * up to)
     * @param newPutup - A Putup representing a stock market security
     * @param newEndTime - A Calendar representing the date / time at which the
     * returned data should finish
     */
    public LoadHistoricData15MinBatch(Putup newPutup, Calendar newEndTime) {
        super(newPutup, newEndTime);
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
    public LoadHistoricData15MinBatch(Putup newPutup, Calendar newEndTime, ICallback newCallback) {
        super(newPutup, newEndTime, newCallback);
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            //Make the connection
            int maxAttempts = 100;
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
                String batchTime = DTUtil.convertCalToBrokerTime(this.endDate);
                this.setLoadComplete(false);
                this.setAbort(false);
                this.strAbortMsg = "";
                this.m_client.reqHistoricalData(this.objContract.m_conId,
                        objContract,
                        batchTime,
                        DTDurationEnum.S900.toString(),
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
                long timeOut = System.currentTimeMillis() + (60 * 60 * 1000);        //Timeout after 1 Hour
                this.setAbortTime(timeOut);
                //Wait for data to be delivered
                synchronized (this.monitor) {
                    do {
                        long waitFor = timeOut - System.currentTimeMillis();
                        if (waitFor <= 0) {
                            waitFor = 1;
                        }
                        if(!this.isLoadComplete()){
                            this.monitor.wait(waitFor);
                        }
                        if (this.isPassedAbortTime() && !this.isLoadComplete()) {
                            this.strAbortMsg = "Timed out waiting for stockbroker server LoadHistoricData15MinBatch";
                            this.setAbort(true);
                            this.disconnect();
                            throw new TWSConnectionException(this.strAbortMsg);
                        }
                    } while (!this.isLoadComplete() && !isAbort());
                }
//                //Wait for data to be delivered
//                while (!this.isLoadComplete() && !isAbort()) {
//                    if (System.currentTimeMillis() < timeOut) {
//                        //Thread.yield();
//                        Thread.sleep(500);
//                    } else {
//                        this.disconnect();
//                        throw new TWSConnectionException("Timed out waiting for stockbroker server LoadHistoricData15MinBatch");
//                    }
//                }
                //Ensure we have not had to abort because of an error
                if (this.isAbort()) {
                    this.disconnect();
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
                //Re-submit yourself to the Historic data engine to try another account if availiable (will queue you for this account if only one avaliable)
                if (this.incrementAndGetResubmitAttempts() < 10) {
                    HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
                    HRSCallableWrapper wrapper = new HRSCallableWrapper(this);
                    HRSys.submitRequest(wrapper);
                } else {
                    throw new TWSConnectionException("No connection to stock broker is availiable");
                }
            }
        } catch (Exception ex) {
            //An exception has occured. Pass the exception back to the execution system inside a result object so it can do the callback with the error
            finalResult = new LoadHistoricDataPointBatchResult(ex, cbDelegate, this.cbList);
        }
        return finalResult;
    }
}
