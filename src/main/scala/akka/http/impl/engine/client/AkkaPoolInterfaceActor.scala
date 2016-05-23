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
package akka.http.impl.engine.client

import akka.actor.Actor.Receive
import akka.http.impl.engine.client.PoolFlow.ResponseContext
import akka.stream.actor.ActorSubscriberMessage.OnNext
import com.github.leachbj.newrelic.akka.http.client.{AkkaPoolRequest, RequestContext}
import com.newrelic.agent.bridge.{AgentBridge, TransactionNamePriority}
import com.newrelic.api.agent.Trace
import com.newrelic.api.agent.weaver.{Weave, Weaver}

@Weave(originalName = "akka.http.impl.engine.client.PoolInterfaceActor")
abstract class AkkaPoolInterfaceActor {
  def isActive: Boolean

  @Trace(async = true)
  @SuppressWarnings(Array("UnusedMethodParameter"))
  def aroundReceive(receive: Receive, msg: Any): Unit = {
    msg match {
      case request: AkkaPoolRequest if isActive =>
        Option(AgentBridge.getAgent.getTransaction(false)).foreach { transaction =>
          if (request.token != null) request.token.expire()
          request.token = transaction.getToken
        }
      case OnNext(ResponseContext(rc, responseTry)) => rc match {
        case arc: RequestContext => // ignore the fruitless type check it will match for the woven classes
          Option(arc.token).foreach { token =>
            if (token.linkAndExpire()) {
              AgentBridge.getAgent.getTracedMethod.setMetricName("Akka", "PoolRequest", "completeResponse")
              AgentBridge.getAgent.getTransaction.setTransactionName(TransactionNamePriority.FRAMEWORK_LOW, false, "Actor", "dispatchRequest")
            }

            arc.token = null
          }
        case _ =>
      }

      case _ =>
    }

    Weaver.callOriginal[Unit]
  }

  def dispatchRequest(pr: AkkaPoolRequest): Unit = {
    Option(pr.token).foreach { token =>
      if (token.link()) {
        AgentBridge.getAgent.getTracedMethod.setMetricName("Akka", "PoolRequest", "dispatchRequest")
        AgentBridge.getAgent.getTransaction.setTransactionName(TransactionNamePriority.FRAMEWORK_LOW, false, "Actor", "dispatchRequest")
      }

      token.expire()
      pr.token = null
    }

    Weaver.callOriginal[Unit]
  }
}
