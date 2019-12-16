package biz.cits;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.kafka.outbound.KafkaProducerMessageHandler;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.transformer.MessageTransformingHandler;
import org.springframework.integration.transformer.MethodInvokingTransformer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.PollableChannel;

@Configuration
public class IntegrationConfiguration {

    @Bean
    public IntegrationFlow received() {
        return IntegrationFlows.from(externalSourceMqChannel())
                .handle(fromKafka())
                .get();

    }

    private MessageChannel externalSourceMqChannel() {
        return MessageChannels.queue(1).get();
    }

    @Bean
    public MessageChannel receivedInAdapter() {
        return new DirectChannel();
    }

    @Transformer(inputChannel = "receivedInAdapter")
    @Bean
    public MessageHandler transformer() {
        return null;
    }

    @ServiceActivator(inputChannel = "inputToKafka")
    @Bean
    public MessageHandler serviceActivator(@Qualifier("kafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        KafkaProducerMessageHandler<String, String> handler =
                new KafkaProducerMessageHandler<>(kafkaTemplate);
        handler.setMessageKeyExpression(new LiteralExpression("this.properties.getMessageKey()"));
        return handler;
    }

    @Bean
    public PollableChannel fromKafka() {
        return new QueueChannel();
    }

}
