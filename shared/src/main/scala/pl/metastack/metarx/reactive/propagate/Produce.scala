package pl.metastack.metarx.reactive.propagate

trait Produce[T] {
  /** Propagates value to children */
  def produce(value: T): Unit

  /** @see [[produce]] */
  def !(value: T) = produce(value)
}
