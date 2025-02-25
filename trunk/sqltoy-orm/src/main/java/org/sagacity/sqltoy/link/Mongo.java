/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.NoSqlConfigModel;
import org.sagacity.sqltoy.config.model.NoSqlFieldsModel;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.model.Page;
import org.sagacity.sqltoy.model.QueryExecutor;
import org.sagacity.sqltoy.model.inner.DataSetResult;
import org.sagacity.sqltoy.model.inner.QueryExecutorExtend;
import org.sagacity.sqltoy.utils.MongoElasticUtils;
import org.sagacity.sqltoy.utils.ResultUtils;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;

import com.mongodb.client.AggregateIterable;

/**
 * @project sagacity-sqltoy
 * @description 提供基于mongodb的查询服务(利用sqltoy组织查询的语句机制的优势提供查询相关功能,增删改暂时不提供)
 * @author zhongxuchen
 * @version v1.0,Date:2018年1月1日
 * @modify {Date:2020-05-29,调整mongo的注入方式,剔除之前MongoDbFactory模式,直接使用MongoTemplate}
 */
public class Mongo extends BaseLink {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4443964509492022973L;

	/**
	 * 定义日志
	 */
	private final Logger logger = LoggerFactory.getLogger(Mongo.class);

	private final String ERROR_MESSAGE = "mongo查询请使用<mql id=\"\" collection=\"\" fields=\"\"></mql>配置,请确定相关配置正确性!";

	/**
	 * 基于spring-data的mongo工厂类
	 */
	private MongoTemplate mongoTemplate;

	/**
	 * 查询语句
	 */
	private String sql;

	/**
	 * sql中的参数名称
	 */
	private String[] names;

	/**
	 * 参数对应的值
	 */
	private Object[] values;

	/**
	 * 查询条件赋值的对象,自动根据sql中的参数名称跟对象的属性进行匹配提取响应的值作为条件
	 */
	private Serializable entity;

	/**
	 * 返回结果类型
	 */
	private Type resultType;

	/**
	 * 返回结果是Map类型，属性标签是否需要驼峰化命名处理
	 */
	private boolean humpMapLabel = true;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Mongo(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public void mongoTemplate(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public Mongo sql(String sql) {
		this.sql = sql;
		return this;
	}

	public Mongo names(String... names) {
		this.names = names;
		return this;
	}

	public Mongo values(Object... values) {
		this.values = values;
		return this;
	}

	public Mongo entity(Serializable entityVO) {
		this.entity = entityVO;
		return this;
	}

	public Mongo resultType(Type resultType) {
		this.resultType = resultType;
		return this;
	}

	public Mongo humpMapLabel(boolean humpMapLabel) {
		this.humpMapLabel = humpMapLabel;
		return this;
	}

	/**
	 * @todo 获取单条记录
	 * @return
	 */
	public Object getOne() throws Exception {
		List<?> result = find();
		if (result != null && !result.isEmpty()) {
			return result.get(0);
		}
		return null;
	}

	/**
	 * @todo 集合记录查询
	 * @return
	 */
	public List<?> find() {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "");
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		if (noSqlModel == null || noSqlModel.getCollection() == null || noSqlModel.getFields() == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		try {
			QueryExecutorExtend extend = queryExecutor.getInnerModel();
			// 最后的执行语句
			String realMql = MongoElasticUtils.wrapMql(sqlToyConfig, extend.getParamsName(sqlToyConfig),
					extend.getParamsValue(sqlToyContext, sqlToyConfig));
			// 聚合查询
			if (noSqlModel.isHasAggs()) {
				return aggregate(getMongoTemplate(), sqlToyConfig, realMql, (Class) extend.resultType,
						extend.humpMapLabel);
			}
			return findTop(getMongoTemplate(), sqlToyConfig, null, realMql, (Class) extend.resultType,
					extend.humpMapLabel);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException(e);
		}
	}

	/**
	 * @todo 查询前多少条记录
	 * @param topSize
	 * @return
	 */
	public List<?> findTop(final Float topSize) {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "");
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		if (noSqlModel == null || noSqlModel.getCollection() == null || noSqlModel.getFields() == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		try {
			QueryExecutorExtend extend = queryExecutor.getInnerModel();
			// 最后的执行语句
			String realMql = MongoElasticUtils.wrapMql(sqlToyConfig, extend.getParamsName(sqlToyConfig),
					extend.getParamsValue(sqlToyContext, sqlToyConfig));
			return findTop(getMongoTemplate(), sqlToyConfig, topSize, realMql, (Class) extend.resultType,
					extend.humpMapLabel);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException(e);
		}
	}

	/**
	 * @todo 分页查询
	 * @param page
	 * @return
	 */
	public Page findPage(Page page) {
		QueryExecutor queryExecutor = build();
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.search, "");
		NoSqlConfigModel noSqlModel = sqlToyConfig.getNoSqlConfigModel();
		if (noSqlModel == null || noSqlModel.getCollection() == null || noSqlModel.getFields() == null) {
			throw new IllegalArgumentException(ERROR_MESSAGE);
		}
		try {
			QueryExecutorExtend extend = queryExecutor.getInnerModel();
			// 最后的执行语句
			String realMql = MongoElasticUtils.wrapMql(sqlToyConfig, extend.getParamsName(sqlToyConfig),
					extend.getParamsValue(sqlToyContext, sqlToyConfig));
			return findPage(getMongoTemplate(), sqlToyConfig, page, realMql, (Class) extend.resultType,
					extend.humpMapLabel);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DataAccessException(e);
		}
	}

	/**
	 * @todo 构造统一的查询条件
	 * @return
	 */
	private QueryExecutor build() {
		QueryExecutor queryExecutor = null;
		if (entity != null) {
			queryExecutor = new QueryExecutor(sql, entity);
		} else {
			queryExecutor = new QueryExecutor(sql).names(names).values(values);
		}
		if (resultType != null) {
			queryExecutor.resultType(resultType);
		}
		queryExecutor.humpMapLabel(humpMapLabel);
		return queryExecutor;
	}

	/**
	 * @todo 分页查询
	 * @param mongoTemplate
	 * @param sqlToyConfig
	 * @param pageModel
	 * @param mql
	 * @param resultClass
	 * @param humpMapLabel
	 * @return
	 * @throws Exception
	 */
	private Page findPage(MongoTemplate mongoTemplate, SqlToyConfig sqlToyConfig, Page pageModel, String mql,
			Class resultClass, boolean humpMapLabel) throws Exception {
		Page result = new Page();
		result.setPageNo(pageModel.getPageNo());
		result.setPageSize(pageModel.getPageSize());
		BasicQuery query = new BasicQuery(mql);
		result.setRecordCount(mongoTemplate.count(query, sqlToyConfig.getNoSqlConfigModel().getCollection()));
		if (result.getRecordCount() == 0) {
			if (sqlToyContext.isPageOverToFirst()) {
				result.setPageNo(1L);
			}
			return result;
		}
		// 设置分页
		if (result.getPageNo() == -1) {
			query.skip(0).limit(Long.valueOf(result.getRecordCount()).intValue());
		} else {
			boolean isOverPage = (pageModel.getPageNo()
					* pageModel.getPageSize() >= (result.getRecordCount() + pageModel.getPageSize()));
			if (isOverPage && !sqlToyContext.isPageOverToFirst()) {
				return result;
			}
			long realStartPage = isOverPage ? 1 : pageModel.getPageNo();
			result.setPageNo(realStartPage);
			query.skip((realStartPage - 1) * pageModel.getPageSize()).limit(pageModel.getPageSize());
		}
		if (sqlToyContext.isDebug()) {
			if (logger.isDebugEnabled()) {
				logger.debug("findPageByMongo script=" + query.getQueryObject());
			} else {
				System.out.println("findPageByMongo script=" + query.getQueryObject());
			}
		}
		List<Document> rs = mongoTemplate.find(query, Document.class,
				sqlToyConfig.getNoSqlConfigModel().getCollection());
		if (rs == null || rs.isEmpty()) {
			return result;
		}
		result.setRows(extractFieldValues(sqlToyConfig, rs.iterator(), resultClass, humpMapLabel));
		return result;
	}

	/**
	 * @todo 取top记录
	 * @param mongoTemplate
	 * @param sqlToyConfig
	 * @param topSize
	 * @param mql
	 * @param resultClass
	 * @param humpMapLabel
	 * @return
	 * @throws Exception
	 */
	private List<?> findTop(MongoTemplate mongoTemplate, SqlToyConfig sqlToyConfig, Float topSize, String mql,
			Class resultClass, boolean humpMapLabel) throws Exception {
		BasicQuery query = new BasicQuery(mql);
		if (topSize != null) {
			if (topSize > 1) {
				query.limit(topSize.intValue());
			} else {
				// 按比例提取
				long count = mongoTemplate.count(query, sqlToyConfig.getNoSqlConfigModel().getCollection());
				query.limit(Double.valueOf(count * topSize.floatValue()).intValue());
			}
		}
		if (sqlToyContext.isDebug()) {
			if (logger.isDebugEnabled()) {
				logger.debug("findTopByMongo script=" + query.getQueryObject());
			} else {
				System.out.println("findTopByMongo script=" + query.getQueryObject());
			}
		}
		List<Document> rs = mongoTemplate.find(query, Document.class,
				sqlToyConfig.getNoSqlConfigModel().getCollection());
		if (rs == null || rs.isEmpty()) {
			return null;
		}
		return extractFieldValues(sqlToyConfig, rs.iterator(), resultClass, humpMapLabel);
	}

	/**
	 * @todo 聚合统计查询
	 * @param mongoTemplate
	 * @param sqlToyConfig
	 * @param mql
	 * @param resultClass
	 * @param humpMapLabel
	 * @return
	 * @throws Exception
	 */
	private List<?> aggregate(MongoTemplate mongoTemplate, SqlToyConfig sqlToyConfig, String mql, Class resultClass,
			boolean humpMapLabel) throws Exception {
		String realMql = mql.trim();
		if (realMql.startsWith("{") && realMql.endsWith("}")) {
			realMql = realMql.substring(1, realMql.length() - 1).trim();
		}
		if (realMql.startsWith("[") && realMql.endsWith("]")) {
			realMql = realMql.substring(1, realMql.length() - 1);
		}
		if (sqlToyContext.isDebug()) {
			if (logger.isDebugEnabled()) {
				logger.debug("aggregateByMongo script=" + realMql);
			} else {
				System.out.println("aggregateByMongo script=" + realMql);
			}
		}
		String[] aggregates = StringUtil.splitExcludeSymMark(realMql, ",", SqlToyConstants.filters);
		List<Bson> dbObjects = new ArrayList<Bson>();
		for (String json : aggregates) {
			if (StringUtil.isNotBlank(json)) {
				dbObjects.add(Document.parse(json));
			}
		}
		AggregateIterable<Document> out = mongoTemplate
				.getCollection(sqlToyConfig.getNoSqlConfigModel().getCollection()).aggregate(dbObjects);
		if (out == null) {
			return null;
		}
		return extractFieldValues(sqlToyConfig, out.iterator(), resultClass, humpMapLabel);
	}

	private List extractFieldValues(SqlToyConfig sqlToyConfig, Iterator<Document> iter, Class resultClass,
			boolean humpMapLabel) throws Exception {
		List resultSet = new ArrayList();
		Document row;
		HashMap<String, String[]> linkMap = new HashMap<String, String[]>();
		NoSqlFieldsModel fieldModel = MongoElasticUtils.processFields(sqlToyConfig.getNoSqlConfigModel().getFields(),
				linkMap);
		// 解决field采用id.name:aliasName 或 id.name 形式
		String[] realFields = fieldModel.getFields();
		String[] translateFields = fieldModel.getAliasLabels();
		String[] keys;
		int size;
		String key;
		Document val;
		List rowData;
		while (iter.hasNext()) {
			row = iter.next();
			rowData = new ArrayList();
			for (String name : realFields) {
				// 存在_id.xxx 模式
				keys = linkMap.get(name);
				if (null == keys) {
					rowData.add(row.get(name));
				} else {
					val = row;
					size = keys.length;
					for (int i = 0; i < size; i++) {
						key = keys[i];
						// 最后一个.xx
						if (i == size - 1) {
							rowData.add(val.get(key));
						} else {
							val = (Document) val.get(key);
						}
					}
				}
			}
			resultSet.add(rowData);
		}
		MongoElasticUtils.processTranslate(sqlToyContext, sqlToyConfig, resultSet, translateFields);
		DataSetResult dataSetResult = new DataSetResult();
		dataSetResult.setRows(resultSet);
		dataSetResult.setLabelNames(translateFields);
		// 不支持指定查询集合的行列转换,对集合进行汇总、行列转换等
		boolean changedCols = ResultUtils.calculate(sqlToyContext.getDesensitizeProvider(), sqlToyConfig, dataSetResult,
				null, null);
		return ResultUtils.wrapQueryResult(sqlToyContext, resultSet, StringUtil.humpFieldNames(translateFields),
				resultClass, changedCols, humpMapLabel);
	}

	/**
	 * @param mongoFactory
	 * @return
	 */
	private MongoTemplate getMongoTemplate() {
		if (this.mongoTemplate != null) {
			return mongoTemplate;
		}
		return (MongoTemplate) sqlToyContext.getBean(MongoTemplate.class);
	}
}
