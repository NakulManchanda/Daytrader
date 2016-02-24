/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphFlat;
import java.util.TreeSet;

/**
 * A concrete implementation of a Graph Flat (IGraphFlat interface). A time 
 * sequential group of graph points where the price value does not change.
 * Therefore when plotted on a price time graph these points appear as a 
 * horizontal straight line.
 * @author Roy
 */
public class GraphFlat implements IGraphFlat<AbstractGraphPoint>{
    
    private TreeSet<AbstractGraphPoint> pointList;
    
    /**
     * Constructor creates a Graph Flat with a single AbstractGraphPoint in its
     * time sequential list of graph points
     * @param firstPoint - The AbstractGraphPoint to include in the time sequential
     * list (usually the first point in the list).
     */
    public GraphFlat(AbstractGraphPoint firstPoint){
        this.pointList = new TreeSet<AbstractGraphPoint>();
        if(null != firstPoint){
            this.pointList.add(firstPoint);
        }
    }

    @Override
    public boolean addPoint(AbstractGraphPoint item) {
        //Only points with the same last price can be added to the flat
        boolean result = false;
        if(null != item && 0 < this.pointList.size()){
            AbstractGraphPoint last = this.pointList.last();
            double lastPrice = last.getLastPrice();
            if(lastPrice == item.getLastPrice()){
                result = this.pointList.add(item);
            }
        }
        return result;
    }

    @Override
    public int getFlatLength() {
        int result = 0;
        if(1 < this.pointList.size()){
            AbstractGraphPoint last = this.pointList.last();
            AbstractGraphPoint first = this.pointList.first();
            Long timeDiff = last.getTimestamp() - first.getTimestamp();
            //Difference will always be well below the max integer value
            if(timeDiff <= Integer.MAX_VALUE){
                result = timeDiff.intValue();
            }
        }
        return result;
    }

    @Override
    public double getFlatPrice() {
        double result = 0;
        if(0 < this.pointList.size()){
            result = this.pointList.first().getLastPrice();
        }
        return result;
    }

    @Override
    public boolean isXLong(int x) {
        boolean result = false;
        x *= 1000;                                                          //X is in seconds, convert to millseconds
        if(1 < this.pointList.size()){
            AbstractGraphPoint last = this.pointList.last();
            AbstractGraphPoint first = this.pointList.first();
            long timeDiff = last.getTimestamp() - first.getTimestamp();
            if(timeDiff == x){
                result = true;
            }
        } else {
            if(0 == x){
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean isAtLeastXLong(int x) {
        boolean result = false;
        x *= 1000;                                                          //X is in seconds, convert to millseconds
        if(1 < this.pointList.size()){
            AbstractGraphPoint last = this.pointList.last();
            AbstractGraphPoint first = this.pointList.first();
            long timeDiff = last.getTimestamp() - first.getTimestamp();
            if(x <= timeDiff){
                result = true;
            }
        } else {
            if(0 == x){
                result = true;
            }
        }
        return result;
    }

    @Override
    public AbstractGraphPoint getLatestPoint() {
        AbstractGraphPoint result = null;
        if(0 < this.pointList.size()){
            result = this.pointList.last();
        }
        return result;
    }

    @Override
    public AbstractGraphPoint getEarliestPoint() {
        AbstractGraphPoint result = null;
        if(0 < this.pointList.size()){
            result = this.pointList.first();
        }
        return result;
    }

    @Override
    public int compareTo(IGraphFlat o) {
        int result = 0;
        if(null != o){
            AbstractGraphPoint oLatestPoint = o.getLatestPoint();
            AbstractGraphPoint myLast = this.pointList.last();
            Long timeDiff = myLast.getTimestamp() - oLatestPoint.getTimestamp();
            //Should always be below max int size so convert directly
            result = timeDiff.intValue();
        }
        return result;
    }

    @Override
    public boolean isOnFlat(AbstractGraphPoint aPoint) {
        boolean result = false;
        if(null != aPoint){
            result = this.pointList.contains(aPoint);
        }
        return result;
    }
    
}
