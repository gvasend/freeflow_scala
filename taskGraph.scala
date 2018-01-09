// Start writing your ScalaFiddle code here
val cache = collection.mutable.Map[String, collection.mutable.Map[String, String]]()
val cache1 = collection.mutable.Map[String, String]()

val map1 = Map("step1"->Map("process"->"sk_step1","succ"->"step2"),
               "step2"->Map("process"->"sk_step2","pred"->"step1","succ"->"step3"),
               "step3"->Map("process"->"sk_step3","pred"->"step2"))
val map2 = collection.mutable.Map(map1.toSeq: _*) 
println("test")

//cache("test").
cache1("this") = "abc"
cache("test") = cache1
println(cache("test"))
cache("test")("this") = "xxx"
println(map2)

// Task
//   tg.set_self(name)
//   if ready() { run(); tg.succ() !  "start"}


class TaskGraph(tasks: Map[String, Map[String, String]]) {
  val cache = tasks
  var self_id: String = "null"
  var task1 = Map("process"->"","pred"->"",""->"")
  val status = collection.mutable.Map[String, String]()
  status(self_id) = "none"
  def set_self(name: String) { 
    self_id = name 
    status(self_id) = "incomplete"
  }
  def task_complete(name: String) { status(name) = "true"}
  def task_status(name: String) = { status(name) }
  def task_pred(name: String) = { cache(name)("pred") }
  def task_succ(name: String) = { cache(name)("succ") }
  def task_details(name: String) = { cache(name) }
  
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
  def succ() = { cache(self_id)("succ") }
  def details() = { cache(self_id) }
  def ready() = { status(pred()) == "complete" } 

}

val tg = new TaskGraph(map1)

println(tg.task_pred("step2"))
tg.set_self("step2")
println(tg.pred())
println(tg.details())
println(tg.state())
if (tg.state() == "complete") {
     println("task complete")
}
else {
     println("task incomplete")
}
tg.complete()
if (tg.state() == "complete") {
     println("task complete")
}
else {
     println("task incomplete")
}
