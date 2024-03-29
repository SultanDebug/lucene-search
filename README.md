#### 代码结构

- com.bird.search.analyzer

  > 分词器：
  >
  > 1.MyJianpinAnalyzer 简拼分词器
  >
  > 2.MyOnlyPinyinAnalyzer 根据ik分词后的拼音词项分词器，不包含中文词项
  >
  > 3.MyPinyinAnalyzer 根据ik分词后的拼音词项分词器，包含中文词项
  >
  > 4.MySingleCharAnalyzer 单字符分词
  >
  > 5.MySpecialCharAnalyzer 根据自定义字符分词

- com.bird.search.config

  > 索引配置
  >
  > 1.FieldDef 字段配置，字段分词类型、字段类型、存储类型等
  >
  > 2.IndexConfig 配置主包装类
  >
  > 3.IndexShardConfig 索引配置，分片数、索引名称、持久化地址等

- com.bird.search.service

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


- 索引查询：http://localhost:8888/shard/query?filter={"industry_code_3":["C27200000","E49900000"],"found_years":[{"min":20,"max":25,"left":false,"right":true},{"min":1,"max":5,"left":true,"right":false}],"reg_capi":[{"min":2000,"max":3001,"left":false,"right":true}]}&index=enterprise&type=complex&query=江苏&page=0&size=3&explain=true

  > GET请求，拼音目前只按空格分词
  > 条件示例，构建JsonObject直接序列化即可
  > index 索引名称
  > query 搜索词
  > filter 过滤条件，json串  格式详见文档
  > size 页大小
  > page 页数
  > explain 解释  true-是   false-否
  > type 查询方式：detail-companyid查询  prefix-精确查询，前缀、term  fuzzy-模糊查询  complex-复合查询，拼音短语及汉字单字大部分匹配


- 索引新增、修改：http://localhost:8888/shard/add/enterprise

  > POST请求，新增、修改逻辑是先根据id删除再新增
  >
  > {
  >  "id":"10003",
  >  "name":"黄震强公司"
  > }

- 索引合并：http://localhost:8888/shard/merge?index=enterprise

  > GET请求，暂不完善


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
- 索引刷新以及索引合并未做定时刷新

#### v20230103版本
* 解决的问题
  * 加权查询，区分字段命中
  * 模糊查询，基于编辑距离
  * 范围过滤
* 存在的问题
  * 索引构建分词，标准化是否统一处理，模糊查询验证大部分单字term召回方案【解决】
  * 标准化处理分词，标准化不处理分词【分词器解决】
  * 搜索，分词按字段，比如邮箱@字符，索引需要全量保留，搜索也需要全量保留搜索【分词器解决】

#### v20230114版本
* 功能改动
  * 索引动态切换
  * 结果集排序
  * 模糊查询按单字召回
* 解决的问题：
  * 英文单字符【通过标准分词器已解决】
  * 结果集太多，companyscore有太低，导致排序靠后，例：瑞浦贸易（上海）有限公司【暂忽略】
* 问题
  * 单字重复，相关性差，例：江苏司生建设有限公司
* 性能测试
  * > 接口总数： 1000 ,正确： 981 ,失败： 19 ,异常： 0 ,总花费： 92751.62482261658 ,平均花费： 92.75162482261658 ,最小花费： 21.50130271911621 ,最大花费： 624.7780323028564
  * > 打索引同时请求，1亿多数据，开发环境32c 128g 耗时59分钟，请求正常，
    > cpu稳定时50%，内存最多75%，gc为每一千万手动gc，停顿时间31秒,gc完内存占用30%左右
  * > 并发测试:10并发，每个线程1000请求
    > 新接口总数： 10000 ,正确： 9855 ,失败： 145 ,异常： 0 ,总花费： 3126195.4839229584 ,平均花费： 312.6195483922958 ,最小花费： 332.2129249572754 ,最大花费： 20466.35150909424

#### v20230215版本
* 功能改动
  * 字段完善
  * 英文名称分词完善
  * 模糊搜索单字召回加条件过滤通用化
* 问题
  * 条件过滤值归一化问题，待处理方案确认


#### v20230221版本
* 功能改动
  * 条件过滤通用化，复合查询及优化
  * 伪分页支持
* 问题
  * 性能问题，效果问题