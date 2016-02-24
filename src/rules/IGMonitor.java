/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.AtrClassEnum;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.DummyGraphPoint;
import daytrader.datamodel.GraphFlatCollection;
import daytrader.datamodel.GraphFlatPair;
import daytrader.interfaces.IGraphFlat;
import daytrader.utils.DTUtil;
import java.util.Calendar;
import java.util.Iterator;
import java.util.NavigableSet;

/**
 * This rule implements an IG check on the provided graph object
 *
 * @author Roy
 */
public class IGMonitor {

    /**
     * A different set of IG fractions / percentages are used based on how long 
     * it has been since the start of trading. This class constant defines the first
     * of two such time zones
     */
    public static final int UE_ZONE = 3 * 60 * 1000;                            //Milliseconds - 09:30 to 09:33
    /**
     * A different set of IG fractions / percentages are used based on how long 
     * it has been since the start of trading. This class constant defines the second
     * of two such time zones
     */
    public static final int TRANS_ZONE = 3 * 60 * 1000;                         //Milliseconds - 09:33 to 09:36

    /**
     * Given a BaseGraph this method tests if the graph is IG'd or not
     * @param graph - The data graph to test
     * @return boolean True if the test FAILES AND THE GRAPH IS IG'd, False
     * if the graph is not IG'd
     */
    public boolean isIGd(BaseGraph<AbstractGraphPoint> graph) {
        boolean result = true;
        if (null != graph) {
            if (1 < graph.size()) {
                //Calculate end of UE time zone
                Calendar exchOT = DTUtil.getExchOpeningTimeFromGraph(graph);
                exchOT.add(Calendar.MILLISECOND, UE_ZONE);
                Calendar ueZone = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
                ueZone.setTimeInMillis(exchOT.getTimeInMillis());
                boolean isInUEZone = false;
                boolean isInTransZone = false;
                //Step 1 get lowest point of day (need to know when NOW is so get that too & when graph starts)
                AbstractGraphPoint lowestPoint = graph.getLowestPointSoFar();
                AbstractGraphPoint last = graph.last();
                AbstractGraphPoint first = graph.first();
                //Set UEZone Flag
                long ueTimestamp = exchOT.getTimeInMillis();
                if (last.getTimestamp() <= ueTimestamp) {
                    isInUEZone = true;
                }
                //Set Trans zone flag
                exchOT.add(Calendar.MILLISECOND, TRANS_ZONE);
                Calendar transZone = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
                transZone.setTimeInMillis(exchOT.getTimeInMillis());
                long transTimestamp = transZone.getTimeInMillis();
                if ((last.getTimestamp() <= exchOT.getTimeInMillis()) && (!isInUEZone)) {
                    isInTransZone = true;
                }
                //If we are outside the UE zone limit graph to items after the UE Zone
                BaseGraph<AbstractGraphPoint> graphToUse = new BaseGraph<AbstractGraphPoint>();
                graphToUse.setPutup(graph.getPutup());
                if (!isInUEZone) {
                    AbstractGraphPoint dummyStart = new DummyGraphPoint(ueTimestamp);
                    //Is their a flat pair that straddles the 09:33 point?? If so we need to include data to start of the flat
                    GraphFlatPair flatPair = DTUtil.getFlatPairAroundTimePoint(ueTimestamp, graph);
                    if (null != flatPair) {
                        dummyStart = flatPair.getFirstFlat().getEarliestPoint();
                    }
                    NavigableSet<AbstractGraphPoint> subSet = graph.subSet(dummyStart, true, last, true);
                    graphToUse.addAll(subSet);
                    lowestPoint = graphToUse.getLowestPointSoFar();
                    last = graphToUse.last();
                    first = graphToUse.first();
                } else {
                    graphToUse.addAll(graph);
                }
                //Step 2 Calculate override price
                //AtrClassEnum atrClass = graph.getPutup().getAtrClass();

                if (isInUEZone) {
                    result = testIfGraphIGd(graph);
                } else if (isInTransZone) {
                    result = testIfGraphIGd(graphToUse);
                } else {
                    //If we get here its AFTER 09:36
                    BaseGraph<AbstractGraphPoint> graph0936 = new BaseGraph<AbstractGraphPoint>();
                    graph0936.setPutup(graph.getPutup());
                    AbstractGraphPoint nine36 = new DummyGraphPoint(exchOT.getTimeInMillis());
                    //Is their a flat pair that straddles the 09:36 point?? If so we need to include data to start of the flat
                    GraphFlatPair flatPair = DTUtil.getFlatPairAroundTimePoint(transTimestamp, graph);
                    if (null != flatPair) {
                        nine36 = flatPair.getFirstFlat().getEarliestPoint();
                    }
                    NavigableSet<AbstractGraphPoint> subSet = graph.subSet(nine36, true, last, true);
                    graph0936.addAll(subSet);
                    result = testIfGraphIGd(graph0936);
                    if (!result) {
                        BaseGraph<AbstractGraphPoint> threeMin = new BaseGraph<AbstractGraphPoint>();
                        threeMin.setPutup(graph.getPutup());
                        AbstractGraphPoint start = new DummyGraphPoint(ueTimestamp);
                        AbstractGraphPoint end = new DummyGraphPoint(transTimestamp);
                        //Is their a flat pair that straddles the 09:33 point?? If so we need to include data to start of the flat
                        flatPair = null;
                        flatPair = DTUtil.getFlatPairAroundTimePoint(ueTimestamp, graph);
                        if (null != flatPair) {
                            start = flatPair.getFirstFlat().getEarliestPoint();
                        }
                        NavigableSet<AbstractGraphPoint> subSet1 = graph.subSet(start, true, end, true);
                        threeMin.addAll(subSet1);
                        result = testAlternateTable(threeMin);
                    }
                }




//                AtrClassEnum atrClass = graphToUse.getPutup().getAtrClass();
//                IGFValueTable objIGValTable = new IGFValueTable();
//                Double igFraction = objIGValTable.getIGFraction(atrClass);
//                double dlbIGOverridePrice = (igFraction * lowestPoint.getLastPrice()) + lowestPoint.getLastPrice();
//                //Locate first point before 'THE LOW OF THE DAY' that that is greater than or equal too the price
//                //If no price is found go to the start of graph
//                AbstractGraphPoint startPoint = null;
//                //Subset the graph from start to low of the day
//                //NavigableSet<AbstractGraphPoint> toLowPoint = graph.subSet(first, true, lowestPoint, true);
//                NavigableSet<AbstractGraphPoint> toLowPoint = graphToUse.subSet(first, true, lowestPoint, true);
//                BaseGraph<AbstractGraphPoint> graphToLow = new BaseGraph<AbstractGraphPoint>(toLowPoint);
//                Iterator<AbstractGraphPoint> descIter = graphToLow.descendingIterator();
//                while (descIter.hasNext()) {
//                    startPoint = descIter.next();
//                    if (startPoint.getLastPrice() >= dlbIGOverridePrice) {
//                        break;
//                    }
//                }
//                if (startPoint != first) {
//                    //We are not at start of the graph we need to find the start of the flat before the current
//                    //startPoint and use this as the start point
//                    //GraphFlatCollection<IGraphFlat> allFlats = DTUtil.findAllFlats(graph);
//                    GraphFlatCollection<IGraphFlat> allFlats = DTUtil.findAllFlats(graphToUse);
//                    GraphFlatCollection<IGraphFlat> flatsToPoint = allFlats.getFlatsToPoint(startPoint);
//                    if (0 < flatsToPoint.size()) {
//                        IGraphFlat flatBeforeOverridePoint = flatsToPoint.last();
//                        startPoint = flatBeforeOverridePoint.getEarliestPoint();
//                    }
//                }
//                //The graph to use for IG scan is defined as the portion of the original graph between
//                //the startPoint and 'NOW' - the 'last' point
//                //NavigableSet<AbstractGraphPoint> subSet = graph.subSet(startPoint, true, last, true);
//                NavigableSet<AbstractGraphPoint> subSet = graphToUse.subSet(startPoint, true, last, true);
//                BaseGraph<AbstractGraphPoint> objIGGraph = new BaseGraph<AbstractGraphPoint>(subSet);
//                GraphFlatCollection<IGraphFlat> flats = DTUtil.findAllFlats(objIGGraph);
//                //Step 5 - Get highest scoring flat
//                GraphFlatPair objHighestFlatPair = flats.getHighestScoringFlatPair();
//                if (null != objHighestFlatPair) {
//                    double score = objHighestFlatPair.getPairScore();
//                    //Step 6 - Use IGLimitEntry Table to test if this is IG'd
//                    IGLimitTable objIGLimitTable = new IGLimitTable();
//                    //Check on price NOT score
//                    IGLimitEntry limit = objIGLimitTable.getLimitFromPrice(dlbIGOverridePrice, isInTransZone);
//                    if (null != limit && score <= limit.getScore()) {
//                        result = false;
//                    }
//                }
            }
        }
        return result;
    }

    private boolean testIfGraphIGd(BaseGraph<AbstractGraphPoint> graph) {
        boolean result = true;
        if (null != graph) {
            if (1 < graph.size()) {
                AbstractGraphPoint lowestPoint = graph.getLowestPointSoFar();
                AbstractGraphPoint last = graph.last();
                AbstractGraphPoint first = graph.first();
                BaseGraph<AbstractGraphPoint> graphToUse = new BaseGraph<AbstractGraphPoint>();
                graphToUse.addAll(graph);
                graphToUse.setPutup(graph.getPutup());
                
                AtrClassEnum atrClass = graphToUse.getPutup().getAtrClass();
                IGFValueTable objIGValTable = new IGFValueTable();
                Double igFraction = objIGValTable.getIGFraction(atrClass);
                double dlbIGOverridePrice = (igFraction * lowestPoint.getLastPrice()) + lowestPoint.getLastPrice();
                //Locate first point before 'THE LOW OF THE DAY' that that is greater than or equal too the price
                //If no price is found go to the start of graph
                AbstractGraphPoint startPoint = null;
                //Subset the graph from start to low of the day
                //NavigableSet<AbstractGraphPoint> toLowPoint = graph.subSet(first, true, lowestPoint, true);
                NavigableSet<AbstractGraphPoint> toLowPoint = graphToUse.subSet(first, true, lowestPoint, true);
                BaseGraph<AbstractGraphPoint> graphToLow = new BaseGraph<AbstractGraphPoint>(toLowPoint);
                Iterator<AbstractGraphPoint> descIter = graphToLow.descendingIterator();
                while (descIter.hasNext()) {
                    startPoint = descIter.next();
                    if (startPoint.getLastPrice() >= dlbIGOverridePrice) {
                        break;
                    }
                }
                if (startPoint != first) {
                    //We are not at start of the graph we need to find the start of the flat before the current
                    //startPoint and use this as the start point
                    //GraphFlatCollection<IGraphFlat> allFlats = DTUtil.findAllFlats(graph);
                    GraphFlatCollection<IGraphFlat> allFlats = DTUtil.findAllFlats(graphToUse);
                    GraphFlatCollection<IGraphFlat> flatsToPoint = allFlats.getFlatsToPoint(startPoint);
                    if (0 < flatsToPoint.size()) {
                        IGraphFlat flatBeforeOverridePoint = flatsToPoint.last();
                        startPoint = flatBeforeOverridePoint.getEarliestPoint();
                    }
                }
                //The graph to use for IG scan is defined as the portion of the original graph between
                //the startPoint and 'NOW' - the 'last' point
                //NavigableSet<AbstractGraphPoint> subSet = graph.subSet(startPoint, true, last, true);
                NavigableSet<AbstractGraphPoint> subSet = graphToUse.subSet(startPoint, true, last, true);
                BaseGraph<AbstractGraphPoint> objIGGraph = new BaseGraph<AbstractGraphPoint>(subSet);
                GraphFlatCollection<IGraphFlat> flats = DTUtil.findAllFlats(objIGGraph);
                //Step 5 - Get highest scoring flat
                GraphFlatPair objHighestFlatPair = flats.getHighestScoringFlatPair();
                if (null != objHighestFlatPair) {
                    double score = objHighestFlatPair.getPairScore();
                    //Step 6 - Use IGLimitEntry Table to test if this is IG'd
                    IGLimitTable objIGLimitTable = new IGLimitTable();
                    //Check on price NOT score
                    IGLimitEntry limit = objIGLimitTable.getLimitFromPrice(dlbIGOverridePrice);
                    if (null != limit && score <= limit.getScore()) {
                        result = false;
                    }
                }
            }
        }
        return result;
    }

    private boolean testAlternateTable(BaseGraph<AbstractGraphPoint> graph) {
        boolean result = true;
        if (null != graph) {
            if (1 < graph.size()) {
                AbstractGraphPoint lowestPoint = graph.getLowestPointSoFar();
                AbstractGraphPoint last = graph.last();
                AbstractGraphPoint first = graph.first();
                BaseGraph<AbstractGraphPoint> graphToUse = new BaseGraph<AbstractGraphPoint>();
                graphToUse.addAll(graph);
                graphToUse.setPutup(graph.getPutup());

                AtrClassEnum atrClass = graphToUse.getPutup().getAtrClass();
                IGFValueTable objIGValTable = new IGFValueTable();
                Double igFraction = objIGValTable.getIGFraction(atrClass);
                double dlbIGOverridePrice = (igFraction * lowestPoint.getLastPrice()) + lowestPoint.getLastPrice();
                //Locate first point before 'THE LOW OF THE DAY' that that is greater than or equal too the price
                //If no price is found go to the start of graph
                AbstractGraphPoint startPoint = null;
                //Subset the graph from start to low of the day
                //NavigableSet<AbstractGraphPoint> toLowPoint = graph.subSet(first, true, lowestPoint, true);
                NavigableSet<AbstractGraphPoint> toLowPoint = graphToUse.subSet(first, true, lowestPoint, true);
                BaseGraph<AbstractGraphPoint> graphToLow = new BaseGraph<AbstractGraphPoint>(toLowPoint);
                Iterator<AbstractGraphPoint> descIter = graphToLow.descendingIterator();
                while (descIter.hasNext()) {
                    startPoint = descIter.next();
                    if (startPoint.getLastPrice() >= dlbIGOverridePrice) {
                        break;
                    }
                }
                if (startPoint != first) {
                    //We are not at start of the graph we need to find the start of the flat before the current
                    //startPoint and use this as the start point
                    //GraphFlatCollection<IGraphFlat> allFlats = DTUtil.findAllFlats(graph);
                    GraphFlatCollection<IGraphFlat> allFlats = DTUtil.findAllFlats(graphToUse);
                    GraphFlatCollection<IGraphFlat> flatsToPoint = allFlats.getFlatsToPoint(startPoint);
                    if (0 < flatsToPoint.size()) {
                        IGraphFlat flatBeforeOverridePoint = flatsToPoint.last();
                        startPoint = flatBeforeOverridePoint.getEarliestPoint();
                    }
                }
                //The graph to use for IG scan is defined as the portion of the original graph between
                //the startPoint and 'NOW' - the 'last' point
                //NavigableSet<AbstractGraphPoint> subSet = graph.subSet(startPoint, true, last, true);
                NavigableSet<AbstractGraphPoint> subSet = graphToUse.subSet(startPoint, true, last, true);
                BaseGraph<AbstractGraphPoint> objIGGraph = new BaseGraph<AbstractGraphPoint>(subSet);
                GraphFlatCollection<IGraphFlat> flats = DTUtil.findAllFlats(objIGGraph);
                //Step 5 - Get highest scoring flat
                GraphFlatPair objHighestFlatPair = flats.getHighestScoringFlatPair();
                if (null != objHighestFlatPair) {
                    double score = objHighestFlatPair.getPairScore();
                    //Step 6 - Use IGLimitEntry Table to test if this is IG'd
                    IGLimitTable objIGLimitTable = new IGLimitTable();
                    //Check on price NOT score
                    IGLimitEntry limit = objIGLimitTable.getAlternateLimitFromPrice(dlbIGOverridePrice);
                    if (null != limit && score <= limit.getScore()) {
                        result = false;
                    }
                }
            }
        }
        return result;
    }
}
