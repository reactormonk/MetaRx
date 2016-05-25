package pl.metastack.metarx.reactive.stream

import pl.metastack.metarx.{ReadPartialChannel, ReadChannelState}

trait Cache[T] {
  def cache: ReadPartialChannel[T]
  def cache(default: T): ReadChannelState[T]
}
