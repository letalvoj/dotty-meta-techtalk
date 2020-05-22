package ai.bs.tt.quill

import scala.quoted._
import scala.quoted.unsafe._

//// --- Scala DSL operations

class Query[T]:
  def map[B](f: T => B): Query[B]       = Query()
  def filter(f: T => Boolean): Query[T] = Query()
  def unique: Query[T]                  = Query()
  def take(i:Int): Query[T]             = Query()
 
//// --- Extremely incomplete SQL AST

sealed trait Sql:
  def sql:String

object Sql:
  case class Select(table:String,
                    columnExpr:List[String]=Nil,
                    whereExpr:Option[Where]=None,
                    unique:Boolean = false,
                    count:Int=0) extends Sql:
    def sql:String =
      val columns = if(columnExpr.isEmpty) "*" else columnExpr.mkString(",")
      val where = whereExpr.map(_.sql).getOrElse("")
      val distinct = if(unique) " DISTINCT" else ""
      val limit = if(count > 0) s" LIMIT $count" else ""

      s"""SELECT$distinct $columns FROM $table $where $limit""".replaceAll("[ ]+"," ").trim

  case class Where(column:String, operator:String, const:String) extends Sql:
    def sql:String = s"""WHERE $column $operator $const"""

// ----- THIS LINE IS WHERE ORDINARY SCALA STOPS AND 🤯🤯🤯 BEGINS  -----

/** Pair of entry methods invking the macro */
inline def quote[T](inline query: Query[T]): String = ${ quoteMacro('query) }
private def quoteMacro[T:Type](queryExpr: Expr[Query[T]])(using QuoteContext): Expr[String] = 
  Expr(parseQuery[T](queryExpr).sql)

/**
  * Method parsing the chained statement, i.e.
  * {{{ Query[Record].filter(_.age > 10).map(_.name) }}}
  * Implemented using whitebox macros.
  */
private def parseQuery[T:Type](queryExpr: Expr[Query[T]])(using QuoteContext): Sql.Select = queryExpr match
  case '{ Query[$t] }                                => Sql.Select(table = t.show.split("\\.").last)
  case '{ (${remaining}: Query[$t]).take($i) }       => parseQuery(remaining).copy(count      = i.show.toInt)
  case '{ (${remaining}: Query[$t]).unique }         => parseQuery(remaining).copy(unique     = true)
  case '{ (${remaining}: Query[$t]).filter($func) }  => parseQuery(remaining).copy(whereExpr  = Some(parseFilter(func)))
  case '{ (${remaining}: Query[$t]).map[$b]($func) } => parseQuery(remaining).copy(columnExpr = List(parseMap(func)))

/**
  * Method parsing the filter function, i.e.
  * {{{ _.age < 10 }}}
  * Implemented using blackbox macros.
  */
private def parseFilter[T:Type](filterExpr: Expr[T => Boolean])(using qctx: QuoteContext): Sql.Where =
  import qctx.tasty._

  parseFunctionBody[T,Boolean,Sql.Where](filterExpr)(_.unseal match {
    case Apply(Select(Select(_,column),operator),List(Literal(Constant(const)))) =>
      Sql.Where(column, operator, const.toString)
  })

/**
  * Method parsing the filter function, i.e.
  * {{{ _.name }}}
  * Implemented using blackbox macros.
  */
private def parseMap[T:Type, B:Type](mapExpr: Expr[T => B])(using qctx: QuoteContext): String =
  import qctx.tasty._

  parseFunctionBody[T,B,String](mapExpr)(_.unseal match {
    case Select(_, column) => column
  })

private def parseFunctionBody[T:Type, B:Type, R](functionExpr: Expr[T => B])(parse: Expr[B] => R)(using qctx: QuoteContext): R =
    UnsafeExpr.open[T,B,R](functionExpr) { case (body, _) => parse(body) }
