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

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaFlowPathIds;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class RevertPathsSwapAction extends
        HaFlowProcessingWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public static final String NO_PATH = "no path";

    public RevertPathsSwapAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        if (!stateMachine.getPartialUpdateEndpoints().isEmpty()) {
            stateMachine.saveActionToHistory("Skip paths swap revert");
            return;
        }

        transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());
            updatePrimaryPaths(stateMachine, haFlow);
            updateProtectedPaths(stateMachine, haFlow);
        });
    }

    private void updatePrimaryPaths(HaFlowUpdateFsm stateMachine, HaFlow haFlow) {
        HaPathIdsPair oldPathIds = stateMachine.getOldPrimaryPathIds();

        if (oldPathIds == null) {
            return;
        }

        if (oldPathIds.getForward() != null) {
            PathId oldForwardPathId = getForwardPathId(oldPathIds);
            updatePathStatus(stateMachine, oldForwardPathId);
            updateSubPathStatuses(stateMachine, getForwardSubPathId(oldPathIds));

            log.debug("Swapping back the primary forward path {} with {}",
                    haFlow.getForwardPathId(), oldForwardPathId != null ? oldForwardPathId : NO_PATH);

            haFlow.setForwardPathId(oldForwardPathId);
            saveHistory(stateMachine, haFlow.getHaFlowId(), oldForwardPathId);
        }

        if (oldPathIds.getReverse() != null) {
            PathId oldReversePathId = getReversePathId(oldPathIds);
            updatePathStatus(stateMachine, oldReversePathId);
            updateSubPathStatuses(stateMachine, getReverseSubPathId(oldPathIds));

            log.debug("Swapping back the primary reverse path {} with {}",
                    haFlow.getReversePathId(), oldReversePathId != null ? oldReversePathId : NO_PATH);

            haFlow.setReversePathId(oldReversePathId);
            saveHistory(stateMachine, haFlow.getHaFlowId(), oldReversePathId);
        }
    }

    private void updateProtectedPaths(HaFlowUpdateFsm stateMachine, HaFlow haFlow) {
        HaPathIdsPair oldPathIds = stateMachine.getOldProtectedPathIds();

        if (oldPathIds == null) {
            return;
        }

        if (oldPathIds.getForward() != null) {
            PathId oldForwardPathId = getForwardPathId(oldPathIds);
            updatePathStatus(stateMachine, oldForwardPathId);
            updateSubPathStatuses(stateMachine, getForwardSubPathId(oldPathIds));

            log.debug("Swapping back the protected forward path {} with {}",
                    haFlow.getProtectedForwardPathId(),
                    oldForwardPathId != null ? oldForwardPathId : NO_PATH);

            haFlow.setProtectedForwardPathId(oldForwardPathId);
            saveHistory(stateMachine, haFlow.getHaFlowId(), oldForwardPathId);
        }

        if (oldPathIds.getReverse() != null) {
            PathId oldReversePathId = getReversePathId(oldPathIds);
            updatePathStatus(stateMachine, oldReversePathId);
            updateSubPathStatuses(stateMachine, getReverseSubPathId(oldPathIds));

            log.debug("Swapping back the protected reverse path {} with {}",
                    haFlow.getProtectedReversePathId(),
                    oldReversePathId != null ? oldReversePathId : NO_PATH);

            haFlow.setProtectedReversePathId(oldReversePathId);
            saveHistory(stateMachine, haFlow.getHaFlowId(), oldReversePathId);
        }
    }

    private void updatePathStatus(HaFlowUpdateFsm stateMachine, PathId oldPathId) {
        if (oldPathId != null) {
            haFlowPathRepository.findById(oldPathId)
                    .ifPresent(path -> {
                        if (path.getStatus() != FlowPathStatus.ACTIVE) {
                            path.setStatus(stateMachine.getOldPathStatus(oldPathId));
                        }
                    });
        }
    }

    private void updateSubPathStatuses(HaFlowUpdateFsm stateMachine, Collection<PathId> oldSubPathIds) {
        for (PathId oldSubPathId : oldSubPathIds) {
            flowPathRepository.findById(oldSubPathId)
                    .ifPresent(path -> {
                        if (path.getStatus() != FlowPathStatus.ACTIVE) {
                            path.setStatus(stateMachine.getOldPathStatus(oldSubPathId));
                        }
                    });
        }
    }

    private void saveHistory(HaFlowUpdateFsm stateMachine, String haFlowId, PathId pathId) {
        String pathName = pathId == null ? NO_PATH : format("the path %s", pathId);
        stateMachine.saveActionToHistory("Ha-flow was reverted to old paths",
                format("The ha-flow %s was updated with %s", haFlowId, pathName));
    }

    private PathId getForwardPathId(HaPathIdsPair haPathIdsPair) {
        return Optional.ofNullable(haPathIdsPair)
                .map(HaPathIdsPair::getForward)
                .map(HaFlowPathIds::getHaPathId)
                .orElse(null);
    }

    private PathId getReversePathId(HaPathIdsPair haPathIdsPair) {
        return Optional.ofNullable(haPathIdsPair)
                .map(HaPathIdsPair::getReverse)
                .map(HaFlowPathIds::getHaPathId)
                .orElse(null);
    }

    private Collection<PathId> getForwardSubPathId(HaPathIdsPair haPathIdsPair) {
        return Optional.ofNullable(haPathIdsPair)
                .map(HaPathIdsPair::getForward)
                .map(this::getSubPathIds)
                .orElse(new ArrayList<>());
    }

    private Collection<PathId> getReverseSubPathId(HaPathIdsPair haPathIdsPair) {
        return Optional.ofNullable(haPathIdsPair)
                .map(HaPathIdsPair::getReverse)
                .map(this::getSubPathIds)
                .orElse(new ArrayList<>());
    }

    private Collection<PathId> getSubPathIds(HaFlowPathIds haFlowPathIds) {
        return Optional.ofNullable(haFlowPathIds)
                .map(HaFlowPathIds::getSubPathIds)
                .map(Map::values)
                .orElse(new ArrayList<>());

    }
}
