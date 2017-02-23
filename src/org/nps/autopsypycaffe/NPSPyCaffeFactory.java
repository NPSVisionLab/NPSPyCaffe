/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nps.autopsypycaffe;

/**
 *
 * @author tomb
 */


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;
import org.sleuthkit.datamodel.AbstractFile;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
/**
 * A factory for creating email parser file ingest module instances.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class NPSPyCaffeFactory extends IngestModuleFactoryAdapter {
    
    private static final Logger logger = Logger.getLogger(NPSPyCaffeFactory.class.getName());
    private static Properties props;
    private static String basePath;
    private static HashMap<String, Boolean> hasGPU = new HashMap<String, Boolean>();
    private static String cudaPath;
    private static PyDetect firstDetector = null;
    private static ProgressHandle progressBar;
    private static boolean progressStarted = false;
    private static int progressCnt = 0;
    private static int currentCnt = 0;
    private static int currentPercent = 0;
    private static int detectCnt = 0;
    private static ArrayList<String> detectorTypes = new ArrayList<String>();
    private static ArrayList<String> detectorNames = new ArrayList<String>();
    private static String pythonExec;
    
    public NPSPyCaffeFactory() {
        IngestServices services = IngestServices.getInstance();
        props = new Properties();
        //The user directory is where the plugin got installed
        File userDir = PlatformUtil.getUserDirectory();
        basePath = userDir.toString();
        // Search for all directories in the detectors directory.  This is the list
        // of all the detector types.
        if (new File(basePath + "/NPSPyCaffe/detectors").exists() == false) {
            // When we run in the netbeans debugger basePath is up two directories
            basePath = basePath + "/../../release/NPSPyCaffe/detectors/";
        } else
            basePath = basePath + "/NPSPyCaffe/detectors/";
        String[] dlist = new File(basePath).list();
        for (String name : dlist){
           if (new File(basePath + name).isDirectory()) {
               detectorTypes.add(name);
           }
        }
        if (detectorTypes.size() == 0){
             IngestMessage msg = IngestMessage.createErrorMessage(NPSPyCaffeFactory.getModuleName(), "Congigure Error!",
                    "No Detector types found!");            
            services.postMessage(msg);
        }else for (String det : detectorTypes){
            String[] plist = new File(basePath + det).list();
            for (String name : plist){
                if (name.endsWith(".properties")){
                    // Read anything that ends with .properties
                    File propfile = new File(basePath + det + "/" + name);
                    if (propfile.exists() == true){
                        try {
                            FileInputStream in = new FileInputStream(propfile);
                            props.load(in);
                            in.close();
                            if (name.equals("detector.properties")){
                                // main property file describing the detector type
                                boolean gpu = checkForGPU(det);
                                hasGPU.put(det, gpu);
                            }else {
                                // specific detector property file so use name of file as detector name
                                int idx = name.indexOf(".properties");
                                String detName = name.substring(0,idx);
                                detectorNames.add(det + "." + detName);
                            }
                        }catch(FileNotFoundException ex){
                            logger.log(Level.SEVERE, "Could not Find detector.properties file");
                        }catch (IOException ex){
                            logger.log(Level.SEVERE, ex.getMessage());
                        }
                    }
                }
            }
            
            for (String name : plist){
                if (name.endsWith(".properties")){
                    
                }
            }
        }
        // Find Python Exec
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            CommandLine args = CommandLine.parse("where.exe");
            args.addArgument("python");
            DefaultExecutor exec = new DefaultExecutor();
            PumpStreamHandler stdpump = new PumpStreamHandler(outStream);
            exec.setStreamHandler(stdpump);
            exec.execute(args);
            String whereOut = outStream.toString();
            String[] lines = StringUtils.split(whereOut, "\r\n");
            pythonExec = lines[0];
      
        } catch (IOException ex){
        } 
         
    }
    
    static Properties getProperties() {
        return props;
    }
    
    static ArrayList<String> getAvailableDetectors() {
        return detectorNames;
    }
    
    static String getBasePath() {
        return basePath;
    }
    static String getModuleName() {
        return  "NPSPyCaffeFileIngestModule";
    }
    static String getPythonPath() {
        return pythonExec;
    }

    static String getModuleVersion() {
        return "1.1";
    }

    static String getDetectorType(String dname){
        String dtype = null;
        int idx = dname.indexOf(".");
        if (idx != -1){
            dtype = dname.substring(0, idx);   
        } 
        return dtype;
    }
    
   
    static boolean hasGPU(String dname){
        // We take both detector names is form of type.name
        // and just type.
        String dtype = getDetectorType(dname);
        if (dtype != null){
            if (hasGPU.containsKey(dtype))   
                return (boolean)hasGPU.get(dtype);
        }
        return false;
    }
    
    static String getCudaPath() {
        return cudaPath;
    }
    
    static synchronized void incProgressCnt() {
        progressCnt++;
    }
    static synchronized void startProgress() {
        if (progressStarted == false){
            progressStarted = true;
            currentCnt = 0;
            currentPercent = 0;
            progressBar.start(100);
        }
    }
    static synchronized void setProgress(){
        currentCnt++;
        
        if (progressStarted){
            int percent;
            if (progressCnt > 0) {
                percent = (100 * currentCnt) / progressCnt;
                if (percent > 100)
                    percent = 100;
                if (percent < currentPercent)
                    percent = currentPercent; // Cannot go backwards
            }else
                percent = 0;
 
            progressBar.progress(percent);
            currentPercent = percent;
        }
    }
    
    static synchronized void finishProgress() {
        if (progressStarted){
            detectCnt--;
            if (detectCnt <= 0){
                progressStarted = false;
                progressCnt = 0;
                detectCnt = 0;
                progressBar.finish();
                firstDetector = null;
            }
        }
    }
    
    
    
    /*
     * Try and set cudaPath and return if cuda has been installed
    */
    public boolean checkForGPU(String dTypeName){
        // If the user overrides using the gpu then return false
        String useGPU = props.getProperty(dTypeName + ".gpu");
        if (useGPU != null && useGPU.toLowerCase().equals("false")){
            return false;
        }
        cudaPath = System.getenv("CUDA_PATH");
        if (cudaPath != null){
            return true;
        }
        // Look for default location
        File loc = new File("c:/Program Files/NVIDIA GPU Computing Toolkit/CUDA");
        if (loc.exists()){
            File[] flist = loc.listFiles();
            String versionDir = null;
            for (File next : flist){
                if (next.isDirectory()){
                    versionDir = next.getName();
                    if (versionDir.equals("v7.5")){
                        break;
                    }
                }
            }
            if (versionDir != null){
                cudaPath = loc.toString() + "/" + versionDir;
            }
        }
        if (cudaPath == null){
            // Try 32 bit version
            loc = new File("c:/Program Files (x86)/NVIDIA GPU Computing Toolkit/CUDA");
            if (loc.exists()){
                File[] flist = loc.listFiles();
                String versionDir = null;
                for (File next : flist){
                    if (next.isDirectory()){
                        versionDir = next.getName();
                        if (versionDir.equals("v7.5")){
                            break;
                        }
                    }
                }
                if (versionDir != null){
                    cudaPath = loc.toString() + "/" + versionDir;
                }
            }
        }
        if (cudaPath != null){
            return true;
        }else {
            return false;
        }       
    }
    
    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
       return "NPS PyCaffe py-faster-rcnn detector";
    }

    @Override
    public String getModuleVersionNumber() {
        return getModuleVersion();
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }
    
    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }
    
    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return (IngestModuleIngestJobSettings)new NPSPyCaffeIngestJobSettings("");
    }
    
    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings){
        return (IngestModuleIngestJobSettingsPanel)new NPSPyCaffeJobSettingsPanel(settings);
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        NPSPyCaffeFileIngestModule module = new NPSPyCaffeFileIngestModule(settings, props);
        NPSPyCaffeIngestJobSettings npsSettings = (NPSPyCaffeIngestJobSettings)settings;
        String dname = npsSettings.getDetector();
        String detectorType = getDetectorType(dname);
        String hasDebug = props.getProperty(detectorType + ".debug");
        boolean debug = false;
        if (hasDebug != null && hasDebug.toLowerCase().equals("true")){
            debug = true;
        }
        PyDetect detect;
        if (firstDetector == null){
            progressBar = ProgressHandleFactory.createHandle("NPSPyCaffe");
            startProgress();
            detect = new PyDetect(dname, props, debug);
            firstDetector = detect;
        }else {
            // Only run one detector if we have a GPU (assuming not multiple GPU's)
            if (hasGPU(dname)){
                detect = firstDetector;
            }else {
                detect = new PyDetect(dname, props, debug);
            }
        }
        module.setDetector(detect);
        detectCnt++;
      
        return module;
        
    }
}
