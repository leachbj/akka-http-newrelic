/**
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
package com.github.leachbj.newrelic.akka.http.scaladsl

import akka.http.scaladsl.model.headers.Host
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.BidiFlow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import com.newrelic.agent.bridge.external.ExternalParametersFactory
import com.newrelic.agent.bridge.{AgentBridge, TracedActivity}
import com.newrelic.api.agent.Trace
import java.net.URI
import java.util.logging.Level

import scala.collection.mutable

object NewRelicClientLayer {
  @Trace(async = true, leaf = true)
  def openConnection(req: HttpRequest): Option[TracedActivity] =
    Option(AgentBridge.getAgent.getTransaction(false)).flatMap { transaction =>
      Option(transaction.createAndStartTracedActivity()).map { tracedActivity =>
        tracedActivity.setAsyncThreadName("external")

        val tm = tracedActivity.getTracedMethod
        tm.setMetricName("akka", "client", "routing")

        val hostName = req.header[Host].map(h => s"http://${h.host.address}:${h.port}").getOrElse(s"${req.uri.scheme}://${req.uri.authority.host}:${req.uri.authority.port}")
        val remoteHost: URI = new URI(hostName)
        tm.reportAsExternal(ExternalParametersFactory.createForHttp("AkkaHttpClient", new URI(hostName), "connection"))

        AgentBridge.getAgent.getLogger.log(Level.INFO, s"Connecting to $remoteHost")

        tracedActivity
      }
    }

  /**
    * {{{
    *        +------+
    *  In1 ~>|      |~> Out1
    *        | bidi |
    * Out2 <~|      |<~ In2
    *        +------+
    * }}}
    */
  val metrics = BidiFlow.fromGraph(new GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {
    val in1: Inlet[HttpRequest] = Inlet("nr-req.in")
    val out1: Outlet[HttpRequest] = Outlet("nr-req.out")
    val in2: Inlet[HttpResponse] = Inlet("nr-resp.in")
    val out2: Outlet[HttpResponse] = Outlet("nr-resp.out")
    override val shape = BidiShape(in1, out1, in2, out2)

    val activities: mutable.Stack[TracedActivity] = mutable.Stack()

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        graph =>
        setHandler(in1, new InHandler {
          override def onPush(): Unit = {
            val req = grab(in1)

            val updatedReq = openConnection(req).map { ta =>
              val outboundHeaders = new OutboundHttpHeaders(req)
              ta.getTracedMethod.addOutboundRequestHeaders(outboundHeaders)

              activities.push(ta)
              outboundHeaders.request
            }.getOrElse(req)

            push(out1, updatedReq)
          }
        })
        setHandler(out1, new OutHandler {
          override def onPull(): Unit = pull(in1)
        })
        setHandler(in2, new InHandler {
          override def onPush(): Unit = {
            val resp = grab(in2)

            activities.headOption.foreach { ta =>
              ta.getTracedMethod.readInboundResponseHeaders(InboundHttpHeaders(resp))
              ta.finish()
              activities.pop()
            }

            push(out2, resp)
          }
        })
        setHandler(out2, new OutHandler {
          override def onPull(): Unit = pull(in2)
        })
      }
  })
}
