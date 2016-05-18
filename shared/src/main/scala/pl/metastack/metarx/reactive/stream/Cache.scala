package pl.metastack.metarx.reactive.stream

import pl.metastack.metarx.{ReadPartialChannel, ObsState}

trait Cache[T] {
  def cache: ReadPartialChannel[T]
  def cache(default: T): ObsState[T]
}
