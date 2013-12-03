package scala.tools.selectivecps

import org.junit.Test
import org.junit.Assert.assertEquals

import scala.annotation._
import scala.collection.Seq
import scala.collection.generic.CanBuildFrom
import scala.language.{ implicitConversions }
import scala.util.continuations._
import scala.util.control.Exception

class Functions {
  def m0() = {
    shift((k: Int => Int) => k(k(7))) * 2
  }

  def m1() = {
    2 * shift((k: Int => Int) => k(k(7)))
  }

  @Test def basics = {

    assertEquals(28, reset(m0()))
    assertEquals(28, reset(m1()))
  }

  @Test def function1 = {

    val f = () => shift { k: (Int => Int) => k(7) }
    val g: () => Int @cps[Int] = f

    assertEquals(7, reset(g()))
  }

  @Test def function4 = {

    val g: () => Int @cps[Int] = () => shift { k: (Int => Int) => k(7) }

    assertEquals(7, reset(g()))
  }

  @Test def function5 = {

    val g: () => Int @cps[Int] = () => 7

    assertEquals(7, reset(g()))
  }

  @Test def function6 = {

    val g: PartialFunction[Int, Int @cps[Int]] = { case x => 7 }

    assertEquals(7, reset(g(2)))

  }

}

class IfThenElse {
  val out = new StringBuilder; def printOut(x: Any): Unit = out ++= x.toString

  def test(x: Int) = if (x <= 7)
    shift { k: (Int => Int) => k(k(k(x))) }
  else
    shift { k: (Int => Int) => k(x) }

  @Test def ifelse0 = {
    assertEquals(10, reset(1 + test(7)))
    assertEquals(9, reset(1 + test(8)))
  }

  def test1(x: Int) = if (x <= 7)
    shift { k: (Int => Int) => k(k(k(x))) }
  else
    x

  def test2(x: Int) = if (x <= 7)
    x
  else
    shift { k: (Int => Int) => k(k(k(x))) }

  @Test def ifelse1 = {
    assertEquals(10, reset(1 + test1(7)))
    assertEquals(9, reset(1 + test1(8)))
    assertEquals(8, reset(1 + test2(7)))
    assertEquals(11, reset(1 + test2(8)))
  }

  def test3(x: Int) = if (x <= 7)
    shift { k: (Unit => Unit) => printOut("abort") }

  @Test def ifelse2 = {
    out.clear()
    printOut(reset { test3(7); printOut("alive") })
    printOut(reset { test3(8); printOut("alive") })
    assertEquals("abort()alive()", out.toString)
  }

  def util(x: Boolean) = shift { k: (Boolean => Int) => k(x) }

  def test4(x: Int) = if (util(x <= 7))
    x - 1
  else
    x + 1

  @Test def ifelse3 = {
    assertEquals(6, reset(test4(7)))
    assertEquals(9, reset(test4(8)))
  }

  def sh(x1: Int) = shift((k: Int => Int) => k(k(k(x1))))

  def testA(x1: Int): Int @cps[Int] = {
    sh(x1)
    if (x1 == 42) x1 else sh(x1)
  }

  def testB(x1: Int): Int @cps[Int] = {
    if (sh(x1) == 43) x1 else x1
  }

  def testC(x1: Int): Int @cps[Int] = {
    sh(x1)
    if (sh(x1) == 44) x1 else x1
  }

  def testD(x1: Int): Int @cps[Int] = {
    sh(x1)
    if (sh(x1) == 45) x1 else sh(x1)
  }

  @Test def ifelse4 = {
    assertEquals(10, reset(1 + testA(7)))
    assertEquals(10, reset(1 + testB(9)))
    assertEquals(10, reset(1 + testC(9)))
    assertEquals(10, reset(1 + testD(7)))
  }
}

class Inference {

  object A {
    class foo[-B, +C] extends StaticAnnotation with TypeConstraint

    def shift[A, B, C](fun: (A => B) => C): A @foo[B, C] = ???
    def reset[A, C](ctx: => (A @foo[A, C])): C = ???

    def m1 = reset { shift { f: (Int => Range) => f(5) }.to(10) }
  }

  object B {

    def m1 = reset { shift { f: (Int => Range) => f(5) }.to(10) }
    def m2 = reset { val a = shift { f: (Int => Range) => f(5) }; a.to(10) }

    val x1 = reset {
      shift { cont: (Int => Range) =>
        cont(5)
      }.to(10)
    }

    val x2 = reset {
      val a = shift { cont: (Int => Range) =>
        cont(5)
      }
      a.to(10)
    } // x is now Range(5, 6, 7, 8, 9, 10)

    val x3 = reset {
      shift { cont: (Int => Int) =>
        cont(5)
      } + 10
    } // x is now 15

    val x4 = reset {
      10 :: shift { cont: (List[Int] => List[Int]) =>
        cont(List(1, 2, 3))
      }
    } // x is List(10, 1, 2, 3)

    val x5 = reset {
      new scala.runtime.RichInt(shift { cont: (Int => Range) =>
        cont(5)
      }) to 10
    }
  }

  @Test def implicit_infer_annotations = {
    import B._
    assertEquals(5 to 10, x1)
    assertEquals(5 to 10, x2)
    assertEquals(15, x3)
    assertEquals(List(10, 1, 2, 3), x4)
    assertEquals(5 to 10, x5)
  }

  def test(x: => Int @cpsParam[String, Int]) = 7

  def test2() = {
    val x = shift { k: (Int => String) => 9 }
    x
  }

  def test3(x: => Int @cpsParam[Int, Int]) = 7

  def util() = shift { k: (String => String) => "7" }

  @Test def infer1: Unit = {
    test { shift { k: (Int => String) => 9 } }
    test { shift { k: (Int => String) => 9 }; 2 }
    //    test { shift { k: (Int => String) => 9 }; util() }  <-- doesn't work
    test { shift { k: (Int => String) => 9 }; util(); 2 }

    test { shift { k: (Int => String) => 9 }; { test3(0); 2 } }

    test3 { { test3(0); 2 } }

  }

}

class PatternMatching {

  def test(x: Int) = x match {
    case 7 => shift { k: (Int => Int) => k(k(k(x))) }
    case 8 => shift { k: (Int => Int) => k(x) }
  }

  @Test def match0 = {
    assertEquals(10, reset(1 + test(7)))
    assertEquals(9, reset(1 + test(8)))
  }

  def test1(x: Int) = x match {
    case 7 => shift { k: (Int => Int) => k(k(k(x))) }
    case _ => x
  }

  @Test def match1 = {
    assertEquals(10, reset(1 + test(7)))
    assertEquals(9, reset(1 + test(8)))
  }

  def test2() = {
    val (a, b) = shift { k: (((String, String)) => String) => k("A", "B") }
    b
  }

  case class Elem[T, U](a: T, b: U)

  def test3() = {
    val Elem(a, b) = shift { k: (Elem[String, String] => String) => k(Elem("A", "B")) }
    b
  }

  @Test def match2 = {
    assertEquals("B", reset(test2()))
    assertEquals("B", reset(test3()))
  }

  def sh(x1: Int) = shift((k: Int => Int) => k(k(k(x1))))

  def testv(x1: Int) = {
    val o7 = {
      val o6 = {
        val o3 =
          if (7 == x1) Some(x1)
          else None

        if (o3.isEmpty) None
        else Some(sh(x1))
      }
      if (o6.isEmpty) {
        val o5 =
          if (8 == x1) Some(x1)
          else None

        if (o5.isEmpty) None
        else Some(sh(x1))
      } else o6
    }
    o7.get
  }

  @Test def patvirt = {
    assertEquals(10, reset(1 + testv(7)))
    assertEquals(11, reset(1 + testv(8)))
  }

  class MatchRepro {
    def s: String @cps[Any] = shift { k => k("foo") }

    def p = {
      val k = s
      s match { case lit0 => }
    }

    def q = {
      val k = s
      k match { case lit1 => }
    }

    def r = {
      s match { case "FOO" => }
    }

    def t = {
      val k = s
      k match { case "FOO" => }
    }
  }

  @Test def z1673 = {
    val m = new MatchRepro
    ()
  }
}

class IfReturn {
  // d = 1, d2 = 1.0, pct = 1.000
  // d = 2, d2 = 4.0, pct = 0.500
  // d = 3, d2 = 9.0, pct = 0.333
  // d = 4, d2 = 16.0, pct = 0.250
  // d = 5, d2 = 25.0, pct = 0.200
  // d = 6, d2 = 36.0, pct = 0.167
  // d = 7, d2 = 49.0, pct = 0.143
  // d = 8, d2 = 64.0, pct = 0.125
  // d = 9, d2 = 81.0, pct = 0.111
  // d = 10, d2 = 100.0, pct = 0.100
  // d = 11, d2 = 121.0, pct = 0.091
  // d = 12, d2 = 144.0, pct = 0.083
  // d = 13, d2 = 169.0, pct = 0.077
  // d = 14, d2 = 196.0, pct = 0.071
  // d = 15, d2 = 225.0, pct = 0.067
  // d = 16, d2 = 256.0, pct = 0.063
  // d = 17, d2 = 289.0, pct = 0.059
  // d = 18, d2 = 324.0, pct = 0.056
  // d = 19, d2 = 361.0, pct = 0.053
  // d = 20, d2 = 400.0, pct = 0.050
  // d = 21, d2 = 441.0, pct = 0.048
  // d = 22, d2 = 484.0, pct = 0.045
  // d = 23, d2 = 529.0, pct = 0.043
  // d = 24, d2 = 576.0, pct = 0.042
  // d = 25, d2 = 625.0, pct = 0.040

  abstract class IfReturnRepro {
    def s1: Double @cpsParam[Any, Unit]
    def s2: Double @cpsParam[Any, Unit]

    def p(i: Int): Double @cpsParam[Unit, Any] = {
      val px = s1
      val pct = if (px > 100) px else px / s2
      //printOut("pct = %.3f".format(pct))
      assertEquals(s1 / s2, pct, 0.0001)
      pct
    }
  }

  @Test def shift_pct = {
    var d: Double = 0d
    def d2 = d * d

    val irr = new IfReturnRepro {
      def s1 = shift(f => f(d))
      def s2 = shift(f => f(d2))
    }
    1 to 25 foreach { i =>
      d = i
      // print("d = " + i + ", d2 = " + d2 + ", ")
      assertEquals(i.toDouble * i, d2, 0.1)
      run(irr p i)
    }
  }

  @Test def t1807 = {
    val z = reset {
      val f: (() => Int @cps[Int]) = () => 1
      f()
    }
    assertEquals(1, z)
  }

  @Test def t1808: Unit = {
    reset0 { 0 }
  }
}

class Suspendable {
  def shifted: Unit @suspendable = shift { (k: Unit => Unit) => () }
  def test1(b: => Boolean) = {
    reset {
      if (b) shifted
    }
  }
  @Test def t1820 = test1(true)

  def suspended[A](x: A): A @suspendable = x
  def test1[A](x: A): A @suspendable = suspended(x) match { case x => x }
  def test2[A](x: List[A]): A @suspendable = suspended(x) match { case List(x) => x }

  def test3[A](x: A): A @suspendable = x match { case x => x }
  def test4[A](x: List[A]): A @suspendable = x match { case List(x) => x }

  @Test def t1821 {
    assertEquals((), reset(test1()))
    assertEquals((), reset(test2(List(()))))
    assertEquals((), reset(test3()))
    assertEquals((), reset(test4(List(()))))
  }
}

class Misc {

  def double[B](n: Int)(k: Int => B): B = k(n * 2)

  @Test def t2864 {
    reset {
      val result1 = shift(double[Unit](100))
      val result2 = shift(double[Unit](result1))
      assertEquals(400, result2)
    }
  }

  def foo: Int @cps[Int] = {
    val a0 = shift((k: Int => Int) => k(0))
    val x0 = 2
    val a1 = shift((k: Int => Int) => x0)
    0
  }

  /*
  def bar: ControlContext[Int,Int,Int] = {
    shiftR((k:Int=>Int) => k(0)).flatMap { a0 =>
    val x0 = 2
    shiftR((k:Int=>Int) => x0).map { a1 =>
    0
    }}
  }
*/

  @Test def t2934 = {
    assertEquals(List(3, 4, 5), reset {
      val x = shift(List(1, 2, 3).flatMap[Int, List[Int]])
      List(x + 2)
    })
  }

  class Bla {
    val x = 8
    def y[T] = 9
  }

  /*
  def bla[A] = shift { k:(Bla=>A) => k(new Bla) }
*/

  def bla1 = shift { k: (Bla => Bla) => k(new Bla) }
  def bla2 = shift { k: (Bla => Int) => k(new Bla) }

  def fooA = bla2.x
  def fooB[T] = bla2.y[T]

  // TODO: check whether this also applies to a::shift { k => ... }

  @Test def t3225Mono(): Unit = {
    assertEquals(8, reset(bla1).x)
    assertEquals(8, reset(bla2.x))
    assertEquals(9, reset(bla2.y[Int]))
    assertEquals(9, reset(bla2.y))
    assertEquals(8, reset(fooA))
    assertEquals(9, reset(fooB))
    0
  }

  def blaX[A] = shift { k: (Bla => A) => k(new Bla) }

  def fooX[A] = blaX[A].x
  def fooY[A] = blaX[A].y[A]

  @Test def t3225Poly(): Unit = {
    assertEquals(8, reset(blaX[Bla]).x)
    assertEquals(8, reset(blaX[Int].x))
    assertEquals(9, reset(blaX[Int].y[Int]))
    assertEquals(9, reset(blaX[Int].y))
    assertEquals(8, reset(fooX[Int]))
    assertEquals(9, reset(fooY[Int]))
    0
  }

  def capture(): Int @suspendable = 42

  @Test def t3501: Unit = reset {
    var i = 0
    while (i < 5) {
      i += 1
      val y = capture()
      val s = y
      assertEquals(42, s)
    }
    assertEquals(5, i)
  }
}

class Return {
  val out = new StringBuilder; def printOut(x: Any): Unit = out ++= x.toString

  class ReturnRepro {
    def s1: Int @cps[Any] = shift { k => k(5) }
    def caller = reset { printOut(p(3)) }
    def caller2 = reset { printOut(p2(3)) }
    def caller3 = reset { printOut(p3(3)) }

    def p(i: Int): Int @cps[Any] = {
      val v = s1 + 3
      return v
    }

    def p2(i: Int): Int @cps[Any] = {
      val v = s1 + 3
      if (v > 0) {
        printOut("hi")
        return v
      } else {
        printOut("hi")
        return 8
      }
    }

    def p3(i: Int): Int @cps[Any] = {
      val v = s1 + 3
      try {
        printOut("from try")
        return v
      } catch {
        case e: Exception =>
          printOut("from catch")
          return 7
      }
    }
  }

  @Test def t5314_2 = {
    out.clear()
    val repro = new ReturnRepro
    repro.caller
    repro.caller2
    repro.caller3
    assertEquals("8hi8from try8", out.toString)
  }

  class ReturnRepro2 {

    def s1: Int @cpsParam[Any, Unit] = shift { k => k(5) }
    def caller = reset { printOut(p(3)) }
    def caller2 = reset { printOut(p2(3)) }

    def p(i: Int): Int @cpsParam[Unit, Any] = {
      val v = s1 + 3
      return { printOut("enter return expr"); v }
    }

    def p2(i: Int): Int @cpsParam[Unit, Any] = {
      val v = s1 + 3
      if (v > 0) {
        return { printOut("hi"); v }
      } else {
        return { printOut("hi"); 8 }
      }
    }
  }

  @Test def t5314_3 = {
    out.clear()
    val repro = new ReturnRepro2
    repro.caller
    repro.caller2
    assertEquals("enter return expr8hi8", out.toString)
  }

  def foo(x: Int): Int @cps[Int] = 7

  def bar(x: Int): Int @cps[Int] = {
    val v = foo(x)
    if (v > 0)
      return v
    else
      return 10
  }

  @Test def t5314_with_if =
    assertEquals(7, reset { bar(10) })

}

class t5314 {
  val out = new StringBuilder; def printOut(x: Any): Unit = out ++= x.toString

  class ReturnRepro3 {
    def s1: Int @cpsParam[Any, Unit] = shift { k => k(5) }
    def caller = reset { printOut(p(3)) }
    def caller2 = reset { printOut(p2(3)) }

    def p(i: Int): Int @cpsParam[Unit, Any] = {
      val v = s1 + 3
      return v
    }

    def p2(i: Int): Int @cpsParam[Unit, Any] = {
      val v = s1 + 3
      if (v > 0) {
        printOut("hi")
        return v
      } else {
        printOut("hi")
        return 8
      }
    }
  }

  def foo(x: Int): Int @cps[Int] = shift { k => k(x) }

  def bar(x: Int): Int @cps[Int] = return foo(x)

  def nocps(x: Int): Int = { return x; x }

  def foo2(x: Int): Int @cps[Int] = 7
  def bar2(x: Int): Int @cps[Int] = { foo2(x); return 7 }
  def bar3(x: Int): Int @cps[Int] = { foo2(x); if (x == 7) return 7 else return foo2(x) }
  def bar4(x: Int): Int @cps[Int] = { foo2(x); if (x == 7) return 7 else foo2(x) }
  def bar5(x: Int): Int @cps[Int] = { foo2(x); if (x == 7) return 7 else 8 }

  @Test def t5314 = {
    out.clear()

    printOut(reset { bar2(10) })
    printOut(reset { bar3(10) })
    printOut(reset { bar4(10) })
    printOut(reset { bar5(10) })

    /* original test case */
    val repro = new ReturnRepro3
    repro.caller
    repro.caller2

    reset {
      val res = bar(8)
      printOut(res)
      res
    }

    assertEquals("77788hi88", out.toString)
  }

}

class HigherOrder {

  import java.util.concurrent.atomic._

  @Test def t5472 = {
    val map = Map("foo" -> 1, "bar" -> 2)
    reset {
      val mapped =
        for {
          (location, accessors) <- new ContinuationizedParallelIterable(map)
        } yield {
          shiftUnit0[Int, Unit](23)
        }
      assertEquals(List(23, 23), mapped.toList)
    }
  }

  @deprecated("Suppress warnings", since = "2.11")
  final class ContinuationizedParallelIterable[+A](protected val underline: Iterable[A]) {
    def toList = underline.toList.sortBy(_.toString)

    final def filter(p: A => Boolean @suspendable): ContinuationizedParallelIterable[A] @suspendable =
      shift(
        new AtomicInteger(1) with ((ContinuationizedParallelIterable[A] => Unit) => Unit) {
          private val results = new AtomicReference[List[A]](Nil)

          @tailrec
          private def add(element: A) {
            val old = results.get
            if (!results.compareAndSet(old, element :: old)) {
              add(element)
            }
          }

          override final def apply(continue: ContinuationizedParallelIterable[A] => Unit) {
            for (element <- underline) {
              super.incrementAndGet()
              reset {
                val pass = p(element)
                if (pass) {
                  add(element)
                }
                if (super.decrementAndGet() == 0) {
                  continue(new ContinuationizedParallelIterable(results.get))
                }
              }
            }
            if (super.decrementAndGet() == 0) {
              continue(new ContinuationizedParallelIterable(results.get))
            }
          }
        })

    final def foreach[U](f: A => U @suspendable): Unit @suspendable =
      shift(
        new AtomicInteger(1) with ((Unit => Unit) => Unit) {
          override final def apply(continue: Unit => Unit) {
            for (element <- underline) {
              super.incrementAndGet()
              reset {
                f(element)
                if (super.decrementAndGet() == 0) {
                  continue()
                }
              }
            }
            if (super.decrementAndGet() == 0) {
              continue()
            }
          }
        })

    final def map[B: Manifest](f: A => B @suspendable): ContinuationizedParallelIterable[B] @suspendable =
      shift(
        new AtomicInteger(underline.size) with ((ContinuationizedParallelIterable[B] => Unit) => Unit) {
          override final def apply(continue: ContinuationizedParallelIterable[B] => Unit) {
            val results = new Array[B](super.get)
            for ((element, i) <- underline.view.zipWithIndex) {
              reset {
                val result = f(element)
                results(i) = result
                if (super.decrementAndGet() == 0) {
                  continue(new ContinuationizedParallelIterable(results))
                }
              }
            }
          }
        })
  }

  def g: List[Int] @suspendable = List(1, 2, 3)

  def fp10: List[Int] @suspendable = {
    g.map(x => x)
  }

  def fp11: List[Int] @suspendable = {
    val z = g.map(x => x)
    z
  }

  def fp12: List[Int] @suspendable = {
    val z = List(1, 2, 3)
    z.map(x => x)
  }

  def fp20: List[Int] @suspendable = {
    g.map[Int, List[Int]](x => x)
  }

  def fp21: List[Int] @suspendable = {
    val z = g.map[Int, List[Int]](x => x)
    z
  }

  def fp22: List[Int] @suspendable = {
    val z = g.map[Int, List[Int]](x => x)(List.canBuildFrom[Int])
    z
  }

  def fp23: List[Int] @suspendable = {
    val z = g.map(x => x)(List.canBuildFrom[Int])
    z
  }

  @Test def t5506 = {
    reset {
      assertEquals(List(1, 2, 3), fp10)
      assertEquals(List(1, 2, 3), fp11)
      assertEquals(List(1, 2, 3), fp12)
      assertEquals(List(1, 2, 3), fp20)
      assertEquals(List(1, 2, 3), fp21)
      assertEquals(List(1, 2, 3), fp22)
      assertEquals(List(1, 2, 3), fp23)
    }
  }
  class ExecutionContext

  implicit def defaultExecutionContext = new ExecutionContext

  case class Future[+T](x: T) {
    final def map[A](f: T => A): Future[A] = new Future[A](f(x))
    final def flatMap[A](f: T => Future[A]): Future[A] = f(x)
  }

  class PromiseStream[A] {
    override def toString = xs.toString

    var xs: List[A] = Nil

    final def +=(elem: A): this.type = { xs :+= elem; this }

    final def ++=(elem: Traversable[A]): this.type = { xs ++= elem; this }

    final def <<(elem: Future[A]): PromiseStream[A] @cps[Future[Any]] =
      shift { cont: (PromiseStream[A] => Future[Any]) => elem map (a => cont(this += a)) }

    final def <<(elem1: Future[A], elem2: Future[A], elems: Future[A]*): PromiseStream[A] @cps[Future[Any]] =
      shift { cont: (PromiseStream[A] => Future[Any]) => Future.flow(this << elem1 << elem2 <<< Future.sequence(elems.toSeq)) map cont }

    final def <<<(elems: Traversable[A]): PromiseStream[A] @cps[Future[Any]] =
      shift { cont: (PromiseStream[A] => Future[Any]) => cont(this ++= elems) }

    final def <<<(elems: Future[Traversable[A]]): PromiseStream[A] @cps[Future[Any]] =
      shift { cont: (PromiseStream[A] => Future[Any]) => elems map (as => cont(this ++= as)) }
  }

  object Future {

    def sequence[A, M[_] <: Traversable[_]](in: M[Future[A]])(implicit cbf: CanBuildFrom[M[Future[A]], A, M[A]], executor: ExecutionContext): Future[M[A]] =
      new Future(in.asInstanceOf[Traversable[Future[A]]].map((f: Future[A]) => f.x)(cbf.asInstanceOf[CanBuildFrom[Traversable[Future[A]], A, M[A]]]))

    def flow[A](body: => A @cps[Future[Any]])(implicit executor: ExecutionContext): Future[A] = reset(Future(body)).asInstanceOf[Future[A]]

  }

  @Test def t5538 = {
    val p = new PromiseStream[Int]
    assertEquals(Future(Future(Future(Future(Future(List(1, 2, 3, 4, 5)))))).toString, Future.flow(p << (Future(1), Future(2), Future(3), Future(4), Future(5))).toString)
  }
}

class TryCatch {

  def foo = try {
    shift((k: Int => Int) => k(7))
  } catch {
    case ex: Throwable =>
      9
  }

  def bar = try {
    7
  } catch {
    case ex: Throwable =>
      shiftUnit0[Int, Int](9)
  }

  @Test def trycatch0 = {
    assertEquals(10, reset { foo + 3 })
    assertEquals(10, reset { bar + 3 })
  }

  def fatal: Int = throw new Exception()

  def foo1 = try {
    fatal
    shift((k: Int => Int) => k(7))
  } catch {
    case ex: Throwable =>
      9
  }

  def foo2 = try {
    shift((k: Int => Int) => k(7))
    fatal
  } catch {
    case ex: Throwable =>
      9
  }

  def bar1 = try {
    fatal
    7
  } catch {
    case ex: Throwable =>
      shiftUnit0[Int, Int](9) // regular shift causes no-symbol doesn't have owner
  }

  def bar2 = try {
    7
    fatal
  } catch {
    case ex: Throwable =>
      shiftUnit0[Int, Int](9) // regular shift causes no-symbol doesn't have owner
  }

  @Test def trycatch1 = {
    assertEquals(12, reset { foo1 + 3 })
    assertEquals(12, reset { foo2 + 3 })
    assertEquals(12, reset { bar1 + 3 })
    assertEquals(12, reset { bar2 + 3 })
  }

  trait AbstractResource[+R <: AnyRef] {
    def reflect[B]: R @cpsParam[B, Either[Throwable, B]] = shift(acquireFor)
    def acquireFor[B](f: R => B): Either[Throwable, B] = {
      import Exception._
      catching(List(classOf[Throwable]): _*) either (f(null.asInstanceOf[R]))
    }
  }

  @Test def t3199 = {
    val x = new AbstractResource[String] {}
    val result = x.acquireFor(x => 7)
    assertEquals(Right(7), result)
  }

}

object AvoidClassT3233 {
  def foo(x: Int) = {
    try {
      throw new Exception
      shiftUnit0[Int, Int](7)
    } catch {
      case ex: Throwable =>
        val g = (a: Int) => a
        9
    }
  }
}

class T3233 {
  // work around scalac bug: Trying to access the this of another class: tree.symbol = class TryCatch, ctx.clazz.symbol = <$anon: Function1>
  import AvoidClassT3233._
  @Test def t3223 = {
    assertEquals(9, reset(foo(0)))
  }
}

class While {
  val out = new StringBuilder; def printOut(x: Any): Unit = out ++= x.toString

  def foo0(): Int @cps[Unit] = 2

  def test0(): Unit @cps[Unit] = {
    var x = 0
    while (x < 9000) { // pick number large enough to require tail-call opt
      x += foo0()
    }
    assertEquals(9000, x)
  }

  @Test def while0 = {
    reset(test0())
  }

  def foo3(): Int @cps[Unit] = shift { k => printOut("up"); k(2); printOut("down") }

  def test3(): Unit @cps[Unit] = {
    var x = 0
    while (x < 9) {
      x += foo3()
    }
    printOut(x)
  }

  @Test def while1 = {
    out.clear
    reset(test3())
    assertEquals("upupupupup10downdowndowndowndown", out.toString)
  }

  def foo1(): Int @cps[Unit] = 2
  def foo2(): Int @cps[Unit] = shift { k => printOut("up"); k(2); printOut("down") }

  def test2(): Unit @cps[Unit] = {
    var x = 0
    while (x < 9000) { // pick number large enough to require tail-call opt
      x += (if (x % 1000 != 0) foo1() else foo2())
    }
    printOut(x)
  }

  @Test def while2 = {
    out.clear
    reset(test2())
    assertEquals("upupupupupupupupup9000downdowndowndowndowndowndowndowndown", out.toString)
  }
}