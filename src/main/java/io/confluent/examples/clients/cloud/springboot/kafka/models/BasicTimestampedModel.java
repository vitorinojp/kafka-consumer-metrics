package io.confluent.examples.clients.cloud.springboot.kafka.models;

public class BasicTimestampedModel {
    public long timestamp;

    public BasicTimestampedModel() {
    }

    public BasicTimestampedModel(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
