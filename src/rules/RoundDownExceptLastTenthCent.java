/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.interfaces.IRoundFunction;

/**
 * This function rounds down any value not within 1/10th of the next whole integer
 * For example 5.8 will round to 5 while 
 * 5.9 will round to 6
 * @author Roy
 */
public class RoundDownExceptLastTenthCent  implements IRoundFunction<Number>{

    @Override
    public Number performRounding(Number value) {
        Double dblStartVal = value.doubleValue();
        Integer intValue = dblStartVal.intValue();
        Double rem = dblStartVal - intValue;
        Integer result = intValue;
        if(rem >= 0.9d){
            result++;
        }
        return result;
    }
    
}
