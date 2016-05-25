package pl.metastack.metarx

import org.scalatest.AsyncFunSuite

import scala.concurrent.Future

class ServiceSpec extends AsyncFunSuite {
  sealed trait Request
  case class RequestA(i: Int = 0) extends Request
  case class RequestB() extends Request

  sealed trait Response
  case class ResponseA(i: Int = 0) extends Response
  case class ResponseB() extends Response

  test("Querying service") {
    var triggered = false
    val service = Service[Request, Response] {
      case _: RequestA =>
        triggered = true
        Future.successful(ResponseA())

      case RequestB() => Future.successful(ResponseB())
    }

    service ! RequestA()
    assert(triggered)
  }

  test("Sending request to service") {
    val service = Service[Request, Response] {
      case _: RequestA => Future.successful(ResponseA())
      case RequestB() => Future.successful(ResponseB())
    }

    val response = service ? RequestA()
    response.map(x => assert(x == ResponseA()))
  }

  test("Composing services") {
    val serviceA = Service[Request, Response] {
      case _: RequestA => Future.successful(ResponseA())
    }

    val serviceB = Service[Request, Response] {
      case RequestB() => Future.successful(ResponseB())
    }

    val service = serviceA.compose(serviceB)

    val response = service ? RequestB()
    response.map(x => assert(x == ResponseB()))
  }

  test("Mapping requests") {
    val service = Service[Request, Response] {
      case r: RequestA => Future.successful(ResponseA(r.i))
      case RequestB() => Future.successful(ResponseB())
    }.map {
      case r: RequestA => RequestA(r.i + 1)
    }

    for {
      r <- service ? RequestA()
      r2 <- service ? RequestB()
    } yield {
      assert(r == ResponseA(1) && r2 == ResponseB())
    }
  }

  test("Forwarding requests") {
    val service = Service[Request, Response] {
      case r: RequestA => Future.successful(ResponseA())
    }

    val service2 = Service[Request, Response] {
      case RequestB() => Future.successful(ResponseB())
      case r => service ? r
    }

    for {
      r <- service2 ? RequestA()
      r2 <- service2 ? RequestB()
    } yield {
      assert(r == ResponseA() && r2 == ResponseB())
    }
  }
}
