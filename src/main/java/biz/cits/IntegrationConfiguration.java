package biz.cits;

import org.aspectj.bridge.IMessageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.jms.dsl.Jms;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.integration.kafka.dsl.KafkaProducerMessageHandlerSpec;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.kafka.outbound.KafkaProducerMessageHandler;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.messaging.*;

@Configuration
@Order(1)
public class IntegrationConfiguration {

    @Autowired
    DefaultMessageListenerContainer externalSourceMqListenerContainer;

    @Autowired
    KafkaMessageListenerContainer<String, String> kafkaListenerContainer;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.key}")
    String kafkaMessageKey;

    @Value("${kafka.topic}")
    String kafkaTopic;

    @Bean
    public IntegrationFlow received() {
        return IntegrationFlows.from(Jms.messageDrivenChannelAdapter(externalSourceMqListenerContainer)
                .autoStartup(true)
                .extractPayload(true))
                .channel("externalSourceMqChannel")
                .log(
                        LoggingHandler.Level.INFO, "External Source MQ", m -> m.getHeaders().getId() + ": " + m.getPayload()
                )
                .enrichHeaders(eh -> eh.header("MessageType", "demo", true))
                .channel("toKafka")
                .get();
    }

    private MessageChannel externalSourceMqChannel() {
        return MessageChannels.queue(1).get();
    }

    @ServiceActivator(inputChannel = "toKafka")
    @Bean
    public MessageHandler handler(KafkaTemplate<String, String> kafkaTemplate) {
        KafkaProducerMessageHandler<String, String> handler =
                new KafkaProducerMessageHandler<>(kafkaTemplate);
        handler.setTopicExpression(new LiteralExpression(kafkaTopic));
//        handler.setMessageKeyExpression(new LiteralExpression(kafkaMessageKey));
        return handler;
    }

    @Bean
    public KafkaMessageDrivenChannelAdapter<String, String>
    adapter(KafkaMessageListenerContainer<String, String> container) {
        KafkaMessageDrivenChannelAdapter<String, String> kafkaMessageDrivenChannelAdapter =
                new KafkaMessageDrivenChannelAdapter<>(container, KafkaMessageDrivenChannelAdapter.ListenerMode.record);
        kafkaMessageDrivenChannelAdapter.setOutputChannel(toMongoChannel());
        return kafkaMessageDrivenChannelAdapter;
    }

    @Bean
    public DefaultKafkaHeaderMapper mapper() {
        return new DefaultKafkaHeaderMapper();
    }

    @Bean
    public PollableChannel consumerChannel() {
        return new QueueChannel();
    }

    @Bean("toMongoChannel")
    public PollableChannel toMongoChannel() {
        return new QueueChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "logChannel")
    public LoggingHandler logging() {
        LoggingHandler adapter = new LoggingHandler(LoggingHandler.Level.DEBUG);
        adapter.setLoggerName("TEST_LOGGER");
        adapter.setLogExpressionString("headers.id + ': ' + payload");
        return adapter;
    }


}
