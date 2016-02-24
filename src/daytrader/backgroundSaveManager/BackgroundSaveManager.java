/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.backgroundSaveManager;

import daytrader.datamodel.DTConstants;
import daytrader.datamodel.RealTimeRunManager;
import daytrader.utils.DTUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This callable class will manage the background saving of data so that it can
 * be recovered during the day It will save any running put ups data every 5 min
 * by default
 *
 * @author Roy
 */
public class BackgroundSaveManager implements Callable<Void> {

    private long backupEveryXms;
    private ReentrantLock lock;
    private long nextBackup;
    private File recoveryFolder;
    private ExecutorService threadPool;
    private boolean isShutdown;

    /**
     * Default Constructor - Initialises this class to perform a background save
     * every 5 minutes of all downloaded data.
     */
    public BackgroundSaveManager() {
        this.backupEveryXms = (5 * 60 * 1000);                                      //By default backup every 5 mins
        this.lock = new ReentrantLock();
        this.threadPool = Executors.newCachedThreadPool();
        this.isShutdown = false;
    }

    @Override
    public Void call() throws Exception {
        this.makeRecoveryDir();
        //Set initial backup time
        this.nextBackup = System.currentTimeMillis() + backupEveryXms;
        while (!this.isShutdown) {
            try {
                if (System.currentTimeMillis() >= this.nextBackup) {
                    //Do Backup operation
                    this.runBackup();
                    //Advance to next backup time
                    lock.lock();
                    try {
                        this.nextBackup = System.currentTimeMillis() + backupEveryXms;
                    } finally {
                        lock.unlock();
                    }
                } else {
                    //Sleep until at least the expected backup time
                    long sleepTime = this.nextBackup - System.currentTimeMillis();
                    if (0 < sleepTime) {
                        Thread.sleep(sleepTime);
                    }
                }
            } catch (InterruptedException ex) {
                this.shutdown();
            } catch (Exception ex) {
                //Fail silently for any other exception and advance to next backup time
                if (this.nextBackup < System.currentTimeMillis()) {
                    //Advance to next backup time
                    lock.lock();
                    try {
                        this.nextBackup = System.currentTimeMillis() + backupEveryXms;
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Accessor to retrieve the time between backups in milliseconds 
     * @return the backupEveryXms
     */
    public long getBackupEveryXms() {
        long result = 0;
        lock.lock();
        try {
            result = this.backupEveryXms;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the time between backups in milliseconds
     * @param backupEveryXms the backupEveryXms to set
     */
    public void setBackupEveryXms(long backupEveryXms) {
        if (0 < backupEveryXms) {
            lock.lock();
            try {
                this.backupEveryXms = backupEveryXms;
                long timeToNext = this.nextBackup - System.currentTimeMillis();
                if (timeToNext > this.backupEveryXms) {
                    this.nextBackup = System.currentTimeMillis();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Performs a controlled shutdown of the backup system. A backup is performed for
     * all running putups and then the thread pool is shutdown and the 5 min backup
     * loop is terminated.
     */
    public void shutdown() {
        ArrayList<RealTimeRunManager> runningPutups = DTConstants.getRunningRecords2();
        int count = 0;
        CompletionService serv = new ExecutorCompletionService(this.threadPool);
        if (null != runningPutups && 0 < runningPutups.size()) {
            for (RealTimeRunManager manager : runningPutups) {
                BackupRealTimeRunManager task = new BackupRealTimeRunManager(manager, recoveryFolder);
                serv.submit(task);
                count++;
            }
            for (int i = 0; i < count; i++) {
                try {
                    //Result is void only interested to know the task is completed
                    serv.take();
                } catch (InterruptedException ex) {
                    Logger.getLogger(BackgroundSaveManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            //All tasks are completed isShutdown thread pool and interrupt myself
            this.threadPool.shutdownNow();
            this.isShutdown = true;
        }
    }

    /**
     * This method uses a callable task to backup all data for all running putups
     */
    private void runBackup() {
        ArrayList<RealTimeRunManager> runningPutups = DTConstants.getRunningRecords2();
        if (null != runningPutups && 0 < runningPutups.size()) {
            for (RealTimeRunManager manager : runningPutups) {
                BackupRealTimeRunManager task = new BackupRealTimeRunManager(manager, recoveryFolder);
                this.threadPool.submit(task);
            }
        }
    }

    /**
     * This function creates a directory to store recovery data. The created directory will be 
     * named "Recovery_YYYYMMDD". So for example the data for 26th August 2013 will be saved
     * into a recovery folder named "Recovery_20130826.
     */
    private void makeRecoveryDir() {
        //Create folder for recovery files
        Calendar exchOpeningTime = DTUtil.getExchOpeningTime();
        int intDate = DTUtil.convertCalendarToIntDate(exchOpeningTime);
        String folderName = "Recovery_" + intDate;
        this.recoveryFolder = new File(folderName);
        if (!this.recoveryFolder.exists()) {
            this.recoveryFolder.mkdir();
        }
    }

    /**
     * Accessor to retrieve the File object that represents the recovery folder.
     * @return the recoveryFolder File object
     */
    public File getRecoveryFolder() {
        return recoveryFolder;
    }
}
