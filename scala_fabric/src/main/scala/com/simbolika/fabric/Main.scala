// 

package com.simbolika.fabric


//import com.simbolika.neo4j
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global
import org.neo4j.driver.v1._
import scala.collection.mutable.ListBuffer
import sys.process._
import java.io.ByteArrayInputStream
import java.io.File
import com.typesafe.config.ConfigFactory


import scala.concurrent.duration._

/*
** Key methods:
        - start(sender,self)
*/
class TaskGraph() {
}

class NeoTaskGraph(job_text: String) extends TaskGraph {
  var sessions = scala.collection.mutable.Map[Int, org.neo4j.driver.v1.Session]()
  val JOB_DATA_SEP = "!!"
  var job_data = ""
  val system = ActorSystem("sentient_fabric")
  val log = system.log
  val driver = GraphDatabase.driver("bolt://localhost/7687")
  val session = driver.session
  val timestamp: Long = System.currentTimeMillis / 1000
  var job_id = 0
//  val valid = valid_job()
  val jin = createJobInstanceNode()
  createTaskInstanceNode()
  private var statev = "waiting"
  private var self_id: String = "step"
  set_state("waiting")
  def createTaskInstanceNode() = {
    tg_log(s"create ti for $jin")
    val result = session.run(s"MATCH (ji:JobInstance)<-[r:HAS_JOBINSTANCE]-(j:Job)<-[:Parent]-(t:Task) where id(ji)= $jin  MERGE (t)-[rti:HAS_TASKINSTANCE]->(ti:TaskInstance {jid: id(ji), name: t.name+' instance', timestamp: $timestamp, state: 'init'}) RETURN id(ti) as tiid, t.name as tname")  
    while (result.hasNext()) {
      val record = result.next()
       val ti = record.get("tiid").asInt()
       val tname = record.get("tname").asString()
       val task_instance: ActorRef = system.actorOf(Props(new TaskInstance(ti, this)), TaskInstanceName(ti))
       task_instance ! Map("function"->"start","output"->"")
    }
  }
  def TaskInstancePath(tiid: Int): String = {
      "/user/$job_name/"+TaskInstanceName(tiid)
  }
  def TaskInstanceName(tiid: Int): String = {
      s"task-$tiid"
  }
  def taskError(tiid: Int, message: String) = {
      tg_log(s"task $tiid failed, message = $message")
      val result = session.run(s"MATCH (ti:TaskInstance) WHERE id(ti) = $tiid SET ti.state='failed', ti.fail_message='$message'")  
  }
  def taskSession(tiid: Int): org.neo4j.driver.v1.Session = {
    tg_log(s"retrieve session for task $tiid")
    if (sessions.contains(tiid)) {
	  return sessions(tiid)
	} else {
      val driver = GraphDatabase.driver("bolt://localhost/7687")
	  sessions(tiid) = driver.session
	  return driver.session
	} 
  }
  def getJobData(): String = {
    if (job_data == "") {
      return ""
    }
    job_data.replace("{","").replace("}","")+","
  }
  def createJobInstanceNode(): Int = {
    var arguments = ""
    var job = ""
    tg_log(s"creating ji: $job_text, $JOB_DATA_SEP")
    if (job_text contains JOB_DATA_SEP) {
      val data_lst = job_text.split(JOB_DATA_SEP)
      println(data_lst.mkString(","))
      job = data_lst(0)
      arguments = data_lst(1)
      job_data = arguments
    }
    else {
      job = job_text
      arguments = ""
    }
    if (job contains "CREATE") {
	  tg_log(s"CREATE: $job")
      val result = session.run(job)
      val job_id = result.next().get("job_instance").asInt()
	  tg_log(s"new job: $job_id")
      val result1 = session.run(s"MATCH (j:Job) where id(j)= $job_id  MERGE (j)-[r:HAS_JOBINSTANCE]->(ji:JobInstance {name: j.name+' instance', timestamp: $timestamp, ji_data: '$arguments'}) RETURN id(ji) AS job_instance")  
      return result1.next().get("job_instance").asInt()
	}
	tg_log(s"lookup job by name: $job")
	val cypher = s"MATCH (j:Job) where j.name= '$job'  MERGE (j)-[r:HAS_JOBINSTANCE]->(ji:JobInstance {name: j.name+' instance', timestamp: $timestamp, ji_data: '$arguments'}) RETURN id(ji) AS job_instance"
    tg_log(s"create ji: $cypher")
    val result2 = session.run(cypher)  
    return result2.next().get("job_instance").asInt()
  }
  def set_self(self_name: String) = {
    self_id = self_name
  }
  def terminate() = {
    session.close()
    driver.close()
  }
  def get_job_id(name: String): Int = {
    val result = session.run(s"MATCH (j:Job) WHERE j.name = '$name' RETURN id(j) AS job")
    if (result.hasNext()) {
      val record = result.next()
      return record.get("job").asInt()
    }
    return -1
  }
  def valid_job(): Boolean = {
    val result = session.run(s"MATCH (j:Job) WHERE id(j) = $job_id RETURN j.name AS job")
    if (result.hasNext()) {
      val record = result.next()
      tg_log(record.get("job").asString())
      return true
    }
    tg_log(s"$job_id not valid")
    false
  }
  def format_service(tiid: Int): String = {
    println(s"format_service: $tiid")
    val result = taskSession(tiid).run(s"MATCH (ti:TaskInstance)-[]-(t:Task) WHERE id(ti) = $tiid RETURN id(t) as svc_id, t.endPoint AS endpoint")
    if (result.hasNext()) {
      val record = result.next()
      var endpt = record.get("endpoint").asString()
	  endpt = "python3 " + endpt
	  tg_log(s"found service endpoint: $endpt")
      var svc_id = record.get("svc_id").asInt()
      val result1 = taskSession(tiid).run(s"MATCH (s:Service)-[]->(p:Parameter) WHERE id(s) = $svc_id RETURN p.name as name, p.value AS value")
      while (result1.hasNext()) {
        val record = result1.next()
        val name = record.get("name").asString()
        val value = record.get("value").asString()
		tg_log(s"proccessing parameter: $name $value")
         endpt += " --"+name+" "+value
      }
      val fname = ConfigFactory.load().getString("ff.job")
      val ff_home =  ConfigFactory.load().getString("ff.ff_home")
      log.info("cypher: $fname")
      val endpt_final = endpt.replace("$ff_home",ff_home)
      return endpt_final
    }
    "none"
  }
  def task_succ_list(tiid: Int): List[Int] = { 
    tg_log(s"succ for $tiid")
    val tsession = taskSession(tiid)
    val result = tsession.run(s"MATCH (ti1:TaskInstance)-[]-(t1:Task)<-[]-(t2:Task)-[]-(ti2:TaskInstance) WHERE id(ti2) = $tiid and ti1.timestamp=ti2.timestamp RETURN id(ti1) AS tid")
    var tasks = new ListBuffer[Int]()
    while (result.hasNext()) {
      val record = result.next()
       tasks += record.get("tid").asInt() 
    }
    return tasks.toList
  }
  def task_pred_list(tiid: Int): List[Int] = {
    tg_log("pred")
    val tsession = taskSession(tiid)
    val result = session.run(s"MATCH (ti1:TaskInstance)-[]-(t1:Task)-[]->(t2:Task)-[]-(ti2:TaskInstance) WHERE id(ti2) = $tiid and ti1.timestamp=ti2.timestamp RETURN id(ti1) AS tid")
    var tasks = new ListBuffer[Int]()
    while (result.hasNext()) {
      val record = result.next()
       tasks += record.get("tid").asInt() 
    }
    return tasks.toList
  }
  def task_list(): List[String] = {
    val result = session.run(s"MATCH (j:Job)-[]-(t:Task) WHERE id(j) = $job_id RETURN t.name AS name")
    var tasks = new ListBuffer[String]()
    while (result.hasNext()) {
      val record = result.next()
       tasks += record.get("name").asString() 
    }
    return tasks.toList
    
  }
  def display(tiid: Int): Boolean = {
    val tsession = taskSession(tiid)
    val result = tsession.run(s"MATCH (ti:TaskInstance) OPTIONAL MATCH (ti)-[]-(t:Task)-[]-(s:Service)-[]-(arg:Parameter) WHERE id(ti) = $tiid RETURN arg as arg, ti.state AS state")
    while (result.hasNext()) {
      val ks = result.keys()
      tg_log(s"keys: $ks")
      val record = result.next()
        val ti_state = record.get("state").asString()
        tg_log(s"ti: $ti_state")
        
        return true
    }
    tg_log("ti not found")
    return false
  }
  def show() { println("show self: ",self_id) }
  def task_complete(name: String) { set_task_state(name,"complete") }
  def task_status(name: String) = {  }
  def task_pred(name: String): List[String] = { 
    val script = s"MATCH (j:Job)-[]-(t1:Task)<-[]-(t2:Task)-[]-(j) WHERE id(j) = $job_id and t2.name = '$name' RETURN t1.name AS name"
    val result = session.run(script)
    if (result.hasNext()) {
      val record = result.next()
      tg_log(record.get("name").asString())
      return List("x")
    }
    List("none")
  }
  def task_succ(name: String) = { }
  def task_details(name: String) = { }
  def get_tasks() = { }
  
  def state() = { 
//    println(s"$this $self_id: self state $statev +++++++++++++++++++++++++++++++++++++++")
    statev
  }
  def set_task_state(task_name: String, next: String) = { }
  
  def set_state(next: String) = {
      tg_log(s"$self_id: changing self state from $statev to $next")
      statev = next
//      if (self_id != null) {
//        status(self_id) = statev
 //     }
  }
  def pred() = { 
  }

  def set_running(tiid: Int): Boolean = {
    val tsession = taskSession(tiid)
    val result0 = tsession.run(s"MATCH (ti1:TaskInstance) WHERE id(ti1) = $tiid and ti1.state = 'init' RETURN id(ti1) AS task_id, ti1.state as state")
    if (result0.hasNext()) {
      val record = result0.next()
//      tg_log("ti status:",record.get("task_id").asInt(),record.get("state").asString())
    } else {
      return false
    }
    val result = tsession.run(s"MATCH (ti1:TaskInstance)-[]-(t1:Task)<-[:succ]-(t2:Task)-[]-(ti2:TaskInstance) WHERE id(ti1) = $tiid and ti1.timestamp=ti2.timestamp and ti2.state <> 'complete' RETURN id(ti2) AS waiting, ti2.state as state")
    if (result.hasNext()) {
      val record = result.next()
//      tg_log("waiting on:",record.get("waiting").asInt(),record.get("state").asString())
      return false
    }
    tg_log(s"task can run")
    val result1 = tsession.run(s"MATCH (ti1:TaskInstance) WHERE id(ti1) = $tiid SET ti1.state='running' ")
	true
  }
  def set_complete(tiid: Int): List[Int] = {
    val tsession = taskSession(tiid)
    tg_log(s"mark task as complete $tiid")
    val result = tsession.run(s"MATCH (ti1:TaskInstance) WHERE id(ti1) = $tiid SET ti1.state='complete' ")
    return task_succ_list(tiid)
  }
  def succ() = { }
  def details() = { }
  def ready(self_name: String) = { 
  } 
  def tg_log(txt: String) = {
//     log.info(txt)
  }
}

import scala.io.Source

object Main extends App {


  val system = ActorSystem("sentient_fabric")

  system.actorOf(Props(new SFM()), "root")
}


class SFM() extends Actor with akka.actor.ActorLogging {

  import context._
  import akka.event.Logging
  import akka.event.LoggingAdapter

//  var log = Logging.getLogger(getContext().system(), this)
  log.info("logger test")
   
  val fname = ConfigFactory.load().getString("ff.job")
  val ff_home =  ConfigFactory.load().getString("ff.ff_home")
  log.info("cypher: $fname")
  val cypher = Source.fromFile(fname).getLines.mkString
  val cypher1 = cypher.replace("$ff_home",ff_home)
  log.info(s"text: $cypher1")


  val job1: ActorRef = system.actorOf(Props(new JobInstance(cypher1)), "job1")
  
  
//This will schedule to send the Tick-message
//to the tickActor after 0ms repeating every 50ms
val cancellable =
  system.scheduler.schedule(
    0 milliseconds,
    5000 milliseconds,
    job1,
    "tick")
  
  def receive = {
    case "tick" => sender ! ""
  }
  
}


class Experiment(exp_data: String) extends Actor with akka.actor.ActorLogging {

// name::{<a;b;c>}

  import context._
  val name = self.path.name
  log.info(s"$name create Experiment data=$exp_data")
//  log.info(tg.task_list())

   val uuid = java.util.UUID.randomUUID.toString  
   var jname = exp_data
   var job: ActorRef = system.actorOf(Props(new JobInstance(jname)), "job"+uuid)
  
  system.scheduler.scheduleOnce(5.seconds) {
      println(s"$name: Experiment heartbeat!")
  }

  def receive = {
    case "tick" => 
      println(s"$name: Experiment heartbeat!!")
  }
  
}

class JobInstance(task_graph: String) extends Actor with akka.actor.ActorLogging {

  import context._
  val name = self.path.name
  log.info(s"$name Job starting id=$task_graph")
  var tg = new NeoTaskGraph(task_graph)
//  log.info(tg.task_list())
  log.info("job id:",tg.get_job_id("job1"))
  
  system.scheduler.scheduleOnce(5.seconds) {
      println(s"$name: Job heartbeat!")
  }

  def receive = {
    case "tick" => 
//      println(s"$name: Job heartbeat!!")
  }
  
}


class TaskInstance(tiid: Int, tg: NeoTaskGraph) extends Actor with akka.actor.ActorLogging {
  import context._

//  private[this] var tg = tg1
  private[this] var self_id = "deleteme"
  private[this] var statev = "waiting"
  var task_input: String = ""
  var task_output: String = ""
  log.info(s"$tiid Task initializing")
//  tg.display(tiid)


val cancellable =
  system.scheduler.schedule(
    0 milliseconds,
    5000 milliseconds,
    self,
    "tock")
	
  def extractJson(inp: String): String = {
      var strout = inp
      if (inp != "null" && inp != "") {
        strout = inp.split("<app_data>\\{")(1).split("\\}</app_data>")(0) + ","
	  }
       else {
	  strout = ""
       }
//      println(s"strout: $strout")
	  strout
	}
  
  def wrapJson(inp: String): String = {
    log.info(s"wrapping input: $inp")
    if (inp != "null") {
      return "<app_data>{"+inp+"\"_terminator\":\"null\"}</app_data>"
	}
	return inp
  }

// receive and execute asynchronous command from process if present
  
  def processLine(line: String): String = {
  println("rcvd:",line)
  val uuid = java.util.UUID.randomUUID.toString
  if (line contains "create_job") {
   var lst = line.split("::")
   var cypher = lst(1)
   println(s"execute ($cypher)")
   var job: ActorRef = system.actorOf(Props(new JobInstance(cypher)), "job-"+uuid)
 
  }
  else if (line contains "create_experiment") {
   var lst = line.split("::")
   var jname = lst(1)
   var job: ActorRef = system.actorOf(Props(new Experiment(jname)), "exp-"+uuid)
  }
  else if (line contains "run_job") {
   var lst = line.split("::")
   var jname = lst(1)
   var job: ActorRef = system.actorOf(Props(new JobInstance(jname)), "job-"+uuid)
  }
  else if (line contains "rcv_data") {}
  line    // return received text 
}

  def subVars(inp: String): String = {
    val ff_home =  ConfigFactory.load().getString("ff.ff_home")
    inp.replace("$ff_home",ff_home)
  }
	
  def executeProcess(cmd: String, inp: String): String = {
     var txt: String = ""
     var err_txt: String = ""
	 log.info(s"execute command: $cmd")
     val calcProc = cmd.run(new ProcessIO(
      // Handle subprocess's stdin
      // (which we write via an OutputStream)
      in => {
        val writer = new java.io.PrintWriter(in)
        writer.println(inp)
        writer.close()
      },
      // Handle subprocess's stdout
      // (which we read via an InputStream)
      out => {
        val src = scala.io.Source.fromInputStream(out)
        for (line <- src.getLines()) {
		  txt += processLine(line)
        }
        src.close()
      },
      // We don't want to use stderr, so just close it
//      stderr => { err_txt = scala.io.Source.fromInputStream(stderr).mkString
 //                 stderr.close() }
      stderr => {
        val err = scala.io.Source.fromInputStream(stderr)
        for (line <- err.getLines()) {
		  println(line)
        }
        err.close()  
        }
    ))

    // Using ProcessBuilder.run() will automatically launch
    // a new thread for the input/output routines passed to ProcessIO.
    // We just need to wait for it to finish.

    val code = calcProc.exitValue()
	if (code != 0) {
  	  log.info(s"error:$cmd:$code:$err_txt: $txt")
	  throw new Exception(txt)
	}
	txt
  }


  def receive = {
   case "tock" =>
//     log.info(s"$tiid: rcvd tock")
     tg.display(tiid)
   case "init" =>
      log.info(s"$tiid init")
      log.info(self_id)
	case map:Map[String, String] =>
//	  println(s"$tiid: map:::",map)
	  var input_stream = extractJson(map("output"))
//      println(s"cum input before $task_input")
//	  println(s"input before $input_stream")
	  task_input += input_stream						// concatenate all pred task output
//	  println(s"cum after $task_input")
	  var from = sender.path.name
	  log.info(s"$self_id: start received by $self_id from $from, state = $statev", input_stream)
	  if (tg.set_running(tiid)) {
	      input_stream = subVars(wrapJson(task_input+tg.getJobData()))
	      log.info(s"$tiid: task running")
	      val svc_call = tg.format_service(tiid)
          var successful: Boolean = false	      
//		  println(s"service: $svc_call")
		  println(s"$tiid: input: $input_stream")
          try { 
             task_output = executeProcess(svc_call, input_stream)
             successful = true
//            println("service output: ",task_output)
          } catch {
            case ex: Exception => 
			    task_output = "error"
				log.info(s"$tiid: task failed")
                tg.taskError(tiid, ex.getStackTraceString)
          }
          if (successful) {
              val send_list = tg.set_complete(tiid)
              log.info("send list: ",send_list)
              send_list.foreach(x => 
              { 
                if (x > 0) {
                  val thePath = "/user/job0/"+tg.TaskInstanceName(x)
                  log.info(s"$self_id: send start to $x:$thePath")
                  context.actorSelection("../*") ! Map("function"->"start","output"->task_output)
                } 
              })
            }
      }

    }
}



