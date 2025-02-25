/**
 * 
 */
package org.sagacity.sqltoy.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @author zhongxuchen
 *
 */
public class StringUtilsTest {

	@Test
	public void testSplitExcludeSymMark1() {
		String source = "#[testNum],'#,#0.00'";
		String[] result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "#[testNum]", "'#,#0.00'" });
		source = ",'#,#0.00'";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "", "'#,#0.00'" });

		source = "'\\'', t.`ORGAN_ID`, '\\''";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "'\\''", " t.`ORGAN_ID`", " '\\''" });

		source = "orderNo,<td align=\"center\" rowspan=\"#[group('orderNo,').size()]\">,@dict(EC_PAY_TYPE,#[payType])</td>";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result,
				new String[] { "orderNo", "<td align=\"center\" rowspan=\"#[group('orderNo,').size()]\">",
						"@dict(EC_PAY_TYPE,#[payType])</td>" });
		source = "reportId=\"RPT_DEMO_005\",chart-index=\"1\",style=\"width:49%;height:350px;display:inline-block;\"";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		assertArrayEquals(result, new String[] { "reportId=\"RPT_DEMO_005\"", "chart-index=\"1\"",
				"style=\"width:49%;height:350px;display:inline-block;\"" });
		source = "a,\"\"\",\",a";
		result = StringUtil.splitExcludeSymMark(source, ",", SqlToyConstants.filters);
		for (String s : result) {
			System.err.println("[" + s.trim() + "]");
		}
		assertArrayEquals(result, new String[] { "a", "\"\"\",\"", "a" });
	}

	@Test
	public void testRegex() {
		String temp = "{Key}";
		String result = temp.replaceAll("(?i)\\$?\\{\\s*key\\s*\\}", "\\$\\{value\\}");
		System.err.println(result);
		System.err.println(result.replace("${value}", "chenren"));
	}

	@Test
	public void testMatchForUpdate() {
		String sql = "selec * from table ";
		System.err.println(SqlUtil.hasLock(sql.concat(" "), DBType.MYSQL));
		System.err.println(SqlUtil.hasLock(sql.concat(" for update"), DBType.MYSQL));
		System.err.println(SqlUtil.hasLock(sql.concat(" for update"), DBType.SQLSERVER));
		System.err.println(SqlUtil.hasLock(sql.concat(" with(rowlock xlock)"), DBType.MYSQL));
		System.err.println(SqlUtil.hasLock(sql.concat(" with(rowlock xlock)"), DBType.SQLSERVER));
		String sql1 = "select * from table with ";
		String regex = "(?i)with\\s*\\(\\s*(rowlock|xlock|updlock|holdlock)?\\,?\\s*(rowlock|xlock|updlock|holdlock)\\s*\\)";
		System.err.println(StringUtil.matches(sql1.concat("(rowlock xlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(rowlock,xlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(rowlock,updlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(rowlock updlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(holdlock updlock)"), regex));
		System.err.println(StringUtil.matches(sql1.concat("(holdlock)"), regex));
	}

	@Test
	public void testLike() {
		String[] ary = "   a   b  c d".trim().split("\\s+");
		for (int i = 0; i < ary.length; i++) {
			System.err.println("[" + ary[i] + "]");
		}
		String sql = "支持保留字处理，对象操作自动增加保留字符号，跨数据库sql自动适配";
		System.err.println(StringUtil.like(sql, "数据库".split("\\s+")));
		System.err.println(StringUtil.like(sql, "保留  操作  ，跨数库".split("\\s+")));
		System.err.println(StringUtil.like(sql, "保留  操作  ， 数据库".split("\\s+")));

	}

	@Test
	public void testMatch() {
		String sqlLow = "from t where1 (1=1)";
		String sql = "select 1 from";
		String sqlWith = "with t as () * from";
		System.err.println(StringUtil.matches(sqlLow, "^\\s*where\\W"));
		System.err.println(StringUtil.matches(sqlLow, "^from\\W"));
		System.err.println(StringUtil.matches(sql, "^(select|with)\\W"));
		System.err.println(StringUtil.matches(sqlWith, "^(select|with)\\W"));
		String sequence = "SEQ_${tableName}";
		System.err.println(sequence.replaceFirst("(?i)\\$\\{tableName\\}", "staff_info"));
		System.err.println(sequence.replaceFirst("(?i)\\$?\\{tableName\\}", "staff_info"));
		System.err.println("A_B_C_D".replace("_", ""));

	}

	@Test
	public void testWhereMatch() {
		Pattern WHERE_CLOSE_PATTERN = Pattern
				.compile("^((order|group)\\s+by|(inner|left|right|full)\\s+join|having|union)\\W");
		System.err.println(StringUtil.matches("inner join ", WHERE_CLOSE_PATTERN));

	}

	@Test
	public void testLineMaskMatch() {
		String sql = "select 'a',\"b\",/**/ from table -- 备注";
		int lastIndex = StringUtil.matchLastIndex(sql, "\'|\"|\\*\\/");
		int lineMaskIndex = sql.indexOf("--");
		System.err.println("lastIndex=" + lastIndex + "lineMaskIndex=" + lineMaskIndex);

	}

	@Test
	public void testWhereMatch1() {
		Pattern SQL_ID_PATTERN = Pattern.compile("^[A-Za-z_0-9\\-]+$");
		System.err.println(StringUtil.matches("inner_join", SQL_ID_PATTERN));
		System.err.println(StringUtil.matches("inner-join", SQL_ID_PATTERN));
		System.err.println(StringUtil.matches("innerjoin1", SQL_ID_PATTERN));
		System.err.println(StringUtil.matches("inner join1", SQL_ID_PATTERN));
		System.err.println(StringUtil.matches("innerAoin1", SQL_ID_PATTERN));
		System.err.println(StringUtil.matches("inner_Aoin-10", SQL_ID_PATTERN));
	}

	@Test
	public void testWhereMatch2() {
		String packageName = "/com/sinochem/xdata/";
		if (packageName.charAt(0) == '/') {
			packageName = packageName.substring(1);
		}
		if (packageName.endsWith("/")) {
			packageName = packageName.substring(0, packageName.length() - 1);
		}
		packageName = packageName.replace("/", ".");
		System.err.println(packageName);
	}
}
