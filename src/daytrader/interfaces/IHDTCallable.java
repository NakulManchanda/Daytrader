/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

import daytrader.historicRequestSystem.TWSAccount;
import daytradertasks.LoadHistoricDataPointBatchResult;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * This interface is implemented by AbstractHDTCallable which is the base class
 * from which all Historical Data Task extend (i.e. the classes that load historic
 * data from the stock broker API).
 * @param <V> A LoadHistoricDataPointBatchResult object or any class that extends from it
 * @author Roy
 */
public interface IHDTCallable<V extends LoadHistoricDataPointBatchResult> extends Callable<V> {
    
    /**
     * Retrieves the account that is executing this callable
     * @return A TWS Trading Account object
     */
    TWSAccount getExecutingAcc();
    
    /**
     * Sets the TWS Trading account that will execute this task. Assigned by the Historic
     * Request System when it has an account that can execute this task
     * @param newAcc The TWS Account assigned to execute this task.
     * @return Boolean True if the account was accepted and stored, False otherwise
     */
    boolean setExecutingAcc(TWSAccount newAcc);
    
    /**
     * Retrieves the tasks 'main' callback. This is the one defined by the person who
     * is using the task to load historic data
     * @return An ICallback interface to the users callback
     */
    ICallback getCallback();
    
    /**
     * Sets the tasks 'main' callback. This is the one defined by the person who
     * is using the task to load historic data
     * @param newCallback The users callback object
     * @return Boolean True if the callback was accepted and stored, False otherwise
     */
    boolean setCallback(ICallback newCallback);
    
    /**
     * Retrieves a list of callback's to be made by this task when it completes
     * The first item on the list will be the users main callback, others may be added
     * by the historic data request system to control rule execution while the task
     * is running and on completion
     * @return A list of all callback's to be made when this task completes
     */
    List<ICallback> getCallBackList();
    
    /**
     * Adds a new callback to the list of callback's that will be made when this task
     * completes execution.
     * @param newCallBack A callback that is to be made when the task completes
     * @return Boolean True if the callback was accepted and stored, False otherwise
     */
    boolean addCallBack(ICallback newCallBack);
    
    @Override
     V call() throws Exception;
    
    
    /**
     * Retrieves the integer representing the unique ID number of this task
     * @return int being the ID number for this task
     */
    int getReqId();
    
}
