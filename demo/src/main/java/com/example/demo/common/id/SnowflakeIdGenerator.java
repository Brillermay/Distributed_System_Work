package com.example.demo.common.id;

import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {
    private static final long START_STAMP = 1704067200000L; // 2024-01-01
    private static final long SEQUENCE_BIT = 12;
    private static final long MACHINE_BIT = 5;
    private static final long DATACENTER_BIT = 5;

    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);
    private static final long MACHINE_LEFT = SEQUENCE_BIT;
    private static final long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final long TIMESTAMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    private final long datacenterId = 1L;
    private final long machineId = 1L;

    private long sequence = 0L;
    private long lastStamp = -1L;

    public synchronized long nextId() {
        long curr = System.currentTimeMillis();
        if (curr < lastStamp) throw new RuntimeException("时钟回拨，拒绝生成ID");

        if (curr == lastStamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                while ((curr = System.currentTimeMillis()) <= lastStamp) { }
            }
        } else {
            sequence = 0L;
        }
        lastStamp = curr;

        return ((curr - START_STAMP) << TIMESTAMP_LEFT)
                | (datacenterId << DATACENTER_LEFT)
                | (machineId << MACHINE_LEFT)
                | sequence;
    }
}