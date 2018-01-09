// 

package com.simbolika.fabric

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}

import scala.concurrent.duration._


object Main extends App {

  val system = ActorSystem("sentient_fabric")

  system.actorOf(Props(new SFM()), "root")
}


class SFM() extends Actor {

  import context._

  val tasks = List("a","b","c")
  val tasks2 = List("first","second","third")  
  val job1: ActorRef = system.actorOf(Props(new Job("job1a",tasks)), "job1")
  val job2: ActorRef = system.actorOf(Props(new Job("job2a",tasks2)), "job2")
  
  
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


class Job(name: String, tasks: List[String]) extends Actor {

  import context._
      println("Job starting!")
  for (task_name <- tasks) {
    val task_ref: ActorRef = context.actorOf(Props(new Task(task_name, tasks)), task_name)
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

class Task(name: String, tasks: List[String]) extends Actor {
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


var id:Int = 0
val imm_fruit_count = Map("apples" -> 4, "oranges" -> 5, "bananas" -> 6)
println(imm_fruit_count("apples"))
println(imm_fruit_count.contains("apples"))
println(imm_fruit_count.getOrElse("melons", "peaches"))
val mut_fruit_count = scala.collection.mutable.Map[String, Int]()

mut_fruit_count("apples") = 4
mut_fruit_count += ("oranges" -> 5, "bananas" -> 6)

println(mut_fruit_count)

mut_fruit_count -= "apples"

mut_fruit_count.keySet

mut_fruit_count.values

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
	

