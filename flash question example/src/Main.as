
import adobe.utils.CustomActions;
import mx.collections.ArrayCollection;
import mx.controls.DataGrid;
import mx.events.FlexEvent;
import mx.utils.StringUtil;

// generic applet parameters from ILIAS 
var session_id:String;
var client:String;
var points_max:String;
var server:String;
var pass:String;
var active_id:String;
var question_id:String;

// question specific parameters from ILIAS
var intro:String;

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
	
	// load user defined parameters from ILIAS 
	intro = params["intro"];		// example ILIAS parameter intro (must be defined in the question settings)

	questiontext.text = "session_id = " + session_id + "\n";
	questiontext.text += "client = " + client + "\n";
	questiontext.text += "points_max = " + points_max + "\n";
	questiontext.text += "server = " + server + "\n";
	questiontext.text += "pass = " + pass + "\n";
	questiontext.text += "active_id = " + active_id + "\n";
	questiontext.text += "question_id = " + question_id + "\n";
	questiontext.text += "intro = " + intro;
	
	ILIASServer.wsdl = server;
	ILIASServer.loadWSDL();
	ILIASServer.getQuestionSolution(session_id + "::" + client, active_id, question_id, pass);
}

public function submit():void
{
	
	// this is the user answer
	var userQuery:String = queryinput.text;
	
	// here goes the logic to determine the points for ther user's answer 
	// Example: 1 points if number of characters is even; 0 points otherwise 
	var points:int = ((userQuery.length % 2)  == 0) ? 1 : 0;
	
	statusfield.text = "Sending to ILIAS: " + points + " out of " + points_max + " points";

	// call ILIAS SOAP service to save question result
	ILIASServer.saveQuestion(session_id + "::" + client, active_id, question_id, pass, [userQuery, points_max, points]);
}

/*
 * Callback function for getting result stored in ILIAS
 * event.result = [user_answer, points_max, points]
*/ 

protected function ILIAS_getPreviousSolution(event:ResultEvent):void
{
	if (event.result.length == 0) return;
	queryinput.text = event.result[0];
}


/*
 * callback functions for ILIAS SOAP responses 
*/

protected function ILIAS_result(event:ResultEvent):void
{
	statusfield.text += "\n[ILIAS:OK]\n" + event.result.toString();
}

protected function ILIAS_fault(event:FaultEvent):void
{
	statusfield.text += "\n[ILIAS:FAULT]\n" + event.toString();
}


