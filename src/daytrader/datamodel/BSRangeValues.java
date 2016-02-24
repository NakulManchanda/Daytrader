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
import java.util.EnumMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This class is a container for the current set of BsRangeEntries use for the
 * BS Range Calculations.
 *
 * @author Roy
 */
public class BSRangeValues implements XMLPersistable<BSRangeValues> {

    private static final String XML_FILE_NAME = "BSRangeValues.xml";
    private EnumMap<AtrClassEnum, BsRangeEntry> entries;

    /**
     * Default Constructor, looks for an XML file (as defined by class attribute XML_FILE_NAME) containing 
     * BsRangeEntry data. If this file exists it will be loaded, if not then the test values provided
     * by Bryn will be used instead.
     */
    public BSRangeValues() {
        this.entries = new EnumMap<AtrClassEnum, BsRangeEntry>(AtrClassEnum.class);
        if (!this.loadBSRangeValues()) {
            //Setup default (test) data as saved file load failed
            for (AtrClassEnum currEnum : AtrClassEnum.values()) {
                switch (currEnum) {
                    case UU:
                        BsRangeEntry aEntry = new BsRangeEntry(currEnum, 0.007);
                        this.entries.put(aEntry.getPutupClass(), aEntry);
                        break;
                    case U:
                        aEntry = new BsRangeEntry(currEnum, 0.008);
                        this.entries.put(aEntry.getPutupClass(), aEntry);
                        break;
                    case PA:
                        aEntry = new BsRangeEntry(currEnum, 0.01);
                        this.entries.put(aEntry.getPutupClass(), aEntry);
                        break;
                    case PAP:
                        aEntry = new BsRangeEntry(currEnum, 0.011);
                        this.entries.put(aEntry.getPutupClass(), aEntry);
                        break;
                    default:
                        aEntry = new BsRangeEntry(currEnum, 0.012);
                        this.entries.put(aEntry.getPutupClass(), aEntry);
                }
            }
        }
    }

    /**
     * Searches for the file defined by the XML_FILE_NAME constant and if found
     * loads data for this class from the file.
     * @return boolean True if data was loaded, False otherwise
     */
    public final boolean loadBSRangeValues() {
        boolean result = false;
        File bsRecords = new File(XML_FILE_NAME);
        if (bsRecords.exists() && bsRecords.canRead()) {
            //Load from the file
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(bsRecords);
                XMLEventReader in = XMLInputFactory.newInstance().createXMLEventReader(stream);
                result = this.loadFromXMLStream(in, this);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(BSRangeValues.class.getName()).log(Level.SEVERE, null, ex);
            } catch (XMLStreamException ex) {
                Logger.getLogger(BSRangeValues.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (null != stream) {
                        stream.close();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(BSRangeValues.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    /**
     * Persists the data stored in this object to the XML file defined by XML_FILE_NAME
     * @return boolean True if the data was saved successfully, False otherwise. 
     */
    public boolean saveBSRangeValues() {
        boolean result = false;
        File bsRecords = new File(XML_FILE_NAME);
        FileOutputStream stream = null;
        try {
            //If file exists back it up
            if (bsRecords.exists()) {

                File backup = new File("OLD_" + XML_FILE_NAME);
                if (bsRecords.renameTo(backup)) {
                    bsRecords.delete();
                }
                bsRecords.createNewFile();
            } else {
                bsRecords.createNewFile();
            }
            //Create Stream to new file
            stream = new FileOutputStream(bsRecords);
            XMLStreamWriter out;
            out = XMLOutputFactory.newInstance().createXMLStreamWriter(stream);
            result = this.writeAsXMLToStream(out);
        } catch (IOException ex) {
            Logger.getLogger(BSRangeValues.class.getName()).log(Level.SEVERE, null, ex);
        } catch (XMLStreamException ex) {
            Logger.getLogger(BSRangeValues.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (null != stream) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    @Override
    public final boolean loadFromXMLStream(XMLEventReader reader, BSRangeValues dest) {
        boolean result = false;
        if (null != reader) {
            if (null == dest) {
                dest = this;
            }
            while (reader.hasNext()) {
                try {
                    XMLEvent event = reader.nextEvent();
                    if (event.isStartElement()) {
                        StartElement startElement = event.asStartElement();
                        String strName = startElement.getName().getLocalPart();
                        if (strName.equalsIgnoreCase("BSRangeValues")) {
                            //Get the number of elements to load
                            QName name = new QName("Count");
                            startElement.getAttributeByName(name);
                            Attribute objCount = startElement.getAttributeByName(name);
                            String strCount = objCount.getValue();
                            int intCount = Integer.parseInt(strCount);
                            for (int i = 0; i < intCount; i++) {
                                BsRangeEntry aEntry = new BsRangeEntry();
                                aEntry.loadFromXMLStream(reader, aEntry);
                                if (aEntry.isValid()) {
                                    dest.addBSRangeEntry(aEntry);
                                }
                            }
                        }
                    }
                    if (event.isEndElement()) {
                        EndElement endElement = event.asEndElement();
                        String strName = endElement.getName().getLocalPart();
                        if (strName.equalsIgnoreCase("BSRangeValues")) {
                            result = true;
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(BSRangeValues.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    /**
     * Persists this object to the provided XML Stream
     *
     * @param writer - The XML Stream to persist to
     * @return boolean True if the object was correctly written, False
     * otherwise.
     */
    @Override
    public final boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        if (null != writer) {
            try {
                writer.writeStartDocument();

                writer.writeStartElement("BSRangeValues");
                Integer count = this.entries.size();
                writer.writeAttribute("Count", count.toString());

                Set<AtrClassEnum> keySet = this.entries.keySet();
                for (AtrClassEnum currClass : keySet) {
                    BsRangeEntry currEntry = this.entries.get(currClass);
                    currEntry.writeAsXMLToStream(writer);
                }
                writer.writeEndElement();

                writer.writeEndDocument();
                result = true;
            } catch (XMLStreamException ex) {
                Logger.getLogger(BSRangeValues.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }
    
    /**
     * Accessor to add a new BsRangeEntry to the data managed by this object
     * @param newEntry - The BsRangeEntry to add
     * @return boolean True if the entry was accepted and added, False otherwise.
     */
    public boolean addBSRangeEntry(BsRangeEntry newEntry){
        boolean result = false;
        if(null != newEntry){
            this.entries.put(newEntry.getPutupClass(), newEntry);
            result = true;
        }
        return result;
    }
    
    /**
     * Given a put ups AtrClassEnum this method locates the matching BsRangeEntry
     * and retrieves it.
     * @param target - The AtrClassEnum for which the matching BsRangeEntry is required
     * @return The BsRangeEntry for the provided AtrClassEnum or NULL if no match is found
     */
    public BsRangeEntry getEntryForClass(AtrClassEnum target){
        BsRangeEntry result = null;
        if(null != target){
            result = this.entries.get(target);
        }
        return result;
    }
}
