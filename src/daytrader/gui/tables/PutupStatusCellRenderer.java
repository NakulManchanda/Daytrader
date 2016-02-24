/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.RealTimeRunManager;
import daytrader.gui.PutupStatusEntry;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author Roy
 */
public class PutupStatusCellRenderer implements TableCellRenderer{
    
    private PutupStatusEntry renderer;
    
    public PutupStatusCellRenderer(){
        this.renderer = new PutupStatusEntry();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component result = null;
        if(null != value){
            if(value instanceof RealTimeRunManager){
                RealTimeRunManager manager = (RealTimeRunManager)value;
                this.renderer.setData(manager);
                result = this.renderer;
            }
        }
        return result;
    }
    
}
