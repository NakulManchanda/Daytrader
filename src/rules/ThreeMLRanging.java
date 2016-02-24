/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.AtrClassEnum;
import daytrader.datamodel.AtrClassGrid;
import daytrader.datamodel.AtrClassValue;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.gui.DaytraderMainForm;
import java.util.Calendar;

/**
 * This class tests if the low point of the day for a given graph is inside the
 * 3ML range
 *
 * @author Roy
 */
public class ThreeMLRanging extends AbstractBaseRule {

    /**
     * Flag to indicate DEBUG mode is in operation
     */
    protected static final boolean DEBUG = false;

    /**
     * Tests the provided data graph to determine if the most recent Price / Time
     * point falls into the 3M Range
     * @param dataGraph - The data graph to test
     * @return boolean True if the graph is inside 3M Range, False otherwise.
     */
    public boolean isIn3MLRange(BaseGraph<AbstractGraphPoint> dataGraph) {
        boolean result = false;
        //Get 3MLPrice
        if (0 < dataGraph.size()) {
            int threeMlPrice = dataGraph.getPutup().getThreeMlPrice();
            AtrClassEnum atrClass = dataGraph.getPutup().getAtrClass();
            AtrClassGrid artData = DaytraderMainForm.atrData;
            AbstractGraphPoint lastPoint = dataGraph.last();
            AbstractGraphPoint fp = dataGraph.first();
            Calendar openTime = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
            openTime.setTimeInMillis(fp.getCalDate().getTimeInMillis());
            openTime.set(Calendar.HOUR_OF_DAY, DTConstants.EXCH_OPENING_HOUR);
            openTime.set(Calendar.MINUTE, DTConstants.EXCH_OPENING_MIN);
            openTime.set(Calendar.SECOND, DTConstants.EXCH_OPENING_SEC);
            openTime.set(Calendar.MILLISECOND, 0);

            long rawCurr = lastPoint.getCalDate().getTimeInMillis();
            long rawstart = openTime.getTimeInMillis();
            long elapsedTime = rawCurr - rawstart;
            AtrClassValue atrClassValue = artData.getApplicableAtrClassValue(atrClass, elapsedTime);

            if (null != atrClassValue) {
                //Get AtrFraction (Scaled if needed)
                double dblAtrFract = atrClassValue.getAtrFractionAt(elapsedTime);
                //Determine upper and lower bounds using 3ML price
                double dblPlusMinus = threeMlPrice * dblAtrFract;
                double upperBound = threeMlPrice + dblPlusMinus;
                double lowerBound = threeMlPrice - dblPlusMinus;
                //Get the lowest point of the day so far
                AbstractGraphPoint lowPoint = dataGraph.getLowestPointSoFar();
                double lastPrice = lowPoint.getLastPrice();
                if (lastPrice <= upperBound && lastPrice >= lowerBound) {
                    result = true;
                }
            }
        }
        return result;
    }

    @Override
    protected boolean runPrimaryRule() throws LoadingAdditionalDataException {
        boolean result = false;
        if (null != this.owner) {
            BaseGraph<AbstractGraphPoint> graph = this.owner.getGraphHistoricData();
            if (null != graph) {
                result = this.isIn3MLRange(graph);
                if (DEBUG) {
                    if (result) {
                        this.printToConsole("Inside 3m Range for put up " + this.owner.getMyPutup().getTickerCode());
                    } else {
                        this.printToConsole("Outside 3m Range for put up " + this.owner.getMyPutup().getTickerCode());
                    }
                }
            }
        }
        return result;
    }
}
