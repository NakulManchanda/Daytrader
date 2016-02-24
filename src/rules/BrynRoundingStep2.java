/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.interfaces.IRoundFunction;

/**
 * Performs the second step of Bryn's rounding operations
 * @author Roy
 */
public class BrynRoundingStep2 implements IRoundFunction<Number>{

    @Override
    public Number performRounding(Number value) {
        Double dblValue = value.doubleValue();
        Integer intValue = dblValue.intValue();
        String strIntVal = intValue.toString();
        String strLastChar = strIntVal.substring(strIntVal.length()-1);
        Integer result = intValue;
        if(strLastChar.equalsIgnoreCase("9")){
            result++;
        } else if(strLastChar.equalsIgnoreCase("1")){
            result--;
        }
        return result;
    }
    
}
