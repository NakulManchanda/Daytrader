/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package daytrader.gui.tables;

import daytrader.datamodel.PrimeDataModel;
import daytrader.datamodel.Putup;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Roy
 */
public class PutupTableModelTwo extends AbstractTableModel {

    private PrimeDataModel data;

    public PutupTableModelTwo() {
        data = new PrimeDataModel();
    }

    @Override
    public int getRowCount() {
        return this.data.getAllPutupsList().size();
    }

    @Override
    public int getColumnCount() {
        return 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Object result = null;
        try {
            result = this.data.getAllPutupsList().get(rowIndex);
        } finally {
            return result;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return Putup.class;
    }

    @Override
    public String getColumnName(int column) {
        return "Putups";
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        boolean result = false;
        if (!this.data.isLocked()) {
            result = true;
        }
        return result;
    }

    public void addPutup(Putup newPutup) {
        if (null != newPutup) {
            this.data.addPutup(newPutup);
            fireTableStructureChanged();
        }
    }

    public void removePutup(Putup oldPutup) {
        if (null != oldPutup) {
            this.data.removePutup(oldPutup);
            fireTableStructureChanged();
        }
    }

    /**
     * @return the data
     */
    public PrimeDataModel getData() {
        return data;
    }
}
