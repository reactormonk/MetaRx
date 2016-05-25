package pl.metastack.metarx

import scala.collection.mutable

import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global

class BufferTest extends CompatTest {
  test("forall()") {
    val buf = Buffer(1, 2, 3)

    var states = mutable.ArrayBuffer.empty[Boolean]
    buf.forall(_ > 0).attach(states += _)
    assertEquals(states, Seq(true))

    buf += 0
    assertEquals(states, Seq(true, false))

    buf.remove(buf.get.last)
    assertEquals(states, Seq(true, false, true))
  }

  test("filter().lastOption") {
    val buf = Buffer[Int]()
    val filter = buf.filter(_ > 1)
    var last = Option.empty[Int]

    filter.buffer.lastOption.attach(last = _)

    buf += 1
    buf += 2
    buf += 3

    assertEquals(last, Some(3))
    buf.remove(buf.get(2))

    assertEquals(last, Some(2))
  }

  test("size()") {
    var cur = -1
    Buffer().size.attach(cur = _)
    assertEquals(cur, 0)
  }

  test("concat()") {
    val x = Buffer(1, 2, 3)
    val y = x.concat(Buffer())
    assertEquals(x.get, y.buffer.get)
  }

  test("flatMap()") {
    val x = Buffer(1, 2, 3)
    val buf = Buffer(42)
    val y = x.flatMap[Int](value =>
      if (value == 4) buf else Buffer(value))

    assertEquals(y.get, Seq(1, 2, 3))

    x.prepend(4)
    assertEquals(y.get, Seq(42, 1, 2, 3))

    buf.clear()
    assertEquals(y.get, Seq(1, 2, 3))

    x.prepend(5)
    assertEquals(y.get, Seq(5, 1, 2, 3))

    buf += 9
    assertEquals(y.get, Seq(5, 9, 1, 2, 3))
  }

  test("flatMapCh() (2)") {
    val x = Buffer(1, 2, 3)
    val y = x.flatMapCh[Int](value => Opt(value))
    assertEquals(x.get, y.buffer.get)

    val fst = x.get.head
    x.remove(fst)
    x.prepend(fst)
    assertEquals(x.get, y.buffer.get)
  }

  test("flatMapCh() (3)") {
    val x = Buffer(1, 2, 3)
    val ch = Opt(42)
    val y = x.flatMapCh[Int](value =>
      if (value == 4) ch else Opt(value))

    assertEquals(y.get, Seq(1, 2, 3))

    x.prepend(4)
    assertEquals(y.get, Seq(42, 1, 2, 3))

    ch.clear()
    assertEquals(y.get, Seq(1, 2, 3))

    x.prepend(5)
    assertEquals(y.get, Seq(5, 1, 2, 3))

    ch := 9
    assertEquals(y.get, Seq(5, 9, 1, 2, 3))
  }

  test("removeAll()") {
    val x = Buffer(1, 2, 3)
    val add = Buffer(4, 5, 6)

    x ++= add
    x.removeAll(add)

    assertEquals(x.get, Seq(1, 2, 3))
  }

  test("removeAll() (2)") {
    val x = Buffer(1, 2, 3)
    val add = Buffer(4, 5, 6)

    x ++= add

    val y = x.filter(_ <= 3).buffer
    assertEquals(y.get, Seq(1, 2, 3))
    x.removeAll(y)

    assertEquals(x.get, add.get)
  }

  test("find") {
    val buffer = Buffer(1, 2, 3)

    var states = mutable.ArrayBuffer.empty[Int]
    buffer.find(_ > 1).values.attach(states += _)
    assertEquals(states, mutable.ArrayBuffer(2))

    buffer.remove(2)
    assertEquals(states, mutable.ArrayBuffer(2, 3))
  }

  test("buffer") {
    val buffer = Buffer(1, 2, 3)

    val states = buffer.find(_ > 1).values.buffer
    assertEquals(states.get, Seq(2))

    buffer.remove(2)
    assertEquals(states.get, Seq(2, 3))
  }

  test("buffer (2)") {
    val buffer = Buffer[Int]()
    val states = buffer.find(_ > 1).values.buffer

    buffer += 1
    buffer += 2
    buffer += 3

    assertEquals(states.get, Seq(2))

    buffer.remove(2)
    assertEquals(states.get, Seq(2, 3))
  }

  test("isLast") {
    val buffer = Buffer[Int](1, 2, 3, 4, 5)

    val elems = mutable.ArrayBuffer.empty[Boolean]
    buffer.isLast(0).attach(elems += _)

    assertEquals(elems, Seq(false))

    buffer += 0
    assertEquals(elems, Seq(false, true))

    buffer += 0
    assertEquals(elems, Seq(false, true))

    buffer -= 0
    assertEquals(elems, Seq(false, true))

    buffer -= 0
    assertEquals(elems, Seq(false, true, false))
  }

  test("Conversion from Future[_]") {
    val p = Promise[Seq[Int]]()
    val f = p.future
    val buf = Buffer.from(f)
    assertEquals(buf.get.isEmpty, true)
    p.success(Seq(1, 2, 3))
    f.onComplete { v =>
      assertEquals(buf.get, Seq(1, 2, 3))
    }
  }

  test("reduce") {
    val buffer = Buffer("a", "b", "c")
    val reduced: ReadChannel[String] = buffer.reduce(_ + "," + _)

    val results = mutable.ArrayBuffer.empty[String]
    reduced.attach(results += _)
    buffer += "d"
    assertEquals(results, Seq("a,b,c", "a,b,c,d"))
  }

  test("mkString") {
    val buffer = Buffer("a", "b", "c")
    val string: ReadChannel[String] = buffer.mkString(", ")

    val results = mutable.ArrayBuffer.empty[String]
    string.attach(results += _)
    assertEquals(results, Seq("a, b, c"))
  }

  test("Invalid removal") {
    val buf = Buffer[String]()
    buf ++= Seq("green", "blue")

    try {
      buf -= "red"  // Invalid removal
    } catch {
      case e: AssertionError =>
    } finally {
      buf.clear()
    }
  }
}
