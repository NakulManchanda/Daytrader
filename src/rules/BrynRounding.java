/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.interfaces.IRoundFunction;

/**
 * Performs the first step of Bryns rounding operations
 * @author Roy
 */
public class BrynRounding implements IRoundFunction<Number>{

    @Override
    public Number performRounding(Number value) {
        Double dblStartVal = value.doubleValue();
        String strRep = dblStartVal.toString();
        char lastChar = '0';
        if(strRep.contains(".")){
            lastChar = strRep.charAt(strRep.length()-1);
        }
        String strLastChar = "" + lastChar;
        Integer intAfterDecimal = Integer.parseInt(strLastChar);
        Integer intValue = dblStartVal.intValue();
        if(intAfterDecimal > 5){
            intValue++;
        }
        Integer result = intValue;
        return result;
    }
    
}
