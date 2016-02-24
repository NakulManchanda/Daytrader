/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rules;

import daytrader.datamodel.DTConstants;
import daytrader.interfaces.IRoundFunction;

/**
 * Given a number (scaled by the scaling factor) this rounds to the next whole
 * cent
 *
 * @author Roy
 */
public class BreachIRounding implements IRoundFunction<Number> {

    @Override
    public Number performRounding(Number value) {
        int result = 0;
        int intValue = value.intValue();
        if ((value.doubleValue() - intValue) != 0) {
            double dblStartVal = value.doubleValue();
            double dblDeScaled = dblStartVal / DTConstants.SCALE;
            //Convert to cents from dollars
            Double cents = dblDeScaled *= 100;
            Integer intCents = cents.intValue();
            Double rem = cents - intCents;
            if (rem != 0d) {
                intCents++;
            }
            //Convert back to scaled value
            Double dblResult = (intCents / 100d) * DTConstants.SCALE;
            result = dblResult.intValue();
        } else {
            result = value.intValue();
        }
        return result;
    }
}
