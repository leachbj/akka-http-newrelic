/*
 * Copyright (c) 2016 Bernard Leach
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.leachbj.newrelic.akka.http.client

import akka.http.scaladsl.model.HttpResponse
import com.github.leachbj.newrelic.akka.http.scaladsl.InboundHttpHeaders
import com.newrelic.agent.bridge.external.ExternalParametersFactory
import com.newrelic.agent.bridge.{AgentBridge, TracedActivity}
import java.net.URI
import java.util.logging.Level

import scala.util.{Failure, Success, Try}

class TracedActivityCompletion(uriString: String, tracedActivity: TracedActivity) extends ((Try[HttpResponse]) => Unit) {
  def apply(tryResponse: Try[HttpResponse]) = tryResponse match {
    case Success(response) =>
      if (tracedActivity != null) {
        AgentBridge.getAgent.getLogger.log(Level.FINEST, "PoolGateway response {0} {1}", response, tracedActivity)
        tracedActivity.getTracedMethod.reportAsExternal(ExternalParametersFactory.createForHttp("AkkaHttpClient", new URI(uriString), "sendReceive", new InboundHttpHeaders(response)))
        tracedActivity.finish()
      }
    case Failure(e) =>
      AgentBridge.getAgent.getLogger.log(Level.FINEST, "PoolGateway failure {0}", tracedActivity)
      if (tracedActivity != null) {
        tracedActivity.getTracedMethod.reportAsExternal(ExternalParametersFactory.createForHttp("AkkaHttpClient", new URI(uriString), "sendReceiveOnFailure"))
        tracedActivity.finish(e)
      }
  }
}
