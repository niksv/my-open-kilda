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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

public class NotifyHaFlowStatsOnRemovedPathsAction<T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E, C> extends
        FlowProcessingWithHistorySupportAction<T, S, E, C> {
    private FlowGenericCarrier carrier;

    public NotifyHaFlowStatsOnRemovedPathsAction(PersistenceManager persistenceManager, FlowGenericCarrier carrier) {
        super(persistenceManager);
        this.carrier = carrier;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        HaFlow originalHaFlow = stateMachine.getOriginalHaFlow();

        // TODO notify stats https://github.com/telstra/open-kilda/issues/5182
        // example: org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyFlowStatsOnRemovedPathsAction
    }
}
