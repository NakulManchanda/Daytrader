/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.Putup;
import daytrader.datamodel.RealTimeRunManager;

/**
 * This rule uses the current historical graph for a running put up and test to determine if
 * the graph is IG'd. To successfully pass this rule the graph must NOT be IG'd
 * @author Roy
 */
public class IGRuleCheck  extends AbstractBaseRule {
    
    private RealTimeRunManager runningPutup;
    private Putup putup;
    private BaseGraph<AbstractGraphPoint> historicGraph;
    private IGMonitor monitor;
    
    /**
     * Constructor accepting the RealTimeRunManager containing the HISTORIC data graph
     * to test the IG rule against.
     * @param aRunningPutup
     */
    public IGRuleCheck(RealTimeRunManager aRunningPutup){
        this.runningPutup = aRunningPutup;
        this.putup = this.runningPutup.getMyPutup();
        this.historicGraph = this.runningPutup.getGraphHistoricData();
        this.monitor = new IGMonitor();
    }

    @Override
    protected boolean runPrimaryRule() throws LoadingAdditionalDataException {
        boolean result = false;
        if(this.isValid()){
            //Test the historic Graph to see if it is IG'd
            boolean iGd = this.monitor.isIGd(this.historicGraph);
            if(!iGd){
                result = true;
            }
        }
        return result;
    }
    
    private boolean isValid(){
        boolean result = false;
        if(null != this.runningPutup && null != this.putup && null != this.historicGraph && null != this.monitor){
            //Historic Graph must have data
            if(0 < this.historicGraph.size()){
                result = true;
            }
        }
        return result;
    }
    
}
