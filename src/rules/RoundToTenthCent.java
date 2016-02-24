/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.interfaces.IRoundFunction;

/**
 * Performs a mathematically normal rounding operation to the nearest 1/10th of
 * a cent (assuming the number represents us dollars).
 * @author Roy
 */
public class RoundToTenthCent implements IRoundFunction<Number>{

    @Override
    public Number performRounding(Number value) {
        Double dblStartVal = value.doubleValue();
        dblStartVal *= 1000;
        dblStartVal = Math.rint(dblStartVal);
        dblStartVal /= 1000;
        return dblStartVal;
    }
    
}
