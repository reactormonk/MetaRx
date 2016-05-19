package pl.metastack.metarx.reactive.propagate

import pl.metastack.metarx.{Obs, Sink}

trait Publish[T] {
  def publish(ch: Sink[T]): Obs[Unit]
  def >>(ch: Sink[T]) = publish(ch)
}
