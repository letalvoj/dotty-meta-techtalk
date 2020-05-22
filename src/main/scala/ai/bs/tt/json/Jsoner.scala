package ai.bs.tt.json

import scala.language.implicitConversions

import scala.compiletime._
import scala.deriving._

import scala.reflect.ClassTag
import scala.util.Either

type Value = String | Int | Double | Boolean | Long

enum Json:
  case Val[V<:Value](value:V)
  case Arr          (list:List[Json])
  case Obj          (map:Map[String, Json])
  case Null

  def prettyPrint: String = this match
    case Val(value) => value.toString
    case Arr(list)  => s"[${list.map(_.prettyPrint).mkString(", ")}]"
    case Obj(map)   => s"{${map.toSeq.map(this.toObjectPair).mkString(", ")}}"
    case Null       => "null"
  
  private def toObjectPair(k:String, v:Json):String = s""""$k": ${v.prettyPrint}"""


/** Json serializer / deserializer interface. */
trait Jsoner[V]:
  def toJson(v:V): Json
  def fromJson(json:Json): Jsoner.Result[V]

object Jsoner:

  /** Convenience method for summoning implicit value */
  inline def apply[V](using js:Jsoner[V]):Jsoner[V] = js

  /** For the sake of simplicity errors can be just strings */
  type Error = String
  type Result[X] = Either[Error, X]

  /** Instance of `Jsoner` for primitive classes */
  given [V <: Value](using ct:ClassTag[V]) as Jsoner[V]:
    def toJson(v:V): Json = Json.Val[V](v)

    def fromJson(json:Json):Result[V] = json match
      case Json.Val(value:V) => Right(value)
      case n                 => Left(s"expected ${ct} value, got $n")

  /** Instance of `Jsoner` for `List` */
  given[V](using js:Jsoner[V], ct:ClassTag[V]) as Jsoner[List[V]]:
    def toJson(list:List[V]): Json = Json.Arr(list map js.toJson)

    def fromJson(json:Json):Result[List[V]] = json match
      case Json.Arr(list:List[Json]) =>
        val init: Result[List[V]] = Right(List.empty)
        list.foldRight(init){ case(json, resultTs) =>
          for
            t <- js.fromJson(json)
            ts <- resultTs
          yield t :: ts
        }
      case n => Left(s"Expected List[$ct], got $n")
  
  // ----- THIS LINE IS WHERE ORDINARY SCALA STOPS AND 🤯🤯🤯 BEGINS  -----

  /** Generic derivation of `Jsoner` for arbitrary `case class`. */
  inline given derived[V](using m: Mirror.ProductOf[V]) as Jsoner[V] = new Jsoner[V]:
    def toJson(v:V): Json = Json.Obj(toMap[m.MirroredElemTypes, m.MirroredElemLabels, V](v, 0))
    
    def fromJson(json:Json):Result[V] = json match
      case Json.Obj(map) => fromMap[m.MirroredElemTypes,m.MirroredElemLabels](map, 0).map(t => m.fromProduct(t.asInstanceOf).asInstanceOf[V])
      case o => Left(s"Expected Int, got $o")

  /** Maps a `Product` with fields `L` of types `T` to `Map[String, Json]` */
  inline def toMap[T, L, V](v:V, i:Int):Map[String, Json] = inline erasedValue[(T, L)] match
    case _: ((Unit, Unit)) => Map.empty
    case _: ((t *: ts, l *: ls)) => 
      val js = summonInline[Jsoner[t]]
      val label  = constValue[l].asInstanceOf[String]
      val value  = js.toJson(productElement[t](v,i))

      toMap[ts, ls , V](v, i+1) + (label -> value)

  /** Maps a `Map[String, Json]` to `Product` with fields `L` of types `T` to  */
  inline def fromMap[T, L](map:Map[String,Json], i:Int):Result[Tuple] = inline erasedValue[(T, L)] match
    case _: ((Unit, Unit)) => Right(())
    case _: ((t *: ts, l *: ls)) => 
      val js = summonInline[Jsoner[t]]
      val label  = constValue[l].asInstanceOf[String]

      for {
        j <- map.get(label).toRight(s"No such element $label")
        h <- js.fromJson(j)
        t <- fromMap[ts, ls](map, i+1)
      } yield h *: t
