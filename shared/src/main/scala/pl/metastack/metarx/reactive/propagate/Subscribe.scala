package pl.metastack.metarx.reactive.propagate

import pl.metastack.metarx.Obs

trait Subscribe[T] {
  def subscribe(ch: Obs[T]): Obs[Unit]
  def <<(ch: Obs[T]): Obs[Unit] = subscribe(ch)
}
