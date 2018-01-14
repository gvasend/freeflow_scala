// 

package com.simbolika.fabric


//import com.simbolika.neo4j
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global
import org.neo4j.driver.v1._
import scala.collection.mutable.ListBuffer


import scala.concurrent.duration._

/*
** Key methods:
        - start(sender,self)
*/
class TaskGraph() {
}

class NeoTaskGraph(job_id: Int) extends TaskGraph {
  val driver = GraphDatabase.driver("bolt://localhost/7687")
  val session = driver.session
  val valid = valid_job()
  val jin = createJobInstanceNode()
  createTaskInstanceNode()
  private var statev = "waiting"
  private var self_id: String = "step"
  set_state("waiting")
  def createTaskInstanceNode() = {
    val id = "id00001"
    val result = session.run(s"MATCH (ji:JobInstance)<-[:JobInstance]-(ji:Job)-[]->(t:Task)-[:TaskInstance]->(ti:TaskInstance {tiid: $id}) WHERE id(j) = $jin")  
  }
  def createJobInstanceNode(): Int = {
    val result = session.run(s"MATCH (j:Job)-[:JobInstance]->(ji:JobInstance) WHERE id(j) = $job_id RETURN id(ji) AS job_instance")  
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
  def format_service(name: String): String = {
    val result = session.run(s"MATCH (j:Job)-[]-(t:Task)-[]->(s:Service) WHERE id(j) = $job_id and t.name = '$name' RETURN s.endPoint AS endpoint")
    if (result.hasNext()) {
      val record = result.next()
      return record.get("endpoint").asString()
    }
    "none"
  }
  def task_succ_list(name: String): List[String] = {
    val result = session.run(s"MATCH (j:Job)-[]-(t1:Task)<-[]-(t2:Task)-[]-(j) WHERE id(j) = $job_id and t2.name = '$name' RETURN t1.name AS name")
    var tasks = new ListBuffer[String]()
    while (result.hasNext()) {
      val record = result.next()
       tasks += record.get("name").asString() 
    }
    return tasks.toList
  }
  def task_pred_list(name: String): List[String] = {
    val result = session.run(s"MATCH (j:Job)-[]-(t1:Task)-[]->(t2:Task)-[]-(j) WHERE id(j) = $job_id and t2.name = '$name' RETURN t1.name AS name")
    var tasks = new ListBuffer[String]()
    while (result.hasNext()) {
      val record = result.next()
       tasks += record.get("name").asString() 
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
  def display() = {
    val result = session.run(s"MATCH (j:Job)-[]-(t1:Task)<-[]-(t2:Task)-[]-(j) WHERE id(j) = $job_id and t2.name = '$self_id' RETURN t1.name AS name")
    if (result.hasNext()) {
      val record = result.next()
      println(record.get("name").asString())
    }else{
      println(s"$self_id not found.")
    }
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

  def set_running(sender: String, self_name: String): Boolean = {

	return false
  }
  def set_complete(): List[String] = {
    set_state("complete")
    return task_succ_list()
  }
  def task_succ_list() = {
    task_succ(self_id).asInstanceOf[List[String]]
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

  retrieveRecord("Anurag")

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
  
  def retrieveRecord(name: String) : String= {
    val driver = GraphDatabase.driver("bolt://localhost/7687")
    val session = driver.session
    val script = s"MATCH (a:Users) WHERE a.name = '$name' RETURN a.name AS name, a.last_name AS last_name, a.age AS age, a.city AS city"
    val result = session.run(script)
    val record_data = if (result.hasNext()) {
      val record = result.next()
      println(record.get("name").asString() + " " + record.get("last_name").asString() + " " + record.get("age").asInt() + " " + record.get("city").asString())
      record.get("name").asString()
    }else{
      s"$name not found."
    }
    session.close()
    driver.close()
    record_data
  }
}


class JobInstance(name: String, task_graph_id: Int) extends Actor {

  import context._
  println(s"$name Job starting id=$task_graph_id")
  var tg = new NeoTaskGraph(task_graph_id)
  println(tg.task_list())
  println("job id:",tg.get_job_id("job1"))
  println("pred step1:",tg.task_pred_list("step1"))
  println("succ step1:",tg.task_succ_list("step1"))
  println("pred step2:",tg.task_pred_list("step2"))
  println("succ step2:",tg.task_succ_list("step2"))
  println("pred step3:",tg.task_pred_list("step3"))
  println("succ step3:",tg.task_succ_list("step3"))
  println("service:",tg.format_service("step2"))
  
  system.scheduler.scheduleOnce(5.seconds) {
      println(s"$name: Job heartbeat!")
  }

  def receive = {
    case "tick" => 
      println(s"$name: Job heartbeat!!")
  }
  
}


class Task(name: String, private[this] var tg: NeoTaskGraph) extends Actor {
  import context._

//  private[this] var tg = tg1
  private[this] var self_id = name
  private[this] var statev = "waiting"
  println(s"$name Task initializing")
  tg.display()
  
val cancellable =
  system.scheduler.schedule(
    0 milliseconds,
    5000 milliseconds,
    self,
    "tock")

  def receive = {
   case "tock" =>
     println(s"$self_id: rcvd tock")
     tg.display()
   case "init" =>
      println(s"$self_id init")
      println(self_id)
    case "start" =>
	  var from = sender.path.name
      tg.task_complete(from)
	  println(s"$self_id: start received by $self_id from $from, state = $statev")
	  if (tg.set_running(sender.path.name, self_id)) {
	      Thread.sleep(5000)
          val send_list = tg.set_complete()
          send_list.foreach(x => 
          { 
            if (x != "null") {
              println(s"$self_id: send start to $x")
              val thePath = "/user/job1/" + x
              context.actorSelection("../*") ! "start"
            } 
          })	      
	  }

    }
}



