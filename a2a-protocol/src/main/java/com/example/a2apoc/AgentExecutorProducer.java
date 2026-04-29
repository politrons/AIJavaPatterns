package com.example.a2apoc;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import com.example.a2apoc.A2ARecords.AgentRequest;
import com.example.a2apoc.A2ARecords.AgentResponse;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UnsupportedOperationError;

@ApplicationScoped
public class AgentExecutorProducer {
    private final AgentRuntime runtime;

    /**
     * Receives the domain runtime that will handle every inbound A2A message.
     */
    @Inject
    public AgentExecutorProducer(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Produces the A2A SDK executor bean.
     *
     * <p>The SDK invokes this executor when an HTTP A2A request reaches the local
     * server. The executor adapts SDK request/emitter types to the PoC runtime
     * model and sends the resulting text response back through A2A.
     */
    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {
            /**
             * Adapts one inbound A2A message into an {@link AgentRequest}, delegates
             * handling to {@link AgentRuntime}, and emits the response text plus metadata.
             */
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                Message message = context.getMessage();
                if (message == null) {
                    emitter.sendMessage("A2A request did not include a message.");
                    return;
                }

                AgentResponse response = runtime.handle(new AgentRequest(context.getUserInput(), message));
                emitter.sendMessage(List.of(new TextPart(response.text())), response.metadata());
            }

            /**
             * Reports that cancellation is not implemented by this PoC executor.
             */
            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
                throw new UnsupportedOperationError();
            }
        };
    }
}
