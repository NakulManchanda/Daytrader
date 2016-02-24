/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.XMLPersistable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * This represents an entry in the atr class table. Each table entry defines the Putup class to
 * which it applies, the start and end time for its use (expressed as milliseconds from the start of
 * the trading day and a price range. 
 * 
 * These entries define a base price (startValue) that applies at a given time into the day (startTime) and then 'scales'
 * that price over the time period it is setup for (endTime - startTime) until it reaches an end price (endValue)
 * at the end of the time period (endTime)
 *
 * @author Roy
 */
public class AtrClassValue implements XMLPersistable<AtrClassValue>, Comparable<AtrClassValue> {

    private int startTime = 0;
    private int endTime = 180000;
    private AtrClassEnum atrclass = AtrClassEnum.UU;
    private double startValue = 0d;
    private double endValue = 0d;

    /**
     * Default Constructor 
     */
    public AtrClassValue() {
    }
    
    /**
     * Constructor with initalisation parameters
     * @param newClass - The AtrClass to which this AtrClassValue belongs
     * @param newStartTime - The time this value comes into effect measured in milliseconds since start of trading day
     * @param newEndTime - The time from which this value ceases to apply measured in milliseconds since start of trading day
     * @param newStartValue - The AtrClassValue to use at the start of the time range
     * @param newEndValue - The AtrClassValue to use at the end of the time range
     */
    public AtrClassValue(AtrClassEnum newClass, int newStartTime, int newEndTime, double newStartValue, double newEndValue){
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        this.atrclass = newClass;
        this.startValue = newStartValue;
        this.endValue = newEndValue;
    }

    /**
     * Retrieves the time in milliseconds after the start of trading that this AtrClassValue starts to apply
     * @return - integer being the The time in milliseconds after the start of trading that this AtrClassValue starts to apply
     */
    public int getStartTime() {
        return startTime;
    }

    /**
     * Retrieves the time in milliseconds after the start of trading that this AtrClassValue ceases to apply
     * @param startTime - integer being the The time in milliseconds after the start of trading that this AtrClassValue ceases to apply
     */
    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    /**
     * Accessor to retrieve the time in milliseconds since the start of the trading day at which this
     * AtrClassValue ceases to apply for calculation purposes
     * @return integer being the milliseconds into the day at which this time value ceases to apply
     */
    public int getEndTime() {
        return endTime;
    }

    /**
     * Accessot to set the time in milliseconds since the start of the trading day at which this
     * AtrClassValue ceases to apply for calculation purposes
     * @param endTime integer being the milliseconds into the day at which this time value ceases to apply
     */
    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

    /**
     * Accessor to retrieve the AtrClassEnum that a put up should have for this entry to be applied.
     * @return A AtrClassEnum
     */
    public AtrClassEnum getAtrclass() {
        return atrclass;
    }

    /**
     * Accessor to set the AtrClassEnum that a put up should have for this entry to be applied.
     * @param atrclass the new AtrClassEnum to which this AtrClassValue should be applied
     */
    public void setAtrclass(AtrClassEnum atrclass) {
        this.atrclass = atrclass;
    }

    /**
     * Accessor to retrieve the start value to be used at the beginning of the time period
     * @return double being the start value to be used for calculations at the beginning of the period
     */
    public double getStartValue() {
        return startValue;
    }

    /**
     *Accessor to set the start value to be used at the beginning of the time period
     * @param value double being the start value to be used for calculations at the beginning of the period
     */
    public void setStartValue(double value) {
        this.startValue = value;
    }
    
    /**
     * Test to see if this set of values should be used for calculations based on the time since the start of trading
     * and the AtrClass of a put up
     * @param Xms - Time in milliseconds since the start of trading
     * @param atrClass - The AtrClass that the put up is using
     * @return boolean True if this value should apply at the given time for the given class, false otherwise.
     */
    public boolean appliestoThisOffset(long Xms, AtrClassEnum atrClass){
        boolean result = false;
        if(0 < Xms && null != atrClass){
            if(Xms > this.startTime && Xms <= this.endTime && atrClass.equals(this.atrclass)){
                result = true;
            }
        }
        return result;
    }

    /**
     * Creates a String that may be used as a Key to this object in a Key Value pair data structure
     * such as a hash table.
     * @return A String that can be used as a Key to this object. It will be unique.
     */
    public String getHashKey() {
        String result = "NULL";
        if (null != this.atrclass) {
            result = this.atrclass.toString() + "," + Integer.toString(this.startTime) + "," + Integer.toString(this.endTime);
        } else {
            result = "NULL," + Integer.toString(this.startTime);
        }
        return result;
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, AtrClassValue dest) {
        boolean result = false;
        if (null != dest) {
            boolean abort = false;
            while (!abort) {
                try {
                    XMLEvent nextEvent = reader.nextEvent();
                    if (nextEvent.isStartElement() || nextEvent.isEndElement()) {
                        if (nextEvent.isStartElement()) {
                            StartElement xmlStartEvent = nextEvent.asStartElement();
                            String name = xmlStartEvent.getName().getLocalPart();
                            if (name.equals("Time")) {
                                Attribute attType = xmlStartEvent.getAttributeByName(new QName("Type"));
                                String type = attType.getValue();
                                boolean isStartTime = true;
                                if(type.equals("STARTTIME")){
                                    isStartTime = true;
                                } else {
                                    isStartTime = false;
                                }
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                strData = strData.replace("\n", "");
                                if(isStartTime){
                                    this.startTime = Integer.parseInt(strData);
                                }else{
                                    this.endTime = Integer.parseInt(strData);
                                }
                            }
                            if (name.equals("AtrClass")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                strData = strData.replace("\n", "");
                                this.atrclass = AtrClassEnum.getAtrClassFromString(strData);
                            }
                            if (name.equals("StartValue")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                strData = strData.replace("\n", "");
                                this.startValue = Double.parseDouble(strData);
                            }
                            if (name.equals("EndValue")) {
                                XMLEvent dataEvent = reader.nextEvent();
                                Characters data = dataEvent.asCharacters();
                                String strData = data.toString();
                                strData = strData.replace("\n", "");
                                this.endValue = Double.parseDouble(strData);
                            }
                        }
                        if(nextEvent.isEndElement()){
                            EndElement xmlEndEvent = nextEvent.asEndElement();
                            String name = xmlEndEvent.getName().getLocalPart();
                            if(name.equals("AtrClassValue")){
                                result = true;
                                abort = true;
                            }
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(AtrClassValue.class.getName()).log(Level.SEVERE, null, ex);
                    break;
                }
            }
            
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        try {
            writer.writeStartElement("AtrClassValue");
            writer.writeCharacters("\n");
            writer.writeStartElement("Time");
            writer.writeAttribute("Type", "STARTTIME");
            writer.writeCharacters("\n");
            writer.writeCharacters(Integer.toString(startTime));
            writer.writeCharacters("\n");
            writer.writeEndElement();
            writer.writeCharacters("\n");
            writer.writeStartElement("Time");
            writer.writeAttribute("Type", "ENDTIME");
            writer.writeCharacters("\n");
            writer.writeCharacters(Integer.toString(endTime));
            writer.writeCharacters("\n");
            writer.writeEndElement();
            writer.writeCharacters("\n");
            writer.writeStartElement("AtrClass");
            writer.writeCharacters("\n");
            writer.writeCharacters(this.atrclass.toString());
            writer.writeCharacters("\n");
            writer.writeEndElement();
            writer.writeCharacters("\n");
            writer.writeStartElement("StartValue");
            writer.writeCharacters("\n");
            writer.writeCharacters(Double.toString(this.startValue));
            writer.writeCharacters("\n");
            writer.writeEndElement();
            writer.writeStartElement("EndValue");
            writer.writeCharacters("\n");
            writer.writeCharacters(Double.toString(this.endValue));
            writer.writeCharacters("\n");
            writer.writeEndElement();
            writer.writeCharacters("\n");
            writer.writeEndElement();
            result = true;
        } catch (XMLStreamException ex) {
            Logger.getLogger(AtrClassValue.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        }
        return result;
    }

    /**
     * @return the endValue
     */
    public double getEndValue() {
        return endValue;
    }

    /**
     * @param endValue the endValue to set
     */
    public void setEndValue(double endValue) {
        this.endValue = endValue;
    }
    
    /**
     * Accessor to retrieve the total period of time in milliseconds that this value scales over
     * @return double the difference between this values end and start times in milliseconds
     */
    public double getPeriodLengthInMs(){
        return this.endTime - this.startTime;
    }
    
    /**
     * Accessor to retrieve the total amount by which the price value changes over the
     * time period to which this value is applied.
     * @return double being the difference between the end and start value.
     */
    public double getPriceChangeOverPeriod(){
        return this.endValue - this.startValue;
    }
    
    /**
     *Accessor to retrieve the rate at which the price changes for every millisecond that passes
     * @return double being the amount by which the value increases in one millisecond
     */
    public double getPriceChangePerMs(){
        return this.getPriceChangeOverPeriod() / this.getPeriodLengthInMs();
    }
    
    /**
     * Given a number of milliseconds into the trading day this function returns the scaled value
     * to be added to the startValue to give the price to use in formula
     * @param millsecs - Milliseconds since stock market opening time
     * @return double the amount to add to the start price to use at this time point or zero if this value should not be applied
     * at the given time.
     */
    public double getPriceAt(long millsecs){
        double result = 0d;
        if(0 < millsecs){
            long msIntoPeriod = millsecs - this.startTime;
            if(0 < msIntoPeriod && msIntoPeriod <= this.getPeriodLengthInMs()){
                result = this.getPriceChangePerMs() * msIntoPeriod;
            }
        }
        return result;
    }

    @Override
    public int compareTo(AtrClassValue o) {
        int result = 0;
        if(null != o){
            if(this.startTime != o.getStartTime() && this.endTime != o.getEndTime()){
                int endDiff = this.endTime - o.getEndTime();
                result = endDiff;
            }
        }
        return result;
    }
    
    /**
     * Given a number of milliseconds into the trading day this function returns the scaled value
     * to be used as the price in calculations.
     * @param time - Milliseconds since stock market opening time
     * @return double the Price to use at this time point or zero if this value should not be applied
     * at the given time.
     */
    public double getAtrFractionAt(long time){
        double result = 0;
        if(time >= this.startTime && time <= this.endTime){
            double timeIntoZone = time - this.startTime;
            result = this.startValue + (this.getPriceChangePerMs() * timeIntoZone);
        }
        return result;
    }
}