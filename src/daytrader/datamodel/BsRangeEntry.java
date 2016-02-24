/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.XMLPersistable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This class represents an entry in the BS Ranges table. The table will have
 * one entry for every put up class (AtrClassEnum entry)
 *
 * @author Roy
 */
public class BsRangeEntry implements Comparable<BsRangeEntry>, XMLPersistable<BsRangeEntry> {

    private AtrClassEnum putupClass;
    private Double bsPercentage;
    
    /**
     * Default Constructor produces required for serialisation to XML
     */
    public BsRangeEntry(){
    }

    /**
     * Constructor associates the given percentage with an AtrClassEnum enumeration
     * @param classVal - The AtrClassEnum to which the percentage applies
     * @param newPerc - double representing the percentage.
     */
    public BsRangeEntry(AtrClassEnum classVal, double newPerc) {
        this.putupClass = classVal;
        this.bsPercentage = newPerc;
    }
    
    /**
     * Test to ensure that valid values have been set for the AtrClassEnum
     * attribute and the percentage attribute. Used to validate loading.
     * @return boolean True if entries exist and the percentage is >= 0,
     * False otherwise.
     */
    public boolean isValid(){
        boolean result = false;
        if(null != this.putupClass && null != this.bsPercentage){
            if(this.bsPercentage >= 0){
                result = true;
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (this.putupClass != null ? this.putupClass.hashCode() : 0);
        hash = 79 * hash + (this.bsPercentage != null ? this.bsPercentage.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BsRangeEntry other = (BsRangeEntry) obj;
        if (this.putupClass != other.putupClass) {
            return false;
        }
        if (this.bsPercentage != other.bsPercentage && (this.bsPercentage == null || !this.bsPercentage.equals(other.bsPercentage))) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(BsRangeEntry o) {
        int result = 0;
        if (null != o) {
            Double dblDiff = this.bsPercentage - o.getBsPercentage();
            if (dblDiff != 0) {
                if (0 < dblDiff) {
                    result = 1;
                } else {
                    result = -1;
                }
            } else {
                //Percentage is the same test the class
                result = this.putupClass.classNumber() - o.getPutupClass().classNumber();
            }
        }
        return result;
    }

    /**
     * Accessor retrieves the AtrClassEnum to which this object relates
     * @return the putupClass
     */
    public AtrClassEnum getPutupClass() {
        return putupClass;
    }

    /**
     * Accessor retrieves the percentage to be used by BS Calculations
     * @return the bsPercentage
     */
    public double getBsPercentage() {
        return bsPercentage;
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, BsRangeEntry dest) {
        boolean result = false;
        if (null != reader) {
            if (null == dest) {
                dest = this;
            }
            boolean abort = false;
            while (!abort) {
                try {
                    XMLEvent nextEvent = reader.nextEvent();
                    if (nextEvent.isStartElement() || nextEvent.isEndElement()) {
                        if (nextEvent.isStartElement()) {
                            StartElement startElement = nextEvent.asStartElement();
                            String strName = startElement.getName().getLocalPart();
                            if (strName.equalsIgnoreCase("Class")) {
                                String elementText = reader.getElementText();
                                dest.setPutupClass(AtrClassEnum.getAtrClassFromString(elementText));
                            }
                            if (strName.equalsIgnoreCase("BSPercentage")) {
                                String elementText = reader.getElementText();
                                dest.setBsPercentage(Double.parseDouble(elementText));
                            }
                        }
                        if (nextEvent.isEndElement()) {
                            EndElement endElement = nextEvent.asEndElement();
                            String strName = endElement.getName().getLocalPart();
                            if (strName.equalsIgnoreCase("BSRangeEntry")) {
                                abort = true;
                                result = true;
                            }
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(BsRangeEntry.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        if (null != writer) {
            try {
                
                writer.writeStartElement("BSRangeEntry");
                
                writer.writeStartElement("Class");
                writer.writeCharacters(this.putupClass.toString());
                writer.writeEndElement();
                
                writer.writeStartElement("BSPercentage");
                writer.writeCharacters(this.bsPercentage.toString());
                writer.writeEndElement();
                
                writer.writeEndElement();
                
                result = true;
                
            } catch (XMLStreamException ex) {
                Logger.getLogger(BsRangeEntry.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }

    /**
     * Accessor to set the AtrClassEnum for this object (only used in loading from XML)
     * otherwise the value is read only, therefore this method is private
     * @param putupClass the putupClass to set
     */
    private void setPutupClass(AtrClassEnum putupClass) {
        this.putupClass = putupClass;
    }

    /**
     * Accessor to set the percentage for this object (only used in loading from XML)
     * otherwise the value is read only, therefore this method is private
     * @param bsPercentage the bsPercentage to set
     */
    private void setBsPercentage(double bsPercentage) {
        this.bsPercentage = bsPercentage;
    }
}