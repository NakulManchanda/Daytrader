/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

/**
 * This enumeration identifies the stage that the Put up is at in loading Y-Lines.
 * Calculating the initial Y-Lines is a lengthy and potentialy expensive process.
 * It has been broken into multiple steps and this enumeration provides a flag
 * used by a putup to track progress.
 * 
 * @author Roy
 */
public enum YLineLoadStatus {
    
    /**
     * This putup has not yet started loading Y-Line data
     */
    YLINESNOTLOADED,
    /**
     * This putup is loading 1 Day resolution data bars this is used to determine
     * what data needs to be loaded with a higher resolution
     */
    YLINESLOADING1DAYBARS,
    /**
     * This putup is loading 1 hour resolution data bars this is used to determine
     * what data needs to be loaded with a higher resolution
     */
    YLINELOADING1HOURBARS,
    /**
     * This putup is loading 15 min resolution data bars this is used to determine
     * what data needs to be loaded with a higher resolution
     */
    YLINELOADING15MINBARS,
    /**
     * This putup is loading 1 sec resolution data bars this will be the data needed
     * to perform Y-Line generation
     */
    YLINELOADING1SECBARS,
    /**
     * All Y-Line data has been loaded down to a one second resolution. The provisional
     * or initial Y-Lines if your prefer are being calculated
     */
    YLINECALCULATING,
    /**
     * All Y-Line data is loaded and the provisional / initial Y-Lines have been
     * calculated and are available for use.
     */
    YLINESLOADED;

    @Override
    public String toString() {
        String result = "UNKNOWN";
        switch(this){
            case YLINESNOTLOADED:
                result = "Y Lines Not Loaded";
                break;
            case YLINESLOADING1DAYBARS:
                result = "Loading 1 Day Bars";
                break;
            case YLINELOADING1HOURBARS:
                result = "Loading 1 Hour Bars";
                break;
            case YLINELOADING15MINBARS:
                result = "Loading 15 Min Bars";
                break;
            case YLINELOADING1SECBARS:
                result = "Loading 1 Sec Bars";
                break;
            case YLINECALCULATING:
                result = "Calculating Y Lines";
                break;
            case YLINESLOADED:
                result = "Y Lines Loaded";
                break;
        }
        return result;
    }
    
    
    
}
