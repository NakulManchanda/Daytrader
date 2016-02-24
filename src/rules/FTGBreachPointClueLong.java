/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.AtrClassEnum;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.FTGData;
import daytrader.datamodel.FTGOverrideData;
import daytrader.datamodel.Putup;
import java.util.NavigableSet;
import static rules.FTGBreachPoint.DEBUG;

/**
 * This is the FTGBreach point code to use for the 5 sec bars (LONGS)
 * @param <T> - Any Price / Time data point
 * @author Roy
 */
public class FTGBreachPointClueLong<T extends AbstractGraphPoint> extends FTGBreachPoint<T> {
    
    @Override
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
//                    System.out.println("Limit was : " + limit);
                    AbstractGraphPoint lowestPoint = data.getLowestPointSoFar();
                    //if (lowestPoint.getWAP() < limit) {
                    if (lowestPoint.getLow() < limit) {
                        //breach occured find first breach
                        for (AbstractGraphPoint currPoint : data) {
                            //if (currPoint.getWAP() < limit) {
                            if (currPoint.getLow() < limit) {
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
                //Double limit = objHigh.getLastPrice() - (objHigh.getLastPrice() * overrideFTGFraction);
                Double limit = objHigh.getLow() - (objHigh.getLow() * overrideFTGFraction);
//                System.out.println("Limit = " + limit + ", Low Price = " + objLow.getLow() + " High point = " + objHigh.toString() + " Low Point = " + objLow.toString());
//                if (limit >= objLow.getLastPrice()) {
//                    result = objLow;
//                }
                if (limit >= objLow.getLow()) {
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
            BaseGraph<T> graph = (BaseGraph<T>) this.owner.getGraph5SecBars();
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
}
