/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions;

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowRequestMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateService;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class UpdateSubFlowsAction extends HistoryRecordingAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    private final FlowUpdateService flowUpdateService;

    public UpdateSubFlowsAction(FlowUpdateService flowUpdateService) {
        this.flowUpdateService = flowUpdateService;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowUpdateContext context, YFlowUpdateFsm stateMachine) {
        Collection<RequestedFlow> requestedFlows =
                YFlowRequestMapper.INSTANCE.toRequestedFlows(stateMachine.getTargetFlow());
        stateMachine.setRequestedFlows(requestedFlows);
        log.debug("Start updating {} sub-flows for y-flow {}", requestedFlows.size(), stateMachine.getYFlowId());
        stateMachine.clearUpdatingSubFlows();

        String yFlowId = stateMachine.getYFlowId();
        requestedFlows.forEach(requestedFlow -> {
            String subFlowId = requestedFlow.getFlowId();
            stateMachine.addSubFlow(subFlowId);
            if (requestedFlow.getFlowId().equals(stateMachine.getMainAffinityFlowId())) {
                stateMachine.addUpdatingSubFlow(subFlowId);
                stateMachine.notifyEventListeners(listener ->
                        listener.onSubFlowProcessingStart(yFlowId, subFlowId));
                CommandContext flowContext = stateMachine.getCommandContext().fork(subFlowId);
                requestedFlow.setDiverseFlowId(stateMachine.getDiverseFlowId());
                flowUpdateService.startFlowUpdating(flowContext, requestedFlow, yFlowId);
            }
        });
    }
}
