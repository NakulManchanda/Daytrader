/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BarSizeSettingEnum;
import daytrader.datamodel.CallbackType;
import daytrader.datamodel.DTDurationEnum;
import daytrader.datamodel.Putup;
import daytrader.datamodel.StockExchangeHours;
import daytrader.datamodel.WhatToShowEnum;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.historicRequestSystem.AbstractHDTCallable;
import daytrader.interfaces.ICallback;
import daytrader.utils.DTUtil;
import java.io.IOException;
import java.util.Calendar;
import java.util.Random;
import java.util.TreeSet;

/**
 * This class makes a single historical data request asking for each days 1 day
 * historic bar between the endDate to the backTo date
 *
 * @author Roy
 */
public class Load1DayBarsHighPoints extends AbstractHDTCallable {

    private Calendar backToDate;

    /**
     * This class loads a price / time point for the highest price the security reached 
     * on the days being examined. Step 1 of Y-Line loading operations
     * @param newPutup - The putup containing the details for the market security 
     * @param endDate - The data / time that the data should end at
     * @param backTo - A data / time defining where the data should start from
     * @param newCallback - The callback to be made with the loaded data
     */
    public Load1DayBarsHighPoints(Putup newPutup, Calendar endDate, Calendar backTo, ICallback newCallback) {
        super(newPutup, endDate, newCallback);
        this.putup = newPutup;
        this.backToDate = backTo;
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            //Make the connection
            int maxAttempts = 100;
            int currAttempts = 0;
            Random randGen = new Random();
            while (!this.m_client.isConnected() && currAttempts < maxAttempts) {
                currAttempts++;
                try {
                    this.connect();
                } catch (Exception ex) {
                    System.err.println("Connect attempt failed no " + currAttempts + " : Date = " + this.endDate.getTime().toString() + ", Port number: " + this.executingAccount.getPortNo());
                    //Sleep for a random number of millisecounds between 1000 and 2000 to allow other connections to close before retrying
                    Double rand = randGen.nextDouble();
                    rand += 1;
                    rand *= 1000;
                    Thread.sleep(rand.longValue());
                }
            }
            if (this.isConnected()) {
                int intRTH = 1;
                int intDateFormat = 1;
                //Make the request
                StockExchangeHours smHrs = new StockExchangeHours(endDate.getTime());
                Calendar gmtEndCal = smHrs.getEndCalendarInGMT();
                //YLines Pre-Load starts from 'yesterday' move back 1 day from current day (endDate is today)
                gmtEndCal.add(Calendar.DAY_OF_MONTH, -1);
                String currBatchTime = DTUtil.convertCalToBrokerTime(gmtEndCal);
                DTDurationEnum durationToUse = DTDurationEnum.M3;
                if (null != this.backToDate) {
                    durationToUse = DTDurationEnum.getDurationToCover(endDate, this.backToDate);
                }
                this.setLoadComplete(false);
                this.setAbort(false);
                this.strAbortMsg = "";
                this.m_client.reqHistoricalData(this.objContract.m_conId,
                        objContract,
                        currBatchTime,
                        durationToUse.toString(),
                        BarSizeSettingEnum.DAY1.toString(),
                        WhatToShowEnum.TRADES.toString(),
                        intRTH,
                        intDateFormat);
                //To avoid pacing violations the thread MUST now sleep (BLOCK) for 2 secs
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    this.disconnect();
                    throw new InterruptedException("Thread interrupted while transmitting data request");
                }
                //Set timeout for waiting operation
                long timeOut = System.currentTimeMillis() + (30 * 1000);        //Timeout after 30 seconds
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
                            this.strAbortMsg = "Timed out waiting for stockbroker server Load1DayBarsHighPoints";
                            this.setAbort(true);
                            this.disconnect();
                            throw new TWSConnectionException(this.strAbortMsg);
                        }
                    } while (!this.isLoadComplete() && !isAbort());
                }
//                while (!this.isLoadComplete() && !isAbort()) {
//                    if (System.currentTimeMillis() < timeOut) {
//                        //Thread.yield();
//                        Thread.sleep(500);
//                    } else {
//                        this.disconnect();
//                        throw new TWSConnectionException("Timed out waiting for stockbroker server Load1DayBarsHighPoints");
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
                //this.filterData();                                            //Do not filter for 1 day this is multiple days
                //Now build the final result
                finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList, CallbackType.YLINES1DAYBARS);
            } else {
                this.disconnect();
                //Re-submit yourself to the Historic data engine to try another account if availiable (will queue you for this account if only one
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
