/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.CallbackType;
import daytrader.datamodel.RealTimeRunManager;
import daytrader.datamodel.SingleDoublePattern;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.interfaces.ICallback;
import daytradertasks.UpdateHistoricDataGraphToNow;
import java.util.Calendar;
import java.util.concurrent.Callable;

/**
 * Each time a new Single Double pattern is identified on a graph we must test
 * if round one has been completed. If so we will need to make a call back to
 * advance the state of the put up into round 2. Round One requires that 
 * the historic data graph is inside FTG Range and 3M Range and that a 
 * valid single double pattern exists and the data graph is NOT IG'd
 *
 * @author Roy
 */
public class TestRoundOneComplete implements Callable<Boolean> {

    private RealTimeRunManager manager;
    private ICallback callback;
    private SingleDoublePattern pattern;
    private Calendar myDate;                            //Remove FOR TESTING ONLY

    /**
     * Constructor accepting a RealTimeRunManager to test
     * @param newManager - The RealTimeRunManager that is to be tested to confirm if 
     * Round One rules are all completed
     */
    public TestRoundOneComplete(RealTimeRunManager newManager) {
        this.manager = newManager;
        this.callback = newManager;
    }
    
    /**
     * Constructor accepting a RealTimeRunManager to test
     * @param newManager - The RealTimeRunManager that is to be tested to confirm if 
     * Round One rules are all completed
     * @param newCb - The Callback to be made with the tests results, nominally to the RealTimeRunManager
     */
    public TestRoundOneComplete(RealTimeRunManager newManager, ICallback newCb) {
        this.manager = newManager;
        this.callback = newCb;
    }

    /**
     * REMOVE THIS CONSTRUCTOR FOR TESTING ONLY
     * @param newManager - The RealTimeRunManager that is to be tested to confirm if 
     * Round One rules are all completed
     * @param newCb - The Callback to be made with the tests results, nominally to the RealTimeRunManager
     * @param date - For testing ONLY this constructor allows the date value to be changed to a 
     * previous day.
     */
    public TestRoundOneComplete(RealTimeRunManager newManager, ICallback newCb, Calendar date) {
        this(newManager, newCb);
        this.myDate = date;
    }

    @Override
    public Boolean call() throws Exception {
        Boolean result = false;
        //Step 1: Load historical data up until 'now'
        UpdateHistoricDataGraphToNow updateTask = new UpdateHistoricDataGraphToNow(this.manager.getMyPutup());
        HRSCallableWrapper wrapper = new HRSCallableWrapper(updateTask);
        HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
        HRSys.submitRequest(wrapper);
        //This is a blocking function call, use wait to block until data is loaded
        synchronized (wrapper) {
            do {
                wrapper.wait();
            } while (null == wrapper.getResultObject());
        }
        String putupCode = this.manager.getMyPutup().get3mCode();
        //Step 2: Now we have all WAPS up to date run 'final' tests in order, if any test fails transition to 5 SecBars + RealTime data
        //If all pass transition to round 2 state
        //Tests are:
        //1) FTG
        if (this.manager.isFTGBreached()) {
            System.out.println(putupCode + "Round one FTG");
            //2) 3M Range
            if (this.manager.isIn3MRange()) {
                System.out.println(putupCode + "Round one 3M Range");
                //3) Look for a Single / Double pattern in the historical data
                SingleDoublePattern newPattern = this.manager.lookForNewSingleDoublePatternsOnGraph();
                if(null != newPattern){
                    this.pattern = newPattern;
                    System.out.println(putupCode + "Round one Pattern Found");
                    //4) Check we are not IG'd
                    IGMonitor IGTester = new IGMonitor();
                    if(!IGTester.isIGd(this.manager.getGraphHistoricData())){
                        System.out.println(putupCode + "Round one IG check passed");
                        //FTG Passed, 3M Range Passed, Single / Double Pattern found and we are not IG'd
                    }
                }
            }
        }
        if(!result){
            this.transTo5SecBarAndRealTimeData();
        }
        return result;
    }

    private void transTo5SecBarAndRealTimeData() {
        this.callback.callback(CallbackType.REJECTSINGLEDOUBLECLUE, this);
    }
}
