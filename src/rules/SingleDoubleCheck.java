/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.DummyGraphPoint;
import daytrader.datamodel.ERETable;
import daytrader.datamodel.ERETableEntry;
import daytrader.datamodel.GraphFlatCollection;
import daytrader.datamodel.GraphLine;
import daytrader.datamodel.SingleDoublePattern;
import daytrader.interfaces.IGraphFlat;
import daytrader.utils.DTUtil;
import daytrader.utils.Timer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;

/**
 * This class produces objects that given a graph will look for a 'single +
 * double' pattern in the data and produce an object that encapsulates its
 * details.
 *
 * @author Roy
 */
public class SingleDoubleCheck extends AbstractBaseRule {

    /**
     * The Single Double pattern identified by this rule (if any)
     */
    protected SingleDoublePattern pattern;
    /**
     * The data graph that was checked for a Single Double Pattern
     */
    protected BaseGraph<AbstractGraphPoint> myGraph;
    /**
     * We need to maintain a list of all Single Double patterns identified and checked by this object
     */
    protected List<SingleDoublePattern> checkedPatterns;

    /**
     * Constructor accepting the data graph to search for a single double pattern
     * @param newGraph - The data graph to search for a single double pattern
     */
    public SingleDoubleCheck(BaseGraph<AbstractGraphPoint> newGraph) {
        this.myGraph = newGraph;
        this.checkedPatterns = new ArrayList<SingleDoublePattern>();
    }

    /**
     * Searches the provided data graph for the most recent single double pattern
     * @param graph - The data graph to search for a single double pattern
     * @return The most recent Single Double pattern on the graph OR NULL if none exists
     */
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
            if (endSingleTip.getTimestamp() < maxSingleEreTime) {
                //The single is not ere'd proceed to look for a double
                AbstractGraphPoint ereDoubleStartTime = startSingleTip;
                //Calculate price retracement
                double highPrice = endSingleTip.getLastPrice();
                double priceDiff = highPrice - lowestPointSoFar.getLastPrice();
                double dblRetracePrice = highPrice - (priceDiff * SingleDoublePattern.RETRACEMENT);
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
                    if (endDTFlat.getTimestamp() < maxDoubleEreTime) {
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
                    }
                }
            }
        }
        if (null != result) {
            this.checkedPatterns.add(result);
        }
        return result;
    }

    /**
     * Given a SingleDoublePattern this method retrieves the MpbValue associated with it
     * @param pattern - The pattern to use in determining the Mpb value
     * @return double being the Mpb Value for this SingleDoublePattern object
     */
    public static double getMpbValue(SingleDoublePattern pattern) {
        double result = 0d;
        if (null != pattern) {
            //Extract the graph
            BaseGraph<AbstractGraphPoint> graph = pattern.getGraph();
            //Restrict graph to the double ere range (start of single tip to end of double tip)
            NavigableSet<AbstractGraphPoint> subSet = graph.subSet(pattern.getSingleTipStart(), true, pattern.getDoubleTipEnd(), true);
            BaseGraph<AbstractGraphPoint> dataGraph = new BaseGraph<AbstractGraphPoint>(subSet);
            dataGraph.setPutup(graph.getPutup());
            dataGraph.setPrevDayClose(graph.getPrevDayClose());
            //Now loop through the dataGraph finding the PB for every point
            double intPbValue = 0;
            for (AbstractGraphPoint currPoint : dataGraph) {
                double foundPBValue = IdentifyPB3Points.getPBValue(dataGraph, currPoint);
                if (intPbValue < foundPBValue) {
                    intPbValue = foundPBValue;
                }
            }
            result = intPbValue;
        }
        return result;
    }

    /**
     * This function retrieves the value of 'I' give a maxPB, a Single Double
     * Pattern and the Graph that contains it.
     * @param maxPb - The max PB Value
     * @param graph - The Price / Time graph to be used in determining 'I'
     * @param pattern - The current Single Double Pattern that appears on the graph
     * @return Integer being the value for 'I' to be recorded when an entry is made.
     */
    public Integer testMaxPB(double maxPb, SingleDoublePattern pattern, BaseGraph<AbstractGraphPoint> graph) {
        Integer i = -1;
        if (null != pattern && null != graph) {
            if (maxPb < 30) {
                Integer amtUp = 0;
                if (maxPb < 10) {
                    amtUp = 10;
                    double dtPrice = pattern.getDoubleTipEnd().getLastPrice();
                    dtPrice += amtUp;
                    BreachIRounding rounder = new BreachIRounding();
                    i = rounder.performRounding(dtPrice).intValue();
                } else if (maxPb < 20) {
                    amtUp = 20;
                    double dtPrice = pattern.getDoubleTipEnd().getLastPrice();
                    dtPrice += amtUp;
                    BreachIRounding rounder = new BreachIRounding();
                    i = rounder.performRounding(dtPrice).intValue();
                } else {
                    amtUp = 30;
                    double dtPrice = pattern.getDoubleTipEnd().getLastPrice();
                    dtPrice += amtUp;
                    BreachIRounding rounder = new BreachIRounding();
                    i = rounder.performRounding(dtPrice).intValue();
                }
                //i has been set
//                System.out.println("Value of i = " + i);
            } else {
                //IJ Line calculation
//                System.out.println("Doing ij line");
                //Create a graph from single tip end to double tip end
                NavigableSet<AbstractGraphPoint> subSet = graph.subSet(pattern.getSingleTipEnd(), true, pattern.getDoubleTipEnd(), true);
                BaseGraph<AbstractGraphPoint> graphToUse = new BaseGraph<AbstractGraphPoint>(subSet);
                graphToUse.setPutup(graph.getPutup());
                graphToUse.setPrevDayClose(graph.getPrevDayClose());

                //Code from CE testing - Start
                IdentifyPB3Points<AbstractGraphPoint> rule = new IdentifyPB3Points<AbstractGraphPoint>();
                List<AbstractGraphPoint> pointList = rule.performBPOperationOnData(graphToUse);
                //GeneratePotentialCELines ceGen = new GeneratePotentialCELines(graphToUse);
                //ArrayList<GraphLine> generateLines = ceGen.generateLines(pointList);
                ArrayList<GraphLine> generateLines = this.generateIJLines(pointList, graphToUse);
                if (0 < generateLines.size()) {
                    //Code from CE testing - End

                    //Clean Sheet Start
                    //Create a graph that contains the points that are part of the triple
                    NavigableSet<AbstractGraphPoint> subSetTriple = graph.subSet(pattern.getDoubleTipEnd(), false, graph.last(), true);
                    BaseGraph<AbstractGraphPoint> graphTriple = new BaseGraph<AbstractGraphPoint>(subSetTriple);
                    graphTriple.setPutup(graph.getPutup());
                    graphTriple.setPrevDayClose(graph.getPrevDayClose());
                    for (AbstractGraphPoint currPoint : graphTriple) {
                        //Find highest CE Line at the current point
                        AbstractGraphPoint highestPoint = null;
                        GraphLine highestLine = null;
                        for (GraphLine currLine : generateLines) {
                            AbstractGraphPoint pointAtTime = currLine.getPointAtTime(currPoint.getTimestamp());
                            if (null != highestPoint) {
                                if (highestPoint.getLastPrice() < pointAtTime.getLastPrice()) {
                                    highestPoint = pointAtTime;
                                    highestLine = currLine;
                                }
                            } else {
                                highestPoint = pointAtTime;
                                highestLine = currLine;
                            }
                        }
                        //Now we have identified the highest line at currPoint on the graph
                        //Now test for a breach of at least 10th of a cent
                        double y = highestPoint.getLastPrice();
                        double breachPrice = (y + (y / 1000));                      //A thenth of a percent
                        if (currPoint.getLastPrice() > breachPrice) {
                            //Line breach calc provisional i 
                            BreachIRounding rounder = new BreachIRounding();
                            int provI = rounder.performRounding(breachPrice).intValue();
                            //Determine if 'currPoint' has breached i
                            if (currPoint.getLastPrice() >= provI) {
                                i = provI;
                                break;
                            }
                        }
                    }
                    //Clean sheet End
                }
            }
        }
        return i;
    }

    @Override
    protected boolean runPrimaryRule() {
        boolean result = false;
        if (this.isValid()) {
            this.pattern = null;
            this.pattern = this.getSingleDoublePatternsOnGraph(this.myGraph);
            if (null != this.pattern) {
                result = true;
            }
        }
        return result;
    }

    /**
     * @return the pattern
     */
    public SingleDoublePattern getPattern() {
        return pattern;
    }

    /**
     * @return the myGraph
     */
    public BaseGraph<AbstractGraphPoint> getMyGraph() {
        return myGraph;
    }

    /**
     * Tests that a data graph has been defined for this object
     * @return boolean True if the data graph attribute is set, False otherwise.
     */
    protected boolean isValid() {
        boolean result = false;
        if (null != this.myGraph) {
            result = true;
        }
        return result;
    }

    /**
     * This method generates the IJ Lines used in checking the Single Double Pattern
     * @param points - The list of points that might potentially form an I-J line
     * @param graph - The Data Graph being checked for a single double pattern
     * @return An ArrayList of GraphLine objects representing the IJ Lines
     */
    public ArrayList<GraphLine> generateIJLines(List<AbstractGraphPoint> points, BaseGraph<AbstractGraphPoint> graph) {
        ArrayList<GraphLine> result = new ArrayList<GraphLine>();
        if (null != points && null != graph && 1 < points.size()) {
            ArrayList<GraphLine> tempStore = new ArrayList<GraphLine>();
            for (int i = 0; i < points.size(); i++) {
                AbstractGraphPoint mainPoint = points.get(i);
                for (int j = i + 1; j < points.size(); j++) {
                    AbstractGraphPoint secondPoint = points.get(j);
                    if (mainPoint.getLastPrice() > secondPoint.getLastPrice()) {
                        GraphLine aLine = new GraphLine(mainPoint, secondPoint, graph);
                        tempStore.add(aLine);
                    }
                }
            }
            result = this.filterOutBlockedLines(tempStore, graph);
        }
        return result;
    }

    private ArrayList<GraphLine> filterOutBlockedLines(ArrayList<GraphLine> data, BaseGraph<AbstractGraphPoint> graph) {
        ArrayList<GraphLine> result = new ArrayList<GraphLine>();
        ArrayList<GraphLine> tempResult = new ArrayList<GraphLine>();
        if (null != data && data.size() > 0 && this.isValid()) {
            int count = 0;
            int max = data.size();
            AbstractGraphPoint startPoint;
            AbstractGraphPoint currPoint;
            boolean blnIncludeInResults = true;
            double priceAtTime = 0;
            for (GraphLine currLine : data) {
                startPoint = currLine.getStartPoint();
                NavigableSet<AbstractGraphPoint> subGraph = graph.subSet(startPoint, false, currLine.getEndPoint(), true);
                Iterator<AbstractGraphPoint> iterator = subGraph.iterator();
                blnIncludeInResults = true;
                while (iterator.hasNext()) {
                    currPoint = iterator.next();
                    priceAtTime = currLine.getPriceAtTime(currPoint.getTimestamp());
                    if (currPoint.getLastPrice() > priceAtTime) {
                        //Get PB value of this point
                        Timer.setBaseTime();
                        int intPBValue = IdentifyPB3Points.findPBValue(graph, currPoint);
                        Timer.printMsg("Finished (C) PB3 Calc");
                        if (intPBValue >= DTConstants.getScaledPBVALUE()) {
                            //This line is invalid, discard it and move to next
                            blnIncludeInResults = false;
                            break;
                        } else {
                            currLine.setStandInC(currPoint);
                        }
                    }
                }
                //Test to see if the currLine should be included in results
                if (blnIncludeInResults) {
                    tempResult.add(currLine);
                }
                count++;
//                System.out.println("Processed (C): " + count + " of " + max);
            }

            // We have found all blocks between c and e now find blocks between e and current time
            count = 0;
            max = tempResult.size();
            for (GraphLine currLine : tempResult) {
                NavigableSet<AbstractGraphPoint> subGraph = graph.subSet(currLine.getEndPoint(), false, graph.getLowestPointSoFar(), true);
                Iterator<AbstractGraphPoint> iterator = subGraph.iterator();
                blnIncludeInResults = true;
                while (iterator.hasNext()) {
                    currPoint = iterator.next();
                    priceAtTime = currLine.getPriceAtTime(currPoint.getTimestamp());
                    if (currPoint.getLastPrice() > priceAtTime) {
                        //Get PB value of this point
                        Timer.setBaseTime();
                        int intPBValue = IdentifyPB3Points.findPBValue(graph, currPoint);
                        Timer.printMsg("Finished (E) PB3 Calc");
                        if (intPBValue >= DTConstants.getScaledPBVALUE()) {
                            //This line is invalid, discard it and move to next
                            blnIncludeInResults = false;
                            break;
                        } else {
                            currLine.setStandInE(currPoint);
                        }
                    }
                }
                if (blnIncludeInResults) {
                    result.add(currLine);
                }
                count++;
//                System.out.println("Processed (E): " + count + " of " + max);
            }
        }
        return result;
    }
}
