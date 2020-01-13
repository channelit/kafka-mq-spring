package biz.cits;

import biz.cits.message.MsgParser;
import com.amazonaws.services.s3.AmazonS3;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.integration.kafka.dsl.KafkaProducerMessageHandlerSpec;
import org.springframework.integration.kafka.inbound.KafkaMessageDrivenChannelAdapter;
import org.springframework.integration.mongodb.outbound.MongoDbStoringMessageHandler;
import org.springframework.integration.mongodb.store.MongoDbChannelMessageStore;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.kafka.support.converter.MessagingMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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

    @Autowired
    AmazonS3 s3client;

    @Value("${kafka.key}")
    String kafkaMessageKey;

    @Value("${kafka.topic}")
    String kafkaTopic;

    @Value("${app.awsServices.bucketName}")
    String bucketName;

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
                .transform(Message.class, m -> {
                    m.getPayload();
                    UUID uuid = UUID.randomUUID();
                    s3client.putObject(bucketName, uuid.toString(), m.getPayload().toString());
                    Message<String> newMessage = MessageBuilder.withPayload(uuid.toString())
                            .copyHeaders(m.getHeaders())
                            .build();
                    return newMessage;
                })
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
                .transform(Message.class, m -> {
                    String uuid = m.getPayload().toString();
                    uuid = uuid.substring(uuid.lastIndexOf("$") + 1);
                    String msg = "";
                    InputStream is = s3client.getObject(bucketName, uuid).getObjectContent();
                    try {
                        msg = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Message<String> newMessage = MessageBuilder.withPayload(String.format("{code:'%s'}", msg))
                            .copyHeaders(m.getHeaders())
                            .build();
                    return newMessage;
                })
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
                .headerMapper(mapper())
                .topicExpression("headers[kafka_topic] ?: '" + kafkaTopic + "'")
                .messageKeyExpression("headers[kafka_messageKey]")
                .configureKafkaTemplate(t -> t.id("kafkaTemplate:" + kafkaTopic));
    }

    @Bean
    public KafkaMessageDrivenChannelAdapter<String, String> adapter(KafkaMessageListenerContainer kafkaListenerContainer) {
        KafkaMessageDrivenChannelAdapter<String, String> kafkaMessageDrivenChannelAdapter =
                new KafkaMessageDrivenChannelAdapter(kafkaListenerContainer, KafkaMessageDrivenChannelAdapter.ListenerMode.record);
        MessagingMessageConverter messagingMessageConverter = new MessagingMessageConverter() {
            @Override
            public Message<?> toMessage(ConsumerRecord<?, ?> record, Acknowledgment acknowledgment, Consumer<?, ?> consumer, Type type) {
                Message<?> message = super.toMessage(record, acknowledgment, consumer, type);
                return MessageBuilder.fromMessage(message).build();
            }
        };
        kafkaMessageDrivenChannelAdapter.setMessageConverter(messagingMessageConverter);
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

    @Bean
    @Autowired
    public MongoDbChannelMessageStore mongoDbChannelMessageStore(MongoDbFactory mongoDbFactory) {
        return new MongoDbChannelMessageStore(mongoDbFactory);
    }

    @Bean
    @ServiceActivator(inputChannel = "toMongo")
    @Autowired
    public MongoDbStoringMessageHandler mongoDbOutboundAdapter(MongoDbFactory mongoDbFactory) {
        MongoDbStoringMessageHandler handler = new MongoDbStoringMessageHandler(mongoDbFactory);
        handler.setCollectionNameExpression(new LiteralExpression("FIFO"));
        return handler;
    }

    @Bean
    @ServiceActivator(inputChannel = "xxxx")
    public MessageHandler handler() {
        return message -> System.out.println(message.getPayload());
    }
}
