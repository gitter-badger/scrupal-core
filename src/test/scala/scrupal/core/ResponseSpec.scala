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

import java.io.ByteArrayInputStream

import akka.http.scaladsl.model.{MediaTypes, MediaType}
import org.specs2.matcher.MatchResult
import play.api.libs.iteratee.{Iteratee, Enumerator}
import play.api.libs.json.Json
import play.twirl.api.Html
import scrupal.test.ScrupalSpecification

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

class ResponseSpec extends ScrupalSpecification("Response") {

  scrupal.withExecutionContext { implicit ec : ExecutionContext  ⇒

    "Response" should {
      "have payload, mediaType and disposition" in {
        val response = new Response {
          def disposition: Disposition = Successful
          def mediaType: MediaType = MediaTypes.`application/octet-stream`
          def content : Array[Byte] = Array.empty[Byte]
          def toEnumerator(implicit ec: ExecutionContext) = Enumerator.empty[Array[Byte]]
        }
        success
      }
      "generate ExceptionResponse for exceptions thrown in Response.safely" in {
        val good = Response.safely { () ⇒ NoopResponse }
        val bad = Response.safely { () ⇒ throw new Exception("oops") }
        good must beEqualTo(NoopResponse)
        bad.isInstanceOf[ExceptionResponse] must beTrue
        val er = bad.asInstanceOf[ExceptionResponse]
        er.content.getMessage must beEqualTo("oops")
      }
    }
    "NoopResponse" should {
      "be unimplemented" in {
        NoopResponse.disposition must beEqualTo(Unimplemented)
      }
      "have application/octet-stream media type" in {
        NoopResponse.mediaType must beEqualTo(MediaTypes.`application/octet-stream`)
      }
      "generate empty body" in {
        checkEnum(NoopResponse.toEnumerator, 0)
      }
      "convert to EnumeratorResponse" in {
        val er = NoopResponse.toEnumeratorResponse
        checkEnum(er.toEnumerator, 0)
      }
    }
    "EnumeratorResponse" should {
      "have reflective content" in {
        val er = EnumeratorResponse(Enumerator.empty[Array[Byte]], MediaTypes.`text/plain`)
        er.toEnumerator must beEqualTo(er.content)
      }
    }

    def checkEnum(enum: Enumerator[Array[Byte]], sum: Int) : MatchResult[Int] = {
      val iteratee = Iteratee.fold[Array[Byte],Int](0) {
        case (total, elem) ⇒ total + elem.foldLeft[Int](0) { case (t, e) ⇒ t + e }
      }
      val future = (enum run iteratee).map { value ⇒ value must beEqualTo(sum) }
      Await.result(future,1.seconds)
    }

    "EnumeratorsREsponse" should {
      "aggregate enumerators" in {
        val enums = Seq(Enumerator(Array[Byte](3,4)),Enumerator(Array[Byte](1,2)))
        val er = EnumeratorsResponse(enums, MediaTypes.`application/octet-stream`)
        val expected = Enumerator(Array[Byte](3,4,1,2))
        checkEnum(er.toEnumerator, 10)
      }
    }

    "StreamResponse" should {
      "read a stream" in {
        val stream = new ByteArrayInputStream(Array[Byte](0,1,2,3,4))
        val sr = StreamResponse(stream, MediaTypes.`application/octet-stream`)
        checkEnum(sr.toEnumerator, 10)
      }
    }

    "OctetsResponse" should {
      "read octest" in {
        val octets = Array[Byte](1,2,3,4,5)
        val or = OctetsResponse(octets, MediaTypes.`application/octet-stream`)
        checkEnum(or.toEnumerator, 15)
      }
    }

    def strSum(str : String) : Int = str.foldLeft[Int](0) { case (sum,ch) ⇒ sum + ch.toInt }

    "StringResponse" should {
      "read string" in {
        val str = "This is a string of no significance."
        val sr = StringResponse(str)
        checkEnum(sr.toEnumerator, strSum(str))
      }
    }

    "HtmlResponse" should {
      "read HTML" in {
        val htmlStr = "<html><body></body></html>"
        val html = Html(htmlStr)
        val sr = HtmlResponse(html)
        checkEnum(sr.toEnumerator, strSum(htmlStr))
      }
    }

    "JsonResponse" should {
      "read JSON" in {
        val json = Json.parse("""{ "foo" : 3 }""")
        val js_as_string = json.toString()
        val jr = JsonResponse(json)
        checkEnum(jr.toEnumerator, strSum(js_as_string))
      }
    }

    "ExceptionResponse" should {
      "handle an Exception" in {
        val exception = new Exception("fake")
        val er = ExceptionResponse(exception)
        er.toText.contains("fake")
        checkEnum(er.toEnumerator, strSum(er.toText))
      }
    }

    "ErrorResponse" should {
      "read an error" in {
        val error = "This is a string of no significance."
        val er = ErrorResponse(error)
        val error_str = er.formatted
        checkEnum(er.toEnumerator, strSum(error_str))
      }
    }

    "JsonExceptionResponse" should {
      "generate JSON response" in {
        val exception = new Exception("fake")
        val er = JsonExceptionResponse(exception)
        val result = er.toJsonResponse.content.toString
        result.contains("fake") must beTrue
        checkEnum(er.toEnumerator, strSum(result))
      }
    }

    "UnimplementedResponse" should {
      "generate unimplemented message" in {
        val what = "Not Done Yet"
        val ur = UnimplementedResponse(what)
        val expected = ur.formatted
        checkEnum(ur.toEnumerator, strSum(expected))
      }
      "have plain/text media type" in {
        val ur = UnimplementedResponse("something")
        ur.mediaType must beEqualTo(MediaTypes.`text/plain`)
      }
    }

  }

}
