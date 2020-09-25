/**
 * 
 */
package org.sagacity.sqltoy;

import static java.lang.System.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagacity.sqltoy.model.SqlExecuteLog;
import org.sagacity.sqltoy.model.SqlExecuteTrace;
import org.sagacity.sqltoy.utils.DateUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供sql执行超时统计和基本的sql输出功能
 * @author zhongxuchen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:SqlExecuteStat.java,Revision:v1.0,Date:2015年6月12日
 * @modify {Date:2020-06-15,改进sql日志输出,将条件参数带入到sql中输出，便于开发调试}
 * @modify {Date:2020-08-12,为日志输出增加统一uid,便于辨别同一组执行语句}
 */
public class SqlExecuteStat {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlExecuteStat.class);

	/**
	 * 输出sql的策略(error/debug 两种)
	 */
	private static String printSqlStrategy = "error";

	/**
	 * 是否调试阶段
	 */
	private static boolean debug = false;

	/**
	 * 打印慢sql(单位毫秒,默认超过18秒)
	 */
	private static int printSqlTimeoutMillis = 18000;

	// 用于拟合sql中的条件值表达式(前后都以非字符和数字为依据目的是最大幅度的避免参数值里面存在问号,实际执行过程中这个问题已经被规避,但调试打印参数带入无法规避)
	private final static Pattern ARG_PATTERN = Pattern.compile("\\W\\?\\W");

	// 通过ThreadLocal 来保存进程数据
	private static ThreadLocal<SqlExecuteTrace> threadLocal = new ThreadLocal<SqlExecuteTrace>();

	/**
	 * @todo 登记开始执行
	 * @param sqlId
	 * @param type
	 * @param debugPrint
	 */
	public static void start(String sqlId, String type, Boolean debugPrint) {
		threadLocal.set(new SqlExecuteTrace(sqlId, type, (debugPrint == null) ? true : debugPrint.booleanValue()));
	}

	/**
	 * @todo 向线程中登记发生了异常,便于在finally里面明确是错误并打印相关sql
	 * @param e
	 */
	public static void error(Exception e) {
		if (threadLocal.get() != null) {
			threadLocal.get().setError(e.getMessage());
		}
	}

	/**
	 * @todo 在debug模式下,在console端输出sql,便于开发人员查看
	 * @param topic
	 * @param sql
	 * @param paramValues
	 */
	public static void showSql(String topic, String sql, Object[] paramValues) {
		try {
			// debug模式直接输出
			if (debug || printSqlStrategy.equals("debug")) {
				if (threadLocal.get() != null) {
					threadLocal.get().addSqlLog(topic, sql, paramValues);
				}
			}
		} catch (Exception e) {

		}
	}

	/**
	 * @TODO 提供中间日志输出
	 * @param message
	 * @param args
	 */
	public static void debug(String topic, String message, Object... args) {
		try {
			if (debug || printSqlStrategy.equals("debug")) {
				if (threadLocal.get() != null) {
					threadLocal.get().addLog(topic, message, args);
				}
			}
		} catch (Exception e) {

		}
	}

	/**
	 * @todo 实际执行打印sql和参数
	 * @param sql
	 * @param paramValues
	 * @param isErrorOrWarn
	 */
	private static void printSql(String sql, Object[] paramValues, boolean isErrorOrWarn) {
		StringBuilder paramStr = new StringBuilder();
		boolean isDebug = logger.isDebugEnabled();
		if (paramValues != null) {
			for (int i = 0; i < paramValues.length; i++) {
				if (i > 0) {
					paramStr.append(",");
				}
				paramStr.append("p[" + i + "]=" + paramValues[i]);
			}
		}
		SqlExecuteTrace sqlTrace = threadLocal.get();
		StringBuilder result = new StringBuilder();
		String uid = null;
		// 这里用system.out 的原因就是给开发者在开发阶段在控制台输出sql观察程序
		if (sqlTrace != null) {
			uid = sqlTrace.getUid();
			result.append("\n/*|执行类型=" + sqlTrace.getType());
			result.append("\n/*|代码定位=" + getFirstTrace());
			result.append("\n/*|sqlId=" + sqlTrace.getId());
		}
		result.append("\n/*|入参后sql:").append(fitSqlParams(sql, paramValues));
		result.append("\n/*|sql 参数:").append(StringUtil.isBlank(paramStr) ? "无参数" : paramStr);
		// 错误或警告
		if (isErrorOrWarn) {
			result.insert(0, "\n\n/*|----start error,UID=" + uid + "---------------------------------*/");
			result.append("\n/*|----end   error,UID=" + uid + "---------------------------------*/\n");
			logger.error(result.toString());
		} else {
			result.insert(0, "\n\n/*|----start debug,UID=" + uid + "---------------------------------*/");
			result.append("\n/*|----end   debug,UID=" + uid + "---------------------------------*/\n");
			if (isDebug) {
				logger.debug(result.toString());
			} else {
				out.println(result.toString());
			}
		}
	}

	/**
	 * 在执行结尾时记录日志
	 */
	private static void destroyLog() {
		try {
			SqlExecuteTrace sqlTrace = threadLocal.get();
			if (sqlTrace == null) {
				return;
			}
			long overTime = sqlTrace.getExecuteTime() - printSqlTimeoutMillis;
			// sql执行超过阀值记录日志为软件优化提供依据
			if (overTime >= 0 && sqlTrace.getStart() != null) {
				sqlTrace.setOverTime(true);
				sqlTrace.addLog("slowSql执行超时", "耗时(毫秒):{} >={} (阀值)!", sqlTrace.getExecuteTime(),
						printSqlTimeoutMillis);
			} else {
				sqlTrace.addLog("执行时长", "耗时:{} 毫秒 !", sqlTrace.getExecuteTime());
			}
			// 日志输出
			printLogs(sqlTrace);
		} catch (Exception e) {

		}
	}

	private static void printLogs(SqlExecuteTrace sqlTrace) {
		boolean printLog = false;
		String reportStatus = "成功!";
		if (sqlTrace.isOverTime()) {
			printLog = true;
			reportStatus = "执行超时!";
		}
		if (sqlTrace.isError()) {
			printLog = true;
			reportStatus = "发生异常错误!";
		}

		if (!printLog) {
			// sql中已经标记了#not_debug# 表示无需输出日志(一般针对缓存检测、缓存加载等,避免这些影响业务日志)
			if (!sqlTrace.isPrint()) {
				return;
			}
			if (!(debug || printSqlStrategy.equals("debug"))) {
				return;
			}
		}

		String uid = sqlTrace.getUid();
		StringBuilder result = new StringBuilder();
		result.append("\n/*|----------------------开始执行报告 --------------------------------------------------*/");
		result.append("\n/*|执行uid=" + uid);
		result.append("\n/*|执行状态=" + reportStatus);
		result.append("\n/*|执行类型=" + sqlTrace.getType());
		result.append("\n/*|代码定位=" + getFirstTrace());
		if (sqlTrace.getId() != null) {
			result.append("\n/*|sqlId=" + sqlTrace.getId());
		}
		List<SqlExecuteLog> executeLogs = sqlTrace.getExecuteLogs();
		int step = 0;
		for (SqlExecuteLog log : executeLogs) {
			step++;
			result.append("\n/*|---- 步骤 " + step + "," + log.getTopic() + "----------------");
			if (log.getType() == 0) {
				result.append("\n/*|入参后sql:").append(fitSqlParams(log.getContent(), log.getArgs()));
				StringBuilder paramStr = new StringBuilder();
				if (log.getArgs() != null) {
					for (int i = 0; i < log.getArgs().length; i++) {
						if (i > 0) {
							paramStr.append(",");
						}
						paramStr.append("p[" + i + "]=" + log.getArgs()[i]);
					}
				}
				result.append("\n/*|sql参数:").append(StringUtil.isBlank(paramStr) ? "无参数" : paramStr);
			} else {
				result.append("\n/*|日志信息:").append(StringUtil.fillArgs(log.getContent(), log.getArgs()));
			}
		}
		result.append("\n/*|----------------------结束执行报告 --------------------------------------------------*/");
	}

	/**
	 * 清理线程中的数据
	 */
	public static void destroy() {
		// 执行完成时打印日志
		destroyLog();
		threadLocal.remove();
		threadLocal.set(null);
	}

	/**
	 * @param printSqlStrategy the printSqlStrategy to set
	 */
	public static void setPrintSqlStrategy(String printSqlStrategy) {
		SqlExecuteStat.printSqlStrategy = printSqlStrategy.toLowerCase();
	}

	/**
	 * @param debug the debug to set
	 */
	public static void setDebug(boolean debug) {
		SqlExecuteStat.debug = debug;
	}

	/**
	 * @param printSqlTimeoutMillis the printSqlTimeoutMillis to set
	 */
	public static void setPrintSqlTimeoutMillis(int printSqlTimeoutMillis) {
		SqlExecuteStat.printSqlTimeoutMillis = printSqlTimeoutMillis;
	}

	/**
	 * @TODO 将参数值拟合到sql中作为debug输出,便于开发进行调试(2020-06-15)
	 * @param sql
	 * @param params
	 * @return
	 */
	private static String fitSqlParams(String sql, Object[] params) {
		if (params == null || params.length == 0) {
			return sql;
		}
		StringBuilder lastSql = new StringBuilder();
		// 补空的目的在于迎合匹配规则
		Matcher matcher = ARG_PATTERN.matcher(sql.concat(" "));
		int start = 0;
		int end;
		int index = 0;
		int paramSize = params.length;
		Object paramValue;
		// 逐个查找?用实际参数值进行替换
		while (matcher.find(start)) {
			end = matcher.start() + 1;
			lastSql.append(sql.substring(start, end));
			if (index < paramSize) {
				paramValue = params[index];
				// 字符
				if (paramValue instanceof CharSequence) {
					lastSql.append("'" + paramValue + "'");
				} else if (paramValue instanceof Date || paramValue instanceof LocalDateTime) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "yyyy-MM-dd HH:mm:ss") + "'");
				} else if (paramValue instanceof LocalDate) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "yyyy-MM-dd") + "'");
				} else if (paramValue instanceof LocalTime) {
					lastSql.append("'" + DateUtil.formatDate(paramValue, "HH:mm:ss") + "'");
				} else if (paramValue instanceof Object[]) {
					lastSql.append(combineArray((Object[]) paramValue));
				} else if (paramValue instanceof Collection) {
					lastSql.append(combineArray(((Collection) paramValue).toArray()));
				} else {
					lastSql.append("" + paramValue);
				}
			} else {
				// 问号数量大于参数值数量,说明sql中存在写死的条件值里面存在问号,因此不再进行条件值拟合
				return sql;
			}
			// 正则匹配最后是\\W,所以要-1
			start = matcher.end() - 1;
			index++;
		}
		lastSql.append(sql.substring(start));
		return lastSql.toString();
	}

	/**
	 * @TODO 组合in参数
	 * @param array
	 * @return
	 */
	private static String combineArray(Object[] array) {
		if (array == null || array.length == 0) {
			return " null ";
		}
		StringBuilder result = new StringBuilder();
		Object value;
		for (int i = 0; i < array.length; i++) {
			if (i > 0) {
				result.append(",");
			}
			value = array[i];
			if (value instanceof CharSequence) {
				result.append("'" + value + "'");
			} else if (value instanceof Date || value instanceof LocalDateTime) {
				result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd HH:mm:ss") + "'");
			} else if (value instanceof LocalDate) {
				result.append("'" + DateUtil.formatDate(value, "yyyy-MM-dd") + "'");
			} else if (value instanceof LocalTime) {
				result.append("'" + DateUtil.formatDate(value, "HH:mm:ss") + "'");
			} else {
				result.append("" + value);
			}
		}
		return result.toString();
	}

	/**
	 * @TODO 定位第一个调用sqltoy的代码位置
	 * @return
	 */
	public static String getFirstTrace() {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		// StackTraceElement[] stackTraceElements=new Throwable().getStackTrace();

		String className = null;
		int lineNumber = 0;
		String method = null;
		StackTraceElement traceElement;
		int length = stackTraceElements.length;
		// 逆序
		for (int i = length - 1; i > 0; i--) {
			traceElement = stackTraceElements[i];
			className = traceElement.getClassName();
			// 进入调用sqltoy的代码，此时取上一个
			if (className.startsWith("org.sagacity.sqltoy")) {
				// 避免异常发生
				if (i + 1 < length) {
					traceElement = stackTraceElements[i + 1];
					className = traceElement.getClassName();
					method = traceElement.getMethodName();
					lineNumber = traceElement.getLineNumber();
				}
				break;
			}
		}
		return "" + className + "." + method + "[line:" + lineNumber + "]";
	}
}
