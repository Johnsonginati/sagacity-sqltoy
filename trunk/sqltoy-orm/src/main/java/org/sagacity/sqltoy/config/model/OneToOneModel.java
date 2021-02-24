/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

/**
 * @project sagacity-sqltoy
 * @description OneToOne 的匹配模型
 * @author zhongxuchen
 * @version v1.0, Date:2021-2-24
 * @modify 2021-2-24,修改说明
 */
public class OneToOneModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5026241313517173808L;

	/**
	 * 对应vo中的List集合属性
	 */
	private String property;

	/**
	 * 主键关联的表
	 */
	private String mappedTable;

	/**
	 * 主键关联表对应的外键字段
	 */
	private String[] mappedColumns;

	/**
	 * 主键关联子表对象的属性(entityManager自动处理按主表主键顺序排列，即跟entityModel中的getIdArray顺序是一致的)
	 */
	private String[] mappedFields;

	/**
	 * 是否级联删除
	 */
	private boolean delete;

	/**
	 * 是否级联更新
	 */
	private boolean update;

	/**
	 * 对应子表对象的类型
	 */
	private Class mappedType;

	/**
	 * 查询子表记录sql
	 */
	private String loadSubTableSql;

	/**
	 * 删除子表sql
	 */
	private String deleteSubTableSql;

	/**
	 * @return the property
	 */
	public String getProperty() {
		return property;
	}

	/**
	 * @param property the property to set
	 */
	public void setProperty(String property) {
		this.property = property;
	}

	/**
	 * @return the mappedTable
	 */
	public String getMappedTable() {
		return mappedTable;
	}

	/**
	 * @param mappedTable the mappedTable to set
	 */
	public void setMappedTable(String mappedTable) {
		this.mappedTable = mappedTable;
	}

	/**
	 * @return the mappedColumn
	 */
	public String[] getMappedColumns() {
		return mappedColumns;
	}

	/**
	 * @param mappedColumn the mappedColumn to set
	 */
	public void setMappedColumns(String[] mappedColumns) {
		this.mappedColumns = mappedColumns;
	}

	/**
	 * @return the mappedType
	 */
	public Class getMappedType() {
		return mappedType;
	}

	/**
	 * @param mappedType the mappedType to set
	 */
	public void setMappedType(Class mappedType) {
		this.mappedType = mappedType;
	}

	/**
	 * @return the loadSubTableSql
	 */
	public String getLoadSubTableSql() {
		return loadSubTableSql;
	}

	/**
	 * @param loadSubTableSql the loadSubTableSql to set
	 */
	public void setLoadSubTableSql(String loadSubTableSql) {
		this.loadSubTableSql = loadSubTableSql;
	}

	/**
	 * @return the deleteSubTableSql
	 */
	public String getDeleteSubTableSql() {
		return deleteSubTableSql;
	}

	/**
	 * @param deleteSubTableSql the deleteSubTableSql to set
	 */
	public void setDeleteSubTableSql(String deleteSubTableSql) {
		this.deleteSubTableSql = deleteSubTableSql;
	}

	/**
	 * @return the mappedFields
	 */
	public String[] getMappedFields() {
		return mappedFields;
	}

	/**
	 * @param mappedFields the mappedFields to set
	 */
	public void setMappedFields(String[] mappedFields) {
		this.mappedFields = mappedFields;
	}

	/**
	 * @return the delete
	 */
	public boolean isDelete() {
		return delete;
	}

	/**
	 * @param delete the delete to set
	 */
	public void setDelete(boolean delete) {
		this.delete = delete;
	}

	/**
	 * @return the update
	 */
	public boolean isUpdate() {
		return update;
	}

	/**
	 * @param update the update to set
	 */
	public void setUpdate(boolean update) {
		this.update = update;
	}
}
