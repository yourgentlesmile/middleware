package group.middleware.kafka.partitioner;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.internals.Topic;

import java.util.Map;

/**
 * @author orangeC
 * @description
 * @date 2020/2/11 14:28
 */
public class MyPartitioner implements Partitioner {

    public static void main(String[] args) {

    }

    @Override
    public int partition(String topic, Object o, byte[] bytes, Object o1, byte[] bytes1, Cluster cluster) {
        Integer integer = cluster.partitionCountForTopic(topic);


        return 1;
    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> map) {

    }
}
