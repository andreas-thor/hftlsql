package hftlsql.server.sql;

public class QueryDiff {

	public String diffExplanation;
	public boolean identical;
	
	
	public QueryDiff(String diff, boolean identical) {
		super();
		this.diffExplanation = diff;
		this.identical = identical;
	}
	
	public QueryDiff(String diff) {
		super();
		this.diffExplanation = diff;
		this.identical = false;
	}
	
}
