// 

package com.simbolika.fabric


//import com.simbolika.neo4j
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}

import scala.concurrent.duration._


object Main extends App {

  val system = ActorSystem("sentient_fabric")

  system.actorOf(Props(new SFM()), "root")
}


class SFM() extends Actor {

  import context._

  val tasks = List("a","b","c")
  val this_job = Map("graph"->Map("process"->"graphit"),"section"->Map("process"->"section_doc"),"load"->Map("process"->"xml_load"))
  val job1: ActorRef = system.actorOf(Props(new Job("job1a",this_job)), "job1")
  val job2: ActorRef = system.actorOf(Props(new Job("job2a",this_job)), "job2")
  
  
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


class Job(name: String, tasks: Map[String, Map[String,String]]) extends Actor {

  import context._
      println("Job starting!")
  for (a_task <- tasks) {
    val task_ref: ActorRef = context.actorOf(Props(new Task(tasks(a_task), tasks)), a_task)
    task_ref ! "init"
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

class Task(name: String, tasks: Map[String, Map[String,String]]) extends Actor {
  import context._

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
      println("start")
//    Wait for a specific time
//    Send start to downstream tasks

    
  }
}

/*
val cache = collection.mutable.Map[String, collection.mutable.Map[String, String]]()
val cache1 = collection.mutable.Map[String, String]()

val map1 = Map("this"->Map("this1"->"xxx"),"that"->Map("this1"->"xxx"),"other"->Map("this1"->"xxx"))
val map2 = collection.mutable.Map(map1.toSeq: _*) 
println("test")

//cache("test").
cache1("this") = "abc"
cache("test") = cache1
println(cache("test"))
cache("test")("this") = "xxx"
println(map2)


// This is a nice feature of Scala Maps:
val defaultMap = Map("foo" -> 1, "bar" -> 2).withDefaultValue(3)
defaultMap("qux")


val task_status = scala.collection.mutable.Map[String, Int]()

for each task in task_list:
    task_status[task] = 0
	
wait for random time
	
rcv msg == "wake":
    task_status[sender.name] += 1
    upstream = getUpStream(name)
    for each task in upstream_list:
        if task_status(task) == 0:
            return
    for each task in downstream:
        task ! "wake"
	

*/
