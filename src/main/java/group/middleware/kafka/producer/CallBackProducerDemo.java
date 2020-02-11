package group.middleware.kafka.producer;

import org.apache.kafka.clients.producer.*;

import java.util.Properties;

/**
 * @author orangeC
 * @description
 * @date 2020/2/11 12:02
 */
public class CallBackProducerDemo {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "hadoop102:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");

        KafkaProducer<String, String> producer = new KafkaProducer<String, String>
                (properties);
        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<String, String>("first",
                    "hello ---" + i), (recordMetadata, e) -> {
                        if (e == null) {
                            System.out.println(recordMetadata.partition() + "-- " +
                                    recordMetadata.offset());
                        } else {
                            e.printStackTrace();
                        }
                    });
        }
        producer.close();

    }
}
