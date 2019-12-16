package biz.cits;

import org.aspectj.bridge.IMessageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.jms.dsl.Jms;
import org.springframework.integration.kafka.outbound.KafkaProducerMessageHandler;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.*;

@Configuration
@Order(1)
public class IntegrationConfiguration {

    @Autowired
    DefaultMessageListenerContainer externalSourceMqListenerContainer;

    @Bean
    public IntegrationFlow received() {
        return IntegrationFlows.from(Jms.messageDrivenChannelAdapter(externalSourceMqListenerContainer)
                .autoStartup(true)
                .extractPayload(true))
                .channel("externalSourceMqChannel")
                .enrichHeaders(eh -> eh.header("MessageType", "demo", true))
                .channel("toMongoChannel")
                .get();
    }

    private MessageChannel externalSourceMqChannel() {
        return MessageChannels.queue(1).get();
    }

    @ServiceActivator(inputChannel = "toKafkaChannel")
    @Bean
    public MessageHandler serviceActivator(@Qualifier("kafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        KafkaProducerMessageHandler<String, String> handler =
                new KafkaProducerMessageHandler<>(kafkaTemplate);
        handler.setMessageKeyExpression(new LiteralExpression("this.properties.getMessageKey()"));
        return handler;
    }

    @Bean("toMongoChannel")
    public PollableChannel toMongoChannel() {
        return new QueueChannel();
    }

}
