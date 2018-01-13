// 

package com.simbolika.fabric


//import com.simbolika.neo4j
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global


import scala.concurrent.duration._

/*
** Key methods:
        - start(sender,self)
*/


class StaticTaskGraph(tasks: Map[String, Map[String, Any]]) {
  private[this] val cache = tasks
  private[this] var statev = "waiting"
  private var self_id: String = "null"
  set_state("waiting")
  private[this] val status = collection.mutable.Map[String, String]()
  status(self_id) = "none"
  def set_self(self_name: String) = {
    self_id = self_name
  }
  def display() = {
//      println(s"???: statev $statev status $status")
  }
  def show() { println("show self: ",self_id) }
  def task_complete(name: String) { set_task_state(name,"complete") }
  def task_status(name: String) = { status(name) }
  def task_pred(name: String) = { cache(name)("pred") }
  def task_succ(name: String) = { cache(name)("succ") }
  def task_details(name: String) = { cache(name) }
  def get_tasks() = { cache.keys }
  
  def state() = { 
//    println(s"$this $self_id: self state $statev +++++++++++++++++++++++++++++++++++++++")
    statev
  }
  def set_task_state(task_name: String, next: String) = {
//      println(s"$self_id): changing state $task_name, $next")
      status(task_name) = next
  }
  def set_state(next: String) = {
      println(s"$self_id: changing self state from $statev to $next")
      statev = next
//      if (self_id != null) {
//        status(self_id) = statev
 //     }
  }
  def pred() = { 
    cache(self_id)("pred") 
  }
  def start(sender: String, self_name: String): List[String] = {
  
    self_id = self_name
    var check_state = state()
//	  println("sender name:", sender)
    task_complete(sender)
    var send_list = List()
    if (ready(self_name) && state() == "waiting") {
	      set_state("running")
	      Thread.sleep(5000)
          println(s"$self_name: is running")
		  set_state("complete")
          return task_succ(self_name).asInstanceOf[List[String]]
    }
	return List("null")
  }
  def set_running(sender: String, self_name: String): Boolean = {
    set_self(self_name)
    var check_state = state()
//	  println("sender name:", sender)
    task_complete(sender)
    var send_list = List()
    if (ready(self_name) && state() == "waiting") {
	      set_state("running")
	      return true
    }
	return false
  }
  def set_complete(): List[String] = {
    set_state("complete")
    return task_succ_list()
  }
  def task_succ_list() = {
    task_succ(self_id).asInstanceOf[List[String]]
  }
  def succ() = { cache(self_id)("succ") }
  def details() = { cache(self_id) }
  def ready(self_name: String) = { 
    statev = state()
//    println(s"$self_name: checking ready state for $self_name current state is $statev")
    var ready_state = true
    var lst = task_pred(self_name).asInstanceOf[List[String]]
//    println(s"$self_name: depends on $lst status is $status")
    lst.foreach(x => 
    { 
      var stat = "incomplete"
      if (status.contains(x)) {
        stat = this.task_status(x)
      }
      if (x == "null") {
        stat = "complete"
      }
//      println(s"stat = $stat")
      ready_state &= (stat == "complete") } )
//    println(s"$self_name: final read $ready_state")
    ready_state
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

  val job1: ActorRef = system.actorOf(Props(new Job("job1a", map1)), "job1")
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


class Job(name: String, private var map: Map[String, Map[String, Any]]) extends Actor {

  import context._
  println(s"$name Job starting!")
  for (a_task <- map.keys ) {
    var task_ref: ActorRef = context.actorOf(Props(new Task(a_task, new StaticTaskGraph(map))), a_task)
    Thread.sleep(5000)
    task_ref ! "start"
  }

  
  system.scheduler.scheduleOnce(5.seconds) {
      println(s"$name: Job heartbeat!")
  }

  def receive = {
    case "tick" => 
      println(s"$name: Job heartbeat!!")
  }
}

class Task(name: String, private[this] var tg: StaticTaskGraph) extends Actor {
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



