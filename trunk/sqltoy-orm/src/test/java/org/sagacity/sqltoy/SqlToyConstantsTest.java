package org.sagacity.sqltoy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sagacity.sqltoy.demo.vo.StaffInfoVO;
import org.sagacity.sqltoy.model.MapKit;
import org.sagacity.sqltoy.utils.DateUtil;

import com.alibaba.fastjson.JSON;

public class SqlToyConstantsTest {
	@Test
	public void testParseParams() {
		String template = "你好${ 姓名},请于${bizDate }来开会!";
		Pattern paramPattern = Pattern.compile(
				"\\$\\{\\s*[0-9a-zA-Z\u4e00-\u9fa5]+((\\.|\\_)[0-9a-zA-Z\u4e00-\u9fa5]+)*(\\[\\d*(\\,)?\\d*\\])?\\s*\\}");
		LinkedHashMap<String, String> paramsMap = new LinkedHashMap<String, String>();
		Matcher m = paramPattern.matcher(template);
		String group;
		while (m.find()) {
			group = m.group();
			// key as ${name} value:name
			paramsMap.put(group, group.substring(2, group.length() - 1).trim());
		}
		System.err.println(JSON.toJSONString(paramsMap));
	}

	@Test
	public void testBigIntger() {
		MapKit.startOf(null, "").get();
		BigInteger a = new BigInteger("2993439899898779987777777777897777");
		System.err.println(a.toString());
		BigDecimal b = new BigDecimal("2993439899898779987777777777897777");
		System.err.println(b.toString());
		System.err.println(String.class.getTypeName());
		System.err.println(int.class.getTypeName());
		System.err.println(new Long("20211111102134").longValue());
	}

	@Test
	public void testBeanInfo() {
		Class classType = StaffInfoVO.class;
		while (!classType.equals(Object.class)) {
			System.err.println(classType.getName());
			classType = classType.getSuperclass();
		}

	}
}
