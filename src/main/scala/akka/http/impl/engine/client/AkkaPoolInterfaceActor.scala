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
package akka.http.impl.engine.client

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import akka.http.impl.engine.client.PoolFlow.ResponseContext
import akka.stream.actor.ActorSubscriberMessage.OnNext
import com.github.leachbj.newrelic.akka.http.client.{AkkaPoolRequest, RequestContext}
import com.newrelic.agent.bridge.{AgentBridge, TransactionNamePriority}
import com.newrelic.api.agent.Trace
import com.newrelic.api.agent.weaver.{Weave, Weaver}
import java.util.logging.Level

@Weave(originalName = "akka.http.impl.engine.client.PoolInterfaceActor")
abstract class AkkaPoolInterfaceActor {
  def isActive: Boolean

  def sender(): ActorRef

  @Trace()
  @SuppressWarnings(Array("UnusedMethodParameter"))
  def aroundReceive(receive: Receive, msg: Any): Unit = {
    msg match {
      case request: AkkaPoolRequest if isActive =>
        AgentBridge.getAgent.getLogger.log(Level.FINER,"request {0} from {1}", request, sender())
        Option(AgentBridge.getAgent.getTransaction(false)).foreach { transaction =>
          val token = transaction.getToken
          AgentBridge.getAgent.getLogger.log(Level.FINER, s"Starting request token = {0}, txn.token {1}, request {2}", request.token, token, request)
          if (request.token != null) request.token.expire()
          request.token = token
        }
        if (AgentBridge.getAgent.getTransaction(false) == null) {
          AgentBridge.getAgent.getLogger.log(Level.FINER, s"Pool request {0} received without a transaction from {1}", request, sender())
        }
      case OnNext(ResponseContext(rc, responseTry)) => rc match {
        case arc: RequestContext => // ignore the fruitless type check it will match for the woven classes
          if (arc.token == null) {
            AgentBridge.getAgent.getLogger.log(Level.FINER, s"ResponseContext {0} received without a transaction from {1}", arc, sender())
          }
          Option(arc.token).foreach { token =>
            if (token.linkAndExpire()) {
              AgentBridge.getAgent.getLogger.log(Level.FINER, s"Linked response {0}", AgentBridge.getAgent.getTransaction)

              AgentBridge.getAgent.getTracedMethod.setMetricName("Akka", "PoolRequest", "completeResponse")
              AgentBridge.getAgent.getTransaction.setTransactionName(TransactionNamePriority.FRAMEWORK_LOW, false, "Actor", "completeResponse")
            }

            arc.token = null
          }
        case t =>
          AgentBridge.getAgent.getLogger.log(Level.FINER, s"Non-instrumented ResponseContext {0} received from {1}", t, sender())

      }

      case _ =>
    }

    Weaver.callOriginal[Unit]
  }

  @Trace
  def dispatchRequest(pr: AkkaPoolRequest): Unit = {
    if (pr.token == null) {
      AgentBridge.getAgent.getLogger.log(Level.FINER, s"dispatch for PR missing token {0}", pr)
    }
    Option(pr.token).foreach { token =>
      if (token.linkAndExpire()) {
        AgentBridge.getAgent.getLogger.log(Level.FINER, s"Linked request {0}", AgentBridge.getAgent.getTransaction)

        AgentBridge.getAgent.getTracedMethod.setMetricName("Akka", "PoolRequest", "dispatchRequest")
        AgentBridge.getAgent.getTransaction.setTransactionName(TransactionNamePriority.FRAMEWORK_LOW, false, "Actor", "dispatchRequest")
      }

      pr.token = null
    }

    Weaver.callOriginal[Unit]
  }
}
