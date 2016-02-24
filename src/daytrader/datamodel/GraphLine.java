/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphLine;
import daytrader.utils.DTUtil;
import java.util.Calendar;
import java.util.TreeSet;

/**
 * This class represents a trend line on a graph it is a base concrete implementation
 * of the IGraphLine interface.
 *
 * @author Roy
 */
public class GraphLine implements IGraphLine {

    private AbstractGraphPoint startPoint;
    private AbstractGraphPoint endPoint;
    private BaseGraph<AbstractGraphPoint> graph;
    private AbstractGraphPoint standInC;
    private AbstractGraphPoint standInE;
    private Double dblGradientCache;
    //If a line reaches across more than one trading day we must use only the time 'in a trading day' when calculating the gradient
    //This attribute maintains a list of trading days. It does not have to be set but gradient calcs across multiple days will not work
    //if it is not
    private TreeSet<Integer> tradingDays;

    /**
     * Constructor defines a new Graph Line with the given start and end points and associates it with the provided graph
     * @param newStartPoint - The price / time point that represents the start of the graph
     * @param newEndPoint - The price / time point that represents the end of the graph
     * @param newGraph - The BaseGraph object on which this Graph Line is 'drawn'
     */
    public GraphLine(AbstractGraphPoint newStartPoint, AbstractGraphPoint newEndPoint, BaseGraph<AbstractGraphPoint> newGraph) {
        this.startPoint = newStartPoint;
        this.endPoint = newEndPoint;
        this.graph = newGraph;
    }

    private GraphLine(AbstractGraphPoint newStartPoint, AbstractGraphPoint newEndPoint, BaseGraph<AbstractGraphPoint> newGraph, AbstractGraphPoint standInC, AbstractGraphPoint standInE) {
        this.startPoint = newStartPoint;
        this.endPoint = newEndPoint;
        this.graph = newGraph;
        this.standInC = standInC;
        this.standInE = standInE;
    }

    @Override
    public double getGradient() {
        double result = 0.0d;
        if (this.isValid()) {
            if (null == this.dblGradientCache) {
                //Get the start point (original or stand in)
                AbstractGraphPoint myCurrStart = this.getCurrentC();
                AbstractGraphPoint myCurrEnd = this.getCurrentE();
                //IF start and end are the same day THEN a normal gradient calc can occur
                if (myCurrStart.getDateAsNumber() == myCurrEnd.getDateAsNumber()) {
                    //Calc Change in X
                    double deltaX = myCurrEnd.getTimestamp() - myCurrStart.getTimestamp();
                    //deltaX /= 1000; //Convert from milliseconds to seconds
                    double deltaY = myCurrEnd.getLastPrice() - myCurrStart.getLastPrice();
                    result = deltaY / deltaX;
                    this.dblGradientCache = result;
                    System.out.println("Delta Y = " + deltaY);
                    System.out.println("Delta X = " + deltaX);
                } else {
                    if (null != this.tradingDays) {
                        TreeSet<AbstractGraphPoint> tradingDayData = new TreeSet<AbstractGraphPoint>();
                        for (Integer currDay : this.tradingDays) {
                            String val = currDay.toString();
                            int length = val.length();
                            String strDay = val.substring(length - 2);
                            String strMonth = val.substring(length - 4, length - 2);
                            String strYear = val.substring(0, length - 4);
                            int intDay = Integer.parseInt(strDay);
                            int intMonth = Integer.parseInt(strMonth);
                            intMonth--;
                            int intYear = Integer.parseInt(strYear);
                            Calendar cal = DTUtil.createCalendar(intYear, intMonth, intDay, DTConstants.EXCH_CLOSING_HOUR, DTConstants.EXCH_CLOSING_MIN, DTConstants.EXCH_CLOSING_SEC, DTConstants.EXCH_TIME_ZONE);
                            DummyGraphPoint newPoint = new DummyGraphPoint(cal);
                            tradingDayData.add(newPoint);
                        }
                        result = DTUtil.getGraidentBasedOnTradingDays(this, tradingDayData);
                        this.dblGradientCache = result;
                    } else {
                        this.dblGradientCache = null;
                        throw new IllegalArgumentException("Trading days missing for gradient calculation");
                    }
                }
            } else {
                //No need to work out the gradient we already have it, return the cache
                result = this.dblGradientCache.doubleValue();
            }
        }
        return result;
    }

    @Override
    public double yInterceptPrice() {
        double result = 0;
        if (this.isValid()) {
            //Get the start point (original or stand in)
            AbstractGraphPoint myCurrStart = this.getCurrentC();
            //Find the y value at zero time on the graph
            result = myCurrStart.getLastPrice();
        }
        return result;
    }

    @Override
    public double xInterceptTime() {
        double result = 0;
        if (this.isValid() && this.getLinedirection() != LineDirectionEnum.HORIZONTAL) {
            //Find the x value on the graph where the price is 0
            //Determine b
            double b = this.yInterceptPrice();
            double gradient = this.getGradient();
            double timesteps = b / gradient;
            switch (this.getLinedirection()) {
                case FALLING:
                    result = this.getCurrentC().getTimestamp() + timesteps;
                    break;
                case RISING:
                    result = this.getCurrentC().getTimestamp() - timesteps;
                    break;
            }
        }
        return result;
    }

    @Override
    public double getPriceAtTime(long timestamp) {
        double result = 0;
        if (this.isValid()) {
            //Get the start point (original or stand in)
            AbstractGraphPoint myCurrStart = this.getCurrentC();
            long deltaTime = timestamp - myCurrStart.getTimestamp();
            result = myCurrStart.getLastPrice();
            if (this.getLinedirection() != LineDirectionEnum.HORIZONTAL) {
                double b = this.yInterceptPrice();
                result = (this.getGradient() * deltaTime) + b;
            }
        }
        return result;
    }

    @Override
    public AbstractGraphPoint getPointAtTime(long timestamp) {
        AbstractGraphPoint result = null;
        if (this.isValid()) {
            double price = this.getPriceAtTime(timestamp);
            result = new HistoricDataGraphPoint(timestamp, price);
        }
        return result;
    }

    @Override
    public LineDirectionEnum getLinedirection() {
        double gradient = this.getGradient();
        LineDirectionEnum result;
        if (0 == gradient) {
            result = LineDirectionEnum.HORIZONTAL;
        } else if (gradient > 0) {
            result = LineDirectionEnum.RISING;
        } else {
            result = LineDirectionEnum.FALLING;
        }
        return result;
    }

    @Override
    public boolean isValid() {
        boolean result = false;
        if (null != this.startPoint && null != this.endPoint && null != graph) {
            if (this.startPoint.getTimestamp() < this.endPoint.getTimestamp()) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public AbstractGraphPoint getStartPoint() {
        return startPoint;
    }

    @Override
    public void setStartPoint(AbstractGraphPoint startPoint) {
        this.startPoint = startPoint;
        this.dblGradientCache = null;
    }

    @Override
    public AbstractGraphPoint getEndPoint() {
        return endPoint;
    }

    @Override
    public void setEndPoint(AbstractGraphPoint endPoint) {
        this.endPoint = endPoint;
        this.dblGradientCache = null;
    }

    @Override
    public BaseGraph<AbstractGraphPoint> getGraph() {
        return graph;
    }

    @Override
    public void setGraph(BaseGraph<AbstractGraphPoint> graph) {
        this.graph = graph;
        this.dblGradientCache = null;
    }

    @Override
    public AbstractGraphPoint getStandInC() {
        return standInC;
    }

    @Override
    public void setStandInC(AbstractGraphPoint standInC) {
        if (null != standInC) {
            if (standInC.getTimestamp() > this.startPoint.getTimestamp()) {
                if (standInC.getTimestamp() < this.endPoint.getTimestamp()) {
                    this.standInC = standInC;
                    this.dblGradientCache = null;
                }
            }
        }
    }

    @Override
    public AbstractGraphPoint getCurrentC() {
        AbstractGraphPoint result = this.startPoint;
        if (null != this.standInC) {
            result = this.standInC;
        }
        return result;
    }

    @Override
    public AbstractGraphPoint getStandInE() {
        return standInE;
    }

    @Override
    public void setStandInE(AbstractGraphPoint standInE) {
        if (null != standInE) {
            if (standInE.getTimestamp() > this.endPoint.getTimestamp()) {
                if (standInE.getTimestamp() > this.startPoint.getTimestamp()) {
                    this.standInE = standInE;
                    this.dblGradientCache = null;
                }
            }
        }
    }

    @Override
    public AbstractGraphPoint getCurrentE() {
        AbstractGraphPoint result = this.endPoint;
        if (null != this.standInE) {
            result = this.standInE;
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder msg = new StringBuilder("C Point = ");
        msg.append(this.getCurrentC().toString());
        msg.append(", E Point = ");
        msg.append(this.getCurrentE().toString());
        return msg.toString();
    }

    /**
     * @param tradingDays the tradingDays to set
     */
    @Override
    public void setTradingDays(TreeSet<Integer> tradingDays) {
        if (null != tradingDays) {
            this.tradingDays = new TreeSet<Integer>(tradingDays);
        } else {
            this.tradingDays = null;
        }
        this.dblGradientCache = null;
    }

    @Override
    public IGraphLine deepCopyLine() {
        IGraphLine result = null;
        result = new GraphLine(startPoint, endPoint, graph, standInC, standInE);
        if (null != this.tradingDays) {
            result.setTradingDays(tradingDays);
        }
        return result;
    }

    /**
     * @return the tradingDays
     */
    @Override
    public TreeSet<Integer> getTradingDays() {
        return new TreeSet<Integer>(this.tradingDays);
    }

    @Override
    public boolean isACPoint(AbstractGraphPoint potentialCPoint) {
        boolean result = false;
        if (null != potentialCPoint) {
            if ((null != this.startPoint && this.startPoint.equals(potentialCPoint)) || (null != this.standInC && this.standInC.equals(potentialCPoint))) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public AbstractGraphPoint getIntersect(IGraphLine targetLine) {
        AbstractGraphPoint result = null;
        if (null != targetLine) {
            //Equation of a line is y = mx+b
            //Where y is the WAP
            //m is the gradient
            //x is the timestamp and
            //b is a constant defined as y - mx
            //Let b1 refer to this lines b value and b2 refer to the target (and so on for other variables)
            //Gradients of the two lines (m1 and m2)
            double m1 = this.getGradient();
            double m2 = targetLine.getGradient();
            //m3 is defined as m1-m2
            double m3 = m1 - m2;
            //Use the start point of each line to calculate its b value
            double b1 = this.getCurrentC().getWAP() - (m1 * this.getCurrentC().getTimestamp());
            double b2 = targetLine.getCurrentC().getWAP() - (m2 * targetLine.getCurrentC().getTimestamp());
            //x is defined as (b2-b1) / m3 (x is the timestamp of the intersect
            Double x = (b2 - b1) / m3;
            //To find the intersecting WAP (y) simply plug x into the equation for either line (I use 'this' line)
            double y = m1 * x + b1;
            //To create a point x must be a 'long' convert to long value and generate the intersect point
            result = new DummyGraphPoint(x.longValue(), y);
        }
        return result;
    }

    @Override
    public int compareTo(IGraphLine o) {
        //For the purpose of comparing 2 lines they will be considered equal if the current C and current E are equal
        int result = 0;
        if (this.getCurrentC().equals(o.getCurrentC()) && this.getCurrentE().equals(o.getCurrentE())) {
            result = 0;
        } else {
            //Ordering will be decided by when the lines 'start' per their start point those that start earlier are 'less than'
            //those that start later
            long thisTimestamp = this.getCurrentC().getTimestamp();
            long oTimestamp = o.getCurrentC().getTimestamp();
            long diffTime = thisTimestamp - oTimestamp;
            if (diffTime > 0) {
                result = 1;
            } else {
                result = -1;
            }
        }
        return result;
    }
}
