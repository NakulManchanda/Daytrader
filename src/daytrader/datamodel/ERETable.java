/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.utils.DTUtil;
import java.util.Calendar;
import java.util.TreeSet;

/**
 * Used to test if a Single Double pattern meets the 'ERED' requirements. This
 * requires that certain time limits should not have passed between single tip,
 * double tip, low of the day etc. These limits change over the course of the 
 * trading day. This class maintains a time ordered TreeSet of all the various
 * ERE Values that might apply in a day and provides a means to retrieve the value
 * that will apply at any give time point.
 * 
 * NB: Ultimately this class should support serialisation to XML file and have a
 * GUI display that allows modification of these values. At the time of writing 
 * this is not implemented and a default set of values is created in the constructor.
 * @author Roy
 */
public class ERETable {
    
    private TreeSet<ERETableEntry> data;
    
    /**
     * Constructor builds a set of default ERETableEntries for use in development testing.
     * Ultimately should be modified to load values from an XML file.
     */
    public ERETable(){
        this.data = new TreeSet<ERETableEntry>();
        long lngLastStart = 0;
        long lngLastEnd = 1800000;
        long hrInMs = 60 * 60 * 1000;
        //09:30 to 10:00
        ERETableEntry item = new ERETableEntry(lngLastStart, lngLastEnd, DTConstants.MILLSECS_PER_DAY, DTConstants.MILLSECS_PER_DAY);
        this.data.add(item);
        double dblStartMs = (30*60*1000);
        double dblEndMs = (dblStartMs + (5 * 60 * 1000));
        for(int i = 0; i < 4; i++){
            lngLastStart = lngLastEnd;
            lngLastEnd += hrInMs;
            item = new ERETableEntry(lngLastStart, lngLastEnd, dblStartMs, dblEndMs);
            this.data.add(item);
            dblStartMs = dblEndMs;
            dblEndMs += (5 * 60 * 1000);
        }
        //Add the final item to go from 14:00 to 16:00
        item = new ERETableEntry(16200000, 23400000, 3000000, 3000000);
        this.data.add(item);
    }
    
    /**
     * Given a price / time point (AbstractGraphPoint) this method retrieves the
     * ERETableEntry that encapsulates the data to be used in performing an
     * ERE'd test.
     * @param aPoint - A price / time point for which the ERE data is required
     * @return An ERETableEntry encapsulating the data needed to make an ERE'd test.
     */
    public ERETableEntry getApplicableEntry(AbstractGraphPoint aPoint){
        ERETableEntry result = null;
        if(null != aPoint){
            Calendar exchOT = DTUtil.getExchOpeningTimeFromPoint(aPoint);
            long diff = aPoint.getTimestamp() - exchOT.getTimeInMillis();
            for(ERETableEntry currEntry : this.data){
                if(currEntry.appliesAt(diff)){
                    result = currEntry;
                    break;
                }
            }
        }
        return result;
    }
}
