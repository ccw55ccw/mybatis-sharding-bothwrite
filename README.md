# mybatis-sharding-bothwrite

## dependency
- 目前最新版sharing jdbc 5.0.0-beta

## description
在原项目基础上增加分库分表双写功能，达到平滑过渡的目的

## usage
```
<dependency>
    <groupId>xxx.sharding</groupId>
    <artifactId>mybatis-sharding-bothwrite</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

spirng.xml:
```
<bean id="mybatisShardingBothWriter" class="yunnex.sharding.bothwrite.MybatisShardingBothWriter">
    <property name="shardingSphereDataSource" ref="shardingSphereDataSource"></property>
</bean>
```
