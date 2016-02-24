/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.PointsCEFormulaData;
import daytrader.datamodel.TVL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class provides functions primarily used in determining whether a point should
 * be included as part of a CE Line
 * @param <T> - Any Price / Time data point
 * @author Roy
 */
public class IdentifyPB3Points<T extends AbstractGraphPoint> {

    /**
     * Given a graph of data this function determines all points that could
     * POTENTIALLY be a C or E point on a C-E Line (or I-J line) and returns them as a list of
     * points (in time order)
     * @param data - The Base data graph to examine for potential CE Points
     * @return A List of potential C-E points from the graph
     */
    public List<AbstractGraphPoint> performBPOperationOnData(BaseGraph<T> data) {
        //The operation is to retrieve a list of potential C and E points
        //Step 1 - Identify lowest price in the data set
        List<AbstractGraphPoint> result = new ArrayList<AbstractGraphPoint>();
        if (null != data) {
            //ORIGINAL CODE REPLACE BY CODE BELOW
            //Always treat the first point as a PB3 Point
            //result.add(data.first());
            //ORIGINAL CODE REPLACE BY CODE BELOW
            //NEW CODE Include First point where price is unchanged and is the latest point in time - START
            T first = data.first();
            Iterator<T> iter = data.iterator();
            T currentPoint = null;
            T prevPoint = first;
            while (iter.hasNext()) {
                currentPoint = iter.next();
                if (currentPoint != first) {
                    if (currentPoint.getLastPrice() != first.getLastPrice()) {
                        break;
                    }
                }
                prevPoint = currentPoint;
            }
            result.add(prevPoint);
            //NEW CODE Include First point where price is unchanged and is the latest point in time - END

            //Now proceed as before
            TreeSet<AbstractGraphPoint> tsTimeSorted = new TreeSet<AbstractGraphPoint>(AbstractGraphPoint.TimeComparator);
            tsTimeSorted.addAll(data);
            //First Time point of the data
            AbstractGraphPoint firstPoint = tsTimeSorted.first();
            TreeSet<AbstractGraphPoint> tsPriceSorted = new TreeSet<AbstractGraphPoint>(AbstractGraphPoint.PriceComparator);
            tsPriceSorted.addAll(data);
            //AbstractGraphPoint lowestPricePoint = tsPriceSorted.first();
            //AbstractGraphPoint lowestPricePoint = this.findLowestPricePoint(data);
            AbstractGraphPoint lowestPricePoint = data.getLowestPointSoFar();
//            System.out.println("Lowest Price Value on the graph " + lowestPricePoint.toString());
            //Limit data to the lowest point no points after this are possible
            NavigableSet<AbstractGraphPoint> setToLowestPrice = tsTimeSorted.subSet(firstPoint, true, lowestPricePoint, true);
            BaseGraph<AbstractGraphPoint> copyData = new BaseGraph<AbstractGraphPoint>(setToLowestPrice);
            //Add Putup, PrevDayClose, YLines and Trading days, and previous days opening data
            copyData.setPutup(data.getPutup());
            copyData.setPrevDayClose(data.getPrevDayClose());
            copyData.setYlines(data.getYlines());
            copyData.setTradingDays(data.getTradingDays());
            copyData.setGraphClosePrevDayData(data.getGraphClosePrevDayData());
            if (null != data.getYlineOneSecGraph()) {
                copyData.setYlineOneSecGraph(data.getYlineOneSecGraph());
            }
            Iterator<AbstractGraphPoint> descIterator = setToLowestPrice.descendingIterator();
            while (descIterator.hasNext()) {
                AbstractGraphPoint currPoint = descIterator.next();
                if (this.isPeakPoint(tsTimeSorted, currPoint)) {
                    if (DTConstants.getScaledPBVALUE() <= IdentifyPB3Points.findPBValue(copyData, currPoint)) {
                        result.add(currPoint);
                    }
                }
            }
//            System.out.println("Should have all points with a PB greater than or equal to 3");
            //Sort list on a time basis
            Collections.sort(result, AbstractGraphPoint.TimeComparator);
        }
        return result;
    }

    /**
     * Given a set of Price / Time points this function finds the lowest price point
     * @param graph - The list of Price / Time points 
     * @return The Price / Time point with the lowest price value
     */
    public AbstractGraphPoint findLowestPricePoint(SortedSet<T> graph) {
        AbstractGraphPoint lowestPricePoint = null;
        if (null != graph && 0 < graph.size()) {
            TreeSet<AbstractGraphPoint> tsPriceSorted = new TreeSet<AbstractGraphPoint>(AbstractGraphPoint.PriceComparator);
            tsPriceSorted.addAll(graph);
            lowestPricePoint = tsPriceSorted.first();
        }
        return lowestPricePoint;
    }

    /**
     * Test a Price / Time point to determine if its PB Value is AT LEAST DTConstants.getScaledPBVALUE() / 100d
     * @param data - the data graph to use when testing the PB Value
     * @param aPoint - A Price / Time point for which the PB Value is required
     * @return integer being the BP Value if less than DTConstants.getScaledPBVALUE() / 100d or
     * the value of DTConstants.getScaledPBVALUE() / 100d if it is greater than or equal to this value
     */
    public static int findPBValue(BaseGraph<AbstractGraphPoint> data, AbstractGraphPoint aPoint) {
        int result = 0;
        TreeSet<AbstractGraphPoint> tsTime = new TreeSet<AbstractGraphPoint>(AbstractGraphPoint.TimeComparator);
        //Also need to know the longest time between consecutive points
        long timeGap = 0;
        AbstractGraphPoint prevPoint = null;
        for (AbstractGraphPoint point : data) {
            tsTime.add(point);
            if (null != prevPoint) {
                long deltaTime = point.getTimestamp() - prevPoint.getTimestamp();
                if (timeGap < deltaTime) {
                    timeGap = deltaTime;
                }
            } else {
                prevPoint = point;
            }
        }
        Iterator<AbstractGraphPoint> descIter = tsTime.descendingIterator();
        boolean abort = false;
        double pbLimit = DTConstants.getScaledPBVALUE() / 100d;
        while (descIter.hasNext() && !abort) {
            AbstractGraphPoint currPoint = descIter.next();
            if (currPoint.getTimestamp() < aPoint.getTimestamp()) {
                if (currPoint.getLastPrice() > aPoint.getLastPrice()) {
                    //We abort cannot be a potential C/E point function will return 0 (its default)
                    abort = true;
                } else {
                    //Is this point less than the start point by PBValue
                    Double priceDiff = aPoint.getLastPrice() - currPoint.getLastPrice();
                    if (priceDiff >= pbLimit) {
                        //Point may be used calc and return the PB
                        priceDiff *= 100;
                        result = priceDiff.intValue();
                        abort = true;
                    }
                }
            }
        }
        if (!abort) {
            //If we did not abort from the above loop then we have failed to find a point higher
            //than the one requested and no point was lower by the required PB amount.
        }
        return result;
    }

    /**
     * This function finds the PBValue without using the PBLimit value
     * @param data - A BseGraph used to determine the PB Value of a point
     * @param aPoint - The Price / Time point for which the PB Value is required
     * @return double being the calculated PB value or 0 if no value can be determined
     */
    public static double getPBValue(BaseGraph<AbstractGraphPoint> data, AbstractGraphPoint aPoint) {
        double result = 0d;
        //Find first point in history where the price is higher than the point value
        NavigableSet<AbstractGraphPoint> subSet = data.subSet(data.first(), true, aPoint, true);
        BaseGraph<AbstractGraphPoint> graphToUse = new BaseGraph<AbstractGraphPoint>(subSet);
        graphToUse.setPutup(data.getPutup());
        graphToUse.setPrevDayClose(data.getPrevDayClose());
        Iterator<AbstractGraphPoint> descIter = graphToUse.descendingIterator();
        AbstractGraphPoint currPoint;
        AbstractGraphPoint firstHigher = null;
        while (descIter.hasNext()) {
            currPoint = descIter.next();
            if (currPoint != aPoint) {
                if (currPoint.getLastPrice() > aPoint.getLastPrice()) {
                    firstHigher = currPoint;
                    break;
                }
            }
        }
        if (null == firstHigher) {
            //Base the result on the lowest point of the graphToUse
            AbstractGraphPoint lowestPointSoFar = graphToUse.getLowestPointSoFar();
            result = aPoint.getLastPrice() - lowestPointSoFar.getLastPrice();
        } else {

            //BaseGraph<AbstractGraphPoint> graphToUse2 = new BaseGraph<AbstractGraphPoint>(subSet);
            NavigableSet<AbstractGraphPoint> subSet1 = data.subSet(firstHigher, true, aPoint, true);
            BaseGraph<AbstractGraphPoint> graphToUse2 = new BaseGraph<AbstractGraphPoint>(subSet1);
            graphToUse2.setPutup(data.getPutup());
            graphToUse2.setPrevDayClose(data.getPrevDayClose());
            AbstractGraphPoint lowestPointSoFar = graphToUse2.getLowestPointSoFar();
            result = aPoint.getLastPrice() - lowestPointSoFar.getLastPrice();
        }
        return result;
    }

    /**
     * Given a Graph and a point this function produces a PointsCEFormulaData
     * object that encapsulates the information you need to know about the point
     * to do the CE calculations.
     * @param data - The BaseGraph that contains the data point
     * @param aPoint - The Price / Time point for which the CEFormulaData is required
     * @return The PointsCEFormulaData object for the requested point.
     */
    public static PointsCEFormulaData getCEFormulaData(BaseGraph<AbstractGraphPoint> data, AbstractGraphPoint aPoint) {
        PointsCEFormulaData result = null;
        if (null != data && null != aPoint) {
            //Find first point in history where the price is higher than the point value
            NavigableSet<AbstractGraphPoint> subSet = data.subSet(data.first(), true, aPoint, true);
            BaseGraph<AbstractGraphPoint> graphToUse = new BaseGraph<AbstractGraphPoint>(subSet);
            graphToUse.setPutup(data.getPutup());
            graphToUse.setPrevDayClose(data.getPrevDayClose());
            Iterator<AbstractGraphPoint> descIter = graphToUse.descendingIterator();
            AbstractGraphPoint currPoint;
            AbstractGraphPoint firstHigher = null;
            while (descIter.hasNext()) {
                currPoint = descIter.next();
                if (currPoint != aPoint) {
                    if (currPoint.getLastPrice() > aPoint.getLastPrice()) {
                        firstHigher = currPoint;
                        break;
                    }
                }
            }
            //The First Higher point now contains the first point higher than aPoint's price value or NULL if none
            //Now work out PB Value and duration
            double pbVal;
            double duration;
            if (null == firstHigher) {
                //Base the result on the lowest point of the graphToUse
                AbstractGraphPoint lowestPointSoFar = graphToUse.getLowestPointSoFar();
                pbVal = aPoint.getLastPrice() - lowestPointSoFar.getLastPrice();
                //Now calculate the EARLIEST POINT = LOW PRICE
                double lowPrice = lowestPointSoFar.getLastPrice();
                Iterator<AbstractGraphPoint> ascIter = graphToUse.iterator();
                AbstractGraphPoint earliestLow = null;
                AbstractGraphPoint nextPoint = null;
                while (ascIter.hasNext()) {
                    nextPoint = ascIter.next();
                    if (lowPrice == nextPoint.getLastPrice()) {
                        earliestLow = nextPoint;
                        duration = aPoint.getTimestamp() - earliestLow.getTimestamp();
                        result = new PointsCEFormulaData(aPoint, pbVal, duration);
                        break;
                    }
                }
            } else {
                NavigableSet<AbstractGraphPoint> subSet1 = data.subSet(firstHigher, true, aPoint, true);
                BaseGraph<AbstractGraphPoint> graphToUse2 = new BaseGraph<AbstractGraphPoint>(subSet1);
                graphToUse2.setPutup(data.getPutup());
                graphToUse2.setPrevDayClose(data.getPrevDayClose());
                AbstractGraphPoint lowestPointSoFar = graphToUse2.getLowestPointSoFar();
                pbVal = aPoint.getLastPrice() - lowestPointSoFar.getLastPrice();
                //Now calculate the EARLIEST POINT = LOW PRICE
                double lowPrice = lowestPointSoFar.getLastPrice();
                Iterator<AbstractGraphPoint> ascIter = graphToUse2.iterator();
                AbstractGraphPoint earliestLow = null;
                AbstractGraphPoint nextPoint = null;
                while (ascIter.hasNext()) {
                    nextPoint = ascIter.next();
                    if (lowPrice == nextPoint.getLastPrice()) {
                        earliestLow = nextPoint;
                        duration = aPoint.getTimestamp() - earliestLow.getTimestamp();
                        result = new PointsCEFormulaData(aPoint, pbVal, duration);
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Tests the provided Price / Time point to determine if it represents a 'peak' 
     * on its Price / Time graph (ie is it a local high on the graph)
     * @param graph - The graph on which the point appears
     * @param aPoint - The point to be tested
     * @return boolean true if the point represents a peak on the graph, False otherwise.
     */
    public boolean isPeakPoint(TreeSet<AbstractGraphPoint> graph, AbstractGraphPoint aPoint) {
        boolean result = false;
        if (null != graph && null != aPoint) {
            NavigableSet<AbstractGraphPoint> subSet = graph.subSet(graph.first(), true, aPoint, false);
            Iterator<AbstractGraphPoint> descIterator = subSet.descendingIterator();
            AbstractGraphPoint prePoint = null;
            while (descIterator.hasNext()) {
                AbstractGraphPoint beforePoint = descIterator.next();
                if (beforePoint.getLastPrice() < aPoint.getLastPrice()) {
                    prePoint = beforePoint;
                    break;
                }
                if (beforePoint.getLastPrice() > aPoint.getLastPrice()) {
                    break;
                }
            }
            if (null != prePoint) {
                NavigableSet<AbstractGraphPoint> subSetF = graph.subSet(aPoint, false, graph.last(), true);
                AbstractGraphPoint postPoint = subSetF.first();
                if (postPoint.getLastPrice() < aPoint.getLastPrice()) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * Use this function to test if the 'E' of a Y-Line is a PB3+. Please use it
     * ONLY for this purpose It is not written to test ANY point against its
     * graph it assumes the graph represents the time between the 'C' and 'E'
     * points of a Y-Line and can return INDETERMINATE if their is a gap in the
     * data where more data must be loaded.
     * @param anEPoint - The 'E' point of an IGraphLine object being used as a Y-Line
     * @param graph - The data graph containing the 'E' Point
     * @return A TVL enumeration, True if the point is valid, False if it is not AND
     * possibly INDETERMINATE if additional data is required to make the determination.
     */
    public static TVL isPB3Plus(AbstractGraphPoint anEPoint, BaseGraph<AbstractGraphPoint> graph) {
        TVL result = TVL.INDETERMINATE;
        if (null != anEPoint && null != graph) {
            //Generate a subset of the graph from its start to the point to check
            AbstractGraphPoint cPoint = graph.first();
            NavigableSet<AbstractGraphPoint> subSet = graph.subSet(cPoint, true, anEPoint, false);
            BaseGraph<AbstractGraphPoint> subGraph = graph.replicateGraph();
            subGraph.clear();
            subGraph.addAll(subSet);
            //SubGraph now contains the points that may be used to test if this is a PB3
            //To be a PB3 plus we must
            double pbLimit = DTConstants.getScaledPBVALUE() / 100d;
            double dblCurrPBVal = 0;
            double dblPBVal = 0;
            Iterator<AbstractGraphPoint> descIter = subGraph.descendingIterator();
            while (descIter.hasNext()) {
                AbstractGraphPoint currPoint = descIter.next();
                dblCurrPBVal = anEPoint.getWAP() - currPoint.getWAP();
                if (dblCurrPBVal > dblPBVal) {
                    dblPBVal = dblCurrPBVal;
                }
                if (dblPBVal >= pbLimit) {
                    //We are at least DTConstants.PBVALUE ticks below aPoints WAP and anEPoint IS a PB3+
                    result = TVL.TRUE;
                    break;
                }
                //If we reach this point we have not yet found a point 3 ticks lower
                //Is this point higher? If so AND we have no time gaps in the graph it is NOT a PB3 ELSE we cannot resolve if its a PB3 (INDETERMINATE)
                if (currPoint.getWAP() > anEPoint.getWAP()) {
                    //Is this point NOT 'C'
                    if (!cPoint.equals(currPoint)) {
                        //A 'Higher point that is not C' - this means that the anEPoint is no longer the point to treat as the Y-Line 'E'
                        //Instead this new point must be treated as the Y-Line 'E'
                        anEPoint = currPoint;
                    } else {
                        //We must ensure their are no GAPS in the data graph before 'C' returns false
                        if (!IdentifyPB3Points.doesGraphHaveTimeGap(subGraph)) {
                            result = TVL.FALSE;
                            break;
                        } else {
                            //We have a time gap break from loop so indeterminate result is returned and more data is loaded
                            result = TVL.INDETERMINATE;                         //Line is not really needed but makes code easier to read
                            break;
                        }
                    }
                }
                //Have we reach 'C' (is currPoint the start of the graph)? IF we have AND THEIR ARE NO TIME GAPS IN THE GRAPH then anEPoint is NOT a PB3+ point
                //ELSE the result is INDETERMINATE as we need to load more data to fill in the gap
                if (cPoint.equals(currPoint)) {
                    //We must ensure their are no GAPS in the data graph before 'C' returns false
                    if (!IdentifyPB3Points.doesGraphHaveTimeGap(subGraph)) {
                        result = TVL.FALSE;
                        break;
                    } else {
                        //We have a time gap break from loop so indeterminate result is returned and more data is loaded
                        result = TVL.INDETERMINATE;                         //Line is not really needed but makes code easier to read
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Given a HISTORIC DATA graph ensure that their are no time gaps in it from
     * the last point in the graph to the first
     */
    private static boolean doesGraphHaveTimeGap(BaseGraph<AbstractGraphPoint> graphToTest) {
        boolean result = false;
        if (null != graphToTest) {
            //A graph with 0 or 1 point can have not time gap
            if (1 < graphToTest.size()) {
                //We have more than 1 point scan through from the end of the graph to the start looking for gaps
                Iterator<AbstractGraphPoint> descIter = graphToTest.descendingIterator();
                AbstractGraphPoint prevPoint = null;
                AbstractGraphPoint currPoint = null;
                while (descIter.hasNext()) {
                    currPoint = descIter.next();
                    if (null != prevPoint) {
                        long timeDiff = prevPoint.getTimestamp() - currPoint.getTimestamp();
                        //The timeDiff MUST ALWAYS BE 1000 milliseconds UNLESS prevPoint is the start of a day.
                        if (timeDiff != 1000) {
                            //UNLESS prevPoint is a start of day point we have a gap
                            if (!prevPoint.isStartOfDay()) {
                                result = true;
                                break;
                            }
                        }
                    }
                    //currPoint becomes previous point before returning to the top of while loop
                    prevPoint = currPoint;
                }
            }
        }
        return result;
    }
}
