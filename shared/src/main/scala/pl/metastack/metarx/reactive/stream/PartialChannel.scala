package pl.metastack.metarx.reactive.stream

import pl.metastack.metarx.Obs

trait PartialChannel[T] {
  /** true if partial channel has a value, false otherwise */
  def isDefined(implicit ev: T <:< Option[_]): Obs[Boolean]

  def undefined(implicit ev: T <:< Option[_]): Obs[Boolean]

  /** Filters out all defined values */
  def values[U](implicit ev: T <:< Option[U]): Obs[U]

  def mapValues[U, V](f: U => V)(implicit ev: T <:< Option[U]): Obs[Option[V]]

  def mapOrElse[U, V](f: U => V, default: => V)(implicit ev: T <:< Option[U]): Obs[V]

  def count(implicit ev: T <:< Option[_]): Obs[Int]

  def orElse[U](default: => Obs[U])(implicit ev: T <:< Option[U]): Obs[U]

  def contains[U](value: U)(implicit ev: T <:< Option[U]): Obs[Boolean]
}
