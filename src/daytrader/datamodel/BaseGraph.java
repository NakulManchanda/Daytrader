/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import daytrader.interfaces.ICSVPersistable;
import daytrader.interfaces.IGraphLine;
import daytrader.interfaces.Lockable;
import daytrader.interfaces.XMLPersistable;
import daytrader.utils.DTUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
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
 * This is a base class for a line graph it contains a collection of Graph
 * points representing a line graph and provides supporting data about the
 * collection (Highest, Lowest point etc). This is a THREADSAFE class that
 * allows multiple threads to access it without deadlocks or synchronisation
 * issues.
 *
 * @param <T> Any class that extends from AbstractGraphPoint. Therefore T represents a
 * point plotted on a Price / Time graph such as those rendered by the stock brokers
 * Trader Workstation application.
 * @author Roy
 */
public class BaseGraph<T extends AbstractGraphPoint> implements NavigableSet<T>, ICSVPersistable, XMLPersistable<BaseGraph<T>>, Lockable {

    /**
     * Used to maintain an ordered list based on the time the response relates to.
     * NB: a response refers to a price time graph point received from the 
     * stock brokers server.
     */
    protected TreeSet<T> tsResponses;
    //Stores Graphs loaded for previous days
    private HashMap<Integer, BaseGraph> previousGraphs;
    //Temporary Graph (for workings)
    private BaseGraph<T> tempGraph;
    //The putup details for this graph
    private Putup putup;
    //Stores the previous days close
    private AbstractGraphPoint prevDayClose;
    /**
     * This stores the next valid order id to use when sending an order to stock broker's
     * server. Not currently used but will be needed for order entry.
     */
    protected int nextValidOrderId = -1;
    /**
     * Lock to control access and synchronisation of threads.
     */
    protected ReentrantLock lock = new ReentrantLock();
    //This attribute stores the YLines for this graph
    private ArrayList<GraphLine> ylines;
    //This attribute stores the trading days in the last week
    private TreeSet<Integer> tradingDays;
    //when I re-write the YLines use this attribute to store the 1 sec data
    private BaseGraph<AbstractGraphPoint> ylineOneSecGraph;
    //This attribute stores the data downloaded to determine the close of the day value
    private BaseGraph<AbstractGraphPoint> graphClosePrevDayData;

    /**
     * Accessor to retrieve the 'stock ticker' of the stock to which this graph relates.
     * Each graph should be associated with a 'Putup' that contains this three to
     * five letter code. For example Microsoft's stock ticker is MSFT
     * @return A string containing the securities stock ticker code.
     */
    public String getStockTicker() {
        String result = "UNKNOWN";
        lock.lock();
        try {
            if (null != this.putup) {
                result = this.putup.getTickerCode();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor set the stock ticker code for the Putup associated with this graph's
     * point data
     * @param stockTicker - String being the stock ticker to set.
     */
    public void setStockTicker(String stockTicker) {
        lock.lock();
        try {
            if (null != this.putup) {
                this.putup.setTickerCode(stockTicker);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Each stock / security is quoted on a single exchange this accessor method
     * retrieves the enumeration representing the market on which the security
     * is quoted.
     * @return A MarketEnum identifying the exchange on which the security is traded.
     */
    public MarketEnum getExchange() {
        MarketEnum result = null;
        lock.lock();
        try {
            if (null != this.putup) {
                result = this.putup.getMarket();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor method to set the security on which the associated Putup is being traded.
     * @param exchange - A MarketEnum identifying the stock market on which this security is traded.
     */
    public void setExchange(MarketEnum exchange) {
        lock.lock();
        try {
            if (null != this.putup) {
                this.putup.setMarket(exchange);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor method to retrieve the next valid order ID. NB: Consider moving this
     * into the Real Time Run Manager class when coding placing an entry
     * @return An integer being the next order ID to use.
     */
    public int getNextValidOrderId() {
        int result = -1;
        lock.lock();
        try {
            result = nextValidOrderId;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor method to set the next valid order ID. NB: Consider moving this
     * into the Real Time Run Manager class when coding placing an entry
     * @param nextValidOrderId - integer being the next order ID to use
     */
    public void setNextValidOrderId(int nextValidOrderId) {
        lock.lock();
        try {
            this.nextValidOrderId = nextValidOrderId;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Stores the graph point with the highest last price. The highest and lowest
     * points on the graph are often needed for Bryn's trading rules
     */
    protected T objHighest;
    /**
     * Stores the graph point with the lowest last price. The highest and lowest
     * points on the graph are often needed for Bryn's trading rules
     */
    protected T objLowest;

    /**
     * Default no argument constructor constructs an empty graph that is not 
     * associated with any Putup and where the default Time ordered sorting of
     * graph data points is used.
     */
    public BaseGraph() {
        this.tsResponses = new TreeSet<T>();
        this.ylines = new ArrayList<GraphLine>();
        this.tradingDays = new TreeSet<Integer>();
    }

    /**
     * Constructs an empty graph that is not associated with any Putup and which
     * orders its data points using the provided comparator.
     * @param comparator - A comparator that defines an ordering for the graph points
     * added to it. Two comparators have been provided a Time ordered comparator
     * (this is the default) and a Price ordered comparator,
     */
    public BaseGraph(Comparator<? super T> comparator) {
        this.tsResponses = new TreeSet<T>(comparator);
        this.ylines = new ArrayList<GraphLine>();
        this.tradingDays = new TreeSet<Integer>();
    }

    /**
     * Constructor creates a graph that is not associated with any Putup, orders
     * its data using the default Time ordering and which contains the data points
     * in the provided collection.
     * @param c - A Java Collection of Price Time data points.
     */
    public BaseGraph(Collection<? extends T> c) {
        this();
        this.tsResponses.addAll(c);
        this.refreshMaxMin();
        this.removeInvalidPoints();
    }

    /**
     * Constructor creates a graph that is not associated with any Putup, orders
     * its data using the default Time ordering and which contains the data points
     * in the provided SortedSet.
     * @param s - A Java SortedSet (such as TreeSet) that contains the data points
     * to add to the graph.
     */
    public BaseGraph(SortedSet<T> s) {
        this(s.comparator());
        this.tsResponses.addAll(s);
        this.refreshMaxMin();
        this.removeInvalidPoints();
    }

    private boolean addDataPoint(T newItem) {
        boolean result = false;
        if (null != newItem && this.isPointInTradingHours(newItem)) {
            lock.lock();
            try {
                result = this.tsResponses.add(newItem);
                if (null == this.objHighest) {
                    this.objHighest = newItem;
                }
                if (null == this.objLowest) {
                    this.objLowest = newItem;
                }
                if (this.objHighest.getLastPrice() < newItem.getLastPrice()) {
                    this.objHighest = newItem;
                }
                if (this.objLowest.getLastPrice() >= newItem.getLastPrice()) {
                    this.objLowest = newItem;
                }
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    private void refreshMaxMin() {
        lock.lock();
        try {
            this.objHighest = null;
            this.objLowest = null;
            for (T currItem : this.tsResponses) {
                if (null == this.objHighest) {
                    this.objHighest = currItem;
                }
                if (null == this.objLowest) {
                    this.objLowest = currItem;
                }
                if (this.objHighest.getLastPrice() <= currItem.getLastPrice()) {
                    this.objHighest = currItem;
                }
                if (this.objLowest.getLastPrice() >= currItem.getLastPrice()) {
                    this.objLowest = currItem;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor to retrieve the highest (price) point on the data graph
     * @return - The point on the data graph that has the highest price
     */
    public T getHighestPointSoFar() {
        T result = null;
        lock.lock();
        try {
            if (null != this.objHighest) {
                result = this.objHighest;
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to retrieve the lowest (price) point on the data graph
     * @return - The point on the data graph that has the lowest price
     */
    public T getLowestPointSoFar() {
        T result = null;
        lock.lock();
        try {
            if (null != this.objLowest) {
                result = this.objLowest;
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to retrieve the date and time of the first data point on this graph.
     * NB: Although the markets open at 09:30 New York time it is possible that data
     * may not start to be available until some time after this (equally on some days
     * data / trading may start early but these are normally rejected by the graph
     * and cannot be included in the graph object).
     * @return A Java Calendar encapsulating the date and time of the first data 
     * point in this graph or NULL if the graph has no data.
     */
    public Calendar getDataStartTime() {
        Calendar result = null;
        lock.lock();
        try {
            if (0 < this.tsResponses.size()) {
                T first = this.tsResponses.first();
                result = first.getCalDate();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to retrieve the date and time of the last data point in this graph
     * @return A Java Calendar encapsulating the date and time of the last data 
     * point in this graph or NULL if the graph has no data.
     */
    public Calendar getDataEndTime() {
        Calendar result = null;
        lock.lock();
        try {
            if (0 < this.tsResponses.size()) {
                T last = this.tsResponses.last();
                result = last.getCalDate();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * It is possible that several points may be the 'high of the day' where the
     * graph returns to the same highest price several times. In this case several
     * rules require us to identify the 'earliest' time at which the high of the days's
     * price was reached. 
     * @return The Abstract Graph Point which represents the earliest point in time 
     * at which the highest price on the graph was reached.
     */
    public AbstractGraphPoint getEarliestHigh() {
        AbstractGraphPoint result = null;
        if (null != this.objHighest) {
            BaseGraph<AbstractGraphPoint> priceGraph = new BaseGraph<AbstractGraphPoint>(AbstractGraphPoint.PriceComparator);
            priceGraph.addAll(this);
            T first = this.first();
            DummyGraphPoint start = new DummyGraphPoint(first.getTimestamp(), this.objHighest.getLastPrice());
            NavigableSet<AbstractGraphPoint> subSet = priceGraph.subSet(start, true, this.objHighest, true);
            result = subSet.first();
        }
        return result;
    }

    /**
     * It is possible that several points may be the 'low of the day' where the
     * graph returns to the same lowest price several times. In this case several
     * rules require us to identify the 'earliest' time at which the low of the days's
     * price was reached. 
     * @return The Abstract Graph Point which represents the earliest point in time 
     * at which the lowest price on the graph was reached.
     */
    public AbstractGraphPoint getEarliestLow() {
        AbstractGraphPoint result = null;
        if (null != this.objLowest) {
            BaseGraph<AbstractGraphPoint> priceGraph = new BaseGraph<AbstractGraphPoint>(AbstractGraphPoint.PriceComparator);
            priceGraph.addAll(this);
            T first = this.first();
            DummyGraphPoint start = new DummyGraphPoint(first.getTimestamp(), this.objLowest.getLastPrice());
            NavigableSet<AbstractGraphPoint> subSet = priceGraph.subSet(start, true, this.objLowest, true);
            result = subSet.first();
        }
        return result;
    }

    @Override
    public String toCSVString() {
        StringBuilder buff = new StringBuilder("ID,Date,Open,High,Low,Close,Volume,Count,WAP,hasGaps,Timestamp,PointType,ASK,BID\n");
        lock.lock();
        try {
            for (T currResponse : this.tsResponses) {
                buff.append(currResponse.toCSVString());
                buff.append("\n");
            }
        } finally {
            lock.unlock();
        }
        return buff.toString();
    }

    /**
     * Not supported to load a graph create a new instance of of this class and
     * add IGraphPoints'
     */
    @Override
    public boolean fromCSVString(String strData) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public T lower(T e) {
        T result = null;
        lock.lock();
        try {
            result = this.tsResponses.lower(e);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public T floor(T e) {
        T result = null;
        lock.lock();
        try {
            result = this.tsResponses.floor(e);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public T ceiling(T e) {
        T result = null;
        lock.lock();
        try {
            result = this.tsResponses.ceiling(e);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public T higher(T e) {
        T higher = null;
        lock.lock();
        try {
            higher = this.tsResponses.higher(e);
        } finally {
            lock.unlock();
        }
        return higher;
    }

    @Override
    public T pollFirst() {
        T result = null;
        lock.lock();
        try {
            result = this.tsResponses.pollFirst();
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public T pollLast() {
        T result = null;
        lock.lock();
        try {
            result = this.tsResponses.pollLast();
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public Iterator<T> iterator() {
        return this.deepCopyResponses().iterator();
    }

    @Override
    public NavigableSet<T> descendingSet() {
        NavigableSet<T> result = null;
        TreeSet<T> backingSet = null;
        lock.lock();
        try {
            backingSet = new TreeSet<T>(this.tsResponses);
        } finally {
            lock.unlock();
        }
        if (null != backingSet) {
            result = backingSet.descendingSet();
        }
        return result;
    }

    @Override
    public Iterator<T> descendingIterator() {
        Iterator<T> iterator = null;
        TreeSet<T> backingSet = null;
        lock.lock();
        try {
            backingSet = new TreeSet<T>(this.tsResponses);
        } finally {
            lock.unlock();
        }
        if (null != backingSet) {
            iterator = backingSet.descendingIterator();
        }
        return iterator;
    }

    @Override
    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        NavigableSet<T> result = null;
        TreeSet<T> deepCopyResponses = this.deepCopyResponses();
        if(null != deepCopyResponses){
            //Ensure from and to are the right way around
            if(fromElement.getTimestamp() <= toElement.getTimestamp()){
                result = deepCopyResponses.subSet(fromElement, fromInclusive, toElement, toInclusive);
            } else {
                result = deepCopyResponses.subSet(toElement, fromInclusive, fromElement, toInclusive);
            }
        }
        return result;
    }

    @Override
    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        NavigableSet<T> headSet = null;
        lock.lock();
        try {
            headSet = this.deepCopyResponses().headSet(toElement, inclusive);
        } finally {
            lock.unlock();
        }
        return headSet;
    }

    @Override
    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        return this.deepCopyResponses().tailSet(fromElement, inclusive);
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        return this.deepCopyResponses().subSet(fromElement, toElement);
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        SortedSet<T> headSet = null;
        lock.lock();
        try {
            headSet = this.deepCopyResponses().headSet(toElement);
        } finally {
            lock.unlock();
        }
        return headSet;
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        return this.deepCopyResponses().tailSet(fromElement);
    }

    @Override
    public Comparator<? super T> comparator() {
        Comparator<? super T> result = null;
        lock.lock();
        try {
            result = this.tsResponses.comparator();
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public T first() {
        T result = null;
        lock.lock();
        try {
            if (0 < this.tsResponses.size()) {
                result = this.tsResponses.first();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public T last() {
        T result = null;
        lock.lock();
        try {
            if (0 < this.tsResponses.size()) {
                result = this.tsResponses.last();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public int size() {
        int result = 0;
        lock.lock();
        try {
            result = this.tsResponses.size();
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean isEmpty() {
        boolean result = false;
        lock.lock();
        try {
            result = this.tsResponses.isEmpty();
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean contains(Object o) {
        boolean result = false;
        lock.lock();
        try {
            result = this.tsResponses.contains(o);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public Object[] toArray() {
        return this.deepCopyResponses().toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return this.deepCopyResponses().toArray(a);
    }

    @Override
    public boolean add(T e) {
        return this.addDataPoint(e);
    }

    @Override
    public boolean remove(Object o) {
        boolean result = false;
        lock.lock();
        try {
            result = this.tsResponses.remove(o);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        boolean result = false;
        lock.lock();
        try {
            result = this.tsResponses.containsAll(c);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean result = false;
        lock.lock();
        try {
            result = this.tsResponses.addAll(c);
            if (result) {
                this.refreshMaxMin();
                this.removeInvalidPoints();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean result = false;
        lock.lock();
        try {
            result = this.tsResponses.retainAll(c);
            this.removeInvalidPoints();
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean result = false;
        lock.lock();
        try {
            result = this.tsResponses.removeAll(c);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            this.tsResponses.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Converts the data in this graph to a SortedSet using a DEEP COPY operation
     * The resulting SortedSet is independent of the graph and NOT backed by the graph.
     * Changes to the graph will not be reflected in the SortedSet and changes to 
     * the SortedSet will not impact on the graph
     * @return A SortedSet backed by a TreeSet that is independent of the TreeSet used
     * by this graph to store its data.
     */
    public SortedSet<T> retrieveAllDataAsList() {
        return this.deepCopyResponses();
    }

    /**
     * Accessor method to retrieve the Putup associated with this graph (if any).
     * @return the putup associated with this graph or NULL if none exists.
     */
    public Putup getPutup() {
        Putup result = null;
        lock.lock();
        try {
            result = putup;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor method to set the Putup associated with this graph.
     * @param putup the putup to set
     */
    public void setPutup(Putup putup) {
        lock.lock();
        try {
            this.putup = putup;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor to retrieve the Price Time point at the close of business on the
     * previous trading day.
     * @return the AbstractGraphPoint encapsulating the price time data for this 
     * security at the close of trading on the previous trading day or NULL if
     * this data has not been loaded.
     */
    public AbstractGraphPoint getPrevDayClose() {
        AbstractGraphPoint result = null;
        lock.lock();
        try {
            result = prevDayClose;
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the Price Time point at the close of business on the
     * previous trading day.
     * @param prevDayClose the Price Time point representing the end of trading on
     * the previous trading day
     */
    public void setPrevDayClose(AbstractGraphPoint prevDayClose) {
        lock.lock();
        try {
            this.prevDayClose = prevDayClose;
        } finally {
            lock.unlock();
        }
    }

    private TreeSet<T> deepCopyResponses() {
        TreeSet<T> result = null;
        lock.lock();
        try {
            result = new TreeSet<T>(this.tsResponses);
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public void acquireObjectLock() {
        lock.lock();
    }

    @Override
    public void releaseObjectLock() {
        lock.unlock();
    }

    /**
     * Persists this graph and its data to the provided file as XML data
     * @param theFile - A File object to persist to OR NULL in which case the
     * default file name of GraphFor_<Stock Ticker>.xml will be used.
     * @return boolean True if the graph was successfully persisted, False otherwise
     */
    public boolean saveToXMLFile(File theFile) {
        boolean result = false;
        if (null != this.putup) {
            //If we do not have a file use the default
            if (null == theFile) {
                String strDefaultFile = "GraphFor_" + this.putup.getTickerCode();
                if (0 < this.size()) {
                    T last = this.last();
                    int dateAsNumber = last.getDateAsNumber();
                    strDefaultFile += ("_" + dateAsNumber);
                }
                strDefaultFile += ".xml";
                theFile = new File(strDefaultFile);
            }
            //If this file exists name it old and create new blank one
            if (theFile.exists()) {
                String strOld = "OLD_" + theFile.getName();
                File rename = new File(strOld);
                theFile.renameTo(rename);
            }
            FileOutputStream stream = null;
            lock.lock();
            try {
                theFile.createNewFile();
                stream = new FileOutputStream(theFile);
                XMLStreamWriter out;
                out = XMLOutputFactory.newInstance().createXMLStreamWriter(stream);
                result = this.writeAsXMLToStream(out);
            } catch (IOException ex) {
                Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
            } catch (XMLStreamException ex) {
                Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (null != stream) {
                    try {
                        stream.close();
                    } catch (IOException ex) {
                        Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                lock.unlock();
            }
        }

        return result;
    }

    /**
     * Parses the specified file for XML data to load into the graph.
     * @param path - String being the path to the file containing the XML data.
     * @return boolean True if the file was parsed and the data loaded, False otherwise
     */
    public boolean loadGraphFromFile(String path) {
        boolean result = false;
        if (null != path) {
            File fileToLoad = new File(path);
            if (fileToLoad.exists() && fileToLoad.canRead()) {
                FileInputStream stream = null;
                try {
                    stream = new FileInputStream(fileToLoad);
                    XMLEventReader in = XMLInputFactory.newInstance().createXMLEventReader(stream);
                    result = this.loadFromXMLStream(in, this);
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
                } catch (XMLStreamException ex) {
                    Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
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
        }
        return result;
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, BaseGraph<T> dest) {
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
                        //Putup Loading
                        if (strName.equalsIgnoreCase("Putup")) {
                            Putup newPutup = new Putup();
                            if (newPutup.loadFromXMLStream(reader, newPutup)) {
                                dest.setPutup(newPutup);
                            }
                        }
                        //PrevDayLoading
                        if (strName.equalsIgnoreCase("PrevDayClose")) {
                            AbstractGraphPoint aPoint = AbstractGraphPoint.loadPointFromStream(reader);
                            if (null != aPoint) {
                                dest.setPrevDayClose(aPoint);
                            }
                        }
                        //GraphPoints
                        if (strName.equalsIgnoreCase("GraphPoints")) {
                            //Get Point Count
                            QName name = new QName("PointCount");
                            Attribute objPCount = startElement.getAttributeByName(name);
                            String strPCount = objPCount.getValue();
                            int intPointCount = Integer.parseInt(strPCount);
                            for (int i = 0; i < intPointCount; i++) {
                                AbstractGraphPoint aPoint = AbstractGraphPoint.loadPointFromStream(reader);
                                if (null != aPoint) {
                                    dest.addDataPoint((T) aPoint);
                                }
                            }
                        }
                    }
                    if (event.isEndElement()) {
                        EndElement endElement = event.asEndElement();
                        String strName = endElement.getName().getLocalPart();
                        if (strName.equalsIgnoreCase("Graph")) {
                            result = true;
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        if (null != writer) {
            ArrayList<Boolean> writeResults = new ArrayList<Boolean>();
            try {
                writer.writeStartElement("Graph");
                writeResults.add(this.putup.writeAsXMLToStream(writer));
                if (null != this.prevDayClose) {
                    writer.writeStartElement("PrevDayClose");
                    writeResults.add(this.prevDayClose.writeAsXMLToStream(writer));
                    writer.writeEndElement();
                }
                writer.writeStartElement("GraphPoints");
                Integer count = this.tsResponses.size();
                writer.writeAttribute("PointCount", count.toString());
                for (AbstractGraphPoint currPoint : this.tsResponses) {
                    writeResults.add(currPoint.writeAsXMLToStream(writer));
                }
                writer.writeEndElement();
                writer.writeEndElement();
                boolean tempResult = true;
                for (Boolean currResult : writeResults) {
                    if (!currResult) {
                        tempResult = currResult;
                        break;
                    }
                }
                result = tempResult;
            } catch (XMLStreamException ex) {
                Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }

    /**
     * Accessor to retrieve the Y-Lines cached in this graph
     * @return the ylines as an ArrayList
     */
    public ArrayList<GraphLine> getYlines() {
        ArrayList<GraphLine> result = new ArrayList<GraphLine>();
        lock.lock();
        try {
            result.addAll(this.ylines);
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the Y-Lines cached in this graph
     * @param ylines the ylines to set as an ArrayList
     */
    public void setYlines(ArrayList<GraphLine> ylines) {
        if (null != ylines) {
            lock.lock();
            try {
                this.ylines = new ArrayList<GraphLine>(ylines);
                if (null != this.tradingDays) {
                    for (GraphLine currLine : this.ylines) {
                        currLine.setTradingDays(this.tradingDays);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Accessor to retrieve the current list of trading days. Each day is given
     * as an integer number in the format YYYYMMDD.
     * @return the tradingDays as a TreeSet of integer values
     */
    public TreeSet<Integer> getTradingDays() {
        TreeSet<Integer> result = new TreeSet<Integer>();
        lock.lock();
        try {
            result.addAll(this.tradingDays);
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to set the current list of trading days. Each day is given
     * as an integer number in the format YYYYMMDD.
     * @param tradingDays the tradingDays to set
     */
    public void setTradingDays(TreeSet<Integer> tradingDays) {
        if (null != tradingDays) {
            lock.lock();
            try {
                this.tradingDays = new TreeSet<Integer>(tradingDays);
                if (null != this.ylines && 0 < this.ylines.size()) {
                    for (GraphLine currLine : this.ylines) {
                        currLine.setTradingDays(tradingDays);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Scans the provided graph and identifies each day on the graph that has some data.
     * These days are considered to be days on which trading occurred on the stock market.
     * A list of these days is maintained in the format YYYYMMDD.
     * This list is used to identify days when the stock market was (or was NOT) trading.
     * For most of Bryn's trading rules the non-trading time does not count (for example
     * in computing gradients of graph lines across multiple days weekends, bank holidays
     * and unforseen closures (like Sept 11th) do not count.
     * @param graph - The graph containing several days trading data
     */
    public void setTradingDays(BaseGraph<AbstractGraphPoint> graph) {
        if (null != graph && 0 < graph.size()) {
            HashMap<Integer, AbstractGraphPoint> dayMap = new HashMap<Integer, AbstractGraphPoint>();
            //Generate map with keys for each day (yes only 1 point per day will survive the point is irrelevent)
            for (AbstractGraphPoint currPoint : graph) {
                dayMap.put(currPoint.getDateAsNumber(), currPoint);
            }
            //The keys of the hasp map represent the trading days
            Set<Integer> keySet = dayMap.keySet();
            TreeSet<Integer> newTradeDays = new TreeSet<Integer>(keySet);
            this.setTradingDays(newTradeDays);
        }
    }

    /**
     * Rather than using some other graph to identify days on which the stock market traded
     * you may want to compile the trading days list based on the data in this graph. This
     * method will scan this graph and compile the list.
     */
    public void setTradingDaysFromGraph() {
        if (null != this.tsResponses && 0 < this.tsResponses.size()) {
            HashMap<Integer, AbstractGraphPoint> dayMap = new HashMap<Integer, AbstractGraphPoint>();
            for (AbstractGraphPoint currPoint : this.tsResponses) {
                dayMap.put(currPoint.getDateAsNumber(), currPoint);
            }
            //The keys of the hasp map represent the trading days
            Set<Integer> keySet = dayMap.keySet();
            TreeSet<Integer> newTradeDays = new TreeSet<Integer>(keySet);
            this.setTradingDays(newTradeDays);
        }
    }

    /**
     * The provisional Y-Lines may be cached with the graph, this accessor will
     * provide a DEEP COPY of any cached Y-Lines.
     * @return An ArrayList of IGraphLine objects representing the cached Y-Lines for
     * this graph.
     */
    public ArrayList<IGraphLine> getCurrentYLines() {
        ArrayList<IGraphLine> result = new ArrayList<IGraphLine>();
        if (null != this.ylines && 0 < this.ylines.size()) {
            for (GraphLine currLine : this.ylines) {
                result.add(currLine.deepCopyLine());
            }
        }
        return result;
    }

    /**
     * Factory method that creates an empty graph object that remains associated
     * with this graphs Putup and includes the previous days close value, cached Y-Lines
     * and Trading days list.
     * @return An empty BaseGraph with all other settings copied from this graph except for
     * the point data.
     */
    public BaseGraph<T> replicateGraph() {
        BaseGraph<T> result = null;
        lock.lock();
        try {
            result = new BaseGraph<T>(this);
            //Add Putup, PrevDayClose, YLines and Trading days
            result.setPutup(this.getPutup());
            result.setPrevDayClose(this.getPrevDayClose());
            result.setYlines(this.getYlines());
            result.setTradingDays(this.getTradingDays());
            result.setGraphClosePrevDayData(this.getGraphClosePrevDayData());
            if (null != this.getYlineOneSecGraph()) {
                result.setYlineOneSecGraph(this.getYlineOneSecGraph());
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor to retrieve the graph reserved for previous day Y-Line data
     * @return the ylineOneSecGraph (A BaseGraph) object
     */
    public BaseGraph<AbstractGraphPoint> getYlineOneSecGraph() {
        BaseGraph<AbstractGraphPoint> result = null;
        if (null != this.ylineOneSecGraph) {
            result = this.ylineOneSecGraph.replicateGraph();
        }
        return result;
    }

    /**
     * Accessor to set the graph reserved for previous day Y-Line data
     * @param ylineOneSecGraph the ylineOneSecGraph to set
     */
    public void setYlineOneSecGraph(BaseGraph<AbstractGraphPoint> ylineOneSecGraph) {
        BaseGraph<AbstractGraphPoint> newData = new BaseGraph<AbstractGraphPoint>(ylineOneSecGraph);
        lock.lock();
        try {
            this.ylineOneSecGraph = newData;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor to retrieve the graph reserved for data from the previous trading day
     * @return the graphClosePrevDayData A BaseGraph object or NULL if no data has been loaded.
     */
    public BaseGraph<AbstractGraphPoint> getGraphClosePrevDayData() {
        BaseGraph<AbstractGraphPoint> result = null;
        if (null != this.graphClosePrevDayData) {
            result = this.graphClosePrevDayData.replicateGraph();
        }
        return result;
    }

    /**
     * Accessor to set the graph reserved for data from the previous trading day
     * @param graphClosePrevDayData the graphClosePrevDayData to set, a BaseGraph object
     */
    public void setGraphClosePrevDayData(BaseGraph<AbstractGraphPoint> graphClosePrevDayData) {
        lock.lock();
        try {
            this.graphClosePrevDayData = graphClosePrevDayData;
        } finally {
            lock.unlock();
        }
    }

    private boolean isPointInTradingHours(AbstractGraphPoint aPoint) {
        boolean result = false;
        if (null != aPoint) {
            Calendar exchOpeningCalendar = DTUtil.getExchOpeningCalendar(aPoint.getCalDate());
            Calendar exchClosingCalendar = DTUtil.getExchClosingCalendar(aPoint.getCalDate());
            long openTime = exchOpeningCalendar.getTimeInMillis();
            long closingTime = exchClosingCalendar.getTimeInMillis();
            long pointTime = aPoint.getTimestamp();
            if (pointTime >= openTime && pointTime <= closingTime) {
                result = true;
            }
        }
        return result;
    }

    private void removeInvalidPoints() {
        if (null != this.tsResponses) {
            for (T currPoint : this.tsResponses) {
                if (!this.isPointInTradingHours(currPoint)) {
                    this.remove(currPoint);
                }
            }
        }
    }

    /**
     * Accessor to retrieve the cache of previous days data
     * @return the previousGraphs, a HashMap that maps the date to which the 
     * BaseGraph relates to its graph. The date is the key expressed as an
     * integer number in the form YYYYMMDD
     */
    public HashMap<Integer, BaseGraph> getPreviousGraphs() {
        HashMap<Integer, BaseGraph> result = new HashMap<Integer, BaseGraph>();
        lock.lock();
        try {
            if (null == this.previousGraphs) {
                this.previousGraphs = new HashMap<Integer, BaseGraph>();
            }
            Set<Integer> keySet = this.previousGraphs.keySet();
            for (Integer currKey : keySet) {
                result.put(currKey, this.previousGraphs.get(currKey));
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Accessor method to add a graph to a HashMap of previous days graphs.
     * It is recommended that each graph use a key which is an integer representing
     * the year, month and day to which the graph relates (format = YYYYMMDD)
     * @param key - An integer number representing the HashMap key
     * @param prevGraph - A graph of data points to cache
     */
    public void addPreviousGraph(Integer key, BaseGraph<T> prevGraph) {
        lock.lock();
        try {
            if (null == this.previousGraphs) {
                this.previousGraphs = new HashMap<Integer, BaseGraph>();
            }
            if (this.previousGraphs.containsKey(key)) {
                this.previousGraphs.remove(key);
            }
            this.previousGraphs.put(key, prevGraph);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accessor to retrieve the current 'working' or scratch pad graph cached in this
     * graph.
     * @return the tempGraph. A BaseGraph object cached with this graph
     */
    public BaseGraph<T> getTempGraph() {
        return tempGraph;
    }

    /**
     * Accessor to set the current 'working' or scratch pad graph cached in this
     * graph.
     * @param tempGraph the tempGraph to set
     */
    public void setTempGraph(BaseGraph<T> tempGraph) {
        lock.lock();
        try {
            this.tempGraph = tempGraph;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates an empty graph based on this graph and caches it. This may be used as
     * a scratch pad to perform work that requires a graph but where we do not want
     * changes to affect the original graph
     */
    public void createTempGraph() {
        this.tempGraph = null;
        this.tempGraph = this.replicateGraph();
    }

    /**
     * Creates a graph based on this graph and caches it. This may be used as
     * a scratch pad to perform work that requires a graph but where we do not want
     * changes to affect the original graph
     * @param data - The data graph containing the data points to be included in the 'working' graph
     */
    public void createTempGraph(BaseGraph<T> data) {
        lock.lock();
        try {
            this.createTempGraph();
            this.tempGraph.clear();
            if (null != data && 0 < data.size()) {
                for (T currPoint : data) {
                    this.tempGraph.add(currPoint);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds all the data points contained in this graph to the current 'working' graph 
     * creating that graph if needed.
     */
    public void mergeCurrAndTempGraph() {
        if (null == this.tempGraph) {
            this.createTempGraph();
        }
        lock.lock();
        try {
            this.tempGraph.addAll(this);
        } finally {
            lock.unlock();
        }
    }

    /**
     * The purpose of this method is to remove all points between the first and
     * last point of the provided tree set from the current graph and replace
     * them with the points in the tree set.
     * It's use is to replace the existing set of data in the graph with a new set.
     * For example it may be useful to replace real time market data with the
     * 'final' historic data points on a regular basis.
     * @param histData A TreeSet of data points (nominally historic data points)
     */
    public void storeHistoricData(TreeSet<T> histData) {
        if (null != histData && 0 < histData.size()) {
            T first = histData.first();
            T last = histData.last();
            lock.lock();
            try {
                NavigableSet<T> subSet = this.subSet(first, true, last, true);
                this.removeAll(subSet);
                this.addAll(histData);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Get previous days graph. This function will return the previous days 1
     * sec data graph or null if it is not avaliable. NB: This data must have been loaded
     * and stored this method will not load it if it is not available.
     * @return A BaseGraph object containing the data for this stock for the previous
     * trading day or NULL if it has not been loaded
     */
    public BaseGraph<T> getPrevDayGraph() {
        BaseGraph<T> result = null;
        lock.lock();
        try {
            //Check to see cache exists
            if (null != this.previousGraphs && 0 < this.previousGraphs.size()) {
                //Sort the keys to find the most recent
                Set<Integer> keySet = this.previousGraphs.keySet();
                Integer highestKey = null;
                for (Integer currKey : keySet) {
                    if (null == highestKey) {
                        highestKey = currKey;
                    } else {
                        if (currKey > highestKey) {
                            highestKey = currKey;
                        }
                    }
                }
                if (null != highestKey) {
                    BaseGraph<T> graph = this.previousGraphs.get(highestKey);
                    result = graph;
                }
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * Working from the lowest data points WAP to the start of the graph (earliest data point)
     * identifies all the local highs higher than a previous local high on the graph. 
     * @return A TreeSet containing each 'higher high point'.
     */
    public TreeSet<T> getHigherHighsFromLatestLowOfDay() {
        TreeSet<T> result = new TreeSet<T>();
        if (0 < this.size()) {
            //Create subset from latest low of day to start of graph
            NavigableSet<T> subSet;
            lock.lock();
            try {
                T lowestPointSoFar = this.getLowestPointSoFar();
                subSet = this.subSet(this.first(), true, lowestPointSoFar, true);
            } finally {
                lock.unlock();
            }
            if (null != subSet && 0 < subSet.size()) {
                T highSoFar = null;
                T currPoint = null;
                Iterator<T> descIter = subSet.descendingIterator();
                while (descIter.hasNext()) {
                    currPoint = descIter.next();
                    if (null == highSoFar) {
                        highSoFar = currPoint;
                        result.add(currPoint);
                    } else {
                        if (currPoint.getWAP() > highSoFar.getWAP()) {
                            highSoFar = currPoint;
                            result.add(currPoint);
                        }
                    }
                }
            }
        }
        return result;
    }
}
