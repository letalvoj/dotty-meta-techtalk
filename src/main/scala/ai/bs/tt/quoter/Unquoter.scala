package ai.bs.tt.quill

import scala.quoted._

/** Unlift Expr to Code */
trait Unquoter[S,T]:
  def unquote(expr:Expr[T])(using QuoteContext): Option[S]

object Unquoter:
  def unquote[S,T](expr:Expr[T])(using u:Unquoter[S,T], qctx: QuoteContext): Option[S] = u.unquote(expr)

  given [T](using u:Unliftable[T]) as Unquoter[T,T]:
    def unquote(expr:Expr[T])(using QuoteContext):Option[T] = u.apply(expr)

  given [T] as Unquoter[Sql.Select,Query[T]]:
    def unquote(expr:Expr[Query[T]])(using QuoteContext): Option[Sql.Select] = expr match
      case '{ Query[$t] }                         => Some(Sql.Select(table = t.show.split("\\.").last))
      case '{ ($rest: Query[$t]).unique }         => Unquoter.unquote(rest).map { _.copy(unique     = true) }
      case '{ ($rest: Query[$t]).take($i) }       => Unquoter.unquote(rest).map { _.copy(count      = i.show.toInt) }
      case '{ ($rest: Query[$t]).filter($func) }  => Unquoter.unquote(rest).map { _.copy(whereExpr  = Some(parseFilter(func))) }
      case '{ ($rest: Query[$t]).map[$b]($func) } => Unquoter.unquote(rest).map { _.copy(columnExpr = List(parseMap(func))) }
    