/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.AbstractGraphPoint;
import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.DTConstants;
import daytrader.datamodel.DTDurationEnum;
import daytrader.datamodel.GraphLine;
import daytrader.datamodel.GraphLinePlusCEFormulaeData;
import daytrader.datamodel.PointsCEFormulaData;
import daytrader.datamodel.Putup;
import daytrader.datamodel.RealTimeRunManager;
import daytrader.interfaces.IGraphLine;
import daytrader.utils.DTUtil;
import daytrader.utils.Timer;
import daytradertasks.LoadHistoricDataBatchTask;
import daytradertasks.LoadTradingDaysTask;
import daytradertasks.LoadXSecOfHistoricDataTask;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Given a set of points generate provisional CE Lines between all points
 *
 * @author Roy
 */
public class GeneratePotentialCELines extends AbstractBaseRule {

    private BaseGraph<AbstractGraphPoint> graph;
    private PointValidationDetails pointValidationRecord;
    //This attribute will hold the C-E Lines generated in the last 'run' of this function
    private ArrayList<IGraphLine> finalCELines;
    //Unblocked Lines Cache stores the potential CE Lines after filterOutBlockedLines completes. If we need to load
    //additional data this cache avoids re-running the time expensive filter method after the data loads
    private ArrayList<IGraphLine> arlUnblockedCELines;
    //This data cache may be needed for STEP 6 of the BS Range test.
    private ArrayList<GraphLinePlusCEFormulaeData> ceFormulaDataCache;

    /**
     * Default Constructor
     */
    public GeneratePotentialCELines() {
    }

    /**
     * Constructor defining the data graph to generate C-E Lines for.
     * @param newGraph - A BaseGraph containing the data to use in generating C-E Lines
     */
    public GeneratePotentialCELines(BaseGraph<AbstractGraphPoint> newGraph) {
        this.graph = newGraph;
    }

    /**
     * Examines the data graph (historic normally) and generates a list of all
     * currently valid C-E Lines
     * @return An ArrayList of IGraphLine objects representing the valid CE Lines for this graph
     * @throws LoadingAdditionalDataException - Thrown if additional data must be loaded
     * to generate the CE Lines list.
     */
    public ArrayList<IGraphLine> generateLines() throws LoadingAdditionalDataException {
        this.finalCELines = new ArrayList<IGraphLine>();
        this.ceFormulaDataCache = null;
        //Set the owners historic graph as the graph to use
        if (null == this.graph) {
            if (null != this.owner) {
                this.graph = this.owner.getGraphHistoricData();
            } else {
                throw new UnsupportedOperationException("No owning put for C-E Lines generator");
            }
        }
        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
        IdentifyPB3Points<AbstractGraphPoint> rule = new IdentifyPB3Points<AbstractGraphPoint>();
        List<AbstractGraphPoint> pointList = rule.performBPOperationOnData(this.graph);
        //The point list now contains EVERY PB3 POINT UP TO THE LOW OF THE DAY + THE 'FIRST' point of the day however
        //For CE calc eliminate all points before the LATEST high of the day
        List<AbstractGraphPoint> revisedPointList = new ArrayList<AbstractGraphPoint>();
        NavigableSet<AbstractGraphPoint> subSet = this.graph.subSet(this.graph.getEarliestHigh(), true, this.graph.getLowestPointSoFar(), true);
        BaseGraph<AbstractGraphPoint> allowedRange = this.graph.replicateGraph();
        allowedRange.clear();
        allowedRange.addAll(subSet);
        for (AbstractGraphPoint currPoint : pointList) {
            if (allowedRange.contains(currPoint)) {
                revisedPointList.add(currPoint);
            }
        }
        pointList = revisedPointList;
        //We now have a list of points between the latest high and latest low all but the first are valid points the first must
        //be verified possibly against previous days data

        //Verify point one - START
        this.pointValidationRecord = null;
        if (0 < pointList.size()) {
            AbstractGraphPoint pointOne = pointList.get(0);
            //double finalPBValuePointOne = 0;
            TreeSet<AbstractGraphPoint> tsTime = new TreeSet<AbstractGraphPoint>(AbstractGraphPoint.TimeComparator);
            for (AbstractGraphPoint point : this.graph) {
                tsTime.add(point);
            }
            Iterator<AbstractGraphPoint> descIter = tsTime.descendingIterator();
            boolean abort = false;
            double pbLimit = DTConstants.getScaledPBVALUE() / 100d;
            while (descIter.hasNext() && !abort) {
                AbstractGraphPoint currPoint = descIter.next();
                if (currPoint.getTimestamp() <= pointOne.getTimestamp()) {
                    if (currPoint.getLastPrice() > pointOne.getLastPrice()) {
                        //We abort cannot be a potential C/E point
                        this.pointValidationRecord = new PointValidationDetails(pointOne, currPoint, true);
                        abort = true;
                    } else {
                        //Is this point less than the start point by PBValue
                        Double priceDiff = pointOne.getLastPrice() - currPoint.getLastPrice();
                        if (priceDiff >= pbLimit) {
                            //Point may be used calc and return the PB
                            priceDiff *= 100;
                            //finalPBValuePointOne = priceDiff.intValue();
                            this.pointValidationRecord = new PointValidationDetails(pointOne, currPoint, false);
                            abort = true;
                        }
                    }
                }
            }
            if (!abort) {
                //WE MUST LOOK BACK IN TIME
//                System.out.println("Look back in time!!!!");
                if (!validateFirstPoint(pointList, this.graph)) {
                    //This stock may not be traded shut it down
                    throw new UnsupportedOperationException("Could not validate first point on PB3 List");
                }
            }
        }
        //If the point verified (as a PB3 or not) then the class attribute this.pointValidationRecord contains a record of the validating point

        //Verify point one - END


        GraphLine ce;
        ArrayList<IGraphLine> tempStore;
        switch (pointList.size()) {
            case 0:
                //There is NO CE LINE.
                break;
            case 1:
                //We have only 1 point. Line is previous days close to the single point
                AbstractGraphPoint prevDayClose = this.graph.getPrevDayClose();
                AbstractGraphPoint ePoint = pointList.get(0);
                ce = new GraphLine(prevDayClose, ePoint, graph);
                TreeSet<Integer> tradeDay = new TreeSet<Integer>();
                tradeDay.add(prevDayClose.getDateAsNumber());
                ce.setTradingDays(tradeDay);
                tempStore = new ArrayList<IGraphLine>();
                tempStore.add(ce);
                result = this.filterOutBlockedLines(tempStore);
                break;
            case 2:
                //We have only 2 points these define the only possible C-E Line
                AbstractGraphPoint firstPoint = pointList.get(0);
                AbstractGraphPoint secPoint = pointList.get(1);
                ce = new GraphLine(firstPoint, secPoint, graph);
                tempStore = new ArrayList<IGraphLine>();
                tempStore.add(ce);
                result = this.filterOutBlockedLines(tempStore);
                break;
            default:
                //We have at least 3 points 
                tempStore = this.generateLines(pointList);
                result = tempStore;

        }
        //Final sanity check ensure their are no 'Horizonal' C-E Lines
        if (0 < result.size()) {
            List<IGraphLine> tempList = new LinkedList<IGraphLine>(result);
            for (IGraphLine currLine : tempList) {
                if (0 == currLine.getGradient()) {
                    result.remove(currLine);
                }
            }
        }
        //Store the final C-E Lines in the result
        this.finalCELines = result;
        return result;
    }

    /**
     * Examines the data in the list of Price / Time points and attempts to generate 
     * CE Lines from them
     * @param points - The list of Price / Time points to use
     * @return An ArrayList of IGraphLine objects that define the valid CE Lines.
     * @throws LoadingAdditionalDataException - Thrown if additional data must be loaded 
     * to determine valid CE Lines.
     */
    public ArrayList<IGraphLine> generateLines(List<AbstractGraphPoint> points) throws LoadingAdditionalDataException {
        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
        ArrayList<IGraphLine> tempStore = new ArrayList<IGraphLine>();
        if (2 <= points.size()) {                                                       //Must have 2 points minimum for a line
            for (int i = 0; i < points.size(); i++) {
                AbstractGraphPoint mainPoint = points.get(i);
                for (int j = i + 1; j < points.size(); j++) {
                    AbstractGraphPoint secondPoint = points.get(j);
                    if (mainPoint.getLastPrice() > secondPoint.getLastPrice()) {
                        GraphLine aLine = new GraphLine(mainPoint, secondPoint, this.graph);
                        tempStore.add(aLine);
                    }
                }
            }
            //Filter the results to eliminate blocked lines
            tempStore = this.filterOutBlockedLines(tempStore);
            //if we have more than one result use formule to play off the CE Lines
            if (1 < tempStore.size()) {
                //Formula go here
                //First we need the exact PB3 value and duration for every point involved in the potential CE Lines
                ArrayList<PointsCEFormulaData> ceFormulaData = this.generateCEFormulaData(tempStore);
                //Now find the record with the highest y score, THEN find the record with the highest x score
                TreeSet<PointsCEFormulaData> PBValOrder = new TreeSet<PointsCEFormulaData>(ceFormulaData);                              //This is the points data sorted by PBValue (Y per Bryn).
                PointsCEFormulaData highestY = PBValOrder.last();
                TreeSet<PointsCEFormulaData> durationOrder = new TreeSet<PointsCEFormulaData>(PointsCEFormulaData.DurationComparator);  //This is the points data sorted by TIME since earliest low (X per Bryn)
                durationOrder.addAll(ceFormulaData);
                PointsCEFormulaData highestX = durationOrder.last();
                PointsCEFormulaData provSD = null;
                PointsCEFormulaData finalSD = null;
                //Highest PBValue is the same object as highest duration? If so this is the provisional SD
                if (highestY.equals(highestX)) {
                    //if a single point has both the greatest y (pb) and the greatest x (duration) then it is the PROVISIONAL SD. "EQUAL" COUNTS AS GREATEST!
                    provSD = highestY;
                }
                //At this point their may or may not be a provisional SD see Bryns note below
                /**
                 * if a single point has both the greatest y (pb) and the
                 * greatest x (duration) then it is the PROVISIONAL SD. "EQUAL"
                 * COUNTS AS GREATEST! if the greatest y and the greatest x are
                 * different points, then there is no provisional sd.
                 */
                //Is the high even in the list 
                if (!this.pointValidationRecord.isRemoved()) {
                    //The high is STILL in the point list
                    //Is their a provisional SD
                    if (null != provSD) {
                        //I have a provisional SD
                        //does the provisional SD include the validated point
                        AbstractGraphPoint highPoint = this.pointValidationRecord.getFirstPoint();
                        AbstractGraphPoint pointSD = provSD.getPoint();
                        //Check in test that equals
                        if (highPoint.equals(pointSD)) {
                            //SD and High point are the same YES
                            provSD.setFirstPoint(true);
                            finalSD = provSD;
                        } else {
                            //SD is NOT the high point NO
                            finalSD = goToComplication(ceFormulaData, PBValOrder, durationOrder);
                        }
                    } else {
                        //I do not have a provisional SD
                        finalSD = goToComplication(ceFormulaData, PBValOrder, durationOrder);
                    }
                } else {
                    //The high IS NOT IN the point list
                    finalSD = provSD;
                }

                //The PBValOrder list and durationOrder may be out of order create a final list 
                //TreeSet<PointsCEFormulaData> PBValOrder = new TreeSet<PointsCEFormulaData>(ceFormulaData);
                TreeSet<PointsCEFormulaData> finalPointList = new TreeSet<PointsCEFormulaData>();
                for (PointsCEFormulaData currData : PBValOrder) {
                    finalPointList.add(currData);
                }

                //AFTER THE COMPLICATION ANY FURTHER CODE GOES HERE
                //WE NOW HAVE AN 'SD' IF ONE EXISTS WHILE tempStore HOLDS THE LIST OF POTENTIAL C-E LINES THAT WE MUST PLAY OFF USING THE FORMULE

                //We may need to know all the C-E Lines AND their C-E Formula data at a later stage
                //In the name of sanity merge the two and cache the result
                ArrayList<GraphLinePlusCEFormulaeData> dataToCache = new ArrayList<GraphLinePlusCEFormulaeData>();
                for (IGraphLine currLine : tempStore) {
                    GraphLinePlusCEFormulaeData aLine = new GraphLinePlusCEFormulaeData(currLine, finalPointList);
                    dataToCache.add(aLine);
                }
                this.ceFormulaDataCache = dataToCache;

                //CE Line Selection Formula are applied here
                if (null == finalSD) {
                    //There is NO SD
                    //Moved the code below to the section immediately above this if statement as we need to cache the data.
                    //In the name of sanity merge the porential C-E Lines with the C-E Formulae Data so each line carries the formulae data for its defining points with it
//                    ArrayList<GraphLinePlusCEFormulaeData> potentialCELines = new ArrayList<GraphLinePlusCEFormulaeData>();
//                    for (IGraphLine currLine : tempStore) {
//                        GraphLinePlusCEFormulaeData aLine = new GraphLinePlusCEFormulaeData(currLine, finalPointList);
//                        potentialCELines.add(aLine);
//                    }
                    ArrayList<GraphLinePlusCEFormulaeData> potentialCELines = new ArrayList<GraphLinePlusCEFormulaeData>(dataToCache);
                    //The variable potentialCELines is now a list of the potential C-E Lines each of which 'knows' the C-E Formulae Data for its defining points.
                    ArrayList<GraphLinePlusCEFormulaeData> deadCELines = new ArrayList<GraphLinePlusCEFormulaeData>();
                    for (GraphLinePlusCEFormulaeData currCELine : potentialCELines) {
                        for (GraphLinePlusCEFormulaeData targetCELine : potentialCELines) {
                            //Ensure I do not test the line against itself
                            boolean survived = true;
                            if (currCELine != targetCELine) {
                                survived = currCELine.survivesAgainst(targetCELine);
                                if (!survived) {
                                    deadCELines.add(currCELine);
                                    break;
                                }
                            }
                        }
                    }
                    //Now remove all 'dead' lines from the potential CE Lines
                    potentialCELines.removeAll(deadCELines);
                    //Store into tempStore for transfer to the results
                    tempStore.clear();
                    for (IGraphLine currLine : potentialCELines) {
                        tempStore.add(currLine);
                    }
                } else {
                    if (finalSD.isFirstPoint()) {
                        //SD Exists and it was the first potenetial C or E of the day
                        //The SD 'C' point is the SD's point
                        AbstractGraphPoint cPoint = finalSD.getSourceLine().getCurrentC();
                        ArrayList<IGraphLine> sdCToAPoint = new ArrayList<IGraphLine>();
                        for (IGraphLine currLine : tempStore) {
                            AbstractGraphPoint currentC = currLine.getCurrentC();
                            if (currentC.equals(cPoint)) {
                                sdCToAPoint.add(currLine);
                            }
                        }
                        IGraphLine winner = null;
                        for (IGraphLine currLine : sdCToAPoint) {
                            if (winner == null) {
                                winner = currLine;
                            } else {
                                //Work out gradient for curr winner and this line
                                double gradientWin = winner.getGradient();
                                double gradientCurr = currLine.getGradient();
                                if (gradientCurr < gradientWin) {
                                    winner = currLine;
                                }
                            }
                        }
                        //Now update tempStore so it holds only the winning line
                        tempStore.clear();
                        tempStore.add(winner);
                    } else {
                        //SD Exists and it was NOT the first potenetial C or E of the day
                        //The SD 'C' point is the SD's point
                        AbstractGraphPoint cPoint = finalSD.getSourceLine().getCurrentC();
                        ArrayList<IGraphLine> sdCToAPoint = new ArrayList<IGraphLine>();
                        for (IGraphLine currLine : tempStore) {
                            AbstractGraphPoint currentC = currLine.getCurrentC();
                            if (currentC.equals(cPoint)) {
                                sdCToAPoint.add(currLine);
                            }
                        }
                        //At this point the variable sdCToAPoint contains all lines that include the SD or its stand in
                        //For each surviving line find the point that is NOT the SD
                        for (IGraphLine currLine : sdCToAPoint) {
                            AbstractGraphPoint nonSDPoint = null;
                            AbstractGraphPoint originalNonSDPoint = null;
                            if (currLine.getCurrentC().equals(finalSD.getPoint())) {
                                //The lines 'C' is the SD so use the current 'E' point
                                nonSDPoint = currLine.getCurrentE();
                                originalNonSDPoint = currLine.getEndPoint();
                            } else {
                                //The lines 'E' is the SD so use the current 'C' point
                                nonSDPoint = currLine.getCurrentC();
                                originalNonSDPoint = currLine.getStartPoint();
                            }
                            //nonSDPoint now holds the point that defines the current line and which is not the SD
                            //originalNonSDPoint is either the same as nonSDPoint (if no stand in existed) or the
                            //nonSDPoint BEFORE the stand in.
                            //The finalPointList holds all the points involved in all lines with their Y and X data,
                            //now find the Y and X data for the originalNonSDPoint
                            PointsCEFormulaData nonSDPointsData = null;
                            for (PointsCEFormulaData currData : finalPointList) {
                                AbstractGraphPoint currDataPoint = currData.getPoint();
                                if (currDataPoint.equals(originalNonSDPoint)) {
                                    nonSDPointsData = currData;
                                    break;
                                }
                            }
                            if (nonSDPointsData != null) {
                                TreeSet<PointsCEFormulaData> deadPoints = new TreeSet<PointsCEFormulaData>();
                                TreeSet<IGraphLine> deadLines = new TreeSet<IGraphLine>();
                                //Test to see if the nonSDPoint 'survives' against all other points (I test Y first only if it fails do I need to test X)
                                for (PointsCEFormulaData currData : finalPointList) {
                                    //Ensure I do not test the point against itself
                                    if (!currData.equals(nonSDPointsData)) {
                                        //Does it fail to survive on the 'Y' value
                                        if (nonSDPointsData.getYScore() < currData.getYScore()) {
                                            //Failed to survive on the 'Y' value
                                            //Does it also fail to survive on the 'X' value
                                            if (nonSDPointsData.getXScore() < currData.getXScore()) {
                                                //This is a dead point that failed to survive
                                                deadPoints.add(nonSDPointsData);
                                                deadLines.add(nonSDPointsData.getSourceLine());
                                            }
                                        }
                                    }
                                }
                                //From the list of final points and the list of final lines remove the 'dead' ones
                                finalPointList.removeAll(deadPoints);
                                sdCToAPoint.removeAll(deadLines);
                                tempStore = sdCToAPoint;
                            }
                        }
                    }
                }
            }
        }
        if (0 < tempStore.size()) {
            for (IGraphLine currLine : tempStore) {
                result.add(currLine);
            }
        }
        return result;
    }

    //First we need the exact PB3 value and duration for every point involved in the potential CE Lines
    /**
     * Given a list of lines this function retrieves the information we need to
     * know about each C & E point to do the C-E play off and determine the
     * winning C-E line The data is returned as an array list of
     * PointsCEFormulaData objects with each object encapsulating the data about
     * a point.
     */
    private ArrayList<PointsCEFormulaData> generateCEFormulaData(ArrayList<IGraphLine> lines) {
        ArrayList<PointsCEFormulaData> result = new ArrayList<PointsCEFormulaData>();
        if (null != lines && 0 < lines.size() && null != this.graph) {
            for (IGraphLine currLine : lines) {
                //Get Current C & E
                //                AbstractGraphPoint currentC = currLine.getCurrentC();
                //                AbstractGraphPoint currentE = currLine.getCurrentE();
                AbstractGraphPoint startPoint = currLine.getStartPoint();
                AbstractGraphPoint endPoint = currLine.getEndPoint();
                PointsCEFormulaData ceFormulaDataC = IdentifyPB3Points.getCEFormulaData(this.graph, startPoint);
                ceFormulaDataC.setSourceLine(currLine);
                PointsCEFormulaData ceFormulaDataE = IdentifyPB3Points.getCEFormulaData(this.graph, endPoint);
                ceFormulaDataE.setSourceLine(currLine);
                result.add(ceFormulaDataC);
                result.add(ceFormulaDataE);
            }
        }
        return result;
    }

    private ArrayList<IGraphLine> filterOutBlockedLines(ArrayList<IGraphLine> data) {
        Timer.setBaseTime();
        Timer.printMsg("Starting filterOutBlockedLines");
        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
        ArrayList<IGraphLine> tempResult = new ArrayList<IGraphLine>();
        if (null != data && data.size() > 0 && this.isValid()) {
            if (null == this.arlUnblockedCELines) {
                //Computing gradients is taking too long I am going to pre-compute them using multi threaded code
                //so multiple CPU's
                ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1);
                ExecutorCompletionService<Double> compServ = new ExecutorCompletionService<Double>(threadPool);
                int taskCount = 0;
                for (IGraphLine currLine : data) {
                    CompGradients task = new CompGradients(currLine);
                    compServ.submit(task);
                    taskCount++;
                }

                for (int i = 0; i < taskCount; i++) {
                    try {
                        //NB: I am not interested in the result simply that the task is done
                        compServ.take();
                    } catch (InterruptedException ex) {
                        Logger.getLogger(GeneratePotentialCELines.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                /**
                 * Multi threaded implementation of looking for stand in C's and
                 * blocked lines
                 */
//            int taskCount = 0;
//            Timer.setBaseTime();
//            Timer.printMsg("Starting task generation for 'c' points");
//            for (IGraphLine currLine : data) {
//                ProcessCPoint task = new ProcessCPoint(currLine, this.graph);
//                this.compServ.submit(task);
//                taskCount++;
//            }
//
//            for (int i = 0; i < taskCount; i++) {
//                try {
//                    Future<BlockedLineResult> f = this.compServ.take();
//                    BlockedLineResult taskResult = f.get();
//                    if(taskResult.isIncludeInResult()){
//                        tempResult.add(taskResult.getLine());
//                    }
//                } catch (Exception ex) {
//                    //No exception should ever arise that cannot be ignored
//                }
//            }
//            Timer.printMsg("All Tasks processed for 'c' points");
//            // We have found all blocks between c and e now find blocks between e and current time
//            taskCount = 0;
//            for (IGraphLine currLine : tempResult) {
//                ProcessEPoint task = new ProcessEPoint(currLine, this.graph);
//                this.compServ.submit(task);
//                taskCount++;
//            }
//            
//            for (int i = 0; i < taskCount; i++) {
//                try {
//                    Future<BlockedLineResult> f = this.compServ.take();
//                    BlockedLineResult taskResult = f.get();
//                    if(taskResult.isIncludeInResult()){
//                        result.add(taskResult.getLine());
//                    }
//                } catch (Exception ex) {
//                    //No exception should ever arise that cannot be ignored
//                }
//            }
//            Timer.printMsg("All Tasks processed for 'e' points");
                /**
                 * *************************************************************************************************
                 */
                /**
                 * Converted this to multi threaded code but have kept the
                 * original for clarity and safety
                 */
//                int count = 0;
//                int max = data.size();
//                AbstractGraphPoint startPoint;
//                AbstractGraphPoint currPoint;
//                boolean blnIncludeInResults = true;
//                double priceAtTime = 0;
//                for (IGraphLine currLine : data) {
//                    startPoint = currLine.getStartPoint();
//                    NavigableSet<AbstractGraphPoint> subGraph = this.graph.subSet(startPoint, false, currLine.getEndPoint(), true);
//                    Iterator<AbstractGraphPoint> iterator = subGraph.iterator();
//                    blnIncludeInResults = true;
//                    while (iterator.hasNext()) {
//                        currPoint = iterator.next();
//                        priceAtTime = currLine.getPriceAtTime(currPoint.getTimestamp());
//                        if (currPoint.getLastPrice() > priceAtTime) {
//                            //Get PB value of this point
//                            Timer.setBaseTime();
//                            int intPBValue = IdentifyPB3Points.findPBValue(this.graph, currPoint);
//                            Timer.printMsg("Finished (C) PB3 Calc");
//                            if (intPBValue >= DTConstants.getScaledPBVALUE()) {
//                                //This line is invalid, discard it and move to next
//                                blnIncludeInResults = false;
//                                break;
//                            } else {
//                                currLine.setStandInC(currPoint);
//                            }
//                        }
//                    }
//                    //Test to see if the currLine should be included in results
//                    if (blnIncludeInResults) {
//                        tempResult.add(currLine);
//                    }
//                    count++;
//                    System.out.println("Processed (C): " + count + " of " + max);
//                }
                //Try it multi threaded!
                taskCount = 0;
                ExecutorCompletionService<PBValueCalcResult> compServ2 = new ExecutorCompletionService<PBValueCalcResult>(threadPool);
                for (IGraphLine currLine : data) {
                    PBValueCalcCPoints task = new PBValueCalcCPoints(currLine, this.graph);
                    compServ2.submit(task);
                    taskCount++;
                }

                for (int i = 0; i < taskCount; i++) {
                    try {
                        Future<PBValueCalcResult> f = compServ2.take();
                        PBValueCalcResult aResult = f.get();
                        if (aResult.isIncludeInResults()) {
                            tempResult.add(aResult.getLine());
                        }
                    } catch (InterruptedException ex) {
                        Logger.getLogger(GeneratePotentialCELines.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ExecutionException ex) {
                        Logger.getLogger(GeneratePotentialCELines.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                // We have found all blocks between c and e now find blocks between e and current time
                taskCount = 0;
                for (IGraphLine currLine : tempResult) {
                    PBValueCalcEPoints task = new PBValueCalcEPoints(currLine, this.graph);
                    compServ2.submit(task);
                    taskCount++;
                }

                for (int i = 0; i < taskCount; i++) {
                    try {
                        Future<PBValueCalcResult> f = compServ2.take();
                        PBValueCalcResult aResult = f.get();
                        if (aResult.isIncludeInResults()) {
                            result.add(aResult.getLine());
                        }
                    } catch (InterruptedException ex) {
                        Logger.getLogger(GeneratePotentialCELines.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ExecutionException ex) {
                        Logger.getLogger(GeneratePotentialCELines.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                // We have found all blocks between c and e now find blocks between e and current time
//                count = 0;
//                max = tempResult.size();
//                for (IGraphLine currLine : tempResult) {
//                    NavigableSet<AbstractGraphPoint> subGraph = this.graph.subSet(currLine.getEndPoint(), false, this.graph.getLowestPointSoFar(), true);
//                    Iterator<AbstractGraphPoint> iterator = subGraph.iterator();
//                    blnIncludeInResults = true;
//                    while (iterator.hasNext()) {
//                        currPoint = iterator.next();
//                        priceAtTime = currLine.getPriceAtTime(currPoint.getTimestamp());
//                        if (currPoint.getLastPrice() > priceAtTime) {
//                            //Get PB value of this point
//                            Timer.setBaseTime();
//                            int intPBValue = IdentifyPB3Points.findPBValue(this.graph, currPoint);
//                            Timer.printMsg("Finished (E) PB3 Calc");
//                            if (intPBValue >= DTConstants.getScaledPBVALUE()) {
//                                //This line is invalid, discard it and move to next
//                                blnIncludeInResults = false;
//                                break;
//                            } else {
//                                currLine.setStandInE(currPoint);
//                            }
//                        }
//                    }
//                    if (blnIncludeInResults) {
//                        result.add(currLine);
//                    }
//                    count++;
//                    System.out.println("Processed (E): " + count + " of " + max);
//                }
            } else {
                //We have already done this function, use the previous cached result
                result = this.arlUnblockedCELines;
            }
        }
        //Cache the result to avoid re-runs of this method
        this.arlUnblockedCELines = result;
        Timer.printMsg("Finished filterOutBlockedLines");
        return result;
    }

    private boolean isValid() {
        boolean result = false;
        if (null != this.graph && this.graph.size() > 0) {
            result = true;
        }
        return result;
    }

    /**
     * Given a list of potential PB3 points this method takes the first point of
     * the list and determines if it really is a PB3 Point using PREVIOUS days
     * data. If it is NOT then it is removed from the list, if it is then the
     * list is unchanged. In either case the method will return true. HOWEVER if
     * SUFFICIANT previous days data is not avaliable the list is left
     * unmodified BUT the method returns false to indicate the first point could
     * not be validated IF THIS HAPPENS YOU SHOULD SHUT DOWN MONITORING THIS
     * STOCK
     *
     * @param pointList - A List of potential PB3 points
     * @param graphData - The data graph for this stock
     * @return boolean True if the point was validated (regardless of whether it
     * was kept in list or not), False if a lack of previous days data prevented
     * validation
     */
    private boolean validateFirstPoint(List<AbstractGraphPoint> pointList, BaseGraph<AbstractGraphPoint> graphData) throws LoadingAdditionalDataException {
        boolean blnWasValidated = false;
        if (null != pointList && null != graphData) {
            if (0 < pointList.size() && 0 < graphData.size()) {
                double pbLimit = DTConstants.getScaledPBVALUE() / 100d;
                //First check use close of previous day to try and validate first point as a PB3
                AbstractGraphPoint firstPoint = pointList.get(0);
                AbstractGraphPoint prevDayClose = graphData.getPrevDayClose();
                if (null != prevDayClose) {
                    double closePrice = prevDayClose.getLastPrice();
                    if (closePrice > firstPoint.getLastPrice()) {
                        //CANNOT be a PB3 remove from list and mark as validated
                        pointList.remove(firstPoint);
                        this.pointValidationRecord = new PointValidationDetails(firstPoint, prevDayClose, true);
                        blnWasValidated = true;
                    } else {
                        //Check to see if the price is low enough to be a PB3
                        //Is this point less than the start point by PBValue
                        Double priceDiff = firstPoint.getLastPrice() - closePrice;
                        if (priceDiff >= pbLimit) {
                            //Point may be used as a PB3 Point (It stays in list and is validated)
                            this.pointValidationRecord = new PointValidationDetails(firstPoint, prevDayClose, false);
                            blnWasValidated = true;
                        }
                    }
                }
                if (!blnWasValidated) {
                    //The close of previous day failed to validate the point now try to use the first min
                    //Of cached data from loading up the close of the previous day
                    BaseGraph<AbstractGraphPoint> graph60Secs = graphData.getGraphClosePrevDayData();
                    if (null != graph60Secs && 0 < graph60Secs.size()) {
                        AbstractGraphPoint currPoint;
                        Iterator<AbstractGraphPoint> descIter = graph60Secs.descendingIterator();
                        while (descIter.hasNext()) {
                            currPoint = descIter.next();
                            if (currPoint != prevDayClose) {
                                double closePrice = currPoint.getLastPrice();
                                if (closePrice > firstPoint.getLastPrice()) {
                                    //CANNOT be a PB3 remove from list and mark as validated
                                    pointList.remove(firstPoint);
                                    this.pointValidationRecord = new PointValidationDetails(firstPoint, currPoint, true);
                                    blnWasValidated = true;
                                    break;
                                } else {
                                    //Check to see if the price is low enough to be a PB3
                                    //Is this point less than the start point by PBValue
                                    Double priceDiff = firstPoint.getLastPrice() - closePrice;
                                    if (priceDiff >= pbLimit) {
                                        //Point may be used as a PB3 Point (It stays in list and is validated)
                                        this.pointValidationRecord = new PointValidationDetails(firstPoint, currPoint, false);
                                        blnWasValidated = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (!blnWasValidated) {
                    //If we get this far the first min of previous days data did not validate point (or is not availiable)
                    //Now we must use previous days data look and see if it is avaliable
                    BaseGraph<AbstractGraphPoint> prevDayGraph = this.graph.getPrevDayGraph();
                    if (null != prevDayGraph) {
                        //We may proceed to do the check as the data is avaliable
                        //Do Validation
                        Iterator<AbstractGraphPoint> descIter = prevDayGraph.descendingIterator();
                        AbstractGraphPoint currPoint;
                        while (descIter.hasNext()) {
                            currPoint = descIter.next();
                            if (currPoint != prevDayClose) {
                                double closePrice = currPoint.getLastPrice();
                                if (closePrice > firstPoint.getLastPrice()) {
                                    //CANNOT be a PB3 remove from list and mark as validated
                                    pointList.remove(firstPoint);
                                    this.pointValidationRecord = new PointValidationDetails(firstPoint, currPoint, true);
                                    blnWasValidated = true;
                                    break;
                                } else {
                                    //Check to see if the price is low enough to be a PB3
                                    //Is this point less than the start point by PBValue
                                    Double priceDiff = firstPoint.getLastPrice() - closePrice;
                                    if (priceDiff >= pbLimit) {
                                        //Point may be used as a PB3 Point (It stays in list and is validated)
                                        this.pointValidationRecord = new PointValidationDetails(firstPoint, currPoint, false);
                                        blnWasValidated = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (!blnWasValidated) {
                            //The while loop has completed without validating the first point we must load another 30 min of data
                            //Get the last availiable points time
                            AbstractGraphPoint lastDataPoint = prevDayGraph.first();
                            Calendar endDate = lastDataPoint.getCalDate();
                            Calendar exchOpeningCalendar = DTUtil.getExchOpeningCalendar(endDate);
                            Putup putup = this.graph.getPutup();
                            if (exchOpeningCalendar.getTimeInMillis() < endDate.getTimeInMillis()) {
                                RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                                LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(putup, endDate, runManager);
                                this.requestMoreData(loadTask);
                            }
                        }
                    } else {
                        //We must load the previous days graph (first 30 min)
                        //Start the loading task
                        RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                        Putup putup = this.graph.getPutup();
                        TreeSet<Integer> tradingDays = this.graph.getTradingDays();
                        //As the prevDayGraph is null load the first 30 min of the previous day
                        Integer prevDayIntCode = tradingDays.last();
                        Calendar endDate = DTUtil.convertIntDateToCalendar(prevDayIntCode, DTConstants.EXCH_TIME_ZONE);
                        //Move endDate time to the end of trading on this day
                        endDate = DTUtil.getExchClosingCalendar(endDate);
                        LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(putup, endDate, runManager);
                        this.requestMoreData(loadTask);
                    }
                }
                if (!blnWasValidated) {
                    //IF WE EVER GET TO THIS POINT AND blnWasValidated is FALSE then the point CANNOT BE VALIDATED MAJOR FAILURE THIS STOCK MUST ABORT
                }
            }
        }
        return blnWasValidated;
    }

    private PointsCEFormulaData goToComplication(ArrayList<PointsCEFormulaData> ceFormulaData, TreeSet<PointsCEFormulaData> PBValOrder, TreeSet<PointsCEFormulaData> durationOrder) throws LoadingAdditionalDataException {
        PointsCEFormulaData result = null;
        PointsCEFormulaData greatestY = PBValOrder.last();
        PointsCEFormulaData greatestX = durationOrder.last();
        AbstractGraphPoint highOfDay = this.pointValidationRecord.getFirstPoint();
        PointsCEFormulaData provisionalPoint = null;
        for (PointsCEFormulaData currRec : ceFormulaData) {
            if (currRec.getPoint().equals(this.pointValidationRecord.getFirstPoint())) {
                provisionalPoint = currRec;
            }
        }
        if (null != provisionalPoint) {
            boolean winOnY = false;
            boolean winOnX = false;

            double dblPBVal = provisionalPoint.getYScore();
            double dblDuration = provisionalPoint.getXScore();

            if (highOfDay.equals(greatestY.getPoint())) {
                winOnY = true;
            }
            if (highOfDay.equals(greatestX.getPoint())) {
                winOnX = true;
            }

            /**
             * One or both of the above will not 'win' and it is necessary to go
             * amend the validation details for the failing record to the
             * correct price and duration (X and Y respectively)
             */
            if (!winOnY) {
                boolean blnContinueChecks = true;
                //What was the winning Y
                double pbValue = greatestY.getPb3Value();
                //First check use close of previous day to try and validate first point as a PB3
                AbstractGraphPoint firstPoint = highOfDay;
                AbstractGraphPoint prevDayClose = this.graph.getPrevDayClose();
                if (null != prevDayClose) {
                    double closePrice = prevDayClose.getLastPrice();
                    if (closePrice > firstPoint.getLastPrice()) {
                        //Use winning Y value from original list PBValue
                        dblPBVal = provisionalPoint.getYScore();
                        blnContinueChecks = false;
                    } else {
                        //Check to see if the price is low enough to be a PB3
                        //Is this point less than the start point by PBValue
                        Double priceDiff = firstPoint.getLastPrice() - closePrice;
                        if (priceDiff >= pbValue) {
                            //Point may be used as a PB3 Point (It stays in list and is validated)
                            dblPBVal = priceDiff;
                            blnContinueChecks = false;
                        }
                    }
                }
                if (blnContinueChecks) {
                    //Previous close did not resolve a PBValue
                    //Look in previous days data (1 min) to close of day
                    BaseGraph<AbstractGraphPoint> graph60Secs = this.graph.getGraphClosePrevDayData();
                    if (null != graph60Secs && 0 < graph60Secs.size()) {
                        AbstractGraphPoint currPoint;
                        Iterator<AbstractGraphPoint> descIter = graph60Secs.descendingIterator();
                        while (descIter.hasNext()) {
                            currPoint = descIter.next();
                            if (currPoint != prevDayClose) {
                                double closePrice = currPoint.getLastPrice();
                                if (closePrice > firstPoint.getLastPrice()) {
                                    //Use winning Y value from original list PBValue
                                    dblPBVal = provisionalPoint.getYScore();
                                    blnContinueChecks = false;
                                    break;
                                } else {
                                    //Check to see if the price is low enough to be a PB3
                                    //Is this point less than the start point by PBValue
                                    Double priceDiff = firstPoint.getLastPrice() - closePrice;
                                    if (priceDiff >= pbValue) {
                                        //Point may be used as a PB3 Point (It stays in list and is validated)
                                        dblPBVal = priceDiff;
                                        blnContinueChecks = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                if (blnContinueChecks) {
                    //Prev Close AND looking at 1 min of data did not resolve it
                    //Check to see if we have previous days data
                    BaseGraph<AbstractGraphPoint> prevDayGraph = this.graph.getPrevDayGraph();
                    if (null != prevDayGraph) {
                        //Attempt to measure Y
                        //Try to find a validating point on the graph
                        Iterator<AbstractGraphPoint> descIter = prevDayGraph.descendingIterator();
                        AbstractGraphPoint currPoint;
                        while (descIter.hasNext()) {
                            currPoint = descIter.next();
                            if (currPoint != prevDayClose) {
                                double closePrice = currPoint.getLastPrice();
                                if (closePrice > firstPoint.getLastPrice()) {
                                    //CANNOT be a PB3 remove from list and mark as validated
                                    //Use winning Y value from original list PBValue
                                    dblPBVal = firstPoint.getLastPrice() - prevDayGraph.getEarliestLow().getLastPrice();
                                    blnContinueChecks = false;
                                    break;
                                } else {
                                    //Check to see if the price is low enough to be a PB3
                                    //Is this point less than the start point by PBValue
                                    Double priceDiff = firstPoint.getLastPrice() - closePrice;
                                    if (priceDiff >= pbValue) {
                                        //Point may be used as a PB3 Point (It stays in list and is validated)
                                        dblPBVal = priceDiff;
                                        blnContinueChecks = false;
                                        break;
                                    }
                                }
                            }
                        }
                        if (blnContinueChecks) {
                            //We could not determine a new Y value from the avaliable data, load another 30 min
                            //Get the last availiable points time
                            AbstractGraphPoint lastDataPoint = prevDayGraph.first();
                            Calendar endDate = lastDataPoint.getCalDate();
                            Calendar exchOpeningCalendar = DTUtil.getExchOpeningCalendar(endDate);
                            Putup putup = this.graph.getPutup();
                            if (exchOpeningCalendar.getTimeInMillis() < endDate.getTimeInMillis()) {
                                RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                                LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(putup, endDate, runManager);
                                this.requestMoreData(loadTask);
                            }
                        }
                    } else {
                        //We must load the previous days graph (first 30 min)
                        //Start the loading task
                        RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                        Putup putup = this.graph.getPutup();
                        TreeSet<Integer> tradingDays = this.graph.getTradingDays();
                        //As the prevDayGraph is null load the first 30 min of the previous day
                        Integer prevDayIntCode = tradingDays.last();
                        Calendar endDate = DTUtil.convertIntDateToCalendar(prevDayIntCode, DTConstants.EXCH_TIME_ZONE);
                        //Move endDate time to the end of trading on this day
                        endDate = DTUtil.getExchClosingCalendar(endDate);
                        LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(putup, endDate, runManager);
                        this.requestMoreData(loadTask);
                    }
                }
            }

            if (!winOnX) {
                //What is the largest current duration? This is the target to equal or beat
                double winningXScore = greatestX.getXScore();
                double winXScoreInSec = winningXScore / DTConstants.SCALE;
                boolean blnContinueChecks = true;
                AbstractGraphPoint prevDayClose = this.graph.getPrevDayClose();
                AbstractGraphPoint firstPoint = highOfDay;
                if (null != prevDayClose) {
                    if (prevDayClose.getLastPrice() > firstPoint.getLastPrice()) {
                        //Calculate a duration this is the time betwen the first point (high of the day) and the lowest
                        //point between that high and the START of the day
                        //1) subset graph from start of day to the first point.
                        NavigableSet<AbstractGraphPoint> subSet = this.graph.subSet(this.graph.first(), true, firstPoint, true);
                        BaseGraph<AbstractGraphPoint> subGraph = this.graph.replicateGraph();
                        subGraph.clear();
                        subGraph.addAll(subSet);
                        //2) Determine the earliest low on the sub graph
                        AbstractGraphPoint earliestLow = subGraph.getEarliestLow();
                        //3) Duration is diff in time between firstPoint and earliest low (Same day for both points so duration is diff in timestamps)
                        dblDuration = firstPoint.getTimestamp() - earliestLow.getTimestamp();
                        blnContinueChecks = false;
                    }
                }
                if (blnContinueChecks) {
                    //Looking at the prev day close does not resolve the duration (it is not higher than the high of the day)
                    //Do we have yesterdays data??
                    BaseGraph<AbstractGraphPoint> prevDayGraph = this.graph.getPrevDayGraph();
                    if (null != prevDayGraph) {
                        //Do we have enough data - i.e. at least the winning X score
                        if (winXScoreInSec <= prevDayGraph.size()) {
                            //We have enough data to make the determination
                            //1) Scan back only as far as winXScoreISec looking for higher high than first point
                            int secCount = 0;
                            Iterator<AbstractGraphPoint> descIter = prevDayGraph.descendingIterator();
                            AbstractGraphPoint currPoint = null;
                            AbstractGraphPoint higherPoint = null;
                            while (descIter.hasNext() && secCount <= winXScoreInSec) {
                                currPoint = descIter.next();
                                if (currPoint.getLastPrice() >= firstPoint.getLastPrice()) {
                                    higherPoint = currPoint;
                                    break;
                                }
                            }
                            if (null != higherPoint) {
                                //Work out exact final duration
                                NavigableSet<AbstractGraphPoint> subSet = prevDayGraph.subSet(higherPoint, true, prevDayGraph.last(), true);
                                BaseGraph<AbstractGraphPoint> subGraph = prevDayGraph.replicateGraph();
                                subGraph.clear();
                                subGraph.addAll(subSet);
                                //Find earliest low
                                AbstractGraphPoint earliestLow = subGraph.getEarliestLow();
                                //First do we have sufficient trading day data to make the determination? If not we will need to load more
                                int firstPointAsNumber = firstPoint.getDateAsNumber();
                                int earliestLowAsNumber = earliestLow.getDateAsNumber();
                                //To have complete data we must have the earliestLowAsNumber value in our trading days list
                                TreeSet<Integer> td = DTConstants.TRADINGDAYSLASTWEEK;
                                if (null != td && td.contains(earliestLowAsNumber)) {
                                    //We can make the determination
                                    double part1 = DTUtil.getExchClosingCalendar(earliestLow.getCalDate()).getTimeInMillis() - earliestLow.getCalDate().getTimeInMillis();
                                    double part2 = firstPoint.getCalDate().getTimeInMillis() - DTUtil.getExchOpeningCalendar(firstPoint.getCalDate()).getTimeInMillis();
                                    double part3 = 0;
                                    NavigableSet<Integer> tdSubSet = td.subSet(earliestLowAsNumber, false, firstPointAsNumber, false);
                                    if (0 < tdSubSet.size()) {
                                        //Work out time in a trading day
                                        long timePerDay = DTUtil.getExchClosingCalendar(firstPoint.getCalDate()).getTimeInMillis() - DTUtil.getExchOpeningCalendar(firstPoint.getCalDate()).getTimeInMillis();
                                        part3 = timePerDay * tdSubSet.size();
                                    }
                                    dblDuration = part1 + part2 + part3;
                                } else {
                                    //We need more trading day data load it
                                    Calendar endDate = firstPoint.getCalDate();
                                    Calendar exchOpeningCalendar = DTUtil.getExchOpeningCalendar(endDate);
                                    Putup putup = this.graph.getPutup();
                                    if (exchOpeningCalendar.getTimeInMillis() < endDate.getTimeInMillis()) {
                                        RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                                        LoadTradingDaysTask loadTask = new LoadTradingDaysTask(putup, endDate, runManager);
                                        this.requestMoreData(loadTask);
                                    }
                                }
                            } else {
                                //Final Duration is equal to the current winning duration
                                dblDuration = winningXScore;
                            }
                        } else {
                            Double neededSecs = winXScoreInSec - prevDayGraph.size();
                            //We need to load more data to exceed the winning X score
                            if (neededSecs < (30 * 60)) {   //30 Min of 60 sec just add one batch
                                //Start the loading task
                                RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                                Putup putup = this.graph.getPutup();
                                AbstractGraphPoint lastLoadedPoint = prevDayGraph.first();
                                Calendar endDate = lastLoadedPoint.getCalDate();
                                LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(putup, endDate, runManager);
                                this.requestMoreData(loadTask);
                            } else {
                                //We need more than 1 data batch load this
                                //Start the loading task
                                RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                                Putup putup = this.graph.getPutup();
                                AbstractGraphPoint lastLoadedPoint = prevDayGraph.first();
                                Calendar endDate = lastLoadedPoint.getCalDate();
                                LoadXSecOfHistoricDataTask loadTask = new LoadXSecOfHistoricDataTask(neededSecs.intValue(), putup, endDate, runManager);
                                this.requestMoreData(loadTask);
                            }
                        }
                    } else {
                        //We must load enough of yesterdays data to make the determination (i.e. a number of seconds at least equal to curr greatest duration - winningXScore)
                        if (winXScoreInSec < (30 * 60)) {   //30 Min of 60 sec just add one batch
                            //We must load the previous days graph (first 30 min)
                            //Start the loading task
                            RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                            Putup putup = this.graph.getPutup();
                            TreeSet<Integer> tradingDays = this.graph.getTradingDays();
                            //As the prevDayGraph is null load the first 30 min of the previous day
                            Integer prevDayIntCode = tradingDays.last();
                            Calendar endDate = DTUtil.convertIntDateToCalendar(prevDayIntCode, DTConstants.EXCH_TIME_ZONE);
                            //Move endDate time to the end of trading on this day
                            endDate = DTUtil.getExchClosingCalendar(endDate);
                            LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(putup, endDate, runManager);
                            this.requestMoreData(loadTask);
                        } else {
                            //We need more than 1 data batch load this
                            //Start the loading task
                            RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                            Putup putup = this.graph.getPutup();
                            //As the prevDayGraph is null load the first 30 min of the previous day
                            TreeSet<Integer> tradingDays = this.graph.getTradingDays();
                            Integer prevDayIntCode = tradingDays.last();
                            Calendar endDate = DTUtil.convertIntDateToCalendar(prevDayIntCode, DTConstants.EXCH_TIME_ZONE);
                            Double dblSec = winXScoreInSec;
                            LoadXSecOfHistoricDataTask loadTask = new LoadXSecOfHistoricDataTask(dblSec.intValue(), putup, endDate, runManager);
                            this.requestMoreData(loadTask);
                        }
                    }
                }
            }

            //BRYN CHANGED THE APPROACH TO CALCULATION THE DURATION BUT HAVE KEPT THE OLD CODE

//            if (!winOnX) {
//                //What is the largest current duration? This is the target to equal or beat
//                double winningXScore = greatestX.getXScore();
//                boolean blnContinueChecks = true;
//                AbstractGraphPoint prevDayClose = this.graph.getPrevDayClose();
//                AbstractGraphPoint firstPoint = highOfDay;
//                if (null != prevDayClose) {
//                    if (prevDayClose.getLastPrice() > firstPoint.getLastPrice()) {
//                        //Calculate a duration this is the time betwen the first point (high of the day) and the lowest
//                        //point between that high and the START of the day
//                        //1) subset graph from start of day to the first point.
//                        NavigableSet<AbstractGraphPoint> subSet = this.graph.subSet(this.graph.first(), true, firstPoint, true);
//                        BaseGraph<AbstractGraphPoint> subGraph = this.graph.replicateGraph();
//                        subGraph.clear();
//                        subGraph.addAll(subSet);
//                        //2) Determine the earliest low on the sub graph
//                        AbstractGraphPoint earliestLow = subGraph.getEarliestLow();
//                        //3) Duration is diff in time between firstPoint and earliest low
//                        dblDuration = firstPoint.getTimestamp() - earliestLow.getTimestamp();
//                        blnContinueChecks = false;
//                    }
//                }
//                if (blnContinueChecks) {
//                    //Looking at the prev day close does not resolve the duration (it is not higher than the high of the day)
//                    //We now need to scan back through the previous days data (loading if necessary) until EITHER
//                    //1) We find a point higher than the first point (in price) OR the currently measured duration is GREATER THAN
//                    //OR EQUAL TO the current largest duration.
//                    //Do we have yesterdays data??
//                    BaseGraph<AbstractGraphPoint> prevDayGraph = this.graph.getPrevDayGraph();
//                    if (null != prevDayGraph) {
//                        dblDuration = this.calcDuration(firstPoint, prevDayGraph, winningXScore);//firstPoint.getTimestamp() - earliestLow.getTimestamp();
//                        //If the above function call does not throw an exception we have a final duration. If it does throw an exception then additional data will be loading
//                        blnContinueChecks = false;
//                    } else {
//                        //We must load the previous days graph (first 30 min)
//                        //Start the loading task
//                        RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
//                        Putup putup = this.graph.getPutup();
//                        TreeSet<Integer> tradingDays = this.graph.getTradingDays();
//                        //As the prevDayGraph is null load the first 30 min of the previous day
//                        Integer prevDayIntCode = tradingDays.last();
//                        Calendar endDate = DTUtil.convertIntDateToCalendar(prevDayIntCode, DTConstants.EXCH_TIME_ZONE);
//                        //Move endDate time to the end of trading on this day
//                        endDate = DTUtil.getExchClosingCalendar(endDate);
//                        LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(putup, endDate, runManager);
//                        this.requestMoreData(loadTask);
//                    }
//                }
//            }
            //Recording the new price and duration goes here
            //The variables dblPBVal and dblDuration now hold the values that must be updated for the provisionalPoint
            provisionalPoint.setPb3Value(dblPBVal);
            provisionalPoint.setDuration(dblDuration);

            //To find if an SD exists compile two sorted lists of PB (Y) Values and Duration X values
            TreeSet<PointsCEFormulaData> finalYTable = new TreeSet<PointsCEFormulaData>();
            for (PointsCEFormulaData currItem : PBValOrder) {
                finalYTable.add(currItem);
            }
            TreeSet<PointsCEFormulaData> finalXTable = new TreeSet<PointsCEFormulaData>(PointsCEFormulaData.DurationComparator);
            for (PointsCEFormulaData currItem : durationOrder) {
                finalXTable.add(currItem);
            }
            PointsCEFormulaData yWinner = finalYTable.last();
            PointsCEFormulaData xWinner = finalXTable.last();

            //If the Y and X winner is the same then this is an SD
            if (yWinner.equals(xWinner)) {
                result = yWinner;                   //Either winner would do they are the same
            }

            //Now is their a result and if so is it the provisional point we have been testing above
            if (null != result) {
                if (provisionalPoint.equals(result)) {
                    //This SD is also the provisional point in the list passed to this function
                    result.setFirstPoint(true);
                }
            }
        }
        return result;
    }

    /**
     * DEPRICATED AS BRYN CHANGED APPROACH FOR DURATION CALCULATION Given a
     * Graph this function loads an additional X seconds of data. It will always
     * throw a LoadingAdditionalDataException after submitting the request
     */
    private void loadXSecOfData(int intSecToAdd, BaseGraph<AbstractGraphPoint> targetGraph) throws LoadingAdditionalDataException {
        if (0 < intSecToAdd && null != targetGraph) {
            Putup putup = targetGraph.getPutup();
            RealTimeRunManager runManager = putup.getRunManager();
            AbstractGraphPoint lastLoadedPoint = targetGraph.first();
            Calendar endDate = lastLoadedPoint.getCalDate();

        } else {
            throw new UnsupportedOperationException("Invalid parameters passed to loadXSecOfData in C-E Calc");
        }
    }

    /**
     * DEPRICATED AS BRYN CHANGED APPROACH FOR DURATION CALCULATION This
     * function calculates the 'current duration' for the first point and checks
     * if it can be treated as a final duration, if it needs more data to do
     * this then it loads it, if it can make the determination of a final
     * duration value it returns it if not then it will have thrown an exception
     * (either LoadingAdditionalDataException OR UnsupportedOperationException
     * if parameters are invalid.
     */
    private double calcDuration(AbstractGraphPoint firstPoint, BaseGraph<AbstractGraphPoint> currGraph, double winningXScore) throws LoadingAdditionalDataException {
        double result = 0;
        if (null != firstPoint && null != currGraph && 0 < currGraph.size()) {
            //Is there a higher point than the firstPoint?
            AbstractGraphPoint higherOrEqualHigh = null;
            for (AbstractGraphPoint currPoint : currGraph) {
                if (currPoint.getLastPrice() >= firstPoint.getLastPrice()) {
                    higherOrEqualHigh = currPoint;
                    break;
                }
            }
            if (null != higherOrEqualHigh) {
                //We have found a point higher than the first point we can determine an exact (i.e. FINAL) duration
                //Subset the graph from the high point to end of day and find the earliest low
                NavigableSet<AbstractGraphPoint> subSet = currGraph.subSet(higherOrEqualHigh, true, currGraph.last(), true);
                BaseGraph<AbstractGraphPoint> subGraph = currGraph.replicateGraph();
                subGraph.clear();
                subGraph.addAll(subSet);
                //2) Determine the earliest low on the sub graph
                AbstractGraphPoint earliestLow = subGraph.getEarliestLow();
                //3) Duration is diff in time between firstPoint and earliest low however we need to account for non-trading time periods
                //Overnight, Weekends, Bank Holidays & Extraordinary events (Planes into WTC, Boston Bombings etc) so we must use trading days
                //to determine the exact amount of time the market was trading for.
                //Three parts to this:
                //Part 1: earliest low to end of its day (must have been a trading day)
                //Part 2: Start of day to firstPoint (must have been a trading day)
                //Part 3: for every trading day between these points add a days worth of time
                //First do we have sufficient trading day data to make the determination? If not we will need to load more
                int firstPointAsNumber = firstPoint.getDateAsNumber();
                int earliestLowAsNumber = earliestLow.getDateAsNumber();
                //To have complete data we must have the earliestLowAsNumber value in our trading days list
                TreeSet<Integer> td = DTConstants.TRADINGDAYSLASTWEEK;
                if (null != td && td.contains(earliestLowAsNumber)) {
                    //We can make the determination
                    double part1 = DTUtil.getExchClosingCalendar(earliestLow.getCalDate()).getTimeInMillis() - earliestLow.getCalDate().getTimeInMillis();
                    double part2 = firstPoint.getCalDate().getTimeInMillis() - DTUtil.getExchOpeningCalendar(firstPoint.getCalDate()).getTimeInMillis();
                    double part3 = 0;
                    NavigableSet<Integer> tdSubSet = td.subSet(earliestLowAsNumber, false, firstPointAsNumber, false);
                    if (0 < tdSubSet.size()) {
                        //Work out time in a trading day
                        long timePerDay = DTUtil.getExchClosingCalendar(firstPoint.getCalDate()).getTimeInMillis() - DTUtil.getExchOpeningCalendar(firstPoint.getCalDate()).getTimeInMillis();
                        part3 = timePerDay * tdSubSet.size();
                    }
                    result = part1 + part2 + part3;
                } else {
                    //We need more trading day data load it
                    Calendar endDate = firstPoint.getCalDate();
                    Calendar exchOpeningCalendar = DTUtil.getExchOpeningCalendar(endDate);
                    Putup putup = this.graph.getPutup();
                    if (exchOpeningCalendar.getTimeInMillis() < endDate.getTimeInMillis()) {
                        RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                        LoadTradingDaysTask loadTask = new LoadTradingDaysTask(putup, endDate, runManager, DTDurationEnum.M1);
                        this.requestMoreData(loadTask);
                    }
                }
            } else {
                //We have NOT found a higher point than the first point now determine the duration and see if it is GREATER THAN OR EQUAL TO the 'winning' duration
                //If it is we have a usable duration IF it is not then we need to load more data and try again
                //Use the earliest low of the graph as the end of the duration
                AbstractGraphPoint earliestLow = currGraph.getEarliestLow();
                //First do we have sufficient trading day data to make the determination? If not we will need to load more
                int firstPointAsNumber = firstPoint.getDateAsNumber();
                int earliestLowAsNumber = earliestLow.getDateAsNumber();
                //To have complete data we must have the earliestLowAsNumber value in our trading days list
                TreeSet<Integer> td = DTConstants.TRADINGDAYSLASTWEEK;
                if (null != td && td.contains(earliestLowAsNumber)) {
                    //We can make the determination
                    double part1 = DTUtil.getExchClosingCalendar(earliestLow.getCalDate()).getTimeInMillis() - earliestLow.getCalDate().getTimeInMillis();
                    double part2 = firstPoint.getCalDate().getTimeInMillis() - DTUtil.getExchOpeningCalendar(firstPoint.getCalDate()).getTimeInMillis();
                    double part3 = 0;
                    NavigableSet<Integer> tdSubSet = td.subSet(earliestLowAsNumber, false, firstPointAsNumber, false);
                    if (0 < tdSubSet.size()) {
                        //Work out time in a trading day
                        long timePerDay = DTUtil.getExchClosingCalendar(firstPoint.getCalDate()).getTimeInMillis() - DTUtil.getExchOpeningCalendar(firstPoint.getCalDate()).getTimeInMillis();
                        part3 = timePerDay * tdSubSet.size();
                    }
                    double provDuration = part1 + part2 + part3;
                    if (provDuration >= winningXScore) {
                        //We have a longer duration this is the finished result
                        result = provDuration;
                    } else {
                        //We need to load another 30 min batch of data as the curr dutation is less than the winning duration
                        AbstractGraphPoint lastDataPoint = currGraph.first();
                        Calendar endDate = lastDataPoint.getCalDate();
                        Calendar exchOpeningCalendar = DTUtil.getExchOpeningCalendar(endDate);
                        Putup putup = this.graph.getPutup();
                        if (exchOpeningCalendar.getTimeInMillis() < endDate.getTimeInMillis()) {
                            RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                            LoadHistoricDataBatchTask loadTask = new LoadHistoricDataBatchTask(putup, endDate, runManager);
                            this.requestMoreData(loadTask);
                        }
                    }
                } else {
                    //We need more trading day data load it
                    Calendar endDate = firstPoint.getCalDate();
                    Calendar exchOpeningCalendar = DTUtil.getExchOpeningCalendar(endDate);
                    Putup putup = this.graph.getPutup();
                    if (exchOpeningCalendar.getTimeInMillis() < endDate.getTimeInMillis()) {
                        RealTimeRunManager runManager = this.graph.getPutup().getRunManager();
                        LoadTradingDaysTask loadTask = new LoadTradingDaysTask(putup, endDate, runManager, DTDurationEnum.M1);
                        this.requestMoreData(loadTask);
                    }
                }
            }
        } else {
            throw new UnsupportedOperationException("Invalid call to calcDuration in C-E Lines");
        }
        return result;
    }

    @Override
    protected boolean runPrimaryRule() throws LoadingAdditionalDataException {
        boolean result = false;
        try {
            //Generate C-E Lines, this should find 'winning' C-E Line
            ArrayList<IGraphLine> generateLines = this.generateLines();
            //If the generatedLines array has a single entry then we have a winning C-E line
//            if (1 == generateLines.size()) {
//                result = true;
//            }
            //It turns out that there can be more than one C-E Line so if we have any entries we succeeded
            if (0 < generateLines.size()) {
                result = true;
            }
        } catch (LoadingAdditionalDataException ex) {
            //Pass this exception out to the rules framework (rule will fail but data loading will be underway)
            throw ex;
        } catch (Exception ex) {
            //In the event of any other exception this rule should fail
            ex.printStackTrace();
            result = false;
        }
        return result;
    }

    /**
     * @return the finalCELines
     */
    public ArrayList<IGraphLine> getFinalCELines() {
        return finalCELines;
    }

    /**
     * @return the ceFormulaDataCache
     */
    public ArrayList<GraphLinePlusCEFormulaeData> getCeFormulaDataCache() {
        ArrayList<GraphLinePlusCEFormulaeData> result = new ArrayList<GraphLinePlusCEFormulaeData>();
        if (null != this.ceFormulaDataCache) {
            result.addAll(this.ceFormulaDataCache);
        }
        return result;
    }

    private class PointValidationDetails {

        private AbstractGraphPoint firstPoint;
        private AbstractGraphPoint validatingPoint;
        private boolean removed = false;

        public PointValidationDetails(AbstractGraphPoint newFirstPoint, AbstractGraphPoint newValidatingPoint) {
            this.firstPoint = newFirstPoint;
            this.validatingPoint = newValidatingPoint;
        }

        public PointValidationDetails(AbstractGraphPoint newFirstPoint, AbstractGraphPoint newValidatingPoint, boolean wasRemoved) {
            this.firstPoint = newFirstPoint;
            this.validatingPoint = newValidatingPoint;
        }

        /**
         * @return the firstPoint
         */
        public AbstractGraphPoint getFirstPoint() {
            return firstPoint;
        }

        /**
         * @param firstPoint the firstPoint to set
         */
        public void setFirstPoint(AbstractGraphPoint firstPoint) {
            this.firstPoint = firstPoint;
        }

        /**
         * @return the validatingPoint
         */
        public AbstractGraphPoint getValidatingPoint() {
            return validatingPoint;
        }

        /**
         * @param validatingPoint the validatingPoint to set
         */
        public void setValidatingPoint(AbstractGraphPoint validatingPoint) {
            this.validatingPoint = validatingPoint;
        }

        /**
         * @return the removed
         */
        public boolean isRemoved() {
            return removed;
        }
    }

    //The performance of the filter out blocked lines method is FAR TO SLOW in some cases
    //Need to pre-compute GraphLine gradients before the filtering code runs. Will do this
    //multi-threaded to speed up the function
    //This class takes a GraphLine and makes it compute its gradient
    private class CompGradients implements Callable<Double> {

        private ArrayList<IGraphLine> jobList;

        public CompGradients(IGraphLine target) {
            this.jobList = new ArrayList<IGraphLine>();
            this.jobList.add(target);
        }

        public CompGradients(ArrayList<IGraphLine> targets) {
            this.jobList = new ArrayList<IGraphLine>();
            for (IGraphLine target : targets) {
                this.jobList.add(target);
            }
        }

        @Override
        public Double call() throws Exception {
            Double result = null;
            if (null != this.jobList) {
                for (int i = 0; i < this.jobList.size(); i++) {
                    result = this.jobList.get(i).getGradient();
                }
            }
            return result;
        }
    }

    private class PBValueCalcCPoints implements Callable<PBValueCalcResult> {

        private BaseGraph<AbstractGraphPoint> graph;
        private IGraphLine aLine;

        public PBValueCalcCPoints(IGraphLine currLine, BaseGraph<AbstractGraphPoint> targetGraph) {
            this.graph = targetGraph;
            this.aLine = currLine;
        }

        @Override
        public PBValueCalcResult call() throws Exception {
            PBValueCalcResult result = null;

            AbstractGraphPoint startPoint;
            AbstractGraphPoint currPoint;
            boolean blnIncludeInResults = true;
            double priceAtTime = 0;
            startPoint = this.aLine.getStartPoint();
            NavigableSet<AbstractGraphPoint> subGraph = this.graph.subSet(startPoint, false, this.aLine.getEndPoint(), true);
            Iterator<AbstractGraphPoint> iterator = subGraph.iterator();
            blnIncludeInResults = true;
            while (iterator.hasNext()) {
                currPoint = iterator.next();
                priceAtTime = this.aLine.getPriceAtTime(currPoint.getTimestamp());
                if (currPoint.getLastPrice() > priceAtTime) {
                    //Get PB value of this point
                    //Timer.setBaseTime();
                    int intPBValue = IdentifyPB3Points.findPBValue(this.graph, currPoint);
                    //Timer.printMsg("Finished (C) PB3 Calc");
                    if (intPBValue >= DTConstants.getScaledPBVALUE()) {
                        //This line is invalid, discard it and move to next
                        blnIncludeInResults = false;
                        break;
                    } else {
                        this.aLine.setStandInC(currPoint);
                    }
                }
            }
            result = new PBValueCalcResult(blnIncludeInResults, this.aLine);
            return result;
        }
    }

    private class PBValueCalcEPoints implements Callable<PBValueCalcResult> {

        private BaseGraph<AbstractGraphPoint> graph;
        private IGraphLine aLine;

        public PBValueCalcEPoints(IGraphLine currLine, BaseGraph<AbstractGraphPoint> targetGraph) {
            this.graph = targetGraph;
            this.aLine = currLine;
        }

        @Override
        public PBValueCalcResult call() throws Exception {
            PBValueCalcResult result = null;
            AbstractGraphPoint currPoint;
            boolean blnIncludeInResults;
            double priceAtTime = 0;
            NavigableSet<AbstractGraphPoint> subGraph = this.graph.subSet(this.aLine.getEndPoint(), false, this.graph.getLowestPointSoFar(), true);
            Iterator<AbstractGraphPoint> iterator = subGraph.iterator();
            blnIncludeInResults = true;
            while (iterator.hasNext()) {
                blnIncludeInResults = true;
                currPoint = iterator.next();
                priceAtTime = this.aLine.getPriceAtTime(currPoint.getTimestamp());
                if (currPoint.getLastPrice() > priceAtTime) {
                    //Get PB value of this point
                    //Timer.setBaseTime();
                    int intPBValue = IdentifyPB3Points.findPBValue(this.graph, currPoint);
                    //Timer.printMsg("Finished (E) PB3 Calc");
                    if (intPBValue >= DTConstants.getScaledPBVALUE()) {
                        //This line is invalid, discard it and move to next
                        blnIncludeInResults = false;
                        break;
                    } else {
                        this.aLine.setStandInE(currPoint);
                    }
                }
            }
            result = new PBValueCalcResult(blnIncludeInResults, this.aLine);
            return result;
        }
    }

    private class PBValueCalcResult {

        private boolean includeInResults;
        private IGraphLine line;

        public PBValueCalcResult(boolean include, IGraphLine aLine) {
            this.includeInResults = include;
            this.line = aLine;
        }

        /**
         * @return the includeInResults
         */
        public boolean isIncludeInResults() {
            return includeInResults;
        }

        /**
         * @return the line
         */
        public IGraphLine getLine() {
            return line;
        }
    }

    /**
     * Applies the CE selection formula to a list of potential CE lines and eliminates those that do 
     * not survive the formula test
     * @param lineList - The ArrayList of CE Lines to be tested using the CE Formula
     * @return  - The ArrayList of CE Lines that survived the formula test.
     */
    public static ArrayList<GraphLinePlusCEFormulaeData> applyCESelectionFormula(ArrayList<GraphLinePlusCEFormulaeData> lineList) {
        ArrayList<GraphLinePlusCEFormulaeData> result = new ArrayList<GraphLinePlusCEFormulaeData>();
        if (null != lineList && 0 < lineList.size()) {
            ArrayList<GraphLinePlusCEFormulaeData> deadCELines = new ArrayList<GraphLinePlusCEFormulaeData>();
            for (GraphLinePlusCEFormulaeData currCELine : lineList) {
                for (GraphLinePlusCEFormulaeData targetCELine : lineList) {
                    //Ensure I do not test the line against itself
                    boolean survived = true;
                    if (currCELine != targetCELine) {
                        survived = currCELine.survivesAgainst(targetCELine);
                        if (!survived) {
                            deadCELines.add(currCELine);
                            break;
                        }
                    }
                }
            }
            //Now build result as original list of lines less the deadCELines
            result.addAll(lineList);
            result.removeAll(deadCELines);
        }
        return result;
    }
}
