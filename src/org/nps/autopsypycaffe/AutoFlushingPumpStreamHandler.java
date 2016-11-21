
package org.nps.autopsypycaffe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.exec.ExecuteStreamHandler;
import org.apache.commons.exec.InputStreamPumper;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.StreamPumper;
import org.apache.commons.exec.util.DebugUtils;

/**
 * Copies standard output and error of subprocesses to standard output and error
 * of the parent process. If output or error stream are set to null, any feedback
 * from that stream will be lost. 
 * 
 * Based off of {@link PumpStreamHandler}, but modified as discussed here:
 * http://stackoverflow.com/a/7531626/59015
 */
public class AutoFlushingPumpStreamHandler implements ExecuteStreamHandler {

   private Thread outputThread;

	    private Thread errorThread;

	    private Thread inputThread;
            private AutoFlushingStreamPumper inputPumper;
            private AutoFlushingStreamPumper outputPumper;

	    private final OutputStream out;

	    private final OutputStream err;

	    private final InputStream input;

	    private InputStreamPumper inputStreamPumper;
	    
	    /**
	     * Construct a new <CODE>PumpStreamHandler</CODE>.
	     */
	    public AutoFlushingPumpStreamHandler() {
	        this(System.out, System.err);
	    }

	    /**
	     * Construct a new <CODE>AutoFlushingPumpStreamHandler</CODE>.
	     *
	     * @param outAndErr
	     *            the output/error <CODE>OutputStream</CODE>.
	     */
	    public AutoFlushingPumpStreamHandler(final OutputStream outAndErr) {
	        this(outAndErr, outAndErr);
	    }
	    
	    /**
	     * Construct a new <CODE>AutoFlushingPumpStreamHandler</CODE>.
	     *
	     * @param out
	     *            the output <CODE>OutputStream</CODE>.
	     * @param err
	     *            the error <CODE>OutputStream</CODE>.
	     */
	    public AutoFlushingPumpStreamHandler(final OutputStream out, final OutputStream err) {
	        this(out, err, null);
	    }

	    /**
	     * Construct a new <CODE>AutoFlushingPumpStreamHandler</CODE>.
	     * 
	     * @param out
	     *            the output <CODE>OutputStream</CODE>.
	     * @param err
	     *            the error <CODE>OutputStream</CODE>.
	     * @param input
	     *            the input <CODE>InputStream</CODE>.
	     */
	    public AutoFlushingPumpStreamHandler(final OutputStream out, final OutputStream err,
	            final InputStream input) {

	        this.out = out;
	        this.err = err;
	        this.input = input;
	    }

            public void waitForInputComplete(){
                if (outputPumper != null){
                    try {
                        outputPumper.waitFor();
                    } catch (InterruptedException ex){
                        System.out.println("waitForInputComplete interrupted!");
                    }
                }
            }
            
            public boolean isFinished(){
                if (outputPumper != null){
                    if (outputPumper.isFinished()){
                        return true;
                    }else {
                        return false;
                    }
                } else {
                    return true;
                }
            }
	    /**
	     * Set the <CODE>InputStream</CODE> from which to read the standard output
	     * of the process.
	     * 
	     * @param is
	     *            the <CODE>InputStream</CODE>.
	     */
	    public void setProcessOutputStream(final InputStream is) {
	        if (out != null) {
	            createProcessOutputPump(is, out);
	        }
	    }

	    /**
	     * Set the <CODE>InputStream</CODE> from which to read the standard error
	     * of the process.
	     * 
	     * @param is
	     *            the <CODE>InputStream</CODE>.
	     */
	    public void setProcessErrorStream(final InputStream is) {
	        if (err != null) {
	            createProcessErrorPump(is, err);
	        }
	    }

	    /**
	     * Set the <CODE>OutputStream</CODE> by means of which input can be sent
	     * to the process.
	     * 
	     * @param os
	     *            the <CODE>OutputStream</CODE>.
	     */
	    public void setProcessInputStream(final OutputStream os) {
	        if (input != null) {
	            if (input == System.in) {
	                inputThread = createSystemInPump(input, os);
	        } else {
	                inputThread = createPump(input, os, true);
	            }        } 
	        else {
	            try {
	                os.close();
	            } catch (IOException e) {
	                String msg = "Got exception while closing output stream";
	                DebugUtils.handleException(msg ,e);
	            }
	        }
	    }
	        
	    /**
	     * Start the <CODE>Thread</CODE>s.
	     */
	    public void start() {
	        if (outputThread != null) {
	            outputThread.start();
	        }
	        if (errorThread != null) {
	            errorThread.start();
	        }
	        if (inputThread != null) {
	            inputThread.start();
	        }
	    }

	    /**
	     * Stop pumping the streams.
	     */
	    public void stop() {

	        if (inputStreamPumper != null) {
	            inputStreamPumper.stopProcessing();
	        }

	        if (outputThread != null) {
	            try {
	                outputThread.join();
	                outputThread = null;
	            } catch (InterruptedException e) {
	                // ignore
	            }
	        }

	        if (errorThread != null) {
	            try {
	                errorThread.join();
	                errorThread = null;
	            } catch (InterruptedException e) {
	                // ignore
	            }
	        }

	        if (inputThread != null) {
	            try {
	                inputThread.join();
	                inputThread = null;
	            } catch (InterruptedException e) {
	                // ignore
	            }
	        }

	         if (err != null && err != out) {
	             try {
	                 err.flush();
	             } catch (IOException e) {
	                 String msg = "Got exception while flushing the error stream : " + e.getMessage();
	                 DebugUtils.handleException(msg ,e);
	             }
	         }

	         if (out != null) {
	             try {
	                 out.flush();
	             } catch (IOException e) {
	                 String msg = "Got exception while flushing the output stream";
	                 DebugUtils.handleException(msg ,e);
	             }
	         }
	    }

	    /**
	     * Get the error stream.
	     * 
	     * @return <CODE>OutputStream</CODE>.
	     */
	    protected OutputStream getErr() {
	        return err;
	    }

	    /**
	     * Get the output stream.
	     * 
	     * @return <CODE>OutputStream</CODE>.
	     */
	    protected OutputStream getOut() {
	        return out;
	    }

	    /**
	     * Create the pump to handle process output.
	     * 
	     * @param is
	     *            the <CODE>InputStream</CODE>.
	     * @param os
	     *            the <CODE>OutputStream</CODE>.
	     */
	    protected void createProcessOutputPump(final InputStream is,
	            final OutputStream os) {
	        outputThread = createPump(is, os);
	    }

	    /**
	     * Create the pump to handle error output.
	     * 
	     * @param is
	     *            the <CODE>InputStream</CODE>.
	     * @param os
	     *            the <CODE>OutputStream</CODE>.
	     */
	    protected void createProcessErrorPump(final InputStream is,
	            final OutputStream os) {
	        errorThread = createPump(is, os);
	    }

	    /**
	     * Creates a stream pumper to copy the given input stream to the given
	     * output stream.
	     *
	     * @param is the input stream to copy from
	     * @param os the output stream to copy into
	     * @return the stream pumper thread
	     */
	    protected Thread createPump(final InputStream is, final OutputStream os) {
	        return createPump(is, os, false);
	    }

	    /**
	     * Creates a stream pumper to copy the given input stream to the given
	     * output stream.
	     *
	     * @param is the input stream to copy from
	     * @param os the output stream to copy into
	     * @param closeWhenExhausted close the output stream when the input stream is exhausted
	     * @return the stream pumper thread
	     */
	    protected Thread createPump(final InputStream is, final OutputStream os,
	            final boolean closeWhenExhausted) {
                // We want to keep the input StreamPumper around so it can be queried
                final Thread result;
                if (is == input){
                    inputPumper = new AutoFlushingStreamPumper(is, os, closeWhenExhausted);
                    result = new Thread(inputPumper);
                }else if (os == out){
                    outputPumper = new AutoFlushingStreamPumper(is, os, closeWhenExhausted);
                    result = new Thread(outputPumper);
                }else {
                    result = new Thread(new AutoFlushingStreamPumper(is, os,
	                closeWhenExhausted));
                }
	        result.setDaemon(true);
	        return result;
	    }


	    /**
	     * Creates a stream pumper to copy the given input stream to the given
	     * output stream.
	     *
	     * @param is the System.in input stream to copy from
	     * @param os the output stream to copy into
	     * @return the stream pumper thread
	     */
	    private Thread createSystemInPump(InputStream is, OutputStream os) {
	        inputStreamPumper = new InputStreamPumper(is, os);
	        final Thread result = new Thread(inputStreamPumper);
	        result.setDaemon(true);
	        return result;
	    }

}
