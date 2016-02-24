/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.utils;

/**
 * Timer class used for debugging and measuring performance do not use in final
 * production code.
 * @author Roy
 */
public class Timer {
    
    private static long baseTime = 0;
    
    /**
     * Sets the current system time as the zero point from which
     * elapsed time is measured
     */
    public static void setBaseTime(){
        Timer.baseTime = System.currentTimeMillis();
    }
    
    /**
     * Retrieves the number of milliseconds that have elapsed since the last 
     * call to the setBaseTime() method.
     * @return The elapsed time in milliseconds since the last call to setBaseTime()
     */
    public static long getElapsedTime(){
        long result = System.currentTimeMillis() - Timer.baseTime;
        return result;
    }
    
    /**
     * Prings both the elapsed time and the provided message to the standard output
     * stream.
     * @param strMsg - Message to print with the elapsed time.
     */
    public static void printMsg(String strMsg){
        String result = "Elapsed Time: " + Timer.getElapsedTime() + " " + strMsg;
        System.out.println(result);
    }
    
}
