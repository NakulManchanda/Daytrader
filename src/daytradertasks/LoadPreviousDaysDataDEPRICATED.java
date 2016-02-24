/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.CallbackType;
import daytrader.datamodel.PreviousDayLoadResult;
import daytrader.datamodel.Putup;
import daytrader.interfaces.ICallback;
import daytrader.utils.DTUtil;
import daytrader.utils.DataGraphLoader;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.Callable;

/**
 * DEPRICATED MOVING TO HISTORICAL REQUEST QUEUE SYSTEM USE LOADPREVDAYCLOSE
 * NEEDS TO REWRITE BaseGraph.getPrevDayGraph() to DEPRICATE THIS
 * This callable takes in a put up and a ICallable object to call back to with the loaded data.
 * Based on the today value stored in the put up it loads the previous TRADING days data in 
 * full to a one sec resolution.
 * @author Roy
 */
public class LoadPreviousDaysDataDEPRICATED<T  extends AbstractGraphPoint> implements Callable<BaseGraph<T>> {
    
    private Putup putup;
    private ICallback delegate;
    
    //This is the close of trading on the previous day
    private Calendar prevExchClose;
    
    public LoadPreviousDaysDataDEPRICATED(Putup newPutup, ICallback newCallbackDelegate){
        this.putup = newPutup;
        this.delegate = newCallbackDelegate;
        this.generateDate();
    }

    @Override
    public BaseGraph<T> call() throws Exception {
        //Define empty result
        BaseGraph<T> result = new BaseGraph<T>();
        result.setPutup(putup);
        //Attempt a load of the data
        DataGraphLoader myLoader = new DataGraphLoader(this.putup.getTickerCode(), this.putup.getMarket().toString(), prevExchClose.getTime(), prevExchClose.getTime());
        result = (BaseGraph<T>) myLoader.loadData();
        if(null != this.delegate){
            PreviousDayLoadResult callBackData = new PreviousDayLoadResult(result);
            this.delegate.callback(CallbackType.HISTORICDATAPREVIOUSDAYS, callBackData);
        }
        return result;
    }

    private void generateDate() {
        //Get previous day value
        Calendar todaysDate = this.putup.getTodaysDate();
        Calendar prevDay = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        prevDay.setTimeInMillis(todaysDate.getTimeInMillis());
        prevDay.add(Calendar.DAY_OF_MONTH, -1);
        //NB we only need to go back 1 day the stockbrokers API will move back further if this is not a
        //trading day. i.e. weekend etc
        //Now get opening time
        this.prevExchClose = DTUtil.getExchClosingCalendar(prevDay);
    }
    
}
