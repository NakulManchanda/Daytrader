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
import java.util.concurrent.Callable;

/**
 * DEPRACATED MOVED TO HISTORICAL DATA QUEUE SYSTEM
 * @author Roy
 */
public class LoadGivenDaysDataTaskDEPRICATED<T  extends AbstractGraphPoint> implements Callable<BaseGraph<T>> {
    
    private Putup putup;
    private Calendar endOfTradingDay;
    private ICallback delegate;
    
    public LoadGivenDaysDataTaskDEPRICATED(Putup newPutup, Calendar dayToLoad){
        this.putup = newPutup;
        this.endOfTradingDay = DTUtil.getExchClosingCalendar(dayToLoad);
    }

    @Override
    public BaseGraph<T> call() throws Exception {
        //Define empty result
        BaseGraph<T> result = new BaseGraph<T>();
        result.setPutup(putup);
        //Attempt a load of the data
        DataGraphLoader myLoader = new DataGraphLoader(this.putup.getTickerCode(), this.putup.getMarket().toString(), endOfTradingDay.getTime(), endOfTradingDay.getTime());
        result = (BaseGraph<T>) myLoader.loadData();
        if(null != this.delegate){
            PreviousDayLoadResult callBackData = new PreviousDayLoadResult(result);
            this.delegate.callback(CallbackType.HISTORICDATAPREVIOUSDAYS, callBackData);
        }
        return result;
    }
    
}
