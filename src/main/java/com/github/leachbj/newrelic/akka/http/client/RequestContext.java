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
package com.github.leachbj.newrelic.akka.http.client;

import java.util.logging.Level;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import com.newrelic.agent.bridge.AgentBridge;
import com.newrelic.agent.bridge.Token;
import com.newrelic.api.agent.weaver.NewField;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import scala.concurrent.Promise;

@Weave(originalName = "akka.http.impl.engine.client.PoolFlow$RequestContext")
public abstract class RequestContext {
  @NewField
  public Token token;

  @SuppressWarnings("UnusedMethodParameter")
  public RequestContext(HttpRequest request, Promise<HttpResponse> promise, int retriesLeft) {
    token = AgentBridge.getAgent().getTransaction() != null ? AgentBridge.getAgent().getTransaction().getToken() : null;
  }

  public RequestContext copy(HttpRequest request, Promise<HttpResponse> promise, int retriesLeft) {
    RequestContext newRc = Weaver.callOriginal();
    newRc.token = token;
    token = null;
    AgentBridge.getAgent().getLogger().log(Level.FINER, "Copied token {0} to new instance", newRc.token);
    return newRc;
  }
}
