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
import daytrader.datamodel.RealTimeRunManager;
import daytrader.datamodel.WhatToShowEnum;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.historicRequestSystem.AbstractHDTCallable;
import daytrader.interfaces.ICallback;
import daytrader.utils.DTUtil;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TreeSet;

/**
 * This class will load the data for the close of the previous day. This data 
 * will cover the last 30 minutes of trading on the previous trading day
 *
 * @author Roy
 */
public class LoadPrevDayClose extends AbstractHDTCallable {

    private boolean hasMovedBack;

    /**
     * Constructor that performs initialisation and accepts the minimum additional
     * data to make an historic data request (ie a putup (the market security the 
     * request is for) and a date / time representing the time the data is needed
     * up to)
     * @param newPutup - A Putup representing a stock market security
     * @param newEndTime - A Calendar representing the date / time at which the
     * returned data should finish
     */
    public LoadPrevDayClose(Putup newPutup, Calendar newEndTime) {
        super(newPutup, newEndTime);
        this.hasMovedBack = false;
        this.moveCalendarToPrevDay();
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
    public LoadPrevDayClose(Putup newPutup, Calendar newEndTime, ICallback newCallback) {
        super(newPutup, newEndTime, newCallback);
        this.hasMovedBack = false;
        this.moveCalendarToPrevDay();
    }

    private void moveCalendarToPrevDay() {
        if (null != this.endDate && !this.hasMovedBack) {
            this.endDate = DTUtil.getExchClosingCalendar(endDate);
            this.endDate.add(Calendar.DAY_OF_MONTH, -1);
            this.hasMovedBack = true;
        }
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        try {
            this.loadedPoints = new TreeSet<AbstractGraphPoint>();
            LoadHistoricDataPointBatchResult finalResult = null;
            try {
                //Make the connection
//            int maxAttempts = 100;
//            int currAttempts = 0;
//            while (!this.m_client.isConnected() && currAttempts < maxAttempts) {
//                currAttempts++;
//                try {
//                    System.out.println("Trying to Connect PrevDayClose for putup: " + this.putup.getTickerCode());
//                    this.connect();
//                } catch (Exception ex) {
//                    System.err.println("Connect attempt failed no " + currAttempts + " : Date = " + this.endDate.getTime().toString() + ", Port number: " + this.executingAccount.getPortNo());
//                }
//            }
                //I do not like it but the stockbrokers server does not alway connect on first attempt, retry until it does.
                long connectFailTime = System.currentTimeMillis() + (5 * 60 * 1000);    //Attemp to connect for 5 min tops
                long sleepTime = 5000;
                while (!this.isConnected() && System.currentTimeMillis() < connectFailTime) {
                    try {
                        this.connect();
                    } catch (Exception ex) {
                        System.err.println("Failed connect LoadPrevDayClose for putup: " + this.putup.getTickerCode() + " Exception: " + ex.getMessage());
                        ex.printStackTrace();
                        Thread.sleep(sleepTime);
                    }
                }
                if (this.isConnected()) {
                    SimpleDateFormat format1 = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                    String timeFMString = format1.format(this.endDate.getTime()) + " GMT";
                    int intRTH = 1;
                    int intDateFormat = 1;
                    this.setLoadComplete(false);
                    this.setAbort(false);
                    this.strAbortMsg = "";
                    this.m_client.reqHistoricalData(this.objContract.m_conId,
                            this.objContract,
                            timeFMString,
                            DTDurationEnum.S1800.toString(),
                            BarSizeSettingEnum.SEC1.toString(),
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
                    long timeOut = System.currentTimeMillis() + (60 * 1000);        //Timeout after 1 min 
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
                                this.strAbortMsg = "Timed out waiting for stockbroker server LoadPrevDayClose";
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
                    //However we can sometimes get data Bryn does not want (both before market opening and after market close)
                    //Bryn wants this data removed
                    this.filterData();
                    //Now build the final result
                    finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList, CallbackType.HISTORICDATACLOSEPREVDAY);
                } else {
                    this.disconnect();
                    //throw new TWSConnectionException("No connection to stock broker is availiable");
                    //If possible re-submit this request
                    RealTimeRunManager runManager = this.putup.getRunManager();
                    if (null != runManager) {
                        runManager.loadPrevDayClose();
                        throw new TWSConnectionException("No connection to stock broker is availiable");
                    } else {
                        throw new TWSConnectionException("No connection to stock broker is availiable");
                    }
                }
            } catch (Exception ex) {
                //An exception has occured. Pass the exception back to the execution system inside a result object so it can do the callback with the error
                finalResult = new LoadHistoricDataPointBatchResult(ex, cbDelegate, this.cbList);
            }
            return finalResult;
        } finally {
        }
    }
}