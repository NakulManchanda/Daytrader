/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
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
 * Given a Put up this task updates the data contained in the Put ups HISTORIC
 * data graph to the most recent avaliable.
 *
 * @author Roy
 */
public class UpdateHistoricDataGraphToNow extends AbstractHDTCallable implements ICallback {

    private BaseGraph<AbstractGraphPoint> graphHistoricData;
    private ArrayList<Calendar> calBatches;
    private ReentrantLock lock;
    private AtomicInteger batchesCount;
    private AtomicInteger callbackCount;
    private static final long MAXLOADTIME = 2 * 60 * 1000;                     //At MOST 2 minutes waiting for data

    /**
     * Constructor that accepts the putup that needs to have its HISTORIC
     * data graph updated to now.
     * @param newPutup - A Putup representing a stock market security
     */
    public UpdateHistoricDataGraphToNow(Putup newPutup) {
        //The 'End Time' for this load is now and the callback MUST be to the real time run manager
        super(newPutup, Calendar.getInstance(), newPutup.getRunManager());
        this.lock = new ReentrantLock();
        //Calculate the batches required.
        this.calcBatches();
    }

    /**
     * DO NOT USE IN PRODUCTION CODE THIS CONSTRUCTOR IS FOR TEST RUNNING ONLY.
     * It allows you to define the date / time to load date up to whereas in
     * production code this should be the current system time
     * @param newPutup - A Putup representing a stock market security
     * @param date - A Calendar representing the date / time at which the
     * returned data should finish
     */
    public UpdateHistoricDataGraphToNow(Putup newPutup, Calendar date) {
        //The 'End Time' for this load is now and the callback MUST be to the real time run manager
        super(newPutup, date, newPutup.getRunManager());
        this.lock = new ReentrantLock();
        //Calculate the batches required.
        this.calcBatches();
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            this.setLoadComplete(false);
            this.setAbort(false);
            this.strAbortMsg = "";
            //For each 30 min batch generate and submit a task
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            long abortTime = System.currentTimeMillis() + MAXLOADTIME;
            this.setAbortTime(abortTime);
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
                        this.strAbortMsg = "Timed out waiting for stockbroker server UpdateHistoricDataGraphToNow";
                        this.setAbort(true);
                        this.disconnect();
                        throw new TWSConnectionException(this.strAbortMsg);
                    }
                } while (!this.isLoadComplete() && !isAbort());
            }
//            while (!this.isLoadComplete() && !abort) {
//                if (System.currentTimeMillis() >= abortTime) {
//                    this.abort = true;
//                    this.strAbortMsg = "Timed out waiting for stockbroker server UpdateHistoricDataGraphToNow";
//                    this.disconnect();
//                    throw new TWSConnectionException(this.strAbortMsg);
//                } else {
//                    Thread.yield();
//                }
//            }
            //Ensure we have not had to abort because of an error
            if (this.isAbort()) {
                throw new IOException(this.strAbortMsg);
            }
            //Ensure we have aborted with all data completely loaded
            if (this.isLoadComplete()) {
                finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList, CallbackType.HISTORICDATATODAY);
            } else {
                this.strAbortMsg = "Unknown Failure updating historic graph";
                throw new IOException(this.strAbortMsg);
            }
        } catch (Exception ex) {
            //An exception has occured. Pass the exception back to the execution system inside a result object so it can do the callback with the error
            finalResult = new LoadHistoricDataPointBatchResult(ex, this.cbDelegate, this.cbList);
        }
        return finalResult;
    }

    private void calcBatches() {
        //Each batch is 30 min long
       long batchLength = 30 * 60 * 1000;                          //Batch legth in milliseconds
        this.graphHistoricData = this.putup.getRunManager().getGraphHistoricData();
        this.calBatches = new ArrayList<Calendar>();
        long completeToTimestamp = DTUtil.getExchOpeningTime().getTimeInMillis();
        //Fudge date 1 day to test - REMOVE THIS LINE FOR PRODUCTION CODE
//        completeToTimestamp -= (24 * 60 * 60 * 1000);
        //Look for the first gap in the data longer than 1000 milliseconds
        for (AbstractGraphPoint currPoint : this.graphHistoricData) {
            long currPointTime = currPoint.getTimestamp();
            if ((currPointTime - completeToTimestamp) <= 1000) {
                completeToTimestamp = currPointTime;
            } else {
                //We have a time gap break from loop and start from the current completeToTimestamp
                break;
            }
        }
        //If we are not complete up to the end time calculate batches
        long endTimeInMs = this.endDate.getTimeInMillis();
        long currTime = completeToTimestamp;
        while (currTime < endTimeInMs) {
            currTime += batchLength;
            Calendar newTime = Calendar.getInstance();
            newTime.setTimeInMillis(currTime);
            this.calBatches.add(newTime);
        }
        //If needed add end time as final batch
        if (currTime != endTimeInMs) {
            Calendar newTime = Calendar.getInstance();
            newTime.setTimeInMillis(endTimeInMs);
            this.calBatches.add(newTime);
        }
        //Store the number of batches and set callbacks to zero
        this.batchesCount = new AtomicInteger(this.calBatches.size());
        this.callbackCount = new AtomicInteger(0);
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
        } catch (Exception ex) {
            System.err.println("Unhandled Exception. Message was: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}
