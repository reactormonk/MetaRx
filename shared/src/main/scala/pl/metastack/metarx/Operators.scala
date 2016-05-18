package pl.metastack.metarx

import scala.math.{Fractional, Numeric}

object Operators {
  trait Base[T, MapOut, MapResult, ZipOut] {
    def map(f: T => MapResult): Obs[MapOut]
    def zipWith(other: Obs[T])(f: (T, T) => MapResult): Obs[ZipOut]
    def collect[U](f: PartialFunction[T, U]): Obs[U]
  }

  trait DefaultBase[T, MapResult] extends Base[T, MapResult, MapResult, MapResult] {
    def ch: Obs[T]

    override def map(f: T => MapResult): Obs[MapResult] = ch.map(f)
    override def zipWith(other: Obs[T])
                        (f: (T, T) => MapResult): Obs[MapResult] =
      ch.zipWith(other)(f)
    override def collect[U](f: PartialFunction[T, U]): Obs[U] =
      ch.collect(f)
  }

  trait OptionBase[T, MapResult] extends Base[T, Option[MapResult], MapResult, Option[MapResult]] {
    def ch: Obs[Option[T]]

    override def map(f: T => MapResult): Obs[Option[MapResult]] =
      ch.mapValues(f)

    override def zipWith(other: Obs[T])
                        (f: (T, T) => MapResult): Obs[Option[MapResult]] =
      ch.zipWith(other) {
        case (None, u) => None
        case (Some(t), u) => Some(f(t, u))
      }

    override def collect[U](f: PartialFunction[T, U]): Obs[U] =
      ch.collect {
        case Some(s) if f.isDefinedAt(s) => f(s)
      }
  }

  trait BooleanOps[Out, ZipOut] extends Base[Boolean, Out, Boolean, ZipOut] {
    def &&(other: Obs[Boolean]): Obs[ZipOut] =
      zipWith(other)(_ && _)
    def &&(argument: => Boolean): Obs[Out] = map(_ && argument)

    def ||(other: Obs[Boolean]): Obs[ZipOut] =
      zipWith(other)(_ || _)
    def ||(argument: => Boolean): Obs[Out] = map(_ || argument)

    def unary_! = map(!_)

    @deprecated("Use `!ch` instead", "v0.1.5")
    def isFalse: Obs[Out] = unary_!

    @deprecated("Use `collect { case true => () }` instead", "v0.1.5")
    def onTrue: Obs[Unit] = collect { case true => () }

    @deprecated("Use `collect { case false => () }` instead", "v0.1.5")
    def onFalse: Obs[Unit] = collect { case false => () }
  }

  trait NumericOps[T, Out, ZipOut] extends Base[T, Out, T, ZipOut] {
    val num: Numeric[T]

    import num._

    def +(other: Obs[T]): Obs[ZipOut] = zipWith(other)(plus)
    def +(argument: => T): Obs[Out] = map(_ + argument)
    def -(other: Obs[T]): Obs[ZipOut] = zipWith(other)(minus)
    def -(argument: => T): Obs[Out] = map(_ - argument)
    def *(other: Obs[T]): Obs[ZipOut] = zipWith(other)(times)
    def *(argument: => T): Obs[Out] = map(_ * argument)

    def unary_-(other: Obs[T]): Obs[Out] = map(-_)
  }

  trait IntegralOps[T, Out, ZipOut] extends Base[T, Out, T, ZipOut] {
    val integral: Integral[T]

    import integral._

    def /(other: Obs[T]): Obs[ZipOut] = zipWith(other)(quot)
    def /(arg: => T): Obs[Out] = map(_ / arg)
    def %(other: Obs[T]): Obs[ZipOut] = zipWith(other)(rem)
  }

  trait FractionalOps[T, Out, ZipOut] extends Base[T, Out, T, ZipOut] {
    val fractional: Fractional[T]

    import fractional._

    def /(other: Obs[T]): Obs[ZipOut] = zipWith(other)(div)
    def /(arg: => T): Obs[Out] = map(_ / arg)
  }

  trait OrderingOps[T, Out, ZipOut] extends Base[T, Out, Boolean, ZipOut] {
    val ordering: Ordering[T]

    import ordering._

    def <(other: Obs[T]): Obs[ZipOut] = zipWith(other)(_ < _)
    def <=(other: Obs[T]): Obs[ZipOut] = zipWith(other)(_ <= _)
    def <(arg: => T): Obs[Out] = map(_ < arg)
    def <=(arg: => T): Obs[Out] = map(_ <= arg)

    def >(other: Obs[T]): Obs[ZipOut] = zipWith(other)(_ > _)
    def >=(other: Obs[T]): Obs[ZipOut] = zipWith(other)(_ >= _)
    def >(arg: => T): Obs[Out] = map(_ > arg)
    def >=(arg: => T): Obs[Out] = map(_ >= arg)
  }

  trait StringOps[Out, ZipOut] extends Base[String, Out, String, ZipOut] {
    def +(other: Obs[String]): Obs[ZipOut] =
      zipWith(other)(_ + _)
    def +(value: => String): Obs[Out] = map(_ + value)
  }
}

trait Operators {
  implicit class StringChannel(val ch: Obs[String])
    extends Operators.StringOps[String, String]
    with Operators.DefaultBase[String, String]

  implicit class OptionalStringChannel(val ch: Obs[Option[String]])
    extends Operators.StringOps[Option[String], Option[String]]
    with Operators.OptionBase[String, String]

  implicit class BooleanChannel(val ch: Obs[Boolean])
    extends Operators.BooleanOps[Boolean, Boolean]
    with Operators.DefaultBase[Boolean, Boolean]

  implicit class OptionalBooleanChannel(val ch: Obs[Option[Boolean]])
    extends Operators.BooleanOps[Option[Boolean], Option[Boolean]]
    with Operators.OptionBase[Boolean, Boolean]

  implicit class BooleanValue(value: Boolean) {
    def &&(ch: Obs[Boolean]): Obs[Boolean] = ch && value
    def ||(ch: Obs[Boolean]): Obs[Boolean] = ch || value
  }

  implicit class BooleanValue2(value: Boolean) {
    def &&(ch: Obs[Option[Boolean]]): Obs[Option[Boolean]] =
      ch && value
    def ||(ch: Obs[Option[Boolean]]): Obs[Option[Boolean]] =
      ch || value
  }

  implicit class NumericChannel[T: Numeric](val ch: Obs[T])
                                           (implicit val num: Numeric[T])
    extends Operators.NumericOps[T, T, T]
    with Operators.DefaultBase[T, T] {

    import num._

    @deprecated("Use map(_.toInt) instead", "v0.1.5")
    def toInt: Obs[Int] = ch.map(_.toInt)

    @deprecated("Use map(_.toLong) instead", "v0.1.5")
    def toLong: Obs[Long] = ch.map(_.toLong)

    @deprecated("Use map(_.toFloat) instead", "v0.1.5")
    def toFloat: Obs[Float] = ch.map(_.toFloat)

    @deprecated("Use map(_.toDouble) instead", "v0.1.5")
    def toDouble: Obs[Double] = ch.map(_.toDouble)
  }

  implicit class OptionalNumericChannel[T: Numeric](val ch: Obs[Option[T]])
                                                   (implicit val num: Numeric[T])
    extends Operators.NumericOps[T, Option[T], Option[T]]
    with Operators.OptionBase[T, T]

  implicit class NumericValue[T: Numeric](value: T)(implicit num: Numeric[T]) {
    import num._

    def +(other: Obs[T]): Obs[T] = other.map(plus(value, _))
    def -(other: Obs[T]): Obs[T] = other.map(minus(value, _))
    def *(other: Obs[T]): Obs[T] = other.map(times(value, _))
  }

  implicit class NumericValue2[T: Numeric](value: T)(implicit num: Numeric[T]) {
    import num._

    def +(other: Obs[Option[T]]): Obs[Option[T]] =
      other.mapValues((x: T) => plus(value, x))
    def -(other: Obs[Option[T]]): Obs[Option[T]] =
      other.mapValues((x: T) => minus(value, x))
    def *(other: Obs[Option[T]]): Obs[Option[T]] =
      other.mapValues((x: T) => times(value, x))
  }

  implicit class IntegralChannel[T: Integral](val ch: Obs[T])
                                             (implicit val integral: Integral[T])
    extends Operators.IntegralOps[T, T, T]
    with Operators.DefaultBase[T, T]

  implicit class OptionalIntegralChannel[T: Integral](val ch: Obs[Option[T]])
                                                     (implicit val integral: Integral[T])
    extends Operators.IntegralOps[T, Option[T], Option[T]]
    with Operators.OptionBase[T, T]

  implicit class IntegralValue[T: Integral](value: T)(implicit num: Integral[T]) {
    import num._

    def /(other: Obs[T]): Obs[T] = other.map(quot(value, _))
    def %(other: Obs[T]): Obs[T] = other.map(rem(value, _))
  }

  implicit class IntegralValue2[T: Integral](value: T)
                                            (implicit num: Integral[T]) {
    import num._

    def /(other: Obs[Option[T]]): Obs[Option[T]] =
      other.mapValues((t: T) => quot(value, t))
    def %(other: Obs[Option[T]]): Obs[Option[T]] =
      other.mapValues((t: T) => rem(value, t))
  }

  implicit class FractionalChannel[T: Fractional](val ch: Obs[T])
                                                 (implicit val fractional: Fractional[T])
    extends Operators.FractionalOps[T, T, T]
    with Operators.DefaultBase[T, T]

  implicit class OptionalFractionalChannel[T: Fractional](val ch: Obs[Option[T]])
                                                         (implicit val fractional: Fractional[T])
    extends Operators.FractionalOps[T, Option[T], Option[T]]
    with Operators.OptionBase[T, T]

  implicit class FractionalValue[T: Fractional](value: T)
                                               (implicit num: Fractional[T]) {
    import num._
    def /(other: Obs[T]): Obs[T] = other.map(div(value, _))
  }

  implicit class FractionalValue2[T: Fractional](value: T)
                                               (implicit num: Fractional[T]) {
    import num._
    def /(other: Obs[Option[T]]): Obs[Option[T]] =
      other.mapValues((x: T) => div(value, x))
  }

  implicit class OrderingChannel[T: Ordering](val ch: Obs[T])
                                             (implicit val ordering: Ordering[T])
    extends Operators.OrderingOps[T, Boolean, Boolean]
    with Operators.DefaultBase[T, Boolean]

  implicit class OptionalOrderingChannel[T: Fractional](val ch: Obs[Option[T]])
                                                       (implicit val ordering: Ordering[T])
    extends Operators.OrderingOps[T, Option[Boolean], Option[Boolean]]
    with Operators.OptionBase[T, Boolean]

  implicit class OrderingValue[T: Ordering](value: T)
                                           (implicit ordering: Ordering[T]) {
    import ordering._

    def >(other: Obs[T]): Obs[Boolean] = other.map(value > _)
    def >=(other: Obs[T]): Obs[Boolean] = other.map(value >= _)
    def <(other: Obs[T]): Obs[Boolean] = other.map(value < _)
    def <=(other: Obs[T]): Obs[Boolean] = other.map(value <= _)
  }

  implicit class OrderingValue2[T: Ordering](value: T)
                                            (implicit ordering: Ordering[T]) {
    import ordering._

    def >(other: Obs[Option[T]]): Obs[Option[Boolean]] =
      other.mapValues((t: T) => value > t)
    def >=(other: Obs[Option[T]]): Obs[Option[Boolean]] =
      other.mapValues((t: T) => value >= t)
    def <(other: Obs[Option[T]]): Obs[Option[Boolean]] =
      other.mapValues((t: T) => value < t)
    def <=(other: Obs[Option[T]]): Obs[Option[Boolean]] =
      other.mapValues((t: T) => value <= t)
  }
}
