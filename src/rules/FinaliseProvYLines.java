/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.DatasetFinaliseYLines;
import daytrader.datamodel.GraphLine;
import daytrader.datamodel.Putup;
import daytrader.datamodel.RealTimeRunManager;
import daytrader.datamodel.RecursionCache;
import daytrader.interfaces.IGraphLine;
import daytrader.utils.DTUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * This class will construct an object that gives the FINAL Y-LINES to be used
 * until a new single double pattern is identified
 *
 * @author Roy
 */
public class FinaliseProvYLines {

    private ArrayList<IGraphLine> finalYLines;
    private Putup putupUsed;

    /**
     * Default Constructor initialises a new instance with an empty list of
     * 'finalised' Y-Lines.
     */
    public FinaliseProvYLines() {
        this.finalYLines = new ArrayList<IGraphLine>();
    }

    /**
     * Given a Putup this method takes the provisional Y-Lines and using the 
     * HISTORIC DATA GRAPH 'finalises them'. This involves deciding if the 
     * current provisional Y-Lines are still valid and whether any new ones
     * have come into existence. 
     * 
     * NB: YOU SHOULD HAVE UPDATED THEHISTORIC GRAPH TO THE MOST RECENT DATA AND
     * EACH SET OF FINALISED Y-LINES IS VALID ONLY FOR THE SINGLE DOUBLE PATTERN
     * THAT PROMPTED THE UPDATE TO THE HISTORIC GRAPH.
     * @param currPutup - The putup to finalise Y-Lines for
     */
    public void finaliseYLinesForPutup(Putup currPutup) {
        if (null != currPutup) {
            this.finalYLines = new ArrayList<IGraphLine>();
            this.putupUsed = currPutup;
            //Get the list of original Y-Line C's
            TreeSet<AbstractGraphPoint> yLineCs = this.putupUsed.getyLineCs();
            //Get the list of Y-Lines
            ArrayList<IGraphLine> initialYLines = this.putupUsed.getInitialYLines();
            //Compile the data into a DatasetFinaliseYLines Object and create a list of them
            TreeSet<DatasetFinaliseYLines> provTable = new TreeSet<DatasetFinaliseYLines>();
            for (AbstractGraphPoint origC : yLineCs) {
                IGraphLine assocLine = null;
                for (IGraphLine currLine : initialYLines) {
                    if (currLine.isACPoint(origC)) {
                        assocLine = currLine;
                        break;
                    }
                }
                if (null != assocLine) {
                    //We have found a line that uses this 'C' point store both together into the dataset
                    DatasetFinaliseYLines anEntry = new DatasetFinaliseYLines(origC, assocLine);
                    provTable.add(anEntry);
                } else {
                    //The current 'c' has no associated line just store the point
                    DatasetFinaliseYLines anEntry = new DatasetFinaliseYLines(origC);
                    provTable.add(anEntry);
                }
            }
            if (null != yLineCs && null != initialYLines && 0 < yLineCs.size() && 0 < provTable.size()) {
                //Cache the value of the highest WAP and the gradient of any associated line (or 0 if their is not one)
                //The natural ordering of DatasetFinaliseYLines is by WAP so the last entry will be the highest
                DatasetFinaliseYLines highestWAPData = provTable.last();
                //Generate the Higher highs list for todays data
                RealTimeRunManager runManager = this.putupUsed.getRunManager();
                if (null != runManager) {
                    BaseGraph<AbstractGraphPoint> graphHistData = runManager.getGraphHistoricData();
                    TreeSet<AbstractGraphPoint> lstHigherHighs = graphHistData.getHigherHighsFromLatestLowOfDay();
                    TreeSet<AbstractGraphPoint> lstHigerHighsWAPOrder = new TreeSet<AbstractGraphPoint>(AbstractGraphPoint.PriceComparator);
                    lstHigerHighsWAPOrder.addAll(lstHigherHighs);
                    if (0 < lstHigerHighsWAPOrder.size()) {
                        AbstractGraphPoint highestPointToday = lstHigerHighsWAPOrder.last();
                        if (!(highestPointToday.getWAP() >= highestWAPData.getOriginalCPoint().getWAP()) && 0 < lstHigherHighs.size()) {
                            //For each potential 'C' point determine if any provisional Y line is discarded, replaced or left unchanged. If no prov line exists test to see if a new Y-Line has been created
                            //Step 1 - Discard any 'C' point where its WAP is >= the WAP of the highest point of today
                            TreeSet<DatasetFinaliseYLines> survivorList = new TreeSet<DatasetFinaliseYLines>();
                            for (DatasetFinaliseYLines currCData : provTable) {
                                //Compare current WAP with highest WAP of the day. Is it higher?
                                if (currCData.getOriginalCPoint().getWAP() >= highestPointToday.getWAP()) {
                                    //This line will be unchanged, updated or a new Y-Line will come into existance
                                    survivorList.add(currCData);
                                }
                            }
                            //The survivorList now contains the data for all 'C' points that might be involved in a final Y-Line
                            //Step 2
                            //Process for new Y-Lines, updates and unchanged Y-Lines
                            for (DatasetFinaliseYLines currCData : survivorList) {
                                //Generate a graphline from this C to every potential new 'E' and identify the line with the smallest gradient
                                AbstractGraphPoint cPoint = currCData.getOriginalCPoint();
                                BaseGraph<AbstractGraphPoint> graph;
                                if (null != currCData.getLine()) {
                                    BaseGraph<AbstractGraphPoint> tempGraph = currCData.getLine().getGraph().replicateGraph();
                                    tempGraph.addAll(lstHigherHighs);
                                    graph = tempGraph;
                                } else {
                                    BaseGraph<AbstractGraphPoint> tempGraph = new BaseGraph<AbstractGraphPoint>();
                                    tempGraph.setPutup(this.putupUsed);
                                    tempGraph.setTradingDays(DTConstants.TRADINGDAYSLASTWEEK);
                                    tempGraph.add(cPoint);
                                    tempGraph.addAll(lstHigherHighs);
                                    graph = tempGraph;
                                }
                                //Generate all lines that must be played off to find smallest gradient
                                //Their is a complication here in that the 'C' may not be the only point we need to treat as 'C'
                                //If the provision Y-Lines generation gave 'stand ins' for 'C' we need to include all possible
                                //stand ins as well as the original 'C'
                                TreeSet<AbstractGraphPoint> lstOfPotentialCs = new TreeSet<AbstractGraphPoint>();
                                //Always include 'original c'
                                lstOfPotentialCs.add(cPoint);
                                //Does this 'C' have a list of alternate 'C' points (i.e. the list of potential 'E's it might connect to from the recursion cache
                                RecursionCache recursionCache = cPoint.getRecursionCache();
                                if (null != recursionCache) {
                                    //We have a list of potential 'C' points add them all in
                                    lstOfPotentialCs.addAll(recursionCache.getAllEPointsInCache());
                                }
                                //Variable to hold list of generated lines
                                ArrayList<IGraphLine> arlGeneratedLines = new ArrayList<IGraphLine>();
                                //Now connect every 'potential C point' to every potential E point in todays data
                                for (AbstractGraphPoint currC : lstOfPotentialCs) {
                                    for (AbstractGraphPoint currE : lstHigherHighs) {
                                        IGraphLine aNewLine = new GraphLine(currC, currE, graph);
                                        aNewLine.setTradingDays(graph.getTradingDays());
                                        //Check gradient is non zero before adding it
                                        double gradient = aNewLine.getGradient();
                                        if (0 != gradient) {
                                            arlGeneratedLines.add(aNewLine);
                                        }
                                    }
                                }
                                //Now find the line with the smallest gradient from the generated lines
                                IGraphLine smallestGradient = null;
                                double dblSmallestGradient = 0;

                                //NEW CODE - START
                                //IF recursionCache is NULL then the 'simple code' can be used as their is no stand in ELSE we must deal with stand in
                                if (null == recursionCache) {
                                    //No stand Ins use the 'simple' code
                                    for (IGraphLine currLine : arlGeneratedLines) {
                                        if (null == smallestGradient) {
                                            smallestGradient = currLine;
                                            dblSmallestGradient = Math.abs(currLine.getGradient());
                                        } else {
                                            double currGradient = Math.abs(currLine.getGradient());
                                            if (currGradient < dblSmallestGradient) {
                                                smallestGradient = currLine;
                                                dblSmallestGradient = Math.abs(currLine.getGradient());
                                            }
                                        }
                                    }
                                } else {
                                    //Use the more complex Stand In code
                                    //Step 1 - Get a list of all points involved in the stand in 'loop'
                                    TreeSet<AbstractGraphPoint> lstRecursionPoints = recursionCache.getAllEPointsInCache();
                                    //Step 2 - Use the 'simple' code to find the lowest gradient of 'today'
                                    for (IGraphLine currLine : arlGeneratedLines) {
                                        if (null == smallestGradient) {
                                            smallestGradient = currLine;
                                            dblSmallestGradient = Math.abs(currLine.getGradient());
                                        } else {
                                            double currGradient = Math.abs(currLine.getGradient());
                                            if (currGradient < dblSmallestGradient) {
                                                smallestGradient = currLine;
                                                dblSmallestGradient = Math.abs(currLine.getGradient());
                                            }
                                        }
                                    }
                                    //At this point smallestGradient is the line from 'Today' - (no NOT right terminoligy as it starts at least 1 day back)
                                    //Alias'd smallestGradient data as 'todays' data so I can follow Bryn's spec
                                    IGraphLine todaysLine = smallestGradient;
                                    double dblTodaysGradient = dblSmallestGradient;
                                    //Get an in order iterator for the recursion cache
                                    Iterator<IGraphLine> cacheIter = recursionCache.getConsideredLines().iterator();
                                    IGraphLine currCachedLine;
                                    boolean didTodayWin = false;
                                    while (cacheIter.hasNext()) {
                                        currCachedLine = cacheIter.next();
                                        double dblCurrCacheLineGrad = Math.abs(currCachedLine.getGradient());
                                        //Is todays gradient less than the current gradient (i.e. does today 'win')
                                        if (dblTodaysGradient < dblCurrCacheLineGrad) {
                                            //Today has 'Won' the final line is the currCachedLine's 'C' and todays 'E' are the final line
                                            IGraphLine finalisedYLine = new GraphLine(currCachedLine.getCurrentC(), todaysLine.getCurrentE(), graph);
                                            finalisedYLine.setTradingDays(currCachedLine.getTradingDays());
                                            smallestGradient = finalisedYLine;
                                            dblSmallestGradient = Math.abs(finalisedYLine.getGradient());
                                            didTodayWin = true;
                                            break;
                                        }
                                    }
                                    //If we have not 'won' after checking all lines in the cache then the final line is 
                                    //'C' = lastCPointOfLoop
                                    //'E' = todaysLine.getCurrentE()
                                    if (!didTodayWin) {
                                        if (recursionCache.isValidTerminationLine()) {
                                            // The 'provisional Y line' in the cache will be the line
                                            IGraphLine finalisedYLine = recursionCache.getFinalLine();
                                            smallestGradient = finalisedYLine;
                                            dblSmallestGradient = Math.abs(finalisedYLine.getGradient());
                                        } else {
                                            //There is no 'provisional Y-Line' in the cache construct one from the last point in the list
                                            //and the 'E' of todays lowest gradient line
                                            //Create graph for new Y-Line (this is the cached Y-Line graph from the putup and todays historic data)
                                            BaseGraph<AbstractGraphPoint> yLineGraph = runManager.getMyPutup().getPreLoadedYLineGraph();
                                            BaseGraph<AbstractGraphPoint> newGraph = graphHistData.replicateGraph();
                                            newGraph.clear();
                                            newGraph.addAll(graphHistData);
                                            newGraph.addAll(yLineGraph);
                                            //Create new Y-Line
                                            IGraphLine finalisedYLine = new GraphLine(lstRecursionPoints.last(), todaysLine.getCurrentE(), newGraph);
                                            finalisedYLine.setTradingDays(todaysLine.getTradingDays());
                                            smallestGradient = finalisedYLine;
                                            dblSmallestGradient = Math.abs(finalisedYLine.getGradient());
                                        }

                                    }
                                    //At this point the smallestGradient contains the final Y Line to check against the provisional Y line
                                }
                                //NEW CODE - END

//                                //ORIGINAL CODE COMMENTED OUT RE-CODING THIS - START
//                                for (IGraphLine currLine : arlGeneratedLines) {
//                                    if (null == smallestGradient) {
//                                        smallestGradient = currLine;
//                                        dblSmallestGradient = Math.abs(currLine.getGradient());
//                                    } else {
//                                        double currGradient = Math.abs(currLine.getGradient());
//                                        if (currGradient < dblSmallestGradient) {
//                                            smallestGradient = currLine;
//                                            dblSmallestGradient = Math.abs(currLine.getGradient());
//                                        }
//                                    }
//                                }
                                //ORIGINAL CODE COMMENTED OUT RE-CODING THIS - END

                                //The variable smallestGradient now holds the line with the smallest gradient from todays 'E' point data
                                //Does the current 'C' point have an associated provision Y-Line?
                                if (null != currCData.getLine()) {
                                    //Yes it has a provisional Y-Line
                                    //Test if original gradient is smaller then the smallest gradient from todays data
                                    double originalGradient = Math.abs(currCData.getGradient());
                                    double newGradient = Math.abs(smallestGradient.getGradient());
                                    if (originalGradient < newGradient) {
                                        //Y-Line is unchanged
                                        this.finalYLines.add(currCData.getLine());
                                    } else {
                                        //Y-Line is updated
                                        this.finalYLines.add(smallestGradient);
                                    }
                                } else {
                                    //No it does not have a provisional Y-Line - A new Y-Line has come into existance (assuming smallestGradient is not null - posible if new Y-Line fails validation)
                                    //Construct a graph covering the entire time period of the Y-Line
                                    BaseGraph<AbstractGraphPoint> yLineGraph = DTUtil.buildYLinePlusHistDataGraph(runManager);
                                    smallestGradient.setGraph(yLineGraph);
                                    //As we have constructed a new line we must validate that their is a PB3+ point somewhere in it
                                    //If validation succeeds this will be our Y-Line otherwise there will be no Y-Line
                                    if (!DTUtil.hasPBPointBetween(smallestGradient, yLineGraph)) {
                                        //The constructed Y-Line is invalid there is no Y-Line
                                        smallestGradient = null;
                                        dblSmallestGradient = 0;
                                    }
                                    //If constructed Y-Line (smallestGradient) is not null it has been validated and it can be stored into final Y Lines
                                    if (null != smallestGradient) {
                                        this.finalYLines.add(smallestGradient);
                                    }
                                }
                            }
                        }
                    } else {
                        //No data for today (09:30:00) retain any existing provisional Y Lines
                        if (0 < initialYLines.size()) {
                            for (IGraphLine currLine : initialYLines) {
                                this.finalYLines.add(currLine);
                            }
                        }
                    }
                }
            }
        } else {
            this.finalYLines = new ArrayList<IGraphLine>();
            this.putupUsed = null;
        }
    }

    /**
     * Accessor Method to retrieve the final Y-Lines from this object
     * @return An ArrayList of IGraphLine objects that represent the finalised
     * Y-Lines.
     */
    public ArrayList<IGraphLine> getFinalYLines() {
        return finalYLines;
    }
}
