#Example ILIAS Flash Question

##src/Main.as
* contains question logic

##scr/Main.xml
* XML description of the user interface
* Specify SOAP WSD URI for ILIAS webservice

##ILIAS bug?
SOAP call of getQuestionSolutionreturns always returns the suer answer from last test (it seems to ignore the pass parameter)