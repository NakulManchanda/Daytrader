/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.CallbackType;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.historicRequestSystem.AbstractHDTCallable;
import daytrader.interfaces.ICallback;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class manages the loading of the 1 sec bars for the 15 min YLine high
 * points
 *
 * @author Roy
 */
public class Load1SecBarsHighPoints extends AbstractHDTCallable implements ICallback {

    private BaseGraph<AbstractGraphPoint> initPoints;
    private ArrayList<Calendar> arl1SecNeeded;
    private AtomicInteger pendingJobs;
    private ReentrantLock lock;

    /**
     * Constructor providing data from the previous step of loading the Y-Line data
     * @param fifteenMinBarsHighPoints - The result from the previous loading step
     * @param endDate - The date / Time at which the data to be loaded should end
     * @param parent - The PreLoadYLinesTask managing the entire operation and co-ordinating
     * the multiple requests.
     */
    public Load1SecBarsHighPoints(LoadHistoricDataPointBatchResult fifteenMinBarsHighPoints, Calendar endDate, PreLoadYLinesTask parent) {
        super(fifteenMinBarsHighPoints.putup, endDate, parent);
        this.lock = new ReentrantLock();
        this.initPoints = fifteenMinBarsHighPoints.getPointsAsGraph();
        this.arl1SecNeeded = new ArrayList<Calendar>();
        for (AbstractGraphPoint currPoint : this.initPoints) {
            Calendar calDate = currPoint.getCalDate();
            calDate.add(Calendar.MINUTE, 15);
            this.arl1SecNeeded.add(calDate);
        }
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            this.setLoadComplete(false);
            this.setAbort(false);
            this.strAbortMsg = "";
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            this.pendingJobs = new AtomicInteger();
            this.pendingJobs.set(this.arl1SecNeeded.size());
            for (Calendar currDate : this.arl1SecNeeded) {
                LoadHistoricData15MinBatch secTask = new LoadHistoricData15MinBatch(putup, currDate, this);
                HRSCallableWrapper wrapper = new HRSCallableWrapper(secTask);
                HRSys.submitRequest(wrapper);
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
                    if (!this.isLoadComplete()) {
                        this.monitor.wait(waitFor);
                    }
                    if (this.isPassedAbortTime() && !this.isLoadComplete()) {
                        this.strAbortMsg = "Timed out waiting for stockbroker server Load1SecBarsHighPoints";
                        this.setAbort(true);
                        this.disconnect();
                        throw new TWSConnectionException(this.strAbortMsg);
                    }
                } while (!this.isLoadComplete() && !isAbort());
            }
            //Wait for data to be delivered
//            while (!this.isLoadComplete() && !isAbort()) {
//                if (System.currentTimeMillis() < timeOut) {
//                    //Thread.yield();
//                    Thread.sleep(500);
//                } else {
//                    this.disconnect();
//                    throw new TWSConnectionException("Timed out waiting for stockbroker server Load1SecBarsHighPoints");
//                }
//            }
            //Ensure we have not had to abort because of an error
            if (this.isAbort()) {
                //this.disconnect();
                throw new IOException(this.strAbortMsg);
            }
            //If we reach this point all data was loaded and the loadedPoints will be returned
            //this.disconnect();
            //However we can sometimes get data Bryn does not want (both before market opening and after market close)
            //Bryn wants this data removed
            //this.filterData();                                            //We want all points do not filter
            //Now build the final result
            finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList, CallbackType.YLINES1SECBARS);
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
                    //Add the 1 Sec Bar points to the existing set of data
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
                        //Mark Task completed (No filtering to be done retain all 1 sec bars)
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
                            this.strAbortMsg = "Unexpected Error retrieving 1 Sec bars in Y Line preload";
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
