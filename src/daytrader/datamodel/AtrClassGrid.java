/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.XMLPersistable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This Class represents a grid of AtrClassValues. Each put up class as defined by the AtrClassEnum
 * has a set of values associated with it that are used in formula. The exact value to use depends on 
 * the time since trading started and the AtrClass of the put up. This grid serves to cross index time
 * and AtrClass to identify a unique AtrClassValue that should be used at the current time for a given 
 * class of put up
 * @author Roy
 */
public class AtrClassGrid implements XMLPersistable<AtrClassGrid>, Iterable<AtrClassValue> {

    private final boolean DEBUG = false;
    private boolean lockedIn = false;
    /**
     * Filename used to persist the atr values parameters.
     */
    public static final String ATRPARAM_FILENAME = "atrParam.xml";
    /**
     * Filename used to persist the atr values parameters. (Backup of previous version)
     */
    public static final String ATRPARAM_FILENAME_OLD = "atrParamOld.xml";
    private ArrayList<TreeSet<AtrClassValue>> grid;

    /**
     * Default Constructor will load either test data (if DEBUG is on) or the last used 
     * set of data from the XML files.
     */
    public AtrClassGrid() {
        this.clear();
        if (DEBUG) {
            loadTestData();
        } else {
            loadLastUsed();
        }
    }
    
    private void clear(){
        this.grid = new ArrayList<TreeSet<AtrClassValue>>(AtrClassEnum.values().length);
        for (AtrClassEnum currClass : AtrClassEnum.values()) {
            TreeSet<AtrClassValue> dataSet = new TreeSet<AtrClassValue>();
            this.grid.add(currClass.classNumber(), dataSet);
        }
    }
    
    /**
     * This method ensures that the defined grid is valid and ensures their are no gaps 
     * in terms of time in the table.
     * @return boolean True if the table is complete and correct, False otherwise.
     */
    public boolean isUsable(){
        boolean result = false;
        if(null != this.grid){
            if(0 < this.grid.size()){
                //Check that each list entry starts at the time the previous ends.
                //The first item should ALWAYS start 0 ms into the day
                boolean blnValid = true;
                for(TreeSet<AtrClassValue> currSet : this.grid){
                    long lastTime = 0;
                    Iterator<AtrClassValue> iterator = currSet.iterator();
                    while(iterator.hasNext()){
                        AtrClassValue next = iterator.next();
                        if(lastTime != next.getStartTime()){
                            blnValid = false;
                            break;
                        } else {
                            lastTime = next.getEndTime();
                        }
                    }
                    if(!blnValid){
                        break;
                    }
                }
                result = blnValid;
            }
        }
        return result;
    }

    /**
     * Accessor that adds a new AtrClassValue item into the grid.
     * @param item - The AtrClassValue to add to the grid
     * @return boolean True if the value was successfully stored, False otherwise.
     */
    public boolean addItem(AtrClassValue item) {
        boolean result = false;
        if (null != item) {
            TreeSet<AtrClassValue> list = this.grid.get(item.getAtrclass().classNumber());
            result = list.add(item);
        }
        return result;
    }

    /**
     * This accessor retrieves the AtrClassValue that applies to the given AtrClassEnum at
     * the given time since the stock market opened.
     * @param aClass - The AtrClass of the put up that needs its AtrValue
     * @param Xms - Time in milliseconds since the start of the trading day.
     * @return The AtrClassValue to be used by formula based on the specified put up class and time
     */
    public AtrClassValue getApplicableAtrClassValue(AtrClassEnum aClass, long Xms) {
        AtrClassValue result = null;
        if (null != aClass && Xms >= 0) {
            TreeSet<AtrClassValue> list = this.grid.get(aClass.classNumber());
            for (AtrClassValue aValue : list) {
                if (Xms >= aValue.getStartTime() && Xms <= aValue.getEndTime()) {
                    result = aValue;
                    break;
                }
            }
            if(null == result){
                result = list.last();
            }
        }
        return result;
    }

    /**
     * Accessor to retrieve the width of the grid
     * @return integer being the width of the grid for display purposes (number of elements
     * in the grid NOT pixels!).
     */
    public int getWidth() {
        int result = this.grid.size();
        return result;
    }

    /**
     * Accessor to retrieve the height of the grid 
     * @return integer being the height of the grid for display purposes (number of elements
     * in the grid NOT pixels!).
     */
    public int getHeight() {
        int result = 0;
        for (TreeSet<AtrClassValue> currList : this.grid) {
            if (result < currList.size()) {
                result = currList.size();
            }
        }
        return result;
    }

    /**
     * Retrieves the AtrClassValue at the specified x, y co-ordinates in the grid
     * @param x - integer being the number of elements 'across' the grid (zero based)
     * @param y - integer being the number of elements 'down' the grid (zero based)
     * @return The AtrClassValue at the given x, y co-ordinates or NULL if none exists.
     */
    public AtrClassValue getItemAt(int x, int y) {
        AtrClassValue result = null;
        if (y < this.getWidth()) {
            TreeSet<AtrClassValue> list = this.grid.get(y);
            if (x < list.size()) {
                if (0 == x) {
                    result = list.first();
                } else {
                    int count = 0;
                    Iterator<AtrClassValue> listIterator = list.iterator();
                    while (listIterator.hasNext()) {
                        if (x == count) {
                            result = listIterator.next();
                            break;
                        } else {
                            listIterator.next();
                            count++;
                        }
                    }
                }
            }
        }
        return result;
    }

    private ArrayList<AtrClassValue> getDataAsArrayList() {
        ArrayList<AtrClassValue> result = new ArrayList<AtrClassValue>();
        for (TreeSet<AtrClassValue> currList : this.grid) {
            for (AtrClassValue currVal : currList) {
                result.add(currVal);
            }
        }
        return result;
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, AtrClassGrid dest) {
        boolean result = false;
        File dataFile = new File(ATRPARAM_FILENAME);
        if (dataFile.exists()) {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(dataFile);
                XMLEventReader in = XMLInputFactory.newInstance().createXMLEventReader(stream);
                while (in.hasNext()) {
                    XMLEvent nextEvent = in.nextEvent();
                    if (nextEvent.isStartElement()) {
                        StartElement xmlStartElement = nextEvent.asStartElement();
                        String name = xmlStartElement.getName().toString();
                        if (name.equals("ATRParams")) {
                            Attribute noOfEntries = xmlStartElement.getAttributeByName(new QName("No"));
                            String value = noOfEntries.getValue();
                            for (int i = 0; i < Integer.parseInt(value); i++) {
                                AtrClassValue item = retrieveAtrClassValue(in);
                                if (null != dest) {
                                    dest.addItem(item);
                                } else {
                                    this.addItem(item);
                                }
                            }
                        }
                    }
                }
                result = true;
            } catch (FileNotFoundException ex) {
                Logger.getLogger(AtrClassGrid.class.getName()).log(Level.SEVERE, null, ex);
            } catch (XMLStreamException ex) {
                Logger.getLogger(AtrClassGrid.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (null != stream) {
                    try {
                        stream.close();
                    } catch (IOException ex) {
                        Logger.getLogger(AtrClassGrid.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        File dataFile = new File(ATRPARAM_FILENAME);
        if (dataFile.exists()) {
            dataFile.renameTo(new File(ATRPARAM_FILENAME_OLD));
            dataFile.delete();
        }
        try {
            dataFile.createNewFile();
            FileOutputStream stream = new FileOutputStream(dataFile);
            XMLStreamWriter out;
            out = XMLOutputFactory.newInstance().createXMLStreamWriter(stream);
            out.writeStartDocument();
            out.writeStartElement("ATRParams");
            ArrayList<AtrClassValue> data = this.getDataAsArrayList();
            out.writeAttribute("No", Integer.toString(data.size()));
            for (AtrClassValue currValue : data) {
                currValue.writeAsXMLToStream(out);
            }
            out.writeEndElement();
            out.writeEndDocument();
            result = true;
        } catch (IOException ex) {
            Logger.getLogger(AtrClassGrid.class.getName()).log(Level.SEVERE, null, ex);
        } catch (XMLStreamException ex) {
            Logger.getLogger(AtrClassGrid.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    private AtrClassValue retrieveAtrClassValue(XMLEventReader in) throws XMLStreamException {
        AtrClassValue result = null;
        boolean blnSpinOn = true;
        do {
            XMLEvent nextEvent = in.peek();
            if (nextEvent.isStartElement()) {
                StartElement xmlStart = nextEvent.asStartElement();
                String name = xmlStart.getName().getLocalPart();
                if (name.equals("AtrClassValue")) {
                    blnSpinOn = false;
                }
            }
            if (blnSpinOn) {
                in.nextEvent();
            }
        } while (blnSpinOn);
        XMLEvent nextEvent = in.peek();

        if (nextEvent.isStartElement()) {
            if (nextEvent.asStartElement().getName().getLocalPart().equals("AtrClassValue")) {
                AtrClassValue newItem = new AtrClassValue();
                if (newItem.loadFromXMLStream(in, newItem)) {
                    result = newItem;
                }
            }
        }
        return result;
    }

    @Override
    public Iterator<AtrClassValue> iterator() {
        return this.getDataAsArrayList().iterator();
    }

    private void loadTestData() {
        int startTime = 0;
        int endTime = 180;
        startTime *= 1000;
        endTime *= 1000;
        AtrClassValue entry = makeAtrValueTwo(AtrClassEnum.UU, startTime, endTime, 0.003, 0.003);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.U, startTime, endTime, 0.0032, 0.0032);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PA, startTime, endTime, 0.0036, 0.0036);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PAP, startTime, endTime, 0.0038, 0.0038);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PP, startTime, endTime, 0.004, 0.004);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PH, startTime, endTime, 0.0042, 0.0064);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.MH, startTime, endTime, 0.0042, 0.0042);
        this.addItem(entry);

        startTime = 180;
        endTime = 360;
        startTime *= 1000;
        endTime *= 1000;
        entry = makeAtrValueTwo(AtrClassEnum.UU, startTime, endTime, 0.004, 0.004);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.U, startTime, endTime, 0.0044, 0.0044);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PA, startTime, endTime, 0.0052, 0.0052);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PAP, startTime, endTime, 0.0056, 0.0056);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PP, startTime, endTime, 0.006, 0.006);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PH, startTime, endTime, 0.0064, 0.0064);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.MH, startTime, endTime, 0.0064, 0.0064);
        this.addItem(entry);

        startTime = 360;
        endTime = 1800;
        startTime *= 1000;
        endTime *= 1000;
        entry = makeAtrValueTwo(AtrClassEnum.UU, startTime, endTime, 0.005, 0.006);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.U, startTime, endTime, 0.006, 0.007);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PA, startTime, endTime, 0.008, 0.009);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PAP, startTime, endTime, 0.009, 0.01);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PP, startTime, endTime, 0.01, 0.011);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PH, startTime, endTime, 0.011, 0.012);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.MH, startTime, endTime, 0.011, 0.012);
        this.addItem(entry);

        startTime = 1800;
        endTime = 5400;
        startTime *= 1000;
        endTime *= 1000;
        entry = makeAtrValueTwo(AtrClassEnum.UU, startTime, endTime, 0.006, 0.007);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.U, startTime, endTime, 0.007, 0.008);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PA, startTime, endTime, 0.009, 0.01);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PAP, startTime, endTime, 0.01, 0.011);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PP, startTime, endTime, 0.011, 0.012);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PH, startTime, endTime, 0.012, 0.013);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.MH, startTime, endTime, 0.012, 0.013);
        this.addItem(entry);

        startTime = 5400;
        endTime = 9000;
        startTime *= 1000;
        endTime *= 1000;
        entry = makeAtrValueTwo(AtrClassEnum.UU, startTime, endTime, 0.007, 0.008);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.U, startTime, endTime, 0.008, 0.009);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PA, startTime, endTime, 0.01, 0.011);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PAP, startTime, endTime, 0.011, 0.012);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PP, startTime, endTime, 0.012, 0.013);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PH, startTime, endTime, 0.013, 0.014);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.MH, startTime, endTime, 0.013, 0.014);
        this.addItem(entry);

        startTime = 9000;
        endTime = 12600;
        startTime *= 1000;
        endTime *= 1000;
        entry = makeAtrValueTwo(AtrClassEnum.UU, startTime, endTime, 0.008, 0.009);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.U, startTime, endTime, 0.009, 0.01);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PA, startTime, endTime, 0.011, 0.012);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PAP, startTime, endTime, 0.012, 0.013);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PP, startTime, endTime, 0.013, 0.014);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PH, startTime, endTime, 0.014, 0.015);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.MH, startTime, endTime, 0.014, 0.015);
        this.addItem(entry);

        startTime = 12600;
        endTime = 23400;
        startTime *= 1000;
        endTime *= 1000;
        entry = makeAtrValueTwo(AtrClassEnum.UU, startTime, endTime, 0.009, 0.009);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.U, startTime, endTime, 0.01, 0.01);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PA, startTime, endTime, 0.012, 0.012);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PAP, startTime, endTime, 0.013, 0.013);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PP, startTime, endTime, 0.014, 0.014);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.PH, startTime, endTime, 0.015, 0.015);
        this.addItem(entry);
        entry = makeAtrValueTwo(AtrClassEnum.MH, startTime, endTime, 0.015, 0.015);
        this.addItem(entry);
    }

    private void loadLastUsed() {
        this.loadFromXMLStream(null, this);
    }
    
    private AtrClassValue makeAtrValueTwo(AtrClassEnum newClass, int startTime, int endTime, double startValue, double endValue){
        return new AtrClassValue(newClass, startTime, endTime, startValue, endValue);
    }

    /**
     * @return the lockedIn
     */
    public boolean isLockedIn() {
        return lockedIn;
    }

    /**
     * @param lockedIn the lockedIn to set
     */
    public void setLockedIn(boolean lockedIn) {
        this.lockedIn = lockedIn;
    }
}
