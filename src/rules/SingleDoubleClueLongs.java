/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DummyGraphPoint;
import daytrader.datamodel.ERETable;
import daytrader.datamodel.ERETableEntry;
import daytrader.datamodel.GraphFlatCollection;
import daytrader.datamodel.SingleDoublePattern;
import daytrader.interfaces.IGraphFlat;
import daytrader.utils.DTUtil;
import java.util.Calendar;
import java.util.NavigableSet;

/**
 * This class produces objects that give a graph will look for a 'single +
 * double' pattern in the data and produce an object that encapsulates its
 * details. This class is intended to scan TWO graphs and if a pattern is found
 * on either it will retrieve that pattern and pass the rules test. In addition
 * the following changes to the test have been made: 1) Ered checks for the
 * single and double tip have been removed 2) the retracement percentage has
 * been changed
 *
 * @author Roy
 */
public class SingleDoubleClueLongs extends SingleDoubleCheck {

    /**
     * The data graph storing the real time 1 sec market data bars for the putup being tested
     */
    protected BaseGraph<AbstractGraphPoint> reqMktDataGraph;
    /**
     * Any Single Double pattern found on the 5 Sec data bar graph
     */
    protected SingleDoublePattern pattern5SecBar;
    /**
     * Any Single Double pattern found on the real time 1 sec market data bar graph
     */
    protected SingleDoublePattern patternReqMkt;

    /**
     * Constructor accepting the two real time graphs that should be tested for a single 
     * double pattern clue
     * @param fiveSecBarGraph - the real time 5 sec market data bars for the putup being tested
     * @param reqMktDataGraph - the real time 1 sec market data bars for the putup being tested
     */
    public SingleDoubleClueLongs(BaseGraph<AbstractGraphPoint> fiveSecBarGraph, BaseGraph<AbstractGraphPoint> reqMktDataGraph) {
        super(fiveSecBarGraph);
        this.reqMktDataGraph = reqMktDataGraph;
    }

    @Override
    public SingleDoublePattern getSingleDoublePatternsOnGraph(BaseGraph<AbstractGraphPoint> graph) {
        SingleDoublePattern result = null;
        if (null != graph && 2 < graph.size()) {                                  //Graph must have at least 3 points
            //Define 'NOW' point
            AbstractGraphPoint now = graph.last();
            //Subset the graph from the lowest point of the day to its end
            AbstractGraphPoint lowestPointSoFar = graph.getLowestPointSoFar();
            NavigableSet<AbstractGraphPoint> subSet = graph.subSet(lowestPointSoFar, true, graph.last(), true);
            BaseGraph<AbstractGraphPoint> graphToUse = new BaseGraph<AbstractGraphPoint>(subSet);
            graphToUse.setPutup(graph.getPutup());
            graphToUse.setPrevDayClose(graph.getPrevDayClose());
            //Ok we now have a graph from the lowest point of the day to the end of the day
            //Find the single tip point
            AbstractGraphPoint singleTip = graphToUse.getHighestPointSoFar();
            //Obtain list of flats that apply to this graph
//            GraphFlatCollection<IGraphFlat> allFlats = DTUtil.findAllFlats(graphToUse);
//            //Determine if single tip lies on a flat and store start of single tip flat if applicable
//            IGraphFlat singleTipFlat = allFlats.getFlatFromPoint(singleTip);
            AbstractGraphPoint startSingleTip = graphToUse.getEarliestHigh();;
            AbstractGraphPoint endSingleTip = singleTip;
//            if (null != singleTipFlat) {
//                startSingleTip = singleTipFlat.getEarliestPoint();
//                endSingleTip = singleTipFlat.getLatestPoint();
//            }
            BaseGraph<AbstractGraphPoint> pGraph = new BaseGraph<AbstractGraphPoint>(AbstractGraphPoint.PriceComparator);
            pGraph.addAll(graph);
            DummyGraphPoint testEnd = new DummyGraphPoint(lowestPointSoFar.getTimestamp(), lowestPointSoFar.getLastPrice());
            Calendar exchOT = DTUtil.getExchOpeningTimeFromPoint(lowestPointSoFar);
            DummyGraphPoint testStart = new DummyGraphPoint(exchOT.getTimeInMillis(), lowestPointSoFar.getLastPrice());
            NavigableSet<AbstractGraphPoint> lowestSubSet = pGraph.subSet(testStart, true, testEnd, true);
            AbstractGraphPoint earliestLow = lowestSubSet.first();
            ERETable ereT = new ERETable();
            //Look up the relevent ERE Table entry for 'NOW' (rolling now) then test is the endSingleTip is ere'd 
            //At the time the high ends (endSingleTip) look up the relevent ERE Table entry and calculate the
            //maximum time seperation
            ERETableEntry ereEntry = ereT.getApplicableEntry(now);
            double ereMS = ereEntry.getSingleEREMilliseconds(endSingleTip.getMSElapsedSinceStartOfTrading());
            double maxSingleEreTime = earliestLow.getTimestamp() + ereMS;
            //Do single ere test
            //FOR THIS CLUE IMPLEMENTATION WE ARE REMOVING THE ERE'D TESTS SO THIS IF IS COMMENTED OUT
            //if (endSingleTip.getTimestamp() < maxSingleEreTime) {
            //The single is not ere'd proceed to look for a double
            AbstractGraphPoint ereDoubleStartTime = startSingleTip;
            //Calculate price retracement
            double highPrice = endSingleTip.getLastPrice();
            double priceDiff = highPrice - lowestPointSoFar.getLastPrice();
            //THIS LINE NEEDS TO BE CHANGED SO THAT THE RETRACEMENT IS NOT 50% BUT (50% - (tenth of a perc of the NOW PRICE))
            //double dblRetracePrice = highPrice - (priceDiff * SingleDoublePattern.RETRACEMENT);
            //NEW CODE TO REDEFINE THE RETRACEMENT AMOUNT - START
            double dblNowPrice = now.getLastPrice();
            double tenthNow = dblNowPrice * 0.001;
            double retracePerc = ((priceDiff * SingleDoublePattern.RETRACEMENT) - tenthNow) / priceDiff;
            double dblRetracePrice = highPrice - (priceDiff * retracePerc);
            //NEW CODE TO REDEFINE THE RETRACEMENT AMOUNT - END
            //Find the first instance AFTER the single tip where the price falls to equal to or less than the retracement price
            //Subset the graph
            NavigableSet<AbstractGraphPoint> subSet1 = graph.subSet(endSingleTip, true, graph.last(), true);
            BaseGraph<AbstractGraphPoint> postSingleGraph = new BaseGraph<AbstractGraphPoint>(subSet1);
            //Is ANY point lower or equal to the retracement price
            if (postSingleGraph.getLowestPointSoFar().getLastPrice() <= dblRetracePrice) {
                //A lower point exists this is our double tip (bar a flat)
                AbstractGraphPoint doubleTip = postSingleGraph.getLowestPointSoFar();
                //Now determine if the doubleTip is on a flat
                GraphFlatCollection<IGraphFlat> dtFlats = DTUtil.findAllFlats(postSingleGraph);
                IGraphFlat dtFlat = dtFlats.getFlatFromPoint(doubleTip);
                AbstractGraphPoint startDTFlat = doubleTip;
                AbstractGraphPoint endDTFlat = doubleTip;
                if (null != dtFlat) {
                    startDTFlat = dtFlat.getEarliestPoint();
                    endDTFlat = dtFlat.getLatestPoint();
                }
                //Start and end of the double tip are now defined. Test to ensure double is not ere'd
                double dblEreMS = ereEntry.getSingleEREMilliseconds(endDTFlat.getMSElapsedSinceStartOfTrading());
                double maxDoubleEreTime = ereDoubleStartTime.getTimestamp() + dblEreMS;
                //FOR THIS CLUE IMPLEMENTATION WE ARE REMOVING THE ERE'D TESTS SO THIS IF IS COMMENTED OUT
                //if (endDTFlat.getTimestamp() < maxDoubleEreTime) {
                //Before accepting this pattern the double tip price MUST BE lower than yesterdays close
                if (endDTFlat.getLastPrice() < graph.getPrevDayClose().getLastPrice()) {
                    //We have a completed Single Double pattern prepare the result.
                    SingleDoublePattern item = new SingleDoublePattern(
                            graphToUse,
                            now,
                            lowestPointSoFar,
                            earliestLow,
                            startSingleTip,
                            endSingleTip,
                            dblRetracePrice,
                            startDTFlat,
                            endDTFlat,
                            ereEntry);
                    if (!this.checkedPatterns.contains(item)) {
                        result = item;
                    }
                }
                //}
            }
            //}
        }
        if (null != result) {
            //We have a new Single Double Pattern log it as found and allow it to be returned.
            this.checkedPatterns.add(result);
        }
        return result;
    }

    @Override
    protected boolean isValid() {
        boolean result = false;
        if (null != this.myGraph && null != this.reqMktDataGraph) {
            result = true;
        }
        return result;
    }

    @Override
    protected boolean runPrimaryRule() {
        boolean result = false;
        if (this.isValid()) {
            this.pattern = null;
            this.pattern5SecBar = null;
            this.patternReqMkt = null;
            this.pattern5SecBar = this.getSingleDoublePatternsOnGraph(this.myGraph);
            this.patternReqMkt = this.getSingleDoublePatternsOnGraph(this.reqMktDataGraph);
            if (null != this.pattern5SecBar && null != this.patternReqMkt) {
                this.pattern = this.pattern5SecBar;
            } else if (null != this.pattern5SecBar) {
                this.pattern = this.pattern5SecBar;
            } else if (null != this.patternReqMkt) {
                this.pattern = this.patternReqMkt;
            }
            if(null != this.pattern){
                result = true;
            }
        }
        return result;
    }
}
