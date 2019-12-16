package biz.cits.mq;

import biz.cits.message.MsgGenerator;
import biz.cits.mq.producer.MqProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.jms.Destination;
import javax.jms.JMSProducer;
import java.util.ArrayList;
import java.util.Map;

@RestController
@RequestMapping("mq")
public class MqController {

    @Autowired
    MqProducer mqProducer;

    @Autowired
    Destination destination;

    @Autowired
    JMSProducer jmsProducer;


    @GetMapping(path = "send", produces = "application/json")
    public String sendMessages(@RequestParam int numMessage) {
        ArrayList<Map.Entry<String, String>> messages = MsgGenerator.getMessages(numMessage);
        messages.forEach((e) -> mqProducer.sendMessage(destination, jmsProducer, e.getValue()));
        return "done";
    }

}
