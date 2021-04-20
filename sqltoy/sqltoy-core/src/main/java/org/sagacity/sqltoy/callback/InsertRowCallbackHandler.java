/**
 * 
 */
package org.sagacity.sqltoy.callback;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @project sagacity-sqltoy
 * @description 批量行数据插入反调抽象类定义(实际极少使用,留给在超极端场景下自定义pst处理)
 * @author chenrf <a href="mailto:zhongxuchen@hotmail.com">联系作者</a>
 * @version id:InsertRowCallbackHandler.java,Revision:v1.0,Date:2010-1-5
 */
@Deprecated
@FunctionalInterface
public interface InsertRowCallbackHandler {
	/**
	 * @todo 批量插入反调
	 * @param pst
	 * @param index
	 * @param rowData
	 * @throws SQLException
	 */
	public void process(PreparedStatement pst, int index, Object rowData) throws SQLException;

}
