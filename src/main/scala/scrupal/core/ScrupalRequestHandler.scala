/**********************************************************************************************************************
 * This file is part of Scrupal, a Scalable Reactive Web Application Framework for Content Management                 *
 *                                                                                                                    *
 * Copyright (c) 2015, Reactific Software LLC. All Rights Reserved.                                                   *
 *                                                                                                                    *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance     *
 * with the License. You may obtain a copy of the License at                                                          *
 *                                                                                                                    *
 *     http://www.apache.org/licenses/LICENSE-2.0                                                                     *
 *                                                                                                                    *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed   *
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for  *
 * the specific language governing permissions and limitations under the License.                                     *
 **********************************************************************************************************************/

package scrupal.core

import javax.inject.{Inject, Singleton}

import play.api.http.{HttpConfiguration, HttpFilters, HttpErrorHandler, DefaultHttpRequestHandler}
import play.api.mvc._
import play.api.routing.Router

import scala.concurrent.Future
import scala.language.implicitConversions

case class ReactorAction(context: Context, reactor: Reactor) extends Action[AnyContent] {
  def parser: BodyParser[AnyContent] = BodyParsers.parse.anyContent
  def apply(request: Request[AnyContent]): Future[Result] = {
    reactor.resultFrom(context, request)
  }
}


/** Scrupal Request Handler
  *
  * Play will invoke this handler to dispatch HTTP Requests as they come in. It's job is
  */
@Singleton()
class ScrupalRequestHandler @Inject() (
    scrupal: Scrupal,
    globalRouter : Router,
    errorHandler : HttpErrorHandler,
    httpConfiguration : HttpConfiguration,
    httpFilters : HttpFilters
) extends DefaultHttpRequestHandler(globalRouter, errorHandler, httpConfiguration, httpFilters) {

  def getReactor(header: RequestHeader, site: Site, subDomain: Option[String]) : (RequestHeader, Handler) = {
    subDomain.flatMap {
      sub ⇒ site.reactorFor(header, sub)
    } orElse {
      site.reactorFor(header)
    } match {
      case Some(rx) ⇒
        val context = Context(scrupal, site)
        header → ReactorAction(context, rx)
      case None ⇒
        super.handlerForRequest(header)
    }
  }

  override def handlerForRequest(header: RequestHeader) : (RequestHeader, Handler) = {
    scrupal.siteForRequest(header) match {
      case (Some(site),Some(subDomain)) =>
        getReactor(header, site, Some(subDomain))
      case (Some(site), None) ⇒
        getReactor(header, site, None)
      case _ =>
        super.handlerForRequest(header)
    }
  }
}
