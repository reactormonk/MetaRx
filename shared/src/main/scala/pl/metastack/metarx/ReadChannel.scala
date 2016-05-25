package pl.metastack.metarx

import scala.concurrent.duration.FiniteDuration

trait ReadChannel[T]
  extends reactive.stream.Head[T]
  with reactive.stream.Tail[ReadChannel, T]
  with reactive.stream.Take[ReadChannel, T]
  with reactive.stream.Fold[T]
  with reactive.stream.Is[T]
  with reactive.stream.Aggregate[ReadChannel, T]
  with reactive.stream.Filter[ReadChannel, T, T]
  with reactive.stream.Map[ReadChannel, T]
  with reactive.stream.MapExtended[ReadChannel, T]
  with reactive.stream.Cache[T]
  with reactive.stream.Size
  with reactive.stream.PartialChannel[T]
  with reactive.poll.Flush[T]
  with reactive.propagate.Publish[T]
  with Disposable
{ self =>
  import Channel.Observer

  private[metarx] val children = Array[ChildChannel[T, _]]()

  def cache: ReadPartialChannel[T] = {
    val res = Opt[T]()
    attach(res := _)
    res
  }

  def cache(default: T): ReadChannelState[T] = {
    val res = Var[T](default)
    attach(res := _)
    res
  }

  def flush(f: T => Unit)

  def publish(ch: WriteChannel[T]): ReadChannel[Unit] = ch.subscribe(this)
  def publish[U](ch: WriteChannel[T], ignore: ReadChannel[U]): ReadChannel[Unit] = ch.subscribe(this, ignore)

  def or(ch: ReadChannel[_]): ReadChannel[Unit] = {
    val that = this

    val res = new RootChannel[Unit] {
      def flush(f: Unit => Unit) {
        that.flush(_ => f(()))
        ch.flush(_ => f(()))
      }
    }

    attach(_ => res ! (()))
    ch.attach(_ => res ! (()))

    res
  }

  def |(ch: ReadChannel[_]) = or(ch)

  def merge(ch: ReadChannel[T]): ReadChannel[T] = {
    val that = this

    val res = new RootChannel[T] {
      def flush(f: T => Unit) {
        that.flush(f)
        ch.flush(f)
      }
    }

    res << this
    res << ch

    res
  }

  def zip[U](other: ReadChannel[U]): ReadChannel[(T, U)] = {
    val that = this

    val res = new RootChannel[(T, U)] {
      def flush(f: ((T, U)) => Unit) {
        that.flush(t => other.flush(u => f((t, u))))
      }
    }

    attach(t => other.flush(u => res.produce((t, u))))
    other.attach(u => flush(t => res.produce((t, u))))

    res
  }

  /** Helper function to zip channel with the two given channels. Can be used
    * to avoid nested tuples.
    */
  def zip[U, V](other1: ReadChannel[U],
                other2: ReadChannel[V]): ReadChannel[(T, U, V)] =
    zip(other1)
      .zip(other2)
      .map(z => (z._1._1, z._1._2, z._2))

  def zip[U, V, W](other1: ReadChannel[U],
                   other2: ReadChannel[V],
                   other3: ReadChannel[W]): ReadChannel[(T, U, V, W)] =
    zip(other1, other2)
      .zip(other3)
      .map(z => (z._1._1, z._1._2, z._1._3, z._2))

  def zip[U, V, W, X](other1: ReadChannel[U],
                      other2: ReadChannel[V],
                      other3: ReadChannel[W],
                      other4: ReadChannel[X]): ReadChannel[(T, U, V, W, X)] =
    zip(other1, other2, other3)
      .zip(other4)
      .map(z => (z._1._1, z._1._2, z._1._3, z._1._4, z._2))

  def zipWith[U, V](other: ReadChannel[U])(f: (T, U) => V): ReadChannel[V] =
    zip(other).map(f.tupled)

  def child(): ReadChannel[T] =
    forkUni(t => Result.Next(t))

  def silentAttach(f: T => Unit): ReadChannel[Unit] =
    forkUni { value =>
      f(value)
      Result.Next()
    }

  def attach(f: T => Unit): ReadChannel[Unit] = {
    val ch = silentAttach(f).asInstanceOf[UniChildChannel[T, Unit]]
    flush(ch.process)
    ch
  }

  def detach(ch: ChildChannel[T, _]): Unit =
    children -= ch

  /** Buffers all produced elements */
  def buffer: Buffer[T] = {
    val buf = Buffer[T]()
    attach(value => buf.append(value))
    buf
  }

  /** Buffers only current element */
  def toBuffer: Buffer[T] = {
    val buf = Buffer[T]()
    attach(value => buf.set(Seq(value)))
    buf
  }

  /** Uni-directional fork for values */
  def forkUni[U](observer: Observer[T, U], filterCycles: Boolean = false): ReadChannel[U] = {
    val ch = UniChildChannel[T, U](this, observer, None, filterCycles)
    children += ch
    ch
  }

  def forkUniState[U](observer: Observer[T, U], onFlush: => Option[U]): ReadChannel[U] = {
    val ch = UniChildChannel[T, U](this, observer, Some(() => onFlush))
    children += ch
    flush(ch.process) /* Otherwise onFlush will be None for the initial value */
    ch
  }

  /** Uni-directional fork for channels */
  def forkUniFlat[U](observer: Observer[T, ReadChannel[U]]): ReadChannel[U] = {
    val ch = FlatChildChannel[T, U](this, observer)
    children += ch
    flush(ch.process)
    ch
  }

  /** Bi-directional fork for channels */
  def forkBiFlat[U](obs: Observer[T, Channel[U]]): Channel[U] = {
    val ch = BiFlatChildChannel[T, U](this, obs)
    children += ch
    ch
  }

  def filter(f: T => Boolean): ReadChannel[T] =
    forkUni { value =>
      if (f(value)) Result.Next(value)
      else Result.Next()
    }

  def withFilter(f: T => Boolean): WithFilter = new WithFilter(f)

  class WithFilter(p: T => Boolean) {
    def map[U](f: T => U): ReadChannel[U] = self.filter(p).map(f)
    def flatMap[U](f: T => ReadChannel[U]): ReadChannel[U] = self.filter(p).flatMap(f)
    def foreach[U](f: T => U): Unit = self.filter(p).foreach(f)
    def withFilter[U](q: T => Boolean): WithFilter = new WithFilter(x => p(x) && q(x))
  }

  def foreach[U](f: T => U): Unit = attach { x => f(x); () }

  def filterCycles: ReadChannel[T] =
    forkUni(value => Result.Next(value), filterCycles = true)

  def take(count: Int): ReadChannel[T] = {
    assert(count > 0)
    var cnt = count
    forkUni { value =>
      if (cnt > 1) { cnt -= 1; Result.Next(value) }
      else Result.Done(value)
    }
  }

  def drop(count: Int): ReadChannel[T] = {
    assert(count > 0)
    var cnt = count
    forkUniState(value =>
      if (cnt > 0) { cnt -= 1; Result.Next() }
      else Result.Next(value)
      , None
    )
  }

  def head: ReadChannel[T] = forkUni(value => Result.Done(value))
  def tail: ReadChannel[T] = drop(1)

  def isHead(value: T): ReadChannel[Boolean] =
    take(1).map(_ == value)

  def map[U](f: T => U): ReadChannel[U] =
    forkUni { value =>
      Result.Next(f(value))
    }

  def mapTo[U](f: T => U): DeltaDict[T, U] = {
    val delta: ReadChannel[Dict.Delta[T, U]] = map[Dict.Delta[T, U]] { value =>
      Dict.Delta.Insert(value, f(value))
    }

    DeltaDict(delta)
  }

  def is(value: T): ReadChannel[Boolean] =
    forkUni { t =>
      Result.Next(t == value)
    }.distinct

  def is(other: ReadChannel[T]): ReadChannel[Boolean] = {
    zip(other).map { case (a, b) => a == b }
  }

  def isNot(value: T): ReadChannel[Boolean] =
    forkUni { t =>
      Result.Next(t != value)
    }.distinct

  def isNot(other: ReadChannel[T]): ReadChannel[Boolean] = !is(other)

  def flatMap[U](f: T => ReadChannel[U]): ReadChannel[U] =
    forkUniFlat(value => Result.Next(f(value)))

  def flatMapSeq[U](f: T => Seq[U]): ReadChannel[U] =
    forkUni(value => Result.Next(f(value): _*))

  /** flatMap with back-propagation. */
  def flatMapCh[U](f: T => Channel[U]): Channel[U] =
    forkBiFlat(value => Result.Next(f(value)))

  def flatMapBuf[U](f: T => ReadBuffer[U]): ReadBuffer[U] = {
    val buf = Buffer[U]()
    var child: ReadChannel[Unit] = null
    attach { value =>
      buf.clear()
      if (child != null) child.dispose()
      child = buf.changes.subscribe(f(value).changes)
    }
    buf
  }

  def collect[U](f: PartialFunction[T, U]): ReadChannel[U] =
    forkUni { value =>
      Result.Next(f.lift(value).toSeq: _*)
    }

  /** @note Caches the accumulator value. */
  def foldLeft[U](acc: U)(f: (U, T) => U): ReadChannel[U] = {
    var accum = acc
    forkUniState(value => {
      accum = f(accum, value)
      Result.Next(accum)
    }, Some(accum))
  }

  def takeUntil(ch: ReadChannel[_]): ReadChannel[T] = {
    val res = forkUni { value =>
      Result.Next(value)
    }

    ch.forkUni[Any] { _ =>
      res.dispose()
      Result.Done()
    }

    res
  }

  def takeWhile(f: T => Boolean): ReadChannel[T] =
    forkUni { value =>
      if (f(value)) Result.Next(value)
      else Result.Done()
    }

  def writeTo(write: WriteChannel[T]): Channel[T] = {
    val res = Channel[T]()
    val ignore = write.subscribe(res)
    res.subscribe(this, ignore)
    res
  }

  def distinct: ReadChannel[T] = {
    var cur = Option.empty[T]
    forkUniState(value => {
      if (cur.contains(value)) Result.Next()
      else {
        cur = Some(value)
        Result.Next(value)
      }
    }, cur)
  }

  def throttle(interval: FiniteDuration)
              (implicit scheduler: Scheduler): ReadChannel[T] = {
    val intervalMs = interval.toMillis
    var next = 0L
    forkUni { t =>
      val time = scheduler.currentTimeMillis()
      if (next > time) Result.Next()
      else {
        next = time + intervalMs
        Result.Next(t)
      }
    }
  }

  override def values[U](implicit ev: T <:< Option[U]): ReadChannel[U] =
    asInstanceOf[ReadChannel[Option[U]]].forkUni {
      case None        => Result.Next()
      case Some(value) => Result.Next(value)
    }

  override def isDefined(implicit ev: T <:< Option[_]): ReadChannel[Boolean] =
    asInstanceOf[ReadChannel[Option[_]]].isNot(None)

  override def undefined(implicit ev: T <:< Option[_]): ReadChannel[Boolean] =
    asInstanceOf[ReadChannel[Option[_]]].is(None)

  override def mapValues[U, V](f: U => V)(implicit ev: T <:< Option[U]): ReadChannel[Option[V]] =
    asInstanceOf[ReadChannel[Option[U]]].forkUni {
      case None        => Result.Next(None)
      case Some(value) => Result.Next(Some(f(value)))
    }

  override def mapOrElse[U, V](f: U => V, default: => V)(implicit ev: T <:< Option[U]): ReadChannel[V] = {
    lazy val d = default
    asInstanceOf[ReadChannel[Option[U]]].forkUni {
      case None        => Result.Next(d)
      case Some(value) => Result.Next(f(value))
    }
  }

  override def count(implicit ev: T <:< Option[_]): ReadChannel[Int] =
    asInstanceOf[ReadChannel[Option[_]]].foldLeft(0) {
      case (acc, Some(_)) => acc + 1
      case (acc, None)    => 0
    }

  override def orElse[U](default: => ReadChannel[U])(implicit ev: T <:< Option[U]): ReadChannel[U] =
    asInstanceOf[ReadChannel[Option[U]]].flatMap {
      case None        => default
      case Some(value) => Var(value)
    }

  override def contains[U](value: U)(implicit ev: T <:< Option[U]): ReadChannel[Boolean] =
    asInstanceOf[ReadChannel[Option[U]]].map {
      case Some(`value`) => true
      case _             => false
    }
}
