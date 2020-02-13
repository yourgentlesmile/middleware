### _1、nosql是什么？_
    
泛指非关系型数据库，这些类型的数据存储不需要固定的模式，无需多余操作就可以横向扩展，优点易扩展，读写效率高，多样灵活的数据类型（mysql增删字段麻烦）

### _2、nosql优势？_

3V+3高：海量Volume、多样Variety、实时Velocity；高并发、高可用（横向扩展，一台机器不够再加）、高性能（单点故障，容灾备份）

IOE：IBM小型机、Oracle、EMC存储设备

### _3、数据库的ACID和CAP原理_

传统ACID：

A：Atomicity原子性
C：Consistency一致性
I：Isolation独立性
D：Durability持久性

经典CAP：

C：Consistency强一致性
A：Availability可用性（高可用）
P：Partition tolerance分区容错性（分布式容忍性）

### _4、Redis分布式内存数据库_

1. 是什么？

    REmote DIctionary Server（远程字典服务器），完全开源免费，C语言编写，遵守BSD协议，是一个高性能的k/v分布式内存数据库，基于内存运行，并支持持久化的nosql数据库，成为数据结构服务器，特点：
    > - 支持数据持久化，将内存中的数据保持在磁盘，重启的时候可以再次加载进行使用。
    > - 支持简单的k/v类型的数据，提供list、set、zset、hash等数据结构的存储。
    > - 支持数据的备份，即master-slave模式的数据备份

2. 能干嘛？

    > - 内存存储和持久化：redis支持异步将内存中的数据写到硬盘上，同事不影响继续服务
    > - 取最新N个数据


持久化和复制，RDB/AOF

默认端口6379

### _5、redis的五大数据类型_

> - String：一个key对应一个value，是二进制安全的eg.jpg图片或者序列化的对象，一个redis中自负床value最多可以是512M
> - Hash：redis hash是一个键值对集合，类似java的map<String,Object>
> - List（列表）：redis列表是简单的字符串列表，按照插入顺序排序，可添加一个元素到列表的头部（左边）或者尾部（右边），它的底层实际上是个链表
> - Set（集合）：是string类型的无序集合，通过hashtable实现
> - ZSet（sorted set：有序集合）：redis zset和set都是string类型元素的集合，且不允许重复的成员，**不同的是每个元素都会关联一个double类型的分数**，redis正式通过分数来为集合中的成员进行从小到大的排序，zset的成员是唯一的，**但分数score却可以重复**

### _6、过期时间策略_

> - Volatile-lru：最近最少使用，使用LRU算法移除key，支队设置了过期时间的键
> - Allkeys-lru：使用LRU算法移除key
> - Volatile-random：在过期集合中移除随机的key，只对设置了过期时间的键
> - Allkeys-random：移除随机的key
> - Volatile-ttl：移除哪些TTL值最小的key，即那些最近要过期的key
Noeviction：永不过期，针对写操作，只是返回错误信息

### _7、redis持久化_

> - **RDB(Redis DataBase)**：在指定的时间间隔内将内存中的数据集快照写入磁盘，也就是行话将的snapshot快照，它恢复时会将快照文件又直接读入内存中。redis会单独创建（fork）一个子进程来进行持久化，会将数据写入到整个过程中，主进程是不进行任何IO操作的，这就确保了极高的性能，如果需要进行大规模数据的恢复，且对于数据恢复的完整性不是非常敏感，那么RDB方式要比AOF方式更加的高效，RDB的缺点是最后一次持久化后的数据可能丢失。save命令立刻备份
> - **AOF(Append Only File)**：备份时间间隔为1秒，以日志的形式来记录每个写操作，将redis执行过的所有写指令记录下来，不记录读操作，只许追加文件但不可以改写文件，redis启动之初会读取该文件重新构建数据，换言之，redis重启的话就根据日志文件的内容将写指令从前到后执行一次以完成数据的恢复工作，**Rewrite**：aof文件越写越大，新增重写机制，当aof文件的大小超过所设定的阈值时，redis就会启动aof文件的内容压缩，只保留可以恢复数据的最小指令集，可以使用命令bgrewriteaof；**重写原理**：AOF文件持续增长而过大时，会fork出一条新进程

**redis启动先加载aof文件，恢复aof文件：redis-check-aof --fix appendonly.aof**

### _8、AOF和RDB的区别、策略_

参考7，AOF和RDB可共存，redis优先加载AOF文件。

### _9、redis的事务_

是什么：可以一次执行多个命令，本质时一组命令的集合，一个事务中所有命令都会序列化，**按顺序地串行化执行而不会被其他命令插入，不许加塞**。

能干嘛：一个队列中，一次性，顺序性，排他性的执行一系列命令。

怎么玩：MULTI开启,不一定开启成功;EXEC执行，DISCARD丢弃事务;WATCH监控一个或多个key，如果在事务执行之前这些key被其他命令所开动给，那么事务将被打断

**redis对事务的支持是部分支持**，在执行事务的过程就报错会牵连所有操作，但在执行事务时才报错则不会影响其他的操作


表锁、行锁，列锁，间隙锁

### _10、redis的发布订阅（了解）_

1. 概念：进程间的一种消息通信模式，发送者（pub）发送消息，订阅者（sub）接收消息

### _11、redis的主从复制_

1. 概念：主机数据更新后根据配置和策略，自动同步到备机的master/slave机制，master以写为主，slave以读为主

2. 如何使用？

> - 配从（库）不配主（库）
> - 从库配置：slaveof 主库ip 主库端口（每次与master断开之后，都需要重新连接，除非你配置进redis.conf文件）
> - 修改配置文件细节操作：拷贝多个(3个)redis.conf文件、开启daemonize yes、修改pid文件、log文件名字修改（logfile "6379"）、dump.rdb文件
> - 常用3招：

    一主二仆：从机读，主机写，如果从机写入和主机一样的会报错，实现读写分离，如果主机挂了，其他两台从机依旧是从机的身份，默认配置，主机重启依旧是主机，从机原地待命，但是如果从机挂掉之后，再重启，该从机会恢复成原来默认的master，需重新设置为master的从机才行

    薪火相传：上一个slave可以是下一个slave的master，slave同样可以接收其他slaves的连接和同步请求，那么该slave作为了链条中下一个的master，可以有效减轻master的写压力，中途变更转向：会清除之前的数据，重新简历拷贝最新的，SLAVEOF 新机ip:新机端口

    反客为主：SLAVEOF no one（将当前从机设置成为master）

info replication：查看是否为master || slaver的信息

SLAVEOF 127.0.0.1:6379：做6379的slave，作为备份

3. 复制原理：slave启动成功连接到master后会发送一个sync命令，

4. 哨兵模式：

> - 自定义sentinel.conf文件，名字不能错，vi此文件：sentinel monitor host6379 127.0.0.1 6379 1， 1表示主机挂掉后slave投票看谁成为新的主机，得票数多成为新主机，启动哨兵：Redis-sentinel /myredis/sentinel.conf

复制延时：

### _命令_

1. redis默认安装16个库,redis索引从0开始，选择库：select 0
2. 查看key的数量大小：DBSIZE
3. 罗列所有key：keys *（不推荐用*号，最好用具体的key值，或者keys k?会查看k开头的key）
4. 清除当前一个库的所有key：FLUSHDB
5. 清除所有库的所有key：FLUSHALL（不推荐，有风险）
6. 剪切（将k3这个key移动到2号库中）：move k3 2
7. 给key设置过期时间：EXPIRE key 秒钟
8. 查看还有多少秒过期，-1表示永不过期，-2表示已过期，过期会移除：ttl key
9. 查看key的类型：type key
10. 删除key：DEL key
11. INCR/decr/incrby/decrby：针对数字进行加减，一定要是数字
12. getrange/setrange（eg.GETRANGE k1 0 -1)：截取字符串
13. setex（set with expire） 键秒值/setnx（set if no exist）：设置过期时间
14. 合并设置值：mset k1 v1 k2 v2 k3 v3
15. 先get再set：getset
16. lpush/rpush/lrange：新增值，左边先进/新增值，右边先进/获取某个范围中的值，做右边出来（lrange k1 0 -1）
17. lpop/rpop（lpop list01）：每次出一个值，先出栈顶
18. lindex，按照索引下标获得元素（从上到下）：lindex list01 2
19. llen：获取列表长度
20. lrem key：删除n个value，lrem list01 2 3（删除2个3）
21. ltrim key：开始index，结束index，截取指定范围的值后再赋值给key，eg ltrim list1 3 5
22. rpoplpush：rpoplpush list01 list02 
23. lset key index value
24. linsert key before/after 值1 值2：把某个值插入某个key中

25. sadd/smembers/sismember：新增值/查看值/
26. scard：获取集合里面的元素个数
27. srem key value：删除集合中元素 srem set01 3 删除3
28.  srandmember key：某个整数（随机出几个数）
29. spop key：随机出栈
30. smove key1 key2 在key1里某个值：作用是将key1里的某个值赋给key2
31. 数学集合类：
    差集：sdiff（sdiff set01 set02）：在第一个集合里面并且不在第二个集合里

    交集：sinter

    并集：sunion

32. hset/hget/hmset/hmget/hgetall/hdel：eg hset user name z3(user为key，name和z3为value，并且是一个键值对)
