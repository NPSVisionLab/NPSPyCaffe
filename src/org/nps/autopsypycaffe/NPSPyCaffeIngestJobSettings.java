/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nps.autopsypycaffe;

import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
/**
 *
 * @author tomb
 */
public class NPSPyCaffeIngestJobSettings implements IngestModuleIngestJobSettings {
    
 
    private static final long serialVersionUID = 1L;
    private boolean skipKnownFiles = true;
    private String curDetector;
 
    NPSPyCaffeIngestJobSettings() {
    }
 
    NPSPyCaffeIngestJobSettings(String curDetector) {
        this.curDetector = curDetector;
    }
 
    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }
 
    
    String getDetector() {
        return curDetector;
    }
    
    void setDetector(String det) {
        curDetector = det;
    }
}
 





 
 


     