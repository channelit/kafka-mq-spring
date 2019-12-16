package biz.cits.message;

import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class MsgConsumer {


    public Message<?> consumeMessage(Message<?> message) {
        System.out.println(message);
        return message;
//        HashMap<String, String> record = message.getPayload();
//            HashMap<String, String> records = new HashMap<>();
//            records.put(record.key(), record.value());
//            dataStore.storeData(record.key(), records);
//            System.out.println("Record Key " + record.key());
//            System.out.println("Record value " + record.value());
//            System.out.println("Record partition " + record.partition());
//            System.out.println("Record offset " + record.offset());
//        }
    }
}
