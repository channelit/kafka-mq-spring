package biz.cits;

import biz.cits.message.MsgParser;
import org.bson.json.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.CompositeStringExpression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.Transformers;
import org.springframework.integration.expression.DynamicExpression;
import org.springframework.integration.expression.FunctionExpression;
import org.springframework.integration.expression.ValueExpression;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.jms.dsl.Jms;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.integration.kafka.dsl.KafkaProducerMessageHandlerSpec;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.kafka.outbound.KafkaProducerMessageHandler;
import org.springframework.integration.mongodb.outbound.MongoDbOutboundGateway;
import org.springframework.integration.mongodb.outbound.MongoDbStoringMessageHandler;
import org.springframework.integration.mongodb.store.MongoDbChannelMessageStore;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.kafka.support.converter.BytesJsonMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
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
                .enrichHeaders(e -> e.header("kafka_topic", kafkaTopic))
                .channel("toKafka")
                .get();
    }

    public String enrichHeaderKafkaMessageKey(Message<String> message) {
        String clientId = MsgParser.getId(message.getPayload());
        return clientId;
    }

    @Bean
    public IntegrationFlow fromKafkaFlow() {
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
    @Autowired
    public KafkaProducerMessageHandlerSpec<String, String, ?> kafkaMessageHandler(ProducerFactory<String, String> producerFactory) {
        return Kafka
                .outboundChannelAdapter(producerFactory)
                .messageKey(m -> {
                    System.out.println(m.getPayload());
                    return
                    m
                            .getHeaders()
                            .get(IntegrationMessageHeaderAccessor.SEQUENCE_NUMBER);
                })
                .headerMapper(mapper())
                .topicExpression("headers[kafka_topic] ?: '" + kafkaTopic + "'")
                .messageKeyExpression("headers[kafka_messageKey]")
                .configureKafkaTemplate(t -> t.id("kafkaTemplate:" + kafkaTopic));
    }

    @Bean
    public KafkaMessageDrivenChannelAdapter<String, String> adapter(KafkaMessageListenerContainer kafkaListenerContainer) {
        KafkaMessageDrivenChannelAdapter<String, String> kafkaMessageDrivenChannelAdapter =
                new KafkaMessageDrivenChannelAdapter(kafkaListenerContainer, KafkaMessageDrivenChannelAdapter.ListenerMode.record);
        kafkaMessageDrivenChannelAdapter.setOutputChannelName("toMongo");
        kafkaMessageDrivenChannelAdapter.setMessageConverter(new BytesJsonMessageConverter());
        kafkaMessageDrivenChannelAdapter.setPayloadType(String.class);
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
    @ServiceActivator(inputChannel = "xxxx")
    @Autowired
    public MongoDbStoringMessageHandler mongoDbOutboundAdapter(MongoDbFactory mongoDbFactory) {
        MongoDbStoringMessageHandler handler = new MongoDbStoringMessageHandler(mongoDbFactory);
        handler.setCollectionNameExpression(new LiteralExpression("FIFO"));
        return handler;
    }

    @Bean
    @ServiceActivator(inputChannel = "toMongo")
    public MessageHandler handler() {
        return message -> System.out.println(message.getPayload());
    }
}
