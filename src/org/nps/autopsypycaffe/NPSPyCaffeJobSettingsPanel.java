/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nps.autopsypycaffe;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

import java.util.ArrayList;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.JSeparator;
import javax.swing.border.Border;
/**
 *
 * @author tomb
 */
public class NPSPyCaffeJobSettingsPanel extends IngestModuleIngestJobSettingsPanel {
    
    private Properties props;
    private ArrayList<String> detectorsAvail = new ArrayList<String>();
    
    /**
     * Creates new form SampleIngestModuleIngestJobSettings
     */
    public NPSPyCaffeJobSettingsPanel(IngestModuleIngestJobSettings settings, Properties props) {
        this.props = props;
        if (props.containsKey("main.detectors")){
            String[] detectors = props.getProperty("main.detectors").split(",");
            // Strip spaces
           for (String next : detectors){
               detectorsAvail.add(next.trim());
           }
        }
        initComponents();
        customizeComponents((NPSPyCaffeIngestJobSettings)settings);
    }
 
    private void customizeComponents(NPSPyCaffeIngestJobSettings settings) {
        String curDet = settings.getDetector();
        for (javax.swing.JRadioButton next : detectorRadioButtons){
            if (next.getActionCommand().equals(curDet)){
                next.setSelected(true);
                break;
            }   
        }
        
    }
 
    /**
     * Gets the ingest job settings for an ingest module.
     *
     * @return The ingest settings.
     */
    @Override
    public IngestModuleIngestJobSettings getSettings() {
        String curDet = "";
        for (javax.swing.JRadioButton next : detectorRadioButtons){
            if (next.isSelected()) {
                curDet = next.getActionCommand();
                break;
            }
        }
        return new NPSPyCaffeIngestJobSettings(curDet);
    }
 
    /*
    public void actionPerformed(ActionEvent e){
        
    }
    */
    
    @SuppressWarnings("unchecked")
    private void initComponents() {
        detectorRadioButtons = new ArrayList<javax.swing.JRadioButton>();
        BoxLayout layout = new BoxLayout(this, javax.swing.BoxLayout.Y_AXIS);
        this.setLayout(layout);
        ButtonGroup group = new ButtonGroup();
         
        for (String next : detectorsAvail){
            JPanel panelH = new JPanel();
            Border border = BorderFactory.createEtchedBorder();
            BoxLayout layoutH = new BoxLayout(panelH, BoxLayout.X_AXIS);
            panelH.setLayout(layoutH);
            panelH.setBorder(border);
            JPanel panelRadio = new JPanel();
            BoxLayout layoutRadio = new BoxLayout(panelRadio, BoxLayout.Y_AXIS);
            panelRadio.setLayout(layoutRadio);
            JRadioButton button = new JRadioButton(next);
            button.setVerticalAlignment(SwingConstants.CENTER);
            button.setActionCommand(next);
            group.add(button);
            panelRadio.setAlignmentY(0.5f);
            panelRadio.add(button);
            panelH.add(panelRadio);
            JPanel panelV = new JPanel();
            BoxLayout layoutV = new BoxLayout(panelV, BoxLayout.Y_AXIS);
            panelV.setLayout(layoutV);
            detectorRadioButtons.add(button);
            String key = next + ".description";
            if (props.containsKey(key)){
                JLabel l1 = new JLabel("Description:");
                l1.setHorizontalAlignment(SwingConstants.LEFT);
                panelV.add(l1);
                JLabel l2 = new JLabel(props.getProperty(key));
                l2.setHorizontalAlignment(SwingConstants.LEFT);
                panelV.add(l2);
            }
            key = next + ".classes";
            if (props.containsKey(key)){
                JLabel l1 = new JLabel("Classes:");
                l1.setHorizontalAlignment(SwingConstants.LEFT);
                panelV.add(l1);
                String classes = props.getProperty(key);
                StringBuilder temp = new StringBuilder(classes);
                int pos = 0;
                while ((pos = temp.indexOf(", ", pos + 30)) >= 0){
                    temp.setCharAt(pos+1, '\n');
                }
                JTextArea classLabel = new JTextArea(temp.toString());
                classLabel.setAlignmentX(0.0f);
                panelV.add(classLabel);
 
                
            }
            panelH.add(panelV);
            this.add(panelH);
        }
        
    }
    
    private ArrayList<JRadioButton> detectorRadioButtons;
    
}
 






 
 


     