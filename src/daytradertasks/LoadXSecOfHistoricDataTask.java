/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.CallbackType;
import daytrader.datamodel.Putup;
import daytrader.historicRequestSystem.AbstractHDTCallable;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.interfaces.ICallback;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Given a number of seconds to request data for this task submits enough 30 min
 * / 1 sec batches to load all the data
 *
 * @author Roy
 */
public class LoadXSecOfHistoricDataTask extends AbstractHDTCallable implements ICallback {

    private ReentrantLock lock;
    private ArrayList<Calendar> arlTimeNeeded;
    private static final int SECINBATCH = (30 * 60);                        //Time covered by one batch in seconds
    private AtomicInteger pendingJobs;

    /**
     * Constructor given a number of seconds X for which data is required this task
     * creates and submits enough 30 min 1 sec requests to load X seconds of data
     * @param secToLoad - the total number of seconds to load data for (ie X)
     * @param newPutup - A Putup representing a stock market security
     * @param endDate - A Calendar representing the date / time at which the
     * returned data should finish
     * @param newCallback - A callback to be made after the requested data has been 
     * loaded to perform further processing
     */
    public LoadXSecOfHistoricDataTask(int secToLoad, Putup newPutup, Calendar endDate, ICallback newCallback) {
        super(newPutup, endDate, newCallback);
        this.lock = new ReentrantLock();
        this.arlTimeNeeded = new ArrayList<Calendar>();
        this.arlTimeNeeded.add(this.endDate);
        int secRemaining = secToLoad - SECINBATCH;
        Calendar lastBatchTime = Calendar.getInstance();
        lastBatchTime.clear();
        lastBatchTime.setTimeInMillis(this.endDate.getTimeInMillis());
        //Generate batches to load
        while (0 < secRemaining) {
            lastBatchTime.add(Calendar.SECOND, -SECINBATCH);
            Calendar newBatchTime = Calendar.getInstance();
            newBatchTime.clear();
            newBatchTime.setTimeInMillis(lastBatchTime.getTimeInMillis());
            this.arlTimeNeeded.add(newBatchTime);
            secRemaining -= SECINBATCH;
        }
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            this.setLoadComplete(false);
            this.setAbort(false);
            this.strAbortMsg = "";
            this.pendingJobs = new AtomicInteger(0);
            this.pendingJobs.set(this.arlTimeNeeded.size());
            for (Calendar currDate : this.arlTimeNeeded) {
                LoadHistoricDataBatchTask batchTask = new LoadHistoricDataBatchTask(putup, currDate, this);
                HRSCallableWrapper wrapper = new HRSCallableWrapper(batchTask);
                HRSys.submitRequest(wrapper);
            }
            //Set timeout for waiting operation
            long timeOut = System.currentTimeMillis() + (60 * 60 * 1000);        //Timeout after 1 Hour
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
                        this.strAbortMsg = "Timed out waiting for stockbroker server LoadXSecOfHistoricDataTask";
                        this.setAbort(true);
                        this.disconnect();
                        throw new TWSConnectionException(this.strAbortMsg);
                    }
                } while (!this.isLoadComplete() && !isAbort());
            }
//            //Wait for data to be delivered
//            while (!this.isLoadComplete() && !isAbort()) {
//                if (System.currentTimeMillis() < timeOut) {
//                    //Thread.yield();
//                    Thread.sleep(500);
//                } else {
//                    this.disconnect();
//                    throw new TWSConnectionException("Timed out waiting for stockbroker server Load1HourBarsHighPoints");
//                }
//            }
            //Ensure we have not had to abort because of an error
            if (this.isAbort()) {
                //this.disconnect();
                throw new IOException(this.strAbortMsg);
            }
            //If we reach this point all data was loaded and the loadedPoints will be returned
            //Now build the final result
            finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList);
        } catch (Exception ex) {
            //An exception has occured. Pass the exception back to the execution system inside a result object so it can do the callback with the error
            finalResult = new LoadHistoricDataPointBatchResult(ex, cbDelegate, this.cbList);
        }
        return finalResult;
    }

    @Override
    public void callback(CallbackType type, Object data) {
        lock.lock();
        try {
            switch (type) {
                case HISTORICDATAFINISHED:
                    this.pendingJobs.decrementAndGet();
                    //Add the points to the existing set of data
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                        if (null != result.loadedPoints && 0 < result.loadedPoints.size()) {
                            //Add the points to my data
                            for (AbstractGraphPoint currPoint : result.loadedPoints) {
                                this.loadedPoints.add(currPoint);
                            }
                        }
                    }
                    if (0 == this.pendingJobs.get()) {
                        //Mark Task completed
                        this.setLoadComplete(true);
                    }
                    break;
                case HISTORICDATAERROR:
                    //Hit the Panic button
                    this.pendingJobs.decrementAndGet();
                    if (data instanceof LoadHistoricDataPointBatchResult) {
                        LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                        Exception execException = result.getExecException();
                        if (null != execException) {
                            this.strAbortMsg = execException.getMessage();
                        } else {
                            this.strAbortMsg = "Unexpected Error retrieving 1 Hour bars in Y Line preload";
                        }
                        this.setAbort(true);
                    }
                    break;
            }
        } finally {
            lock.unlock();
        }
    }
}
