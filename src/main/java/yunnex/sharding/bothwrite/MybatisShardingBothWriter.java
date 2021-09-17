package yunnex.sharding.bothwrite;

import cn.hutool.core.util.ReflectUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisShardingBothWriter extends InstantiationAwareBeanPostProcessorAdapter {

    @Autowired
    private ShardingSphereDataSource shardingSphereDataSource;

    private Object mapperLocations;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SqlSessionFactory) {
            SqlSessionFactory sqlSessionFactory = (SqlSessionFactory) bean;
            sqlSessionFactory.getConfiguration().addInterceptor(shardingAdapterInterceptor());
        }
        if (bean instanceof SqlSessionFactoryBean) {
            mapperLocations = ReflectUtil.getFieldValue(bean, "mapperLocations");
        }

        return bean;
    }

    private ShardingAdapterInterceptor shardingAdapterInterceptor() {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(shardingSphereDataSource);
        factoryBean.setMapperLocations((org.springframework.core.io.Resource[]) mapperLocations);
        ShardingAdapterInterceptor shardingAdapterInterceptor = new ShardingAdapterInterceptor();
        try {
            shardingAdapterInterceptor.setExecutions(executions(factoryBean.getObject()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return shardingAdapterInterceptor;
    }

    public void setShardingSphereDataSource(ShardingSphereDataSource shardingSphereDataSource) {
        this.shardingSphereDataSource = shardingSphereDataSource;
    }

    private Executions executions(SqlSessionFactory sqlSessionFactory) {
        Executions executions = new Executions();
        executions.setBothWriterSqlSessionFactory(sqlSessionFactory);
        return executions;
    }
}
