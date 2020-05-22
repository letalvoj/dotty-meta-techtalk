package ai.bs.tt.quill

import scala.quoted._

/** Lift Code to Expr */
trait Quoter[S,T]:
  def quote(s:S)(using QuoteContext):Expr[T]

  def pure[U](u:Expr[U]):Quoter[S,U] = new Quoter[S,U]:
    def quote(s:S)(using QuoteContext):Expr[U] = u

  def map[U](f:Expr[T] => Expr[U]):Quoter[S,U] = flatMap(f andThen pure)

  def flatMap[U](f:Expr[T] => Quoter[S,U]):Quoter[S,U] = new Quoter[S,U]:
    def quote(s:S)(using QuoteContext):Expr[U] =
        f(Quoter.this.quote(s)).quote(s)

object Quoter:
  def quote[S,T](s:S)(using q:Quoter[S,T], qctx: QuoteContext): Expr[T] = q.quote(s)

  given [T](using l:Liftable[T]) as Quoter[T,T]:
    def quote(t:T)(using QuoteContext): Expr[T] = l.toExpr(t)
