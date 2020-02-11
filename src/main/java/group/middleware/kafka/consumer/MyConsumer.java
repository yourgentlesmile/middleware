package group.middleware.kafka.consumer;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/**
 * @author orangeC
 * @description
 * @date 2020/2/11 15:41
 */
public class MyConsumer {
    public static void main(String[] args) {
        Properties properties = new Properties();
        // 连接的集群
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "hadoop102:9092");
        // 自动提交，如果设置为false，则每次消费者启动都会从初次offset开始进行消费，但是启动之后会一直获取新的offset（前提是该消费者未重启）
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        // 自动提交offset的延时
        properties.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        // key、value的反序列化
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        //消费者组
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "bigdata");
        // 重置消费者的offset，生效条件：1.更换消费者组 2.数据过了七天，已经过期，满足以上两个条件，会重置消费者的offset为最初的0
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(properties);

        // 订阅主题
        consumer.subscribe(Arrays.asList("first", "second"));

        while (true) {
            // 订阅主题后获取数据
            ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String,String> consumerRecord: consumerRecords) {
                System.out.println(consumerRecord.key() + "---" + consumerRecord.value());
            }
            // 手动提交，同步提交，当前线程会阻塞直到offset获取成功
            consumer.commitSync();

            // 异步提交，由其他对象去提交，剩余线程依旧会走while循环拉取新的数据
            consumer.commitAsync(new OffsetCommitCallback() {
                @Override
                public void onComplete(Map<TopicPartition, OffsetAndMetadata> offset, Exception e) {
                    if (e == null) {
                        System.out.println("commit failed for " + offset);
                    }
                }
            });
        }
    }
}
