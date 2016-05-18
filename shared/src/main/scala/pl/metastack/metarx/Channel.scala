package pl.metastack.metarx

import scala.concurrent.{ExecutionContext, Future}

object Channel {
  type Observer[T, U] = T => Result[U]

  def apply[T](): Channel[T] =
    new RootChannel[T] {
      def flush(f: T => Unit) { }
    }

  def from[T](future: Future[T])
             (implicit exec: ExecutionContext): Obs[T] = {
    val ch = Channel[T]()
    future.foreach(ch.produce)
    ch
  }

  /** Combine a read with a write channel. */
  def apply[T](read: Obs[T], write: WriteChannel[T]): Channel[T] = {
    val res = new RootChannel[T] {
      def flush(f: T => Unit) { read.flush(f) }
    }
    val pub = res.publish(write)
    res.subscribe(read, pub)
    res
  }
}

trait ChannelImplicits {
  implicit def FutureToObs[T](future: Future[T])
                             (implicit exec: ExecutionContext): Obs[T] = Channel.from(future)
}

object ChannelImplicits extends ChannelImplicits

trait Result[T] {
  val values: Seq[T]
}

object Result {
  case class Next[T](values: T*) extends Result[T]
  case class Done[T](values: T*) extends Result[T]
}

trait WriteChannel[T]
  extends reactive.propagate.Produce[T]
  with reactive.propagate.Subscribe[T]
{
  import Channel.Observer

  private[metarx] val children: Array[ChildChannel[T, _]]

  def detach(ch: ChildChannel[T, _])

  def flush(f: T => Unit)

  def produce(value: T): Unit =
    children.foreach(_.process(value))

  def produce[U](value: T, ignore: Obs[U]*) {
    assume(ignore.forall(cur => children.contains(cur.asInstanceOf[ChildChannel[T, _]])))
    children.foreach { child =>
      if (!ignore.contains(child)) child.process(value)
    }
  }

  def flatProduce(value: Option[T]) {
    value.foreach(produce)
  }

  def flatProduce[U](value: Option[T], ignore: Obs[U]*) {
    value.foreach(v => produce(v, ignore: _*))
  }

  /** Bi-directional fork for values */
  def forkBi[U](fwd: Observer[T, U], bwd: Observer[U, T], silent: Boolean = false): Channel[U] = {
    val ch = BiChildChannel[T, U](this, fwd, bwd)
    children += ch
    if (!silent) flush(ch.process)
    ch
  }

  /** Redirect stream from `other` to `this`. */
  def subscribe(ch: Obs[T]): Obs[Unit] = ch.attach(produce)

  def subscribe[U](ch: Obs[T], ignore: Obs[U]): Obs[Unit] =
    ch.attach(produce(_, ignore))
}

trait Channel[T]
  extends Obs[T]
  with WriteChannel[T]
  with reactive.propagate.Bind[T]
{
  def dispose(): Unit

  def toOpt: Opt[T] = {
    val res = Opt[T]()
    attach(res := Some(_))
    res
  }

  def state: Opt[T] = {
    val res = Opt[T]()
    res <<>> partialBiMap[Option[T]](x => Some(Some(x)), identity[Option[T]])
    res
  }

  def state(default: T): Var[T] = {
    val res = Var[T](default)
    res <<>> this
    res
  }

  def biMap[U](f: T => U, g: U => T): Channel[U] =
    forkBi(
      fwdValue => Result.Next(f(fwdValue)),
      bwdValue => Result.Next(g(bwdValue)))

  def partialBiMap[U](f: T => Option[U], g: U => Option[T]): Channel[U] =
    forkBi(
      fwdValue => Result.Next(f(fwdValue).toSeq: _*),
      bwdValue => Result.Next(g(bwdValue).toSeq: _*))

  /** Two-way binding; synchronises `this` and `other`. */
  def bind(other: Channel[T]) {
    var obsOther: Obs[Unit] = null
    val obsThis = silentAttach(other.produce(_, obsOther))
    obsOther = other.attach(produce(_, obsThis))
    flush(obsThis.asInstanceOf[UniChildChannel[Any, Any]].process)
  }

  def bind(other: Channel[T], ignoreOther: Obs[Unit]) {
    var obsOther: Obs[Unit] = null
    val obsThis = silentAttach(other.produce(_, obsOther, ignoreOther))
    obsOther = other.attach(produce(_, obsThis))
    flush(obsThis.asInstanceOf[UniChildChannel[Any, Any]].process)
  }

  /*def +(write: WriteChannel[T]): Channel[T] = {
    val res = new RootChannel[T] {
      def flush(f: T => Unit) { Channel.this.flush(f) }
    }
    val ignore = write << res
    this <<>> (res, ignore)
    res
  }*/

  override def toString = "Channel()"
}

trait ChildChannel[T, U]
  extends Channel[U]
  with ChannelDefaultSize[U]
{
  /** Return true if the stream is completed. */
  def process(value: T)
}

case class FlatChildChannel[T, U](parent: Obs[T],
                                  observer: Channel.Observer[T, Obs[U]])
  extends ChildChannel[T, U]
{
  private var bound: Obs[U] = null
  private var subscr: Obs[Unit] = null

  def onChannel(ch: Obs[U]) {
    if (subscr != null) subscr.dispose()
    bound = ch
    subscr = bound.attach(this ! _)
  }

  def process(value: T) {
    observer(value) match {
      case Result.Next(values @ _*) =>
        values.foreach(onChannel)

      case Result.Done(values @ _*) =>
        values.foreach(onChannel)
        dispose()
    }
  }

  def flush(f: U => Unit) {
    parent.flush { value =>
      observer(value) match {
        case Result.Next(values @ _*) =>
          values.foreach(_.flush(f))
        case Result.Done(values @ _*) =>
          values.foreach(_.flush(f))
          dispose()
      }
    }
  }

  def dispose() {
    parent.detach(this)

    if (subscr != null) subscr.dispose()

    children.foreach(_.dispose())
    children.clear()
  }

  override def toString = "FlatChildChannel()"
}

/** Uni-directional child */
case class UniChildChannel[T, U](parent: Obs[T],
                                 observer: Channel.Observer[T, U],
                                 onFlush: Option[() => Option[U]],
                                 doFilterCycles: Boolean = false)
  extends ChildChannel[T, U]
{
  private var inProcess = false

  def process(value: T) {
    synchronized {
      if (doFilterCycles) {
        if (inProcess) return
      } else assert(!inProcess, "Cycle found")

      inProcess = true

      try {
        observer(value) match {
          case Result.Next(values@_*) =>
            values.foreach(produce)
          case Result.Done(values@_*) =>
            values.foreach(produce)
            dispose()
        }
      } finally {
        inProcess = false
      }
    }
  }

  def flush(f: U => Unit) {
    synchronized {
      inProcess = true
      if (onFlush.isDefined) onFlush.get().foreach(f)
      else parent.flush(observer(_).values.foreach(f))
      inProcess = false
    }
  }

  def dispose() {
    parent.detach(this)

    children.foreach(_.dispose())
    children.clear()
  }

  override def toString = "UniChildChannel()"
}

/** Bi-directional child */
case class BiChildChannel[T, U](parent: WriteChannel[T],
                                fwd: Channel.Observer[T, U],
                                bwd: Channel.Observer[U, T])
  extends ChildChannel[T, U]
{
  val back = silentAttach { value =>
    bwd(value) match {
      case Result.Next(values @ _*) =>
        values.foreach(r => parent.produce(r, this))
        Result.Next()
      case Result.Done(values @ _*) =>
        values.foreach(r => parent.produce(r, this))
        Result.Done()
    }
  }

  def process(value: T) {
    fwd(value) match {
      case Result.Next(values @ _*) =>
        values.foreach(r => produce(r, back))
      case Result.Done(values @ _*) =>
        values.foreach(r => produce(r, back))
        dispose()
    }
  }

  def flush(f: U => Unit) {
    parent.flush(fwd(_).values.foreach(f))
  }

  def dispose() {
    parent.detach(this)

    back.dispose()

    children.foreach(_.dispose())
    children.clear()
  }

  override def toString = "BiChildChannel()"
}

case class BiFlatChildChannel[T, U](parent: Obs[T],
                                    observer: Channel.Observer[T, Channel[U]])
  extends ChildChannel[T, U]
{
  private var bound: Channel[U] = null
  private var subscr: Obs[Unit] = null

  val back = silentAttach { value =>
    if (bound != null && subscr != null) bound.produce(value, subscr)
  }

  def onChannel(ch: Channel[U]) {
    if (subscr != null) subscr.dispose()
    bound = ch
    subscr = bound.silentAttach(produce(_, back))
    bound.flush(produce(_, back))
  }

  def process(value: T) {
    observer(value) match {
      case Result.Next(values @ _*) =>
        values.foreach(onChannel)

      case Result.Done(values @ _*) =>
        values.foreach(onChannel)
        dispose()
    }
  }

  def flush(f: U => Unit) {
    if (bound != null) bound.flush(f)
  }

  def dispose() {
    parent.detach(this)

    if (subscr != null) subscr.dispose()

    back.dispose()

    children.foreach(_.dispose())
    children.clear()
  }

  override def toString = "BiFlatChildChannel()"
}

trait ChannelDefaultSize[T] {
  this: Obs[T] =>

  def size: Obs[Int] =
    foldLeft(0) { case (acc, cur) => acc + 1 }
}

trait ChannelDefaultDispose[T] {
  private[metarx] val children: Array[ChildChannel[T, _]]

  def dispose(): Unit = {
    children.foreach(_.dispose())
    children.clear()
  }
}

trait RootChannel[T]
  extends Channel[T]
  with ChannelDefaultSize[T]
  with ChannelDefaultDispose[T]
