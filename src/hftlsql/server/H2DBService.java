package hftlsql.server;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Properties;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.h2.tools.RunScript;

import hftlsql.server.sql.QueryDiff;
import hftlsql.server.sql.QueryResult;
public class H2DBService extends HttpServlet {
	

	private static final long serialVersionUID = 1L;

	private DataSource dataSource;
	
	private ServletContext logContext;
	private Date timestamp;
	
	public void init(ServletConfig config) throws ServletException {

		super.init(config);
		/* Connection pool for H2 database */
		try {
			Context initContext = new InitialContext();
			Context envContext = (Context) initContext.lookup("java:/comp/env");
			dataSource = (DataSource) envContext.lookup("jdbc/testdb");
		} catch (NamingException e) {
			e.printStackTrace();
		}
		
		logContext = this.getServletContext();
		timestamp = new Date();
	} 

	
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		doPost(req, resp);
	}
	
	
	
	private String opCreateDB (Connection conn) {
		
		String result = "";
		String[] schemas = { "bibo", "auto_HP", "auto_VP", "auto_VR", "flug_2NF", "flug_3NF" }; 
		try {
			Set<String> files = getServletContext().getResourcePaths("/WEB-INF/data/");
			for (String file: files) {
				if (file.endsWith("_import.sql")) {
					Reader reader = new InputStreamReader(getServletContext().getResourceAsStream(file));
					RunScript.execute(conn, reader);
					result += "\nDatabase " + file + " created successfully.";
				}
			}
			return result;
		} catch (SQLException e) {
			logContext.log(timestamp + "@opCreateDB: Exception\n" + e.toString());
//			e.printStackTrace();
			return "Could not create database.\n" + e.toString();
//		} catch (IOException e) {
//			logContext.log(timestamp + "@opCreateDB: Exception\n" + e.toString());
	//		e.printStackTrace();
//			return "Could not read data directory.\n" + e.toString();
		}		
	}
	
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		
		timestamp = new Date();
		
		// super.doPost(req, resp);
		// resp.setCharacterEncoding("UTF-8");

		
		
		/* Make connection to H2 database */
		Connection conn; 
		try {
			conn = dataSource.getConnection();
		} catch (Exception ex) {
			resp.getWriter().print("info=Could not connect to database\n" + ex.toString());
			return;
		}
		
		
		String op = req.getPathInfo().toLowerCase();	// /gettaskinfo, /gettaskresult, or /createdb
		
		String tasktype = req.getParameter("tasktype");
		if (tasktype != null) tasktype = tasktype.toLowerCase();	// sql, result, or header
		
		String schema = req.getParameter("schema");					// bibo, auto_HP, auto_VP, auto_VR, flug_2NF, flug_3NF, ...
		
		String taskid = req.getParameter("taskid");		// task number (1, 2, ...)
		

		if (op.equals("/createdb")) {
			logContext.log(timestamp + "/createdb: START");
			resp.getWriter().print("info=" + opCreateDB(conn));
			logContext.log(timestamp + "/createdb: END");
		}
		
		
		if (op.equals("/gettaskinfo")) {
			try {
				logContext.log(timestamp + "/gettaskinfo: START");
				resp.getWriter().print("text=" + getProperties(schema + "_queries").getProperty(req.getParameter("taskid")+".T"));
				resp.getWriter().print("&info=" + getInfo(conn, schema));
				logContext.log(timestamp + "/gettaskinfo: END");
			} catch (Exception e) {
				logContext.log(timestamp + "/gettaskinfo: EXCEPTION");
			}
		}
			 
		if (op.equals("/gettaskresult")) {

			logContext.log(timestamp + "/gettaskresult: START");
			
			String query_corr = getProperties(schema + "_queries").getProperty(taskid + ".Q");
			boolean query_sort = getProperties(schema + "_queries").getProperty(taskid + ".S") == null ? false : getProperties(schema + "_queries").getProperty(taskid + ".S").equalsIgnoreCase("1");

			try {

				QueryDiff diff = null;

				if (tasktype.equals("sql")) {
					QueryResult userResult = new QueryResult(conn, schema, req.getParameter("query"), logContext, timestamp);
					diff = new QueryResult(conn, schema, query_corr, logContext, timestamp).getDifference(userResult, query_sort, true, false);
				}
				
				if (tasktype.equals("result") || tasktype.equals("header")) {
					QueryResult userResult = new QueryResult(req.getParameter("table"));
					diff = new QueryResult(conn, schema, query_corr, logContext, timestamp).getDifference(userResult, query_sort, false, true);
				}
				
				resp.getWriter().print("info=" + diff.diffExplanation);
				resp.getWriter().print("&points=" + (diff.identical ? "1" : "0"));
				
				logContext.log(timestamp + "/gettaskresult: END");
			} catch (Exception e) {
				logContext.log(timestamp + "/gettaskresult: EXCEPTION\n" + e.toString());
				// TODO Auto-generated catch block
//				e.printStackTrace();
			}
		}

		
		try {
			if (conn != null) conn.close();
			logContext.log(timestamp + "@doPost: Connection closed");
		} catch (Exception e) {
			logContext.log(timestamp + "@doPost: Connection closed Exception\n" + e.toString());
//			e.printStackTrace();
		}


	}
	

	
	private String getInfo (Connection con, String schema) throws SQLException {
		
		Statement stmt = null;
		ResultSet rs = null;
		try {
			stmt = con.createStatement();
			rs = stmt.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE table_schema = '" + schema.toUpperCase() + "'");
			rs.next();
			int noOfTables = rs.getInt(1);
			return "Ready (schema " + schema + " with " +  noOfTables + " tables)";
		} catch (SQLException ex) {
			return "ERROR: Could not connect to database\n" + ex.toString();
		} finally {
			if (rs != null) rs.close();
			if (stmt != null) stmt.close();
		}
	}

	
	
	/*
	 * Lazy loading of property files
	 */
	private Properties getProperties (String name) throws IOException {
		
		Object o = getServletContext().getAttribute("prop_" + name);
		if (o != null) return ((Properties)o);
		
		Properties p = new Properties();
		System.out.println("/WEB-INF/data/" + name + ".properties");
		p.load(getServletContext().getResourceAsStream("/WEB-INF/data/" + name + ".properties"));
		getServletContext().setAttribute("prop_" + name, p);
		logContext.log(timestamp + "@getProperties: loaded " + name);
		
		return p;
	}
	
	
//	@Override
//	public void destroy() {
//
//		Enumeration<?> attrNames=getServletContext().getAttributeNames();
//		while(attrNames.hasMoreElements()){
//			String attr=(String) attrNames.nextElement();
//			if (attr.startsWith("conn")) {
//				try {
//					((Connection)getServletContext().getAttribute(attr)).close();
//				} catch (SQLException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//		}		
//		
//	
//		
//	}
	

	
	
}
