package com.bod.consumer;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.bod.consumer.KCL.KinRecordFactory;
import com.bod.consumer.Utils.ConfigProps;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class KinConsumer {
    private static final InitialPositionInStream APPLICATION_INITIAL_POSITION_IN_STREAM = InitialPositionInStream.LATEST;
    private static final Set<String> batcharray = Collections.synchronizedSet(new HashSet<String>());
    private static final Log LOG = LogFactory.getLog(KinConsumer.class);
    // Position can be one of LATEST (most recent data) or TRIM_HORIZON (oldest available data)
    private static final IRecordProcessorFactory recordProcessorFactory = new KinRecordFactory(batcharray);
    private static int corePoolSize = Runtime.getRuntime().availableProcessors();
    private static int maxPoolSize = Runtime.getRuntime().availableProcessors() + 1;
    private static int keepAliveTime = 120;
    private static LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(5000);
    private static ThreadPoolExecutor exec = new ThreadPoolExecutor(corePoolSize,
            maxPoolSize,
            keepAliveTime,
            TimeUnit.MILLISECONDS,
            workQueue);
    //private static ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public static void main(String... args) throws Exception {
        LOG.info("Available Processors: " + Runtime.getRuntime().availableProcessors());

        String environment = args[0];
        ConfigProps config = new ConfigProps();
        /*loads log files*/
        FileUtils.deleteQuietly(new File("/var/tmp/consumer.log"));
        FileUtils.touch(new File("/var/tmp/consumer.log"));
        System.setProperty("logfile.name", "/var/tmp/consumer.log");
        config.loadLog4jprops();

        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
        /*sts token call*/
        AWSSecurityTokenService sts_client = AWSSecurityTokenServiceClientBuilder
                .standard()
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
        STSAssumeRoleSessionCredentialsProvider crossCredential = new STSAssumeRoleSessionCredentialsProvider
                .Builder(config.getPropValues("viper_role"), "bd-kcl-viper" + "-" + environment)
                .withStsClient(sts_client)
                .build();
        /*sts token call*/

        /*Delete the table
        AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(crossCredential)
                .withRegion(Regions.US_WEST_2).build();
        System.out.println(dynamoDB.describeTable(config.getPropValues("app_name") + "-" + environment));
        System.out.printf("Deleting the Amazon DynamoDB table used by the Amazon Kinesis Client Library. Table Name = %s.\n", config.getPropValues("app_name") + "-" + environment);
        try {
            dynamoDB.deleteTable(config.getPropValues("app_name") + "-" + environment);
        } catch (com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException ex) {
            System.out.println("The table doesn't exist.");
        }*/

        /*submit infinite loop*/
        exec.submit(() -> {
            try {
                String workerId = InetAddress.getLocalHost().getCanonicalHostName() + ":" + UUID.randomUUID();
                KinesisClientLibConfiguration kinesisClientLibConfiguration = new KinesisClientLibConfiguration(config.getPropValues("app_name") + "-" + environment, config.getPropValues("stream_name"), crossCredential, workerId);
                kinesisClientLibConfiguration.withInitialPositionInStream(APPLICATION_INITIAL_POSITION_IN_STREAM);
                kinesisClientLibConfiguration.withKinesisEndpoint(config.getPropValues("endpoint"));
                kinesisClientLibConfiguration.withRegionName(config.getPropValues("region"));
                kinesisClientLibConfiguration.withIdleTimeBetweenReadsInMillis(Integer.valueOf(config.getPropValues("idle_time_between_reads")));
                kinesisClientLibConfiguration.withFailoverTimeMillis(Integer.valueOf(config.getPropValues("fail_over_time")));
                kinesisClientLibConfiguration.withShardSyncIntervalMillis(Integer.valueOf(config.getPropValues("shard_sync_interval")));
                //kinesisClientLibConfiguration.withInitialLeaseTableReadCapacity(300);
                //kinesisClientLibConfiguration.withInitialLeaseTableWriteCapacity(300);
                kinesisClientLibConfiguration.withMaxRecords(Integer.valueOf(config.getPropValues("max_records")));

                Worker worker = new Worker.Builder()
                        .recordProcessorFactory(recordProcessorFactory)
                        .config(kinesisClientLibConfiguration)
                        .build();

                System.out.printf("Running %s to process stream %s as worker %s...\n", config.getPropValues("app_name"), config.getPropValues("stream_name"), workerId);

                while (!exec.isShutdown()) {
                    worker.run();
                }
                if (!exec.isTerminated()) {
                    exec.shutdown();
                }
            } catch (Exception e) {
                LOG.error(e.getStackTrace() + "->" + e.getMessage());
            } catch (Throwable t) {
                System.exit(1);
            }
        });
    }
}
