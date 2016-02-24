/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.util.Comparator;

/**
 * Compares two abstract graph points by their price (Y) value
 * @param <T> Any Price / Time point
 * @author Roy
 */
public class GraphPointPriceComparator<T extends AbstractGraphPoint> implements Comparator<T> {

    @Override
    public int compare(AbstractGraphPoint o1, AbstractGraphPoint o2) {
        int result = 0;
        if(o1.getLastPrice() < o2.getLastPrice()){
            result = -1;
        }else if(o1.getLastPrice() > o2.getLastPrice()){
            result = 1;
        }
        return result;
    }
    
}
