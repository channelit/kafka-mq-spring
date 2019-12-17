package biz.cits;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.jms.dsl.Jms;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.kafka.outbound.KafkaProducerMessageHandler;
import org.springframework.integration.mongodb.config.MongoDbInboundChannelAdapterParser;
import org.springframework.integration.mongodb.outbound.MongoDbOutboundGateway;
import org.springframework.integration.mongodb.store.MongoDbChannelMessageStore;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;

@Configuration
@Order(1)
public class IntegrationConfiguration {

    @Autowired
    DefaultMessageListenerContainer externalSourceMqListenerContainer;

    @Autowired
    KafkaMessageListenerContainer kafkaListenerContainer;

    @Autowired
    ConsumerFactory<?, ?> kafkaConsumerFactory;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.key}")
    String kafkaMessageKey;

    @Value("${kafka.topic}")
    String kafkaTopic;

    @Bean
    public IntegrationFlow externalToKafkaFlow() {
        return IntegrationFlows.from(Jms.messageDrivenChannelAdapter(externalSourceMqListenerContainer)
                .autoStartup(true)
                .extractPayload(true))
                .channel("externalSourceMqChannel")
                .log(
                        LoggingHandler.Level.INFO, "External Source MQ", m -> m.getHeaders().getId() + ": " + m.getPayload()
                )
                .enrichHeaders(eh -> eh.header("kafka_partitionId", 0, true))
                .channel("toKafka")
                .get();
    }

    @Bean
    public IntegrationFlow fromKafkaFlow() {
//        return IntegrationFlows
//                .from(Kafka.messageDrivenChannelAdapter(kafkaListenerContainer,
//                        KafkaMessageDrivenChannelAdapter.ListenerMode.record)
//                        .id("topic2Adapter"))
//        .get();
        return IntegrationFlows
                .from(Kafka.messageDrivenChannelAdapter(kafkaConsumerFactory,
                        KafkaMessageDrivenChannelAdapter.ListenerMode.record, kafkaTopic)
                        .configureListenerContainer(c ->
                                c.ackMode(ContainerProperties.AckMode.MANUAL)
                                        .id("topic1ListenerContainer"))
                        .filterInRetry(true))
                .channel("toMongo")
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

//    @Bean
//    public KafkaMessageDrivenChannelAdapter<String, String> adapter(KafkaMessageListenerContainer kafkaListenerContainer) {
//        KafkaMessageDrivenChannelAdapter<String, String> kafkaMessageDrivenChannelAdapter =
//                new KafkaMessageDrivenChannelAdapter(kafkaListenerContainer, KafkaMessageDrivenChannelAdapter.ListenerMode.record);
//        kafkaMessageDrivenChannelAdapter.setOutputChannelName("toMongo");
//        return kafkaMessageDrivenChannelAdapter;
//    }

    @Bean
    public DefaultKafkaHeaderMapper mapper() {
        return new DefaultKafkaHeaderMapper();
    }

    @Bean
    public PollableChannel consumerChannel() {
        return new QueueChannel();
    }

//    @Bean
//    @ServiceActivator(inputChannel = "toMongo")
//    public PollableChannel toMongo() {
//        QueueChannel queueChannel = new QueueChannel();
//        return queueChannel;
//    }

    @Bean
    @ServiceActivator(inputChannel = "logChannel")
    public LoggingHandler logging() {
        LoggingHandler adapter = new LoggingHandler(LoggingHandler.Level.DEBUG);
        adapter.setLoggerName("TEST_LOGGER");
        adapter.setLogExpressionString("headers.id + ': ' + payload");
        return adapter;
    }

    @Bean
    @Autowired
    public MongoDbChannelMessageStore mongoDbChannelMessageStore(MongoDbFactory mongoDbFactory) {
        return new MongoDbChannelMessageStore(mongoDbFactory);
    }

    @Bean
    @ServiceActivator(inputChannel = "toMongo")
    @Autowired
    public MessageHandler mongoDbOutboundGateway(MongoDbFactory mongoDbFactory) {
        MongoDbOutboundGateway gateway = new MongoDbOutboundGateway(mongoDbFactory);
        gateway.setCollectionNameExpressionString("'FIFO'");
        gateway.setQueryExpressionString("'{''name'':''Bob''}'");
        gateway.setEntityClass(Object.class);
        gateway.setOutputChannelName("replyChannel");
        gateway.setSendTimeout(5000);
        return gateway;
    }

    @Bean
    @ServiceActivator(inputChannel = "replyChannel")
    public MessageHandler handler() {
        return message -> System.out.println(message.getPayload());
    }
}
