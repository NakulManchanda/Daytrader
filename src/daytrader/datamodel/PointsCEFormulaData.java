/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphLine;
import java.util.Comparator;

/**
 * This class encapsulates the CE Line formula 'play off' data for each point
 * involved in a CE Line. That is:
 * 1) The point to which this data applies,
 * 2) The exact pb value (the Y as Bryn calls it)
 * 3) The duration between the points time and the earliest low of the day (in milliseconds)
 * 4) The Graph Line that includes the point
 *
 * @author Roy
 */
public class PointsCEFormulaData implements Comparable<PointsCEFormulaData> {

    private AbstractGraphPoint point;
    private double pb3Value;
    private double duration;
    private IGraphLine sourceLine;
    private boolean firstPoint;

    /**
     * Constructor that accepts a price / time point and the PB value of this point
     * (which Bryn often refers to as Y) and the duration (time) between the EARLIEST
     * low of the day and the price / time point (Bryn often calls this X).
     * @param newPoint - An AbstractGraphPoint representing the price / time point
     * to which this data relates.
     * @param newPB3 - The exact PB value of the point (also known as the Y value)
     * @param newDuration - the time in milliseconds from the earliest low of the day to 
     * the provided price / time point.
     */
    public PointsCEFormulaData(AbstractGraphPoint newPoint, double newPB3, double newDuration) {
        this.point = newPoint;
        this.pb3Value = newPB3;
        this.duration = newDuration;
        this.firstPoint = false;
    }

    /**
     * Constructor that accepts a price / time point and the PB value of this point
     * (which Bryn often refers to as Y) and the duration (time) between the EARLIEST
     * low of the day and the price / time point (Bryn often calls this X). In addition
     * it also accepts a reference to the GraphLine from which the provided point comes
     * @param newPoint - An AbstractGraphPoint representing the price / time point
     * to which this data relates.
     * @param newPB3 - The exact PB value of the point (also known as the Y value)
     * @param newDuration - the time in milliseconds from the earliest low of the day to 
     * the provided price / time point.
     * @param newSource - The GraphLine object that is defined (in part) by the
     * newPoint argument.
     */
    public PointsCEFormulaData(AbstractGraphPoint newPoint, double newPB3, double newDuration, GraphLine newSource) {
        this(newPoint, newPB3, newDuration);
        this.sourceLine = newSource;
    }

    /**
     * Retrieves the point to which this data applies
     *
     * @return the point
     */
    public AbstractGraphPoint getPoint() {
        return point;
    }

    /**
     * Sets the point to which this data applies
     *
     * @param point the point to set
     */
    public void setPoint(AbstractGraphPoint point) {
        this.point = point;
    }

    /**
     * Retrieves the points PB3 Value
     *
     * @return the pb3Value
     */
    public double getPb3Value() {
        return pb3Value;
    }

    /**
     * Sets the points PB3 Value
     *
     * @param pb3Value the pb3Value to set
     */
    public void setPb3Value(double pb3Value) {
        this.pb3Value = pb3Value;
    }

    /**
     * Retrieves the duration in milliseconds from the start of the low to the
     * point
     *
     * @return the duration
     */
    public double getDuration() {
        return duration;
    }

    /**
     * Sets the duration in milliseconds from the start of the low to the point
     *
     * @param duration the duration to set
     */
    public void setDuration(double duration) {
        this.duration = duration;
    }

    /**
     * @return the sourceLine
     */
    public IGraphLine getSourceLine() {
        return sourceLine;
    }

    /**
     * @param sourceLine the sourceLine to set
     */
    public void setSourceLine(IGraphLine sourceLine) {
        this.sourceLine = sourceLine;
    }

    /**
     * Accessor method to retrieve the duration value for the price / time point
     * encapsulated in this class. Referred to as the X Score
     * as this is Bryns terminology when discussing the this value
     * @return double being the time in milliseconds from the EARLIEST LOW of 
     * the day to the time of the encapsulated point
     */
    public double getXScore() {
        return this.duration;
    }

    /**
     * Accessor method to retrieve the exact BP value associated with the 
     * price / time point encapsulated in this class. Referred to as the Y Score
     * as this is Bryns terminology when discussing the this value.
     * @return double being the PB value of the encapsulated point
     */
    public double getYScore() {
        return this.pb3Value;
    }

    @Override
    public int compareTo(PointsCEFormulaData o) {
        //By default I will compare on PBValue (thats Y score) with higher PBValue being 'more than' a lower PBValue
        return PointsCEFormulaData.PBValueComparator.compare(this, o);
    }
    
    /**
     * At various points it is necessary to order lists of these objects by their 
     * X (Duration) or Y (PB Value) scores. This comparator may be used to create a
     * list ordered by the X (Duration) score.
     */
    public static Comparator<PointsCEFormulaData> DurationComparator =
            new Comparator<PointsCEFormulaData>() {
        @Override
        public int compare(PointsCEFormulaData o1, PointsCEFormulaData o2) {
            int result = 0;
            if (null != o1 && null != o2) {
                double durationO1 = o1.getDuration();
                double durationO2 = o2.getDuration();
                double diff = durationO1 - durationO2;
                if (0 < diff) {
                    result = 1;
                } else if (0 > diff) {
                    result = -1;
                }
            }
            return result;
        }
    };
    
    /**
     * At various points it is necessary to order lists of these objects by their 
     * X (Duration) or Y (PB Value) scores. This comparator may be used to create a
     * list ordered by the Y (PB Value) score.
     */
    public static Comparator<PointsCEFormulaData> PBValueComparator =
            new Comparator<PointsCEFormulaData>() {
        @Override
        public int compare(PointsCEFormulaData o1, PointsCEFormulaData o2) {
            int result = 0;
            if (null != o1 && null != o2) {
                double diff = o1.getPb3Value() - o2.getPb3Value();
                if (0 < diff) {
                    result = 1;
                } else if (0 > diff) {
                    result = -1;
                }
                //IF result is still 0 then use the timestamp to order items
                if (0 == result) {
                    result = AbstractGraphPoint.TimeComparator.compare(o1.getPoint(), o2.getPoint());
                }
            }
            return result;
        }
    };

    /**
     * Accessor method used by the CE Line 'play off' formula. That formula can create
     * a point that Bryn terms an SD point which MAYBE the highest price point on
     * the securities graph. If the point encapsulated in this object is both the 
     * SD Point and the highest price point this boolean flag should be set to True
     * @return boolean True if the point encapsulated in this object is both the 
     * SD Point and the highest price point on its graph, False otherwise
     */
    public boolean isFirstPoint() {
        return firstPoint;
    }

    /**
     * Accessor method to set the value of the first point flag see the isFirstPoint()
     * method for a description of this flags use.
     * @param firstPoint boolean value to set for the flag
     */
    public void setFirstPoint(boolean firstPoint) {
        this.firstPoint = firstPoint;
    }
}
