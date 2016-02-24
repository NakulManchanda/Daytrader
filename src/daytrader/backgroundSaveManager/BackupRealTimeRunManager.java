/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.backgroundSaveManager;

import daytrader.datamodel.BaseGraph;
import daytrader.datamodel.RealTimeRunManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

/**
 * This callable class accepts a RealTimeRunManager to backup and performs the
 * backup into the specified directory.
 *
 * @author Roy
 */
public class BackupRealTimeRunManager implements Callable<Void> {

    private RealTimeRunManager manager;
    private File recoveryFolder;
    private File targetFile;

    /**
     * Constructor that accepts a running RealTimeRunManager to backup and a File object
     * representing the directory to store the data into
     * @param newData - The RealTimeRunManager containing the data to be backed up
     * @param newRecoveryFolder - File object representing the directory in which the 
     * backup XML file will be saved.
     */
    public BackupRealTimeRunManager(RealTimeRunManager newData, File newRecoveryFolder) {
        this.manager = newData;
        this.recoveryFolder = newRecoveryFolder;
        if (this.recoveryFolder.exists() && this.recoveryFolder.isDirectory()) {
            String pathRF = this.recoveryFolder.getPath();
            String pathSaveFile = pathRF + File.separator + this.manager.getMyPutup().getTickerCode() + ".xml";
            this.targetFile = new File(pathSaveFile);
        }
    }

    @Override
    public Void call() throws Exception {
        if (this.isValid()) {
            if (this.targetFile.exists()) {
                this.targetFile.delete();
            }
            FileOutputStream stream = null;
            try {
                this.targetFile.createNewFile();
                stream = new FileOutputStream(this.targetFile);
                XMLStreamWriter out;
                out = XMLOutputFactory.newInstance().createXMLStreamWriter(stream);
                this.manager.writeAsXMLToStream(out);
            } finally {
                if (null != stream) {
                    try {
                        stream.close();
                    } catch (IOException ex) {
                        Logger.getLogger(BaseGraph.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
        return null;
    }

    private boolean isValid() {
        boolean result = false;
        if (null != this.manager && null != this.recoveryFolder && null != this.targetFile) {
            result = true;
        }
        return result;
    }
}
