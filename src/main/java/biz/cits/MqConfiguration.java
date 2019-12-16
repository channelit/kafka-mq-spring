package biz.cits;

import com.ibm.mq.jms.MQQueue;
import com.ibm.msg.client.jms.JmsConnectionFactory;
import com.ibm.msg.client.jms.JmsFactoryFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;

@Configuration
@Order(2)
public class MqConfiguration implements ExceptionListener {

    @Value("${mq.host}")
    private String host;

    private Integer port = 1414;

    @Value("${mq.channel}")
    private String channel;

    @Value("${mq.qmgr}")
    private String QMGR;

    @Value("${mq.app.user}")
    private String appUser;

    @Value("${mq.app.pswd}")
    private String appPswd;

    @Value("${mq.app.name}")
    private String appName;

    @Value("${mq.queue.name}")
    private String queueName;

    @Bean
    public CachingConnectionFactory externalSourceMqFactory() throws JMSException {
        JmsFactoryFactory ff = JmsFactoryFactory.getInstance(WMQConstants.WMQ_PROVIDER);
        JmsConnectionFactory cf = ff.createConnectionFactory();
        cf.setStringProperty(WMQConstants.WMQ_HOST_NAME, host);
        cf.setIntProperty(WMQConstants.WMQ_PORT, port);
        cf.setStringProperty(WMQConstants.WMQ_CHANNEL, channel);
        cf.setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT);
        cf.setStringProperty(WMQConstants.WMQ_QUEUE_MANAGER, QMGR);
        cf.setStringProperty(WMQConstants.WMQ_APPLICATIONNAME, appName);
        cf.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
        cf.setStringProperty(WMQConstants.USERID, appUser);
        cf.setStringProperty(WMQConstants.PASSWORD, appPswd);
        CachingConnectionFactory ccf = new CachingConnectionFactory();
        ccf.setSessionCacheSize(1);
        ccf.setCacheProducers(false);
        ccf.setCacheConsumers(false);
        ccf.setTargetConnectionFactory(cf);
        ccf.setExceptionListener(this);
        return ccf;
    }

    @Override
    public void onException(JMSException e) {
        e.printStackTrace();
    }

    @Bean
    public DefaultMessageListenerContainer externalSourceMqListenerContainer(@Qualifier("externalSourceMqQueue") MQQueue externalSourceMqQueue, @Qualifier("externalSourceMqFactory") ConnectionFactory externalSourceMqFactory) {
        DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
        container.setExceptionListener(this);
        container.setConnectionFactory(externalSourceMqFactory);
        container.setDestination(externalSourceMqQueue);
        container.setSessionTransacted(true);
        container.setAutoStartup(true);
        container.setConcurrentConsumers(1);
        return container;
    }

    @Bean
    public MQQueue externalSourceMqQueue() throws JMSException {
        MQQueue queue = new MQQueue();
        queue.setBaseQueueName(queueName);
        queue.setMQMDMessageContext(WMQConstants.WMQ_MDCTX_SET_ALL_CONTEXT);
        queue.setMQMDReadEnabled(true);
        return queue;
    }
}
