package pl.metastack.metarx.reactive.stream

import pl.metastack.metarx.Obs

trait RelativeOrder[T] {
  /**
   * @note Buffers: Current row before `value`
   * @note Channels: Value that is produced right before each `value`
   */
  def before(value: T): Obs[T]

  /**
   * @note Buffers: Current row after `value`
   * @note Channels: Value that is produced right after each `value`
   */
  def after(value: T): Obs[T]

  /** @see [[before]] */
  def beforeOption(value: T): Obs[T]

  /** @see [[after]] */
  def afterOption(value: T): Obs[T]
}
