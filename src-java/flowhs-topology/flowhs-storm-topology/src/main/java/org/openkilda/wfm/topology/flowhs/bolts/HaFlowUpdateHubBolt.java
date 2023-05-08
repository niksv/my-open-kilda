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

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_METRICS_BOLT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_NB_RESPONSE_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_PING_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.HUB_TO_SPEAKER_WORKER;
import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.utils.KeyProvider;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;
import org.openkilda.wfm.topology.flowhs.service.haflow.HaFlowUpdateService;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

/**
 * This implementation of the class is temporary.
 * It only works with DB. Switch rules wouldn't be modified.
 * Class is just a stub to give an API for users. It will be modified later.
 */
public class HaFlowUpdateHubBolt extends HubBolt implements FlowGenericCarrier {
    public static final String HA_FLOW_UPDATE_ERROR = "Couldn't update HA-flow";
    private final HaFlowUpdateConfig config;
    private final PathComputerConfig pathComputerConfig;
    private final FlowResourcesConfig flowResourcesConfig;
    private final RuleManagerConfig ruleManagerConfig;

    private transient HaFlowUpdateService service;
    private String currentKey;

    private LifecycleEvent deferredShutdownEvent;

    public HaFlowUpdateHubBolt(
            @NonNull HaFlowUpdateHubBolt.HaFlowUpdateConfig config, @NonNull PersistenceManager persistenceManager,
            @NonNull PathComputerConfig pathComputerConfig, @NonNull FlowResourcesConfig flowResourcesConfig,
            @NonNull RuleManagerConfig ruleManagerConfig) {
        super(persistenceManager, config);
        this.config = config;
        this.pathComputerConfig = pathComputerConfig;
        this.flowResourcesConfig = flowResourcesConfig;
        this.ruleManagerConfig = ruleManagerConfig;
        enableMeterRegistry("kilda.ha_flow_update", HUB_TO_METRICS_BOLT.name());
    }

    @Override
    protected void init() {
        FlowResourcesManager resourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
        AvailableNetworkFactory availableNetworkFactory =
                new AvailableNetworkFactory(pathComputerConfig, persistenceManager.getRepositoryFactory());
        PathComputer pathComputer =
                new PathComputerFactory(pathComputerConfig, availableNetworkFactory).getPathComputer();
        RuleManager ruleManager = new RuleManagerImpl(ruleManagerConfig);
        service = new HaFlowUpdateService(this, persistenceManager, pathComputer, resourcesManager,
                ruleManager, config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                config.getResourceAllocationRetriesLimit(), config.getSpeakerCommandRetriesLimit());
    }

    @Override
    protected boolean deactivate(LifecycleEvent event) {
        if (service.deactivate()) {
            return true;
        }
        deferredShutdownEvent = event;
        return false;
    }

    @Override
    protected void activate() {
        service.activate();
    }

    @Override
    protected void onRequest(Tuple input) throws PipelineException {
        currentKey = pullKey(input);
        CommandData payload = pullValue(input, FIELD_ID_PAYLOAD, CommandData.class);
        try {
            if (payload instanceof HaFlowRequest) {
                service.handleUpdateRequest(currentKey, getCommandContext(), (HaFlowRequest) payload);
            } else if (payload instanceof HaFlowPartialUpdateRequest) {
                service.handlePartialUpdateRequest(
                        currentKey, getCommandContext(), (HaFlowPartialUpdateRequest) payload);
            } else {
                unhandledInput(input);
            }
        } catch (DuplicateKeyException e) {
            log.error("Failed to handle a request with key {}. {}", currentKey, e.getMessage());
        } catch (FlowProcessingException e) {
            sendErrorResponse(e, e.getErrorType());
        } catch (Exception e) {
            sendErrorResponse(e, ErrorType.INTERNAL_ERROR);
        }
    }

    @Override
    protected void onWorkerResponse(Tuple input) throws PipelineException {
        String operationKey = pullKey(input);
        currentKey = KeyProvider.getParentKey(operationKey);
        SpeakerResponse speakerResponse = pullValue(input, FIELD_ID_PAYLOAD, SpeakerResponse.class);
        if (speakerResponse instanceof SpeakerCommandResponse) {
            service.handleAsyncResponse(currentKey, (SpeakerCommandResponse) speakerResponse);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void onTimeout(String key, Tuple tuple) {
        currentKey = key;
        service.handleTimeout(key);
    }

    @Override
    public void sendSpeakerRequest(@NonNull SpeakerRequest command) {
        String commandKey = KeyProvider.joinKeys(command.getCommandId().toString(), currentKey);
        Values values = new Values(commandKey, command);
        emitWithContext(HUB_TO_SPEAKER_WORKER.name(), getCurrentTuple(), values);
    }

    @Override
    public void sendNorthboundResponse(@NonNull Message message) {
        emitWithContext(Stream.HUB_TO_NB_RESPONSE_SENDER.name(), getCurrentTuple(), new Values(currentKey, message));
    }

    @Override
    public void sendHistoryUpdate(@NonNull FlowHistoryHolder historyHolder) {
        //TODO check history https://github.com/telstra/open-kilda/issues/5169
        InfoMessage message = new InfoMessage(historyHolder, getCommandContext().getCreateTime(),
                getCommandContext().getCorrelationId());
        emitWithContext(Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), getCurrentTuple(),
                new Values(historyHolder.getTaskId(), message));
    }

    @Override
    public void sendNotifyFlowStats(@NonNull UpdateFlowPathInfo flowPathInfo) {
        //TODO implement https://github.com/telstra/open-kilda/issues/5182
    }

    public void sendNotifyFlowStats(RemoveFlowPathInfo flowPathInfo) {
        //TODO implement https://github.com/telstra/open-kilda/issues/5182
    }

    @Override
    public void sendNotifyFlowMonitor(@NonNull CommandData flowCommand) {
        //TODO implement https://github.com/telstra/open-kilda/issues/5172
    }

    @Override
    public void sendPeriodicPingNotification(String haFlowId, boolean enabled) {
        //TODO implement periodic pings https://github.com/telstra/open-kilda/issues/5153
        log.info("Periodic pings are not implemented for ha-flow update operation yet. Skipping for the ha-flow {}",
                haFlowId);
    }

    @Override
    public void cancelTimeoutCallback(String key) {
        cancelCallback(key);
    }

    @Override
    public void sendInactive() {
        getOutput().emit(ZkStreams.ZK.toString(), new Values(deferredShutdownEvent, getCommandContext()));
        deferredShutdownEvent = null;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);
        declarer.declareStream(HUB_TO_SPEAKER_WORKER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_NB_RESPONSE_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_PING_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name(), MessageKafkaTranslator.STREAM_FIELDS);
        declarer.declareStream(ZkStreams.ZK.toString(),
                new Fields(ZooKeeperBolt.FIELD_ID_STATE, ZooKeeperBolt.FIELD_ID_CONTEXT));
    }

    private void sendErrorResponse(Exception exception, ErrorType errorType) {
        ErrorData errorData = new ErrorData(errorType, HA_FLOW_UPDATE_ERROR, exception.getMessage());
        CommandContext commandContext = getCommandContext();
        ErrorMessage errorMessage = new ErrorMessage(errorData, commandContext.getCreateTime(),
                commandContext.getCorrelationId());
        sendNorthboundResponse(errorMessage);
    }

    @Getter
    public static class HaFlowUpdateConfig extends Config {
        private final int pathAllocationRetriesLimit;
        private final int pathAllocationRetryDelay;
        private final int resourceAllocationRetriesLimit;
        private final int speakerCommandRetriesLimit;

        @Builder(builderMethodName = "haFlowUpdateBuilder", builderClassName = "haFlowUpdateBuild")
        public HaFlowUpdateConfig(
                String requestSenderComponent, String workerComponent, String lifeCycleEventComponent, int timeoutMs,
                boolean autoAck, int pathAllocationRetriesLimit, int pathAllocationRetryDelay,
                int resourceAllocationRetriesLimit, int speakerCommandRetriesLimit) {
            super(requestSenderComponent, workerComponent, lifeCycleEventComponent, timeoutMs, autoAck);
            this.pathAllocationRetriesLimit = pathAllocationRetriesLimit;
            this.pathAllocationRetryDelay = pathAllocationRetryDelay;
            this.resourceAllocationRetriesLimit = resourceAllocationRetriesLimit;
            this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        }
    }
}
