/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.util.Calendar;

/**
 * Enumeration representing the duration settings that can be used on an
 * historic data request to the stock brokers API. See page 388 of the 
 * stock brokers API documentation
 * @author Roy
 */
public enum DTDurationEnum {

    S60,
    S300,
    S900,
    S1800,
    S3600,
    S7200,
    S14400,
    D1,
    D2,
    W1,
    M1,
    M3,
    M6,
    Y1;

    @Override
    public String toString() {
        String result = "";
        switch (this) {
            case S60:
                result = "60 S";
                break;
            case S300:
                result = "300 S";
                break;
            case S900:
                result = "900 S";
                break;
            case S1800:
                result = "1800 S";
                break;
            case S3600:
                result = "3600 S";
                break;
            case S7200:
                result = "7200 S";
                break;
            case S14400:
                result = "14400 S";
                break;
            case D1:
                result = "1 D";
                break;
            case D2:
                result = "2 D";
                break;
            case W1:
                result = "1 W";
                break;
            case M1:
                result = "1 M";
                break;
            case M3:
                result = "3 M";
                break;
            case M6:
                result = "6 M";
                break;
            case Y1:
                result = "1 Y";
                break;
            default:
                result = "";
        }
        return result;
    }
    
    public static DTDurationEnum getDurationToCover(Calendar startCal, Calendar endCal){
        DTDurationEnum result = DTDurationEnum.M3;
        Long lngdiff = startCal.getTimeInMillis() - endCal.getTimeInMillis();
        Double diff = lngdiff.doubleValue();
        diff /= 1000;
        diff /= 60;
        diff /= 60;
        if(diff < (24)){
            result = DTDurationEnum.D1;
        } else if(diff < (24 * 2)){
            result = DTDurationEnum.D2;
        } else if(diff < ((24 * 7))){
            result = DTDurationEnum.W1;
        }
        return result;
    }
}
