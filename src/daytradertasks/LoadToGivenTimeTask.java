/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.CallbackType;
import static daytrader.datamodel.CallbackType.HISTORICDATAERROR;
import static daytrader.datamodel.CallbackType.HISTORICDATAFINISHED;
import daytrader.datamodel.Putup;
import daytrader.historicRequestSystem.AbstractHDTCallable;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.interfaces.ICallback;
import daytrader.utils.DTUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Given a Put-up and a date + time as calendar object this class loads all
 * historic data for that day up to the given time
 *
 * @author Roy
 */
public class LoadToGivenTimeTask extends AbstractHDTCallable implements ICallback {

    private ArrayList<Calendar> calBatches;
    private static final long MAXLOADTIME = 60 * 60 * 1000;                     //At MOST 1 hour waiting for data
    private ReentrantLock lock;
    private AtomicInteger batchesCount;
    private AtomicInteger callbackCount;

    /**
     * Constructor that performs initialisation and accepts the minimum additional
     * data to make an historic data request (ie a putup (the market security the 
     * request is for) and a date / time representing the time the data is needed
     * up to)
     * @param newPutup - A Putup representing a stock market security
     * @param newEndTime - A Calendar representing the date / time at which the
     * returned data should finish
     */
    public LoadToGivenTimeTask(Putup newPutup, Calendar newEndTime) {
        super(newPutup, newEndTime);
        this.initialise();
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
    public LoadToGivenTimeTask(Putup newPutup, Calendar newEndTime, ICallback newCallback) {
        super(newPutup, newEndTime, newCallback);
        this.initialise();
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            //For each 30 min batch generate and submit a task
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            long abortTime = System.currentTimeMillis() + MAXLOADTIME;
            this.setLoadComplete(false);
            this.setAbort(false);
            this.strAbortMsg = "";
            for (Calendar currDate : this.calBatches) {
                LoadHistoricDataBatchTask aLoadTask = new LoadHistoricDataBatchTask(this.putup, currDate, this);
                HRSCallableWrapper wrapper = new HRSCallableWrapper(aLoadTask);
                HRSys.submitRequest(wrapper);
            }
            synchronized (this.monitor) {
                do {
                    long waitFor = abortTime - System.currentTimeMillis();
                    if (waitFor <= 0) {
                        waitFor = 1;
                    }
                    if (!this.isLoadComplete()) {
                        this.monitor.wait(waitFor);
                    }
                    if (this.isPassedAbortTime() && !this.isLoadComplete()) {
                        this.strAbortMsg = "Timed out waiting for stockbroker server PreLoadYLinesTask";
                        this.setAbort(true);
                        this.disconnect();
                        throw new TWSConnectionException(this.strAbortMsg);
                    }
                } while (!this.isLoadComplete() && !isAbort());
            }
//            while (!this.isLoadComplete() && !isAbort()) {
//                if (System.currentTimeMillis() >= abortTime) {
//                    this.setAbort(true);
//                    this.strAbortMsg = "Timed out waiting for stockbroker server LoadGivenDaysData";
//                    this.disconnect();
//                    throw new TWSConnectionException(this.strAbortMsg);
//                } else {
//                    Thread.yield();
//                }
//            }
            //Ensure we have not had to abort because of an error
            if (this.isAbort()) {
                //this.disconnect();
                throw new IOException(this.strAbortMsg);
            }
            //Ensure we have aborted with all data completely loaded
            if (this.isLoadComplete()) {
                finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList, CallbackType.HISTORICDATATODAY);
            } else {
                this.strAbortMsg = "Unknown Failure loading YLines";
                throw new IOException(this.strAbortMsg);
            }
        } catch (Exception ex) {
            //An exception has occured. Pass the exception back to the execution system inside a result object so it can do the callback with the error
            finalResult = new LoadHistoricDataPointBatchResult(ex, cbDelegate, this.cbList);
        }
        return finalResult;
    }

    @Override
    public void callback(CallbackType type, Object data) {
        //This method is thread safe NO LONG RUNNING TASKS MAY BE CODED HERE each result must be processes in sequence
        lock.lock();
        try {
            int incrementAndGet = this.callbackCount.incrementAndGet();
            System.err.println("Callbacks Required: " + this.batchesCount.get() + ", Callbacks received: " + incrementAndGet);
            switch (type) {
                case HISTORICDATAFINISHED:
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                        this.loadedPoints.addAll(result.loadedPoints);
                        if (this.callbackCount.get() == this.batchesCount.get()) {
                            //This was the last job mark this task completed so it does its own callback
                            this.setLoadComplete(true);
                        }
                    }
                    break;
                case HISTORICDATAERROR:
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                        Exception execException = result.getExecException();
                        System.err.println(execException.getMessage());
                        execException.printStackTrace();
                        this.strAbortMsg = "Exception loading Given Days data. Message was: " + result.getExecException().getMessage();
                        this.setAbort(true);
                    }
                    break;
            }
        } finally {
            lock.unlock();
        }
    }

    private void initialise() {
        Calendar exchOpeningTime = DTUtil.getExchOpeningCalendar(this.endDate);
        this.lock = new ReentrantLock();
        this.calBatches = new ArrayList<Calendar>();
        //Generate a batch for every 30 min of the day to the end time
        long thirtyMin = (30 * 60 * 1000);                                          //Thirty min as millisecs
        long currTime = exchOpeningTime.getTimeInMillis() + thirtyMin;
        long endTime = this.endDate.getTimeInMillis();
        if (currTime >= endTime) {
            //Only 1 batch of data is needed load to end time
            this.calBatches.add(this.endDate);
        } else {
            //Multiple batchesCount are needed generate them
            while (currTime < endTime) {
                Calendar calTime = Calendar.getInstance(this.endDate.getTimeZone());
                calTime.clear();
                calTime.setTimeInMillis(currTime);
                this.calBatches.add(calTime);
                currTime += thirtyMin;
            }
            //If we have not added the end time add it now
            if ((currTime - thirtyMin) != endTime) {
                this.calBatches.add(this.endDate);
            }
        }
        //Store the size of the batchesCount as the number of callbacks required
        this.batchesCount = new AtomicInteger(this.calBatches.size());
        this.callbackCount = new AtomicInteger(0);
    }
}
