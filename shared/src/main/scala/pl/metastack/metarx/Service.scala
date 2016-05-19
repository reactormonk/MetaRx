package pl.metastack.metarx

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait Service[Req, Resp] extends reactive.propagate.Produce[Req] {
  val process: PartialFunction[Req, Future[Resp]]

  def produce(req: Req): Unit = process(req)

  def request(req: Req): Future[Resp] = process(req)
  def ?(req: Req): Future[Resp] = request(req)

  def compose(service: Service[Req, Resp]): Service[Req, Resp] =
    Service(process.orElse(service.process))

  def map(f: PartialFunction[Req, Req]): Service[Req, Resp] =
    Service {
      Function.unlift { req: Req =>
        process.lift(f.lift(req).getOrElse(req))
      }
    }

  def mapResponse(f: Resp => Resp): Service[Req, Resp] =
    Service {
      Function.unlift { req: Req =>
        process.lift(req).map(_.map(f))
      }
    }

  def filter(f: Req => Boolean): Service[Req, Resp] =
    Service {
      Function.unlift { req: Req =>
        if (f(req)) process.lift(req)
        else None
      }
    }
}

object Service {
  def apply[Req, Resp](f: PartialFunction[Req, Future[Resp]]): Service[Req, Resp] =
    new Service[Req, Resp] {
      val process = f
    }
}
