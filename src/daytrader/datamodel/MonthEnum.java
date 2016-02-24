/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.util.Calendar;

/**
 * An enumeration for the month of the year (helps with display)
 *
 * @author Roy
 */
public enum MonthEnum {

    JANUARY,
    FEBRUARY,
    MARCH,
    APRIL,
    MAY,
    JUNE,
    JULY,
    AUGUST,
    SEPTEMBER,
    OCTOBER,
    NOVEMBER,
    DECEMBER;

    @Override
    public String toString() {
        String result = "UNKNOWN";
        switch (this) {
            case JANUARY:
                result = "JAN";
                break;
            case FEBRUARY:
                result = "FEB";
                break;
            case MARCH:
                result = "MAR";
                break;
            case APRIL:
                result = "APR";
                break;
            case MAY:
                result = "MAY";
                break;
            case JUNE:
                result = "JUN";
                break;
            case JULY:
                result = "JUL";
                break;
            case AUGUST:
                result = "AUG";
                break;
            case SEPTEMBER:
                result = "SEP";
                break;
            case OCTOBER:
                result = "OCT";
                break;
            case NOVEMBER:
                result = "NOV";
                break;
            case DECEMBER:
                result = "DEC";
                break;
        }
        return result;
    }

    public int toCalEnum() {
        int result = -1;
        switch (this) {
            case JANUARY:
                result = Calendar.JANUARY;
                break;
            case FEBRUARY:
                result = Calendar.FEBRUARY;
                break;
            case MARCH:
                result = Calendar.MARCH;
                break;
            case APRIL:
                result = Calendar.APRIL;
                break;
            case MAY:
                result = Calendar.MAY;
                break;
            case JUNE:
                result = Calendar.JUNE;
                break;
            case JULY:
                result = Calendar.JULY;
                break;
            case AUGUST:
                result = Calendar.AUGUST;
                break;
            case SEPTEMBER:
                result = Calendar.SEPTEMBER;
                break;
            case OCTOBER:
                result = Calendar.OCTOBER;
                break;
            case NOVEMBER:
                result = Calendar.NOVEMBER;
                break;
            case DECEMBER:
                result = Calendar.DECEMBER;
                break;
        }
        return result;
    }

    public static MonthEnum fromCalEnum(Integer calValue) {
        MonthEnum result = MonthEnum.JANUARY;
        switch (calValue) {
            case Calendar.JANUARY:
                result = MonthEnum.JANUARY;
                break;
            case Calendar.FEBRUARY:
                result = MonthEnum.FEBRUARY;
                break;
            case Calendar.MARCH:
                result = MonthEnum.MARCH;
                break;
            case Calendar.APRIL:
                result = MonthEnum.APRIL;
                break;
            case Calendar.MAY:
                result = MonthEnum.MAY;
                break;
            case Calendar.JUNE:
                result = MonthEnum.JUNE;
                break;
            case Calendar.JULY:
                result = MonthEnum.JULY;
                break;
            case Calendar.AUGUST:
                result = MonthEnum.AUGUST;
                break;
            case Calendar.SEPTEMBER:
                result = MonthEnum.SEPTEMBER;
                break;
            case Calendar.OCTOBER:
                result = MonthEnum.OCTOBER;
                break;
            case Calendar.NOVEMBER:
                result = MonthEnum.NOVEMBER;
                break;
            case Calendar.DECEMBER:
                result = MonthEnum.DECEMBER;
                break;
        }
        return result;
    }
}
