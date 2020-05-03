package com.bod.consumer.kcl;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ThrottlingException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.ShutdownReason;
import com.amazonaws.services.kinesis.model.Record;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.bod.consumer.utils.CompressWrite;
import com.bod.consumer.utils.ConfigProps;
import com.bod.consumer.utils.JSONTransformer;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.apache.commons.lang3.time.StopWatch;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class KinRecordProcess implements IRecordProcessor {

    private static final long BACKOFF_TIME_IN_MILLIS = 1000L;
    private static final int NUMBER_OF_RETRIES = 20;
    // Checkpoint about once a minute
    private static final long CHECKPOINT_INTERVAL_MILLIS = 1000L;
    private static ConfigProps config = new ConfigProps();
    // Backoff and retry settings
    private final Log LOG = LogFactory.getLog(KinRecordProcess.class);
    private final CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
    private final LinkedBlockingQueue<Record> recordQueue = new LinkedBlockingQueue<Record>();
    private AmazonS3 cli = AmazonS3ClientBuilder.standard().withCredentials(new DefaultAWSCredentialsProviderChain()).build();
    private ObjectMetadata objectMetadata = new ObjectMetadata();
    private ExecutorService processQueue = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat ftkey = new SimpleDateFormat("yyyy-MM-dd-HHmmss");
    private SimpleDateFormat dtc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private StringBuffer batchstring = new StringBuffer(50000000);
    private String kinesisShardId;
    private long nextCheckpointTimeInMillis;
    /* need to synchronize HashSet to make thread safe in multi-threaded environment*/
    private Set<String> batcharray;
    /* need to synchronize HashSet to make thread safe in multi-threaded environment*/
    private QueueConsumer queueConsumer;
    private String s3key;
    private StopWatch sw = new StopWatch();

    public KinRecordProcess(Set<String> batcharray) {
        this.batcharray = Collections.synchronizedSet(batcharray);
    }


    /*method validates if string is a JSON Object*/
    private static boolean isValidJSONObject(Object j) {

        try {
            new JSONObject(j.toString());
        } catch (JSONException jsonEx) {
            return false;
        }
        return true;
    }

    /*method validates if string is a JSON Array*/
    private static boolean isValidJSONArray(Object j) {

        try {
            new JSONArray(new JSONParser().parse(j.toString()).toString());
        } catch (ParseException jsonEx) {
            return false;
        } catch (JSONException jsonEx) {
            return false;
        }
        try {
            new JSONArray(j.toString());
        } catch (JSONException jsonEx) {
            return false;
        }
        return true;
    }

    @Override
    public void initialize(String shardId) {
        this.kinesisShardId = shardId;
        queueConsumer = new QueueConsumer();
        processQueue.submit(queueConsumer);
        processQueue.shutdown();
        //shutdownAndAwaitTermination(processQueue);
    }

    @Override
    public void processRecords(List<Record> records, IRecordProcessorCheckpointer checkpointer) {
        //queueConsumer.setCheckpointer(checkpointer);
        try {
            // adds to the queue
            records.stream().distinct().forEach(x -> recordQueue.add(x));
            // Checkpoint once every checkpoint interval.
            if (System.currentTimeMillis() > nextCheckpointTimeInMillis) {
                queueConsumer.setCheckpointer(checkpointer);
                checkpoint(checkpointer);
                nextCheckpointTimeInMillis = System.currentTimeMillis() + CHECKPOINT_INTERVAL_MILLIS;
            }
        } catch (Exception e) {
            LOG.info(e.getStackTrace() + "->" + e.getMessage());
        }
    }


    private void processRecordsWithRetries(List<Record> records) {
        records.stream().forEach(r -> {
            boolean processedSuccessfully = false;
            for (int i = 0; i < NUMBER_OF_RETRIES; i++) {
                try {

                    processSingleRecord(r);
                    processedSuccessfully = true;
                    break;

                } catch (Throwable t) {
                    LOG.error(t.getMessage());
                    LOG.error(t.getCause());
                }
                // backoff if we encounter an exception.
                try {
                    Thread.sleep(BACKOFF_TIME_IN_MILLIS);
                } catch (InterruptedException e) {
                    //System.out.println("Interrupted sleep: " + e);
                    LOG.error("Interrupted sleep: " + e);
                }
            }
            if (!processedSuccessfully) {
                LOG.info("Couldn't process record " + r + ". Skipping the record.");
            }
        });
    }


    private void processSingleRecord(Record record) throws Exception {
        String raw = new String();
        String payload;
        /*ingest kineses stream to s3*/
        try {

            // For this app, we interpret the payload as UTF-8 chars.
            CharBuffer cbuf = decoder.decode(record.getData());
            raw = cbuf.toString().trim();
            objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

            if ((isValidJSONArray(raw) == true) || (isValidJSONObject(raw) == true)) {
                sw.start();
                LinkedHashMap<String, String> map = new JSONTransformer(raw).transform();
                payload = map.values().stream().map(Object::toString).collect(Collectors.joining(","));
                batcharray.add(payload);
                sw.stop();
                LOG.info("Runtime Completion " + sw.getTime(TimeUnit.MICROSECONDS) + "μs" + ": " + payload.split(",")[0].replace("\"", "") + ":" + payload.split(",")[1].replace("\"", "") + ":" + payload.split(",")[8].replace("\"", ""));
                sw.reset();
            }
        }         /*Catch Errors Go Into s3*/ catch (NumberFormatException e) {
            LOG.error(e.getMessage());
            LOG.error(e.getCause());
        } catch (CharacterCodingException e) {
            LOG.error(e.getMessage());
            LOG.error(e.getCause());

        } catch (Exception e) {
            LOG.error(e.getMessage());
            LOG.error(e.getCause());
        }
    }


    @Override
    public void shutdown(IRecordProcessorCheckpointer checkpointer, ShutdownReason reason) {
        LOG.warn("Shutting down record processor for shard: " + kinesisShardId);
        try {
            if (reason == ShutdownReason.TERMINATE) {
                checkpoint(checkpointer);
            }
        } catch (Exception e) {
            //System.out.println(e.getMessage());
            LOG.error(e.getMessage());
            LOG.error(e.getCause());
        }
    }

    private void checkpoint(IRecordProcessorCheckpointer checkpointer) throws Exception {
        for (int i = 0; i < NUMBER_OF_RETRIES; i++) {
            try {
                checkpointer.checkpoint();
                break;
            } catch (ShutdownException se) {
                // Ignore checkpoint if the processor instance has been shutdown (fail over).
                LOG.warn(se);
                //break;
            } catch (ThrottlingException e) {
                // Backoff and re-attempt checkpoint upon transient failures
                if (i >= (NUMBER_OF_RETRIES - 1)) {
                    LOG.error(e.getMessage());
                    LOG.info(e);
                    break;
                } else {
                    LOG.info("Transient issue when checkpointing - attempt " + (i + 1) + " of " + NUMBER_OF_RETRIES + " " + e);
                }
            } catch (InvalidStateException e) {
                // This indicates an issue with the DynamoDB table (check for table, provisioned IOPS).
                LOG.error(e.getMessage());
                LOG.error(e);
                break;
            }
            try {
                Thread.sleep(BACKOFF_TIME_IN_MILLIS);
            } catch (InterruptedException e) {
                LOG.info("Interrupted sleep" + " " + e);
            }

        }
    }

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(10, TimeUnit.SECONDS))
                    LOG.error("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }


    /**
     * Asynchronous queue consumer to process records.
     */
    private class QueueConsumer implements Runnable {
        /**
         * /**
         * Flag to shutdown the queue consumer.
         */
        volatile boolean shutdown = false;
        /**
         * The latest checkpointer. Is wrapped to protect user from calling default checkpoint method. All access should be synchronized on the instance of
         * {@link QueueConsumer}.
         */
        private volatile IRecordProcessorCheckpointer checkpointer;

        public void setCheckpointer(IRecordProcessorCheckpointer checkpointer) {
            this.checkpointer = checkpointer;
        }

        @Override
        public void run() {
            LOG.info("Starting queue consumer for shard: " + kinesisShardId);
            while (!shutdown) {
                try {
                    consumeQueue();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    LOG.error(e);
                }
            }
            LOG.info("Queue consumer terminated for shard: " + kinesisShardId);
        }

        /**
         * Processes the records in the queue using the wrapped {@link IRecordProcessor}.
         */
        private void consumeQueue() throws Exception {
            final List<Record> records = new ArrayList<Record>();
            // Use blocking queue's poll with timeout to wait for new records
            Record polled = null;
            try {
                polled = recordQueue.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.error(e);
                Thread.currentThread().interrupt();
            }
            // Check if queue contained records.
            if (polled == null) {
                processRecords(records /* Empty list */, checkpointer);
                return;
            }
            records.add(polled);
            // Drain the remaining of the records
            recordQueue.drainTo(records);
            processRecordsWithRetries(records);
            LOG.info("Thread: " + Thread.currentThread().getName() + ": batcharray = " + batcharray.size());
            if (batcharray.size() > Integer.valueOf(config.getPropValues("batch_scale"))) {
                /** batch put into s3
                 * It is imperative that the user manually synchronize on the returned set when iterating over it, Failure to follow this advice may result in non-deterministic
                 * behavior. According to implementation. {@link batcharray}
                 */

                /* It is imperative that the user manually synchronize on the returned set when iterating over it */
                synchronized (batcharray) {
                    batcharray.stream().distinct().forEach(i -> batchstring.append(i).append(System.lineSeparator()));
                    batcharray.clear();
                }

                ByteArrayOutputStream compress = new CompressWrite().writestream(batchstring.toString());
                objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
                objectMetadata.setContentLength(compress.size());
                s3key = new String(config.getPropValues("s3ProdPrefix") + "/" + new String(ft.format(new Date())) + "/" + new String(ftkey.format(new Date())) + "-" + RandomStringUtils.randomAlphanumeric(8) + "-" + config.getPropValues("region") + ".gz");

                PutObjectRequest putRequestCSV = new PutObjectRequest(config.getPropValues("s3Bucket"), s3key, new ByteArrayInputStream(compress.toByteArray()), objectMetadata);
                putRequestCSV.setMetadata(objectMetadata);
                cli.putObject(putRequestCSV);
                LOG.info("s3 Put" + " : " + s3key);

                /*resets StringBuilder and Array*/
                batchstring.setLength(0);
                compress.close();
            }
            processRecords(records, checkpointer /* Protected checkpointer */);
        }
    }


}
