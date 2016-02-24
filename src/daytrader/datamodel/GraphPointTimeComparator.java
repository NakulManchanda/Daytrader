/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.util.Comparator;

/**
 * Compares two abstract graph points by their time (X) value
 *
 * @author Roy
 */
public class GraphPointTimeComparator implements Comparator<AbstractGraphPoint> {

    @Override
    public int compare(AbstractGraphPoint o1, AbstractGraphPoint o2) {
        int result = 0;
        long lngDiff = o1.getTimestamp() - o2.getTimestamp();
        if (0 < lngDiff) {
            result = -1;
        } else if (0 > lngDiff) {
            result = 1;
        }
        return result;
    }
}
