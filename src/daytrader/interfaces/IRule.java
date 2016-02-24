/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

import daytrader.datamodel.RealTimeRunManager;
import rules.LoadingAdditionalDataException;

/**
 * This interface represents the abstract concept of a rule to be tested
 * @author Roy
 */
public interface IRule {
    
    /**
     * Executes both this rule and any sub-rules
     * @return boolean True if this rule and all sub-rules passed their test, False otherwise
     * @throws LoadingAdditionalDataException - This exception is thrown if the rule cannot
     * complete its true / false test as it needs to load additional data.
     */
    boolean runRule() throws LoadingAdditionalDataException;
    
    /**
     * Adds a sub rule that is tested if the primary rule passes its test. Only if 
     * the sub rule also passes will this primary rule be considered to have passed
     * @param newRule - The sub rule to test if the primary rule provisionally passes
     * @return boolean true if the rule was accepted and will be tested in future, False otherwise.
     */
    boolean addSubRule(IRule newRule);
    
    /**
     * Removes a sub rule so that it is no longer tested
     * @param oldRule - The sub rule to remove from testing
     * @return boolean True if the rule was found and removed, False otherwise
     */
    boolean removeSubRule(IRule oldRule);
    
    /**
     * Each rule must have a set of target data to work on this stores the real time
     * run manager in charge of the put up and its data graph
     * @param target - A RealTimeRunManager or null if you want to clear the manager
     * @return - boolean True if the new manager was stored and applied to all sub rules,
     * False otherwise.
     */
    boolean setRealTimeRunManager(RealTimeRunManager target);
    
    /**
     * Test to determine if the rule is waiting to receive data before it can be checked
     * again. If it is waiting on data to load the rule will FAIL until the data has been
     * loaded.
     * @return Boolean True if this rule is waiting to receive data, False otherwise.
     */
    boolean isLoadingMoreData();
    
    /**
     * This method ALWAYS throws a LoadingAdditionalDataException unless the 
     * historicRequestTask parameter is NULL (which it should never be). The method
     * takes the historic request task, appends a callback to this rule so that it
     * is advised when the task completes (succeeded or failed) and then sets the flag
     * indicating that the rule is waiting for data to load. The rule will FAIL being tested
     * until the task has completed.
     * @param historicRequestTask
     * @throws LoadingAdditionalDataException
     */
    void requestMoreData(IHDTCallable historicRequestTask) throws LoadingAdditionalDataException;
    
}
