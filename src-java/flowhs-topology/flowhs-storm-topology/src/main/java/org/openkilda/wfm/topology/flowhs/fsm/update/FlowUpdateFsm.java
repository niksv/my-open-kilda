/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.update;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.pce.PathComputer;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.metrics.MeterRegistryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyFlowMonitorAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyFlowStatsOnNewPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NotifyFlowStatsOnRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.ReportErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.AbandonPendingCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.AllocatePrimaryResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.AllocateProtectedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.CompleteFlowPathInstallationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.CompleteFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.DeallocateResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.EmitIngressRulesVerifyRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.EmitNonIngressRulesVerifyRequestsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.HandleNotCompletedCommandsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.HandleNotDeallocatedResourcesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.HandleNotRemovedPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.HandleNotRevertedResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.InstallIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.InstallNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.OnFinishedAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.OnFinishedWithErrorAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.OnReceivedInstallResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.OnReceivedRemoveOrRevertResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.PostResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RemoveOldRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertMirrorPointsSettingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertNewRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertPathsSwapAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.RevertResourceAllocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.SkipPathsAndResourcesDeallocationAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.SwapFlowPathsAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.UpdateFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.UpdateFlowStatusAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.ValidateFlowAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.ValidateIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.actions.ValidateNonIngressRulesAction;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateEventListener;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateHubCarrier;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@Slf4j
public final class FlowUpdateFsm extends FlowPathSwappingFsm<FlowUpdateFsm, State, Event, FlowUpdateContext,
        FlowUpdateHubCarrier, FlowUpdateEventListener> {
    private RequestedFlow targetFlow;

    private FlowStatus originalFlowStatus;
    private String originalFlowStatusInfo;
    private String originalDiverseFlowGroup;
    private String originalAffinityFlowGroup;
    private PathComputationStrategy oldTargetPathComputationStrategy;

    private FlowStatus newFlowStatus;

    private Set<String> bulkUpdateFlowIds;
    private boolean doNotRevert;

    private EndpointUpdate endpointUpdate = EndpointUpdate.NONE;
    private FlowLoopOperation flowLoopOperation = FlowLoopOperation.NONE;

    public FlowUpdateFsm(@NonNull CommandContext commandContext, @NonNull FlowUpdateHubCarrier carrier,
                         @NonNull String flowId,
                         @NonNull Collection<FlowUpdateEventListener> eventListeners) {
        super(Event.NEXT, Event.ERROR, commandContext, carrier, flowId, eventListeners);
    }

    @Override
    public void fireNoPathFound(String errorReason) {
        fireError(Event.NO_PATH_FOUND, errorReason);
    }

    @Override
    protected String getCrudActionName() {
        return "update";
    }

    public void sendHubSwapEndpointsResponse(Message message) {
        getCarrier().sendHubSwapEndpointsResponse(message);
    }

    public static class Factory {
        private final StateMachineBuilder<FlowUpdateFsm, State, Event, FlowUpdateContext> builder;
        private final FlowUpdateHubCarrier carrier;

        public Factory(@NonNull FlowUpdateHubCarrier carrier, @NonNull Config config,
                       @NonNull PersistenceManager persistenceManager,
                       @NonNull PathComputer pathComputer, @NonNull FlowResourcesManager resourcesManager) {
            this.carrier = carrier;

            builder = StateMachineBuilderFactory.create(FlowUpdateFsm.class, State.class, Event.class,
                    FlowUpdateContext.class, CommandContext.class, FlowUpdateHubCarrier.class, String.class,
                    Collection.class);

            FlowOperationsDashboardLogger dashboardLogger = new FlowOperationsDashboardLogger(log);
            final ReportErrorAction<FlowUpdateFsm, State, Event, FlowUpdateContext>
                    reportErrorAction = new ReportErrorAction<>(Event.TIMEOUT);

            builder.transition().from(State.INITIALIZED).to(State.FLOW_VALIDATED).on(Event.NEXT)
                    .perform(new ValidateFlowAction(persistenceManager, dashboardLogger));
            builder.transition().from(State.INITIALIZED).to(State.FINISHED_WITH_ERROR).on(Event.TIMEOUT);

            builder.transition().from(State.FLOW_VALIDATED).to(State.FLOW_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowAction(persistenceManager));
            builder.transitions().from(State.FLOW_VALIDATED)
                    .toAmong(State.REVERTING_FLOW_STATUS, State.REVERTING_FLOW_STATUS)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.FLOW_UPDATED).to(State.PRIMARY_RESOURCES_ALLOCATED).on(Event.NEXT)
                    .perform(new AllocatePrimaryResourcesAction(persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            config.getResourceAllocationRetriesLimit(), pathComputer, resourcesManager,
                            dashboardLogger));
            builder.transitions().from(State.FLOW_UPDATED)
                    .toAmong(State.REVERTING_FLOW, State.REVERTING_FLOW)
                    .onEach(Event.TIMEOUT, Event.ERROR);
            builder.transition().from(State.FLOW_UPDATED).to(State.RESOURCE_ALLOCATION_COMPLETED)
                    .on(Event.UPDATE_ENDPOINT_RULES_ONLY)
                    .perform(new PostResourceAllocationAction(persistenceManager));

            builder.transition().from(State.PRIMARY_RESOURCES_ALLOCATED).to(State.PROTECTED_RESOURCES_ALLOCATED)
                    .on(Event.NEXT)
                    .perform(new AllocateProtectedResourcesAction(persistenceManager,
                            config.getPathAllocationRetriesLimit(), config.getPathAllocationRetryDelay(),
                            config.getResourceAllocationRetriesLimit(), pathComputer, resourcesManager,
                            dashboardLogger));
            builder.transitions().from(State.PRIMARY_RESOURCES_ALLOCATED)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR, Event.NO_PATH_FOUND);

            builder.transition().from(State.PROTECTED_RESOURCES_ALLOCATED).to(State.RESOURCE_ALLOCATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new PostResourceAllocationAction(persistenceManager));
            builder.transitions().from(State.PROTECTED_RESOURCES_ALLOCATED)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED, State.REVERTING_ALLOCATED_RESOURCES)
                    .onEach(Event.TIMEOUT, Event.ERROR, Event.NO_PATH_FOUND);

            builder.transition().from(State.RESOURCE_ALLOCATION_COMPLETED).to(State.INSTALLING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallNonIngressRulesAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.RESOURCE_ALLOCATION_COMPLETED)
                    .toAmong(State.NEW_RULES_REVERTED, State.NEW_RULES_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new SkipPathsAndResourcesDeallocationAction(persistenceManager));

            builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(config.getSpeakerCommandRetriesLimit()));
            builder.internalTransition().within(State.INSTALLING_NON_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(config.getSpeakerCommandRetriesLimit()));
            builder.transition().from(State.INSTALLING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transitions().from(State.INSTALLING_NON_INGRESS_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.NON_INGRESS_RULES_INSTALLED).to(State.VALIDATING_NON_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new EmitNonIngressRulesVerifyRequestsAction());
            builder.transitions().from(State.NON_INGRESS_RULES_INSTALLED)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new RevertMirrorPointsSettingAction(persistenceManager));

            builder.internalTransition().within(State.VALIDATING_NON_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new ValidateNonIngressRulesAction(config.getSpeakerCommandRetriesLimit()));
            builder.internalTransition().within(State.VALIDATING_NON_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new ValidateNonIngressRulesAction(config.getSpeakerCommandRetriesLimit()));
            builder.transition().from(State.VALIDATING_NON_INGRESS_RULES).to(State.NON_INGRESS_RULES_VALIDATED)
                    .on(Event.RULES_VALIDATED);
            builder.transitions().from(State.VALIDATING_NON_INGRESS_RULES)
                    .toAmong(State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED, State.PATHS_SWAP_REVERTED)
                    .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.NON_INGRESS_RULES_VALIDATED).to(State.PATHS_SWAPPED).on(Event.NEXT)
                    .perform(new SwapFlowPathsAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.NON_INGRESS_RULES_VALIDATED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition()
                    .from(State.PATHS_SWAPPED)
                    .to(State.NOTIFY_FLOW_STATS_ON_NEW_PATHS)
                    .on(Event.NEXT)
                    .perform(new NotifyFlowStatsOnNewPathsAction<>(persistenceManager, carrier));
            builder.transitions().from(State.PATHS_SWAPPED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.NOTIFY_FLOW_STATS_ON_NEW_PATHS)
                    .to(State.INSTALLING_INGRESS_RULES)
                    .on(Event.NEXT)
                    .perform(new InstallIngressRulesAction(persistenceManager, resourcesManager));

            builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(config.getSpeakerCommandRetriesLimit()));
            builder.internalTransition().within(State.INSTALLING_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedInstallResponseAction(config.getSpeakerCommandRetriesLimit()));
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_INSTALLED)
                    .on(Event.RULES_INSTALLED);
            builder.transition().from(State.INSTALLING_INGRESS_RULES).to(State.INGRESS_RULES_VALIDATED)
                    .on(Event.INGRESS_IS_SKIPPED);
            builder.transitions().from(State.INSTALLING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.INGRESS_RULES_INSTALLED).to(State.VALIDATING_INGRESS_RULES).on(Event.NEXT)
                    .perform(new EmitIngressRulesVerifyRequestsAction());
            builder.transitions().from(State.INGRESS_RULES_INSTALLED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.VALIDATING_INGRESS_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new ValidateIngressRulesAction(config.getSpeakerCommandRetriesLimit()));
            builder.internalTransition().within(State.VALIDATING_INGRESS_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new ValidateIngressRulesAction(config.getSpeakerCommandRetriesLimit()));
            builder.transition().from(State.VALIDATING_INGRESS_RULES).to(State.INGRESS_RULES_VALIDATED)
                    .on(Event.RULES_VALIDATED);
            builder.transitions().from(State.VALIDATING_INGRESS_RULES)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.MISSING_RULE_FOUND, Event.ERROR)
                    .perform(new AbandonPendingCommandsAction());

            builder.transition().from(State.INGRESS_RULES_VALIDATED).to(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteFlowPathInstallationAction(persistenceManager));
            builder.transitions().from(State.INGRESS_RULES_VALIDATED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.transition().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .to(State.REMOVING_OLD_RULES).on(Event.NEXT)
                    .perform(new RemoveOldRulesAction(persistenceManager, resourcesManager));
            builder.transitions().from(State.NEW_PATHS_INSTALLATION_COMPLETED)
                    .toAmong(State.REVERTING_PATHS_SWAP, State.REVERTING_PATHS_SWAP)
                    .onEach(Event.TIMEOUT, Event.ERROR);

            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(config.getSpeakerCommandRetriesLimit()));
            builder.internalTransition().within(State.REMOVING_OLD_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(config.getSpeakerCommandRetriesLimit()));
            builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                    .on(Event.RULES_REMOVED)
                    .perform(new SkipPathsAndResourcesDeallocationAction(persistenceManager));
            builder.transition().from(State.REMOVING_OLD_RULES).to(State.OLD_RULES_REMOVED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transition().from(State.OLD_RULES_REMOVED)
                    .to(State.NOTIFY_FLOW_STATS_ON_REMOVED_PATHS).on(Event.NEXT)
                    .perform(new NotifyFlowStatsOnRemovedPathsAction<>(persistenceManager, carrier));
            builder.transition().from(State.OLD_RULES_REMOVED).to(State.UPDATING_FLOW_STATUS)
                    .on(Event.UPDATE_ENDPOINT_RULES_ONLY);

            builder.transition().from(State.NOTIFY_FLOW_STATS_ON_REMOVED_PATHS)
                    .to(State.OLD_PATHS_REMOVAL_COMPLETED)
                    .on(Event.NEXT)
                    .perform(new CompleteFlowPathRemovalAction(persistenceManager));

            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.NEXT);
            builder.transition().from(State.OLD_PATHS_REMOVAL_COMPLETED).to(State.DEALLOCATING_OLD_RESOURCES)
                    .on(Event.ERROR)
                    .perform(new HandleNotRemovedPathsAction());

            builder.transition().from(State.DEALLOCATING_OLD_RESOURCES)
                    .to(State.OLD_RESOURCES_DEALLOCATED).on(Event.NEXT)
                    .perform(new DeallocateResourcesAction(persistenceManager, resourcesManager));

            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS).on(Event.NEXT);
            builder.transition().from(State.OLD_RESOURCES_DEALLOCATED).to(State.UPDATING_FLOW_STATUS)
                    .on(Event.ERROR)
                    .perform(new HandleNotDeallocatedResourcesAction());

            builder.transition().from(State.UPDATING_FLOW_STATUS).to(State.FLOW_STATUS_UPDATED).on(Event.NEXT)
                    .perform(new UpdateFlowStatusAction(persistenceManager, dashboardLogger));

            builder.transition().from(State.FLOW_STATUS_UPDATED).to(State.NOTIFY_FLOW_MONITOR).on(Event.NEXT);
            builder.transition().from(State.FLOW_STATUS_UPDATED)
                    .to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR).on(Event.ERROR);

            builder.onEntry(State.REVERTING_PATHS_SWAP)
                    .perform(reportErrorAction);
            builder.onExit(State.REVERTING_PATHS_SWAP)
                    .perform(new RevertMirrorPointsSettingAction(persistenceManager));
            builder.transition().from(State.REVERTING_PATHS_SWAP).to(State.PATHS_SWAP_REVERTED)
                    .on(Event.NEXT)
                    .perform(new RevertPathsSwapAction(persistenceManager));

            builder.onEntry(State.PATHS_SWAP_REVERTED)
                    .perform(reportErrorAction);
            builder.transition().from(State.PATHS_SWAP_REVERTED)
                    .to(State.REVERTING_NEW_RULES)
                    .on(Event.NEXT)
                    .perform(new RevertNewRulesAction(persistenceManager, resourcesManager));

            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.RESPONSE_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(config.getSpeakerCommandRetriesLimit()));
            builder.internalTransition().within(State.REVERTING_NEW_RULES).on(Event.ERROR_RECEIVED)
                    .perform(new OnReceivedRemoveOrRevertResponseAction(config.getSpeakerCommandRetriesLimit()));
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.RULES_REMOVED)
                    .perform(new SkipPathsAndResourcesDeallocationAction(persistenceManager));
            builder.transition().from(State.REVERTING_NEW_RULES).to(State.NEW_RULES_REVERTED)
                    .on(Event.ERROR)
                    .perform(new HandleNotCompletedCommandsAction());

            builder.transitions().from(State.NEW_RULES_REVERTED)
                    .toAmong(State.REVERTING_ALLOCATED_RESOURCES, State.REVERTING_FLOW)
                    .onEach(Event.NEXT, Event.UPDATE_ENDPOINT_RULES_ONLY)
                    .perform(new RevertMirrorPointsSettingAction(persistenceManager));

            builder.onEntry(State.REVERTING_ALLOCATED_RESOURCES)
                    .perform(reportErrorAction);
            builder.transitions().from(State.REVERTING_ALLOCATED_RESOURCES)
                    .toAmong(State.RESOURCES_ALLOCATION_REVERTED)
                    .onEach(Event.NEXT)
                    .perform(new RevertResourceAllocationAction(persistenceManager, resourcesManager));
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.REVERTING_FLOW).on(Event.NEXT);
            builder.transition().from(State.RESOURCES_ALLOCATION_REVERTED).to(State.REVERTING_FLOW)
                    .on(Event.ERROR)
                    .perform(new HandleNotRevertedResourceAllocationAction());

            builder.onEntry(State.REVERTING_FLOW)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_FLOW)
                    .to(State.REVERTING_FLOW_STATUS)
                    .on(Event.NEXT)
                    .perform(new RevertFlowAction(persistenceManager));

            builder.onEntry(State.REVERTING_FLOW_STATUS)
                    .perform(reportErrorAction);
            builder.transition().from(State.REVERTING_FLOW_STATUS)
                    .to(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new RevertFlowStatusAction(persistenceManager));

            builder.onEntry(State.FINISHED_WITH_ERROR)
                    .perform(reportErrorAction);

            builder.transition()
                    .from(State.NOTIFY_FLOW_MONITOR)
                    .to(State.FINISHED)
                    .on(Event.NEXT)
                    .perform(new NotifyFlowMonitorAction<>(persistenceManager, carrier));
            builder.transition()
                    .from(State.NOTIFY_FLOW_MONITOR_WITH_ERROR)
                    .to(State.FINISHED_WITH_ERROR)
                    .on(Event.NEXT)
                    .perform(new NotifyFlowMonitorAction<>(persistenceManager, carrier));

            builder.defineFinalState(State.FINISHED)
                    .addEntryAction(new OnFinishedAction(dashboardLogger));
            builder.defineFinalState(State.FINISHED_WITH_ERROR)
                    .addEntryAction(new OnFinishedWithErrorAction(dashboardLogger));
        }

        public FlowUpdateFsm newInstance(@NonNull String flowId, @NonNull CommandContext commandContext,
                                         @NonNull Collection<FlowUpdateEventListener> eventListeners) {
            FlowUpdateFsm fsm = builder.newStateMachine(State.INITIALIZED, commandContext, carrier, flowId,
                    eventListeners);

            fsm.addTransitionCompleteListener(event ->
                    log.debug("FlowUpdateFsm, transition to {} on {}", event.getTargetState(), event.getCause()));

            if (fsm.getEventListeners() != null && !fsm.getEventListeners().isEmpty()) {
                fsm.addTransitionCompleteListener(event -> {
                    switch (event.getTargetState()) {
                        case FINISHED:
                            fsm.getEventListeners().forEach(listener -> listener.onCompleted(fsm.getFlowId()));
                            break;
                        case FINISHED_WITH_ERROR:
                            ErrorType errorType = Optional.ofNullable(fsm.getOperationResultMessage())
                                    .filter(message -> message instanceof ErrorMessage)
                                    .map(message -> ((ErrorMessage) message).getData())
                                    .map(ErrorData::getErrorType).orElse(ErrorType.INTERNAL_ERROR);
                            fsm.getEventListeners().forEach(listener -> listener.onFailed(fsm.getFlowId(),
                                    fsm.getErrorReason(), errorType));
                            break;
                        default:
                            // ignore
                    }
                });
            }

            MeterRegistryHolder.getRegistry().ifPresent(registry -> {
                Sample sample = LongTaskTimer.builder("fsm.active_execution")
                        .register(registry)
                        .start();
                fsm.addTerminateListener(e -> {
                    long duration = sample.stop();
                    if (fsm.getCurrentState() == State.FINISHED) {
                        registry.timer("fsm.execution.success")
                                .record(duration, TimeUnit.NANOSECONDS);
                    } else if (fsm.getCurrentState() == State.FINISHED_WITH_ERROR) {
                        registry.timer("fsm.execution.failed")
                                .record(duration, TimeUnit.NANOSECONDS);
                    }
                });
            });
            return fsm;
        }
    }

    public enum EndpointUpdate {
        NONE(false),
        SOURCE(true),
        DESTINATION(true),
        BOTH(true);

        private boolean partialUpdate;

        EndpointUpdate(boolean partialUpdate) {
            this.partialUpdate = partialUpdate;
        }

        public boolean isPartialUpdate() {
            return partialUpdate;
        }
    }

    public enum FlowLoopOperation {
        NONE,
        CREATE,
        DELETE
    }

    public enum State {
        INITIALIZED,
        FLOW_VALIDATED,
        FLOW_UPDATED,
        PRIMARY_RESOURCES_ALLOCATED,
        PROTECTED_RESOURCES_ALLOCATED,
        RESOURCE_ALLOCATION_COMPLETED,

        INSTALLING_NON_INGRESS_RULES,
        NON_INGRESS_RULES_INSTALLED,
        VALIDATING_NON_INGRESS_RULES,
        NON_INGRESS_RULES_VALIDATED,

        PATHS_SWAPPED,

        INSTALLING_INGRESS_RULES,
        INGRESS_RULES_INSTALLED,
        VALIDATING_INGRESS_RULES,
        INGRESS_RULES_VALIDATED,

        NEW_PATHS_INSTALLATION_COMPLETED,

        REMOVING_OLD_RULES,
        OLD_RULES_REMOVED,

        OLD_PATHS_REMOVAL_COMPLETED,

        DEALLOCATING_OLD_RESOURCES,
        OLD_RESOURCES_DEALLOCATED,

        UPDATING_FLOW_STATUS,
        FLOW_STATUS_UPDATED,

        FINISHED,

        REVERTING_PATHS_SWAP,
        PATHS_SWAP_REVERTED,
        REVERTING_NEW_RULES,
        NEW_RULES_REVERTED,

        REVERTING_ALLOCATED_RESOURCES,
        RESOURCES_ALLOCATION_REVERTED,
        REVERTING_FLOW_STATUS,
        REVERTING_FLOW,

        FINISHED_WITH_ERROR,

        NOTIFY_FLOW_MONITOR,
        NOTIFY_FLOW_MONITOR_WITH_ERROR,

        NOTIFY_FLOW_STATS_ON_NEW_PATHS,
        NOTIFY_FLOW_STATS_ON_REMOVED_PATHS
    }

    public enum Event {
        NEXT,

        UPDATE_ENDPOINT_RULES_ONLY,

        NO_PATH_FOUND,

        RESPONSE_RECEIVED,
        ERROR_RECEIVED,

        INGRESS_IS_SKIPPED,

        RULES_INSTALLED,
        RULES_VALIDATED,
        MISSING_RULE_FOUND,

        RULES_REMOVED,

        TIMEOUT,
        ERROR
    }

    @Value
    @Builder
    public static class Config implements Serializable {
        @Builder.Default
        int speakerCommandRetriesLimit = 3;
        @Builder.Default
        int pathAllocationRetriesLimit = 10;
        @Builder.Default
        int pathAllocationRetryDelay = 50;
        @Builder.Default
        int resourceAllocationRetriesLimit = 10;
    }
}
