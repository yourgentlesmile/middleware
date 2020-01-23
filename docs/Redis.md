# Redis安装及运用

## 理论
Redis的网络请求模块使用了一个线程(所以不需考虑并发安全性)，即一个线程处理所有的网络请求，其他模块仍用了多个线程  
### Redis是单线程 + 多路IO复用技术  
多路复用是指使用一个线程来检查多个文件描述符(socket)的就绪状态，比如调用select和poll函数，传入多个文件描述符，如果有一个文件描述符就绪，则返回，否则阻塞知道超时。得到就绪状态后进行真正的操作，操作可以在同一个线程里执行，也可以启动线程执行  
> 串行 vs 多线程 + 锁 (memcached) vs 单线程 + 多路IO复用

### Redis Cluster 相关
引用：  
[Redis Cluster集群的实现原理] https://yq.aliyun.com/articles/711825  
[Redis集群实现原理探讨] https://segmentfault.com/p/1210000009708869/read  
#### 一、 Redis Cluster架构
  Redis Cluster是Redis在3.0版本推出的分布式解决方案。Redis Cluster由多个Redis节点组成。不同节点之间数据无交集，每个节点对应多个数据分片。节点内部分为主备节点，通过主备复制的方式保证数据的一致性。一个主节点可以有多个从节点，主节点提供读写服务，从节点提供读服务。

**1.1 内部通信机制**  
  Redis Cluster各节点之间通过Redis Cluster Bus交互信息。Redis Cluster每个节点都会记录集群的配置信息：如信息版本号Epoch、集群状态State等数据。  
  Redis Cluster的每个节点都保存着Node视角的集群结构。它描述了数据的分片方式，节点主备关系，并通过Epoch作为版本号实现集群结构信息的一致性，同时控制着数据迁移和故障转移的过程。  

**1.2 Redis Cluster去中心化**  
  在Redis Cluster中，原则上每个主节点都有一个或多个Slave节点。集群中所有的Master节点都可以进行读写数据，不分主次。每个主节点与从节点间通过Goossip协议内部通信，异步复制。但是异步复制会导致某些特定情况下的数据丢失。所以，Redis Cluster不能保证数据的强一致性。  
Redis Cluster设计的核心思想：数据拆分、去中心化。  

#### 二、 Redis Cluster数据分片

**2.1 数据分片规则**  
  在分布式集群中，如何保证相同请求落到相同的机器上，并且后面的集群机器可以尽可能的均分请求，需要使用合理的策略，有两种传统的方法：  
1、哈希算法：采用固定节点数量，当某一节点宕机，缓存重建。  
2、一致性哈希算法：当某一结点宕机，只有此节点数据受影响。会将压力压到数据库。  
  Redis Cluster使用的时hash slot算法通过采用固定节点数量和可配置映射节点，来避免取模的不灵活性和一致性哈希的部分影响。  
  Redis Cluster将所有数据按照hash slot算法分布到16384[0-16383]个哈希槽上面，哈希槽分布在各节点上，各节点维护自己的哈希槽。  

**2.2 客户端路由**  
  当Client访问的Key不在当前节点的哈希槽中时，Redis Cluster会返回moved命令，并告知正确路由信息。当Client接收到moved命令时，会再次请求Redis并更新其内部路由缓存信息。  
  当Redis Cluster在数据重新分布时，Redis Cluster会使用ask命令用于重定向。因为在数据重新分布时，某个哈希槽的数据可能同时存在于新旧两个节点。所以ask只会重定向，并不会更新路由信息。  

**2.3 数据分片迁移**  
  当有新主节点加入集群中、从集群中移除节点或者因数据分布不均衡时需要数据重新分布时，就需要对数据分片的迁移。数据迁移分为三个步骤：  
1、向目标节点发送状态变更命令，将目标节点的对应哈希槽状态置为importing。  
2、向源节点发送状态变更命令，将源节点对应的哈希槽状态置为migrating。  
3、针对源节点上的哈希槽的所有key，向源节点发送migrate命令，告知源节点将对应的key迁移到目标节点。  
当源节点的状态置为migrating后。此时源节点提供的服务和通常状态下有所区别：  
1、如果Client访问的key尚未迁出，则正常的处理该key；  
2、如果key已经迁出或者key不存在，则回复Client ASK，信息让其跳转到目标节点处理；  

当目标节点状态变成importing后。表示对应的slot正在向目标节点迁入。目标节点和通常情况下有所区别：  
1、对于该slot上所有非ask跳转的操作，目标节点不会进行操作，而是通过moved让Client跳转至源节点执行。这样就保证了同一个key在迁移之前总是在源节点执行。迁移后总是在目标节点执行，从而杜绝了双写的冲突。  
2、迁移过程中，新增加的key会在目标节点执行，源节点不会新增key。使得迁移有界限，可以在某个确定的时刻结束。  
  单个key的迁移过程可以通过原子化的migrate命令完成。对于源节点和目标节点的从节点，是通过主备复制，从而达到增删数据。当所有key迁移完成后，Client 通过Redis Cluster的setslot命令设置目标节点的分片信息，从而包含了迁入的slot。设置过程中会让Epoch自增，并且是Cluster中的最新值。然后通过相互感知，传播到Cluster 中的其他节点。  

#### 三、Redis Cluster故障转移

**3.1 节点故障判断**  
  首先，在Redis Cluster中每个节点都存有集群中所有节点的信息。它们之间通过互相ping-pong判断节点是否可以连接。如果有一半以上的节点去ping一个节点的时候没有回应，集群就认为这个节点宕机。

**3.2 slave选举**  
  当主节点被集群公认为fail状态，那么它的从节点就会发起竞选，如果存在多个从节点，数据越新的节点越有可能发起竞选。集群中其他主节点返回响应信息。

**3.3 结构变更**  
  当竞选从节点收到过半主节点同意，便会成为新的主节点。此时会以最新的Epoch通过PONG消息广播，让Redis Cluster的其他节点尽快的更新集群信息。当原主节点恢复加入后会降级为从节点。

#### 四、Redis Cluster高可用性

**4.1 主节点保护**  
  当集群中某节点中的所有从实例宕机时，Redis Cluster会将其他节点的非唯一从实例进行副本迁移，成为此节点的从实例。  
  这样集群中每个主节点至少有一个slave，使得Cluster 具有高可用。集群中只需要保持 2*master+1 个节点，就可以保持任一节点宕机时，故障转移后继续高可用。

**4.2 集群fail条件**  
Redis Cluster保证基本可用的特性，在达到一定条件时才会认定为fail：  
1、某个主节点和所有从节点全部挂掉，则集群进入fail状态。  
2、如果集群超过半数以上主节点挂掉，无论是否有从节点，集群进入fail状态。  
3、如果集群任意主节点挂掉,且当前主节点没有从节点，集群进入fail状态。  

## 安装
```shell
$ wget http://download.redis.io/releases/redis-5.0.7.tar.gz
$ tar xzf redis-5.0.7.tar.gz
$ cd redis-5.0.7
$ make
```
Redis编译需要以下组件，make，gcc  
进入redis目录后执行make  
```shell
出现以下，则代表未安装gcc
make[3]: Entering directory '/opt/redis-5.0.7/deps/hiredis'
gcc -std=c99 -pedantic -c -O3 -fPIC  -Wall -W -Wstrict-prototypes -Wwrite-strings -g -ggdb  net.c
make[3]: gcc: Command not found
make[3]: *** [Makefile:156: net.o] Error 127
make[3]: Leaving directory '/opt/redis-5.0.7/deps/hiredis'
make[2]: *** [Makefile:46: hiredis] Error 2
make[2]: Leaving directory '/opt/redis-5.0.7/deps'
make[1]: [Makefile:200: persist-settings] Error 2 (ignored)
    CC adlist.o
/bin/sh: 1: cc: not found
make[1]: *** [Makefile:248: adlist.o] Error 127
make[1]: Leaving directory '/opt/redis-5.0.7/src'
make: *** [Makefile:6: all] Error 2

```
```shell
In file included from adlist.c:34:
zmalloc.h:50:10: fatal error: jemalloc/jemalloc.h: No such file or directory
 #include <jemalloc/jemalloc.h>
```

这个错误是由于jemalloc重载了Linux下的ANSI C的malloc和free函数，make的时候添加`make MALLOC=libc`即可  
当出现`Hint: It's a good idea to run 'make test' ;`表示make成功  
进入src，目录，可以删除后缀名为`.c` `.h` `.o`的文件，使得目录整洁点  
## 配置
如果不指定配置文件启动，默认redis是bind127.0.0.1，端口是6379,这样会导致我们无法从外部访问redis。  
所以编辑在安装目录下的redis.conf，对redis进行一些配置  
1、将bind 127.0.0.1改为0.0.0.0  
2、将protected-mode 设置为no  
3、将daemonize 修改为yes。这样redis是后台启动
## 启动
启动时指定redis配置文件`./src/redis-server ./redis.conf`
## 数据类型
Redis支持5种数据类型：`string` `hash` `list` `set` `zset`

| 类型                 | 简介                                                   | 特性                                                         | 场景                                                         |
| :------------------- | :----------------------------------------------------- | :----------------------------------------------------------- | :----------------------------------------------------------- |
| String(字符串)       | 二进制安全                                             | 可以包含任何数据,比如jpg图片或者序列化的对象,一个键最大能存储512M | ---                                                          |
| Hash(字典)           | 键值对集合,即编程语言中的Map类型                       | 适合存储对象,并且可以像数据库中update一个属性一样只修改某一项属性值(Memcached中需要取出整个字符串反序列化成对象修改完再序列化存回去) | 存储、读取、修改用户属性                                     |
| List(列表)           | 链表(双向链表)                                         | 增删快,提供了操作某一段元素的API                             | 1,最新消息排行等功能(比如朋友圈的时间线) 2,消息队列          |
| Set(集合)            | 哈希表实现,元素不重复                                  | 1、添加、删除,查找的复杂度都是O(1) 2、为集合提供了求交集、并集、差集等操作 | 1、共同好友 2、利用唯一性,统计访问网站的所有独立ip 3、好友推荐时,根据tag求交集,大于某个阈值就可以推荐 |
| Sorted Set(有序集合) | 将Set中的元素增加一个权重参数score,元素按score有序排列 | 数据插入集合时,已经进行天然排序                              | 1、排行榜 2、带权重的消息队列                                |

### string
string 是redis最基本的类型，是二进制安全的，意思是redis的string可以包含任何数据，例如图片，序列化的对象。  
**注意：** string类型的值最大能储存**512MB**
### hash
hash是一个键值对集合，是string类型的field和value的映射表，hash特别适合用于存储对象  
**每个hash可以存储2^32 -1**个键值对(40多亿)
### List
字符串列表，按照插入顺序排序，可以添加一个元素到列表头部(左边)或者尾部(右边)  
**列表最多可存储2^32 - 1个元素，每个列表可存储40多亿**
### Set
Set是string类型的无序集合  
> 集合是通过哈希表实现的，所以添加，删除，查找的复杂度都是O(1)

集合中最大的成员数为 2^32 - 1(4294967295, 每个集合可存储40多亿个成员)
### zset (sorted set :有序集合)
zset 和 set 一样也是string类型元素的集合,且不允许重复的成员。  
不同的是每个元素都会关联一个double类型的分数。redis正是通过分数来为集合中的成员进行从小到大的排序。  
zset的成员是唯一的,但分数(score)却可以重复。  

## 操作

按数据类型进行分类

### 通用

| 命令      | 命令格式                                                    | 作用                                                         | 示例                      |
| --------- | ----------------------------------------------------------- | ------------------------------------------------------------ | ------------------------- |
| del       | `DEL KEY_NAME `                                             | 在key存在时删除key                                           | del kk                    |
| dump      | `DUMP KEY_NAME`                                             | 序列化给定key，并返回被序列化的值                            | dump kk                   |
| exists    | `EXISTS KEY_NAME`                                           | 检查给定key是否存在                                          | exists kk                 |
| expire    | `Expire KEY_NAME TIME_IN_SECONDS`                           | 给指定的key设置过期时间，单位为秒,(例子中，设置kk在60秒后过期) | expire kk 100             |
| expireat  | `Expireat KEY_NAME TIME_IN_UNIX_TIMESTAMP`                  | 接受的时间参数为unix时间，在指定的timestamp后过期            | expireat kk 1293840000    |
| pexpire   | `PEXPIRE key milliseconds`                                  | 设置key的过期时间以毫秒计                                    | pexpire kk 1000           |
| pexpireat | `PEXPIREAT KEY_NAME TIME_IN_MILLISECONDS_IN_UNIX_TIMESTAMP` | 接受的时间参数为unix时间(毫秒)，在指定的timestamp后过期      | expireat kk 1293840000000 |
| keys      | KEYS PATTERN                                                | 查找所有符合给定pattern的key                                 | keys k*                   |
| move      | MOVE KEY_NAME DESTINATION_DATABASE                          | 将当前数据库的key移动到给定的数据库db当中                    | move kk 1                 |
| persist   | PERSIST KEY_NAME                                            | 移除key的过期时间，key将永久保持                             | persist kk                |
| PTTL      | PTTL KEY_NAME                                               | 以毫秒为单位返回key的剩余过期时间                            | pttl kk                   |
| TTL       | TTL KEY_NAME                                                | 以秒为单位，返回给定key的剩余生存时间                        | ttl kk                    |
| randomkey | RANDOMKEY                                                   | 从当前数据库中随机返回一个key                                | randomkey                 |
| rename    | RENAME KEY NEWKEY                                           | 修改key的名称                                                | rename kk pp              |
| renamenx  | RENAMENX KEY NEWKEY                                         | 仅当newkey不存在时，将key改名为newkey                        | renamenx kk pp            |
| type      | TYPE KEY                                                    | 返回key所储存的值的类型                                      | type kk                   |

### string字符串

| 命令     | 命令格式                        | 作用                                                   | 示例                  |
| -------- | ------------------------------- | ------------------------------------------------------ | --------------------- |
| set      | set key_name value              | 设置指定key的值                                        | set kk "value"        |
| get      | get key_name                    | 获取指定key的值                                        | get kk                |
| getrange | get key_name start end          | 返回key中字符串的子字串                                | get kk 0 5            |
| getset   | getset key_name value           | 将给定key的值设置为value，并返回key的**旧值**          | getset kk "a"         |
| getbit   | getbit key_name                 | 对key所储存的字符串值，获取指定偏移量上的位bit         | getbit kk 6           |
| mget     | mget key1 key2....              | 获取所有(一个或者多个)给定的key的值                    | mget kk mm            |
| setbit   | setbit key_name offset value    | 对key所储存的字符串值，设置或清除指定偏移量上的位(bit) | setbit kk 10086 1     |
| setex    | setex key_name seconds value    | 将值value关联到key，并将key的过期时间设置为seconds     | setex kk 180 "1"      |
| setnx    | setnx key_name value            | 只有在key不存在的时候设置key的值                       | setnx kk "value"      |
| setrange | setrange key_name  offset value | 用value覆写给定key所储存的字符串值，从offset开始       | setrange kk 6 "Redis" |
| strlen   | strlen key_name                 | 返回key所储存的字符串值的长度                          | strlen kk             |

# 运维相关

## 重要，Redis集群配置
当写入大量数据的时候，并且每写入一个数据开启一个链接，之后立即断开，会引发redis链接不上的问题，比如报`unable to connect {ip}` `Cannot assign requested address`这一类的错误
这是由于客户端频繁的连服务器，由于每次连接都在很短的时间内结束，导致很多的TIME_WAIT，以至于用光了可用的端口号。  
因此，可以通过配置linux内核参数来解决这个问题  
写入文件  
`/etc/security/limits.conf`
`net.ipv4.tcp_syncookies = 1`  
`net.ipv4.tcp_tw_reuse = 1`  
`net.ipv4.tcp_tw_recycle = 1`  
`net.ipv4.tcp_fin_timeout = 30`  
配置说明：
net.ipv4.tcp_syncookies = 1 表示开启SYN Cookies。当出现SYN等待队列溢出时，启用cookies来处理，可防范少量SYN攻击，默认为0，表示关闭  
net.ipv4.tcp_tw_reuse = 1    表示开启重用。允许将TIME-WAIT sockets重新用于新的TCP连接，默认为0，表示关闭  
net.ipv4.tcp_tw_recycle = 1  表示开启TCP连接中TIME-WAIT sockets的快速回收，默认为0，表示关闭  
net.ipv4.tcp_fin_timeout=30修改系統默认的 TIMEOUT 时间  

# redis中 redis-trib 的使用
参考博客  
http://www.cnblogs.com/PatrickLiu/p/8484784.html?spm=5176.11156381.0.0.415979f5OT4Yjo  
redis-trib基本命令  
```
create          host1:port1 ... hostN:portN
              --replicas <arg>
check           host:port
info            host:port
fix             host:port
              --timeout <arg>
reshard         host:port
              --from <arg>
              --to <arg>
              --slots <arg>
              --yes
              --timeout <arg>
              --pipeline <arg>
rebalance       host:port
              --weight <arg>
              --auto-weights
              --use-empty-masters
              --timeout <arg>
              --simulate
              --pipeline <arg>
              --threshold <arg>
add-node        new_host:new_port existing_host:existing_port
              --slave
              --master-id <arg>
del-node        host:port node_id
set-timeout     host:port milliseconds
call            host:port command arg arg .. arg
import          host:port
              --from <arg>
              --copy
              --replace
```
create：创建集群  
check：检查集群  
info：查看集群信息  
fix：修复集群  
reshard：在线迁移slot  
rebalance：平衡集群节点slot数量  
add-node：将新节点加入集群  
del-node：从集群中删除节点  
set-timeout：设置集群节点间心跳连接的超时时间  
call：在集群全部节点上执行命令  
import：将外部redis数据导入集群  
## create 创建集群
redis-trib.rb  create  [--replicas <arg>]  host1:port1 ... hostN:portN  
Example : `./redis-trib.rb create --replicas 1 192.168.1.68:6001 192.168.1.68:6002 192.168.1.69:6003 192.168.1.69:6004 192.168.1.170:6005 192.168.1.170:6006`  
replicas 参数必须在host:port之前 代表着master主节点有多少个slaver从节点，如果省略，则最少会创建3个master主节点，也意味着redis集群至少要三个节点  
## check 检查集群
redis-trib.rb check host:port 这个地址可以是集群中任意一个地址
## info 查看集群信息
redis-trib.rb info 192.168.1.68:6001  
运行结果  
```
[root@localhost src]# ./redis-trib.rb info 192.168.1.68:6001
192.168.1.68:6001 (8bc31e0e...) -> 20 keys | 5461 slots | 1 slaves.
192.168.1.69:6003 (9726b0ff...) -> 117283 keys | 5462 slots | 1 slaves.
192.168.1.68:6002 (dd009699...) -> 15 keys | 5461 slots | 1 slaves.
[OK] 117318 keys in 3 masters.
7.16 keys per slot on average.
```
## fix 修复集群
`redis-trib.rb fix --timeout <arg> host:port  `
## reshard 迁移slot
此命令有以下几个子命令  
`--from <args>` 需要迁移的slot来自于哪些源节点,可以指定多个源节点，args参数是节点的node id,也能直接传递all，代表指定集群中所有节点  
`--to <arg>` 指定接收slot的节点，只能填写一个节点  
`--slots <arg>` 需要迁移的slot数量  
`--yes` 在打印完reshard计划后等待用户确认  
`--timeout <arg>` 设置migrate命令的超时时间  
`--pipeline <arg>` 定义cluster getkeysinslot命令一次取出的key数量，不传的话使用默认值为10