package scala.tools.selectivecps

import org.junit.Test

class CompilerErrors extends CompilerTesting {
  // @Test -- disabled
  def infer0 =
    expectCPSError("cannot cps-transform expression 8: type arguments [Int(8),String,Int] do not conform to method shiftUnit's type parameter bounds [A,B,C >: B]",
      """|def test(x: => Int @cpsParam[String,Int]) = 7
         |
         |def main(args: Array[String]) = {
         |  test(8)
         |}""")

  @Test def function0 =
    expectCPSError("""|type mismatch;
                       | found   : () => Int @scala.util.continuations.cpsParam[Int,Int]
                       | required: () => Int""".stripMargin,
      """|def main(args: Array[String]): Any = {
         |  val f = () => shift { k: (Int=>Int) => k(7) }
         |  val g: () => Int = f
         |
         |  println(reset(g()))
         |}""")

  @Test def function2 =
    expectCPSError(
      """|type mismatch;
         | found   : () => Int
         | required: () => Int @scala.util.continuations.cpsParam[Int,Int]""".stripMargin,
      """|def main(args: Array[String]): Any = {
         |  val f = () => 7
         |  val g: () => Int @cps[Int] = f
         |
         |  println(reset(g()))
         |}""")

  @Test def function3 =
    expectCPSError(
      """|type mismatch;
     | found   : Int @scala.util.continuations.cpsParam[Int,Int]
     | required: Int""".stripMargin,
      """|def main(args: Array[String]): Any = {
       |  val g: () => Int = () => shift { k: (Int=>Int) => k(7) }
       |
       |  println(reset(g()))
       |}""")

  @Test def infer2 =
    expectCPSError("illegal answer type modification: scala.util.continuations.cpsParam[String,Int] andThen scala.util.continuations.cpsParam[String,Int]",
      """|def test(x: => Int @cpsParam[String,Int]) = 7
         |
         |def sym() = shift { k: (Int => String) => 9 }
         |
         |
         |def main(args: Array[String]): Any = {
         |  test { sym(); sym() }
         |}""")

  @Test def `lazy` =
    expectCPSError("implementation restriction: cps annotations not allowed on lazy value definitions",
      """|def foo() = {
         |  lazy val x = shift((k:Unit=>Unit)=>k())
         |  println(x)
         |}
         |
         |def main(args: Array[String]) = {
         |  reset {
         |    foo()
         |  }
         |}""")

  @Test def t1929 =
    expectCPSError(
      """|type mismatch;
         | found   : Int @scala.util.continuations.cpsParam[String,String] @scala.util.continuations.cpsSynth
         | required: Int @scala.util.continuations.cpsParam[Int,String]""".stripMargin,
      """|def main(args : Array[String]) {
         |  reset {
         |    println("up")
         |    val x = shift((k:Int=>String) => k(8) + k(2))
         |    println("down " + x)
         |    val y = shift((k:Int=>String) => k(3))
         |    println("down2 " + y)
         |    y + x
         |  }
         |}""")

  @Test def t2285 =
    expectCPSError(
      """|type mismatch;
         | found   : Int @scala.util.continuations.cpsParam[String,String] @scala.util.continuations.cpsSynth
         | required: Int @scala.util.continuations.cpsParam[Int,String]""".stripMargin,
      """|def bar() = shift { k: (String => String) => k("1") }
         |
         |def foo() = reset { bar(); 7 }""")

  @Test def t2949 =
    expectCPSError(
      """|type mismatch;
         | found   : Int
         | required: ? @scala.util.continuations.cpsParam[List[?],Any]""".stripMargin,
      """|def reflect[A,B](xs : List[A]) = shift{ xs.flatMap[B, List[B]] }
         |def reify[A, B](x : A @cpsParam[List[A], B]) = reset{ List(x) }
         |
         |def main(args: Array[String]): Unit = println(reify {
         |  val x = reflect[Int, Int](List(1,2,3))
         |  val y = reflect[Int, Int](List(2,4,8))
         |  x * y
         |})""")

  @Test def t3718 =
    expectCPSError(
      "cannot cps-transform malformed (possibly in shift/reset placement) expression",
      "scala.util.continuations.reset((_: Any).##)")

  @Test def t5314_missing_result_type =
    expectCPSError(
      "method bar has return statement; needs result type",
      """|def foo(x:Int): Int @cps[Int] = x
         |
         |def bar(x:Int) = return foo(x)
         |
         |reset {
         |  val res = bar(8)
         |  println(res)
         |  res
         |}""")

  @Test def t5314_npe =
    expectCPSError(
      "method bar has return statement; needs result type",
      "def bar(x:Int) = { return x; x }  // NPE")

  @Test def t5314_return_reset =
    expectCPSError(
      "return expression not allowed, since method calls CPS method",
      """|val rnd = new scala.util.Random
       |
       |def foo(x: Int): Int @cps[Int] = shift { k => k(x) }
       |
       |def bar(x: Int): Int @cps[Int] = return foo(x)
       |
       |def caller(): Int = {
       |  val v: Int = reset {
       |    val res: Int = bar(8)
       |    if (rnd.nextInt(100) > 50) return 5 // not allowed, since method is calling `reset`
       |    42
       |  }
       |  v
       |}
       |
       |caller()""")

  @Test def t5314_type_error =
    expectCPSError(
      """|type mismatch;
         | found   : Int @scala.util.continuations.cpsParam[Int,Int]
         | required: Int @scala.util.continuations.cpsParam[String,String]""".stripMargin,
      """|def foo(x:Int): Int @cps[Int] = shift { k => k(x) }
         |
         |// should be a type error
         |def bar(x:Int): Int @cps[String] = return foo(x)
         |
         |def caller(): Unit = {
         |  val v: String = reset {
         |    val res: Int = bar(8)
         |    "hello"
         |  }
         |}
         |
         |caller()""")

  @Test def t5445 =
    expectCPSError(
      "cps annotations not allowed on by-value parameters or value definitions",
      "def foo(block: Unit @suspendable ): Unit @suspendable = {}")

  @Test def trycatch2 =
    expectCPSErrors(2, "only simple cps types allowed in try/catch blocks (found: Int @scala.util.continuations.cpsParam[String,Int])",
      """|def fatal[T]: T = throw new Exception
       |def cpsIntStringInt = shift { k:(Int=>String) => k(3); 7 }
       |def cpsIntIntString = shift { k:(Int=>Int) => k(3); "7" }
       |
       |def foo1 = try {
       |  fatal[Int]
       |  cpsIntStringInt
       |} catch {
       |  case ex: Throwable =>
       |    cpsIntStringInt
       |}
       |
       |def foo2 = try {
       |  fatal[Int]
       |  cpsIntStringInt
       |} catch {
       |  case ex: Throwable =>
       |    cpsIntStringInt
       |}
       |
       |
       |def main(args: Array[String]): Unit = {
       |  println(reset { foo1; "3" })
       |  println(reset { foo2; "3" })
       |}""")
}

class CompilerTesting {
  def loadPlugin = s"-Xplugin:${sys.props("scala-continuations-plugin.jar")} -P:continuations:enable"

  // note: `code` should have a | margin
  def cpsErrorMessages(msg: String, code: String) =
    errorMessages(msg, loadPlugin)(s"import scala.util.continuations._\nobject Test {\n${code.stripMargin}\n}")

  def expectCPSError(msg: String, code: String) = {
    val errors = cpsErrorMessages(msg, code)
    assert(errors exists (_ contains msg), errors mkString "\n")
  }

  def expectCPSErrors(msgCount: Int, msg: String, code: String) = {
    val errors = cpsErrorMessages(msg, code)
    val errorCount = errors.filter(_ contains msg).length
    assert(errorCount == msgCount, s"$errorCount occurrences of \'$msg\' found -- expected $msgCount in:\n${errors mkString "\n"}")
  }

  // TODO: move to scala.tools.reflect.ToolboxFactory
  def errorMessages(errorSnippet: String, compileOptions: String)(code: String): List[String] = {
    import scala.tools.reflect._
    val m = scala.reflect.runtime.currentMirror
    val tb = m.mkToolBox(options = compileOptions) //: ToolBox[m.universe.type]
    val fe = tb.frontEnd

    try {
      tb.eval(tb.parse(code))
      Nil
    } catch {
      case _: ToolBoxError =>
        import fe._
        infos.toList collect { case Info(_, msg, ERROR) => msg }
    }
  }
}