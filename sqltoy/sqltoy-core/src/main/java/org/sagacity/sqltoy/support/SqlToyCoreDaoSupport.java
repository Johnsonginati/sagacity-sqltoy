/**
 * @Copyright 2009 版权归陈仁飞，不要肆意侵权抄袭，如引用请注明出处保留作者信息。
 */
package org.sagacity.sqltoy.support;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.DataSourceCallbackHandler;
import org.sagacity.sqltoy.callback.InsertRowCallbackHandler;
import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.*;
import org.sagacity.sqltoy.dialect.DialectFactory;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.executor.UniqueExecutor;
import org.sagacity.sqltoy.model.*;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.translate.TranslateHandler;
import org.sagacity.sqltoy.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author renfei.chen <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:SqlToyDaoSupport.java,Revision:v4.0,Date:2012-6-1
 * @project sagacity-sqltoy4.0
 * @description sqltoy的对外服务层, 基础Dao支持工具类，用于被继承扩展自己的Dao
 * @publish Copyright@2009 版权归陈仁飞,反对任何不尊重版权的抄袭，如引用请注明出处。
 * @modify Date:2012-8-8 {增强对象级联查询、删除、保存操作机制,不支持2层以上级联}
 * @modify Date:2012-8-23 {新增loadAll(List entities) 方法，可以批量通过主键取回详细信息}
 * @modify Date:2014-12-17 {1、增加sharding功能,改进saveOrUpdate功能，2、采用merge
 * into策略;3、优化查询 条件和查询结果，变为一个对象，返回结果支持json输出}
 * @modify Date:2016-3-07 {优化存储过程调用,提供常用的执行方式,剔除过往复杂的实现逻辑和不必要的兼容性,让调用过程更加可读 }
 * @modify Date:2016-11-25
 * {增加了分页优化功能,缓存相同查询条件的总记录数,在一定周期情况下无需再查询总记录数,从而提升分页查询的整体效率 }
 * @modify Date:2017-7-13 {增加saveAllNotExist功能,批量保存数据时忽视已经存在的,避免重复性数据主键冲突}
 * @modify Date:2017-11-1 {增加对象操作分库分表功能实现,精简和优化代码}
 * @modify Date:2019-3-1 {增加通过缓存获取Key然后作为查询条件cache-arg 功能，从而避免二次查询或like检索}
 * @modify Date:2019-6-25 {将异常统一转化成RuntimeException,不在方法上显式的抛异常}
 * @modify Date:2020-4-5 {分页PaginationModel中设置skipQueryCount=true跳过查总记录,默认false}
 */
//新的模式不鼓励自己继承DaoSupport,一般情况下使用SqlToyLazyDao即可
@SuppressWarnings("rawtypes")
public class SqlToyCoreDaoSupport {
    /**
     * 定义日志
     */
    protected final Logger logger = LoggerFactory.getLogger(SqlToyCoreDaoSupport.class);

    /**
     * 数据源
     */
    protected DataSource dataSource;

    /**
     * sqlToy上下文定义
     */
    protected SqlToyContext sqlToyContext = SqlToyContext.getInstance();

    /**
     * 各种数据库方言实现
     */
    private DialectFactory dialectFactory = DialectFactory.getInstance();

    // @Autowired(required = false)
    // @Qualifier(value = "dataSource")
    // update 2020-07-11 剔除Autowired采用新的ObtainDataSource策略便于在多数据库场景下可以自由适配
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @param dataSource
     * @return
     * @todo 获取数据源, 如果参数dataSource为null则返回默认的dataSource
     */
    public DataSource getDataSource(DataSource dataSource) {
        DataSource result = dataSource;
        if (null == result) {
            result = this.dataSource;
        }
        if (null == result) {
            result = sqlToyContext.obtainDataSource();
        }
        return result;
    }

    /**
     * @param dataSource
     * @param sqltoyConfig
     * @return
     * @TODO 获取sql对应的dataSource
     */
    private DataSource getDataSource(DataSource dataSource, SqlToyConfig sqltoyConfig) {
        // 第一、接口调用时直接指定的数据源
        DataSource result = dataSource;
        // 第二、sql指定的数据源
        if (null == result && (null != sqltoyConfig && null != sqltoyConfig.getDataSource())) {
            result = sqlToyContext.getDataSourceBean(sqltoyConfig.getDataSource());
        }
        // 第三、自动注入的数据源
        if (null == result) {
            result = this.dataSource;
        }
        // 第四、sqltoyContext默认的数据源
        if (null == result) {
            result = sqlToyContext.obtainDataSource();
        }
        return result;
    }

//	/**
//	 * @param sqlToyContext the sqlToyContext to set
//	 */
//	@Autowired
//	@Qualifier(value = "sqlToyContext")
//	public void setSqlToyContext(SqlToyContext sqlToyContext) {
//		this.sqlToyContext = sqlToyContext;
//	}

    /**
     * @return the sqlToyContext
     */
    public SqlToyContext getSqlToyContext() {
        return sqlToyContext;
    }

    /**
     * @param sqlKey
     * @param sqlType
     * @return
     * @todo 获取sqlId 在sqltoy中的配置模型
     */
    protected SqlToyConfig getSqlToyConfig(final String sqlKey, final SqlType sqlType) {
        return sqlToyContext.getSqlToyConfig(sqlKey, sqlType, getDialect(null));
    }

    /**
     * @param entity
     * @param paramsNamed
     * @return
     * @todo 判断数据库中数据是否唯一，true 表示唯一(可以插入)，false表示不唯一(数据库已经存在该数据)，用法
     * isUnique(dictDetailVO,new
     * String[]{"dictTypeCode","dictName"})，将会根据给定的2个参数
     * 通过VO取到相应的值，作为组合条件到dictDetailVO对应的表中查询记录是否存在
     */
    protected boolean isUnique(final Serializable entity, final String... paramsNamed) {
        return isUnique(new UniqueExecutor(entity, paramsNamed));
    }

    /*
     * @see isUnique(final Serializable entity, final String[] paramsNamed)
     */
    protected boolean isUnique(final UniqueExecutor uniqueExecutor) {
        return dialectFactory.isUnique(sqlToyContext, uniqueExecutor,
                this.getDataSource(uniqueExecutor.getDataSource()));
    }

    /**
     * @param sqlOrNamedQuery
     * @param paramsNamed
     * @param paramsValue
     * @return Long
     * @todo 获取数据库查询语句的总记录数
     */
    protected Long getCountBySql(final String sqlOrNamedQuery, final String[] paramsNamed, final Object[] paramsValue) {
        return getCountByQuery(new QueryExecutor(sqlOrNamedQuery, paramsNamed, paramsValue));
    }

    /**
     * @param queryExecutor
     * @return
     * @todo 指定数据源查询记录数量
     */
    protected Long getCountByQuery(final QueryExecutor queryExecutor) {
        QueryExecutorExtend extend = queryExecutor.getInnerModel();
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(extend.sql, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        return dialectFactory.getCountBySql(sqlToyContext, queryExecutor, sqlToyConfig,
                this.getDataSource(extend.dataSource, sqlToyConfig));
    }

    protected StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamValues,
                                       final Integer[] outParamsType, final Class resultType) {
        return executeStore(storeSqlOrKey, inParamValues, outParamsType, resultType, null);
    }

    protected StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamValues) {
        return executeStore(storeSqlOrKey, inParamValues, null, null, null);
    }

    /**
     * @param storeSqlOrKey 可以直接传call storeName (?,?) 也可以传xml中的存储过程sqlId
     * @param inParamsValue
     * @param outParamsType (可以为null)
     * @param resultType    VOClass,HashMap或null(表示二维List)
     * @param dataSource
     * @return
     * @todo 通用存储过程调用, 一般数据库{?=call xxxStore(? in,? in,? out)} 针对oracle数据库只能{call
     * xxxStore(? in,? in,? out)} 同时结果集必须通过OracleTypes.CURSOR out 参数返回
     * 目前此方法只能返回一个结果集(集合类数据),可以返回多个非集合类数据，如果有特殊用法，则自行封装调用
     */
    protected StoreResult executeStore(final String storeSqlOrKey, final Object[] inParamsValue,
                                       final Integer[] outParamsType, final Class resultType, final DataSource dataSource) {
        SqlToyConfig sqlToyConfig = getSqlToyConfig(storeSqlOrKey, SqlType.search);
        return dialectFactory.executeStore(sqlToyContext, sqlToyConfig, inParamsValue, outParamsType, resultType,
                this.getDataSource(dataSource, sqlToyConfig));
    }

    protected Object getSingleValue(final String sqlOrNamedSql, final String[] paramsNamed,
                                    final Object[] paramsValue) {
        return getSingleValue(sqlOrNamedSql, paramsNamed, paramsValue, null);
    }

    /**
     * @param sqlOrNamedSql
     * @param paramsNamed
     * @param paramsValue
     * @param dataSource
     * @return
     * @todo 返回单行单列值，如果结果集存在多条数据则返回null
     */
    protected Object getSingleValue(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
                                    final DataSource dataSource) {
        Object queryResult = loadByQuery(
                new QueryExecutor(sqlOrNamedSql, paramsNamed, paramsValue).dataSource(dataSource));
        if (null != queryResult) {
            return ((List) queryResult).get(0);
        }
        return null;
    }

    /**
     * @param entity
     * @return
     * @todo 根据给定的对象中的主键值获取对象完整信息
     */
    protected <T extends Serializable> T load(final T entity) {
        if (entity == null) {
            return null;
        }
        EntityMeta entityMeta = this.getEntityMeta(entity.getClass());
        if (SqlConfigParseUtils.isNamedQuery(entityMeta.getLoadSql(null))) {
            return (T) this.loadBySql(entityMeta.getLoadSql(null), entity);
        }
        return load(entity, null, null);
    }

    /**
     * @param entity
     * @param lockMode
     * @return
     * @todo 提供锁定功能的加载
     */
    protected <T extends Serializable> T load(final T entity, final LockMode lockMode) {
        return load(entity, lockMode, null);
    }

    /**
     * @param entity
     * @param lockMode
     * @param dataSource
     * @return
     * @todo <b>根据主键值获取对应的记录信息</b>
     */
    protected <T extends Serializable> T load(final T entity, final LockMode lockMode, final DataSource dataSource) {
        return dialectFactory.load(sqlToyContext, entity, null, lockMode, this.getDataSource(dataSource));
    }

    /**
     * @param entity
     * @param lockMode
     * @param cascadeTypes
     * @return
     * @todo 指定需要级联加载的类型，通过主对象加载自身和相应的子对象集合
     */
    protected <T extends Serializable> T loadCascade(T entity, LockMode lockMode, Class... cascadeTypes) {
        if (entity == null) {
            return null;
        }
        Class[] cascades = cascadeTypes;
        // 当没有指定级联子类默认全部级联加载(update 2020-7-31 缺失了cascades.length == 0 判断)
        if (cascades == null || cascades.length == 0) {
            cascades = sqlToyContext.getEntityMeta(entity.getClass()).getCascadeTypes();
        }
        return dialectFactory.load(sqlToyContext, entity, cascades, lockMode, this.getDataSource(null));
    }

    /**
     * @param entities
     * @param lockMode
     * @return
     * @todo 批量根据实体对象的主键获取对象的详细信息
     */
    protected <T extends Serializable> List<T> loadAll(final List<T> entities, final LockMode lockMode) {
        return dialectFactory.loadAll(sqlToyContext, entities, null, lockMode, this.getDataSource(null));
    }

    /**
     * @param entities
     * @param lockMode
     * @param cascadeTypes
     * @return
     * @todo 批量对象级联加载, 指定级联加载的子表
     */
    protected <T extends Serializable> List<T> loadAllCascade(final List<T> entities, final LockMode lockMode,
                                                              final Class... cascadeTypes) {
        if (entities == null || entities.isEmpty()) {
            return entities;
        }
        Class[] cascades = cascadeTypes;
        if (cascades == null || cascades.length == 0) {
            cascades = sqlToyContext.getEntityMeta(entities.get(0).getClass()).getCascadeTypes();
        }
        return dialectFactory.loadAll(sqlToyContext, entities, cascades, lockMode, this.getDataSource(null));
    }

    /**
     * @param sqlOrNamedSql
     * @param paramNames
     * @param paramValues
     * @param resultType
     * @return
     * @todo 根据sql语句查询并返回单个VO对象(可指定自定义对象, sqltoy根据查询label跟对象的属性名称进行匹配映射)
     */
    protected <T> T loadBySql(final String sqlOrNamedSql, final String[] paramNames, final Object[] paramValues,
                              final Class<T> resultType) {
        QueryExecutor query = new QueryExecutor(sqlOrNamedSql, paramNames, paramValues);
        if (resultType != null) {
            query.resultType(resultType);
        }
        return (T) loadByQuery(query);
    }

    /**
     * @param sql
     * @param entity
     * @return
     * @todo 解析sql中:named 属性到entity对象获取对应的属性值作为查询条件,并将查询结果以entity的class类型返回
     */
    protected <T extends Serializable> T loadBySql(final String sql, final T entity) {
        return (T) loadByQuery(new QueryExecutor(sql, entity));
    }

    /**
     * TODO 通过构造QueyExecutor 提供更加灵活的参数传递方式，包括DataSource 比如:
     * <p>
     * 1、new QueryExecutor(sql,entity).dataSource(dataSource) 2、new
     * QueryExecutor(sql).names(paramNames).values(paramValues).resultType(resultType);
     * </p>
     *
     * @param queryExecutor
     * @return
     */
    protected Object loadByQuery(final QueryExecutor queryExecutor) {
        QueryExecutorExtend extend = queryExecutor.getInnerModel();
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        QueryResult result = dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, null,
                this.getDataSource(extend.dataSource, sqlToyConfig));
        List rows = result.getRows();
        if (rows != null && rows.size() > 0) {
            return rows.get(0);
        }
        return null;
    }

    /**
     * @todo 执行无条件的sql语句,一般是一个修改、删除等操作，并返回修改的记录数量
     * @param sqlOrNamedSql
     * @return
     */
    protected Long executeSql(final String sqlOrNamedSql) {
        return executeSql(sqlOrNamedSql, null, null, false, null);
    }

    /**
     * @todo 解析sql中的参数名称，以此名称到entity中提取对应的值作为查询条件值执行sql
     * @param sqlOrNamedSql
     * @param entity
     * @param reflectPropertyHandler 用来批量设置某个属性的值,一般设置为null即可
     * @return
     */
    protected Long executeSql(final String sqlOrNamedSql, final Serializable entity,
                              final ReflectPropertyHandler reflectPropertyHandler) {
        SqlToyConfig sqlToyConfig = getSqlToyConfig(sqlOrNamedSql, SqlType.update);
        // 根据sql中的变量从entity对象中提取参数值
        Object[] paramValues = BeanUtil.reflectBeanToAry(entity, sqlToyConfig.getParamsName(), null,
                reflectPropertyHandler);
        return executeSql(sqlOrNamedSql, sqlToyConfig.getParamsName(), paramValues, false, null);
    }

    protected Long executeSql(final String sqlOrNamedSql, final Map<String, Object> paramsMap) {
        NamedValuesModel model = CollectionUtil.mapToNamedValues(paramsMap);
        return executeSql(sqlOrNamedSql, model.getNames(), model.getValues(), false, null);
    }

    /**
     * @todo 执行无返回结果的SQL(返回updateCount)
     * @param sqlOrNamedSql
     * @param paramsNamed
     * @param paramsValue
     */
    protected Long executeSql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue) {
        return executeSql(sqlOrNamedSql, paramsNamed, paramsValue, false, null);
    }

    /**
     * @todo 执行无返回结果的SQL(返回updateCount),根据autoCommit设置是否自动提交
     * @param sqlOrNamedSql
     * @param paramsNamed
     * @param paramsValue
     * @param autoCommit    是否自动提交
     * @param dataSource
     */
    protected Long executeSql(final String sqlOrNamedSql, final String[] paramsNamed, final Object[] paramsValue,
                              final Boolean autoCommit, final DataSource dataSource) {
        final SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sqlOrNamedSql, SqlType.update,
                getDialect(dataSource));
        return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, paramsNamed, paramsValue, autoCommit,
                this.getDataSource(dataSource, sqlToyConfig));
    }

    /**
     * @todo 批量执行sql修改或删除操作(返回updateCount)
     * @param sqlOrNamedSql
     * @param dataSet
     * @param reflectPropertyHandler 反调函数(一般不需要)
     * @param autoCommit
     */
    protected Long batchUpdate(final String sqlOrNamedSql, final List dataSet,
                               final ReflectPropertyHandler reflectPropertyHandler, final Boolean autoCommit) {
        // 例如sql 为:merge into table update set xxx=:param
        // dataSet可以是VO List,可以根据属性自动映射到:param
        return batchUpdate(sqlOrNamedSql, dataSet, sqlToyContext.getBatchSize(), reflectPropertyHandler, null,
                autoCommit, null);
    }

    /**
     * @TODO 批量执行sql修改或删除操作(返回updateCount)
     * @param sqlOrNamedSql
     * @param dataSet
     * @param reflectPropertyHandler
     * @param insertCallhandler
     * @param autoCommit
     */
    protected Long batchUpdate(final String sqlOrNamedSql, final List dataSet,
                               final ReflectPropertyHandler reflectPropertyHandler, final InsertRowCallbackHandler insertCallhandler,
                               final Boolean autoCommit) {
        return batchUpdate(sqlOrNamedSql, dataSet, sqlToyContext.getBatchSize(), reflectPropertyHandler,
                insertCallhandler, autoCommit, null);
    }

    /**
     * @todo 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用
     * @param sqlOrNamedSql
     * @param dataSet
     * @param batchSize
     * @param insertCallhandler
     * @param autoCommit
     */
    protected Long batchUpdate(final String sqlOrNamedSql, final List dataSet, final int batchSize,
                               final InsertRowCallbackHandler insertCallhandler, final Boolean autoCommit) {
        return batchUpdate(sqlOrNamedSql, dataSet, batchSize, null, insertCallhandler, autoCommit, null);
    }

    /**
     * @todo 批量执行sql修改或删除操作
     * @param sqlOrNamedSql
     * @param dataSet
     * @param batchSize
     * @param reflectPropertyHandler
     * @param insertCallhandler
     * @param autoCommit
     * @param dataSource
     */
    protected Long batchUpdate(final String sqlOrNamedSql, final List dataSet, final int batchSize,
                               final ReflectPropertyHandler reflectPropertyHandler, final InsertRowCallbackHandler insertCallhandler,
                               final Boolean autoCommit, final DataSource dataSource) {
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sqlOrNamedSql, SqlType.update,
                getDialect(dataSource));
        return dialectFactory.batchUpdate(sqlToyContext, sqlToyConfig, dataSet, batchSize, reflectPropertyHandler,
                insertCallhandler, autoCommit, getDataSource(dataSource, sqlToyConfig));
    }

    protected boolean wrapTreeTableRoute(final TreeTableModel treeModel) {
        return wrapTreeTableRoute(treeModel, null);
    }

    /**
     * @param treeModel
     * @param dataSource
     * @return
     * @todo 构造树形表的节点路径、节点层级、节点类别(是否叶子节点)
     */
    protected boolean wrapTreeTableRoute(final TreeTableModel treeModel, final DataSource dataSource) {
        return dialectFactory.wrapTreeTableRoute(sqlToyContext, treeModel, this.getDataSource(dataSource));
    }

    /**
     * @param sql
     * @param entity
     * @return
     * @todo 以entity对象的属性给sql中的:named 传参数，进行查询，并返回entityClass类型的集合
     */
    protected <T extends Serializable> List<T> findBySql(final String sql, final T entity) {
        return (List<T>) findByQuery(new QueryExecutor(sql, entity)).getRows();
    }

    /**
     * @param <T>
     * @param sql
     * @param paramsNamed
     * @param paramsValue
     * @param voClass     分null(返回二维List)、voClass、HashMap.class、LinkedHashMap.class等
     * @return
     * @TODO 查询集合
     */
    protected <T> List<T> findBySql(final String sql, final String[] paramsNamed, final Object[] paramsValue,
                                    final Class<T> voClass) {
        QueryExecutor query = new QueryExecutor(sql, paramsNamed, paramsValue);
        if (voClass != null) {
            query.resultType(voClass);
        }
        return (List<T>) findByQuery(query).getRows();
    }

    protected QueryResult findByQuery(final QueryExecutor queryExecutor) {
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        return dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, null,
                this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
    }

    /**
     * @param paginationModel
     * @param queryExecutor
     * @return
     * @todo 以QueryExecutor 封装sql、参数等条件，实现分页查询
     */
    protected QueryResult findPageByQuery(final PaginationModel paginationModel, final QueryExecutor queryExecutor) {
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        // 跳过查询总记录数量
        if (paginationModel.getSkipQueryCount() != null && paginationModel.getSkipQueryCount()) {
            return dialectFactory.findSkipTotalCountPage(sqlToyContext, queryExecutor, sqlToyConfig,
                    paginationModel.getPageNo(), paginationModel.getPageSize(),
                    this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
        }
        return dialectFactory.findPage(sqlToyContext, queryExecutor, sqlToyConfig, paginationModel.getPageNo(),
                paginationModel.getPageSize(),
                this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
    }

    /**
     * @param paginationModel
     * @param sql
     * @param paramsNamed
     * @param paramsValue
     * @param voClass(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
     * @return
     * @todo 指定sql和参数名称以及名称对应的值和返回结果的类型(类型可以是java.util.HashMap), 进行分页查询
     * sql可以是一个具体的语句也可以是xml中定义的sqlId
     */
    protected <T> PaginationModel<T> findPageBySql(final PaginationModel paginationModel, final String sql,
                                                   final String[] paramsNamed, final Object[] paramsValue, Class<T> voClass) {
        QueryExecutor query = new QueryExecutor(sql, paramsNamed, paramsValue);
        if (voClass != null) {
            query.resultType(voClass);
        }
        return (PaginationModel<T>) findPageByQuery(paginationModel, query).getPageResult();
    }

    protected <T extends Serializable> PaginationModel<T> findPageBySql(final PaginationModel paginationModel,
                                                                        final String sql, final T entity) {
        return (PaginationModel<T>) findPageByQuery(paginationModel, new QueryExecutor(sql, entity)).getPageResult();
    }

    /**
     * @param sql
     * @param paramsNamed
     * @param paramsValue
     * @param voClass(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
     * @param topSize
     * @return
     * @todo 取符合条件的结果前多少数据, topSize>1 则取整数返回记录数量，topSize<1 则按比例返回结果记录(topSize必须是大于0)
     */
    protected <T> List<T> findTopBySql(final String sql, final String[] paramsNamed, final Object[] paramsValue,
                                       final Class<T> voClass, final double topSize) {
        QueryExecutor query = new QueryExecutor(sql, paramsNamed, paramsValue);
        if (voClass != null) {
            query.resultType(voClass);
        }
        return (List<T>) findTopByQuery(query, topSize).getRows();
    }

    protected <T extends Serializable> List<T> findTopBySql(final String sql, final T entity, final double topSize) {
        return (List<T>) findTopByQuery(new QueryExecutor(sql, entity), topSize).getRows();
    }

    protected QueryResult findTopByQuery(final QueryExecutor queryExecutor, final double topSize) {
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        return dialectFactory.findTop(sqlToyContext, queryExecutor, sqlToyConfig, topSize,
                this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
    }

    /**
     * @param queryExecutor
     * @param randomCount
     * @return
     * @todo 在符合条件的结果中随机提取多少条记录, randomCount>1 则取整数记录，randomCount<1 则按比例提取随机记录
     * 如randomCount=0.05 总记录数为100,则随机取出5条记录
     */
    protected QueryResult getRandomResult(final QueryExecutor queryExecutor, final double randomCount) {
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        return dialectFactory.getRandomResult(sqlToyContext, queryExecutor, sqlToyConfig, randomCount,
                this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig));
    }

    // voClass(null则返回List<List>二维集合,HashMap.class:则返回List<HashMap<columnLabel,columnValue>>)
    protected <T> List<T> getRandomResult(final String sqlOrNamedSql, final String[] paramsNamed,
                                          final Object[] paramsValue, Class<T> voClass, final double randomCount) {
        QueryExecutor query = new QueryExecutor(sqlOrNamedSql, paramsNamed, paramsValue);
        if (voClass != null) {
            query.resultType(voClass);
        }
        return (List<T>) getRandomResult(query, randomCount).getRows();
    }

    protected void truncate(final Class entityClass, final Boolean autoCommit) {
        if (null == entityClass) {
            throw new IllegalArgumentException("entityClass is null!Please enter the correct!");
        }
        truncate(sqlToyContext.getEntityMeta(entityClass).getTableName(), autoCommit, null);
    }

    /**
     * @param tableName
     * @param autoCommit
     * @param dataSource
     * @todo <b>快速删除表中的数据,autoCommit为null表示按照连接的默认值(如dbcp可以配置默认是否autoCommit)</b>
     */
    protected void truncate(final String tableName, final Boolean autoCommit, final DataSource dataSource) {
        this.executeSql("truncate table ".concat(tableName), null, null, autoCommit, this.getDataSource(dataSource));
    }

    /**
     * @param entity
     * @return
     * @todo 保存对象数据(返回插入的主键值), 会针对对象的子集进行级联保存
     */
    protected Object save(final Serializable entity) {
        return this.save(entity, null);
    }

    /**
     * @param entity
     * @param dataSource
     * @return
     * @todo <b>指定数据库插入单个对象并返回主键值,会针对对象的子表集合数据进行级联保存</b>
     */
    protected Object save(final Serializable entity, final DataSource dataSource) {
        return dialectFactory.save(sqlToyContext, entity, this.getDataSource(dataSource));
    }

    /**
     * @param entities
     * @todo 批量插入对象(会自动根据主键策略产生主键值, 并填充对象集合), 不做级联操作
     */
    protected <T extends Serializable> Long saveAll(final List<T> entities) {
        return this.saveAll(entities, null, null);
    }

    /**
     * @param entities
     * @param reflectPropertyHandler
     * @todo 批量保存对象, 并可以通过反调函数对插入值进行灵活干预修改
     */
    protected <T extends Serializable> Long saveAll(final List<T> entities,
                                                    final ReflectPropertyHandler reflectPropertyHandler) {
        return this.saveAll(entities, reflectPropertyHandler, null);
    }

    /**
     * @param entities
     * @param reflectPropertyHandler
     * @param dataSource
     * @todo <b>指定数据库进行批量插入</b>
     */
    protected <T extends Serializable> Long saveAll(final List<T> entities,
                                                    final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource) {
        return dialectFactory.saveAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), reflectPropertyHandler,
                this.getDataSource(dataSource), null);
    }

    /**
     * @param entities
     * @return
     * @todo 保存对象数据(返回插入的主键值), 忽视已经存在的
     */
    protected <T extends Serializable> Long saveAllIgnoreExist(final List<T> entities) {
        return this.saveAllIgnoreExist(entities, null, null);
    }

    /**
     * @param entities
     * @param dataSource
     * @return
     * @todo 保存对象数据(返回插入的主键值), 忽视已经存在的
     */
    protected <T extends Serializable> Long saveAllIgnoreExist(final List<T> entities, final DataSource dataSource) {
        return this.saveAllIgnoreExist(entities, null, dataSource);
    }

    /**
     * @param entities
     * @param reflectPropertyHandler
     * @param dataSource
     * @todo 保存对象数据(返回插入的主键值), 忽视已经存在的
     */
    protected <T extends Serializable> Long saveAllIgnoreExist(final List<T> entities,
                                                               final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource) {
        return dialectFactory.saveAllIgnoreExist(sqlToyContext, entities, sqlToyContext.getBatchSize(),
                reflectPropertyHandler, this.getDataSource(dataSource), null);
    }

    /**
     * @param entity
     * @param forceUpdateProps
     * @todo update对象(值为null的属性不修改, 通过forceUpdateProps指定要进行强制修改属性)
     */
    protected Long update(final Serializable entity, final String... forceUpdateProps) {
        return this.update(entity, forceUpdateProps, null);
    }

    /**
     * @param entity
     * @param forceUpdateProps
     * @param dataSource
     * @todo <b>根据传入的对象，通过其主键值查询并修改其它属性的值</b>
     */
    protected Long update(final Serializable entity, final String[] forceUpdateProps, final DataSource dataSource) {
        return dialectFactory.update(sqlToyContext, entity, forceUpdateProps, false, null, null,
                this.getDataSource(dataSource));
    }

    /**
     * @param entity
     * @param forceUpdateProps
     * @param forceCascadeClasses      (强制需要修改的子对象,当子集合数据为null,则进行清空或置为无效处理,否则则忽视对存量数据的处理)
     * @param subTableForceUpdateProps
     * @todo 修改对象, 并通过指定级联的子对象做级联修改
     */
    protected Long updateCascade(final Serializable entity, final String[] forceUpdateProps,
                                 final Class[] forceCascadeClasses, final HashMap<Class, String[]> subTableForceUpdateProps) {
        return dialectFactory.update(sqlToyContext, entity, forceUpdateProps, true, forceCascadeClasses,
                subTableForceUpdateProps, this.getDataSource(null));
    }

    /**
     * @param entity
     * @todo 深度更新实体对象数据, 根据对象的属性值全部更新对应表的字段数据, 不涉及级联修改
     */
    protected Long updateDeeply(final Serializable entity) {
        return this.updateDeeply(entity, null);
    }

    /**
     * @param entity
     * @param dataSource
     * @todo <b>深度修改,即对象所有属性值都映射到数据库中,如果是null则数据库值被改为null</b>
     */
    protected Long updateDeeply(final Serializable entity, final DataSource dataSource) {
        return this.update(entity, sqlToyContext.getEntityMeta(entity.getClass()).getRejectIdFieldArray(),
                this.getDataSource(dataSource));
    }

    /**
     * @param entities
     * @param forceUpdateProps
     * @todo 批量根据主键更新每条记录, 通过forceUpdateProps设置强制要修改的属性
     */
    protected <T extends Serializable> Long updateAll(final List<T> entities, final String... forceUpdateProps) {
        return this.updateAll(entities, null, forceUpdateProps, null);
    }

    protected <T extends Serializable> Long updateAll(final List<T> entities,
                                                      final ReflectPropertyHandler reflectPropertyHandler, final String... forceUpdateProps) {
        return this.updateAll(entities, reflectPropertyHandler, forceUpdateProps, null);
    }

    /**
     * @param entities
     * @param forceUpdateProps
     * @param reflectPropertyHandler
     * @param dataSource
     * @todo <b>指定数据库,通过集合批量修改数据库记录</b>
     */
    protected <T extends Serializable> Long updateAll(final List<T> entities,
                                                      final ReflectPropertyHandler reflectPropertyHandler, final String[] forceUpdateProps,
                                                      final DataSource dataSource) {
        return dialectFactory.updateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), forceUpdateProps,
                reflectPropertyHandler, this.getDataSource(dataSource), null);
    }

    /**
     * @param entities
     * @param reflectPropertyHandler
     * @todo 批量深度修改(参见updateDeeply, 直接将集合VO中的字段值修改到数据库中, 未null则置null)
     */
    protected <T extends Serializable> Long updateAllDeeply(final List<T> entities,
                                                            final ReflectPropertyHandler reflectPropertyHandler) {
        return updateAllDeeply(entities, reflectPropertyHandler, null);
    }

    /**
     * @param entities
     * @param reflectPropertyHandler
     * @param dataSource
     * @todo 指定数据源进行批量深度修改(对象属性值为null则设置表对应的字段为null)
     */
    protected <T extends Serializable> Long updateAllDeeply(final List<T> entities,
                                                            final ReflectPropertyHandler reflectPropertyHandler, final DataSource dataSource) {
        if (entities == null || entities.isEmpty()) {
            return 0L;
        }
        return updateAll(entities, reflectPropertyHandler,
                this.getEntityMeta(entities.get(0).getClass()).getRejectIdFieldArray(), null);
    }

    protected Long saveOrUpdate(final Serializable entity, final String... forceUpdateProps) {
        return this.saveOrUpdate(entity, forceUpdateProps, null);
    }

    /**
     * @param entity
     * @param forceUpdateProps
     * @param dataSource
     * @todo 指定数据库, 对对象进行保存或修改，forceUpdateProps:当修改操作时强制修改的属性
     */
    protected Long saveOrUpdate(final Serializable entity, final String[] forceUpdateProps,
                                final DataSource dataSource) {
        return dialectFactory.saveOrUpdate(sqlToyContext, entity, forceUpdateProps, this.getDataSource(dataSource));
    }

    /**
     * @param entities
     * @param forceUpdateProps
     * @todo 批量保存或修改，并指定强迫修改的字段属性
     */
    protected <T extends Serializable> Long saveOrUpdateAll(final List<T> entities, final String... forceUpdateProps) {
        return this.saveOrUpdateAll(entities, null, forceUpdateProps, null);
    }

    /**
     * @param entities
     * @param reflectPropertyHandler
     * @param forceUpdateProps
     * @todo 批量保存或修改, 自动根据主键来判断是修改还是保存，没有主键的直接插入记录，
     * 存在主键值的先通过数据库查询判断记录是否存在，不存在则插入记录，存在则修改
     */
    protected <T extends Serializable> Long saveOrUpdateAll(final List<T> entities,
                                                            final ReflectPropertyHandler reflectPropertyHandler, final String... forceUpdateProps) {
        return this.saveOrUpdateAll(entities, reflectPropertyHandler, forceUpdateProps, null);
    }

    /**
     * @param entities
     * @param reflectPropertyHandler
     * @param forceUpdateProps
     * @param dataSource
     * @todo <b>批量保存或修改</b>
     */
    protected <T extends Serializable> Long saveOrUpdateAll(final List<T> entities,
                                                            final ReflectPropertyHandler reflectPropertyHandler, final String[] forceUpdateProps,
                                                            final DataSource dataSource) {
        return dialectFactory.saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), forceUpdateProps,
                reflectPropertyHandler, this.getDataSource(dataSource), null);
    }

    /**
     * @param entity
     * @todo 通过主键删除单条记录(会自动级联删除子表, 根据数据库配置)
     */
    protected Long delete(final Serializable entity) {
        return dialectFactory.delete(sqlToyContext, entity, this.getDataSource(null));
    }

    protected Long delete(final Serializable entity, final DataSource dataSource) {
        return dialectFactory.delete(sqlToyContext, entity, this.getDataSource(dataSource));
    }

    /**
     * @TODO 提供单表简易查询进行删除操作(删除操作filters过滤无效)
     * @param entityClass
     * @param entityQuery
     * @return
     */
    protected Long deleteByQuery(Class entityClass, EntityQuery entityQuery) {
        EntityQueryExtend innerModel = entityQuery.getInnerModel();
        if (null == entityClass || null == entityQuery || StringUtil.isBlank(innerModel.where)
                || StringUtil.isBlank(innerModel.values)) {
            throw new IllegalArgumentException("deleteByQuery entityClass、where、value 值不能为空!");
        }
        // 做一个必要提示
        if (!innerModel.paramFilters.isEmpty()) {
            logger.warn("删除操作设置动态条件过滤是无效的,数据删除查询条件必须是精准的!");
        }
        EntityMeta entityMeta = getEntityMeta(entityClass);
        String where = SqlUtil.convertFieldsToColumns(entityMeta, innerModel.where);
        String sql = "delete from ".concat(entityMeta.getSchemaTable()).concat(" where ").concat(where);
        // :named 模式
        if (SqlConfigParseUtils.hasNamedParam(where) && StringUtil.isBlank(innerModel.names)) {
            SqlToyConfig sqlToyConfig = getSqlToyConfig(sql, SqlType.update);
            // 根据sql中的变量从entity对象中提取参数值
            Object[] paramValues = BeanUtil.reflectBeanToAry((Serializable) innerModel.values[0],
                    sqlToyConfig.getParamsName(), null, null);
            return executeSql(sql, sqlToyConfig.getParamsName(), paramValues, false,
                    getDataSource(innerModel.dataSource));
        }
        return executeSql(sql, innerModel.names, innerModel.values, false, getDataSource(innerModel.dataSource));
    }

    protected <T extends Serializable> Long deleteAll(final List<T> entities) {
        return this.deleteAll(entities, null);
    }

    /**
     * @param entities
     * @param dataSource
     * @todo <b>批量删除数据</b>
     */
    protected <T extends Serializable> Long deleteAll(final List<T> entities, final DataSource dataSource) {
        return dialectFactory.deleteAll(sqlToyContext, entities, sqlToyContext.getBatchSize(),
                this.getDataSource(dataSource), null);
    }

    /**
     * @param queryExecutor
     * @param updateRowHandler
     * @return
     * @todo 锁定记录查询，并对记录进行修改,最后将结果返回
     */
    protected List updateFetch(final QueryExecutor queryExecutor, final UpdateRowHandler updateRowHandler) {
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getInnerModel().sql, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        return dialectFactory.updateFetch(sqlToyContext, queryExecutor, sqlToyConfig, updateRowHandler,
                this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig)).getRows();
    }

    /**
     * @param queryExecutor
     * @param topSize
     * @param updateRowHandler
     * @return
     * @todo 取符合条件的前固定数量的记录，锁定并进行修改
     */
    @Deprecated
    protected List updateFetchTop(final QueryExecutor queryExecutor, final Integer topSize,
                                  final UpdateRowHandler updateRowHandler) {
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getInnerModel().sql, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        return dialectFactory.updateFetchTop(sqlToyContext, queryExecutor, sqlToyConfig, topSize, updateRowHandler,
                this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig)).getRows();
    }

    /**
     * @param queryExecutor
     * @param random
     * @param updateRowHandler
     * @return
     * @todo 随机提取符合条件的记录, 锁定并进行修改
     */
    @Deprecated
    protected List updateFetchRandom(final QueryExecutor queryExecutor, final Integer random,
                                     final UpdateRowHandler updateRowHandler) {
        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor.getInnerModel().sql, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        return dialectFactory.updateFetchRandom(sqlToyContext, queryExecutor, sqlToyConfig, random, updateRowHandler,
                this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig)).getRows();
    }

    /**
     * @param entityClass
     * @return
     * @todo 获取对象信息(对应的表以及字段 、 主键策略等等的信息)
     */
    protected EntityMeta getEntityMeta(Class entityClass) {
        return sqlToyContext.getEntityMeta(entityClass);
    }

    /**
     * @return
     * @todo 获取sqltoy配置的批处理每批记录量(默认为50)
     */
    protected int getBatchSize() {
        return sqlToyContext.getBatchSize();
    }

    /**
     * @param names
     * @return
     * @todo 协助完成对对象集合的属性批量赋予相应数值
     */
    protected BeanPropsWrapper wrapBeanProps(String... names) {
        return new BeanPropsWrapper(names);
    }

    /**
     * @todo <b>手工提交数据库操作,只提供当前DataSource提交</b>
     */
    protected void flush() {
        String connectionManagerName = this.sqlToyContext.getConnectionManagerName();
        ConnectionManager connectionManager = ConnectionManager.getConnectionManager(connectionManagerName);
        connectionManager.commit();
    }

    protected void rollback() {
        String connectionManagerName = this.sqlToyContext.getConnectionManagerName();
        ConnectionManager connectionManager = ConnectionManager.getConnectionManager(connectionManagerName);
        connectionManager.rollback();
    }

    protected void closeConnection(){
        String connectionManagerName = this.sqlToyContext.getConnectionManagerName();
        ConnectionManager connectionManager = ConnectionManager.getConnectionManager(connectionManagerName);
        connectionManager.close();
    }

//    protected void rollback(DataSource dataSource) {
//        DataSourceUtils.processDataSource(sqlToyContext, this.getDataSource(dataSource),
//                new DataSourceCallbackHandler() {
//                    @Override
//                    public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
//                        System.out.println("flush conn======"+conn);
//                        conn.rollback();
//                    }
//                });
//    }

    /**
     * @param dataSource
     * @todo <b>手工提交数据库操作,只提供当前DataSource提交</b>
     */
//    protected void flush(DataSource dataSource) {
//        DataSourceUtils.processDataSource(sqlToyContext, this.getDataSource(dataSource),
//                new DataSourceCallbackHandler() {
//                    @Override
//                    public void doConnection(Connection conn, Integer dbType, String dialect) throws Exception {
//                        if (!conn.isClosed()) {
//                            conn.commit();
//                        }
//                    }
//                });
//    }

//

    /**
     * @param entity
     * @return
     * @todo 根据实体对象对应的POJO配置的业务主键策略, 提取对象的属性值产生业务主键
     */
    protected String generateBizId(Serializable entity) {
        EntityMeta entityMeta = this.getEntityMeta(entity.getClass());
        if (entityMeta == null || !entityMeta.isHasBizIdConfig()) {
            throw new IllegalArgumentException(
                    StringUtil.fillArgs("对象:{},没有配置业务主键生成策略,请检查POJO 的业务主键配置!", entity.getClass().getName()));
        }
        String businessIdType = entityMeta.getColumnJavaType(entityMeta.getBusinessIdField());
        Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
        Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getFieldsArray(), null, null);
        // 提取关联属性的值
        Object[] relatedColValue = null;
        if (relatedColumn != null) {
            relatedColValue = new Object[relatedColumn.length];
            for (int meter = 0; meter < relatedColumn.length; meter++) {
                relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
                if (relatedColValue[meter] == null) {
                    throw new IllegalArgumentException("对象:" + entity.getClass().getName() + " 生成业务主键依赖的关联字段:"
                            + relatedColumn[meter] + " 值为null!");
                }
            }
        }
        IdGenerator idGenerator = (entityMeta.getBusinessIdGenerator() == null) ? entityMeta.getIdGenerator()
                : entityMeta.getBusinessIdGenerator();
        return idGenerator.getId(entityMeta.getTableName(), entityMeta.getBizIdSignature(),
                entityMeta.getBizIdRelatedColumns(), relatedColValue, new Date(), businessIdType,
                entityMeta.getBizIdLength(), entityMeta.getBizIdSequenceSize()).toString();
    }

    /**
     * @return
     * @todo 获取所有缓存的名称
     */
    public Set<String> getCacheNames() {
        return this.sqlToyContext.getTranslateManager().getCacheNames();
    }

    /**
     * @param cacheName
     * @return
     * @todo 判断缓存是否存在
     */
    protected boolean existCache(String cacheName) {
        return this.sqlToyContext.getTranslateManager().existCache(cacheName);
    }

    /**
     * @param cacheName
     * @param cacheType
     * @return
     * @todo 获取缓存数据
     */
    protected HashMap<String, Object[]> getTranslateCache(String cacheName, String cacheType) {
        return this.sqlToyContext.getTranslateManager().getCacheData(this.sqlToyContext, cacheName, cacheType);
    }

    protected String[] cacheMatchKeys(String cacheName, String matchRegex, int... matchIndexes) {
        return cacheMatchKeys(cacheName, null, matchRegex, matchIndexes);
    }

    /**
     * @param cacheName
     * @param cacheType
     * @param matchRegex
     * @param matchIndexes
     * @return
     * @TODO 通过缓存匹配名称并返回key集合(类似数据库中的like)便于后续进行精准匹配
     */
    protected String[] cacheMatchKeys(String cacheName, String cacheType, String matchRegex, int... matchIndexes) {
        if (StringUtil.isBlank(cacheName) || StringUtil.isBlank(matchRegex)) {
            throw new IllegalArgumentException("缓存反向名称匹配key必须要提供cacheName和matchName值!");
        }
        int[] nameIndexes = {1};
        if (matchIndexes != null && matchIndexes.length > 0) {
            nameIndexes = matchIndexes;
        }
        HashMap<String, Object[]> cacheDatas = this.sqlToyContext.getTranslateManager().getCacheData(this.sqlToyContext,
                cacheName, cacheType);
        Collection<Object[]> values = cacheDatas.values();
        List<String> keySet = new ArrayList<String>();
        String lowName = matchRegex.toLowerCase();
        int meter = 0;
        for (Object[] row : values) {
            for (int index : nameIndexes) {
                // 字符包含
                if (row[index] != null && row[index].toString().toLowerCase().contains(lowName)) {
                    meter++;
                    keySet.add(row[0].toString());
                    break;
                }
            }
            // 不超过1000个(作为in条件值有限制)
            if (meter == 1000) {
                break;
            }
        }
        String[] result = new String[keySet.size()];
        keySet.toArray(result);
        return result;
    }

    /**
     * @param dataSet        要翻译的数据集合
     * @param cacheName      缓存名称
     * @param cacheType      缓存分类(如字典分类),非分类型的填null
     * @param cacheNameIndex 缓存名称对应的列，默认为1(null也表示1)
     * @param handler        2个方法:getKey(Object row),setName(Object row,String name)
     * @todo 利用sqltoy的translate缓存，通过显式调用对集合数据的列进行翻译
     */
    protected void translate(Collection dataSet, String cacheName, String cacheType, Integer cacheNameIndex,
                             TranslateHandler handler) {
        // 数据以及合法性校验
        if (dataSet == null || dataSet.isEmpty()) {
            return;
        }
        if (cacheName == null) {
            throw new IllegalArgumentException("缓存名称不能为空!");
        }
        if (handler == null) {
            throw new IllegalArgumentException("缓存翻译行取key和设置name的反调函数不能为null!");
        }
        // 获取缓存,框架会自动判断null并实现缓存数据的加载和更新检测
        final HashMap<String, Object[]> cache = getTranslateCache(cacheName, cacheType);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        Iterator iter = dataSet.iterator();
        Object row;
        Object key;
        Object name;
        // 默认名称字段列为1
        int cacheIndex = (cacheNameIndex == null) ? 1 : cacheNameIndex.intValue();
        Object[] keyRow;
        // 循环获取行数据
        while (iter.hasNext()) {
            row = iter.next();
            if (row != null) {
                // 反调获取需要翻译的key
                key = handler.getKey(row);
                if (key != null) {
                    keyRow = cache.get(key.toString());
                    // 从缓存中获取对应的名称
                    name = (keyRow == null) ? null : keyRow[cacheIndex];
                    // 反调设置行数据中具体列或属性翻译后的名称
                    handler.setName(row, (name == null) ? "" : name.toString());
                }
            }
        }
    }

    /**
     * @param <T>
     * @param entityClass
     * @param entityQuery
     * @return
     * @TODO 提供针对单表简易快捷查询 EntityQuery.where("#[name like ?]#[and status in
     * (?)]").values(new Object[]{xxx,xxx})
     */
    public <T> List<T> findEntity(Class<T> entityClass, EntityQuery entityQuery) {
        if (null == entityClass || null == entityQuery || StringUtil.isBlank(entityQuery.getInnerModel().values)) {
            throw new IllegalArgumentException("findEntityList entityClass、where、value 值不能为空!");
        }
        return (List<T>) findEntityUtil(entityClass, null, entityQuery);
    }

    /**
     * @param <T>
     * @param entityClass
     * @param paginationModel
     * @param entityQuery
     * @return
     * @TODO 提供针对单表简易快捷分页查询 EntityQuery.where("#[name like ?]#[and status in
     * (?)]").values(new Object[]{xxx,xxx})
     */
    public <T> PaginationModel<T> findEntity(Class<T> entityClass, PaginationModel paginationModel,
                                             EntityQuery entityQuery) {
        if (null == entityClass || null == paginationModel || null == entityQuery
                || StringUtil.isBlank(entityQuery.getInnerModel().values)) {
            throw new IllegalArgumentException("findEntityPage entityClass、paginationModel、where、value 值不能为空!");
        }
        return (PaginationModel<T>) findEntityUtil(entityClass, paginationModel, entityQuery);
    }

    private Object findEntityUtil(Class entityClass, PaginationModel paginationModel, EntityQuery entityQuery) {
        String where;
        EntityMeta entityMeta = getEntityMeta(entityClass);
        EntityQueryExtend innerModel = entityQuery.getInnerModel();
        // 动态组织where 后面的条件语句,此功能并不建议使用,where 一般需要指定明确条件
        if (StringUtil.isBlank(innerModel.where)) {
            where = SqlUtil.wrapWhere(entityMeta);
        } else {
            where = SqlUtil.convertFieldsToColumns(entityMeta, innerModel.where);
        }

        String translateFields = "";
        // 将缓存翻译对应的查询补充到select column 上,形成select keyColumn as viewColumn 模式
        if (!innerModel.translates.isEmpty()) {
            Iterator<Translate> iter = innerModel.translates.values().iterator();
            String keyColumn;
            TranslateExtend extend;
            while (iter.hasNext()) {
                extend = iter.next().getExtend();
                // 将java模式的字段名称转化为数据库字段名称
                keyColumn = entityMeta.getColumnName(extend.keyColumn);
                translateFields = translateFields.concat(",").concat((keyColumn == null) ? extend.keyColumn : keyColumn)
                        .concat(" as ").concat(extend.column);
            }
        }

        String sql = "select ".concat(entityMeta.getAllColumnNames()).concat(translateFields).concat(" from ")
                .concat(entityMeta.getSchemaTable()).concat(" where ").concat(where);
        // 处理order by 排序
        if (!innerModel.orderBy.isEmpty()) {
            sql = sql.concat(" order by ");
            Iterator<Entry<String, String>> iter = innerModel.orderBy.entrySet().iterator();
            Entry<String, String> entry;
            String columnName;
            int index = 0;
            while (iter.hasNext()) {
                entry = iter.next();
                columnName = entityMeta.getColumnName(entry.getKey());
                if (columnName == null) {
                    columnName = entry.getKey();
                }
                // 保留字处理
                columnName = ReservedWordsUtil.convertWord(columnName, null);
                if (index > 0) {
                    sql = sql.concat(",");
                }

                // entry.getValue() is order way,like: desc or " "
                sql = sql.concat(columnName).concat(entry.getValue());
                index++;
            }
        }
        QueryExecutor queryExecutor;
        // :named 模式
        if (SqlConfigParseUtils.hasNamedParam(where) && StringUtil.isBlank(innerModel.names)) {
            queryExecutor = new QueryExecutor(sql, (Serializable) innerModel.values[0]).resultType(entityClass)
                    .dataSource(getDataSource(innerModel.dataSource));
        } else {
            queryExecutor = new QueryExecutor(sql).names(innerModel.names).values(innerModel.values)
                    .resultType(entityClass).dataSource(getDataSource(innerModel.dataSource));
        }
        // 设置额外的缓存翻译
        if (!innerModel.translates.isEmpty()) {
            queryExecutor.getInnerModel().translates.putAll(innerModel.translates);
        }
        // 设置额外的参数条件过滤
        if (!innerModel.paramFilters.isEmpty()) {
            queryExecutor.getInnerModel().paramFilters.addAll(innerModel.paramFilters);
        }

        // 设置安全脱敏
        if (!innerModel.secureMask.isEmpty()) {
            queryExecutor.getInnerModel().secureMask.putAll(innerModel.secureMask);
        }

        // 设置分页优化
        queryExecutor.getInnerModel().pageOptimize = innerModel.pageOptimize;

        SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(queryExecutor, SqlType.search,
                getDialect(queryExecutor.getInnerModel().dataSource));
        // 分库分表策略
        if (entityMeta.getShardingConfig() != null) {
            // dataSource sharding
            ShardingStrategyConfig shardingStrategy = entityMeta.getShardingConfig().getShardingDBStrategy();
            if (shardingStrategy != null) {
                sqlToyConfig.setDataSourceShardingStragety(shardingStrategy.getName());
                sqlToyConfig.setDataSourceShardingParams(shardingStrategy.getFields());
                sqlToyConfig.setDataSourceShardingParamsAlias(shardingStrategy.getAliasNames());
            }
            // table sharding
            shardingStrategy = entityMeta.getShardingConfig().getShardingTableStrategy();
            if (shardingStrategy != null) {
                sqlToyConfig.setTableShardingParams(shardingStrategy.getFields());
                List<QueryShardingModel> queryShardings = new ArrayList<QueryShardingModel>();
                QueryShardingModel model = new QueryShardingModel();
                model.setParams(shardingStrategy.getFields());
                model.setParamsAlias(shardingStrategy.getAliasNames());
                model.setStrategy(shardingStrategy.getName());
                model.setTables(new String[]{entityMeta.getSchemaTable()});
                queryShardings.add(model);
                sqlToyConfig.setTablesShardings(queryShardings);
            }
        }
        // 非分页
        if (paginationModel == null) {
            return dialectFactory.findByQuery(sqlToyContext, queryExecutor, sqlToyConfig, innerModel.lockMode,
                    this.getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig)).getRows();
        }
        // 跳过总记录数形式的分页
        if (paginationModel.getSkipQueryCount()) {
            return dialectFactory.findSkipTotalCountPage(sqlToyContext, queryExecutor, sqlToyConfig,
                    paginationModel.getPageNo(), paginationModel.getPageSize(),
                    getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig)).getPageResult();
        }
        return dialectFactory.findPage(sqlToyContext, queryExecutor, sqlToyConfig, paginationModel.getPageNo(),
                paginationModel.getPageSize(), getDataSource(queryExecutor.getInnerModel().dataSource, sqlToyConfig))
                .getPageResult();
    }

    /**
     * @param entityClass
     * @param entityUpdate
     * @return
     * @TODO 针对单表对象查询进行更新操作(update和delete 操作filters过滤是无效的 ， 必须是精准的条件参数)
     */
    public Long updateByQuery(Class entityClass, EntityUpdate entityUpdate) {
        if (null == entityClass || null == entityUpdate || StringUtil.isBlank(entityUpdate.getInnerModel().where)
                || StringUtil.isBlank(entityUpdate.getInnerModel().values)
                || entityUpdate.getInnerModel().updateValues.isEmpty()) {
            throw new IllegalArgumentException("updateByQuery: entityClass、where条件、条件值value、变更值setValues不能为空!");
        }
        EntityUpdateExtend innerModel = entityUpdate.getInnerModel();
        boolean isName = SqlConfigParseUtils.hasNamedParam(innerModel.where);
        Object[] values = innerModel.values;
        String where = innerModel.where;
        // 重新通过对象反射获取参数条件的值
        if (isName) {
            if (values.length > 1) {
                throw new IllegalArgumentException("updateByQuery: where条件采用:paramName形式传参,values只能传递单个VO对象!");
            }
            String[] paramName = SqlConfigParseUtils.getSqlParamsName(where, false);
            values = BeanUtil.reflectBeanToAry(values[0], paramName, null, null);
            SqlToyResult sqlToyResult = SqlConfigParseUtils.processSql(where, paramName, values);
            where = sqlToyResult.getSql();
            values = sqlToyResult.getParamsValue();
        } else {
            if (StringUtil.matchCnt(where, "\\?") != values.length) {
                throw new IllegalArgumentException("updateByQuery: where语句中的?数量跟对应values 数组长度不一致,请检查!");
            }
        }
        EntityMeta entityMeta = getEntityMeta(entityClass);
        // 处理where 中写的java 字段名称为数据库表字段名称
        where = SqlUtil.convertFieldsToColumns(entityMeta, where);
        StringBuilder sql = new StringBuilder();
        sql.append("update ").append(entityMeta.getSchemaTable()).append(" set ");
        Iterator<Entry<String, Object>> iter = innerModel.updateValues.entrySet().iterator();
        Entry<String, Object> entry;
        String columnName;
        Object[] realValues = new Object[innerModel.updateValues.size() + values.length];
        System.arraycopy(values, 0, realValues, innerModel.updateValues.size(), values.length);
        int index = 0;
        while (iter.hasNext()) {
            entry = iter.next();
            // entry.getKey() is field
            columnName = entityMeta.getColumnName(entry.getKey());
            if (columnName == null) {
                columnName = entry.getKey();
            }
            // 保留字处理
            columnName = ReservedWordsUtil.convertWord(columnName, null);
            realValues[index] = entry.getValue();
            if (index > 0) {
                sql.append(",");
            }
            sql.append(columnName).append("=?");
            index++;
        }
        sql.append(" where ").append(where);
        return executeSql(sql.toString(), null, realValues, false, getDataSource(innerModel.dataSource));
    }

    /**
     * @param <T>
     * @param source
     * @param resultType
     * @return
     * @throws Exception
     * @TODO 实现POJO和DTO(VO) 之间类型的相互转换和数据复制
     */
    public <T extends Serializable> T convertType(Serializable source, Class<T> resultType) throws Exception {
        return MapperUtils.map(sqlToyContext, source, resultType);
    }

    /**
     * @param <T>
     * @param sourceList
     * @param resultType
     * @return
     * @throws Exception
     * @TODO 实现POJO和DTO(VO) 集合之间类型的相互转换和数据复制
     */
    public <T extends Serializable> List<T> convertType(List<Serializable> sourceList, Class<T> resultType)
            throws Exception {
        return MapperUtils.mapList(sqlToyContext, sourceList, resultType);
    }

    /**
     * @TODO 获取当前数据库方言的名称
     * @param dataSource
     * @return
     */
    protected String getDialect(DataSource dataSource) {
        if (StringUtil.isNotBlank(sqlToyContext.getDialect())) {
            return sqlToyContext.getDialect();
        }
        return DataSourceUtils.getDialect(getDataSource(dataSource));
    }
}
