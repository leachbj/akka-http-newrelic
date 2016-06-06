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
package akka.http.impl.engine.client;

import java.util.logging.Level;

import akka.actor.ActorRef;
import akka.http.impl.settings.HostConnectionPoolSetup;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import com.github.leachbj.newrelic.akka.http.client.TracedActivityCompletion;
import com.github.leachbj.newrelic.akka.http.client.TracedActivityFactory;
import com.github.leachbj.newrelic.akka.http.scaladsl.OutboundHttpHeaders;
import com.newrelic.agent.bridge.AgentBridge;
import com.newrelic.agent.bridge.TracedActivity;
import com.newrelic.api.agent.weaver.NewField;
import com.newrelic.api.agent.weaver.Weave;
import com.newrelic.api.agent.weaver.Weaver;
import scala.concurrent.Future;

@Weave(originalName = "akka.http.impl.engine.client.PoolGateway")
public abstract class AkkaPoolGateway {
	private final Materializer fm = Weaver.callOriginal();

	@NewField
	private ThreadLocal<TracedActivity> localTa = ThreadLocal.withInitial(TracedActivityFactory::getTracedActivity);

	public AkkaPoolGateway(ActorRef gatewayRef, HostConnectionPoolSetup hcps, PoolGateway.GatewayIdentifier gatewayId, Materializer fm) {
		AgentBridge.getAgent().getLogger().log(Level.FINEST, "AkkaPoolGateway created");
	}

	private static TracedActivity getTracedActivity() {
		if (AgentBridge.getAgent().getTransaction(false) != null) {
			TracedActivity tracedActivity = AgentBridge.getAgent().getTransaction().createAndStartTracedActivity();
			AgentBridge.getAgent().getLogger().log(Level.FINER, "PoolGateway tracedActivity {0}", tracedActivity);
			return tracedActivity;
		}
		return null;
	}

	private HttpRequest updateRequest(HttpRequest request, TracedActivity tracedActivity) {
		if (tracedActivity != null) {
			AgentBridge.getAgent().getLogger().log(Level.FINEST, "PoolGateway for {0}", request);
			tracedActivity.setAsyncThreadName("External");
			OutboundHttpHeaders outbound = new OutboundHttpHeaders(request);
			tracedActivity.getTracedMethod().addOutboundRequestHeaders(outbound);
			AgentBridge.getAgent().getLogger().log(Level.FINEST, "PoolGateway updated request {0}", outbound.request());
			return outbound.request();
		}
		return request;
	}

	public Future<HttpResponse> apply(HttpRequest request) {
		TracedActivity tracedActivity = localTa.get();		// this uses a thread-local as the weaver kept messing up the local var and causing VerifyErrors
		request = updateRequest(request, tracedActivity);

		Future<HttpResponse> result = Weaver.callOriginal();

		result.onComplete(new TracedActivityCompletion(request.getUri().toString(), localTa.get()), fm.executionContext());
		localTa.remove();

		return result;
	}
}
