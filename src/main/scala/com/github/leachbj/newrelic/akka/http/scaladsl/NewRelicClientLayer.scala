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

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.BidiFlow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, BidiShape, Inlet, Outlet}
import com.newrelic.agent.bridge.{AgentBridge, Token}

import scala.collection.mutable

object NewRelicClientLayer {
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

    val tokens: mutable.Stack[Token] = mutable.Stack()

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        graph =>
        setHandler(in1, new InHandler {
          override def onPush(): Unit = {
            val req = grab(in1)

            Option(AgentBridge.getAgent.getTransaction(false)).flatMap(t => Option(t.getToken)).foreach { token =>
              tokens.push(token)
            }

            push(out1, req)
          }
        })
        setHandler(out1, new OutHandler {
          override def onPull(): Unit = pull(in1)
        })
        setHandler(in2, new InHandler {
          override def onPush(): Unit = {
            val resp = grab(in2)

            if (tokens.nonEmpty) {
              tokens.pop().linkAndExpire()
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
