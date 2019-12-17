package biz.cits;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.TopicPartitionOffset;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfiguration {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Value("${kafka.topic}")
    String kafkaTopic;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        return new DefaultKafkaProducerFactory<>(producerProperties);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public KafkaMessageListenerContainer<?, ?> kafkaListenerContainer(@Qualifier("kafkaConsumerFactory") ConsumerFactory<?, ?> kafkaConsumerFactory) {
        return new KafkaMessageListenerContainer<>(kafkaConsumerFactory,
                new ContainerProperties(new TopicPartitionOffset(kafkaTopic, 0)));
    }

    @Bean
    public ConsumerFactory<?, ?> kafkaConsumerFactory() {
        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();
        consumerProperties.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 15000);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

}
