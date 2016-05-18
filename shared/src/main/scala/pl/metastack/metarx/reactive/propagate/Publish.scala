package pl.metastack.metarx.reactive.propagate

import pl.metastack.metarx.{Obs, WriteChannel}

trait Publish[T] {
  def publish(ch: WriteChannel[T]): Obs[Unit]
  def >>(ch: WriteChannel[T]) = publish(ch)
}
