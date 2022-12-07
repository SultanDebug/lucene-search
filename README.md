#### 代码结构

- analyzer

  > 分词器：
  >
  > 1.MyJianpinAnalyzer 简拼分词器
  >
  > 2.MyOnlyPinyinAnalyzer 根据ik分词后的拼音词项分词器，不包含中文词项
  >
  > 3.MyPinyinAnalyzer 根据ik分词后的拼音词项分词器，包含中文词项

- config

  > 索引配置
  >
  > 1.FieldDef 字段配置，字段分词类型、字段类型、存储类型等
  >
  > 2.IndexConfig 配置主包装类
  >
  > 3.IndexShardConfig 索引配置，分片数、索引名称、持久化地址等

- service

  > 索引管理及搜索
  >
  > 1.ShardIndexLoadService 单分片索引数据搜索
  >
  > 2.ShardIndexService 单分片索引数据管理，初始化、新增/修改、主/增量索引合并
  >
  > 3.IndexCommonService 多分片索引公共处理，索引初始化检查
  >
  > 4.ShardIndexMergeLoadService 分片索引数据并发搜索
  >
  > 5.ShardIndexMergeService 分片索引数据并发初始化、修改、索引合并


#### 测试用例

- 索引初始化：http://localhost:8888/shard/create?index=enterprise

  > GET请求，索引配置信息需要存在


- 索引查询：http://localhost:8888/shard/query?index=enterprise&query=li bai

  > GET请求，拼音目前只按空格分词

- 索引新增、修改：http://localhost:8888/shard/add/enterprise

  > POST请求，新增、修改逻辑是先根据id删除再新增
  >
  > {
  >  "id":"10003",
  >     "name":"李白公司"
  > }

- 索引合并：http://localhost:8888/shard/merge?index=enterprise

  > GET请求


#### 已有的功能

- 布尔查询

- 字段分分词器

- 拼音分词器

- 前缀查询，短语查询，拼音倒排，简拼查询

- 增量索引，索引合并

- 索引、字段配置化定义


#### 存在的问题

- 相关性计算未做深入验证，能否兼容现有打分模型未验证

- 范围查询未验证

- 宽表数据查询未做适配，这个相对处理简单

- 索引搜索未通用化，针对索引的搜索需要定制

#### 有轮子，用轮子，轮子太方，磨圆它，没有轮子，造轮子
