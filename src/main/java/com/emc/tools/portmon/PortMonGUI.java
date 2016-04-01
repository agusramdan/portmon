/**
 * A simple Port monitor.
 */
package com.emc.tools.portmon;

import java.awt.Component;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.EventListener;
import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CellEditorListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

/**
 * A simple Port monitor.
 *
 * @author chitas
 */
public class PortMonGUI extends javax.swing.JFrame {

    private static final Logger logger = Logger.getLogger(PortMonGUI.class.getName());

    private static final Image ICON = new ImageIcon(PortMonGUI.class.getResource("/icons/portmon.png")).getImage();
    private static final ImageIcon ICON_INFO = new ImageIcon(PortMonGUI.class.getResource("/icons/info.png"));
    private static final ImageIcon ICON_TERMINATE = new ImageIcon(PortMonGUI.class.getResource("/icons/terminate.png"));

    private static final Font MONOSPACE_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private final BlockingQueue<String> refreshQueue = new LinkedBlockingDeque<>();

    private final Timer autoRefreshTimer;

    static class TableButton extends JButton implements TableCellRenderer, TableCellEditor {

        private int selectedRow;
        private int selectedColumn;
        List<TableButtonListener> listener;

        public TableButton(ImageIcon icon) {
            super("", icon);
            setBorderPainted(false);
            setFocusPainted(false);
            listener = new LinkedList<>();
            addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    for (TableButtonListener l : listener) {
                        l.tableButtonClicked(selectedRow, selectedColumn);
                    }
                }
            });
        }

        public void addTableButtonListener(TableButtonListener l) {
            listener.add(l);
        }

        public void removeTableButtonListener(TableButtonListener l) {
            listener.remove(l);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            return this;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table,
                Object value, boolean isSelected, int row, int col) {
            selectedRow = row;
            selectedColumn = col;
            return this;
        }

        @Override
        public void addCellEditorListener(CellEditorListener arg0) {
        }

        @Override
        public void cancelCellEditing() {
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }

        @Override
        public boolean isCellEditable(EventObject arg0) {
            return true;
        }

        @Override
        public void removeCellEditorListener(CellEditorListener arg0) {
        }

        @Override
        public boolean shouldSelectCell(EventObject arg0) {
            return true;
        }

        @Override
        public boolean stopCellEditing() {
            return true;
        }
    }

    static interface TableButtonListener extends EventListener {

        public void tableButtonClicked(int row, int col);
    }

    private static class PortsTableModel extends AbstractTableModel {

        private List<PortMon.Port> ports;

        PortsTableModel() {
            ports = new LinkedList<>();
        }

        public void setPorts(List<PortMon.Port> ports) {
            this.ports = ports;

            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return ports.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return "Host";
                case 1:
                    return "Port";
                case 2:
                    return "PID";
                case 3:
                    return "State";
                case 4:
                    return "Info";
                case 5:
                    return "Actions";
            }
            return null;
        }

        @Override
        public Class getColumnClass(int column) {
            switch (column) {
                case 0:
                    return String.class;
                case 1:
                    return Integer.class;
                case 2:
                    return String.class;
                case 3:
                    return String.class;
                case 4:
                    return PortMon.Port.class;
                case 5:
                    return PortMon.Port.class;
            }
            return Object.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return  columnIndex == 4 ||  columnIndex == 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return ports.get(rowIndex).localHost;
                case 1:
                    return Integer.valueOf(ports.get(rowIndex).localPort);
                case 2:
                    return ports.get(rowIndex).pid + "  ";
                case 3:
                    return ports.get(rowIndex).state;
                case 4:
                    return ports.get(rowIndex);
                case 5:
                    return ports.get(rowIndex);
            }
            return null;
        }

    }

    private PortsTableModel portsTableModel;

    /**
     * Creates new form PortMonGUI
     */
    public PortMonGUI() {
        portsTableModel = new PortsTableModel();
        initComponents();
        setIconImage(ICON);

        int autoRefreshDelaySeconds = (Integer) autoRefreshSecondsSpinner.getModel().getValue();

        autoRefreshTimer = new Timer(autoRefreshDelaySeconds * 1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });

        TableButton infoActionButton = new TableButton(ICON_INFO);
        infoActionButton.addTableButtonListener(new TableButtonListener() {
            @Override
            public void tableButtonClicked(int row, int col) {
                Object value = portsTableModel.getValueAt(row, col);
                if (value instanceof PortMon.Port) {
                    PortMon.Port port = (PortMon.Port) value;
                    JTextArea info = new JTextArea(PortMon.processInfo(port.pid).substring(1)
                            ,12
                            ,40);
                    info.setFont(MONOSPACE_FONT);
                    JOptionPane.showMessageDialog(PortMonGUI.this,
                            info,
                            "Portmon - Process Info",
                            JOptionPane.PLAIN_MESSAGE);
                }
            }
        });
        infoActionButton.setToolTipText("Show process info");

        TableButton killActionButton = new TableButton(ICON_TERMINATE);
        killActionButton.addTableButtonListener(new TableButtonListener() {
            @Override
            public void tableButtonClicked(int row, int col) {
                Object value = portsTableModel.getValueAt(row, col);
                if (value instanceof PortMon.Port) {
                    PortMon.Port port = (PortMon.Port) value;
                    int answer = JOptionPane.showConfirmDialog(PortMonGUI.this,
                            "Kill process : "
                                    + port.pid
                                    + " (owner of port : "
                                    + port.localPort
                                    + ") ?",
                            "Portmon - Kill Port Owner",
                            JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.YES_OPTION) {
                        PortMon.killProcess(port.pid);
                        portsTableModel.setPorts(Collections.EMPTY_LIST);
                        Timer timer = new Timer(500, new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                refresh();
                            }
                        });
                        timer.setRepeats(false);
                        timer.start();
                    }
                }
            }
        });
        killActionButton.setToolTipText("Kill process owning the port.");

        DefaultTableCellRenderer rightAlignedTableCellRenderer = new DefaultTableCellRenderer();
        rightAlignedTableCellRenderer.setHorizontalAlignment(SwingConstants.TRAILING);

        TableColumn tableColumn;
        final TableColumnModel columnModel = portsTable.getColumnModel();

        tableColumn = columnModel.getColumn(1);
        tableColumn.setWidth(80);
        tableColumn.setMaxWidth(80);
        tableColumn.setCellRenderer(rightAlignedTableCellRenderer);

        tableColumn = columnModel.getColumn(2);
        tableColumn.setWidth(100);
        tableColumn.setMaxWidth(100);
        tableColumn.setCellRenderer(rightAlignedTableCellRenderer);

        tableColumn = columnModel.getColumn(3);
        tableColumn.setWidth(160);
        tableColumn.setMaxWidth(160);

        tableColumn = columnModel.getColumn(4);
        tableColumn.setWidth(20);
        tableColumn.setMaxWidth(20);
        tableColumn.setCellEditor(infoActionButton);
        tableColumn.setCellRenderer(infoActionButton);

        tableColumn = columnModel.getColumn(5);
        tableColumn.setWidth(20);
        tableColumn.setMaxWidth(20);
        tableColumn.setCellEditor(killActionButton);
        tableColumn.setCellRenderer(killActionButton);

        portsTable.setRowSorter(new TableRowSorter(portsTableModel));
        refresh();
        portsTable.getRowSorter().toggleSortOrder(1);

        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    while (true) {
                        final String ports = refreshQueue.take();
                        // Coalesce
                        int i = 0;
                        String nextPorts = refreshQueue.peek();
                        while (nextPorts != null && ports.equals(nextPorts)) {
                            refreshQueue.remove();
                            i++;
                            nextPorts = refreshQueue.peek();
                        }
                        if (i > 0) {
                            logger.log(Level.FINE, "Coalesced:" + (i));
                        }
                        refresh(ports);
                        // 5 seconds gap between refresh
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }

        }, "Refresh Queue").start();
    }

    private void refresh() {
        try {
            refreshQueue.put(portsComboBox.getSelectedItem().toString().trim());
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    private void refresh(String ports) {
        ports = ports.trim();
        refreshImpl(ports.split(","));
    }

    private void refreshImpl(String... ports) {
        if (refreshing.compareAndSet(false, true)) {
            try {
                final List<PortMon.Port> portObjects = PortMon.getPorts(listeningOnly.isSelected(), ports);
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        // Update GUI
                        portsTableModel.setPorts(portObjects);
                    }
                });
            } finally {
                refreshing.set(false);
            }
        }
    }

    private void startStopAutoRefresh() {
        if (autoRefreshCheckBox.isSelected()) {
            refreshButton.setEnabled(false);
            refresh();
            autoRefreshTimer.start();
        } else {
            autoRefreshTimer.stop();
            refreshButton.setEnabled(true);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        portsLabel = new javax.swing.JLabel();
        portsComboBox = new javax.swing.JComboBox();
        listeningOnly = new javax.swing.JCheckBox();
        refreshButton = new javax.swing.JButton();
        autoRefreshCheckBox = new javax.swing.JCheckBox();
        autoRefreshSecondsSpinner = new javax.swing.JSpinner();
        secondsLabel = new javax.swing.JLabel();
        portsScrollpane = new javax.swing.JScrollPane();
        portsTable = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Portmon");
        setName("portsMonFrame"); // NOI18N
        setPreferredSize(new java.awt.Dimension(600, 400));

        portsLabel.setText("Ports:");

        portsComboBox.setEditable(true);
        portsComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { " ", "2910,8080,8765" }));
        portsComboBox.setSelectedItem(" ");
        portsComboBox.setToolTipText("Enter comma separated list of port numbers");
        portsComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                portsComboBoxActionPerformed(evt);
            }
        });

        listeningOnly.setSelected(true);
        listeningOnly.setText("LISTENING ONLY");
        listeningOnly.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                listeningOnlyActionPerformed(evt);
            }
        });

        refreshButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/refresh.png"))); // NOI18N
        refreshButton.setToolTipText("Refresh");
        refreshButton.setMargin(new java.awt.Insets(0, 2, 0, 2));
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });

        autoRefreshCheckBox.setText("Auto refresh every");
        autoRefreshCheckBox.setToolTipText("");
        autoRefreshCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoRefreshCheckBoxActionPerformed(evt);
            }
        });

        autoRefreshSecondsSpinner.setModel(new javax.swing.SpinnerNumberModel(5, 5, 20, 1));

        secondsLabel.setText("seconds");

        portsTable.setModel(this.portsTableModel);
        portsTable.setRowHeight(20);
        portsScrollpane.setViewportView(portsTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(portsScrollpane, javax.swing.GroupLayout.DEFAULT_SIZE, 441, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(portsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(autoRefreshCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(autoRefreshSecondsSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(12, 12, 12)
                                .addComponent(secondsLabel))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(portsComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(listeningOnly)))
                        .addGap(6, 6, 6)
                        .addComponent(refreshButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(portsLabel)
                        .addComponent(portsComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(listeningOnly))
                    .addComponent(refreshButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(autoRefreshSecondsSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(secondsLabel)
                    .addComponent(autoRefreshCheckBox))
                .addGap(10, 10, 10)
                .addComponent(portsScrollpane, javax.swing.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        refresh();
    }//GEN-LAST:event_refreshButtonActionPerformed

    private void autoRefreshCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoRefreshCheckBoxActionPerformed
        startStopAutoRefresh();
    }//GEN-LAST:event_autoRefreshCheckBoxActionPerformed

    private void portsComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_portsComboBoxActionPerformed
        refresh();
    }//GEN-LAST:event_portsComboBoxActionPerformed

    private void listeningOnlyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_listeningOnlyActionPerformed
        refresh();
    }//GEN-LAST:event_listeningOnlyActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>

        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new PortMonGUI().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox autoRefreshCheckBox;
    private javax.swing.JSpinner autoRefreshSecondsSpinner;
    private javax.swing.JCheckBox listeningOnly;
    private javax.swing.JComboBox portsComboBox;
    private javax.swing.JLabel portsLabel;
    private javax.swing.JScrollPane portsScrollpane;
    private javax.swing.JTable portsTable;
    private javax.swing.JButton refreshButton;
    private javax.swing.JLabel secondsLabel;
    // End of variables declaration//GEN-END:variables
}
