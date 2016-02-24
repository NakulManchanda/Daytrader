/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.datamodel;

import java.io.File;
import java.io.FileFilter;

/**
 * An implementation of the FileFilter interface to select XML files only
 * @author Roy
 */
public class XmlFileFilter implements FileFilter {

    @Override
    public boolean accept(File pathname) {
        boolean result = false;
        if(null != pathname){
            if(pathname.getName().endsWith(".xml") || pathname.getName().endsWith(".XML")){
                result = true;
            }
        }
        return result;
    }
    
}
