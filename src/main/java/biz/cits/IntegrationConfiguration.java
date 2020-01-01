package biz.cits;

import biz.cits.message.MsgParser;
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
import org.springframework.integration.mongodb.outbound.MongoDbOutboundGateway;
import org.springframework.integration.mongodb.store.MongoDbChannelMessageStore;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
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
                .log(
                        LoggingHandler.Level.INFO, "External Source MQ", m -> m.getHeaders()
                )
                .enrichHeaders(e -> e.headerFunction("kafka_messageKey", this::enrichHeaderKafkaMessageKey))
                .enrichHeaders(e-> e.header("kafka_topic", kafkaTopic))
                .channel("toKafka")
                .get();
    }

    public String enrichHeaderKafkaMessageKey(Message<String> message) {
        String clientId = MsgParser.getId(message.getPayload());
        return clientId;
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
                                c.ackMode(ContainerProperties.AckMode.COUNT)
                                        .id("toKafkaAck"))
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

    @Bean
    public KafkaMessageDrivenChannelAdapter<String, String> adapter(KafkaMessageListenerContainer kafkaListenerContainer) {
        KafkaMessageDrivenChannelAdapter<String, String> kafkaMessageDrivenChannelAdapter =
                new KafkaMessageDrivenChannelAdapter(kafkaListenerContainer, KafkaMessageDrivenChannelAdapter.ListenerMode.record);
        kafkaMessageDrivenChannelAdapter.setOutputChannelName("toMongo");
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

//    @Bean
//    @ServiceActivator(inputChannel = "toMongo")
//    public PollableChannel toMongo() {
//        QueueChannel queueChannel = new QueueChannel();
//        return queueChannel;
//    }


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
