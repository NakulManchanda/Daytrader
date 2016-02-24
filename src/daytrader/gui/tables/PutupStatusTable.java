/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.RealTimeRunManager;
import daytrader.gui.PutupStatusEntry;
import javax.swing.JTable;

/**
 * This table is used to display the status of each running put up
 * @author Roy
 */
public class PutupStatusTable extends JTable {
    
    private PutupStatusCellRenderer renderer;
    
    public PutupStatusTable(){
        this.renderer = new PutupStatusCellRenderer();
        this.setDefaultRenderer(RealTimeRunManager.class, this.renderer);
        PutupStatusEntry panel = new PutupStatusEntry();
        this.setRowHeight(panel.getPreferredSize().height);
    }
}
