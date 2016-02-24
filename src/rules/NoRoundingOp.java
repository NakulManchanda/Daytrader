/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.interfaces.IRoundFunction;

/**
 * A Rounding function that leaves the number unchanged
 * @author Roy
 */
public class NoRoundingOp implements IRoundFunction<Number>{

    @Override
    public Number performRounding(Number value) {
        return value;
    }
    
}
