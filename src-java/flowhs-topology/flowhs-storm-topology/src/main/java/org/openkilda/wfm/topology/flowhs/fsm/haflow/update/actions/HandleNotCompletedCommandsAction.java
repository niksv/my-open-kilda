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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class HandleNotCompletedCommandsAction extends
        HistoryRecordingAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public static final String ACTION_MESSAGE = "Command is not finished yet";

    @Override
    public void perform(State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        for (UUID commandId : stateMachine.getPendingCommands().keySet()) {
            BaseSpeakerCommandsRequest request = stateMachine.getRemoveCommands().get(commandId);
            String actionName;
            if (request != null) {
                actionName = "remove";
            } else {
                request = stateMachine.getIngressCommands()
                        .computeIfAbsent(commandId, uuid -> stateMachine.getNonIngressCommands().get(uuid));
                actionName = "install";
            }

            if (request == null) {
                log.error("Can't find a request for pending commands {}. Switch id: {}",
                        commandId, stateMachine.getPendingCommands().get(commandId));
            } else {
                stateMachine.saveErrorToHistory(ACTION_MESSAGE,
                        format("Completing the update operation although the %s command may not be "
                                        + "finished yet: commandId %s, switch %s, command count %s",
                                commandId, actionName, request.getSwitchId(), request.getCommands().size()));
            }
        }

        log.debug("Abandoning all pending commands: {}", stateMachine.getPendingCommands());
        stateMachine.clearPendingCommands();

        if (!stateMachine.getPartialUpdateEndpoints().isEmpty()) {
            stateMachine.saveActionToHistory("Skip paths and resources allocation");
            stateMachine.fire(Event.UPDATE_ENDPOINT_RULES_ONLY);
        }
    }
}
