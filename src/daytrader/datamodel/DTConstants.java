/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.backgroundSaveManager.BackgroundSaveManager;
import daytrader.gui.PutupStatusDisplay;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This class provides a central place for the Day Trader application constants 
 * all attributes and methods in this class are static
 * @author Roy
 */
public class DTConstants {
    
    /**
     * This scaling factor is applied to all prices stored in the application to
     * avoid rounding errors. By default it is 1000 so a price of 54.76 would be 
     * recorded as 54760 (54.76 * 1000).
     */
    public static final double SCALE = 1000;
    
    /**
     * Constant representing the minimum acceptable PB value (nominally 3)
     */
    public static final double PBVALUE = 3;
    
    /**
     * The hour when the stock market opens (24 hour clock)
     */
    public static final int EXCH_OPENING_HOUR = 9;
    
    /**
     * The minute when the stock market opens (0 - 59)
     */
    public static final int EXCH_OPENING_MIN = 30;
    
    /**
     * The second when the stock market opens (0 - 59)
     */
    public static final int EXCH_OPENING_SEC = 0;
    
    /**
     * The hour when the stock market closes (24 hour clock)
     */
    public static final int EXCH_CLOSING_HOUR = 16;
    
    /**
     * The minute when the stock market closes (0 - 59)
     */
    public static final int EXCH_CLOSING_MIN = 00;
    
    /**
     * The second when the stock market closes (0 - 59)
     */
    public static final int EXCH_CLOSING_SEC = 00;
    
    /**
     * The time zone in which the stock market is located.
     */
    public static final TimeZone EXCH_TIME_ZONE = TimeZone.getTimeZone("America/New_York");
    
    /**
     * DEPRICATED DO NOT USE - Moved to TWSAccount class to manage on an account
     * by account basis.
     */
    public static final int CONNECTION_PORT = 7497;
    
    /**
     * DEPRICATED DO NOT USE - Moved to TWSAccount class to manage on an account
     * by account basis.
     */
    public static final int CONNECTION_PORT_ACC_2 = 7496;
    
    /**
     * Number of milliseconds in a day
     */
    public static final double MILLSECS_PER_DAY = 24*60*60*1000;
    
    /**
     * Number of milliseconds in an hour
     */
    public static final double MILLSECS_PER_HOUR = 60*60*1000;
    
    private static int conId = 100;
    
    /**
     * A cache of the BSRangeValues to use for the BSRanging function
     */
    public static final BSRangeValues BSRANGES = new BSRangeValues();
    
    /**
     * A general thread pool for use in running concurrent tasks. Sized to the number of
     * processors on the machine +1.
     */
    public static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1);
    
    /**
     * The System.out byte stream. Used to ensure that all threads write to the same output stream.
     */
    public static final PrintStream SYSOUT = System.out;
    
    /**
     * I have decided that I must know the last weeks worth of trading days when the application starts
     * The prime data model will start a thread that retrieves this information and stores it in
     * this public static attribute
     */
    public static TreeSet<Integer> TRADINGDAYSLASTWEEK;
    
    /**
     * Reference to the Putup status display shown on the GUI. Cached here to provide
     * application wide access to this display
     */
    public static PutupStatusDisplay STATUS_DISPLAY;
    
    private static BackgroundSaveManager backupManager;
    
    private static ExecutorService backupService;
    
    /**
     * Each connection requires a unique ID number which identifies it. This 
     * thread safe method increments and retrieves an integer number counter
     * ensuring that each connection is assigned a unique ID number
     * @return The next available ID number 
     */
    public static synchronized int getConId(){
        int result = DTConstants.conId;
        DTConstants.conId++;
        return result;
    }
    
    /**
     * Retrieves the minimum acceptable price difference for a PB Point. As prices
     * are scaled up by a fixed factor the minimum value retrieved is also 
     * scaled by the same amount.
     * @return double being the minimum acceptable PB price difference.
     */
    public static double getScaledPBVALUE(){
        return DTConstants.PBVALUE * DTConstants.SCALE;
    }
    
    private static PrimeDataModel currModel;

    /**
     * @return the currModel
     */
    public static PrimeDataModel getCurrModel() {
        return currModel;
    }

    /**
     * @param aCurrModel the currModel to set
     */
    public static void setCurrModel(PrimeDataModel aCurrModel) {
        if(null == currModel){
            aCurrModel.setLocked(true);
            currModel = aCurrModel;
        }
    }
    
    private static volatile ArrayList<RealTimeRunRecord> runningRecords;
    private static volatile ArrayList<RealTimeRunManager> runningRecords2;

    /**
     * DEPRICATED Replaced by getRunningRecords2()
     * @return the runningRecords
     */
    public static synchronized ArrayList<RealTimeRunRecord> getRunningRecords() {
        return runningRecords;
    }

    /**
     * DEPRICATED Replaced by setRunningRecords2()
     * @param aRunningRecords the runningRecords to set
     */
    public static synchronized void setRunningRecords(ArrayList<RealTimeRunRecord> aRunningRecords) {
        if(null != aRunningRecords){
            runningRecords = aRunningRecords;
        }
    }
    
    /**
     * When a real time run starts each active put up is wrapped in a RealTimeRunManager
     * that adds the data graphs and other functions. System wide access to the running put ups
     * is needed so the list of RealTimeRunManagers is cached in this class and retrieved
     * by this accessor method.
     * @return An ArrayList of RealTimeRunManagers
     */
    public static synchronized ArrayList<RealTimeRunManager> getRunningRecords2() {
        if(null == runningRecords2){
            runningRecords2 = new ArrayList<RealTimeRunManager>();
        }
        return runningRecords2;
    }

    /**
     * When a real time run starts each active put up is wrapped in a RealTimeRunManager
     * that adds the data graphs and other functions. System wide access to the running put ups
     * is needed so the list of RealTimeRunManagers is cached in this class
     * by this accessor method.
     * @param aRunningRecords An ArrayList of the RealTimeRunManagers to cache
     */
    public static synchronized void setRunningRecords2(ArrayList<RealTimeRunManager> aRunningRecords) {
        if(null != aRunningRecords){
            runningRecords2 = aRunningRecords;
        }
    }
    
    /**
     * This method allows an individual RealTimeRunManager to be added to the existing list
     * of managers
     * @param newItem - A REalTimeRunManager to add to the running records list
     */
    public static synchronized void addRunningRecord2(RealTimeRunManager newItem){
        if(null != newItem){
            if(null == runningRecords2){
                runningRecords2 = new ArrayList<RealTimeRunManager>();
            }
            runningRecords2.add(newItem);
        }
    }
    
    /**
     * The application background saves the data it contains every 5 minutes into 
     * XML Files in a backup folder. This is managed by the BackgroundSaveManager class
     * This method starts a thread running an instance of the background save manager.
     */
    public static void startBackupManager(){
        DTConstants.backupService = Executors.newFixedThreadPool(1);
        DTConstants.backupManager = new BackgroundSaveManager();
        DTConstants.backupService.submit(DTConstants.backupManager);
    }
    
    /**
     * The application background saves the data it contains every 5 minutes into 
     * XML Files in a backup folder. This is managed by the BackgroundSaveManager class
     * This method terminates the scheduled backup and shuts the system down.
     */
    public static void stopBackupManager(){
        if(null != DTConstants.backupManager){
            DTConstants.backupManager.shutdown();
            DTConstants.backupManager = null;
            if(null != DTConstants.backupService){
                DTConstants.backupService.shutdownNow();
                DTConstants.backupService = null;
            }
        }
    }
    
    /**
     * Accessor method to retrieve the File object representing the current 
     * backup folder in use by the BackgroundSaveManager class.
     * @return A File object representing the abstract path to the current
     * backup folder.
     */
    public static File getRecoveryFolder(){
        File result = null;
        if(null != DTConstants.backupManager){
            DTConstants.backupManager.getRecoveryFolder();
        }
        return result;
    }
}
