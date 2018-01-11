// 

package com.simbolika.fabric


//import com.simbolika.neo4j
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import scala.util.{Success, Failure}
import scala.concurrent.ExecutionContext.Implicits.global.

import scala.concurrent.duration._


class StaticTaskGraph(tasks: Map[String, Map[String, Any]]) {
  val cache = tasks
  var statev = "waiting"
  var self_id: String = "null"
  val status = collection.mutable.Map[String, String]()
  status(self_id) = "none"

  def set_self(name: String) { 
    self_id = name 
    status(self_id) = "incomplete"
  }
  def show() { println("show self: ",self_id) }
  def task_complete(name: String) { status(name) = "complete"}
  def task_status(name: String) = { status(name) }
  def task_pred(name: String) = { cache(name)("pred") }
  def task_succ(name: String) = { cache(name)("succ") }
  def task_details(name: String) = { cache(name) }
  def get_tasks() = { cache.keys }
  
  def complete() { 
    println("set task complete")
    status(self_id) = "complete"
  }
  def state() = { 
    println(s"id = $self_id")
    status(self_id) }
  def pred() = { 
    cache(self_id)("pred") 
  }
  def start(sender: String) = {
  
 //   val s = sender    // Actor[akka://<system>@<host>:<port>/user/path/to/actor]
 //   val p = s.path    // akka://<system>@<host>:<port>/user/path/to/actor
 //  val a = p.address // akka://<system>@<host>:<port>
 //   val host = a.host // Some(<host>), where <host> is the listen address as configured for the remote system
 //   val port = a.port
	  println("sender name:", sender)
    task_complete(sender)
    if (ready() && state == "waiting") {
	      statev = "running"
          println(s"$self_id is running")
          var lst = succ().asInstanceOf[List[String]]
          lst.foreach(x => 
          { 
            if (x != "null") {
              println(s"send to $x")
              ActorSystem("sentient_fabric").actorSelection("user/" + "somename").resolveOne(500 milliseconds).onComplete {
                case Success(actorRef) => // logic with the actorRef
                case Failure(ex) => println("user/" + "somename" + " does not exist")}
            } 
          })
    statev = "complete"
    }
  }
  def succ() = { cache(self_id)("succ") }
  def details() = { cache(self_id) }
  def ready() = { 
    println(s"checking ready state for $self_id")
    var ready_state = true
    var lst = pred().asInstanceOf[List[String]]
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
    println("final read ", ready_state)
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

  val job1: ActorRef = system.actorOf(Props(new Job("job1a", new StaticTaskGraph(map1))), "job1")
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


class Job(name: String, tasks: StaticTaskGraph) extends Actor {

  import context._
  println("Job starting!")
  for (a_task <- tasks.get_tasks() ) {
    val task_ref: ActorRef = context.actorOf(Props(new Task(a_task, tasks)), a_task)
    task_ref ! "start"
  }

  
  system.scheduler.scheduleOnce(5.seconds) {
      println("Job heartbeat!")
      println(name)
  }

  def receive = {
    case "tick" => 
      println("Job heartbeat!!")
  }
}

class Task(name: String, tg1: StaticTaskGraph) extends Actor {
  import context._

  var tg = tg1
  var self_id = name
  println("Task starting!")
  println(name)
  
val cancellable =
  system.scheduler.schedule(
    0 milliseconds,
    5000 milliseconds,
    context.parent,
    "tock")

  def receive = {
    case "init" =>
      println("init")
      println(name)
    case "start" =>
	  tg.set_self(self_id)
	  println(sender.getClass())
      tg.start(sender.path.name)
  }
}



