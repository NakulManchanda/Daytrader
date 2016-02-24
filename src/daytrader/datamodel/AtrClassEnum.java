/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 *
 * @author Roy
 */
public enum AtrClassEnum {

    /**
     * A Putup class used to apply different formula and limits throughout the application
     */
    UU,
    /**
     * A Putup class used to apply different formula and limits throughout the application
     */
    U,
    /**
     * A Putup class used to apply different formula and limits throughout the application
     */
    PA,
    /**
     * A Putup class used to apply different formula and limits throughout the application
     */
    PAP,
    /**
     * A Putup class used to apply different formula and limits throughout the application
     */
    PP,
    /**
     * A Putup class used to apply different formula and limits throughout the application
     */
    PH,
    /**
     * A Putup class used to apply different formula and limits throughout the application
     */
    MH;

    @Override
    public String toString() {
        String result = "UNKNOWN";
        switch (this) {
            case UU:
                result = "UU";
                break;
            case U:
                result = "U";
                break;
            case PA:
                result = "PA";
                break;
            case PAP:
                result = "PAP";
                break;
            case PP:
                result = "PP";
                break;
            case PH:
                result = "PH";
                break;
            case MH:
                result = "MH";
                break;
        }
        return result;
    }

    /**
     * Each class can be represented by an integer number this method associates numbers and classes
     * @return The integer number representing this class
     */
    public int classNumber() {
        int result = 0;
        switch (this) {
            case UU:
                result = 0;
                break;
            case U:
                result = 1;
                break;
            case PA:
                result = 2;
                break;
            case PAP:
                result = 3;
                break;
            case PP:
                result = 4;
                break;
            case PH:
                result = 5;
                break;
            case MH:
                result = 6;
                break;
            default:
                result = 0;
        }
        return result;
    }

    /**
     * Parses the provided string and identifies the class represented by the string
     * @param text - The string to parse
     * @return An AtrClassEnum if one can be identified from the string, NULL otherwise
     */
    public static AtrClassEnum getAtrClassFromString(String text) {
        AtrClassEnum result = null;
        if (null != text && 0 < text.length()) {
            AtrClassEnum[] values = AtrClassEnum.values();
            for (int i = 0; i < values.length; i++) {
                AtrClassEnum item = values[i];
                if (item.toString().equals(text)) {
                    result = item;
                    break;
                }
            }
        }
        return result;
    }
}
