/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import static daytrader.datamodel.PutupTypeEnum.LONGS;
import static daytrader.datamodel.PutupTypeEnum.SHORTS;
import daytrader.interfaces.IRule;
import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import rules.AlwaysFailRule;
import rules.FTGBreachPoint;
import rules.FTGBreachPointClueLong;
import rules.FTGBreachPointClueShort;
import rules.LoadingAdditionalDataException;
import rules.RulesContainer;
import rules.SingleDoubleClueLongs;
import rules.TestRoundOneComplete;
import rules.ThreeMLRanging;
import rules.ThreeMLRangingClueLongs;
import rules.ThreeMLRangingClueShorts;

/**
 * This manager manages a collection of rules. Depending on the state at any
 * given instant the manager selects the appropriate set of rules to execute.
 *
 * @author Roy
 */
public class RulesStateManager {

    private static final boolean DEBUG = true;
    private PutupTypeEnum type;
    private RulesStateEnum currState;
    private EnumMap<RulesStateEnum, IRule> rules;
    private IRule currRules;
    private RealTimeRunManager owner;
    /**
     * Lock to control access from multiple threads
     */
    protected ReentrantLock lock = new ReentrantLock();
    /**
     * Single Double Clues object is a rule but it needs to be aware of previous runs. Create this object at startup
     * Will need one for shorts when the clue code for this is written
     */
    protected SingleDoubleClueLongs singleDoubleClueL;

    /**
     * Constructor creates the rules state manager for the specified RealTimeRunManager. 
     * Builds the tree of rules and sets the initial state to RulesStateEnum.FIVESECBARONLY
     * A different set of rules apply to putups with type LONG compared to putups
     * of type SHORT so the type of putup wrapped by the RealTimeRunManager must be
     * specified
     * @param newType - The Putups type (LONGS or SHORTS)
     * @param newOwner - The RealTimeRunManager object wrapping the putup
     */
    public RulesStateManager(PutupTypeEnum newType, RealTimeRunManager newOwner) {
        this.type = newType;
        this.owner = newOwner;
        this.currState = RulesStateEnum.FIVESECBARONLY;
        this.rules = new EnumMap<RulesStateEnum, IRule>(RulesStateEnum.class);
        this.buildRules();
        this.changeRuleStateTo(currState);
    }

    /**
     * Tests the current set of rules in use for this putup to determine if it should transition to 
     * the next state (and rules set).
     * @return boolean true if a transition to the next rules set should occur, false otherwise
     */
    public boolean checkCurrentRules() {
        boolean result = false;
        if (null != currRules) {
            lock.lock();
            try {
                try {
                    result = this.currRules.runRule();
                } catch (LoadingAdditionalDataException ex) {
                    result = false;
                }
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Changes the rules currently in use by the RulesStateManager. All rules are 
     * cached in the EnumMap rules and the state enumeration that matches that rules set
     * is the key to the EnumMap. This will retrieve the rules set for the given state
     * from the EnumMap and store them into the currRules attribute as well as update
     * the currState attribute.
     * @param newState A RulesStateEnum defining the rules set to transition to.
     */
    public final void changeRuleStateTo(RulesStateEnum newState) {
        boolean result = false;
        if (null != newState) {
            lock.lock();
            //DEBUG CODE ONLY TO RECORD STATE CHANGE
            if (DEBUG) {
                String putupCode = this.owner.getMyPutup().getTickerCode();
                System.out.println(putupCode + " has changed state to " + newState.toString());
            }
            //DEBUG CODE ONLY TO RECORD STATE CHANGE
            try {
                if (this.rules.containsKey(newState)) {
                    IRule newRules = this.rules.get(newState);
                    if (null != newRules) {
                        this.currRules = newRules;
                        this.currState = newState;
                        result = true;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        if (!result) {
            try {
                throw new UnsupportedOperationException("Not supported yet. State: " + newState.toString());
            } catch (NullPointerException ex) {
                throw new UnsupportedOperationException("Cannot change to a NULL state");
            }
        } else {
            //Change of state succeeded run any state specific code
            this.runCodeForNewState();
        }
    }

    /**
     * This method defines the rules that apply in any given state. A rules set 
     * must be defined for every possible RulesStateEnum value
     */
    private void buildRules() {
        for (RulesStateEnum currItem : RulesStateEnum.values()) {
            switch (currItem) {
                //This is the initial STATE and represents STATE 1 and STATE 2 of Bryns instructions
                case FIVESECBARONLY:
                    ThreeMLRanging threeMRangeRule = new ThreeMLRanging();
                    FTGBreachPoint ftgBreachRule = new FTGBreachPoint();
                    switch (this.type) {
                        case LONGS:
                            threeMRangeRule = new ThreeMLRangingClueLongs();
                            ftgBreachRule = new FTGBreachPointClueLong();
                            break;
                        case SHORTS:
                            threeMRangeRule = new ThreeMLRangingClueShorts();
                            ftgBreachRule = new FTGBreachPointClueShort();
                            break;
                    }
                    //Both of the above rules must be passed together group them into a RulesContainer
                    RulesContainer cont = new RulesContainer();
                    cont.addSubRule(ftgBreachRule);
                    cont.addSubRule(threeMRangeRule);
                    //Store into rules list
                    this.rules.put(RulesStateEnum.FIVESECBARONLY, cont);
                    break;
                case FIVESECBARPLUSMARKETDATA:
                    //STATE 3: In this state the program will be receiving 5 Sec Bars and Real Time Market data as well as
                    //scanning for a Single/Double pattern on these two graphs. If one is found then change to the STATE 4
                    //Testing for Single / Double Pattern state IF it has not been seen before
                    switch (this.type) {
                        case LONGS:
                            if (null == this.singleDoubleClueL) {
                                this.singleDoubleClueL = new SingleDoubleClueLongs(this.owner.getGraph5SecBars(), this.owner.getGraphReqMarketData());
                            }
                            this.rules.put(RulesStateEnum.FIVESECBARPLUSMARKETDATA, this.singleDoubleClueL);
                            break;
                        case SHORTS:
                            //Clue version of Single Double pattern check for SHORTS needs to be written for now always fail
                            AlwaysFailRule fail = new AlwaysFailRule();
                            this.rules.put(RulesStateEnum.FIVESECBARPLUSMARKETDATA, fail);
                            break;
                    }
                    break;
                case TESTINGSINGLEDOUBLE:
                    //At this point the Five Sec Bar Plus market data rules still apply but another thread will be running to decide if round 1 is complete
                    //From the running rules point of view their is no change so use the FIVESECBARPLUSMARKETDATA rules.
                    AlwaysFailRule fail = new AlwaysFailRule();
                    switch (this.type) {
                        case LONGS:
//                            this.rules.put(RulesStateEnum.TESTINGSINGLEDOUBLE, fail);
                            if (null == this.singleDoubleClueL) {
                                this.singleDoubleClueL = new SingleDoubleClueLongs(this.owner.getGraph5SecBars(), this.owner.getGraphReqMarketData());
                            }
                            this.rules.put(RulesStateEnum.TESTINGSINGLEDOUBLE, this.singleDoubleClueL);
                            break;
                        case SHORTS:
                            //Clue version of Single Double pattern check for SHORTS needs to be written for now always fail
                            this.rules.put(RulesStateEnum.TESTINGSINGLEDOUBLE, fail);
                            break;
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("Not supported yet. State: " + currItem.toString());
            }
        }
    }

    /**
     * @return the currState
     */
    public RulesStateEnum getCurrState() {
        return currState;
    }

    /**
     * Each rule will need to know the RealTimeRunManager that wraps the putup 
     * that it is to be applied to. This method associates the same RealTimeRunManager
     * with every rule in the RulesStateManager
     * @param newManager - A RealTimeRunManager that will be using the rules set.
     */
    public void setRealTimeRunManager(RealTimeRunManager newManager) {
        if (null != newManager && null != this.rules) {
            lock.lock();
            try {
                //set this as the manager for every rules set
                Set<RulesStateEnum> keySet = this.rules.keySet();
                for (RulesStateEnum currKey : keySet) {
                    IRule aRulesSet = this.rules.get(currKey);
                    aRulesSet.setRealTimeRunManager(newManager);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * This function should contain code that should be run depending on the state changed to.
     * NB the change to a new rules set is achieved by changeToRuleStateTo and 
     * this method is then invoked so the new rules will be in effect, as will the new state
     * 
     * Use this method to contain code which should run each time a specific state is entered
     * NB: Not thread safe so if thread safety is required you must provide the synchronisation code
     */
    private void runCodeForNewState() {
        switch(this.currState){
            case TESTINGSINGLEDOUBLE:
                //A new 'clue' single double has been found start a thread to update the historic graph
                //Perform FTG, 3M checks, confirm single double from historic data and ensure not IG'd
                TestRoundOneComplete task = new TestRoundOneComplete(this.owner);
                ExecutorService serv = DTConstants.THREAD_POOL;
                serv.submit(task);
                break;
        }
    }
}
