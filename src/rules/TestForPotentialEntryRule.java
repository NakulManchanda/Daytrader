/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.AtrClassEnum;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.BsRangeEntry;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.DummyGraphPoint;
import daytrader.datamodel.GraphLine;
import daytrader.datamodel.GraphLinePlusCEFormulaeData;
import daytrader.datamodel.Putup;
import daytrader.datamodel.RealTimeRunManager;
import daytrader.datamodel.SingleDoublePattern;
import daytrader.interfaces.IGraphLine;
import java.util.ArrayList;

/**
 * AFTER ftg range, 3m range, single/double eres, and then IG functions, in that
 * order this rule test to determine if any potential entry can be made for the
 * given Single / double combination.
 *
 * It requires the Single / Double pattern to be tested and a running put up
 *
 * @author Roy
 */
public class TestForPotentialEntryRule extends AbstractBaseRule {

    private Putup putup;
    private SingleDoublePattern pattern;
    //Attributes to monitor if CE and Y-Line genration was completed
    private boolean blnCEDone;
    private boolean blnYLinesDone;
    private ArrayList<IGraphLine> finalCELines;
    private ArrayList<IGraphLine> finalYLines;
    private GeneratePotentialCELines ceGen;
    private FinaliseProvYLines yLineFinaliser;

    /**
     * Constructor to be functional this rule requires a Single / Double pattern
     * to have been identified and access to the RealTimeRunManager that
     * identified it.
     *
     * @param newPattern - A Single / Double pattern
     * @param newManager - RealTimeRunManager with the HISTORIC graph on which
     * the pattern appears
     */
    public TestForPotentialEntryRule(SingleDoublePattern newPattern, RealTimeRunManager newManager) {
        this.pattern = newPattern;
        this.owner = newManager;
        this.putup = this.owner.getMyPutup();
        this.blnCEDone = false;
        this.blnYLinesDone = false;
    }

    @Override
    protected boolean runPrimaryRule() throws LoadingAdditionalDataException {
        boolean result = false;
        if (this.isValid()) {
            //Check that finalised CE's and Y-Lines have been found
            if (!this.blnCEDone) {
                //Generation of C-E lines is not completed do this
                if (null == this.ceGen) {
                    this.ceGen = new GeneratePotentialCELines(this.owner.getGraphHistoricData());
                }
                this.ceGen.generateLines();
                this.finalCELines = this.ceGen.getFinalCELines();
                this.blnCEDone = true;
            }
            //Check that finalised Y-Lines are found
            if (!this.blnYLinesDone) {
                //Generation of final Y-Lines is not done do this
                if (null == this.yLineFinaliser) {
                    this.yLineFinaliser = new FinaliseProvYLines();
                }
                this.yLineFinaliser.finaliseYLinesForPutup(this.putup);
                this.finalYLines = this.yLineFinaliser.getFinalYLines();
                this.blnYLinesDone = true;
            }
            if (this.blnCEDone && this.blnYLinesDone) {
                //All finalised lines are availiable for this single / double pattern run check
                //for potential entry
                result = this.hasPotentialEntry();
            }
        }
        return result;
    }

    private boolean isValid() {
        boolean result = false;
        if (null != this.owner && null != this.pattern && null != this.putup) {
            result = true;
        }
        return result;
    }

    private boolean hasPotentialEntry() {
        boolean result = false;
        //Determine the total number of C-E and Y-Lines that exist
        int lineCount = this.finalCELines.size() + this.finalYLines.size();
        switch (lineCount) {
            case 0:
                //No lines means that we have a potential t1 entry
                result = true;
                break;
            default:
                //We have at least 1 line to consider process all lines to identify entries
                result = this.processAllLines();
        }
        return result;
    }

    private boolean processAllLines() {
        boolean result = false;
        BaseGraph<AbstractGraphPoint> graphHistoricData = this.owner.getGraphHistoricData();
        AbstractGraphPoint now = graphHistoricData.last();
        ArrayList<IGraphLine> ceThreatLines = new ArrayList<IGraphLine>();
        ArrayList<IGraphLine> yThreatLines = new ArrayList<IGraphLine>();
        ArrayList<IGraphLine> allThreatLines = new ArrayList<IGraphLine>();
        //At Bryn's suggestion I am processing the C-E Lines first
        if (0 < this.finalCELines.size()) {
            //Step 1
            //1a - Get the earliest low and the end of the single tip
            AbstractGraphPoint earliestLow = this.pattern.getEarliestLow();
            AbstractGraphPoint singleTipEnd = this.pattern.getSingleTipEnd();
            //1b Connect these points in a line
            IGraphLine eredLine = new GraphLine(earliestLow, singleTipEnd, graphHistoricData);
            eredLine.setTradingDays(this.owner.getGraphHistoricData().getTradingDays());
            //Step 2 Get the gradient of this line and generate a new line from "now" with the same gradient
            double eredLineGradient = eredLine.getGradient();
            //2a Generate line from now (I use a dummy end point 10,000 milliseconds into the future just to create a line
            //Work out WAP 10,000 ms into future and the timestamp
            int TIMEGAP = 10000;
            double endPrice = now.getLastPrice() + (TIMEGAP * eredLineGradient);
            long endTime = now.getTimestamp() + TIMEGAP;
            DummyGraphPoint dummyPoint = new DummyGraphPoint(endTime, endPrice);
            //2b Create the make believe line
            IGraphLine nowLine = new GraphLine(now, dummyPoint, graphHistoricData);
            //Get the low of the day we will need it
            AbstractGraphPoint lowestPointSoFar = graphHistoricData.getLowestPointSoFar();
            ArrayList<IGraphLine> bsThreatLines = new ArrayList<IGraphLine>();
            AtrClassEnum atrClass = this.putup.getAtrClass();
            //Get the relevent BSRangeEntry for this putups class
            BsRangeEntry BSRangeParameters = DTConstants.BSRANGES.getEntryForClass(atrClass);
            for (IGraphLine currCELine : this.finalCELines) {
                //Get the intersect point between the current CE Line and the 'nowLine'
                AbstractGraphPoint intersect = currCELine.getIntersect(nowLine);
                //Retrieve the price at the intersect
                double intersectWap = intersect.getWAP();
                //Step 3: Calc diff between intersect WAP and low of the day
                double dblWapDiff = intersectWap - lowestPointSoFar.getWAP();
                //Step 4: Express as a percentage of the low of the day
                double dblWapPerc = dblWapDiff / lowestPointSoFar.getWAP();
                //Step 5: Determine if ant BSRange 'Threat' lines exist
                //Test percentage against BSRangeParameters and entry if the dblWapPerc is <= range value we have a 'threat' line
                if (dblWapPerc <= BSRangeParameters.getBsPercentage()) {
                    //This is a 'Threat' Line
                    bsThreatLines.add(currCELine);
                }
            }

            //Test if Step 6 is required (only needed if NO threat lines are found)
            if (0 == bsThreatLines.size()) {
                //The complication - here is Bryns notes:
                /**
                 * in the special complication case (rare but needs to be
                 * coded!) that ALL CE LINES are outside bs range, then we need
                 * to potentially get new ce lines (not y-lines because there
                 * arent any more!), if there are any left from the original
                 * list .. that is the list of ce-lines with their y and x
                 * scores that you had before you applied the ce line selection
                 * formulae. To do this, go back to the original list of ce
                 * lines, remove those that have been shown to be out of bs
                 * range, and re-apply the ce line selection formulae to those
                 * that remain, if any (if there are no more than the whole
                 * function is complete and there is a potential t1 entry).
                 *
                 * Then take your new ce-line(s) (if any) and re-do the bs range
                 * test. If all are outside bs range then repeat step 6 and try
                 * again until you either get a ce line that is inside bs range,
                 * or you run out of ce lines.
                 *
                 * If you run out of ce lines then the function is complete and
                 * we have a potential t1 entry.
                 */
                //List of original C-E Lines and their formula data
                ArrayList<GraphLinePlusCEFormulaeData> ceFormulaDataCache = this.ceGen.getCeFormulaDataCache();
                //Eliminate all final CE Lines as these are known to be outside BS Range
                ceFormulaDataCache.removeAll(this.finalCELines);
                BSRangingResult resultFinder = new BSRangingResult(ceFormulaDataCache, nowLine, lowestPointSoFar, atrClass);
                while(!resultFinder.isCompleted()){
                    resultFinder.doNextProcessingRound();
                }
                //Either 1 or more threat lines where found or no lines are threats. If threat lines found add them to bsThreats
                if(resultFinder.threatsFound()){
                    ArrayList<GraphLinePlusCEFormulaeData> threatLines = resultFinder.getThreatLines();
                    bsThreatLines.addAll(threatLines);
                }
            }
            //From step 7 CE and Y lines are treated the same compile a list of 'threat' CE-Lines to store them while we do Y-Lines
            ceThreatLines.addAll(bsThreatLines);
        }
        //Now process the Y-Lines
        if (0 < this.finalYLines.size()) {
            //Step 1
            //1a - Get the earliest low and the end of the single tip
            AbstractGraphPoint earliestLow = this.pattern.getEarliestLow();
            AbstractGraphPoint singleTipEnd = this.pattern.getSingleTipEnd();
            //1b Connect these points in a line
            IGraphLine eredLine = new GraphLine(earliestLow, singleTipEnd, graphHistoricData);
            eredLine.setTradingDays(this.owner.getGraphHistoricData().getTradingDays());
            //Step 2 Get the gradient of this line and generate a new line from "now" with the same gradient
            double eredLineGradient = eredLine.getGradient();
            //2a Generate line from now (I use a dummy end point 10,000 milliseconds into the future just to create a line
            //Work out WAP 10,000 ms into future and the timestamp
            int TIMEGAP = 10000;
            double endPrice = now.getLastPrice() + (TIMEGAP * eredLineGradient);
            long endTime = now.getTimestamp() + TIMEGAP;
            DummyGraphPoint dummyPoint = new DummyGraphPoint(endTime, endPrice);
            //2b Create the make believe line
            IGraphLine nowLine = new GraphLine(now, dummyPoint, graphHistoricData);
            //Get the low of the day we will need it
            AbstractGraphPoint lowestPointSoFar = graphHistoricData.getLowestPointSoFar();
            ArrayList<IGraphLine> bsThreatLines = new ArrayList<IGraphLine>();
            AtrClassEnum atrClass = this.putup.getAtrClass();
            //Get the relevent BSRangeEntry for this putups class
            BsRangeEntry BSRangeParameters = DTConstants.BSRANGES.getEntryForClass(atrClass);
            for (IGraphLine currYLine : this.finalYLines) {
                //Get the intersect point between the current CE Line and the 'nowLine'
                AbstractGraphPoint intersect = currYLine.getIntersect(nowLine);
                //Retrieve the price at the intersect
                double intersectWap = intersect.getWAP();
                //Step 3: Calc diff between intersect WAP and low of the day
                double dblWapDiff = intersectWap - lowestPointSoFar.getWAP();
                //Step 4: Express as a percentage of the low of the day
                double dblWapPerc = dblWapDiff / lowestPointSoFar.getWAP();
                //Step 5: Determine if ant BSRange 'Threat' lines exist
                //Test percentage against BSRangeParameters and entry if the dblWapPerc is <= range value we have a 'threat' line
                if (dblWapPerc <= BSRangeParameters.getBsPercentage()) {
                    //This is a 'Threat' Line
                    bsThreatLines.add(currYLine);
                }
            }
            //Step 6 does not apply for Y-Lines.
            //From step 7 CE and Y lines are treated the same compile a list of 'threat' CE-Lines to store them while we do Y-Lines
            yThreatLines.addAll(bsThreatLines);
        }
        //Step 7 - Merge the CE and Y threat lines lists. From now on they will be treated in the same way
        allThreatLines.addAll(ceThreatLines);
        allThreatLines.addAll(yThreatLines);
        if (0 == allThreatLines.size()) {
            //We have a potential T1 entry
            result = true;
        } else {
            //Proceed into Step 7:
            double singleTipWap = this.pattern.getSingleTipEnd().getWAP();
            double discardPrice = singleTipWap - (singleTipWap * 0.001);
            long singleTipTime = this.pattern.getSingleTipEnd().getTimestamp();
            ArrayList<IGraphLine> discardedLines = new ArrayList<IGraphLine>();
            for (IGraphLine currLine : allThreatLines) {
                //Step 8: Price of each line at single tip time
                double priceAtSingleTipTime = currLine.getPriceAtTime(singleTipTime);
                //Step 9: discard lines below discardPrice
                if (priceAtSingleTipTime < discardPrice) {
                    discardedLines.add(currLine);
                }
            }
            allThreatLines.removeAll(discardedLines);
            //Step 10 - If no lines survive at this point we have a T1 entry
            if (0 == allThreatLines.size()) {
                //We may continue to a t1 entry
                result = true;
            } else {
                //Step 11: no t1 entry but a possible t2 entry IF all remaining lines price at the single tip time are lower than the single tip WAP
                boolean allLower = true;
                for (IGraphLine currLine : allThreatLines) {
                    double priceAtSingleTipTime = currLine.getPriceAtTime(singleTipTime);
                    if (priceAtSingleTipTime >= singleTipWap) {
                        //We have found 1 line that is equal to or higher than the single tip
                        //No entry is possible, no need to check any more lines
                        allLower = false;
                        break;
                    }
                }
                if (allLower) {
                    //We have a potential t2 entry
                    result = true;
                }
            }
        }
        return result;
    }

    private BSRangingResult applyBSRanging(IGraphLine nowLine, ArrayList<GraphLinePlusCEFormulaeData> newCELinesForBSRanging, AbstractGraphPoint lowestPointSoFar, AtrClassEnum atrClass) {
        BSRangingResult result = new BSRangingResult();
        if (null != nowLine && null != newCELinesForBSRanging && null != lowestPointSoFar && null != atrClass) {
            BsRangeEntry BSRangeParameters = DTConstants.BSRANGES.getEntryForClass(atrClass);
            for (GraphLinePlusCEFormulaeData currCELine : newCELinesForBSRanging) {
                //Get the intersect point between the current CE Line and the 'nowLine'
                AbstractGraphPoint intersect = currCELine.getIntersect(nowLine);
                //Retrieve the price at the intersect
                double intersectWap = intersect.getWAP();
                //Step 3: Calc diff between intersect WAP and low of the day
                double dblWapDiff = intersectWap - lowestPointSoFar.getWAP();
                //Step 4: Express as a percentage of the low of the day
                double dblWapPerc = dblWapDiff / lowestPointSoFar.getWAP();
                //Step 5: Determine if ant BSRange 'Threat' lines exist
                //Test percentage against BSRangeParameters and entry if the dblWapPerc is <= range value we have a 'threat' line
                if (dblWapPerc <= BSRangeParameters.getBsPercentage()) {
                    //This is a 'Threat' Line
                    result.addThreatLine(currCELine);
                }
            }
            //The threat
        }
        return result;

    }

    /**
     * This class encapsulates a BSRanging Test Result. The result holds the
     * following data 1) A list of identified threat lines 2) The remaining
     * GraphLinePlusCEFormulaeData lines to consider
     */
    private class BSRangingResult {

        private ArrayList<GraphLinePlusCEFormulaeData> threatLines;
        private ArrayList<GraphLinePlusCEFormulaeData> consideredLines;
        private ArrayList<GraphLinePlusCEFormulaeData> remainingLines;
        private IGraphLine nowLine;
        private AbstractGraphPoint lowestPointSoFar;
        private AtrClassEnum atrClass;

        private BSRangingResult() {
            this.threatLines = new ArrayList<GraphLinePlusCEFormulaeData>();
            this.consideredLines = new ArrayList<GraphLinePlusCEFormulaeData>();
            this.remainingLines = new ArrayList<GraphLinePlusCEFormulaeData>();
        }

        public BSRangingResult(ArrayList<GraphLinePlusCEFormulaeData> remainingLines, IGraphLine nowLine, AbstractGraphPoint lowestPointSoFar, AtrClassEnum atrClass) {
            this();
            this.remainingLines.addAll(remainingLines);
            this.nowLine = nowLine;
            this.lowestPointSoFar = lowestPointSoFar;
            this.atrClass = atrClass;
        }

        public void addThreatLine(GraphLinePlusCEFormulaeData aThreatLine) {
            if (null != aThreatLine) {
                this.threatLines.add(aThreatLine);
            }
        }

        public void addRemainingLines(ArrayList<GraphLinePlusCEFormulaeData> linesList) {
            if (null != linesList && 0 < linesList.size()) {
                this.remainingLines.addAll(linesList);
            }
        }

        public void doNextProcessingRound() {
            if (!this.isCompleted()) {
                //Remove last rounds considered lines and reset the lines to consider this round
                this.remainingLines.removeAll(this.consideredLines);
                this.consideredLines = new ArrayList<GraphLinePlusCEFormulaeData>();
                //Extract the lines to consider in this round
                ArrayList<GraphLinePlusCEFormulaeData> linesToConsider = GeneratePotentialCELines.applyCESelectionFormula(this.remainingLines);
                this.consideredLines.addAll(linesToConsider);
                BsRangeEntry BSRangeParameters = DTConstants.BSRANGES.getEntryForClass(this.atrClass);
                for (GraphLinePlusCEFormulaeData currCELine : this.consideredLines) {
                    //Get the intersect point between the current CE Line and the 'nowLine'
                    AbstractGraphPoint intersect = currCELine.getIntersect(nowLine);
                    //Retrieve the price at the intersect
                    double intersectWap = intersect.getWAP();
                    //Step 3: Calc diff between intersect WAP and low of the day
                    double dblWapDiff = intersectWap - this.lowestPointSoFar.getWAP();
                    //Step 4: Express as a percentage of the low of the day
                    double dblWapPerc = dblWapDiff / this.lowestPointSoFar.getWAP();
                    //Step 5: Determine if ant BSRange 'Threat' lines exist
                    //Test percentage against BSRangeParameters and entry if the dblWapPerc is <= range value we have a 'threat' line
                    if (dblWapPerc <= BSRangeParameters.getBsPercentage()) {
                        //This is a 'Threat' Line
                        this.threatLines.add(currCELine);
                    }
                }
            }
        }

        public boolean isCompleted() {
            boolean result = false;
            if (0 != this.threatLines.size()) {
                result = true;
            } else if (0 == this.remainingLines.size()) {
                result = true;
            }
            return result;
        }
        
        public boolean threatsFound(){
            boolean result = false;
            if(0 != this.threatLines.size()){
                result = true;
            }
            return result;
        }

        /**
         * Accessor method to retrieve the Graph Lines that remain a threat
         * @return An ArrayList of GraphLinePlusCEFormulaeData representing the 
         * threat lines.
         */
        public ArrayList<GraphLinePlusCEFormulaeData> getThreatLines() {
            return threatLines;
        }
    }
}
