
import adobe.utils.CustomActions;
import mx.collections.ArrayCollection;
import mx.controls.DataGrid;
import mx.events.FlexEvent;
import mx.utils.StringUtil;

var session_id:String;
var client:String;
var points_max:String;
var server:String;
var pass:String;
var active_id:String;
var question_id:String;

var schema:String;
var taskid:String;
var tasktype:String;

var dbserviceurl:String = "http://212.184.75.153/HFTLSQL/db";	// PRODUKTIV-Datenbankserver 
//  var dbserviceurl:String = "http://212.184.75.153/HFTLSQLTomcat/db";	// ENTWICKLUNGS-Datenbankserver 
// var dbserviceurl:String = "http://localhost:8080/HFTLSQL/db";	// lokaler Entwicklungsserver

import mx.controls.Alert;
import mx.rpc.http.HTTPService;
import mx.rpc.events.ResultEvent;
import mx.rpc.events.FaultEvent;

public function initapp(event:FlexEvent):void
{
	
	// load general parameters from ILIAS 
	var params:Object = this.root.loaderInfo.parameters;
	session_id = params["session_id"];
	client = params["client"];
	server = params["server"];
	pass = params["pass"];
	active_id = params["active_id"];
	question_id = params["question_id"];
	points_max = params["points_max"];
	
	// load user defined parameters from ILIAS */
	schema = params["schema"];		// refers to a database
	taskid = params["taskid"];		// determines specific task
	tasktype = params["tasktype"];	// possible values: sql, result, header
	
	
	   // set fixed values and print values for testing purposes
	   // session_id = "eb8bac58972958bad937d42b5ee7f132";
	   // client = "FHL-Master";
	   // server = "https://ilias.hft-leipzig.de/ilias/webservice/soap/server.php?wsdl";
 
	   // pass = "5";
	   // active_id  = "5903";
	   // question_id = "6525";
	   // points_max = "1";

	   // schema = "bibo";  tasktype = "sql"; taskid = "3";
	   // schema = "mondial";  tasktype = "sql"; taskid = "1";
	   // schema = "auto_HP"; tasktype = "result";  taskid = "3";
	   // schema = "flug_2NF";  tasktype = "header";  taskid = "3";
   
	   //queryinput.text = "session_id = " + session_id + "\n";
	   //queryinput.text += "client = " + client + "\n";
	   //queryinput.text += "points_max = " + points_max + "\n";
	   //queryinput.text += "server = " + server + "\n";
	   //queryinput.text += "pass = " + pass + "\n";
	   //queryinput.text += "active_id = " + active_id + "\n";
	   //queryinput.text += "question_id = " + question_id + "\n";
	
	getTaskInfo();
	ILIASServer.getQuestionSolution(session_id + "::" + client, active_id, question_id, pass);
}

public function submit():void
{
	
	var userQuery:String = queryinput.text;
	
	statusfield.text = "sending";
	icon_ok.visible = false;
	icon_not_ok.visible = false;
	icon_wait.visible = true;
	
	// prepare HTTP request to Database server
	var service:HTTPService = new HTTPService();
	service.url = dbserviceurl + "/gettaskresult";
	service.method = "POST";
	var parameters:Object = new Object();
	parameters["time"] = new Date().getTime();
	parameters["schema"] = schema;
	parameters["tasktype"] = tasktype;
	parameters["taskid"] = taskid;
	
	var userResult:String;
	
	if (tasktype == "sql") {
		userResult = queryinput.text;
		parameters["query"] = userResult;
	}
	
	// serialize table result to CSV format (lines separated by \n, fields separated by \t)
	if ((tasktype == "result") || (tasktype == "header")) {
		
		var t:ArrayCollection = arrCollHeader;
		userResult = t.getItemAt(0).A + "\t" + t.getItemAt(0).B + "\t" + t.getItemAt(0).C + "\t" + t.getItemAt(0).D + "\t" + t.getItemAt(0).E + "\t" + t.getItemAt(0).F;
		
		t = arrColl;
		for (var i:int = 0; i < 8; i++) {
			userResult = userResult + "\n" + t.getItemAt(i).A + "\t" + t.getItemAt(i).B + "\t" + t.getItemAt(i).C + "\t" + t.getItemAt(i).D + "\t" + t.getItemAt(i).E + "\t" + t.getItemAt(i).F;			
		}
		
		parameters["table"] = userResult; 
	}
	
	service.resultFormat = "flashvars";
	service.addEventListener("result", function httpResult(event:ResultEvent):void
		{
			var result:Object = event.result;
			
			// show db service response info-value in status field and show corresponding icon
			statusfield.text = result["info"];
			icon_wait.visible = false;
			if (result["points"] == "1") icon_ok.visible = true;
			if (result["points"] == "0") icon_not_ok.visible = true;
			
			// call ILIAS SOAP service to save question result
			ILIASServer.saveQuestion(session_id + "::" + client, active_id, question_id, pass, [userResult, points_max, result["points"]]);
		});
	service.addEventListener("fault", httpFault);
	service.send(parameters);
}

/*
 * Call DB service to retrieve task info 
 */

public function getTaskInfo():void
{
	
	statusfield.text = "getTaskInfo";
	
	// prepare HTTP request to Database server
	var service:HTTPService = new HTTPService();
	service.url = dbserviceurl + "/gettaskinfo";
	service.method = "POST";
	var parameters:Object = new Object();
	parameters["schema"] = schema;
	parameters["tasktype"] = tasktype;
	parameters["taskid"] = taskid;
	
	service.resultFormat = "flashvars";
	service.addEventListener("result", function httpResult(event:ResultEvent):void
		{
			var result:Object = event.result;
			
			// for all tasks: show text (question) and status infos
			questiontext.text = result["text"];
			statusfield.text = result["info"];
			
			// input field for sql queries
			if (tasktype == "sql") {
				queryinput.visible = true;
				tableheader.visible = false;
				tablebody.visible = false;
			}
			
			// data table for other types (show just the first line for type header)
			if ((tasktype == "result") || (tasktype == "header")) {
				queryinput.visible = false;
				tableheader.visible = true;
				tablebody.visible = (tasktype == "result");
				questiontext.height = 70;
			}
			
			
		});
	service.addEventListener("fault", httpFault);
	service.send(parameters);
}

/*
 * Callback function for getting result stored in ILIAS
 * event.result = [user_answer, points_max, points]
*/ 

protected function ILIAS_getPreviousSolution(event:ResultEvent):void
{

	if (event.result.length == 0) return;
	
	// show previous sql query
	if (tasktype == "sql") {
		queryinput.text += event.result[0];
	} 

	// fill data table (deserialize from string); result is stored in csv format (lines separated by \n, fields separated by \t)
	if ((tasktype == "result") || (tasktype == "header")) {
		
		var rows:Array = event.result[0].split("\n");
		var o:Object;
		
		// Header
		if (rows.length>0) {
			var t:ArrayCollection = arrCollHeader;
			var row:Array = rows[0].split("\t");
			o  = t.getItemAt(0);
			o.A = StringUtil.trim (row[0]);
			o.B = StringUtil.trim (row[1]);
			o.C = StringUtil.trim (row[2]);
			o.D = StringUtil.trim (row[3]);
			o.E = StringUtil.trim (row[4]);
			o.F = StringUtil.trim (row[5]);
			t.setItemAt(o, 0);
		}
		
		// Body
		t = arrColl;
		for (var i:int = 1; i < rows.length; i++) {
			row = rows[i].split("\t");
			o = t.getItemAt(i-1);
			o.A = StringUtil.trim (row[0]);
			o.B = StringUtil.trim (row[1]);
			o.C = StringUtil.trim (row[2]);
			o.D = StringUtil.trim (row[3]);
			o.E = StringUtil.trim (row[4]);
			o.F = StringUtil.trim (row[5]);
			t.setItemAt(o, i-1);
		}
	}

	// show icons depending on result 
	icon_ok.visible = (event.result[2] == "1");
	icon_not_ok.visible = (event.result[2] == "0");
	icon_wait.visible = false;
}


/*
 * callback functions for ILIAS SOAP responses 
*/

protected function ILIAS_result(event:ResultEvent):void
{
	statusfield.text += "\n[ILIAS:OK]\n" + "\n" + event.result.toString();
}

protected function ILIAS_fault(event:FaultEvent):void
{
	statusfield.text += "\n[ILIAS:FAULT]\n" + event.toString();
}

/*
 * callback functions for HTTP response to Database server
*/

public function httpFault(event:FaultEvent):void
{
	statusfield.text += "\n[HTTP:FAULT]\n" + event.toString();
	statusfield.text += "\n[HTTP:FAULT2]\n" + event.fault.toString();
}


