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
import daytrader.utils.DTUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class manages the loading of the 15 min bars that make up the data for 1
 * hour bars that are passed into the constructor.
 *
 * @author Roy
 */
public class Load15MinBarsHighPoints extends AbstractHDTCallable implements ICallback {

    private BaseGraph<AbstractGraphPoint> initPoints;
    private ArrayList<Calendar> arl15MinNeeded;
    private AtomicInteger pendingJobs;
    private ReentrantLock lock;

    /**
     * Loads the required 15 min bars for the Y-Lines high points calculation based on
     * a set of 1 day bars (that where loaded in the previous step)
     * @param oneDayBarsData - The result of the 1 day bars step
     * @param endDate - the final date and time to search up to
     * @param parent - The PreLoadYLinesTask managing this Y - Lines loading operation
     */
    public Load15MinBarsHighPoints(LoadHistoricDataPointBatchResult oneDayBarsData, Calendar endDate, PreLoadYLinesTask parent) {
        super(oneDayBarsData.putup, endDate, parent);
        this.lock = new ReentrantLock();
        this.initPoints = oneDayBarsData.getPointsAsGraph();
        this.arl15MinNeeded = new ArrayList<Calendar>();
        for (AbstractGraphPoint currPoint : this.initPoints) {
            Calendar calDate = currPoint.getCalDate();
            calDate.add(Calendar.HOUR, 1);
            this.arl15MinNeeded.add(calDate);
        }
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            //Make the connection
//            int maxAttempts = 100;
//            int currAttempts = 0;
//            Random randGen = new Random();
//            while (!this.m_client.isConnected() && currAttempts < maxAttempts) {
//                currAttempts++;
//                try {
//                    this.connect();
//                } catch (Exception ex) {
//                    System.err.println("Connect attempt failed no " + currAttempts + " : Date = " + this.endDate.getTime().toString() + ", Port number: " + this.executingAccount.getPortNo());
//                    //Sleep for a random number of millisecounds between 1000 and 2000 to allow other connections to close before retrying
//                    Double rand = randGen.nextDouble();
//                    rand += 1;
//                    rand *= 1000;
//                    Thread.sleep(rand.longValue());
//                }
//            }
//            if (this.isConnected()) {
            this.setLoadComplete(false);
            this.setAbort(false);
            this.strAbortMsg = "";
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            this.pendingJobs = new AtomicInteger();
            this.pendingJobs.set(this.arl15MinNeeded.size());
            for (Calendar currDate : this.arl15MinNeeded) {
                Load1HrOf15MinBars hourTask = new Load1HrOf15MinBars(putup, currDate, this);
                HRSCallableWrapper wrapper = new HRSCallableWrapper(hourTask);
                HRSys.submitRequest(wrapper);
            }
            //Set timeout for waiting operation
            Calendar now = Calendar.getInstance();
            now.add(Calendar.HOUR_OF_DAY, 1);
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
                        this.strAbortMsg = "Timed out waiting for stockbroker server Load15MinBarsHighPoints";
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
//                        throw new TWSConnectionException("Timed out waiting for stockbroker server Load15MinBarsHighPoints");
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
            //this.filterData();                                            //We want all points do not filter
            //Now build the final result
            finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList, CallbackType.YLINES15MINBARS);
//            } else {
//                this.disconnect();
//                throw new TWSConnectionException("No connection to stock broker is availiable");
//            }
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
                case YLINES15MINBARS:
                    this.pendingJobs.decrementAndGet();
                    //Add the 1 Hour Bar points to the existing set of data
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
                        //This was the last callback FILTER THE RESULTS TO REMOVE NON HIGHS and then mark this task as completed
                        if (null != this.loadedPoints && this.loadedPoints.size() > 1) {
                            //Copy points list to avoid a concurrent modification exception
                            TreeSet<AbstractGraphPoint> dataStore = new TreeSet<AbstractGraphPoint>(this.loadedPoints);
                            AbstractGraphPoint currHigh = dataStore.last();
                            Iterator<AbstractGraphPoint> descIter = dataStore.descendingIterator();
                            double tenthPerc = 1.0d - 0.001;
                            while (descIter.hasNext()) {
                                AbstractGraphPoint currPoint = descIter.next();
                                if (currPoint != currHigh) {
                                    //if (currPoint.getHigh() <= currHigh.getHigh()) {
                                    //To be removed the high must be lower by AT LEAST a tenth of a percent
                                    if (currPoint.getHigh() <= (currHigh.getHigh() * tenthPerc)) {
                                        Calendar exchCal = DTUtil.convertToExchCal(currPoint.getCalDate());
                                        int hour = exchCal.get(Calendar.HOUR_OF_DAY);
                                        //We are IGNORING blocking point within first 30 min of the day (for 1 hour & 15 min bars)
                                        if (hour != 9) {
                                            this.loadedPoints.remove(currPoint);
                                        }
                                    } else {
                                        currHigh = currPoint;
                                    }
                                }
                            }
                        }
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
