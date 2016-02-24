/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.historicRequestSystem;

import daytrader.historicRequestSystem.exceptions.AllProcessingQueuesFullException;
import daytrader.interfaces.Lockable;
import daytrader.interfaces.XMLPersistable;
import daytrader.interfaces.observerpattern.IObserver;
import daytrader.interfaces.observerpattern.ISubject;
import daytrader.interfaces.observerpattern.ISubjectDelegate;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
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
 * This class encapsulates an iterable list of the avaliable accounts as defined in an XML
 * File (see the class constant TWS_ACC_FILE). This class can also be 'the subject of
 * an observation' by another class through the ISubject interface and this is used to 
 * update the GUI.
 *
 * @author Roy
 */
public class TWSAccountList implements XMLPersistable<TWSAccountList>, Iterable<TWSAccount>, ISubject, Lockable {

    private ArrayList<TWSAccount> accList;
    private ISubjectDelegate iSubjectDelegate;
    /**
     * The XML File that stores the details of accounts to be used by the system
     */
    public static final String TWS_ACC_FILE = "TWSAcc.xml";

    /**
     * Lock for thread safety
     */
    private ReentrantLock lock;

    /**
     * Default constructor creates an empty account list and initialises the attributes
     * needed for the observer pattern and thread safety.
     */
    public TWSAccountList() {
        this.accList = new ArrayList<TWSAccount>();
        this.iSubjectDelegate = new ISubjectDelegate();
        this.lock = new ReentrantLock();
    }

    /**
     * This static factory method builds a TWSAccount List object initialised with 
     * the account data contained in the XML file defined by the TWS_ACC_FILE class
     * constant
     * @return A TWSAccountList initialised with the accounts in the provided
     * XML file or NULL if the file could not be found / parsed.
     */
    public static TWSAccountList loadAccountsList() {
        TWSAccountList result = null;
        File sourceData = new File(TWS_ACC_FILE);
        if (sourceData.exists() && sourceData.canRead()) {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(sourceData);
                XMLEventReader in = XMLInputFactory.newInstance().createXMLEventReader(stream);
                result = new TWSAccountList();
                result.loadFromXMLStream(in, result);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(TWSAccountList.class.getName()).log(Level.SEVERE, null, ex);
            } catch (XMLStreamException ex) {
                Logger.getLogger(TWSAccountList.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if(null != stream){
                    try {
                        stream.close();
                    } catch (IOException ex) {
                        Logger.getLogger(TWSAccountList.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Persists the currently loaded account data to the XML file specified bt the 
     * TWS_ACC_FILE class constant.
     * @return boolean True if the current account data was successfully saved to the
     * file, False otherwise.
     */
    public boolean saveAccountsList() {
        boolean result = false;
        File sourceData = new File(TWS_ACC_FILE);
        if (sourceData.exists()) {
            String strOldName = "OLD_" + TWS_ACC_FILE;
            File oldFile = new File(strOldName);
            sourceData.renameTo(oldFile);
            sourceData.delete();
        }
        FileOutputStream stream = null;
        try {
            sourceData.createNewFile();
            if (sourceData.exists() && sourceData.canWrite()) {
                stream = new FileOutputStream(sourceData);
                XMLStreamWriter out;
                out = XMLOutputFactory.newInstance().createXMLStreamWriter(stream);
                result = this.writeAsXMLToStream(out);
            }
        } catch (IOException ex) {
            Logger.getLogger(TWSAccountList.class.getName()).log(Level.SEVERE, null, ex);
        } catch (XMLStreamException ex) {
            Logger.getLogger(TWSAccountList.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    /**
     * Accessor method to add a new TWSAccount to the list of accounts
     * @param newAcc - The new TWSAccound to add to the list.
     */
    public void addAccount(TWSAccount newAcc) {
        lock.lock();
        try {
            this.accList.add(newAcc);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean loadFromXMLStream(XMLEventReader reader, TWSAccountList dest) {
        boolean result = false;
        if (null != reader) {
            boolean abort = false;
            if (null == dest) {
                dest = this;
            }
            while (!abort) {
                try {
                    XMLEvent nextEvent = reader.nextEvent();
                    if (nextEvent.isStartElement() || nextEvent.isEndElement()) {
                        if (nextEvent.isStartElement()) {
                            StartElement xmlStartEvent = nextEvent.asStartElement();
                            String name = xmlStartEvent.getName().getLocalPart();
                            if (name.equals("TWSAccountList")) {
                                //Get number of items on the list
                                Attribute noOfEntries = xmlStartEvent.getAttributeByName(new QName("NoOfAcc"));
                                String value = noOfEntries.getValue();
                                for (int i = 0; i < Integer.parseInt(value); i++) {
                                    TWSAccount newAcc = this.retrieveAccount(reader);
                                    dest.addAccount(newAcc);
                                }
                            }
                        }
                        if (nextEvent.isEndElement()) {
                            EndElement xmlEndEvent = nextEvent.asEndElement();
                            String name = xmlEndEvent.getName().getLocalPart();
                            if (name.equals("TWSAccountList")) {
                                result = true;
                                abort = true;
                            }
                        }
                    }
                } catch (XMLStreamException ex) {
                    Logger.getLogger(TWSAccountList.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

    private TWSAccount retrieveAccount(XMLEventReader in) throws XMLStreamException {
        TWSAccount result = null;
        boolean blnSpinOn = true;
        do {
            XMLEvent nextEvent = in.peek();
            if (nextEvent.isStartElement()) {
                StartElement xmlStart = nextEvent.asStartElement();
                String name = xmlStart.getName().getLocalPart();
                if (name.equals("TWSAccount")) {
                    blnSpinOn = false;
                }
            }
            if (blnSpinOn) {
                in.nextEvent();
            }
        } while (blnSpinOn);
        XMLEvent nextEvent = in.peek();
        if (nextEvent.isStartElement()) {
            if (nextEvent.asStartElement().getName().getLocalPart().equals("TWSAccount")) {
                TWSAccount newItem = new TWSAccount();
                if (newItem.loadFromXMLStream(in, newItem)) {
                    result = newItem;
                }
            }
        }
        return result;
    }

    @Override
    public boolean writeAsXMLToStream(XMLStreamWriter writer) {
        boolean result = false;
        if (null != writer) {
            lock.lock();
            try {
                writer.writeStartElement("TWSAccountList");
                Integer size = this.accList.size();
                writer.writeAttribute("NoOfAcc", size.toString());

                for (TWSAccount currAcc : this.accList) {
                    currAcc.writeAsXMLToStream(writer);
                }

                writer.writeEndElement();
                result = true;
            } catch (XMLStreamException ex) {
                Logger.getLogger(TWSAccount.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Examines each TWSAccount held on this list and retrieves the one with the
     * highest number of free request slots remaining in its request queue.
     * @return The TWSAccount with the largest amount of free space for additional
     * data requests.
     * @throws AllProcessingQueuesFullException - Thrown if all TWSAccounts on this
     * list report that they have no available space for further requests.
     */
    public TWSAccount getLeastUsedAcc() throws AllProcessingQueuesFullException {
        TWSAccount result = null;
        TWSAccount tempAcc = null;
        int tempAccRemainingReq = 0;
        lock.lock();
        try {
            for (TWSAccount currAcc : this.accList) {
                int reqRemaining = currAcc.getRemainingRequests();
                if (null != tempAcc) {
                    if (tempAccRemainingReq < reqRemaining) {
                        tempAcc = currAcc;
                        tempAccRemainingReq = reqRemaining;
                    }
                } else {
                    if (0 < reqRemaining) {
                        tempAcc = currAcc;
                        tempAccRemainingReq = reqRemaining;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        if (null != tempAcc) {
            result = tempAcc;
        } else {
            throw new AllProcessingQueuesFullException();
        }
        return result;
    }

    /**
     * Retrieves the size (number of TWSAccounts in) this list
     * @return integer being the number of TWSAccounts in use by the application
     */
    public int size() {
        int result = 0;
        lock.lock();
        try {
            result = this.accList.size();
        } finally {
            lock.unlock();
        }
        return result;
    }

    /**
     * The first account included in the list is considered to be a general account
     * that can be used to make non-Historic data requests (ie those that do not 
     * count towards the limit of requests made in the last 10 minutes). This method
     * retrieves that account.
     * @return the first TWSAccount in this list
     */
    public TWSAccount getGeneralAcc() {
        TWSAccount result = null;
        lock.lock();
        try {
            if (this.size() > 0) {
                result = this.accList.get(0);
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public Iterator<TWSAccount> iterator() {
        return this.accList.iterator();
    }

    @Override
    public boolean registerObserver(IObserver newObserver) {
        return this.iSubjectDelegate.registerObserver(newObserver);
    }

    @Override
    public boolean removeObserver(IObserver oldObserver) {
        return this.iSubjectDelegate.removeObserver(oldObserver);
    }

    @Override
    public void notifyObservers() {
        this.iSubjectDelegate.notifyObservers();
    }

    @Override
    public void acquireObjectLock() {
        lock.lock();
    }

    @Override
    public void releaseObjectLock() {
        lock.unlock();
    }
}
