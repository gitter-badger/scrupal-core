package scrupal.core

import play.api.http.{Status, NoHttpFilters, HttpConfiguration}
import play.api.mvc.{Result, AnyContent}
import play.api.routing.Router
import play.api.test.FakeRequest
import scrupal.test.{FakeSite, ScrupalSpecification}

import scala.concurrent.ExecutionContext.Implicits.global

class ScrupalRequestHandlerSpec extends ScrupalSpecification("ScrupalRequestHandler") {

  "ScrupalRequestHandler" should {
    "route request to correct site" in {
      val site1 = new FakeSite("one", "one.com")(scrupal)
      val site2 = new FakeSite("two", "two.com")(scrupal)
      val app = scrupal.application
      val httpConf = HttpConfiguration.fromConfiguration(app.configuration)
      val srh = new ScrupalRequestHandler(scrupal, Router.empty, app.errorHandler, httpConf, NoHttpFilters)
      val orig_request = FakeRequest("GET", "/index.html").withHeaders("Host" -> "one.com:80")
      val (request, handler) = srh.handlerForRequest(orig_request)
      handler.isInstanceOf[ReactorAction] must beTrue
      val ra : ReactorAction =  handler.asInstanceOf[ReactorAction]
      ra.context.site must beEqualTo(Some(site1))
      val future = ra.parser(request).run.map {
        case Left(r: Result) ⇒ r.header.status must beEqualTo(Status.OK)
        case Right(c: AnyContent) ⇒ c.asText.getOrElse("") must beEqualTo("")
      }
      await(future)
      val future2 = ra.apply(orig_request).map { result: Result  ⇒ result.header.status must beEqualTo(Status.NOT_IMPLEMENTED) }
      await(future2)
    }
  }
}
