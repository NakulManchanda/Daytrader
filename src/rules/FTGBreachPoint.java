/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.AtrClassEnum;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.FTGData;
import daytrader.datamodel.FTGOverrideData;
import daytrader.datamodel.Putup;
import daytrader.utils.DTUtil;
import java.util.NavigableSet;

/**
 * Given a base Graph with an close point for the previous day set this
 * determines the first point at which a FTG breach occurs
 *
 * @param <T> Any Price / Time data point
 * @author Roy
 */
public class FTGBreachPoint<T extends AbstractGraphPoint> extends AbstractBaseRule {

    /**
     * DEBUG FLAG set to false in final production code
     */
    protected static final boolean DEBUG = false;
    /**
     * Boolean used to test if this rule is ever passed. If passed then it is passed for the rest of the day
     */
    protected boolean blnEverPassed = false;

    /**
     * This attribute will be used by the CLUE versions to subset the graph being examined so we only
     * check for a FTGBreach AFTER the given startPoint
     */
    protected AbstractGraphPoint startTime;

    /**
     * Test the provided graph to see if it can identify a point which 'breaches' the FTG rule
     * if found it returns the point.
     * @param data A graph of AbstractBasePoints 
     * @return The first point that breaches the FTG rule or NULL if no such point can be found
     */
    public AbstractGraphPoint getFTGBreachPoint(BaseGraph<T> data) {
        AbstractGraphPoint result = null;
        //Test for FTG Breach without override
        if (null != data && 0 < data.size()) {
            if (null != data.getPutup()) {
                Putup putup = data.getPutup();
                AbstractGraphPoint prevDayClose = data.getPrevDayClose();
                AtrClassEnum atrClass = putup.getAtrClass();
                if (null != prevDayClose && null != atrClass) {
                    Double closePD = prevDayClose.getClose();
                    FTGData ftgCalc = new FTGData();
                    Double ftgFraction = ftgCalc.getFTGFraction(atrClass);
                    Double limit = closePD - (closePD * ftgFraction);
                    AbstractGraphPoint lowestPoint = data.getLowestPointSoFar();
                    if (lowestPoint.getWAP() < limit) {
                        //breach occured find first breach
                        for (AbstractGraphPoint currPoint : data) {
                            if (currPoint.getWAP() < limit) {
                                result = currPoint;
                                break;
                            }
                        }
                    }
                }
//                if (null == result) {
//                    System.out.println("FTG Test Not satisfied");
//                } else {
//                    System.out.println("FTG Satisfied by point " + result.toString());
//                }
            }
            //Test for breach on FTG Override if no breach so far
            if (null != data.getPutup() && null == result) {
                //Get lowest & highest points
                T objHigh = data.getHighestPointSoFar();
                NavigableSet<T> subSet = data.subSet(objHigh, true, data.last(), true);
                BaseGraph<T> subGraph = new BaseGraph<T>(subSet);
                T objLow = subGraph.getLowestPointSoFar();
                FTGOverrideData calc = new FTGOverrideData();
                Putup putup = data.getPutup();
                AtrClassEnum atrClass = putup.getAtrClass();
                Double overrideFTGFraction = calc.getOverrideFTGFraction(atrClass);
                Double limit = objHigh.getLastPrice() - (objHigh.getLastPrice() * overrideFTGFraction);
//                System.out.println("Limit = " + limit + ", Low Price = " + objLow.getLastPrice() + " High point = " + objHigh.toString() + " Low Point = " + objLow.toString());
                if (limit >= objLow.getLastPrice()) {
                    result = objLow;
                }
            }
        }
        return result;
    }

    @Override
    protected boolean runPrimaryRule() throws LoadingAdditionalDataException {
        boolean result = false;
        if (null != this.owner) {
            BaseGraph<T> graph = (BaseGraph<T>) this.owner.getGraphHistoricData();
            if (null != graph) {
                AbstractGraphPoint ftgBreachPoint = this.getFTGBreachPoint(graph);
                if (!this.blnEverPassed) {
                    if (null != ftgBreachPoint) {
                        result = true;
                        this.blnEverPassed = true;
                    }
                } else {
                    result = true;
                }
                if (DEBUG) {
                    if (result) {
                        this.printToConsole("Inside FTG Range for put up " + this.owner.getMyPutup().getTickerCode());
                    } else {
                        this.printToConsole("Outside FTG Range for put up " + this.owner.getMyPutup().getTickerCode());
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * The methods in this section are NOT used in the 'Real' FTG Test. HOWEVER they will be overridden, implemented and USED in the CLUE versions.
     * In this 'Real' version they are simply empty methods.
     * @param checkAfter 
     */
    protected void setConsiderGraphAfterTime(long checkAfter){
        long startTime = DTUtil.getExchOpeningTime().getTimeInMillis();
        //DTUtil.getEx
    }
}
