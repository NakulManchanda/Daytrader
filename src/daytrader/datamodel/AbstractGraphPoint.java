/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.IGraphPoint;
import daytrader.interfaces.IRoundFunction;
import daytrader.utils.DTUtil;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.TimeZone;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import rules.NoRoundingOp;
import rules.RoundToTenthCent;

/**
 * Base class to implement GraphPoints from.
 *
 * @author Roy
 */
public abstract class AbstractGraphPoint implements IGraphPoint {

    /**
     * Attribute to store the ID of the data request that generated this data point
     */
    protected int reqId;
    /**
     * The string representation of the time and date used by the stockbroker's server
     */
    protected String date;
    /**
     * Attribute to store the Open price for this data point
     */
    protected double open;
    /**
     *Attribute to store the High price for this data point
     */
    protected double high;
    /**
     * Attribute to store the low price for this data point
     */
    protected double low;
    /**
     * Attribute to store the close price for this data point
     */
    protected double close;
    /**
     * Attribute to store the volume traded at this data point
     */
    protected long volume;
    /**
     * Attribute to store the count of orders placed at this data point
     */
    protected int count;
    /**
     * Attribute to store the Weighted Average Price (WAP) for this data point
     */
    protected double WAP;
    /**
     * Attribute to store the has gaps flag for this data point
     */
    protected boolean hasGaps;
    /**
     * A Java Calendar encapsulating the time data for this graph point
     */
    protected Calendar calDate;

    /**
     * Added a recursion cache for use in tracking the Y-Line's the point might be involved in
     */
    protected RecursionCache recursionCache;
    /**
     * As the recursion cache will be storing data (i.e. writing into a point I will use a lock to manage multi threaded interactions).
     */
    protected ReentrantLock lock;

    /**
     * Default Constructor, All Graph Points are required to provide a zero argument constructor
     */
    public AbstractGraphPoint() {
        lock = new ReentrantLock();
    }

    /**
     * The data & time values for a Graph Point are provided as a string from the stockbroker's API which is nominally
     * stored into the this.date attribute. This method parses the string stored in that attribute and generates a
     * standard Java Calendar object that encapsulates the data. This object is the stored into the this.calDate attribute
     */
    protected void createCalendar() {
        if (null != this.date) {
            String[] arrDateParts = this.date.split("  ");
            int intYear = Integer.parseInt(arrDateParts[0].substring(0, 4));
            int intMonth = Integer.parseInt(arrDateParts[0].substring(4, 6));
            intMonth--;     //Month is a zero based value !!!
            int intDay = Integer.parseInt(arrDateParts[0].substring(6));
            Calendar tempCal = DTUtil.createCalendar(intYear, intMonth, intDay, DTConstants.EXCH_CLOSING_HOUR, DTConstants.EXCH_CLOSING_MIN, DTConstants.EXCH_CLOSING_SEC, DTConstants.EXCH_TIME_ZONE);
            tempCal.setTimeZone(TimeZone.getTimeZone("GMT"));
            int intHour = tempCal.get(Calendar.HOUR_OF_DAY);
            int intMin = tempCal.get(Calendar.MINUTE);
            int intSec = 0;
            if (arrDateParts.length > 1) {
                String[] arrTimeParts = arrDateParts[1].split(":");
                intHour = Integer.parseInt(arrTimeParts[0]);
                intMin = Integer.parseInt(arrTimeParts[1]);
                intSec = Integer.parseInt(arrTimeParts[2]);
            }
            this.calDate = DTUtil.createCalendar(intYear, intMonth, intDay, intHour, intMin, intSec, TimeZone.getTimeZone("Europe/London"));
        }
    }

    /**
     * Occasionally the stockbroker API provides date time data as a timestamp. this method
     * converts the timestamp to a standard Java Calendar and to a date time string more commonly used
     * by the stockbroker and stores them into the relevant attributes.
     * @param newTimestamp - The timestamp to convert and store.
     */
    protected void createCalendarFromTimestamp(long newTimestamp) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        cal.clear();
        cal.setTimeInMillis(newTimestamp);
        this.calDate = cal;
        //Now generate and store the stockbroker string
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
        this.date = formatter.format(this.calDate.getTime());
    }

    @Override
    public Calendar getCalDate() {
        Calendar result = Calendar.getInstance(TimeZone.getTimeZone("Europe/London"));
        result.setTime(calDate.getTime());
        return result;
    }

    @Override
    public String getDayAsCSVString() {
        String result = "";
        StringBuilder data = new StringBuilder("");
        int year = calDate.get(Calendar.YEAR);
        int month = calDate.get(Calendar.MONTH);
        month++;
        int day = calDate.get(Calendar.DAY_OF_MONTH);
        data.append(Integer.toString(year));
        data.append(",");
        switch (month) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                String strMonth = "0" + Integer.toString(month);
                data.append(strMonth);
                break;
            default:
                data.append(Integer.toString(month));
        }
        data.append(",");
        switch (day) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                String strDay = "0" + Integer.toString(day);
                data.append(strDay);
                break;
            default:
                data.append(Integer.toString(day));
        }
        result = data.toString();
        return result;
    }

    @Override
    public int getYear() {
        int result = 0;
        int year = calDate.get(Calendar.YEAR);
        result = year;
        return result;
    }

    @Override
    public int getMonth() {
        int result = 0;
        int month = calDate.get(Calendar.MONTH);
        result = month;
        return result;
    }

    @Override
    public int getDay() {
        int result = 0;
        int day = calDate.get(Calendar.DAY_OF_MONTH);
        result = day;
        return result;
    }

    @Override
    public int getDateAsNumber() {
        int result = 0;
        StringBuilder data = new StringBuilder("");
        int year = calDate.get(Calendar.YEAR);
        int month = calDate.get(Calendar.MONTH);
        month++;
        int day = calDate.get(Calendar.DAY_OF_MONTH);
        data.append(Integer.toString(year));
        switch (month) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                String strMonth = "0" + Integer.toString(month);
                data.append(strMonth);
                break;
            default:
                data.append(Integer.toString(month));
        }
        switch (day) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                String strDay = "0" + Integer.toString(day);
                data.append(strDay);
                break;
            default:
                data.append(Integer.toString(day));
        }
        result = Integer.parseInt(data.toString());
        return result;
    }

    @Override
    public long getTimestamp() {
        return this.calDate.getTimeInMillis();
    }

    @Override
    public double getLastPrice() {
        double wap = this.getWAP();
        return wap;
    }

    @Override
    public double getOpen() {
        return this.getOpen(AbstractGraphPoint.getNoOpRounder());
    }

    @Override
    public double getOpen(IRoundFunction rounder) {
        double result = this.open;
        if (null != rounder) {
            Number value = this.open;
            result = rounder.performRounding(value).doubleValue();
        }
        return result;
    }

    @Override
    public double getHigh() {
        return this.getHigh(AbstractGraphPoint.getNoOpRounder());
    }

    @Override
    public double getHigh(IRoundFunction rounder) {
        double result = this.high;
        if (null != rounder) {
            Number value = this.high;
            result = rounder.performRounding(value).doubleValue();
        }
        return result;
    }

    @Override
    public double getLow() {
        return this.getLow(AbstractGraphPoint.getNoOpRounder());
    }

    @Override
    public double getLow(IRoundFunction rounder) {
        double result = this.low;
        if (null != rounder) {
            Number value = this.low;
            result = rounder.performRounding(value).doubleValue();
        }
        return result;
    }

    @Override
    public double getClose() {
        return this.getClose(AbstractGraphPoint.getNoOpRounder());
    }

    @Override
    public double getClose(IRoundFunction rounder) {
        double result = this.close;
        if (null != rounder) {
            Number value = this.close;
            result = rounder.performRounding(value).doubleValue();
        }
        return result;
    }

    @Override
    public double getWAP() {
        return this.getWAP(AbstractGraphPoint.getNoOpRounder());
    }

    @Override
    public double getWAP(IRoundFunction rounder) {
        double result = this.WAP;
        if (null != rounder) {
            Number value = this.WAP;
            result = rounder.performRounding(value).doubleValue();
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        //For two abstract graph points to be equal they must have the same timestamp and the same last price.
        boolean result = false;
        if(obj instanceof AbstractGraphPoint){
            AbstractGraphPoint target = (AbstractGraphPoint)obj;
            if(this.getTimestamp() == target.getTimestamp()){
                if(this.getLastPrice() == target.getLastPrice()){
                    result = true;
                }
            }
        } else {
            result = super.equals(obj);
        }
        return result;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + (int) (Double.doubleToLongBits(this.WAP) ^ (Double.doubleToLongBits(this.WAP) >>> 32));
        return hash;
    }
    
    

    @Override
    public abstract int getOrderingValue();

    @Override
    public long getMSElapsedSinceStartOfTrading() {
        long result = 0;
        Calendar exchOT = DTUtil.getExchOpeningTimeFromPoint(this);
        result = this.getTimestamp() - exchOT.getTimeInMillis();
        return result;
    }

    @Override
    public String toCSVString() {
        String result = "";
        String strCSVOut = "" + Integer.toString(this.getReqId())
                + "," + this.date
                + "," + Double.toString(this.open / DTConstants.SCALE)
                + "," + Double.toString(this.high / DTConstants.SCALE)
                + "," + Double.toString(this.low / DTConstants.SCALE)
                + "," + Double.toString(this.close / DTConstants.SCALE)
                + "," + Long.toString(this.getVolume())
                + "," + Integer.toString(this.getCount())
                + "," + Double.toString(this.WAP / DTConstants.SCALE)
                + "," + Boolean.toString(this.isHasGaps())
                + "," + this.getCalDate().getTimeInMillis()
                + "," + this.getTypeAsString();
        strCSVOut = this.customToCSVString(strCSVOut);
        result = strCSVOut;
        return result;
    }

    /**
     * Provides a 'hook' method that can be overridden to introduce object specific
     * code to the CSV string serialisation process encapsulated in the toCSVString()
     * while avoiding the need to override and re-code that method. 
     * The default implementation of this method adds the ask and bid prices to the 
     * encoded CSV data (these prices are 0 for most Graph point objects).
     * @param baseCSV - The CSV String to be modified by the additional code
     * @return The CSV String after processing by the user implemented code
     */
    protected String customToCSVString(String baseCSV) {
        String result = "";
        if (null != baseCSV) {
            //Appends 0 ask and bid prices
            result = baseCSV + ",0,0";
        } else {
            result = baseCSV;
        }
        return result;
    }

    @Override
    public boolean fromCSVString(String strData) {
        boolean result = false;
        if (null != strData && 0 < strData.length()) {
            String[] parts = strData.split(",");
            try {
                this.reqId = Integer.parseInt(parts[0]);
                this.date = parts[1];
                this.open = (Double.parseDouble(parts[2])) * DTConstants.SCALE;
                this.high = (Double.parseDouble(parts[3])) * DTConstants.SCALE;
                this.low = (Double.parseDouble(parts[4])) * DTConstants.SCALE;
                this.close = (Double.parseDouble(parts[5])) * DTConstants.SCALE;
                this.volume = Integer.parseInt(parts[6]);
                this.count = Integer.parseInt(parts[7]);
                this.WAP = (Double.parseDouble(parts[8])) * DTConstants.SCALE;
                this.hasGaps = Boolean.parseBoolean(parts[9]);
                createCalendar();
                result = customFromCSVString(strData);
            } catch (NumberFormatException ex) {
                System.err.println("Error Parsing Stock data");
            }
        }
        return result;
    }

    /**
     * Provides a 'hook' method that can be overridden to introduce object specific
     * code to the CSV string parsing process encapsulated in the fromCSVString()
     * while avoiding the need to override and re-code that method.
     * No special parsing is required by default so the base implementation returns true.
     * @param strData - The string to parse for additional data
     * @return True if data parsed correctly and was stored. False otherwise.
     */
    protected boolean customFromCSVString(String strData) {
        return true;
    }

    private int DefaultTimeComparison(IGraphPoint o) {
        int result = 0;
        Comparator<AbstractGraphPoint> comp = AbstractGraphPoint.TimeComparator;
        result = comp.compare(this, (AbstractGraphPoint) o);
        return result;
    }

    private static IRoundFunction getNoOpRounder() {
        return new NoRoundingOp();
    }

    private static IRoundFunction getTenthCentRounder() {
        return new RoundToTenthCent();
    }

    @Override
    public int compareTo(IGraphPoint o) {
        return this.DefaultTimeComparison(o);
    }
    /**
     * A Java Comparator suitable to compare graph data points based on price
     */
    public static Comparator<AbstractGraphPoint> PriceComparator =
            new Comparator<AbstractGraphPoint>() {
        @Override
        public int compare(AbstractGraphPoint o1, AbstractGraphPoint o2) {
            int result = 0;
            if (o1.getLastPrice() < o2.getLastPrice()) {
                result = -1;
            } else if (o1.getLastPrice() > o2.getLastPrice()) {
                result = 1;
            }
            //If the price is the same then make the decision based on time
            if (0 == result) {
                long lngDiff = o1.getTimestamp() - o2.getTimestamp();
                if (0 < lngDiff) {
                    result = 1;
                } else if (0 > lngDiff) {
                    result = -1;
                }
            }
            return result;
        }
    };
    /**
     * A Java Comparator suitable to compare graph data points based on time. This
     * is the default comparator used to order graph data points in a time sequential
     * manner in a TreeSet.
     */
    public static Comparator<AbstractGraphPoint> TimeComparator =
            new Comparator<AbstractGraphPoint>() {
        @Override
        public int compare(AbstractGraphPoint o1, AbstractGraphPoint o2) {
            int result = 0;
            long lngDiff = o1.getTimestamp() - o2.getTimestamp();
            if (0 < lngDiff) {
                result = 1;
            } else if (0 > lngDiff) {
                result = -1;
            }
            //If Time is the same order by type of point
            if (0 == result) {
                result = o2.getOrderingValue() - o1.getOrderingValue();
            }
            return result;
        }
    };

    /**
     * @return the reqId
     */
    public int getReqId() {
        return reqId;
    }

    /**
     * @return the volume
     */
    public long getVolume() {
        return volume;
    }

    /**
     * @return the count
     */
    public int getCount() {
        return count;
    }

    /**
     * @return the hasGaps
     */
    public boolean isHasGaps() {
        return hasGaps;
    }

    @Override
    public void setOpen(double open) {
        this.open = DTUtil.step1Rounding(open).doubleValue();
    }

    @Override
    public void setHigh(double high) {
        this.high = DTUtil.step1Rounding(high).doubleValue();
    }

    @Override
    public void setLow(double low) {
        this.low = DTUtil.step1Rounding(low).doubleValue();
    }

    @Override
    public void setClose(double close) {
        this.close = DTUtil.step1Rounding(close).doubleValue();
    }

    @Override
    public void setWAP(double WAP) {
        this.WAP = DTUtil.step1Rounding(WAP).doubleValue();
    }

    /**
     * Accessor method to quickly set the 5 standard price values for a graph data
     * point.
     * @param open - double being the new open price for this Graph Data Point
     * @param high - double being the new high price for this Graph Data Point
     * @param low - double being the new low price for this Graph Data Point
     * @param close - double being the new close price for this Graph Data Point
     * @param WAP - double being the new WAP price for this Graph Data Point
     */
    protected void setValues(double open, double high, double low, double close, double WAP) {
        this.setOpen(open);
        this.setHigh(high);
        this.setLow(low);
        this.setClose(close);
        this.setWAP(WAP);
    }
    
    /**
     * Static method that accepts an XMLEventReader pointing at an XML data source.
     * The method reads the XML Data and initialises a new graph point based on it.
     * @param reader - The XMLEventReader connected to an XML data source.
     * @return A concrete implementation of an AbstractGraphPoint based on the data 
     * from the XML data source or NULL if the data could not be read or successfully
     * parsed.
     */
    public static AbstractGraphPoint loadPointFromStream(XMLEventReader reader) {
        AbstractGraphPoint result = null;
        if (null != reader) {
            boolean abort = false;
            while (!abort) {
                try {
                    XMLEvent nextEvent = reader.nextEvent();
                    if (nextEvent.isStartElement() || nextEvent.isEndElement()) {
                        if (nextEvent.isStartElement()) {
                            StartElement startElement = nextEvent.asStartElement();
                            String strName = startElement.getName().getLocalPart();
                            if (strName.equalsIgnoreCase("GraphPoint")) {
                                QName name = new QName("PointType");
                                Attribute objPType = startElement.getAttributeByName(name);
                                String strClassType = objPType.getValue();
                                Class<?> forName = Class.forName(strClassType);
                                Object newInstance = forName.newInstance();
                                if (newInstance instanceof AbstractGraphPoint) {
                                    result = (AbstractGraphPoint) newInstance;
                                    //Now we have a point call the specific loader for this type
                                    if (result.loadFromXMLStream(reader, result)) {
                                        abort = true;
                                    } else {
                                        result = null;
                                    }
                                }
                            }
                        }
                        if (nextEvent.isEndElement()) {
                            EndElement endElement = nextEvent.asEndElement();
                            String strName = endElement.getName().getLocalPart();
                            if (strName.equalsIgnoreCase("GraphPoint")) {
                                abort = true;
                            }
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(AbstractGraphPoint.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(AbstractGraphPoint.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InstantiationException ex) {
                    Logger.getLogger(AbstractGraphPoint.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(AbstractGraphPoint.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, IGraphPoint dest) {
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
                            if (strName.equalsIgnoreCase("ReqId")) {
                                String elementText = reader.getElementText();
                                int newId = Integer.parseInt(elementText);
                                dest.setReqId(newId);
                            }
                            if (strName.equalsIgnoreCase("OpenValue")) {
                                String elementText = reader.getElementText();
                                double newOpen = Double.parseDouble(elementText);
                                dest.setOpen(newOpen);
                            }
                            if (strName.equalsIgnoreCase("CloseValue")) {
                                String elementText = reader.getElementText();
                                double newClose = Double.parseDouble(elementText);
                                dest.setClose(newClose);
                            }
                            if (strName.equalsIgnoreCase("HighValue")) {
                                String elementText = reader.getElementText();
                                double newHighVal = Double.parseDouble(elementText);
                                dest.setHigh(newHighVal);
                            }
                            if (strName.equalsIgnoreCase("LowValue")) {
                                String elementText = reader.getElementText();
                                double newLowVal = Double.parseDouble(elementText);
                                dest.setLow(newLowVal);
                            }
                            if (strName.equalsIgnoreCase("WapValue")) {
                                String elementText = reader.getElementText();
                                double newWapVal = Double.parseDouble(elementText);
                                dest.setWAP(newWapVal);
                            }
                            if (strName.equalsIgnoreCase("Timestamp")) {
                                String elementText = reader.getElementText();
                                long newTimestamp = Long.parseLong(elementText);
                                dest.setCalDate(newTimestamp);
                            }
                            if (strName.equalsIgnoreCase("Volume")) {
                                String elementText = reader.getElementText();
                                int newVol = Integer.parseInt(elementText);
                                dest.setVolume(newVol);
                            }
                            if (strName.equalsIgnoreCase("Count")) {
                                String elementText = reader.getElementText();
                                int newCount = Integer.parseInt(elementText);
                                dest.setCount(newCount);
                            }
                            if (strName.equalsIgnoreCase("HasGaps")) {
                                String elementText = reader.getElementText();
                                boolean newHasGaps = Boolean.parseBoolean(elementText);
                                dest.setHasGaps(newHasGaps);
                            }
                            this.customReadXML(reader, dest);
                        }
                        if (nextEvent.isEndElement()) {
                            EndElement endElement = nextEvent.asEndElement();
                            String strName = endElement.getName().getLocalPart();
                            if (strName.equalsIgnoreCase("GraphPoint")) {
                                abort = true;
                                result = true;
                            }
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(AbstractGraphPoint.class.getName()).log(Level.SEVERE, null, ex);
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
                writer.writeStartElement("GraphPoint");
                writer.writeAttribute("PointType", this.getClass().getName());

                writer.writeStartElement("ReqId");
                writer.writeCharacters(((Integer) this.reqId).toString());
                writer.writeEndElement();

                writer.writeStartElement("OpenValue");
                writer.writeCharacters(((Double) this.open).toString());
                writer.writeEndElement();

                writer.writeStartElement("CloseValue");
                writer.writeCharacters(((Double) this.close).toString());
                writer.writeEndElement();

                writer.writeStartElement("HighValue");
                writer.writeCharacters(((Double) this.high).toString());
                writer.writeEndElement();

                writer.writeStartElement("LowValue");
                writer.writeCharacters(((Double) this.low).toString());
                writer.writeEndElement();

                writer.writeStartElement("WapValue");
                writer.writeCharacters(((Double) this.WAP).toString());
                writer.writeEndElement();

                writer.writeStartElement("Timestamp");
                writer.writeCharacters(((Long) this.calDate.getTimeInMillis()).toString());
                writer.writeEndElement();

                writer.writeStartElement("DataDate");
                writer.writeCharacters(this.date);
                writer.writeEndElement();

                writer.writeStartElement("Volume");
                writer.writeCharacters(((Long) this.volume).toString());
                writer.writeEndElement();

                writer.writeStartElement("Count");
                writer.writeCharacters(((Integer) this.count).toString());
                writer.writeEndElement();

                writer.writeStartElement("HasGaps");
                String val = "";
                if (this.hasGaps) {
                    val = "TRUE";
                } else {
                    val = "FALSE";
                }
                writer.writeCharacters(val);
                writer.writeEndElement();

                boolean customWriteXML = this.customWriteXML(writer);

                writer.writeEndElement();

                if (customWriteXML) {
                    result = true;
                }

            } catch (XMLStreamException ex) {
                Logger.getLogger(AbstractGraphPoint.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }

    /**
     * A hook function that can be overridden to insert object specific code into the
     * standard graph point XML parsing code in the loadFromXMLStream() method 
     * without having to override and re-code that method.
     * @param reader - XMLEventReader connected to an XML data source
     * @param dest - The IGraphPoint object in which to store the data read in
     */
    protected void customReadXML(XMLEventReader reader, IGraphPoint dest) {
    }

    /**
     * A hook function that can be overridden to insert object specific code into the
     * standard graph point XML serialisation code in the writeAsXMLToStream() method 
     * without having to override and re-code that method.
     * @param writer - An XMLStreamWriter to write the XML Code to
     * @return boolean True if the XML was successfully written to the stream, False otherwise.
     */
    protected boolean customWriteXML(XMLStreamWriter writer) {
        return true;
    }

    @Override
    public void setReqId(int newId) {
        this.reqId = newId;
    }

    @Override
    public void setVolume(long newVol) {
        this.volume = newVol;
    }

    @Override
    public void setCount(int newCount) {
        this.count = newCount;
    }

    @Override
    public void setHasGaps(boolean newHasGaps) {
        this.hasGaps = newHasGaps;
    }

    @Override
    public void setCalDate(long newTimestamp) {
        this.createCalendarFromTimestamp(newTimestamp);
    }

    @Override
    public RecursionCache getRecursionCache() {
        RecursionCache result = null;
        lock.lock();
        try {
            if(null != this.recursionCache){
                result = new RecursionCache(this.recursionCache);
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean setRecursionCache(RecursionCache newCache) {
        boolean result = false;
        lock.lock();
        try {
            if (null != newCache) {
                this.recursionCache = newCache;
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Abstract method to be implemented by every concrete implementation of an Abstract Graph Point.
     * This method should return the name of the concrete class as a string
     * @return A String being the class name as it is declared
     */
    protected abstract String getTypeAsString();

    /**
     * Tests if this price time graph point represents the start of a trading day on
     * the date to which the time point relates.
     * @return boolean True if the time for this graph point represents the time at which 
     * the stock market opened on that trading day, False otherwise.
     */
    public boolean isStartOfDay() {
        boolean result = false;
        Calendar exchOpeningCalendar = DTUtil.getExchOpeningCalendar(this.calDate);
        if (this.calDate.getTimeInMillis() == exchOpeningCalendar.getTimeInMillis()) {
            result = true;
        }
        return result;
    }
}
