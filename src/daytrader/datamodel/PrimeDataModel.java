/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import static daytrader.datamodel.CallbackType.HISTORICDATAERROR;
import static daytrader.datamodel.CallbackType.HISTORICDATATRADINGDAYS;
import daytrader.historicRequestSystem.HRSCallableWrapper;
import daytrader.historicRequestSystem.HistoricRequestProcessingSystem;
import daytrader.interfaces.ICallback;
import daytrader.interfaces.XMLPersistable;
import daytradertasks.LoadHistoricDataPointBatchResult;
import daytradertasks.LoadTradingDaysTask;
import java.awt.EventQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This is the primary model class for the day trader application it stores a
 * list of all putups (active and inactive) and allows you to retrieve active
 * ones
 *
 * @author Roy
 */
public class PrimeDataModel implements XMLPersistable<PrimeDataModel>, Iterable<Putup> {

    private final boolean DEBUG = false;
    private ArrayList<Putup> allPutupsList;
    private boolean locked = false;
    /**
     * String containing the file name of the XML File that defines the Put ups 
     * currently in use
     */
    public static final String PUTUP_FILE_NAME = "putups.xml";

    /**
     * Default constructor - loads putup data from its XML file and makes the 
     * historic data request to the stock brokers API that produced details
     * of the days on which the New York stock markets have been open for trading
     */
    public PrimeDataModel() {
        this.allPutupsList = new ArrayList<Putup>();
        if (DEBUG) {
            this.allPutupsList.add(new Putup("BAX", MarketEnum.NYSE, PutupTypeEnum.LONGS, 50000, AtrClassEnum.UU));
        }

        //Check to see if putups have been saved and if so load them
        File putupFile = new File(PUTUP_FILE_NAME);
        if (putupFile.exists() && putupFile.isFile() && putupFile.canRead()) {
            loadPutups(putupFile);
        }
        //Need to check this runs on the event queue only
        if(EventQueue.isDispatchThread()){
            loadTradingDays();
        }
    }

    /**
     * Accessor retrieves an ArrayList containing all the putups (active or otherwise)
     * currently in the system.
     * @return An ArrayList of Putup objects.
     */
    public ArrayList<Putup> getAllPutupsList() {
        ArrayList<Putup> result = new ArrayList<Putup>(this.allPutupsList);
        return result;
    }

    /**
     * Accessor retrieves an ArrayList containing all the active putups entered
     * into the system. 
     * @return An ArrayList of Putup objects.
     */
    public ArrayList<Putup> getActivePutups() {
        ArrayList<Putup> result = new ArrayList<Putup>();
        if (null != this.allPutupsList && 0 < this.allPutupsList.size()) {
            for (Putup currPutup : this.allPutupsList) {
                if (currPutup.isActive()) {
                    result.add(currPutup);
                }
            }
        }
        return result;
    }

    /**
     * This method adds a new putup to the list of putups in the system
     * @param newItem - The Putup object to add to the list.
     */
    public void addPutup(Putup newItem) {
        if (!this.locked) {
            this.allPutupsList.add(newItem);
        }
    }

    /**
     * This method removes a putup from the list of putups in the system
     * @param oldPutup - The Putup object to remove from the list.
     */
    public void removePutup(Putup oldPutup) {
        if (null != oldPutup) {
            if (!this.isLocked()) {
                this.allPutupsList.remove(oldPutup);
            }
        }
    }
    
    /**
     * Completely clears the list of putups
     */
    public void clearAllPutups(){
        if(!this.locked){
            this.allPutupsList.clear();
        }
    }

    /**
     * This method persists the putups list to an XML File defined by the
     * PUTUP_FILE_NAME class constant. Any existing list will be backed up to
     * "OLD_" + PUTUP_FILE_NAME rather than being overwritten
     * @return boolean true if a successful save of the putup list was made,
     * False otherwise.
     */
    public boolean savePutups() {
        boolean result = false;
        if (0 < this.allPutupsList.size()) {
            File putupFile = new File(PUTUP_FILE_NAME);
            if (putupFile.exists()) {
                File oldFile = new File("OLD_" + PUTUP_FILE_NAME);
                putupFile.renameTo(oldFile);
            }
            FileOutputStream stream = null;
            try {
                putupFile.createNewFile();
                stream = new FileOutputStream(putupFile);
                XMLStreamWriter out;
                out = XMLOutputFactory.newInstance().createXMLStreamWriter(stream);
                result = this.writeAsXMLToStream(out);
            } catch (IOException ex) {
                Logger.getLogger(PrimeDataModel.class.getName()).log(Level.SEVERE, null, ex);
            } catch (XMLStreamException ex) {
                Logger.getLogger(PrimeDataModel.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (null != stream) {
                    try {
                        stream.close();
                    } catch (IOException ex) {
                        Logger.getLogger(PrimeDataModel.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, PrimeDataModel dest) {
        boolean result = false;
        while (reader.hasNext()) {
            try {
                XMLEvent event = reader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    String strName = startElement.getName().getLocalPart();
                    if (strName.equalsIgnoreCase("Putup")) {
                        Putup newPutup = new Putup();
                        if (newPutup.loadFromXMLStream(reader, newPutup)) {
                            if (null != dest) {
                                dest.addPutup(newPutup);
                            } else {
                                this.addPutup(newPutup);
                            }
                        }
                    }
                }
            } catch (XMLStreamException ex) {
                Logger.getLogger(PrimeDataModel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (null != dest) {
            if (0 < dest.getAllPutupsList().size()) {
                result = true;
            }
        } else {
            if (0 < this.allPutupsList.size()) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        if (null != writer && null != this.allPutupsList) {
            if (0 < this.allPutupsList.size()) {
                try {
                    writer.writeStartDocument();
                    writer.writeStartElement("PutupList");
                    for (Putup currPutup : this.allPutupsList) {
                        currPutup.writeAsXMLToStream(writer);
                    }
                    writer.writeEndElement();
                    writer.writeEndDocument();
                    result = true;
                } catch (XMLStreamException ex) {
                    Logger.getLogger(PrimeDataModel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    private void loadPutups(File putupFile) {
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(putupFile);
            XMLEventReader in = XMLInputFactory.newInstance().createXMLEventReader(stream);
            this.loadFromXMLStream(in, this);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(PrimeDataModel.class.getName()).log(Level.SEVERE, null, ex);
        } catch (XMLStreamException ex) {
            Logger.getLogger(PrimeDataModel.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (null != stream) {
                    stream.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(PrimeDataModel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * @return the locked
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * @param locked the locked to set
     */
    public void setLocked(boolean locked) {
        if (!this.locked) {
            this.locked = locked;
        }
    }

    @Override
    public Iterator<Putup> iterator() {
        return this.allPutupsList.iterator();
    }

    //This method fires off a thread that loads the last week of trading days
    private void loadTradingDays() {
        LoadTradingDays task = new LoadTradingDays();
        Thread newThread = new Thread(task);
        newThread.start();
    }
    
    private class LoadTradingDays implements Runnable, ICallback {

        @Override
        public void run() {
            HistoricRequestProcessingSystem HRSys = HistoricRequestProcessingSystem.getInstance();
            //I need a putup so used Microsoft on NYSE
            Putup tempPutup = new Putup("IBM", MarketEnum.NYSE, PutupTypeEnum.LONGS, 0, AtrClassEnum.UU);
            
            //FOR TESTING PURPOSES ONLY WE MAY NEED TO FUDGE THE CURRENT DAY - COMMENT OUT FOR LIVE RUNS - START
//            Calendar dateTime = Calendar.getInstance(DTConstants.EXCH_TIME_ZONE);
//            dateTime.set(Calendar.YEAR, 2013);
//            dateTime.set(Calendar.MONTH, Calendar.JULY);
//            dateTime.set(Calendar.DAY_OF_MONTH, 24);
//            dateTime.set(Calendar.HOUR_OF_DAY, 9);
//            dateTime.set(Calendar.MINUTE, 30);
//            dateTime.set(Calendar.SECOND, 0);
//            dateTime.set(Calendar.MILLISECOND, 0);
            //FOR TESTING PURPOSES ONLY WE MAY NEED TO FUDGE THE CURRENT DAY - COMMENT OUT FOR LIVE RUNS - END
            
            //FOR LIVE RUNS USE THIS CODE INSTEAD OF TESTING CODE - START
            Calendar dateTime = Calendar.getInstance();
            //FOR LIVE RUNS USE THIS CODE INSTEAD OF TESTING CODE - END
            
            LoadTradingDaysTask theTask = new LoadTradingDaysTask(tempPutup, dateTime, this);
            HRSCallableWrapper wrapper = new HRSCallableWrapper(theTask);
            HRSys.submitRequest(wrapper);
        }
        
        @Override
        public void callback(CallbackType type, Object data) {
            switch (type) {
                case HISTORICDATATRADINGDAYS:
                    if(data instanceof LoadHistoricDataPointBatchResult){
                        LoadHistoricDataPointBatchResult result = (LoadHistoricDataPointBatchResult)data;
                        //Each data point represents one trading day in the last week
                        TreeSet<Integer> newTradingDays = new TreeSet<Integer>();
                        for(AbstractGraphPoint currPoint : result.loadedPoints){
                            int dateAsNumber = currPoint.getDateAsNumber();
                            newTradingDays.add(dateAsNumber);
                        }
                        //Now store this as the trading days
                        DTConstants.TRADINGDAYSLASTWEEK = newTradingDays;
                    }
                    break;
                case HISTORICDATAERROR:
                    break;
            }
        }
    }
}
