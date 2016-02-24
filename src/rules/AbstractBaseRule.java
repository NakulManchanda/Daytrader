/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.CallbackType;
import daytrader.datamodel.RealTimeRunManager;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.historicRequestSystem.PriorityEnum;
import daytrader.interfaces.ICallback;
import daytrader.interfaces.IHDTCallable;
import daytrader.interfaces.IRule;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This provides a basic implementation of the IRule interface and the code to
 * manage sub rule collections. All rules should extend from this class
 *
 * @author Roy
 */
public abstract class AbstractBaseRule implements IRule, ICallback {

    /**
     * An Array of rules that must be passed for this test to pass
     */
    protected ArrayList<IRule> subRules;
    /**
     * The RealTimeRunManager that is using this rule to test its data.
     */
    protected RealTimeRunManager owner;
    /**
     * Thread safe boolean flag indicating that this rule is waiting on more data 
     * to be loaded from the stock brokers data server.
     */
    protected AtomicBoolean loadingMoreData;

    /**
     * Default constructor
     */
    protected AbstractBaseRule() {
        this.subRules = new ArrayList<IRule>();
        this.loadingMoreData = new AtomicBoolean(false);
    }

    @Override
    public boolean runRule() {
        boolean result = false;
        if (!this.loadingMoreData.get()) {
            try {
                boolean blnPrime = this.runPrimaryRule();
                if (blnPrime) {
                    //Prime rule passed run all sub rules
                    for (IRule currRule : this.subRules) {
                        if (!currRule.runRule()) {
                            blnPrime = false;
                            break;
                        }
                    }
                    result = blnPrime;
                }
            } catch (LoadingAdditionalDataException ex) {
                this.loadingMoreData.set(true);
                result = false;
            }
        }
        return result;
    }

    @Override
    public boolean addSubRule(IRule newRule) {
        boolean result = false;
        if (null != newRule) {
            if (null == this.subRules) {
                this.subRules = new ArrayList<IRule>();
            }
            result = this.subRules.add(newRule);
            if (result) {
                newRule.setRealTimeRunManager(this.owner);
            }
        }
        return result;
    }

    @Override
    public boolean removeSubRule(IRule oldRule) {
        boolean result = false;
        if (null != oldRule) {
            if (null != this.subRules && 0 < this.subRules.size()) {
                result = this.subRules.remove(oldRule);
                if (result) {
                    oldRule.setRealTimeRunManager(null);
                }
            }
        }
        return result;
    }

    @Override
    public boolean setRealTimeRunManager(RealTimeRunManager target) {
        boolean result = false;
        if (null != target) {
            this.owner = target;
            if (null != this.subRules && 0 < this.subRules.size()) {
                for (IRule currRule : this.subRules) {
                    currRule.setRealTimeRunManager(target);
                }
            }
        }
        return result;
    }

    @Override
    public boolean isLoadingMoreData() {
        return this.loadingMoreData.get();
    }
    
    @Override
    public void requestMoreData(IHDTCallable historicRequestTask) throws LoadingAdditionalDataException {
        if(null != historicRequestTask){
            //Add rule as an additional callback
            historicRequestTask.addCallBack(this);
            //Submit the task to the Historic Request System
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            HRSCallableWrapper wrapper = new HRSCallableWrapper(historicRequestTask, PriorityEnum.IMMEDIATE);
            HRSys.submitRequest(wrapper);
            //Set flag to indicate waiting for task to complete
            this.loadingMoreData.set(true);
            //Throw the Loading Additional Data Exception to abort the current test of the rule.
            throw new LoadingAdditionalDataException("Loading more data for rule...");
        }
    }

    /**
     * This method should be implemented by the extending class. The code to run
     * the primary rule should be implement into this 'hook' method
     *
     * @return boolean True if the primary rules was passed and the sub rules should now
     * be tested. False otherwise
     * @throws LoadingAdditionalDataException - Thrown if the rule has had to request
     * additional data from the stock brokers server. The rule will ALWAYS fail after 
     * throwing this exception and will continue to fail until the additional data is
     * available or the request to the data server completes in some other way (such
     * as throwing its own exception)
     */
    protected abstract boolean runPrimaryRule() throws LoadingAdditionalDataException;

    @Override
    public void callback(CallbackType type, Object data) {
        this.loadingMoreData.set(false);
    }

    /**
     * A thread safe method to print a message to the console - use for debugging
     * @param strMsg - String being the message to print
     */
    protected void printToConsole(String strMsg) {
        java.awt.EventQueue.invokeLater(new ConMsg(strMsg));
    }

    /**
     * Java Runnable that may be used to print a message to the console
     */
    protected class ConMsg implements Runnable {

        private String strMsg;

        /**
         * Constructor that accepts the message to print to the console
         * @param strNewMsg - String containing a message to print.
         */
        public ConMsg(String strNewMsg) {
            this.strMsg = strNewMsg;
        }

        @Override
        public void run() {
            System.out.println(this.strMsg);
        }
    }
}
