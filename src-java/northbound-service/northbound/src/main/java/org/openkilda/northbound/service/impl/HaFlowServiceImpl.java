/* Copyright 2023 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.northbound.service.impl;

import static java.util.Collections.emptySet;

import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.HaFlowPingRequest;
import org.openkilda.messaging.command.haflow.HaFlowDeleteRequest;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest;
import org.openkilda.messaging.command.haflow.HaFlowPathSwapRequest;
import org.openkilda.messaging.command.haflow.HaFlowPathsReadRequest;
import org.openkilda.messaging.command.haflow.HaFlowPathsResponse;
import org.openkilda.messaging.command.haflow.HaFlowReadRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteResponse;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.command.haflow.HaFlowValidationRequest;
import org.openkilda.messaging.command.haflow.HaFlowValidationResponse;
import org.openkilda.messaging.command.haflow.HaFlowsDumpRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.flow.HaFlowPingResponse;
import org.openkilda.northbound.converter.HaFlowMapper;
import org.openkilda.northbound.dto.v2.haflows.HaFlow;
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowDump;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPingResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSyncResult;
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowValidationResult;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.HaFlowService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Handles the HA-flow operations.
 */
@Slf4j
@Service
public class HaFlowServiceImpl implements HaFlowService {
    @Value("#{kafkaTopicsConfig.getFlowHsTopic()}")
    private String flowHsTopic;
    @Value("#{kafkaTopicsConfig.getTopoRerouteTopic()}")
    private String rerouteTopic;

    @Value("#{kafkaTopicsConfig.getPingTopic()}")
    private String pingTopic;

    private final MessagingChannel messagingChannel;
    private final HaFlowMapper flowMapper;

    @Autowired
    public HaFlowServiceImpl(MessagingChannel messagingChannel, HaFlowMapper flowMapper) {
        this.messagingChannel = messagingChannel;
        this.flowMapper = flowMapper;
    }

    @Override
    public CompletableFuture<HaFlow> createHaFlow(HaFlowCreatePayload createPayload) {
        log.info("API request: create ha-flow: {}", createPayload);
        String correlationId = RequestCorrelationId.getId();

        HaFlowRequest flowRequest;
        try {
            flowRequest = flowMapper.toHaFlowCreateRequest(createPayload);
        } catch (IllegalArgumentException e) {
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the create ha-flow request");
        }

        CommandMessage command = new CommandMessage(flowRequest, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(HaFlowResponse.class::cast)
                .thenApply(HaFlowResponse::getHaFlow)
                .thenApply(flowMapper::toHaFlow);
    }

    @Override
    public CompletableFuture<HaFlowDump> dumpHaFlows() {
        log.info("API request: Dump all ha-flows");
        HaFlowsDumpRequest dumpRequest = new HaFlowsDumpRequest();
        CommandMessage request = new CommandMessage(dumpRequest, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGetChunked(flowHsTopic, request)
                .thenApply(result -> result.stream()
                        .map(HaFlowResponse.class::cast)
                        .map(HaFlowResponse::getHaFlow)
                        .map(flowMapper::toHaFlow)
                        .collect(Collectors.toList()))
                .thenApply(HaFlowDump::new);
    }

    @Override
    public CompletableFuture<HaFlow> getHaFlow(String haFlowId) {
        log.info("API request: Get ha-flow: {}", haFlowId);
        HaFlowReadRequest readRequest = new HaFlowReadRequest(haFlowId);
        CommandMessage request = new CommandMessage(readRequest, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(HaFlowResponse.class::cast)
                .thenApply(HaFlowResponse::getHaFlow)
                .thenApply(flowMapper::toHaFlow);
    }

    @Override
    public CompletableFuture<HaFlowPaths> getHaFlowPaths(String haFlowId) {
        log.info("API request: Get ha-flow paths: {}", haFlowId);
        HaFlowPathsReadRequest readPathsRequest = new HaFlowPathsReadRequest(haFlowId);
        CommandMessage request = new CommandMessage(readPathsRequest, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(HaFlowPathsResponse.class::cast)
                .thenApply(flowMapper::toHaFlowPaths);
    }

    @Override
    public CompletableFuture<HaFlow> updateHaFlow(String haFlowId, HaFlowUpdatePayload updatePayload) {
        log.info("API request: Update ha-flow {}. New properties {}", haFlowId, updatePayload);
        String correlationId = RequestCorrelationId.getId();

        HaFlowRequest flowRequest;
        try {
            flowRequest = flowMapper.toHaFlowUpdateRequest(haFlowId, updatePayload);
        } catch (IllegalArgumentException e) {
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the update ha-flow request");
        }

        CommandMessage command = new CommandMessage(flowRequest, System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(HaFlowResponse.class::cast)
                .thenApply(HaFlowResponse::getHaFlow)
                .thenApply(flowMapper::toHaFlow);
    }

    @Override
    public CompletableFuture<HaFlow> patchHaFlow(String haFlowId, HaFlowPatchPayload patchPayload) {
        log.info("API request: Patch ha-flow {}. New properties {}", haFlowId, patchPayload);
        String correlationId = RequestCorrelationId.getId();

        HaFlowPartialUpdateRequest partialUpdateRequest;
        try {
            partialUpdateRequest = flowMapper.toHaFlowPatchRequest(haFlowId, patchPayload);
        } catch (IllegalArgumentException e) {
            throw new MessageException(correlationId, System.currentTimeMillis(), ErrorType.DATA_INVALID,
                    e.getMessage(), "Can not parse arguments of the ha-flow patch request");
        }

        CommandMessage request = new CommandMessage(partialUpdateRequest,
                System.currentTimeMillis(), correlationId);
        return messagingChannel.sendAndGet(flowHsTopic, request)
                .thenApply(HaFlowResponse.class::cast)
                .thenApply(HaFlowResponse::getHaFlow)
                .thenApply(flowMapper::toHaFlow);
    }

    @Override
    public CompletableFuture<HaFlow> deleteHaFlow(String haFlowId) {
        log.info("API request: Delete ha-flow: {}", haFlowId);
        CommandMessage command = new CommandMessage(new HaFlowDeleteRequest(haFlowId), System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(HaFlowResponse.class::cast)
                .thenApply(HaFlowResponse::getHaFlow)
                .thenApply(flowMapper::toHaFlow);
    }

    @Override
    public CompletableFuture<HaFlowRerouteResult> rerouteHaFlow(String haFlowId) {
        log.info("API request: Reroute HA-flow: {}", haFlowId);
        HaFlowRerouteRequest request = new HaFlowRerouteRequest(
                haFlowId, emptySet(), false, "initiated via Northbound", false, true);
        CommandMessage command = new CommandMessage(request, System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(rerouteTopic, command)
                .thenApply(HaFlowRerouteResponse.class::cast)
                .thenApply(flowMapper::toRerouteResult);
    }

    /**
     * Validates a high-availability (HA) flow with the given ID asynchronously.
     *
     * @param haFlowId the ID of the HA flow to be validated
     * @return a {@link CompletableFuture} that will be completed with the validation result
     */
    @Override
    public CompletableFuture<HaFlowValidationResult> validateHaFlow(String haFlowId) {
        log.info("API request: Validate the ha-flow: {}", haFlowId);
        CommandMessage command = new CommandMessage(new HaFlowValidationRequest(haFlowId), System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(HaFlowValidationResponse.class::cast)
                .thenApply(flowMapper::toValidationResult);
    }

    @Override
    public CompletableFuture<HaFlowSyncResult> synchronizeHaFlow(String haFlowId) {
        return null;
    }

    @Override
    public CompletableFuture<HaFlowPingResult> pingHaFlow(String haFlowId, HaFlowPingPayload payload) {
        log.info("API request: Ping ha-flow {}, payload {}", haFlowId, payload);
        CommandMessage command = new CommandMessage(new HaFlowPingRequest(haFlowId, payload.getTimeoutMillis()),
                System.currentTimeMillis(), RequestCorrelationId.getId());

        return messagingChannel.sendAndGet(pingTopic, command)
                .thenApply(HaFlowPingResponse.class::cast)
                .thenApply(flowMapper::toPingResult);
    }

    @Override
    public CompletableFuture<HaFlow> swapHaFlowPaths(String haFlowId) {
        log.info("API request: Swap paths of HA-flow: {}", haFlowId);
        CommandMessage command = new CommandMessage(new HaFlowPathSwapRequest(haFlowId), System.currentTimeMillis(),
                RequestCorrelationId.getId());
        return messagingChannel.sendAndGet(flowHsTopic, command)
                .thenApply(HaFlowResponse.class::cast)
                .thenApply(HaFlowResponse::getHaFlow)
                .thenApply(flowMapper::toHaFlow);

    }
}
