package yunnex.sharding.bothwrite;


import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public class Executions {

    private SqlSessionFactory bothWriterSqlSessionFactory;

    private static Map<String, Method> executionMethod = new ConcurrentHashMap<>();

    public Object proxyExecute(Invocation invocation) {
        SqlSession sqlSession = bothWriterSqlSessionFactory.openSession();
        //执行双写  //如果配置异步 开启线程处理
        Object[] args = invocation.getArgs();
        try {
            Object res = invoke((MappedStatement) args[0], args[1], sqlSession);
            return invocation.proceed();
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected Object invoke(MappedStatement ms, Object parameter, SqlSession sqlSession) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String id = ms.getId();
        String interfaceMapper = id.substring(0, id.lastIndexOf("."));
        Object result = null;
        try{
            Class mapperClass = Class.forName(interfaceMapper);
            Object mapper = sqlSession.getMapper(mapperClass);
            //获取参数值
            Object[] paramValues = getParamValue(parameter);;
            Method method = getMethod(id, mapperClass, paramValues);
            result = method.invoke(mapper, paramValues);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            sqlSession.close();
        }
        return result;
    }

    private Object[] getParamValue(Object parameter){
        List<Object> paramValues = new ArrayList<>();
        if (parameter instanceof MapperMethod.ParamMap){
            MapperMethod.ParamMap paramMap = (MapperMethod.ParamMap) parameter;
            int count = 1;
            while (count <= paramMap.size() / 2){
                try {
                    paramValues.add(paramMap.get("param"+(count++)));
                }catch (BindingException e){
                    break;
                }
            }

        } else if (parameter instanceof DefaultSqlSession.StrictMap){
            DefaultSqlSession.StrictMap paramMap = (DefaultSqlSession.StrictMap) parameter;
            List list = (List) paramMap.get("list");
            paramValues.add(list);

        } else if (parameter != null){
            paramValues.add(parameter);
        }
        return paramValues.toArray();
    }

    private Method getMethod(String id,Class mapperClass,Object[] paramValues) throws NoSuchMethodException {
        //获取参数对应的class
        StringJoiner stringJoiner = new StringJoiner("_");
        String methodName = id.substring(id.lastIndexOf(".") + 1);
        stringJoiner.add(id);
        Class<?>[] classes = new Class[paramValues.length];
        for (int i = 0; i< paramValues.length;i++){
            classes[i] = paramValues[i].getClass();
            stringJoiner.add(classes[i].getSimpleName());
        }
        String key = stringJoiner.toString();
        Method method = executionMethod.get(key);
        if (method == null){
            synchronized (executionMethod){
                method = findMethodByMethodSignature(mapperClass, methodName, classes);
                if (method == null){
                    throw new NoSuchMethodException("No such method " + methodName + " in class " + mapperClass.getName());
                }
                executionMethod.put(key,method);
            }
        }
        return method;
    }

    private Method findMethodByMethodSignature(Class mapperClass,String methodName,Class<?>[] parameterTypes) throws NoSuchMethodException {
        //class参数为空 为无参数方法
        Method method = null;
        List<Method> finded = new ArrayList<Method>();
        for (Method m : mapperClass.getMethods()) {
            if (m.getName().equals(methodName)) {
                finded.add(m);
            }
        }
        if (parameterTypes.length == 0) {
            if (finded.isEmpty()) {
                throw new NoSuchMethodException("No such method " + methodName + " in class " + mapperClass.getName());
            }
            if (finded.size() > 1) {
                String msg = String.format("Not unique method for method name(%s) in class(%s), find %d methods.",
                        methodName, mapperClass.getName(), finded.size());
                throw new IllegalStateException(msg);
            }
            method= finded.get(0);
        }else{
            for (Method m : finded){
                int parameterCount = m.getParameterCount();
                if (parameterCount != parameterTypes.length) continue;
                Class<?>[] realParamTypes = m.getParameterTypes();
                for (int i = 0;i < realParamTypes.length;i++){
                    if (!realParamTypes[i].isAssignableFrom(parameterTypes[i])){
                        continue;
                    }
                }
                method = m;

            }
        }
        return method;
    }

    public void setBothWriterSqlSessionFactory(SqlSessionFactory bothWriterSqlSessionFactory) {
        this.bothWriterSqlSessionFactory = bothWriterSqlSessionFactory;
    }
}
