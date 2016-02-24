/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.CallbackType;
import daytrader.datamodel.Putup;
import daytrader.datamodel.YLineLoadStatus;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.exceptions.TWSConnectionException;
import daytrader.historicRequestSystem.AbstractHDTCallable;
import daytrader.interfaces.ICallback;
import java.io.IOException;
import java.util.Calendar;
import java.util.TreeSet;

/**
 * This task manages the pre-loading of Y Line data. Each Putup may contain a
 * date to go back to looking for Y Lines (MaxYLineDate) this class is
 * responsible for managing the loading of data back to this date ahead of the
 * calculation of the initial Y Lines for the security.
 * 
 * NB: it makes potentially many data requests to the stock brokers server and
 * may take some time to complete.
 *
 * @author Roy
 */
public class PreLoadYLinesTask extends AbstractHDTCallable implements ICallback {

    private Calendar maxYLineDate;
    private TreeSet<AbstractGraphPoint> monthCache;                     //This is needed for gradient calculations across days
    private int intRTH = 1;
    private int intDateFormat = 1;
    private static final long MAXLOADTIME = 60 * 60 * 1000;                     //At MOST 1 hour waiting for data

    /**
     * Constructor accepting the putup representing the stock market security 
     * for which Y-Lines should be loaded.
     * @param myPutup
     */
    public PreLoadYLinesTask(Putup myPutup) {
        this.putup = myPutup;
        this.maxYLineDate = this.putup.getMaxYLineDate();
        this.cbDelegate = this.putup;
        this.endDate = this.putup.getTodaysDate();
        this.init();
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        this.loadedPoints = new TreeSet<AbstractGraphPoint>();
        LoadHistoricDataPointBatchResult finalResult = null;
        try {
            //GET INSTANCE OF HISTORICAL REQUEST PROCESSING SYSTEM
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            //Generate first task (1 Day Bars)
            if (null != this.maxYLineDate) {
                this.setLoadComplete(false);
                this.setAbort(false);
                this.strAbortMsg = "";
                long abortTime = System.currentTimeMillis() + MAXLOADTIME;
                this.setAbortTime(abortTime);
                Load1DayBarsHighPoints loadTask = new Load1DayBarsHighPoints(this.putup, this.endDate, this.maxYLineDate, this);
                HRSCallableWrapper wrapper = new HRSCallableWrapper(loadTask);
                HRSys.submitRequest(wrapper);
                synchronized (this.monitor) {
                    do {
                        long waitFor = abortTime - System.currentTimeMillis();
                        if (waitFor <= 0) {
                            waitFor = 1;
                        }
                        if(!this.isLoadComplete()){
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
//                    while (!this.isLoadComplete() && !isAbort()) {
//                        if (System.currentTimeMillis() >= abortTime) {
//                            this.setAbort(true);
//                            this.strAbortMsg = "Timed out waiting for stockbroker server PreLoadYLinesTask";
//                            this.disconnect();
//                            throw new TWSConnectionException(this.strAbortMsg);
//                        } else {
//                            Thread.yield();
//                        }
//                    }
                //Ensure we have not had to abort because of an error
                if (this.isAbort()) {
                    //this.disconnect();
                    throw new IOException(this.strAbortMsg);
                }
                //Ensure the YLines Loading Sequence completed
                if (this.isLoadComplete()) {
                    //Disconnect & Prepare the final result (NB No need to filter all points will be in trading hours)
                    //this.disconnect();
                    finalResult = new LoadHistoricDataPointBatchResult(putup, loadedPoints, cbDelegate, this.cbList, CallbackType.YLINESCOMPLETE);
                } else {
                    //this.disconnect();
                    this.strAbortMsg = "Unknown Failure loading YLines";
                    throw new IOException(this.strAbortMsg);
                }
            } else {
                throw new IllegalArgumentException("No end date for Y Line loading was specified");
            }
        } catch (Exception ex) {
            //An exception has occured. Pass the exception back to the execution system inside a result object so it can do the callback with the error
            finalResult = new LoadHistoricDataPointBatchResult(ex, cbDelegate, this.cbList, CallbackType.YLINESLOADERROR);
        }
        return finalResult;
    }

    @Override
    public void callback(CallbackType type, Object data) {
        switch (type) {
            case HISTORICDATAERROR:
                if (data instanceof LoadHistoricDataPointBatchResult) {
                    LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                    Exception execException = result.getExecException();
                    System.err.println(execException.getMessage());
                    execException.printStackTrace();
                }
                break;
            case YLINES1DAYBARS:
                //Advance to 1 Hour Bars
                if (data instanceof LoadHistoricDataPointBatchResult) {
                    LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                    HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
                    Load1HourBarsHighPoints hourTask = new Load1HourBarsHighPoints(result, this.endDate, this);
                    HRSCallableWrapper wrapper = new HRSCallableWrapper(hourTask);
                    HRSys.submitRequest(wrapper);
                    //Now send the 1 Day Bars to the Putup to store as its month cache (needed for gradient calculations across days)
                    this.putup.callback(CallbackType.YLINEMONTHCACHE, result.loadedPoints);
                }
                break;
            case YLINES1HOURBARS:
                //Advance to 15 min Bars
                if (data instanceof LoadHistoricDataPointBatchResult) {
                    LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                    HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
                    Load15MinBarsHighPoints minTask = new Load15MinBarsHighPoints(result, endDate, this);
                    HRSCallableWrapper wrapper = new HRSCallableWrapper(minTask);
                    HRSys.submitRequest(wrapper);
                    this.putup.setYLineStatus(YLineLoadStatus.YLINELOADING15MINBARS);
                }
                break;
            case YLINES15MINBARS:
                //Advance to 1 sec Bars
                if (data instanceof LoadHistoricDataPointBatchResult) {
                    LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                    HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
                    Load1SecBarsHighPoints secTask = new Load1SecBarsHighPoints(result, endDate, this);
                    HRSCallableWrapper wrapper = new HRSCallableWrapper(secTask);
                    HRSys.submitRequest(wrapper);
                    this.putup.setYLineStatus(YLineLoadStatus.YLINELOADING1SECBARS);
                }
                break;
            case YLINES1SECBARS:
                //Final result store this ready for return
                if (data instanceof LoadHistoricDataPointBatchResult) {
                    LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult) data;
                    this.loadedPoints.addAll(result.loadedPoints);
                }
                this.setLoadComplete(true);
                break;
        }
    }
}
