package ai.bs.tt

import ai.bs.tt.quill.{quote,Query}
import ai.bs.tt.json.{Jsoner, Json}

case class Record(id:Int, name:String, age:Int)
case class DSBL(d:Double, s:String, b:Boolean, l:List[Int])

@main def runQuill() =
  println("🤯")

  println(quote{ Query[Record].filter(_.age < 30) })
  println(quote{ Query[Record].map(_.id).take(10) })
  println(quote{ Query[Record].filter(_.age > 100).map(_.name).unique })


@main def runJsoner() =
  println("🤯")

  val obj = DSBL(1,"s",true,List(1,2,3))
  val json = Jsoner[DSBL].toJson(obj)
  val back = Jsoner[DSBL].fromJson(json)

  println(json.prettyPrint)
  println(back)
  
  println(Jsoner[String].fromJson(Json.Val(2.0)))
  println(Jsoner[List[List[List[Int]]]].toJson(List(List(List(1),List(2,3)))).prettyPrint)
  println(Jsoner[DSBL].fromJson(Json.Obj(Map.empty)))