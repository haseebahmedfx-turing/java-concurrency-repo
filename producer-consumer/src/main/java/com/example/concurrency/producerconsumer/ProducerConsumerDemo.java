package com.example.concurrency.producerconsumer;

/** Simple entrypoint that prints run metrics for manual experimentation. */
public class ProducerConsumerDemo {
    public static void main(String[] args) {
        int poolSize = Integer.getInteger("poolSize", 4);
        int queueCapacity = Integer.getInteger("queueCapacity", 64);
        int durationSec = Integer.getInteger("durationSec", 3);
        int rate = Integer.getInteger("producerRatePerSec", 200);

        var m = BackpressureRunner.run(poolSize, queueCapacity, durationSec, rate);
        System.out.println("Produced=" + m.produced());
        System.out.println("Consumed=" + m.consumed());
        System.out.println("CallerRuns=" + m.callerRuns());
        System.out.println("Rejected=" + m.rejected());
        System.out.println("QueueEnd=" + m.queueEnd());
    }
}
