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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions;

import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleNotCreatedHaFlowAction extends HistoryRecordingAction<
        HaFlowCreateFsm, State, Event, HaFlowCreateContext> {
    private final HaFlowRepository haFlowRepository;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public HandleNotCreatedHaFlowAction(PersistenceManager persistenceManager,
                                        FlowOperationsDashboardLogger dashboardLogger) {
        this.haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    public void perform(State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();
        dashboardLogger.onFlowStatusUpdate(haFlowId, FlowStatus.DOWN);
        // TODO status info
        haFlowRepository.updateStatus(haFlowId, FlowStatus.DOWN);
        stateMachine.saveActionToHistory("Failed to create the flow", stateMachine.getErrorReason());
        stateMachine.fire(Event.NEXT);
    }
}
