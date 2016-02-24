/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This enumeration provides the valid Bar Size Settings for Historical Data Requests
 * See page 388 of stock broker's API documentation. The toString() method is
 * overridden to provide the strings used by the API.
 * @author Roy
 */
public enum BarSizeSettingEnum {

    /**
     * One Second resolution for data bars
     */
    SEC1,
    /**
     * Five second resolution for data bars
     */
    SEC5,
    /**
     * Fifteen second resolution for data bars
     */
    SEC15,
    /**
     * Thirty second resolution for data bars
     */
    SEC30,
    /**
     * One minute resolution for data bars
     */
    MIN1,
    /**
     * Two minute resolution for data bars
     */
    MIN2,
    /**
     * Three minute resolution for data bars
     */
    MIN3,
    /**
     * Five minute resolution for data bars
     */
    MIN5,
    /**
     * Fifteen minute resolution for data bars
     */
    MIN15,
    /**
     * Thirty minute resolution for data bars
     */
    MIN30,
    /**
     * One hour resolution for data bars
     */
    HR1,
    /**
     * One day resolution for data bars
     */
    DAY1;

    @Override
    public String toString() {
        String result = "";
        switch (this) {
            case SEC1:
                result = "1 secs";
                break;
            case SEC5:
                result = "5 secs";
                break;
            case SEC15:
                result = "15 secs";
                break;
            case SEC30:
                result = "30 secs";
                break;
            case MIN1:
                result = "1 min";
                break;
            case MIN2:
                result = "2 mins";
                break;
            case MIN3:
                result = "3 mins";
                break;
            case MIN5:
                result = "5 mins";
                break;
            case MIN15:
                result = "15 mins";
                break;
            case MIN30:
                result = "30 mins";
                break;
            case HR1:
                result = "1 hour";
                break;
            case DAY1:
                result = "1 day";
                break;
            default:
                result = "";
        }
        return result;
    }
}
