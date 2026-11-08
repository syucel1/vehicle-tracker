package com.vehicletracker.bridge.config;

import com.vehicletracker.bridge.core.BridgeProcessor;
import com.vehicletracker.bridge.core.DeviceRegistry;
import com.vehicletracker.bridge.core.EventNormalizer;
import com.vehicletracker.bridge.core.PayloadCodec;
import com.vehicletracker.bridge.core.PositionNormalizer;
import com.vehicletracker.bridge.core.UnknownDeviceThrottle;
import com.vehicletracker.bridge.kafka.JacksonPayloadCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BridgeProperties.class)
public class BridgeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BridgeConfiguration.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PayloadCodec payloadCodec() {
        return new JacksonPayloadCodec();
    }

    @Bean
    DeviceRegistry deviceRegistry(BridgeProperties properties) {
        BridgeProperties.Devices devices = properties.getDevices();
        log.info("Device registry mode {} ({} allowed IDs)", devices.getMode(), devices.getAllowed().size());
        return DeviceRegistry.of(devices.getMode(), devices.getAllowed());
    }

    @Bean
    BridgeProcessor bridgeProcessor(PayloadCodec codec, DeviceRegistry registry, BridgeProperties properties,
                                    Clock clock) {
        BridgeProperties.TopicNames t = properties.getTopics();
        return new BridgeProcessor(
                codec,
                new PositionNormalizer(clock, properties.getValidation().getMaxFutureSkew()),
                new EventNormalizer(clock),
                registry,
                new UnknownDeviceThrottle(clock, properties.getDevices().getUnknownReportInterval()),
                new BridgeProcessor.Topics(t.getPositionsRaw(), t.getPositionsRejected(), t.getDeviceEvents(),
                        t.getDeviceUnknown()),
                clock);
    }

    /**
     * Bad payloads never throw (they are routed to positions.rejected), so the only failures reaching
     * the container are Kafka publish errors. Retry those forever with capped exponential back-off so a
     * broker outage pauses the bridge instead of dropping positions. Offsets are only committed after
     * a batch has been fully published (at-least-once delivery).
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxInterval(30_000L);
        return new DefaultErrorHandler(backOff);
    }
}
