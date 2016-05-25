package pl.metastack.metarx

trait ReadChannelState[T] extends ReadChannel[T] {
  def get: T
}

/** In Rx terms, a [[State]] can be considered a cold observable. */
trait State[T]
  extends Channel[T]
  with ReadChannelState[T]
  with ChannelDefaultDispose[T] {

  /** Sets and propagates value to children */
  def set(value: T): Unit

  /** @see [[set]] */
  def :=(value: T): Unit = set(value)

  def update(f: T => T): Unit = set(f(get))

  // Shapeless has not been built yet for Scala.js 0.6.0
  /*def value[U](f: shapeless.Lens[T, T] => shapeless.Lens[T, U]) =
    lens(f(shapeless.lens[T]))

  /** Two-way lens that propagates back changes to all observers. */
  def lens[U](l: shapeless.Lens[T, U]): Channel[U] = {
    var cur: Option[T] = None
    forkBi(
      fwdValue => { cur = Some(fwdValue); Result.Next(Some(l.get(fwdValue))) },
      bwdValue => Result.Next(Some(l.set(cur.get)(bwdValue))))
  }*/
}
