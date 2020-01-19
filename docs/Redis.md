# Redis安装及运用
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
