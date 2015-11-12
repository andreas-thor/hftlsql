package hftlsql.server.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import javax.servlet.ServletContext;


public class QueryResult {

	public boolean executed = true;
	public String sqlError = "";
	public int columnCount = -1;
	public ArrayList<String> columnTypes;
	public ArrayList<String> columnNames;
	public ArrayList<String[]> rows;
	
	
	
	public QueryResult(String table) {
		
		columnTypes = new ArrayList<String>();
		columnNames = new ArrayList<String>();
		
		String[] rows_String = table.split("\n", -1);
		
		// header processing: we ignore empty column names
		String[] header = rows_String[0].split("\t", -1);
		ArrayList<Integer> colIndex = new ArrayList<Integer>();	// mapping table column -> result column 
		for (int i=0; i<header.length; i++) {
			if (!header[i].trim().equals("")) {
				colIndex.add(i);
				columnNames.add (header[i].trim().toUpperCase());
				columnTypes.add ("BIGINT");
			}
		}
		columnCount = columnNames.size();
		 
		// rows: we ignore empty rows
		rows = new ArrayList<String[]>();
		for (int i=1; i<rows_String.length; i++) {
			if (rows_String[i].trim().equals("")) continue;
			String[] tableRow = rows_String[i].split("\t", -1);
			String[] row = new String[columnCount];
			for (int k=0; k<columnCount; k++) {
				row[k] = tableRow[colIndex.get(k)].trim();
			}
			rows.add(row);
		}
	}
	
	

	/**
	 * Execute query and store query result (data and metadata) for later comparison
	 * @param conn
	 * @param query
	 * @throws Exception
	 */
	public QueryResult(Connection conn, String schema, String query, ServletContext logContext, Date timestamp) throws Exception {
		
        Statement stmt = conn.createStatement();
        ResultSet selectRS = null;
        try {
        	System.out.println("SET SCHEMA " + schema);
        	stmt.executeUpdate("SET SCHEMA " + schema);
        	// set query timeout to avoid long-ruuning queries that slow down the web server
        	System.out.println("SET QUERY_TIMEOUT 5000");
        	stmt.executeUpdate("SET QUERY_TIMEOUT 5000"); 
        	System.out.println("Execute Query" + query);
			selectRS = stmt.executeQuery(query);
        	System.out.println("Done");
			
			columnCount = selectRS.getMetaData().getColumnCount();
			columnTypes = new ArrayList<String>();
			columnNames = new ArrayList<String>();
			for (int i=0; i<columnCount; i++) {
				columnTypes.add(selectRS.getMetaData().getColumnTypeName(i+1).toUpperCase());
				columnNames.add(selectRS.getMetaData().getColumnName(i+1).toUpperCase());
			}
			
			int count = 0;	
			rows = new ArrayList<String[]>();
			while (selectRS.next()) {
				String[] row = new String[columnCount];
				for (int i=0; i<columnCount; i++) row[i] = selectRS.getString(i+1);
				rows.add(row);
				
				if (++count == 10000) break; // count the number and stop after 10,000 rows to prevent heap space overflow
			}
			
		} catch (SQLException e) {
			executed = false;
			sqlError = e.getMessage();
			logContext.log(timestamp + "@QueryResult: SQLException\n" + sqlError);
//			System.out.println(sqlError);
		} finally {
			if (selectRS != null) selectRS.close();
			if (stmt != null) stmt.close();
			logContext.log(timestamp + "@QueryResult: ResultSet and Statement closed");
		}
	}

	

	

	
	/**
	 * Compute the difference between two query results (this.qr and parameter.qr)
	 * @param qr the Queryresult for comparison
	 * @param sortRelevant is sort order relevant? (true for queries that require ORDER BY)
	 * @param checkDatatypes 
	 * @param checkColumnNames column names are checked; allows for query results having different column orders 
	 * @return 
	 */
	public QueryDiff getDifference (QueryResult qr, boolean sortRelevant, boolean checkDatatypes, boolean checkColumnNames) {

		// check for valid results
		if (!this.executed) return new QueryDiff(String.format("Query1 konnte nicht ausgefuehrt werden. (%s)", this.sqlError));
		if (!qr.executed)   return new QueryDiff(String.format("Query2 konnte nicht ausgefuehrt werden. (%s)", qr.sqlError));

		// check for correct result size
		if (this.rows.size() != qr.rows.size()) return new QueryDiff(String.format ("Unterschiedliche Anzahl an Zeilen (%d vs. %d)", this.rows.size(), qr.rows.size()));
		if (this.columnCount != qr.columnCount) return new QueryDiff(String.format ("Unterschiedliche Anzahl an Spalten (%d vs. %d)", this.columnCount, qr.columnCount));
		
		// determine column mapping
		int[] colMap = new int[this.columnCount];	// colMap[x] = y ... column #x of this equals column #y of qr
		for (int col=0; col<this.columnCount; col++) {
			colMap[col] = checkColumnNames ? qr.columnNames.indexOf(this.columnNames.get(col)): col;
			if (colMap[col] == -1) return new QueryDiff(String.format ("Unterschiedliche Spaltennamen; Spalte %s nicht gefunden", this.columnNames.get(col)));
		}
		
		// check for data types
		if (checkDatatypes) {
			for (int col=0; col<this.columnCount; col++) {
				if (! this.columnTypes.get(col).equalsIgnoreCase(qr.columnTypes.get(colMap[col]))) {
					return new QueryDiff(String.format ("Unterschiedliche Spalten-Datentypen (%s vs. %s)", this.columnTypes.get(col), qr.columnTypes.get(colMap[col])));
				}
			}
		}
		
		// generate rows as string concatenation for easy comparison
		ArrayList<String> rowComp = new ArrayList<String>();
		for (String[] row: qr.rows) {
			String[] rowMapped = new String[row.length];
			for (int col=0; col<row.length; col++) {
				rowMapped[col] = row[colMap[col]];
			}
			rowComp.add(Arrays.toString(rowMapped).toLowerCase());
		}
		
		// check for equality
		boolean isSorted = true;
		for (int i=0; i<this.rows.size(); i++) {
			String[] row = this.rows.get(i);
			int index = rowComp.indexOf(Arrays.toString(row).toLowerCase());
			if (index == -1) return new QueryDiff(String.format ("Unterschiedliche Tupel; Tupel %s nicht gefunden", Arrays.toString(row)));
//			System.out.println("i=" + i + " and index="+ index);
			isSorted &= Arrays.toString(row).toLowerCase().equals(rowComp.get(i));	  // check for "i == index" may fail if there are multiple equal rows  
		}
		
		
		return (isSorted || !sortRelevant) ? new QueryDiff("Identisches Ergebnis!",true) : new QueryDiff("Identische Tupelmenge, aber unterschiedliche Reihenfolge!");
	}
	
	
}
