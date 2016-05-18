package pl.metastack.metarx.reactive.stream

import pl.metastack.metarx.Obs

/**
 * Operations for streams that can only have one value at the same time, like
 * channels
 */
trait Is[T] {
  /** Current value is equal to `value` */
  def is(value: T): Obs[Boolean]
  def is(value: Obs[T]): Obs[Boolean]
  def ===(value: T) = is(value)
  def ===(value: Obs[T]) = is(value)

  /** Current value is not equal to `value` */
  def isNot(value: T): Obs[Boolean]
  def isNot(value: Obs[T]): Obs[Boolean]
  def !==(value: T) = isNot(value)
  def !==(value: Obs[T]) = isNot(value)
}
