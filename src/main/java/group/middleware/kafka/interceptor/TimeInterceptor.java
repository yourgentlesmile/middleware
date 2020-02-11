package group.middleware.kafka.interceptor;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.internals.Topic;

import java.util.Map;

/**
 * @author orangeC
 * @description
 * @date 2020/2/11 17:47
 */
public class TimeInterceptor implements ProducerInterceptor<String,String> {

    @Override
    public void configure(Map<String, ?> map) {

    }
    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> producerRecord) {
        // 取出数据
        String value = producerRecord.value();

        // 创建一个新的ProducerRecord对象并返回

        return new ProducerRecord<String, String>(producerRecord.topic(), producerRecord.partition(),
                producerRecord.key(),System.currentTimeMillis() + "," + value);
    }

    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {

    }

    @Override
    public void close() {

    }


}
