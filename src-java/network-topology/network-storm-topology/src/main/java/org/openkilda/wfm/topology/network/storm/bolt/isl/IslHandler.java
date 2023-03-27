/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.network.storm.bolt.isl;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.reroute.RerouteFlows;
import org.openkilda.messaging.info.event.IslChangedInfoData;
import org.openkilda.messaging.info.event.IslStatusUpdateNotification;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.BfdProperties;
import org.openkilda.model.Isl;
import org.openkilda.model.IslDownReason;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.share.bolt.KafkaEncoder;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.share.model.IslReference;
import org.openkilda.wfm.topology.network.error.ControllerNotFoundException;
import org.openkilda.wfm.topology.network.model.BfdStatusUpdate;
import org.openkilda.wfm.topology.network.model.IslDataHolder;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.model.RoundTripStatus;
import org.openkilda.wfm.topology.network.service.IIslCarrier;
import org.openkilda.wfm.topology.network.service.IslRulesService;
import org.openkilda.wfm.topology.network.service.NetworkIslService;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.bfd.hub.command.BfdHubCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfd.hub.command.BfdHubDisableCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfd.hub.command.BfdHubEnableCommand;
import org.openkilda.wfm.topology.network.storm.bolt.bfd.hub.command.BfdHubIslRemoveNotificationCommand;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslCommand;
import org.openkilda.wfm.topology.network.storm.bolt.isl.command.IslCommandBase;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRouter;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRulesRouter;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRulesWorker;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.command.ProcessSpeakerRulesCommand;
import org.openkilda.wfm.topology.network.storm.bolt.uniisl.UniIslHandler;
import org.openkilda.wfm.topology.network.storm.bolt.uniisl.command.UniIslCommand;
import org.openkilda.wfm.topology.network.storm.bolt.uniisl.command.UniIslNotifyIslRemovedCommand;
import org.openkilda.wfm.topology.network.storm.bolt.watchlist.command.WatchListAuxiliaryPollModeUpdateCommand;
import org.openkilda.wfm.topology.network.storm.bolt.watchlist.command.WatchListCommand;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.List;
import java.util.UUID;

public class IslHandler extends AbstractBolt implements IIslCarrier {
    public static final String BOLT_ID = ComponentId.ISL_HANDLER.toString();

    public static final String FIELD_ID_DATAPATH = UniIslHandler.FIELD_ID_DATAPATH;
    public static final String FIELD_ID_PORT_NUMBER = UniIslHandler.FIELD_ID_PORT_NUMBER;
    public static final String FIELD_ID_COMMAND = UniIslHandler.FIELD_ID_COMMAND;

    public static final String STREAM_BFD_HUB_ID = "bfd-port";
    public static final Fields STREAM_BFD_HUB_FIELDS = new Fields(FIELD_ID_DATAPATH,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_REROUTE_ID = "reroute";
    public static final Fields STREAM_REROUTE_FIELDS = new Fields(
            KafkaEncoder.FIELD_ID_KEY, KafkaEncoder.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    public static final String STREAM_STATUS_ID = "status";
    public static final Fields STREAM_STATUS_FIELDS = new Fields(
            KafkaEncoder.FIELD_ID_KEY, KafkaEncoder.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    public static final String STREAM_SPEAKER_RULES_ID = "speaker.rules";
    public static final Fields STREAM_SPEAKER_RULES_FIELDS = new Fields(
            KafkaEncoder.FIELD_ID_KEY, FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_POLL_ID = "poll";
    public static final Fields STREAM_POLL_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_UNIISL_ID = "uni-isl";
    public static final Fields STREAM_UNIISL_FIELDS = new Fields(FIELD_ID_DATAPATH, FIELD_ID_PORT_NUMBER,
            FIELD_ID_COMMAND, FIELD_ID_CONTEXT);

    public static final String STREAM_FLOW_MONITORING_ID = "flow-monitoring";
    public static final Fields STREAM_FLOW_MONITORING_FIELDS = new Fields(
            KafkaEncoder.FIELD_ID_KEY, KafkaEncoder.FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    private final NetworkOptions options;
    private final RuleManagerConfig ruleManagerConfig;

    private transient NetworkIslService islService;
    private transient IslRulesService islRulesService;

    public IslHandler(PersistenceManager persistenceManager, NetworkOptions options,
                      RuleManagerConfig ruleManagerConfig) {
        super(persistenceManager);
        this.options = options;
        this.ruleManagerConfig = ruleManagerConfig;
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String source = input.getSourceComponent();
        if (UniIslHandler.BOLT_ID.equals(source)) {
            handleUniIslCommand(input);
        } else if (SpeakerRouter.BOLT_ID.equals(source)) {
            handleSpeakerInput(input);
        } else if (SpeakerRulesWorker.BOLT_ID.equals(source)) {
            handleSpeakerRulesWorkerInput(input);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    protected void handleException(Exception error) throws Exception {
        try {
            super.handleException(error);
        } catch (ControllerNotFoundException e) {
            log.error(e.getMessage());
        }
    }

    private void handleUniIslCommand(Tuple input) throws PipelineException {
        IslCommand command = pullValue(input, UniIslHandler.FIELD_ID_COMMAND, IslCommand.class);
        command.apply(this);
    }

    private void handleSpeakerInput(Tuple input) throws PipelineException {
        IslCommand command = pullValue(input, SpeakerRouter.FIELD_ID_COMMAND, IslCommand.class);
        command.apply(this);
    }

    private void handleSpeakerRulesWorkerInput(Tuple input) throws PipelineException {
        IslCommandBase command = pullValue(input, SpeakerRulesRouter.FIELD_ID_INPUT, IslCommandBase.class);
        command.apply(this);
    }

    @Override
    protected void init() {
        islService = new NetworkIslService(this, persistenceManager, options);
        RuleManager ruleManager = new RuleManagerImpl(ruleManagerConfig);
        islRulesService = new IslRulesService(this, persistenceManager, ruleManager);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declareStream(STREAM_BFD_HUB_ID, STREAM_BFD_HUB_FIELDS);
        streamManager.declareStream(STREAM_REROUTE_ID, STREAM_REROUTE_FIELDS);
        streamManager.declareStream(STREAM_STATUS_ID, STREAM_STATUS_FIELDS);
        streamManager.declareStream(STREAM_SPEAKER_RULES_ID, STREAM_SPEAKER_RULES_FIELDS);
        streamManager.declareStream(STREAM_POLL_ID, STREAM_POLL_FIELDS);
        streamManager.declareStream(STREAM_UNIISL_ID, STREAM_UNIISL_FIELDS);
        streamManager.declareStream(STREAM_FLOW_MONITORING_ID, STREAM_FLOW_MONITORING_FIELDS);
    }

    @Override
    public void bfdPropertiesApplyRequest(Endpoint physicalEndpoint, IslReference reference, BfdProperties properties) {
        emit(STREAM_BFD_HUB_ID, getCurrentTuple(),
                makeBfdHubTuple(new BfdHubEnableCommand(physicalEndpoint, reference, properties)));
    }

    @Override
    public void bfdDisableRequest(Endpoint physicalEndpoint) {
        emit(STREAM_BFD_HUB_ID, getCurrentTuple(),
                makeBfdHubTuple(new BfdHubDisableCommand(physicalEndpoint)));
    }

    @Override
    public void triggerReroute(RerouteFlows trigger) {
        emit(STREAM_REROUTE_ID, getCurrentTuple(), makeRerouteTuple(trigger));
    }

    @Override
    public void islStatusUpdateNotification(IslStatusUpdateNotification trigger) {
        emit(STREAM_STATUS_ID, getCurrentTuple(), makeStatusUpdateTuple(trigger));
    }

    @Override
    public void islRulesInstall(IslReference reference, Endpoint endpoint) {
        islRulesService.installIslRules(reference, endpoint);
    }

    @Override
    public void sendIslRulesInstallCommand(SwitchId switchId, UUID commandId, List<OfCommand> commands) {
        InstallSpeakerCommandsRequest request = InstallSpeakerCommandsRequest.builder()
                .messageContext(new MessageContext(getCommandContext().fork(commandId.toString()).getCorrelationId()))
                .switchId(switchId)
                .commandId(commandId)
                .commands(commands)
                .failIfExists(false)
                .build();
        emit(STREAM_SPEAKER_RULES_ID, getCurrentTuple(), makeSpeakerRulesTuple(request));
    }

    @Override
    public void islRulesDelete(IslReference reference, Endpoint endpoint) {
        islRulesService.deleteIslRules(reference, endpoint);
    }

    @Override
    public void sendIslRulesDeleteCommand(SwitchId switchId, UUID commandId, List<OfCommand> commands) {
        DeleteSpeakerCommandsRequest request = DeleteSpeakerCommandsRequest.builder()
                .messageContext(new MessageContext(getCommandContext().fork(commandId.toString()).getCorrelationId()))
                .switchId(switchId)
                .commandId(commandId)
                .commands(commands)
                .build();
        emit(STREAM_SPEAKER_RULES_ID, getCurrentTuple(), makeSpeakerRulesTuple(request));
    }

    @Override
    public void auxiliaryPollModeUpdateRequest(Endpoint endpoint, boolean enableAuxiliaryPollMode) {
        WatchListCommand command = new WatchListAuxiliaryPollModeUpdateCommand(endpoint, enableAuxiliaryPollMode);
        // We emit without the anchor tuple because here we are generating a new event to change the mode.
        // Also, if a cycle appears in the future by the anchor tuple, it will be quite difficult to find it,
        // and we remove the possibility of this cycle appearing initially.
        emit(STREAM_POLL_ID, makePollTuple(command));
    }

    @Override
    public void islRulesInstalled(IslReference reference, Endpoint endpoint) {
        islService.islRulesInstalled(reference, endpoint);
    }

    @Override
    public void islRulesDeleted(IslReference reference, Endpoint endpoint) {
        islService.islRulesDeleted(reference, endpoint);
    }

    @Override
    public void islRulesFailed(IslReference reference, Endpoint endpoint) {
        islService.islRulesFailed(reference, endpoint);
    }

    @Override
    public void islRemovedNotification(Endpoint srcEndpoint, IslReference reference) {
        // We emit without the anchor tuple to break the loop.
        emit(STREAM_UNIISL_ID, makeUniIslTuple(new UniIslNotifyIslRemovedCommand(srcEndpoint, reference)));
        emit(STREAM_BFD_HUB_ID, makeBfdHubTuple(new BfdHubIslRemoveNotificationCommand(srcEndpoint, reference)));
    }

    @Override
    public void islChangedNotifyFlowMonitor(IslReference reference, boolean removed) {
        Endpoint src = reference.getSource();
        Endpoint dst = reference.getDest();
        IslChangedInfoData islChangedInfoData = IslChangedInfoData.builder()
                .source(new NetworkEndpoint(src.getDatapath(), src.getPortNumber()))
                .destination(new NetworkEndpoint(dst.getDatapath(), dst.getPortNumber()))
                .removed(removed)
                .build();
        emit(STREAM_FLOW_MONITORING_ID, getCurrentTuple(), makeIslChangedTuple(islChangedInfoData));
    }

    private Values makeSpeakerRulesTuple(BaseSpeakerCommandsRequest request) {
        return new Values(request.getCommandId().toString(), new ProcessSpeakerRulesCommand(request),
                getCommandContext());
    }

    private Values makeBfdHubTuple(BfdHubCommand command) {
        return new Values(command.getSwitchId(), command,
                getCommandContext().fork(String.format("ISL-2-BFD %s", command.getWorkflowQualifier())));
    }

    private Values makeRerouteTuple(CommandData payload) {
        return new Values(null, payload, getCommandContext());
    }

    private Values makeStatusUpdateTuple(IslStatusUpdateNotification payload) {
        return new Values(null, payload, getCommandContext());
    }

    private Values makeIslChangedTuple(IslChangedInfoData payload) {
        return new Values(null, payload, getCommandContext());
    }

    private Values makePollTuple(WatchListCommand command) {
        Endpoint endpoint = command.getEndpoint();
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, getCommandContext());
    }

    private Values makeUniIslTuple(UniIslCommand command) {
        Endpoint endpoint = command.getEndpoint();
        return new Values(endpoint.getDatapath(), endpoint.getPortNumber(), command, getCommandContext());
    }

    public void processIslSetupFromHistory(Endpoint endpoint, IslReference reference, Isl history) {
        islService.islSetupFromHistory(endpoint, reference, history);
    }

    public void processIslUp(Endpoint endpoint, IslReference reference, IslDataHolder islData) {
        islService.islUp(endpoint, reference, islData);
    }

    public void processIslMove(Endpoint endpoint, IslReference reference) {
        islService.islMove(endpoint, reference);
    }

    public void processIslDown(Endpoint endpoint, IslReference reference, IslDownReason reason) {
        islService.islDown(endpoint, reference, reason);
    }

    public void processRoundTripStatus(IslReference reference, RoundTripStatus status) {
        islService.roundTripStatusNotification(reference, status);
    }

    public void processBfdStatusUpdate(Endpoint endpoint, IslReference reference, BfdStatusUpdate status) {
        islService.bfdStatusUpdate(endpoint, reference, status);
    }

    public void processBfdPropertiesUpdate(IslReference reference) {
        islService.bfdPropertiesUpdate(reference);
    }

    public void processIslRulesResponse(SpeakerCommandResponse response) {
        islRulesService.handleResponse(response);
    }

    public void processIslRulesTimeout(UUID commandId) {
        islRulesService.handleTimeout(commandId);
    }

    public void processIslRemove(IslReference reference) {
        islService.remove(reference);
    }
}
