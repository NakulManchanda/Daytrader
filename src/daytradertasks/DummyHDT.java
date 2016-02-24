/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytradertasks;

import com.ib.client.Contract;
import daytrader.datamodel.DTConstants;
import daytrader.historicRequestSystem.AbstractHDTCallable;

/**
 * This class is used to fill the processing queue of a TWSAccount that receives a 
 * pacing violation forcing that account to wait 10 min before accepting other requests.
 * 
 * NOT IT SHOULD NEVER EXECUTE ITS CALL METHOD and an exception will be thrown if
 * it does. The purpose of this class is simply to mark the "processed in the last
 * 10 min queue" of a TWSAccount as full.
 * @author Roy
 */
public class DummyHDT extends AbstractHDTCallable {

    @Override
    public LoadHistoricDataPointBatchResult call() throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void createContract() {
        //Create a standard contract to use when issuing the API call
        //As this is a Dummy Item I just borrowed Microsofts details for this as their is
        //no real putup
        this.objContract = new Contract();
        objContract.m_conId = DTConstants.getConId();
        objContract.m_symbol = "MSFT";
        objContract.m_secType = "STK";
        objContract.m_exchange = "SMART";
        objContract.m_currency = "USD";
        objContract.m_primaryExch = "NYSE";
    }
    
    
}
