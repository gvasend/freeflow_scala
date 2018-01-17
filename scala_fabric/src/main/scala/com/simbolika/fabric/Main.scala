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

import scala.concurrent.duration._

/*
** Key methods:
        - start(sender,self)
*/
class TaskGraph() {
}

class NeoTaskGraph(job_id: Int) extends TaskGraph {

  val system = ActorSystem("sentient_fabric")
  val driver = GraphDatabase.driver("bolt://localhost/7687")
  val session = driver.session
  val timestamp: Long = System.currentTimeMillis / 1000
  val valid = valid_job()
  val jin = createJobInstanceNode()
  createTaskInstanceNode()
  // org.neo4j.driver.internal.InternalSession
//  var sessions: Map[Int, org.neo4j.driver.v1.Session] = Map()
  var sessions: Map[Int, org.neo4j.driver.internal.InternalSession] = Map()
  private var statev = "waiting"
  private var self_id: String = "step"
  set_state("waiting")
  def createTaskInstanceNode() = {
    println(s"create ti for $jin")
    val result = session.run(s"MATCH (ji:JobInstance)<-[r:HAS_JOBINSTANCE]-(j:Job)<-[:Parent]-(t:Task) where id(ji)= $jin  MERGE (t)-[rti:HAS_TASKINSTANCE]->(ti:TaskInstance {jid: id(ji), name: t.name+' instance', timestamp: $timestamp, state: 'init'}) RETURN id(ti) as tiid, t.name as tname")  
    while (result.hasNext()) {
      val record = result.next()
       val ti = record.get("tiid").asInt()
       val tname = record.get("tname").asString()
       val task_instance: ActorRef = system.actorOf(Props(new TaskInstance(ti, this)), TaskInstanceName(ti))
       task_instance ! Map("function"->"start","output"->"null")
    }
  }
  def TaskInstancePath(tiid: Int): String = {
      "/user/$job_name/"+TaskInstanceName(tiid)
  }
  def TaskInstanceName(tiid: Int): String = {
      s"task-$tiid"
  }
  def taskError(tiid: Int, message: String) = {
      println(s"task $tiid failed, message = $message")
      val result = session.run(s"MATCH (ti:TaskInstance) WHERE id(ti) = $tiid SET ti.state='failed', ti.fail_message='$message'")  
  }
  def taskSession(tiid: Int): org.neo4j.driver.v1.Session = {
    if (sessions.contains(tiid)) {
	  return sessions(tiid)
	} else {
      val driver = GraphDatabase.driver("bolt://localhost/7687")
	  println(driver.session)
	  sessions(tiid) = driver.session
	  return driver.session
	} 
  }
  def createJobInstanceNode(): Int = {
    val result = session.run(s"MATCH (j:Job) where id(j)= $job_id  MERGE (j)-[r:HAS_JOBINSTANCE]->(ji:JobInstance {name: j.name+' instance', timestamp: $timestamp}) RETURN id(ji) AS job_instance")  
    return result.next().get("job_instance").asInt()
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
      println(record.get("job").asString())
      return true
    }
    println(s"$job_id not valid")
    false
  }
  def format_service(tiid: Int): String = {
    println(s"format_service: $tiid")
    val result = session.run(s"MATCH (ti:TaskInstance)-[]-(t:Task)-[]->(s:Service) WHERE id(ti) = $tiid RETURN id(s) as svc_id, s.endPoint AS endpoint")
    if (result.hasNext()) {
      val record = result.next()
      var endpt = record.get("endpoint").asString()
	  endpt = "python3 " + endpt
	  println("found service endpoint:",endpt)
      var svc_id = record.get("svc_id").asInt()
      val result1 = session.run(s"MATCH (s:Service)-[]->(p:Parameter) WHERE id(s) = $svc_id RETURN p.name as name, p.value AS value")
      while (result1.hasNext()) {
        val record = result1.next()
        val name = record.get("name").asString()
        val value = record.get("value").asInt()
		println("proccessing parameter: ",name,value)
         endpt += " --"+name+" "+value
      }
      return endpt
    }
    "none"
  }
  def task_succ_list(tiid: Int): List[Int] = { 
    println(s"succ for $tiid")
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
    println("pred")
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
    val result = tsession.run(s"MATCH (ti:TaskInstance) WHERE id(ti) = $tiid RETURN ti.state AS state")
    while (result.hasNext()) {
      val record = result.next()
        println("ti: ",record.get("state").asString())
        return true
    }
    println("ti not found")
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
      println(record.get("name").asString())
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
      println(s"$self_id: changing self state from $statev to $next")
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
      println("ti status:",record.get("task_id").asInt(),record.get("state").asString())
    } else {
      return false
    }
    val result = tsession.run(s"MATCH (ti1:TaskInstance)-[]-(t1:Task)<-[:succ]-(t2:Task)-[]-(ti2:TaskInstance) WHERE id(ti1) = $tiid and ti1.timestamp=ti2.timestamp and ti2.state <> 'complete' RETURN id(ti2) AS waiting, ti2.state as state")
    if (result.hasNext()) {
      val record = result.next()
      println("waiting on:",record.get("waiting").asInt(),record.get("state").asString())
      return false
    }
    println(s"task can run")
    val result1 = tsession.run(s"MATCH (ti1:TaskInstance) WHERE id(ti1) = $tiid SET ti1.state='running' ")
	true
  }
  def set_complete(tiid: Int): List[Int] = {
    val tsession = taskSession(tiid)
    println(s"mark task as complete $tiid")
    val result = tsession.run(s"MATCH (ti1:TaskInstance) WHERE id(ti1) = $tiid SET ti1.state='complete' ")
    return task_succ_list(tiid)
  }
  def succ() = { }
  def details() = { }
  def ready(self_name: String) = { 
  } 
}


object Main extends App {

  val system = ActorSystem("sentient_fabric")

 
  system.actorOf(Props(new SFM()), "root")
}


class SFM() extends Actor {

  import context._

val map1 = Map("step1"->Map("process"->"/home/gvasend/sk_step1","succ"->List("step2","step3"),"pred"->List("null")),
               "step2"->Map("process"->"sk_step2","pred"->List("step1"),"succ"->List("step3")),
               "step3"->Map("process"->"sk_step3","pred"->List("step2","step1"),"succ"->List("null")))
println(s"map1 = $map1")

//  val job0 = ActorRef = system.actorOf(Props(new Job("job0a", )), "job0")
  val job1: ActorRef = system.actorOf(Props(new JobInstance("job1a", 1934124)), "job1")
//  val job2: ActorRef = system.actorOf(Props(new Job("job2a",tg)), "job2")
  
  
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


class JobInstance(name: String, task_graph_id: Int) extends Actor {

  import context._
  println(s"$name Job starting id=$task_graph_id")
  var tg = new NeoTaskGraph(task_graph_id)
  println(tg.task_list())
  println("job id:",tg.get_job_id("job1"))
  
  system.scheduler.scheduleOnce(5.seconds) {
      println(s"$name: Job heartbeat!")
  }

  def receive = {
    case "tick" => 
      println(s"$name: Job heartbeat!!")
  }
  
}


class TaskInstance(tiid: Int, tg: NeoTaskGraph) extends Actor {
  import context._

//  private[this] var tg = tg1
  private[this] var self_id = "deleteme"
  private[this] var statev = "waiting"
  var task_input: String = ""
  var task_output: String = ""
  println(s"$tiid Task initializing")


val cancellable =
  system.scheduler.schedule(
    0 milliseconds,
    5000 milliseconds,
    self,
    "tock")
	
  def executeProcess(cmd: String, inp: String): String = {
     var txt: String = ""
	 println(s"execute command: $cmd")
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
		  txt += line
          println("Answer: " + line)
        }
        src.close()
      },
      // We don't want to use stderr, so just close it.
      _.close()
    ))

    // Using ProcessBuilder.run() will automatically launch
    // a new thread for the input/output routines passed to ProcessIO.
    // We just need to wait for it to finish.

    val code = calcProc.exitValue()
	if (code != 0) {
  	  println(s"error:$cmd:$code: $txt")
	  throw new Exception(txt)
	}
	txt
  }

  def receive = {
   case "tock" =>
     println(s"$tiid: rcvd tock")
     tg.display(tiid)
   case "init" =>
      println(s"$tiid init")
      println(self_id)
	case map:Map[String, String] =>
	  println(s"$tiid: map:::",map)
	  var input_stream = map("output")
	  var from = sender.path.name
	  println(s"$self_id: start received by $self_id from $from, state = $statev", input_stream)
	  if (tg.set_running(tiid)) {
	      println(s"$tiid: task running")
	      val svc_call = tg.format_service(tiid)
          var successful: Boolean = false	      
		  println(s"service: $svc_call")
		  println(s"$tiid: input: $input_stream")
          try { 
             task_output = executeProcess(svc_call, input_stream)
             successful = true
            println("service output: ",task_output)
          } catch {
            case ex: Exception => 
			    task_output = "error"
                tg.taskError(tiid, ex.toString)
          }
          if (successful) {
              val send_list = tg.set_complete(tiid)
              println("send list: ",send_list)
              send_list.foreach(x => 
              { 
                if (x > 0) {
                  val thePath = "/user/job0/"+tg.TaskInstanceName(x)
                  println(s"$self_id: send start to $x:$thePath")
                  context.actorSelection("../*") ! Map("function"->"start","output"->task_output)
                } 
              })
            }
      }

    }
}



