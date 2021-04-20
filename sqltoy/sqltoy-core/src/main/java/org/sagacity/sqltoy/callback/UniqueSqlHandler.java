/**
 * 
 */
package org.sagacity.sqltoy.callback;

import org.sagacity.sqltoy.config.model.EntityMeta;

/**
 * @project sagacity-sqltoy
 * @description 提供给各个数据库针对唯一性验证组织取top记录的反调(sqltoy内部使用)
 * @author zhongxuchen@gmail.com
 * @version v1.0, Date:2020-8-5
 * @modify 2020-8-5,修改说明
 */
@FunctionalInterface
public interface UniqueSqlHandler {
	public String process(EntityMeta entityMeta, String[] paramNames, String tableName, int topSize);
}
