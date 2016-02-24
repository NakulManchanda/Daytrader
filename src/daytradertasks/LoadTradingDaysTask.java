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
import daytrader.historicRequestSystem.AbstractHDTCallable;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.interfaces.ICallback;
import daytrader.utils.DTUtil;
import java.io.IOException;
import java.util.Calendar;
import java.util.Random;
import java.util.TreeSet;

/**
 * This task loads a weeks worth of 1 Day bars. I use it to identify the trading
 * days in the last week
 *
 * @author Roy
 */
public class LoadTradingDaysTask extends AbstractHDTCallable {

    private DTDurationEnum durationToUse;

    /**
     * Constructor that performs initialisation and accepts the minimum additional
     * data to make an historic data request (ie a putup (the market security the 
     * request is for) and a date / time representing the time the data is needed
     * up to)
     * @param newPutup - A Putup representing a stock market security
     * @param newEndTime - A Calendar representing the date / time at which the
     * returned data should finish
     */
    public LoadTradingDaysTask(Putup newPutup, Calendar newEndTime) {
        super(newPutup, newEndTime);
        this.durationToUse = DTDurationEnum.W1;
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
    public LoadTradingDaysTask(Putup newPutup, Calendar newEndTime, ICallback newCallback) {
        super(newPutup, newEndTime, newCallback);
        this.durationToUse = DTDurationEnum.W1;
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
     * @param newDurationToUse - A DTDurationEnum enumeration that allows you to 
     * modify the period of time for which the data bars should be loaded
     */
    public LoadTradingDaysTask(Putup newPutup, Calendar newEndTime, ICallback newCallback, DTDurationEnum newDurationToUse) {
        super(newPutup, newEndTime, newCallback);
        this.durationToUse = newDurationToUse;
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
                //Make the request for 1 week of 1 day bars
                Calendar exchClosingCalendar = DTUtil.getExchClosingCalendar(this.endDate);
                StockExchangeHours smHrs = new StockExchangeHours(exchClosingCalendar.getTime());
                Calendar gmtEndCal = smHrs.getEndCalendarInGMT();
                //YLines Pre-Load starts from 'yesterday' move back 1 day from current day (endDate is today)
                gmtEndCal.add(Calendar.DAY_OF_MONTH, -1);
                String currBatchTime = DTUtil.convertCalToBrokerTime(gmtEndCal);
                this.setLoadComplete(false);
                this.setAbort(false);
                this.strAbortMsg = "";
                this.m_client.reqHistoricalData(this.objContract.m_conId,
                        objContract,
                        currBatchTime,
                        this.durationToUse.toString(),
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
                long timeOut = System.currentTimeMillis() + (1 * 10 * 1000);        //Timeout after 1 hour
                this.setAbortTime(timeOut);
                //Wait for data to be delivered
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
                            this.strAbortMsg = "Timed out waiting for stockbroker server LoadTradingDaysTask";
                            this.setAbort(true);
                            this.disconnect();
                            throw new TWSConnectionException(this.strAbortMsg);
                        }
                    } while (!this.isLoadComplete() && !isAbort());
                }
                //Ensure we have not had to abort because of an error
                if (this.isAbort()) {
                    this.disconnect();
                    throw new IOException(this.strAbortMsg);
                }
                //If we reach this point all data was loaded and the loadedPoints will be returned
                this.disconnect();
                //Now build the final result
                finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList, CallbackType.HISTORICDATATRADINGDAYS);
            } else {
                System.err.println("LoadTradingDays is Re-Submitting!!!");
                this.disconnect();
                //Re-submit yourself to the Historic data engine to try another account if availiable (will queue you for this account if only one
                if (this.incrementAndGetResubmitAttempts() < 10) {
                    HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
                    HRSCallableWrapper wrapper = new HRSCallableWrapper(this);
                    HRSys.submitRequest(wrapper);
                } else {
                    System.err.println("LoadTradingDays FAILED after multiple re-submits");
                    throw new TWSConnectionException("No connection to stock broker is availiable");
                }
            }
        } catch (Exception ex) {
            System.err.println("LoadTradingDays Exception: " + ex.getMessage());
            ex.printStackTrace();
            //An exception has occured. Pass the exception back to the execution system inside a result object so it can do the callback with the error
            finalResult = new LoadHistoricDataPointBatchResult(ex, cbDelegate, this.cbList);
        }
        System.out.println("Load Trading Days Done....");
        return finalResult;
    }
}
