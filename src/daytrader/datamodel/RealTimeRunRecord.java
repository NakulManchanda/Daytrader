/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * DEPRICATED DO NOT USE IN PRODUCTION CODE!
 * This class stores all details pertaining to a live data run
 * @author Roy
 */
public class RealTimeRunRecord {
    
    private RealTimeDataLoader loader;
    private ExecutorService executor;
    private ExecutorCompletionService<RealTimeDataLoader> executionService;
    private Future<RealTimeDataLoader> future;
    
    public RealTimeRunRecord(RealTimeDataLoader newLoader, ExecutorService newExec, ExecutorCompletionService<RealTimeDataLoader> newService){
        if(null != newLoader && null != newExec && null != newService){
            this.loader = newLoader;
            this.executor = newExec;
            this.executionService = newService;
            this.future = this.executionService.submit(this.loader);
        }
    }

    public RealTimeDataLoader getLoader() {
        return loader;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public ExecutorCompletionService<RealTimeDataLoader> getExecutionService() {
        return executionService;
    }

    public Future<RealTimeDataLoader> getFuture() {
        return future;
    }
}
