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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.InvalidFlowException;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.KildaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.validation.FlowValidator;
import org.openkilda.wfm.topology.flowhs.validation.UnavailableFlowEndpointException;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class ValidateFlowAction extends
        NbTrackableWithHistorySupportAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final KildaFeatureTogglesRepository featureTogglesRepository;
    private final FlowValidator flowValidator;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public ValidateFlowAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        featureTogglesRepository = repositoryFactory.createFeatureTogglesRepository();
        flowValidator = new FlowValidator(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowUpdateContext context,
                                                    FlowUpdateFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        RequestedFlow targetFlow = context.getTargetFlow();
        String diverseFlowId = targetFlow.getDiverseFlowId();

        dashboardLogger.onFlowUpdate(flowId,
                targetFlow.getSrcSwitch(), targetFlow.getSrcPort(), targetFlow.getSrcVlan(),
                targetFlow.getDestSwitch(), targetFlow.getDestPort(), targetFlow.getDestVlan(),
                diverseFlowId, targetFlow.getBandwidth(), targetFlow.getPathComputationStrategy(),
                targetFlow.getMaxLatency(), targetFlow.getMaxLatencyTier2());

        boolean isOperationAllowed = featureTogglesRepository.getOrDefault().getUpdateFlowEnabled();
        if (!isOperationAllowed) {
            throw new FlowProcessingException(ErrorType.NOT_PERMITTED, "Flow update feature is disabled");
        }

        stateMachine.setTargetFlow(targetFlow);
        stateMachine.setBulkUpdateFlowIds(context.getBulkUpdateFlowIds());
        stateMachine.setDoNotRevert(context.isDoNotRevert());
        Flow flow = getFlow(flowId);

        try {
            flowValidator.validate(targetFlow, stateMachine.getBulkUpdateFlowIds());
        } catch (InvalidFlowException e) {
            throw new FlowProcessingException(e.getType(), e.getMessage(), e);
        } catch (UnavailableFlowEndpointException e) {
            throw new FlowProcessingException(ErrorType.DATA_INVALID, e.getMessage(), e);
        }

        if ((!targetFlow.getSrcSwitch().equals(flow.getSrcSwitchId())
                || !targetFlow.getDestSwitch().equals(flow.getDestSwitchId()))
                && (!flow.getForwardPath().getFlowMirrorPointsSet().isEmpty()
                || !flow.getReversePath().getFlowMirrorPointsSet().isEmpty())) {
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                    "The current implementation of flow mirror points does not allow allocating paths. "
                            + "Therefore, remove the flow mirror points before changing the endpoint switch.");
        }

        transactionManager.doInTransaction(() -> {
            Flow foundFlow = getFlow(flowId);
            if (foundFlow.getStatus() == FlowStatus.IN_PROGRESS && stateMachine.getBulkUpdateFlowIds().isEmpty()) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Flow %s is in progress now", flowId));
            }

            // Keep it, just in case we have to revert it.
            stateMachine.setOriginalFlowStatus(foundFlow.getStatus());
            stateMachine.setOriginalFlowStatusInfo(foundFlow.getStatusInfo());

            foundFlow.setStatus(FlowStatus.IN_PROGRESS);
            foundFlow.setStatusInfo("");
            return foundFlow;
        });

        stateMachine.saveNewEventToHistory("Flow was validated successfully", FlowEventData.Event.UPDATE);

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not update flow";
    }

    @Override
    protected void handleError(FlowUpdateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed validation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());

    }
}
