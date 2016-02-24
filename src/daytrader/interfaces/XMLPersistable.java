/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.interfaces;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * This interface defines the methods required to persist an object to a file 
 * as XML
 * @param <T> A Java type which is implementing this interface
 * @author Roy
 */
public interface XMLPersistable<T> {
    
    /**
     * Loads an object from an XML data source
     * @param reader - An XMLEventReader attached to the data source.
     * @param dest - The object to store the data read in from the data source (may be null to store
     * data into the calling object)
     * @return boolean True if the data was read and stored successfully, False otherwise.
     */
    boolean loadFromXMLStream(XMLEventReader reader, T dest);
    
    /**
     * Serialise this object to XML.
     * @param writer - An XMLStreamWriter to which the objects XML code will be written
     * @return boolean True if the XML was correctly generated and written to the provided 
     * data stream, False otherwise.
     */
    boolean writeAsXMLToStream(XMLStreamWriter writer);
}
