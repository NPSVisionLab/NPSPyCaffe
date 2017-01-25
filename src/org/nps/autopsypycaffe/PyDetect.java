/******************************************************************************
 * $Id$
 *
 * Name:     PyDetect.java
 * Author:   Thomas Batcha
 * 
 *
 * $Log$
 * Revision 1.1  2006/02/08 19:39:03  collinsb
 * Initial version
 *
 *
 */

package org.nps.autopsypycaffe;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.lang.IllegalArgumentException;
import java.lang.RuntimeException;
import java.lang.Process;

import org.apache.commons.io.FileUtils;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.environment.EnvironmentUtils;

//import org.apache.commons.lang3.CharEncoding;

import java.awt.geom.*;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.lang.StringUtils;
import static org.nps.autopsypycaffe.NPSPyCaffeFileIngestModule.addToBlackboardPostCount;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;


public class PyDetect
{
    private static final Logger logger = Logger.getLogger(NPSPyCaffeFileIngestModule.class.getName() + ".PyDetect");
    final private Properties _lastProps;
    private PipedInputStream inPipe;
    private PipedOutputStream outPipe;
    private CollectOutputStream stdOutData;
    private CollectOutputStream stdErrData; 
    private DefaultExecutor exec;
    private DefaultExecuteResultHandler resH;
    private AutoFlushingPumpStreamHandler pump;
    private String detectorScript;
    private Map<String, AbstractFile> absMap;
    private boolean detectorRunning = false;
    private IngestServices services = IngestServices.getInstance();
    private boolean debugMode = false;
    private String detectName;
    
  
    public PyDetect(String name, Properties props, boolean dMode) {
        detectName = name;
        absMap = new HashMap<String, AbstractFile>();
        _lastProps = props;
        if (dMode){
        //turn on all logging
            this.debugMode = true;
            logger.setLevel(Level.FINE);
        }
        
    }
    
    public static class DetectionResult {
        public String fileName;
        public String objName;
        public java.awt.geom.Point2D pix;
        public Double width;
        public Double height;
        public Double score;
        
    }

    public class CollectOutputStream extends OutputStream {
        private ConcurrentLinkedQueue<String> lines = new ConcurrentLinkedQueue<String>();
        private boolean keep = true;
        
        CollectOutputStream(boolean keep){
            this.keep = keep;
        }
        
        @Override
        public  void write(byte[] b, int off, int len){
            
            if (keep){
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                buf.write(b, off, len);
                String temp = buf.toString();
                String[] tempLines = temp.split("\\r?\\n");
                for (String next : tempLines){   
                    lines.offer(next);
                }
            }
        }
        @Override
        public void write(int b){
            logger.log(Level.WARNING, "Function not implemented!");
        }
        
        public List<String> getLines() {
            LinkedList<String> res = new LinkedList<String>();
            String next;
            while ((next = lines.poll()) != null) {
                res.add(next);
            }
            return res;
        }
        
        public  boolean hasLines() {
            return lines.isEmpty() == false;
        }
        
        
    }
    

   

    public Properties getLastProps() {
        return _lastProps;
    }

     public AbstractFile getAbsMapAFile(String name){
        //String excaped = name.replace("\\", "\\\\");
        return absMap.get(name);
    }
    
    public void putAbsMapAFile(String name, AbstractFile afile){
        //String excaped = name.replace("\\", "\\\\");
        absMap.put(name, afile);
    }
    
    public void removeAbsMapFile(String name){
        //String excaped = name.replace("\\", "\\\\");
        if (absMap.containsKey(name)){
            absMap.remove(name);
            
            File file = new File(name);
            if (file.exists()){
                file.delete();
            }
            
        }
    }
    
    /*
     * Remove any remaining entries and temp files.
     * These are are the ones that no detections were found for
    */
    public void clearAbsMap(){
       Iterator<Map.Entry<String, AbstractFile>> entries = absMap.entrySet().iterator();
       while (entries.hasNext()){
           Map.Entry<String, AbstractFile> entry = entries.next();
           String fileName = entry.getKey();
           File file = new File(fileName);
           if (file.exists()){
               file.delete();
           }
       }
       absMap.clear();
    }
    
    
    public void getResults(ArrayList<DetectionResult> res, List<String> lines){
        // The input stream is:
        // The filename processed, this is allways returned
        // file: filename 
        // Input steam for each detection should be objname  x y w h
        // The x,y,w,h are in image pixel coordinates.
        // These are only present if detections occur.
        // Since the filename is processed delete the file as its a temp file
        String lastFile = null;
        for (String line : lines) {
            logger.log(Level.FINE, "results: " + line);
            if (line.equals("")){
                continue;
            }
            StringTokenizer tok = new StringTokenizer(line);
            String next = (String)tok.nextElement();
            if (next.equals("file:")){
                String nextFile = (String)tok.nextElement();
                /*
                if (lastFile != null){
                    File file = new File(lastFile);
                    if (file.delete() == false) {
                        logger.log(Level.INFO, "Failed to delete temp file: {0}", file.getName()); //NON-NLS
                    }
                }
                */
                lastFile = nextFile;
            }else {
                DetectionResult dres = new DetectionResult();
                dres.fileName = lastFile;
                dres.objName = next;
                dres.score = Double.valueOf((String)tok.nextElement());
                dres.pix = new Point2D.Double(
                                  Double.valueOf((String)tok.nextElement()),
                                  Double.valueOf((String)tok.nextElement()));
                dres.width = Double.valueOf((String)tok.nextElement());
                dres.height = Double.valueOf((String)tok.nextElement());
                res.add(dres);
            }
        }
        
    }
    
    
    
    public ArrayList<DetectionResult> getDetections(
                                      String detectorScript,
                                      String modelfile,
                                      String prototxt,
                                      String cfg,
                                      String classes,
                                      String confidence,
                                      String imageFileName,
                                      AbstractFile absFile) throws
                                      InterruptedException, IOException{
        
   
        ArrayList<DetectionResult> res = new ArrayList<DetectionResult>();
        
        if (exec == null){
            // First time executing so spin up the detector process
            if (new File(detectorScript).exists() == false){
                logger.log(Level.SEVERE, "Detector script " + detectorScript + " does not exist!!!");
                return res;
            }
            String pythonExe = _lastProps.getProperty(detectName + ".python");
            if (pythonExe == null || new File(pythonExe).exists() == false){
                pythonExe = NPSPyCaffeFactory.getPythonPath();
                if (pythonExe == null || new File(pythonExe).exists() == false){
                    logger.log(Level.SEVERE, "Not a valid python executeable in properties or path!!!");
                    return res;
                }
            }

            CommandLine args = CommandLine.parse(pythonExe);
            this.detectorScript = detectorScript;
            args.addArgument(detectorScript);
            //args.addArgument("--split");
            //args.addArgument("600");
            //args.addArgument("--cpu");
            args.addArgument("--model");
            args.addArgument(modelfile);
            args.addArgument("--proto");
            args.addArgument(prototxt);
            args.addArgument("--cfg");
            args.addArgument(cfg);
            if (confidence != null){
                args.addArgument("--confidence");
                args.addArgument(confidence);
            }
            if (classes != null){
                args.addArgument("--classes");
                args.addArgument(classes);
            }
            //args.addArgument("--tiles");
            //args.addArgument(imageLayerPath.getAbsolutePath());
            args.addArgument(imageFileName);
            String baseDir = NPSPyCaffeFactory.getBasePath();
            // get any PYTHONPATH add
            Map<String, String> env = EnvironmentUtils.getProcEnvironment();

            if (NPSPyCaffeFactory.hasGPU(detectName)){
                logger.log(Level.INFO, "Using GPU");
                String cudaPath = NPSPyCaffeFactory.getCudaPath();
                String pythonpath = _lastProps.getProperty(detectName + ".pythonPath");
                String[] pylines = null;
                if (pythonpath != null)
                    pylines = StringUtils.split(pythonpath, ";");
                String mypath = _lastProps.getProperty(detectName + ".path");
                String[] pathlines = null;
                if (mypath != null)
                    pathlines = StringUtils.split(mypath, ";");
                String envString = "%PATH%;" + cudaPath + "/bin";
                for (String next : pathlines){
                    envString = envString + ";" + baseDir + detectName + "/" + next;   
                }
                envString = envString + ";" + "../3rdparty";
                env.put("PATH", envString);
                envString = "";
                for (String next : pylines){
                   envString = envString + ";" + baseDir +  detectName + "/" + next;
                }   
                env.put("PYTHONPATH", envString);
               
            }else {
                String pythonpath = _lastProps.getProperty(detectName + ".pythonPathNoCuda");
                String[] pylines = null;
                if (pythonpath != null)
                    pylines = StringUtils.split(pythonpath, ";");
                String mypath = _lastProps.getProperty(detectName + ".pathNoCuda");
                String[] pathlines = null;
                if (mypath != null)
                    pathlines = StringUtils.split(mypath, ";");
                String envString = "%PATH%";
                for (String next : pathlines){
                    envString = envString + ";" + baseDir + detectName + "/" + next;
                }
                envString = envString + ";" + "../3rdparty";
                env.put("PATH", envString);
                envString = "";
                for (String next : pylines){
                   envString = envString + ";" + baseDir + detectName + "/" + next;
                }   
                env.put("PYTHONPATH", envString);
                env.put("OPENBLAS_NUM_THREADS","8");
                args.addArgument("--cpu");
            }

            logger.log(Level.INFO, "PYTHONPATH= " + env.get("PYTHONPATH"));
            logger.log(Level.INFO, "PATH=" + env.get("PATH"));
            logger.log(Level.INFO, "Calling detection script " + detectorScript);
            logger.log(Level.INFO, "Detecting file " + imageFileName);
            stdOutData = new CollectOutputStream(true);
            stdErrData = new CollectOutputStream(debugMode);
            outPipe = new PipedOutputStream();
            inPipe = new PipedInputStream(outPipe);
            
            resH = new DefaultExecuteResultHandler();
            exec = new DefaultExecutor();
            pump = new AutoFlushingPumpStreamHandler(stdOutData, stdErrData, inPipe);
            exec.setStreamHandler(pump);
            
            try {
                exec.execute(args, env, resH);
                detectorRunning = true;
            }catch (ExecuteException e) {
                logger.log(Level.SEVERE, "Execute Exception " + e.getMessage());
            
            }catch (IOException e) {
                logger.log(Level.SEVERE, "IO Exception " + e.getMessage());
            }
        }else {
            //processStdinput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            logger.log(Level.INFO, "Detecting file " + imageFileName);
  
            try {
                outPipe.write(imageFileName.getBytes());
                outPipe.write("\n".getBytes());
            } catch(IOException ex){
                logger.log(Level.SEVERE, "IO Exception: Can't open detector stdin " + ex.getMessage());
            }
        }
        List<String> lines;
        // If we have a GPU then assume we only have one and wait for it
        // to finish detection.
        //if (NPSPyCaffeFactory.hasGPU()){
        //    waitForDetection();
        //} 
        // We output this if the debug property is true.
        if (this.debugMode){
            lines = stdErrData.getLines();
            for (String line : lines) {
                logger.log(Level.FINE, "script: " +line);
            }
        }
        try {
            getResults(res, stdOutData.getLines());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Parsing detection return exception caught.");
            logger.log(Level.SEVERE, e.getMessage());

        } 
        
     
        /*
        if (errCode != 0) {
            logger.log(Level.SEVERE, "Could not execute detection script!!");
            logger.log(Level.SEVERE, "Error code: " + String.valueOf(errCode));
        }
        */
        
        return res;
    }
    
    
    public ArrayList<DetectionResult> close() {
        ArrayList<DetectionResult> res = new ArrayList<DetectionResult>();
        if (detectorRunning == false){
            return res;  // Already closed it
        }
        try {
            ArrayList<DetectionResult> nextres;
            nextres = getDetections(detectorScript, "", "", "", "","","close", null);
            for (DetectionResult next : nextres){
                res.add(next);
            }
            detectorRunning = false;
            //TimeUnit.MICROSECONDS.sleep(800); // Might need time for detector to start
            pump.waitForInputComplete();  
            logger.log(Level.INFO, "Detector closed");
            
        }catch(Exception ex){
            logger.log(Level.SEVERE, "Exception closing input pipe.");
            logger.log(Level.SEVERE, ex.getMessage());
        }
        if (debugMode){
            List<String> lines = stdErrData.getLines();
            for (String line : lines) {
                logger.log(Level.FINE, "script: " +line);
            }
        }
        // Lets check results one more time just in case
        getResults(res, stdOutData.getLines());
       
         exec = null;
        logger.log(Level.INFO, "Last detect count " + String.valueOf(res.size()));
        /*
        try{
            outPipe.close();
        }catch(IOException ex){
            logger.log(Level.SEVERE, "Exception closing outPipe pipe." +
                       ex.getMessage());
        } 
        */
        
        return res;
        
    }
    


}

