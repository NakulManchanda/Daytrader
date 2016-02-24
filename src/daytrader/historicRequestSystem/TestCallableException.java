/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem;

import daytrader.datamodel.Putup;
import daytrader.interfaces.ICallback;
import daytradertasks.LoadHistoricDataPointBatchResult;
import java.util.Calendar;

/**
 * DEPRICATED CLASS DO NOT USE IN PRODUCTION CODE
 * A class to test what happens when a callable throws an error, useful for debugging
 * but not used in production code.
 * 
 * @author Roy
 */
public class TestCallableException extends AbstractHDTCallable {
    
    public TestCallableException(){
    }
    
    public TestCallableException(Putup newPutup, Calendar newEndTime){
        super(newPutup, newEndTime);
    }
    
    public TestCallableException(Putup newPutup, Calendar newEndTime, ICallback newCallback){
        super(newPutup, newEndTime, newCallback);
    }

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
