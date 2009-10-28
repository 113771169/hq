package org.hyperic.hq.events.server.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Implementation of {@link AlertConditionEvaluatorRepository} that stores and
 * retrieves by serialzing state to/from a file
 * @author jhickey
 *
 */
public class FileAlertConditionEvaluatorStateRepository implements AlertConditionEvaluatorStateRepository {
    private final File storageDirectory;
    public static final String EVALUATOR_STATE_FILE_NAME = "AlertConditionEvaluatorStates.dat";
    public static final String EXECUTION_STRATEGY_FILE_NAME = "ExecutionStrategyStates.dat";
    private final Log log = LogFactory.getLog(FileAlertConditionEvaluatorStateRepository.class);

    /**
     *
     * @param storageDirectory The directory in which to write and read
     *        serialized object files
     */
    public FileAlertConditionEvaluatorStateRepository(File storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    private void closeStream(InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (Exception e) {
            log.warn("Error closing input stream", e);
        }
    }

    private void closeStream(OutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (Exception e) {
            log.warn("Error closing output stream", e);
        }
    }

    public Map<Integer, Serializable> getAlertConditionEvaluatorStates() {
        return getStates(new File(storageDirectory, EVALUATOR_STATE_FILE_NAME));
    }

    public Map<Integer, Serializable> getExecutionStrategyStates() {
        return getStates(new File(storageDirectory, EXECUTION_STRATEGY_FILE_NAME));
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Serializable> getStates(File in) {
        if (in.exists() && in.canRead()) {
            if (log.isInfoEnabled()) {
                log.info("Loading alert condition evaluator states from [" + in.getAbsolutePath() + "]");
            }
            FileInputStream fileInputStream = null;
            ObjectInputStream objectInputStream = null;
            try {
                fileInputStream = new FileInputStream(in);
                objectInputStream = new ObjectInputStream(new BufferedInputStream(fileInputStream));
                Map states = (Map) objectInputStream.readObject();
                return states;
            } catch (Exception e) {
                log.warn("Error while reading alert condition evaluator states from [" + in.getAbsolutePath() + "]", e);
            } finally {
                in.delete();
                closeStream(objectInputStream);
                closeStream(fileInputStream);
            }
        } else if (in.exists()) {
            log.warn("Alert condition evaluator states found in [" + in.getAbsolutePath() +
                     "] but I don't have read access!");
        }
        return new HashMap<Integer, Serializable>();
    }

    private void persistStates(Map<Integer, Serializable> states, File out) {
        ObjectOutputStream objectOutputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            if (out.isFile()) {
                log.warn(out.getAbsolutePath() + " already exists.  It will be deleted.");
            }
            out.delete();
            fileOutputStream = new FileOutputStream(out);
            objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(fileOutputStream));
            objectOutputStream.writeObject(states);
            objectOutputStream.flush();
            if (log.isInfoEnabled()) {
                log.info("Successfully saved alert condition evaluator states to " + out.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("Unable to save alert condition evaluator states", e);
        } finally {
            closeStream(objectOutputStream);
            closeStream(fileOutputStream);
        }
    }

    public void saveAlertConditionEvaluatorStates(Map<Integer, Serializable> alertConditionEvaluatorStates) {
        persistStates(alertConditionEvaluatorStates, new File(storageDirectory, EVALUATOR_STATE_FILE_NAME));

    }

    public void saveExecutionStrategyStates(Map<Integer, Serializable> executionStrategyStates) {
        persistStates(executionStrategyStates, new File(storageDirectory, EXECUTION_STRATEGY_FILE_NAME));
    }

}
