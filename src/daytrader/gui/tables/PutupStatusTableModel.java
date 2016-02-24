/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.RealTimeRunManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Roy
 */
public class PutupStatusTableModel extends AbstractTableModel {

    private List<RealTimeRunManager> entries;
    private Thread monitor;

    public static PutupStatusTableModel createModel() {
        PutupStatusTableModel result = new PutupStatusTableModel();
        result.startup();
        return result;
    }

    private PutupStatusTableModel() {
        
    }
    
    private void startup(){
        this.monitor = new Thread(new Refresher());
        this.monitor.start();
    }

    @Override
    public int getRowCount() {
        int result = 0;
        if (null != entries && 0 < entries.size()) {
            result = entries.size();
        }
        return result;
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Object result = null;
        if (null != this.entries) {
            result = this.entries.get(rowIndex);
        }
        return result;
    }

    public void addPutupEntry(RealTimeRunManager newItem) {
        if (null == this.entries) {
            this.entries = Collections.synchronizedList(new ArrayList<RealTimeRunManager>());
        }
        this.entries.add(newItem);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return RealTimeRunManager.class;
    }

    @Override
    public String getColumnName(int column) {
        return "Running Putups";
    }
    
    private class Refresher implements Runnable{
        
        private long SLEEP_TIME = 1000;

        @Override
        public void run() {
            while(!Thread.interrupted()){
                fireTableDataChanged();
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException ex) {
                    Logger.getLogger(PutupStatusTableModel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
    }
}
