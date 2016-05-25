package pl.metastack.metarx

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

  def produce[U](value: T, ignore: ReadChannel[U]*) {
    assume(ignore.forall(cur => children.contains(cur.asInstanceOf[ChildChannel[T, _]])))
    children.foreach { child =>
      if (!ignore.contains(child)) child.process(value)
    }
  }

  def flatProduce(value: Option[T]) {
    value.foreach(produce)
  }

  def flatProduce[U](value: Option[T], ignore: ReadChannel[U]*) {
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
  def subscribe(ch: ReadChannel[T]): ReadChannel[Unit] = ch.attach(produce)

  def subscribe[U](ch: ReadChannel[T], ignore: ReadChannel[U]): ReadChannel[Unit] =
    ch.attach(produce(_, ignore))
}
