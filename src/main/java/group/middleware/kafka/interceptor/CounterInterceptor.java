package group.middleware.kafka.interceptor;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;

/**
 * @author orangeC
 * @description
 * @date 2020/2/11 17:51
 */
public class CounterInterceptor implements ProducerInterceptor<String, String> {

    int success;
    int error;

    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> producerRecord) {
        return producerRecord;
    }

    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {
        if (recordMetadata != null) {
            success++;
        } else {
            error++;
        }
    }

    @Override
    public void close() {
        System.out.println("success : " + success);
        System.out.println("error : " + error);
    }

    @Override
    public void configure(Map<String, ?> map) {

    }
}
