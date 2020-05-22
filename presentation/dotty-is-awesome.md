# Metaprogramming in Dotty

---

## Moderation

---

## Motivation

----

- **Slides**
    - Show why you might want to pay attention to Dotty
    - What are the cool libraries in _Scala 2_
    - How _Scala 3_ reflects on previous issues

- **_Metaprogramming_ is not what an average Joe programmer does. This will be _done for you_ by library devs!**
    - Anayway it's fun and it can make you a better coder

- **DEMOs**

---

## Scala is the "cool" language

----

### Scala is ahead of trends
- Ideas pushed by _Scala 2_ are slowly getting to mainstream languages (_Java_, _Kotlin_, ...)
- Collections
    - Immutability
    - `map`, `flatMap`, `filter`, ... combiners
- Data Classes
- A lot of new languages look "Scalish"
  - _Kotlin_
  - _Swift_
  - _Rust_

----

### It combines the two worlds

- ***(Purely)* Functional Mindset**
  - Functions are used to process data, not mutate state
- **Objective Oriented Mindset**
  - Objects encapsulate state and distribute responsibilty


---

## Scala Libraries

----

Great community of functional developers emerged aroud _Scala 2_ and created bunch of cool ass libraries.

----

### Doobie

```scala
def selectCountries(year: Int, limit: Long): Query[Country] =
    sql"""
        SELECT code, name, area, independece
        FROM countries
        WHERE independece > $year
        LIMIT $limit
    """.query[Country]
```

- custom `sql` string interpolation
- `.query` parses the string in compile time using macros and creates a valid `JDBC` program

----

### Quill

```scala
def biggerThan(i: Float) = quote {
  query[Circle].filter(r => r.radius > lift(i))
}
ctx.run(biggerThan(10))
// SELECT r.radius FROM Circle r WHERE r.radius > ?
```

- A way to create a valid SQL commands using a DSL
- Validated in compile time

----

### Circe

```scala
import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

sealed trait Foo
case class Bar(i: Int, d: Option[Double]) extends Foo

val encoded = Bar(13, Some(14.0)).asJson.noSpaces
val decoded = decode[Foo](encoded)
```

- Fast `Json` serializers
- No reflection involved, fast, validated in compilation

----

### Refined

```scala
import eu.timepit.refined
import refined._, refined.api._, refined.auto._, refined.numeric._

val positive: Int Refined Positive = -5
val interger: String Refined MatchesRegex["[0-9]+"] = "fail"

def validatedFunction(age: Int Refined Positive) = ...
```

- Adding constrain to types
- Can be mapped to Json schemas

----

### CaseApp

```scala
import caseapp._
case class Params(foo: String, bar: Option[Int])

object Main extends CaseApp[Params] {
  def run(options: Params, arg: RemainingArgs): Unit = ???
}
```

```
Main
Usage: main [options]
  --foo  <value>
  --bar  <value> (optional)
```

----

### And many, many more

[lauris/awesome-scala](https://github.com/lauris/awesome-scala)

- Scala servers are notoriously known for _long compilation_ time, especially when macro heavy libraries are used
- Troubleshooting _errors_ comming from these type-level libraries was _painful_
- Dotty devs took that to their hearts and added better support for _type-level programming_ 🤯

---

## Dotty Features

----

### Language Server Protocol

- Dotty implements the [LSP](https://github.com/Microsoft/language-server-protocol), no plugin needed
  - The compiler serves as a _backed_ and _presentation_ compiler at the same time
    - No need for IDE update with every version of scala
    - Great support for marginal IDEs
      - Looking at you `vim` and _Sublime_ users
- List of supported IDEs and languages: [langserver.org](https://langserver.org/)

----

### Scala 2 compatibility

- Dotty use on _Scala 2.13_ libraries as long as they **do not use macros**
  - _Scala 2_ macros were directly using `scalac` internal datastructures 
  - _Dotty_ does not have the same internal code representation and hence can not execute these macros.
  - The new macro system consists of
    - `inline` - _code inlining_ capabilities
    - **quotes** - a way to do _pattern matching_ on code
    - **TASTy**  - typed _abstract syntax tree_

----

### Optional Braces

It was already _partially_ supported in _Scala 2_.
```scala
class Database:
  def parser(records:List[Record]):Int = validated match
      case Some(i) => i
      case None    => -1
```

Personally, I love it.

----

### Types

- `A & B` **Intersection** (`extends`)
  - In Dotty
    - `A & B = B & A`
  - In Java / Scala 2
    - `A & B != B & A`
- `A | B` **Union**
  - `String | Null`

----

### Singleton Literal Types 
  - `4`, `"Cat"`
  - How is it useful
    - `MatchRegex["[0-9]+"]`
    - `Matrix[Double, 4, 4]`
    - `Vector[Int, 16]`

----

### Match types

```scala
type Elem[X] = X match:
  case String      => Char
  case Array[t]    => t
  case Iterable[t] => t
```

Aaaand it's _turing complete_.

----

### Tuple decomposition

- Tuples can be decomposed into `HList`
  - (Heterogenous linked list)
  - `(Int, String)`
  - `Int *: String *: Unit`
- Intended to be used as

```scala
type Last[T <: Tuple] = T match:
    case h *: Unit   => h
    case h *: tail   => Last[tail]
```

Which gives us a _collection_ datastructure to work with.

----

### Type-Level programming

- Constants 
  - `1 <: Int`, `"Cat" <: String`
- List
  - `Int *: String *: Unit`
- Branching & Sub-procedures
  - `X match { case F[x] => Lift[G, x] }`

It's a simple _programming language_ of it's own which allows us to _reason_ about types.

----

### Contextual Abstractions

```scala
trait Comparator[T]:
  def compare(x: T, y: T): Int

given intOrdering as Comparator[Int]:
  def compare(x: Int, y: Int) = ???

def max[T](x: T, y: T)(using comp: Comparator[T]): T = ???

max(1, 2) // works
```

- cleans up `implicit`s from _Scala 2_
- `using` - parameters automatically provided by the language
- `given` - providers of these special parameters
  - you can put these to several places and there is a precedence defined


----

### Enums / ADTs

```scala
enum Color(r: Int,g: Int,b: Int):
  case Red   extends Color(255,0,0)
  case Green extends Color(0,255,0)
  case Blue  extends Color(0,0,255)
  case Mix(r: Int,g: Int,b: Int) extends Color(r,g,b)
```

Algebraic data types are composed types. Those usually include
- **Product** _(dataclasses)_
 - `Mix = Int x Int x Int`
- **Sums** _(class hierarchies)_
 - `Color <: Red | Green | Blue | Mix`

----

### Inline

```scala
inline def log(level:Level, inline message:String):Unit =
    if(logger.isLevel(level)) logger.log(level, message)
        
```

---

## Metaprogramming

----

### Mirror

- Dotty provides a compile-time reflection
- It is implemented for subset of _products_ and _sums_

----

#### Enum

```scala
enum Tree[T] {
  case Branch[T](left: Tree[T], right: Tree[T])
  case Leaf[T](elem: T)
}
```

##### Mirror

```scala
Mirror.Sum {
  type MirroredType = Tree
  type MirroredMonoType = Tree[_]
  type MirroredElemLabels = ("Branch", "Leaf")
  ...
  def ordinal(x: MirroredMonoType): Int = x match {
    case _: Branch[_] => 0
    case _: Leaf[_] => 1
  }
}
```

----

#### Product

```scala
case Branch[T](left: Tree[T], right: Tree[T])
```

##### Mirror

```scala
Mirror.Product {
  type MirroredType = Branch
  type MirroredMonoType = Branch[_]
  type MirroredElemTypes[T] = (Tree[T], Tree[T])
  type MirroredElemLabels = ("left", "right")
  ...
  def fromProduct(p: Product): MirroredMonoType = ???
}
```

And similarly for `Leaf`.

----

### Combined with `inline` 🤯🤯🤯🤯

- Gives you a new powerful way to do metaprogramming

```scala
inline def derived[V](using m: Mirror.Of[V]) as Jsoner[V] =
    new Jsoner[V]:
        def toJson(v:V): Json = Json.Obj(
            toMap[m.MirroredElemTypes, m.MirroredElemLabels, V]
        )
        def fromJson(json:Json):Result[V] = json match
            case Json.Obj(map) => 
                fromMap[
                    m.MirroredElemTypes,
                    m.MirroredElemLabels
                ]
            ...
```
- I will furher show how to implement `toMap` and `fromMap`

----

### DEMO - Json Serializer

Let's write a _generic json (de)serializer_ for arbitraty data class.
[Jsoner.scala](https://github.com/letalvoj/dotty-meta-techtalk/blob/master/src/main/scala/ai/bs/tt/json/Jsoner.scala)

---

## Macros

----

### Abstract Syntax Tree

- As in many languages you can work with _AST_
- It is unsafe blackbox approach, which is messy - different syntax can yield different AST for the same logic

For example `t.column > 20` compiles to
```scala
Apply(
    Select(Select(TermName("t"), "column"), ">"),
    List(Literal(Constant(const)))
)
```

- _Scala 2_ macros were based only on that approach. `Dotty` has a similar capability plus ...

----

### Quotes - Pattern matching on code 🤯🤯
From doc [macros#quoted-patterns](https://dotty.epfl.ch/docs/reference/metaprogramming/macros.html#quoted-patterns):

```scala
def optimizeExpr(body: Expr[Int])(using QuoteContext): Expr[Int] =
  body match
    case '{ sum() } => Expr(0)
    case '{ sum($n) } => n
    case '{ sum(${Varargs(args)}: _*) } => sumExpr(args)
    case e => e
```

```scala
optimize { sum(sum(1, a, 2), 3, b) } // optimized to 6 + a + b
```

----

### Liftable / Unliftable 🤯🤯🤯
Similar to _json (de)serializer_, but it serializes _to and from code_ instead of plain text json.
- `object` <-> `json` (runtime)
- `DSL` <-> `code` (compile time)

```scala
given liftable(using env: Evironment) as Liftable[Exp]:
  def toExpr(e: Exp) = e match:
    case Num(n)          => Expr(n)
    case Var(x)          => env(x)
    case Plus(e1, e2)    => '{ ${ compile(e1, env) } + ${ compile(e2, env) } }
    case Let(x, e, body) => '{ val y = ${ compile(e, env) }; ${ compile(body, env + (x -> 'y)) } }

val exp = Plus(Plus(Num(2), Var("x")), Num(4))
lift(Let("x", Num(3), exp), Map()) 
// '{ val y = 3; (2 + y) + 4 }
```

----

#### DEMO - Quill

Let's implement a _Scala 3_ DSL for writing _SQL_ queries in a typed way.
[Quill.scala](https://github.com/letalvoj/dotty-meta-techtalk/blob/master/src/main/scala/ai/bs/tt/quill/Quill.scala)

---

## Take-aways

- Dotty has the potential to be a clear and expressive language
- It can leverage some of the existing _Scala 2_ ecosystem
- Metaprogramming can allow you to write a robust generic code
   - It is going to be simpler then ever
   - Use it _only if you really **need to** write a generic tool_
   - Leave the work to the community whenever possible

---

## Questions?   
