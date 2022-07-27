package io.confluent.examples.clients.cloud.springboot.kafka;

import io.confluent.examples.clients.cloud.springboot.kafka.models.BasicTimestampedModel;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;


@Log4j2
@Component
public class ConsumerBasicTimestampedModel {
  Environment env;
  @Autowired
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  KafkaListenerEndpointRegistry registry;

  // Configuration details
  private final long maxRecords;
  private String fileName = null;
  private FileWriter fileWriter = null;
  private boolean stop = false;

  // Local metrics
  private AtomicLong count = new AtomicLong(0), sum = new AtomicLong(0);
  private Instant firstWrite, lastWrite, firstLog, lastLog;

  public ConsumerBasicTimestampedModel(Environment env) {
    this.env = env;
    this.maxRecords = Long.parseLong(env.getProperty("max.records", "0"));
  }

  private FileWriter getFileWriter() throws IOException {
    if (this.fileName == null){
      this.fileName = "kafkaWriteLogs_" + Instant.now().toString().replace(":", "-") + ".csv";
    }
    if (this.fileWriter == null){
      this.fileWriter = new FileWriter(this.fileName);
      this.fileWriter.append("logTimeDate,logTimeDateType,writeDate,latMillis,avgLatMillis,elapsedMillis,cumulativeCount" + System.lineSeparator());
    }

    return this.fileWriter;
  }

  @KafkaListener(
          id = "BasicTimestampedModel",
          topics = "#{'${io.confluent.developer.config.topic.name}'}"
  )
  public void consume(final @NotNull ConsumerRecord<Long, BasicTimestampedModel> consumerRecord) throws IOException {
    if (stop == false && maxRecords > 0 && count.get() < maxRecords){
      BasicTimestampedModel model = consumerRecord.value();
      long write = model.getTimestamp();

      String type = consumerRecord.timestampType().name;
      long logTime = consumerRecord.timestamp();

      long diff = logTime - write;

      long _count = count.incrementAndGet();
      long _sum = sum.addAndGet(diff);

      long avg = _sum/_count;

      Instant logTimeDate = Instant.ofEpochMilli(logTime);
      Instant writeDate = Instant.ofEpochMilli(write);

      if(firstLog == null || firstLog.compareTo(logTimeDate) > 0){
        firstLog = logTimeDate;
      }
      if(lastLog == null || lastLog.compareTo(writeDate) < 0){
        lastLog = logTimeDate;
      }
      if(firstWrite == null || firstWrite.compareTo(writeDate) > 0){
        firstWrite = writeDate;
      }
      if(lastWrite == null || lastWrite.compareTo(writeDate) < 0){
        lastWrite = writeDate;
      }

      long elapsedMillis = Duration.between(firstLog, lastLog).toMillis();

      FileWriter fileWriter = getFileWriter();
      fileWriter.append(logTimeDate + "," + type + "," + writeDate + "," + diff + "," + avg + "," + elapsedMillis + "," + _count + System.lineSeparator());
      fileWriter.flush();

      if(count.get() >= maxRecords || Duration.between(firstLog, logTimeDate).toMinutes() >= 2 ){
        this.stop = true;
        registry.getListenerContainer("BasicTimestampedModel").stop();
      }
    }
  }

  @PreDestroy
  public void getMetrics(){
    try {
      if (this.fileWriter != null){
        this.fileWriter.flush();
        this.fileWriter.close();
      }
    }catch (IOException e){
      log.error("Unable to close log file: " + this.fileName);
    }
    try {
      long elapsedWrite = Duration.between(firstWrite, lastWrite).toMillis();
      log.info("First Write: {} Last Write: {} Elapsed write millis: {}", firstWrite, lastWrite, elapsedWrite);
    } catch (Exception e){
      log.error("Failed to output elapsedWrite with: \nFirst Write: {} \nLast Write: {} \nCause: {}", firstWrite, lastWrite, e.getMessage());
    }
    try {
      long elapsedlog = Duration.between(firstLog, lastLog).toMillis();
      log.info("First log: {} Last Log: {} Elapsed log millis: {}", firstLog, lastLog, elapsedlog);
    } catch (Exception e){
      log.error("Failed to output elapsedWrite with: \nFirst Log: {} \nLast Log: {} \nCause: {}", firstLog, lastLog, e.getMessage());
    }
    try {
      long _count = count.get();
      long _sum = sum.get();
      long avg = _sum/_count;
      log.info("Average latency: {} Record count: {}" , avg, _count);
    } catch (Exception e) {
      log.error("Fail to output Average latency with: " + e.getMessage());
    }
  }

}
