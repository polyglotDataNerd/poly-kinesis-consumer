package com.bod.consumer.KCL;

import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorFactory;

import java.util.Collections;
import java.util.Set;

public class KinRecordFactory implements IRecordProcessorFactory {

    Set<String> batcharray;

    public KinRecordFactory(Set<String> batcharray) {
        this.batcharray = Collections.synchronizedSet(batcharray);
    }

    @Override
    public IRecordProcessor createProcessor() {
        return new KinRecordProcess(batcharray);
    }

}
