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

package org.openkilda.wfm.topology.flowhs.bolts;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_CREATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_CREATE_MIRROR_POINT_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_DELETE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_DELETE_MIRROR_POINT_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_PATH_SWAP_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_REROUTE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_SWAP_ENDPOINTS_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_SYNC_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_UPDATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_VALIDATION_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_HA_FLOW_CREATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_HA_FLOW_DELETE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_HA_FLOW_PATH_SWAP_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_HA_FLOW_READ;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_HA_FLOW_REROUTE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_HA_FLOW_UPDATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_HA_FLOW_VALIDATION_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_CREATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_DELETE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_PATH_SWAP_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_READ;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_REROUTE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_SYNC_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_UPDATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_VALIDATION_HUB;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_KEY;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.MessageData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.CreateFlowLoopRequest;
import org.openkilda.messaging.command.flow.DeleteFlowLoopRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowMirrorPointCreateRequest;
import org.openkilda.messaging.command.flow.FlowMirrorPointDeleteRequest;
import org.openkilda.messaging.command.flow.FlowPathSwapRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.messaging.command.flow.FlowSyncRequest;
import org.openkilda.messaging.command.flow.FlowValidationRequest;
import org.openkilda.messaging.command.flow.SwapFlowEndpointRequest;
import org.openkilda.messaging.command.haflow.HaFlowDeleteRequest;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest;
import org.openkilda.messaging.command.haflow.HaFlowPathSwapRequest;
import org.openkilda.messaging.command.haflow.HaFlowPathsReadRequest;
import org.openkilda.messaging.command.haflow.HaFlowReadRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaFlowRerouteRequest;
import org.openkilda.messaging.command.haflow.HaFlowValidationRequest;
import org.openkilda.messaging.command.haflow.HaFlowsDumpRequest;
import org.openkilda.messaging.command.yflow.SubFlowsReadRequest;
import org.openkilda.messaging.command.yflow.YFlowDeleteRequest;
import org.openkilda.messaging.command.yflow.YFlowPartialUpdateRequest;
import org.openkilda.messaging.command.yflow.YFlowPathSwapRequest;
import org.openkilda.messaging.command.yflow.YFlowPathsReadRequest;
import org.openkilda.messaging.command.yflow.YFlowReadRequest;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteRequest;
import org.openkilda.messaging.command.yflow.YFlowSyncRequest;
import org.openkilda.messaging.command.yflow.YFlowValidationRequest;
import org.openkilda.messaging.command.yflow.YFlowsDumpRequest;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class RouterBolt extends AbstractBolt {

    public static final String FLOW_ID_FIELD = "flow-id";

    private static final Fields STREAM_FIELDS =
            new Fields(FIELD_ID_KEY, FLOW_ID_FIELD, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    public RouterBolt(String lifeCycleEventSourceComponent) {
        super(lifeCycleEventSourceComponent);
    }

    @Override
    protected void handleInput(Tuple input) {
        if (active) {
            String key = input.getStringByField(FIELD_ID_KEY);
            if (StringUtils.isBlank(key)) {
                //TODO: the key must be unique, but the correlationId comes in from outside and we can't guarantee that.
                //IMPORTANT: Storm may initiate reprocessing of the same tuple (e.g. in the case of timeout) and
                // cause creating multiple FSMs for the same tuple. This must be avoided.
                // As for now tuples are routed by the key field, and services can check FSM uniqueness.
                key = getCommandContext().getCorrelationId();
            }

            CommandMessage message = (CommandMessage) input.getValueByField(FIELD_ID_PAYLOAD);
            MessageData data = message.getData();

            if (data instanceof FlowRequest) {
                FlowRequest request = (FlowRequest) data;
                log.debug("Received request {} with the key {}", request, key);
                Values values = new Values(key, request.getFlowId(), request);
                switch (request.getType()) {
                    case CREATE:
                        emitWithContext(ROUTER_TO_FLOW_CREATE_HUB.name(), input, values);
                        break;
                    case UPDATE:
                        emitWithContext(ROUTER_TO_FLOW_UPDATE_HUB.name(), input, values);
                        break;
                    default:
                        throw new UnsupportedOperationException(format("Flow operation %s is not supported",
                                request.getType()));
                }
            } else if (data instanceof FlowRerouteRequest) {
                FlowRerouteRequest rerouteRequest = (FlowRerouteRequest) data;
                log.debug("Received a reroute request {}/{} with the key {}. MessageId {}", rerouteRequest.getFlowId(),
                        rerouteRequest.getAffectedIsls(), key, input.getMessageId());
                Values values = new Values(key, rerouteRequest.getFlowId(), data);
                emitWithContext(ROUTER_TO_FLOW_REROUTE_HUB.name(), input, values);
            } else if (data instanceof FlowDeleteRequest) {
                FlowDeleteRequest deleteRequest = (FlowDeleteRequest) data;
                log.debug("Received a delete request {} with the key {}. MessageId {}", deleteRequest.getFlowId(),
                        key, input.getMessageId());
                Values values = new Values(key, deleteRequest.getFlowId(), data);
                emitWithContext(ROUTER_TO_FLOW_DELETE_HUB.name(), input, values);
            } else if (data instanceof FlowSyncRequest) {
                routeFlowSyncRequest(input, (FlowSyncRequest) data, key);
            } else if (data instanceof FlowPathSwapRequest) {
                FlowPathSwapRequest pathSwapRequest = (FlowPathSwapRequest) data;
                log.debug("Received a path swap request {} with the key {}. MessageId {}", pathSwapRequest.getFlowId(),
                        key, input.getMessageId());
                Values values = new Values(key, pathSwapRequest.getFlowId(), data);
                emitWithContext(ROUTER_TO_FLOW_PATH_SWAP_HUB.name(), input, values);
            } else if (data instanceof SwapFlowEndpointRequest) {
                log.debug("Received a swap flow endpoints request with the key {}. MessageId {}", key,
                        input.getMessageId());
                emitWithContext(ROUTER_TO_FLOW_SWAP_ENDPOINTS_HUB.name(), input, new Values(key, data));
            } else if (data instanceof CreateFlowLoopRequest) {
                log.debug("Received a create flow loop request with the key {}. MessageId {}",
                        key, input.getMessageId());
                CreateFlowLoopRequest request = (CreateFlowLoopRequest) data;
                emitWithContext(ROUTER_TO_FLOW_UPDATE_HUB.name(), input, new Values(key, request.getFlowId(), data));
            } else if (data instanceof DeleteFlowLoopRequest) {
                log.debug("Received a delete flow loop request with the key {}. MessageId {}",
                        key, input.getMessageId());
                DeleteFlowLoopRequest request = (DeleteFlowLoopRequest) data;
                emitWithContext(ROUTER_TO_FLOW_UPDATE_HUB.name(), input, new Values(key, request.getFlowId(), data));
            } else if (data instanceof FlowMirrorPointCreateRequest) {
                log.debug("Received a flow mirror point create request with the key {}. MessageId {}",
                        key, input.getMessageId());
                FlowMirrorPointCreateRequest request = (FlowMirrorPointCreateRequest) data;
                emitWithContext(ROUTER_TO_FLOW_CREATE_MIRROR_POINT_HUB.name(),
                        input, new Values(key, request.getFlowId(), data));
            } else if (data instanceof FlowMirrorPointDeleteRequest) {
                log.debug("Received a flow mirror point delete request with the key {}. MessageId {}",
                        key, input.getMessageId());
                FlowMirrorPointDeleteRequest request = (FlowMirrorPointDeleteRequest) data;
                emitWithContext(ROUTER_TO_FLOW_DELETE_MIRROR_POINT_HUB.name(),
                        input, new Values(key, request.getFlowId(), data));
            } else if (data instanceof FlowValidationRequest) {
                log.debug("Received a flow validation request with the key {}. MessageId {}",
                        key, input.getMessageId());
                FlowValidationRequest request = (FlowValidationRequest) data;
                emitWithContext(ROUTER_TO_FLOW_VALIDATION_HUB.name(), input,
                        new Values(key, request.getFlowId(), data));
            } else if (data instanceof YFlowRequest) {
                YFlowRequest request = (YFlowRequest) data;
                log.debug("Received request {} with the the key {}", request, key);
                Values values = new Values(key, request.getYFlowId(), request);
                switch (request.getType()) {
                    case CREATE:
                        emitWithContext(ROUTER_TO_YFLOW_CREATE_HUB.name(), input, values);
                        break;
                    case UPDATE:
                        emitWithContext(ROUTER_TO_YFLOW_UPDATE_HUB.name(), input, values);
                        break;
                    default:
                        throw new UnsupportedOperationException(format("Y-flow operation %s is not supported",
                                request.getType()));
                }
            } else if (data instanceof YFlowPartialUpdateRequest) {
                YFlowPartialUpdateRequest request = (YFlowPartialUpdateRequest) data;
                log.debug("Received a y-flow partial update request {} with the key {}", request, key);
                emitWithContext(ROUTER_TO_YFLOW_UPDATE_HUB.name(), input, new Values(key, request.getYFlowId(), data));
            } else if (data instanceof YFlowRerouteRequest) {
                YFlowRerouteRequest request = (YFlowRerouteRequest) data;
                log.debug("Received a y-flow reroute request {} with the key {}", data, key);
                emitWithContext(ROUTER_TO_YFLOW_REROUTE_HUB.name(), input, new Values(key, request.getYFlowId(), data));
            } else if (data instanceof YFlowDeleteRequest) {
                YFlowDeleteRequest request = (YFlowDeleteRequest) data;
                log.debug("Received a y-flow delete request {} with the key {}", request, key);
                emitWithContext(ROUTER_TO_YFLOW_DELETE_HUB.name(), input, new Values(key, request.getYFlowId(), data));
            } else if (data instanceof YFlowsDumpRequest) {
                log.debug("Received a y-flow dump request {} with the key {}", data, key);
                emitWithContext(ROUTER_TO_YFLOW_READ.name(), input, new Values(key, data));
            } else if (data instanceof YFlowReadRequest) {
                log.debug("Received a y-flow read request {} with the key {}", data, key);
                emitWithContext(ROUTER_TO_YFLOW_READ.name(), input, new Values(key, data));
            } else if (data instanceof YFlowPathsReadRequest) {
                log.debug("Received a y-flow read path request {} with the key {}", data, key);
                emitWithContext(ROUTER_TO_YFLOW_READ.name(), input, new Values(key, data));
            } else if (data instanceof SubFlowsReadRequest) {
                log.debug("Received a y-flow sub-flows request {} with the key {}", data, key);
                emitWithContext(ROUTER_TO_YFLOW_READ.name(), input, new Values(key, data));
            } else if (data instanceof YFlowValidationRequest) {
                YFlowValidationRequest request = (YFlowValidationRequest) data;
                log.debug("Received a y-flow validation request {} with the key {}", request, key);
                emitWithContext(ROUTER_TO_YFLOW_VALIDATION_HUB.name(), input,
                        new Values(key, request.getYFlowId(), data));
            } else if (data instanceof YFlowSyncRequest) {
                routeYflowSyncRequest(input, (YFlowSyncRequest) data, key);
            } else if (data instanceof YFlowPathSwapRequest) {
                YFlowPathSwapRequest request = (YFlowPathSwapRequest) data;
                log.debug("Received a y-flow path swap request {} with the key {}", request, key);
                emitWithContext(ROUTER_TO_YFLOW_PATH_SWAP_HUB.name(), input, new Values(key, request.getYFlowId(),
                        data));
            } else if (data instanceof HaFlowRequest) {
                HaFlowRequest request = (HaFlowRequest) data;
                log.debug("Received an ha-flow request {} with key {}", request, key);
                Values values = new Values(key, request.getHaFlowId(), request);
                switch (request.getType()) {
                    case CREATE:
                        emitWithContext(ROUTER_TO_HA_FLOW_CREATE_HUB.name(), input, values);
                        break;
                    case UPDATE:
                        emitWithContext(ROUTER_TO_HA_FLOW_UPDATE_HUB.name(), input, values);
                        break;
                    default:
                        throw new UnsupportedOperationException(format("HA-flow operation %s is not supported",
                                request.getType()));
                }
            } else if (data instanceof HaFlowPartialUpdateRequest) {
                HaFlowPartialUpdateRequest request = (HaFlowPartialUpdateRequest) data;
                log.debug("Received an ha-flow partial update request {} with the key {}", request, key);
                emitWithContext(ROUTER_TO_HA_FLOW_UPDATE_HUB.name(), input,
                        new Values(key, request.getHaFlowId(), data));
            } else if (data instanceof HaFlowRerouteRequest) {
                HaFlowRerouteRequest rerouteRequest = (HaFlowRerouteRequest) data;
                log.debug("Received an HA-flow reroute request {}/{} with the key {}. MessageId {}",
                        rerouteRequest.getHaFlowId(), rerouteRequest.getAffectedIsls(), key, input.getMessageId());
                Values values = new Values(key, rerouteRequest.getHaFlowId(), data);
                emitWithContext(ROUTER_TO_HA_FLOW_REROUTE_HUB.name(), input, values);
            } else if (data instanceof HaFlowDeleteRequest) {
                HaFlowDeleteRequest request = (HaFlowDeleteRequest) data;
                log.debug("Received an ha-flow delete request {} with the key {}", request, key);
                emitWithContext(ROUTER_TO_HA_FLOW_DELETE_HUB.name(), input,
                        new Values(key, request.getHaFlowId(), data));
            } else if (data instanceof HaFlowsDumpRequest) {
                log.debug("Received an ha-flow dump request {} with the key {}", data, key);
                emitWithContext(ROUTER_TO_HA_FLOW_READ.name(), input, new Values(key, data));
            } else if (data instanceof HaFlowReadRequest) {
                log.debug("Received an ha-flow read request {} with the key {}", data, key);
                emitWithContext(ROUTER_TO_HA_FLOW_READ.name(), input, new Values(key, data));
            } else if (data instanceof HaFlowPathsReadRequest) {
                log.debug("Received an ha-flow paths read request {} with the key {}", data, key);
                emitWithContext(ROUTER_TO_HA_FLOW_READ.name(), input, new Values(key, data));
            } else if (data instanceof HaFlowValidationRequest) {
                HaFlowValidationRequest request = (HaFlowValidationRequest) data;
                log.debug("Received an ha-flow validation request {} with the key {}", request, key);
                emitWithContext(ROUTER_TO_HA_FLOW_VALIDATION_HUB.name(), input,
                        new Values(key, request.getHaFlowId(), data));
            } else if (data instanceof HaFlowPathSwapRequest) {
                HaFlowPathSwapRequest request = (HaFlowPathSwapRequest) data;
                log.debug("Received a ha-flow paths swap request {} with key {}", request.getHaFlowId(), key);
                emitWithContext(ROUTER_TO_HA_FLOW_PATH_SWAP_HUB.name(), input,
                        new Values(key, request.getHaFlowId(), data));
            } else {
                unhandledInput(input);
            }
        }
    }

    private void routeFlowSyncRequest(Tuple input, FlowSyncRequest request, String key) {
        log.debug("Received a flow sync request {} with key {} (MessageId={})",
                request.getFlowId(), key, input.getMessageId());
        Values values = new Values(key, request.getFlowId(), request, getCommandContext());
        emit(ROUTER_TO_FLOW_SYNC_HUB.name(), input, values);
    }

    private void routeYflowSyncRequest(Tuple input, YFlowSyncRequest request, String key) {
        log.debug("Received an y-flow sync request {} with key {} (MessageId={})",
                request.getYFlowId(), key, input.getMessageId());
        Values values = new Values(key, request.getYFlowId(), request, getCommandContext());
        emit(ROUTER_TO_YFLOW_SYNC_HUB.name(), input, values);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(ROUTER_TO_FLOW_CREATE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_UPDATE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_REROUTE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_DELETE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_PATH_SWAP_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_CREATE_MIRROR_POINT_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_DELETE_MIRROR_POINT_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_SYNC_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_FLOW_SWAP_ENDPOINTS_HUB.name(),
                new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
        declarer.declareStream(ROUTER_TO_FLOW_VALIDATION_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_YFLOW_CREATE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_YFLOW_UPDATE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_YFLOW_REROUTE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_YFLOW_DELETE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_YFLOW_SYNC_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_YFLOW_READ.name(),
                new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
        declarer.declareStream(ROUTER_TO_YFLOW_VALIDATION_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_YFLOW_PATH_SWAP_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_HA_FLOW_CREATE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_HA_FLOW_UPDATE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_HA_FLOW_REROUTE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_HA_FLOW_DELETE_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_HA_FLOW_VALIDATION_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_HA_FLOW_PATH_SWAP_HUB.name(), STREAM_FIELDS);
        declarer.declareStream(ROUTER_TO_HA_FLOW_READ.name(),
                new Fields(FIELD_ID_KEY, FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT));
        declarer.declareStream(ZkStreams.ZK.toString(),
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
