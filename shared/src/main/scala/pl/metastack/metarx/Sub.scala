package pl.metastack.metarx

import java.util.concurrent.atomic.AtomicReference

class Sub[T](init: T) extends Var[T](init) {
  private val subscription =
    new AtomicReference(Option.empty[Obs[Unit]])

  def set(subscriber: Obs[T]): Unit = {
    val old = subscription.getAndSet(Some(subscriber.attach(super.set)))
    old.foreach(_.dispose())
  }

  def :=(subscriber: Obs[T]): Unit = set(subscriber)

  override def set(value: T): Unit = {
    val old = subscription.getAndSet(None)
    old.foreach(_.dispose())
    super.set(value)
  }

  def detach(): Unit = {
    val old = subscription.getAndSet(None)
    old.foreach(_.dispose())
  }

  def dep[U](fwd: Obs[T] => Obs[U],
             bwd: Obs[U] => Obs[T]): Dep[T, U] =
    new Dep(this, fwd, bwd)

  override def toString = s"Sub()"
  override def dispose(): Unit = detach()
}

object Sub {
  def apply[T](init: T): Sub[T] = new Sub[T](init)

  def apply[T](ch: Obs[T]): Sub[T] = {
    val subscriber = new Sub[T](null.asInstanceOf[T])
    subscriber := ch
    subscriber
  }
}
