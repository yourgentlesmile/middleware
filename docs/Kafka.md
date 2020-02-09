1、消息队列的两种模式

> - 点对点模式（一对一，消费者主动拉去数据，消息收到后消息清除），主动拉取数据的话消费者需要维护长轮询，消耗资源
> - 发布/订阅模式（一对多，消费者消费数据之后不会清除消息），推送数据的话生产者需要维护一个消费者的列表以供按照每个消费者的消费速度主动推送给每个消费者

2、安装kafka，修改server.properties

	broker.id=0
	delete.topic.enable=true
	log.dirs=/opt/module/kafka/data
	zookeeper.connect=hadoop101:2181
	
3、启动kafka（-daemon启动守护进程）

	bin/kafka-server-start.sh -daemon config/server.properties

4、群启kafka脚本

    ```xml
        #!bin/bash

        case $1 in
        "start"){
            for i in hadoop102 hadoop103 hadoop104
            do
                echo "************$1**********"
                ssh $i "/opt/module/kafka/bin/kafka-server-start.sh -daemon /opt/module/kafka/config/server.properties
            done
        };;;

        "stop"){
            for i in hadoop102 hadoop103 hadoop104
            do
                echo "************$1**********"
                ssh $i "/opt/module/kafka/bin/kafka-server-stop.sh  /opt/module/kafka/config/server.properties
            done
        };;;

        esac
    ```

5、查看进程

    xcall.sh jps

6、topic

查看topic：bin/kafka-topics.sh --list --zookeeper hadoop102:2181

创建topic：bin/kafka-topics/sh --create --zookeeper hadoop102:2181 --topic first --partitions 2 --replication-factor 2

**partitions分区数为2
replication-factor副本数为2，小于等于broker数量，副本数作为备份**

删除topic：bin/kafka-topics.sh --delete --zookeeper hadoop102:2181 --topic first

查看topic详情：bin/kafka-topics.sh --describe --topic first --zookeeper hadoop102:2181


7、启动生产者控制台

bin/kafka-console-producer.sh --topic first --broker-list hadoop102:9092

8、启动消费者控制台模式

bin/kafka-console-consumer.sh --topic first --zookeeper hadoop102:2181

**kafka 0.9版本以前存在zookeeper中，0.9版本以后存在本地kafka，使用boostrap-server 替代zookeeper（bin/kafka-console-consumer.sh --topic first --boostrap-servereper hadoop102:2181）**

9、启动消费者控制台从一开始的消息开始查看

bin/kafka-console-consumer.sh --topic first --boostrap-servereper hadoop102:2181 --from-beginning
**.log文件中的数据保存最大时间为7天**

10、kafka工作流程

> - leader和follower统共两个副本，且follower和leader一定不在同一台broker
> - producer发送消息入kafka集群leader中，leader同步最新消息入follwer，consumer消费leader中的消息

    kafka中消息是以topic进行分类的，生产者生产消息，消费者消费消息，都是面向topic的，topic是逻辑上的概念，而partition分区时物理上的概念，每个partition对应于一个log文件，该log文件中存储的就是producer生产的数据，producer生产的数据会被不断追加到该log文件末端，且每条数据都有自己的offset，消费者组中的每个消费者，都会实时记录自己消费到了哪个offset，以便出错恢复时，从上次的为止继续消费

    一个topic分为多个partition，一个partition分为多个segment，一个segment对应两个文件.log、.index文件，由于生产者生产的雄安锡会不断追加到log文件末尾，为防止log文件过大导致数据定位效率低下，kafka采取了分片和索引机制，将每个partition分为多个segment，每个segment对应两个文件——.index文件和.log文件，这些文件位于一个文件夹下，该文件的命名规则为：topic名称+分区序号，eg.first-0，index和log文件以当前segment的第一条消息的offset命名，index文件中存储偏移量和数据大小，以此来查找log文件中的数据

11、kafka生产者

1. 分区策略

    分区原因
    > - 方便再集群中扩展，每个partition可以通过调整以适应它所在的机器，而一个topic又可以有多个partition组成，因此整个集群就可以适应任意大小的数据了
    > - 可以提高并发，因为可以以patition为单位读写

    分区原则
    > - 指明partition的情况下，直接指明的值直接作为patition值
    > - 没有指明partition值但有key的情况下，将key的hash值与topic的partition数进行取余得到partition值
    > - 既没有partition值又没有key值的情况下，第一次调用时随机生成一个整数（后面每次调用在这个整数上自增），将这个值与topic可用的partition总数取余得到partition值，也就是常说的round-robin（轮询）算法

12、数据可靠性保证（ack为确认消息）

为保证producer发送的数据，能可靠的发送到指定的topic，topic的每个partition收到producer发送的数据后，都需要像producer发送ack（acknowledge 确认收到），如果producer收到ack，就会进行下一轮的发送，否则重新发送数据

1. 半数以上完成同步，就发送ack， 优点 延迟低，缺点 选举新的leader时，容忍n台节点的故障，需要2n+1个副本
2. 全部完成同步，才发送ack， 优点 选举新的leader时，容忍n台节点的故障，需要n+1个副本，缺点 延迟高

kafka选择了第二种方案，原因：
> - 同样为了容忍n台节点的故障，第一种方案需要2n+1个副本，而第二种方案只需要n+1个副本，而kafka的每个分区都有大量的数据，第一种方案会造成大量数据的冗余
> - 虽然第二种那个方案的网络延迟会比较高，但网络延迟对kafka的影响较小

**ISR（同步队列，为了将来选举新的leader）**

采用第二种方案之后，设想以下情景：leader收到数据，所有follower开始同步数据，但有一个follower，因为某种故障，迟迟不能与leader进行同步，那leader就要一直等下去，直到它完成同步，才能发送ack，如何解决？

    leader维护一个动态的in-sync replica set（ISR），意为和leader保持同步的followe集合，当ISR中的followe完成数据的同步之后，leader就会给follower发送ack，如果follower长时间未向leader同步数据，则该follower将被踢出ISR，该时间阈值由replica.lag.time.max.ms（默认时间10秒钟）参数设定，leader发生故障之后，就会从ISR中选举新的leader

**0.9版本移除数据大小配置的参数，保留默认时间配置的参数**