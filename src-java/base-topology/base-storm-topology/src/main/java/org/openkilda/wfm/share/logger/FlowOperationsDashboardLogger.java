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

package org.openkilda.wfm.share.logger;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.IslEndpoint;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.reporting.AbstractDashboardLogger;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowOperationsDashboardLogger extends AbstractDashboardLogger {

    private static final String FLOW_ID = "flow_id";
    private static final String STATUS = "status";
    private static final String EVENT_TYPE = "event_type";
    private static final String FLOW_READ_EVENT = "flow_read";
    private static final String FLOW_CREATE_EVENT = "flow_create";
    private static final String CREATE_RESULT_EVENT = "flow_create_result";
    private static final String FLOW_UPDATE_EVENT = "flow_update";
    private static final String UPDATE_RESULT_EVENT = "flow_update_result";
    private static final String FLOW_DELETE_EVENT = "flow_delete";
    private static final String DELETE_RESULT_EVENT = "flow_delete_result";
    private static final String FLOW_SYNC_EVENT = "flow_sync";
    private static final String SYNC_RESULT_EVENT = "flow_sync_result";
    private static final String PATHS_SWAP_EVENT = "paths_swap";
    private static final String REROUTE_EVENT = "flow_reroute";
    private static final String REROUTE_RESULT_EVENT = "flow_reroute_result";
    private static final String STATUS_UPDATE_EVENT = "status_update";
    private static final String FLOW_MIRROR_POINT_CREATE_EVENT = "flow_mirror_point_create";
    private static final String FLOW_MIRROR_POINT_CREATE_RESULT_EVENT = "flow_mirror_point_create_result";
    private static final String FLOW_MIRROR_POINT_DELETE_EVENT = "flow_mirror_point_delete";
    private static final String FLOW_MIRROR_POINT_DELETE_RESULT_EVENT = "flow_mirror_point_create_delete";

    private static final String YFLOW_CREATE_EVENT = "y_flow_create";
    private static final String YFLOW_CREATE_RESULT_EVENT = "y_flow_create_result";
    private static final String YFLOW_UPDATE_EVENT = "y_flow_update";
    private static final String YFLOW_UPDATE_RESULT_EVENT = "y_flow_update_result";
    private static final String YFLOW_REROUTE_EVENT = "y_flow_reroute";
    private static final String YFLOW_REROUTE_RESULT_EVENT = "y_flow_reroute_result";
    private static final String YFLOW_DELETE_EVENT = "y_flow_delete";
    private static final String YFLOW_DELETE_RESULT_EVENT = "y_flow_delete_result";
    private static final String YFLOW_PATHS_SWAP_EVENT = "y_flow_paths_swap";
    private static final String YFLOW_PATHS_SWAP_RESULT_EVENT = "y_flow_paths_swap_result";

    private static final String HA_FLOW_CREATE_EVENT = "ha_flow_create";
    private static final String HA_FLOW_CREATE_RESULT_EVENT = "ha_flow_create_result";
    private static final String HA_FLOW_DELETE_EVENT = "ha_flow_delete";
    private static final String HA_FLOW_DELETE_RESULT_EVENT = "ha_flow_delete_result";
    private static final String HA_FLOW_UPDATE_EVENT = "ha_flow_update";
    private static final String HA_FLOW_UPDATE_RESULT_EVENT = "ha_flow_update_result";
    private static final String HA_FLOW_PATH_SWAP_EVENT = "ha_flow_path_swap";
    private static final String HA_FLOW_PATH_SWAP_RESULT_EVENT = "ha_flow_path_swap_result";

    private static final String TAG = "FLOW_OPERATIONS_DASHBOARD";
    private static final String DASHBOARD = "dashboard";

    private static final String DELETE_RESULT = "delete-result";
    private static final String UPDATE_RESULT = "update-result";
    private static final String REROUTE_RESULT = "reroute-result";

    private static final String SUCCESSFUL_RESULT = "successful";
    private static final String FAILED_RESULT = "failed";

    private static final String FAILURE_REASON = "failure-reason";

    public FlowOperationsDashboardLogger(Logger logger) {
        super(logger);
    }

    /**
     * Log a flow-dump event.
     */
    public void onFlowDump() {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, "Dump flows", data);
    }

    /**
     * Log a flow-dump-by-link event.
     */
    public void onFlowPathsDumpByLink(SwitchId srcSwitchId, Integer srcPort,
                                      SwitchId dstSwitchId, Integer dstPort) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-link");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Dump flows by link %s_%d-%s_%d", srcSwitchId, srcPort,
                dstSwitchId, dstPort), data);
    }

    /**
     * Log a flow-dump-by-endpoint event.
     */
    public void onFlowPathsDumpByEndpoint(SwitchId switchId, Integer port) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-endpoint");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Dump flows by end-point %s_%d", switchId, port), data);
    }

    /**
     * Log a flow-dump-by-switch event.
     */
    public void onFlowPathsDumpBySwitch(SwitchId switchId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-dump-by-switch");
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Dump flows by switch %s", switchId), data);
    }

    /**
     * Log a flow-read event.
     */
    public void onFlowRead(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-read");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Read the flow %s", flowId), data);
    }

    /**
     * Log a flow-paths-read event.
     */
    public void onFlowPathsRead(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-read");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_READ_EVENT);
        invokeLogger(Level.INFO, String.format("Read paths of the flow %s", flowId), data);
    }

    /**
     * Log a flow-create event.
     */
    public void onFlowCreate(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create the flow: %s", flow), data);
    }

    /**
     * Log a flow-create event.
     */
    public void onFlowCreate(String flowId, SwitchId srcSwitch, int srcPort, int srcVlan,
                             SwitchId destSwitch, int destPort, int destVlan, String diverseFlowId, long bandwidth,
                             PathComputationStrategy strategy, Long maxLatency, Long maxLatencyTier2) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create the flow: %s, source %s_%d_%d, destination %s_%d_%d, "
                        + "diverse flowId %s, bandwidth %d, path computation strategy %s, max latency %s, "
                        + "max latency tier2 %s", flowId, srcSwitch, srcPort, srcVlan, destSwitch, destPort, destVlan,
                diverseFlowId, bandwidth, strategy, maxLatency, maxLatencyTier2), data);
    }

    /**
     * Log a flow-create-successful event.
     */
    public void onSuccessfulFlowCreate(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, CREATE_RESULT_EVENT);
        data.put("create-result", SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful create of the flow %s", flowId), data);
    }

    /**
     * Log a flow-create-failed event.
     */
    public void onFailedFlowCreate(String flowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-create-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, CREATE_RESULT_EVENT);
        data.put(UPDATE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed create of the flow %s, reason: %s", flowId, failureReason),
                data);
    }

    /**
     * Log a flow-push event.
     */
    public void onFlowPush(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-push");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Push the flow: %s", flow), data);
    }

    /**
     * Log a flow-status-update event.
     */
    public void onFlowStatusUpdate(String flowId, FlowStatus status) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-status-update");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, STATUS_UPDATE_EVENT);
        data.put(STATUS, status.toString());
        invokeLogger(Level.INFO, String.format("Update the status of the flow %s to %s", flowId, status), data);
    }

    /**
     * Log a flow-update event.
     */
    public void onFlowUpdate(String flowId, SwitchId srcSwitch, int srcPort, int srcVlan,
                             SwitchId destSwitch, int destPort, int destVlan, String diverseFlowId, long bandwidth,
                             PathComputationStrategy strategy, Long maxLatency, Long maxLatencyTier2) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-update");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Update the flow %s with: source %s_%d_%d, destination %s_%d_%d, "
                        + "diverse flowId %s, bandwidth %d, path computation strategy %s, max latency %s, "
                        + "max latency tier2 %s", flowId, srcSwitch, srcPort, srcVlan,
                destSwitch, destPort, destVlan, diverseFlowId, bandwidth, strategy, maxLatency, maxLatencyTier2), data);
    }

    /**
     * Log a flow-update-successful event.
     */
    public void onSuccessfulFlowUpdate(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-update-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, UPDATE_RESULT_EVENT);
        data.put(UPDATE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful update of the flow %s", flowId), data);
    }

    /**
     * Log a flow-update-failed event.
     */
    public void onFailedFlowUpdate(String flowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-update-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, UPDATE_RESULT_EVENT);
        data.put(UPDATE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed update of the flow %s, reason: %s", flowId, failureReason),
                data);
    }

    /**
     * Log a flow-patch-update event.
     */
    public void onFlowPatchUpdate(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-patch-update");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Patch update the flow: %s", flow), data);
    }

    /**
     * Log a flow-delete event.
     */
    public void onFlowDelete(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-delete");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_DELETE_EVENT);
        invokeLogger(Level.INFO, String.format("Delete the flow %s", flowId), data);
    }

    /**
     * Log a flow-delete-successful event.
     */
    public void onSuccessfulFlowDelete(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-delete-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, DELETE_RESULT_EVENT);
        data.put(DELETE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful delete of the flow %s", flowId), data);
    }

    /**
     * Log a flow-delete-failed event.
     */
    public void onFailedFlowDelete(String flowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-delete-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, DELETE_RESULT_EVENT);
        data.put(DELETE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed delete of the flow %s, reason: %s", flowId, failureReason),
                data);
    }

    /**
     * Log a flow-sync event.
     */
    public void onFlowSync(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-sync");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_SYNC_EVENT);
        invokeLogger(Level.INFO, String.format("Performing flow \"%s\" SYNC", flowId), data);
    }

    /**
     * Log a flow-sync-successful event.
     */
    public void onSuccessfulFlowSync(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-sync-success");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_SYNC_EVENT);
        data.put("sync-result", SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Flow \"%s\" SYNC success", flowId), data);
    }

    /**
     * Log a flow-sync-failed event.
     */
    public void onFailedFlowSync(String flowId, int failedPathsCount, int totalPathsCount) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-sync-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_SYNC_EVENT);
        data.put("sync-result", FAILED_RESULT);
        invokeLogger(
                Level.INFO, String.format(
                        "Flow \"%s\" SYNC failed - %d of %d path have failed to sync",
                        flowId, failedPathsCount, totalPathsCount),
                data);
    }

    /**
     * Log a flow-endpoint-swap event.
     */
    public void onFlowEndpointSwap(Flow firstFlow, Flow secondFlow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-endpoint-swap");
        data.put(FLOW_ID, firstFlow.getFlowId());
        data.put(EVENT_TYPE, FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Swap end-points for the flows %s / %s", firstFlow, secondFlow), data);
    }

    /**
     * Log a flow-paths-swap event.
     */
    public void onFlowPathsSwap(Flow flow) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-swap");
        data.put(FLOW_ID, flow.getFlowId());
        data.put(EVENT_TYPE, PATHS_SWAP_EVENT);
        invokeLogger(Level.INFO, String.format("Swap paths for the flow: %s", flow), data);
    }

    /**
     * Log a flow-paths-reroute event.
     */
    public void onFlowPathReroute(String flowId, Collection<IslEndpoint> affectedIsl) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-paths-reroute");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, REROUTE_EVENT);
        invokeLogger(Level.INFO, String.format("Reroute due to failure on %s ISLs flow %s", affectedIsl, flowId), data);
    }

    /**
     * Log a flow-reroute-successful event.
     */
    public void onSuccessfulFlowReroute(String flowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-reroute-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, REROUTE_RESULT_EVENT);
        data.put(REROUTE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful reroute of the flow %s", flowId), data);
    }

    /**
     * Log a flow-reroute-failed event.
     */
    public void onFailedFlowReroute(String flowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "flow-reroute-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, REROUTE_RESULT_EVENT);
        data.put(REROUTE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(
                Level.WARN, String.format("Failed reroute of the flow %s, reason: %s", flowId, failureReason), data);
    }

    /**
     * Log a flow-mirror-point-create event.
     */
    public void onFlowMirrorPointCreate(String flowId, SwitchId srcSwitch, String direction,
                                        SwitchId destSwitch, int destPort, int destVlan) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-create");
        data.put(TAG, "flow-mirror-point-create");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create a mirror point for the flow %s: source switch %s, "
                        + "destination endpoint %s_%d_%d, direction %s",
                flowId, srcSwitch, destSwitch, destPort, destVlan, direction), data);
    }

    /**
     * Log a flow-mirror-point-create-successful event.
     */
    public void onSuccessfulFlowMirrorPointCreate(String flowId, SwitchId srcSwitch, String direction,
                                                  SwitchId destSwitch, int destPort, int destVlan) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-create-successful");
        data.put(TAG, "flow-mirror-point-create-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_CREATE_RESULT_EVENT);
        data.put("flow-mirror-point-create-result", SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful create a mirror point for the flow %s: source switch %s, "
                        + "destination endpoint %s_%d_%d, direction %s",
                flowId, srcSwitch, destSwitch, destPort, destVlan, direction), data);
    }

    /**
     * Log a flow-mirror-point-create-failed event.
     */
    public void onFailedFlowMirrorPointCreate(String flowId, SwitchId srcSwitch, String direction,
                                              SwitchId destSwitch, int destPort, int destVlan, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-create-failed");
        data.put(TAG, "flow-mirror-point-create-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_CREATE_RESULT_EVENT);
        data.put("flow-mirror-point-create-result", FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed create a mirror point for the flow %s: source switch %s, "
                        + "destination endpoint %s_%d_%d, direction %s, reason: %s",
                flowId, srcSwitch, destSwitch, destPort, destVlan, direction, failureReason), data);
    }

    /**
     * Log a flow-mirror-point-delete event.
     */
    public void onFlowMirrorPointDelete(String flowId, String flowMirrorPointId) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-delete");
        data.put(TAG, "flow-mirror-point-delete");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_DELETE_EVENT);
        invokeLogger(Level.INFO, String.format("Delete the flow mirror point %s for the flow %s",
                flowMirrorPointId, flowId), data);
    }

    /**
     * Log a flow-delete-successful event.
     */
    public void onSuccessfulFlowMirrorPointDelete(String flowId, String flowMirrorPointId) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-delete-successful");
        data.put(TAG, "flow-mirror-point-delete-successful");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_DELETE_RESULT_EVENT);
        data.put(DELETE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful delete of the flow mirror point %s for the flow %s",
                flowMirrorPointId, flowId), data);
    }

    /**
     * Log a flow-delete-failed event.
     */
    public void onFailedFlowMirrorPointDelete(String flowId, String flowMirrorPointId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(DASHBOARD, "flow-mirror-point-delete-failed");
        data.put(TAG, "flow-mirror-point-delete-failed");
        data.put(FLOW_ID, flowId);
        data.put(EVENT_TYPE, FLOW_MIRROR_POINT_DELETE_RESULT_EVENT);
        data.put(DELETE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed delete of the flow mirror point %s for the flow %s, reason: %s",
                flowMirrorPointId, flowId, failureReason), data);
    }

    /**
     * Log a y-flow-create event.
     */
    public void onYFlowCreate(
            String yFlowId, FlowEndpoint sharedEndpoint, List<FlowEndpoint> subFlowEndpoints, long maximumBandwidth,
            PathComputationStrategy strategy, Long maxLatency, Long maxLatencyTier2) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-create");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create the y-flow: %s, shared endpoint %s, endpoints (%s), "
                        + "bandwidth %d, path computation strategy %s, max latency %s, max latency tier2 %s",
                yFlowId, sharedEndpoint, subFlowEndpoints, maximumBandwidth, strategy, maxLatency, maxLatencyTier2),
                data);
    }

    /**
     * Log a y-flow-create-successful event.
     */
    public void onSuccessfulYFlowCreate(String yFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-create-successful");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_CREATE_RESULT_EVENT);
        data.put("create-result", SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful create of the y-flow %s", yFlowId), data);
    }

    /**
     * Log a y-flow-create-failed event.
     */
    public void onFailedYFlowCreate(String yFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-create-failed");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_CREATE_RESULT_EVENT);
        data.put("create-result", FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed create of the y-flow %s, reason: %s", yFlowId, failureReason),
                data);
    }

    /**
     * Log a y-flow-update event.
     */
    public void onYFlowUpdate(
            String yFlowId, FlowEndpoint sharedEndpoint, List<FlowEndpoint> subFlowEndpoints, long maximumBandwidth,
            PathComputationStrategy strategy, Long maxLatency, Long maxLatencyTier2) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-update");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Update the y-flow: %s, shared endpoint %s, endpoints (%s), "
                + "bandwidth %d, path computation strategy %s, max latency %s, max latency tier2 %s",
                yFlowId, sharedEndpoint, subFlowEndpoints, maximumBandwidth, strategy, maxLatency, maxLatencyTier2),
                data);
    }

    /**
     * Log a y-flow-update-successful event.
     */
    public void onSuccessfulYFlowUpdate(String yFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-update-successful");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_UPDATE_RESULT_EVENT);
        data.put(UPDATE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful update of the y-flow %s", yFlowId), data);
    }

    /**
     * Log a y-flow-update-failed event.
     */
    public void onFailedYFlowUpdate(String yFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-update-failed");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_UPDATE_RESULT_EVENT);
        data.put(UPDATE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed update of the y-flow %s, reason: %s", yFlowId, failureReason),
                data);
    }

    /**
     * Log a y-flow-reroute event.
     */
    public void onYFlowReroute(String yFlowId, Collection<IslEndpoint> affectedIsl) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-reroute");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_REROUTE_EVENT);
        invokeLogger(Level.INFO, String.format("Reroute y-flow due to failure on %s ISLs flow %s",
                affectedIsl, yFlowId), data);
    }

    /**
     * Log a y-flow-reroute-successful event.
     */
    public void onSuccessfulYFlowReroute(String yFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-reroute-successful");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_REROUTE_RESULT_EVENT);
        data.put(REROUTE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful reroute of the y-flow %s", yFlowId), data);
    }

    /**
     * Log a y-flow-reroute-failed event.
     */
    public void onFailedYFlowReroute(String yFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-reroute-failed");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_REROUTE_RESULT_EVENT);
        data.put(REROUTE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed reroute of the y-flow %s, reason: %s", yFlowId, failureReason),
                data);
    }

    /**
     * Log a y-flow-status-update event.
     */
    public void onYFlowStatusUpdate(String yFlowId, FlowStatus status) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-status-update");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, STATUS_UPDATE_EVENT);
        data.put(STATUS, status.toString());
        invokeLogger(Level.INFO, String.format("Update the status of the y-flow %s to %s", yFlowId, status), data);
    }

    /**
     * Log a y-flow-delete event.
     */
    public void onYFlowDelete(String yFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-delete");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_DELETE_EVENT);
        invokeLogger(Level.INFO, String.format("Delete the y-flow: %s", yFlowId), data);
    }

    /**
     * Log a y-flow-delete-successful event.
     */
    public void onSuccessfulYFlowDelete(String yFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-delete-successful");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_DELETE_RESULT_EVENT);
        data.put(DELETE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful delete of the y-flow %s", yFlowId), data);
    }

    /**
     * Log a y-flow-delete-failed event.
     */
    public void onFailedYFlowDelete(String yFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-delete-failed");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_DELETE_RESULT_EVENT);
        data.put(DELETE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed delete of the y-flow %s, reason: %s", yFlowId, failureReason),
                data);
    }

    /**
     * Log a y-flow-paths-swap event.
     */
    public void onYFlowPathsSwap(String yFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-paths-swap");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_PATHS_SWAP_EVENT);
        invokeLogger(Level.INFO, String.format("Swap paths for the y-flow: %s", yFlowId), data);
    }

    /**
     * Log a y-flow-paths-swap-successful event.
     */
    public void onSuccessfulYFlowPathsSwap(String yFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-paths-swap-successful");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_PATHS_SWAP_RESULT_EVENT);
        data.put("swap-result", SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful path swap of the y-flow %s", yFlowId), data);
    }

    /**
     * Log a y-flow-paths-swap-failed event.
     */
    public void onFailedYFlowPathsSwap(String yFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "y-flow-paths-swap-failed");
        data.put(FLOW_ID, yFlowId);
        data.put(EVENT_TYPE, YFLOW_PATHS_SWAP_RESULT_EVENT);
        data.put("swap-result", FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed path swap of the y-flow %s, reason: %s", yFlowId, failureReason),
                data);
    }

    /**
     * Log a ha-flow-create event.
     */
    public void onHaFlowCreate(
            String haFlowId, FlowEndpoint sharedEndpoint, List<FlowEndpoint> subFlowEndpoints, long maximumBandwidth,
            PathComputationStrategy strategy, Long maxLatency, Long maxLatencyTier2) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-create");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_CREATE_EVENT);
        invokeLogger(Level.INFO, String.format("Create the ha-flow: %s, shared endpoint %s, endpoints (%s), "
                        + "bandwidth %d, path computation strategy %s, max latency %s, max latency tier2 %s",
                haFlowId, sharedEndpoint, subFlowEndpoints, maximumBandwidth, strategy, maxLatency,
                maxLatencyTier2), data);
    }

    /**
     * Log a y-flow-create-successful event.
     */
    public void onSuccessfulHaFlowCreate(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-create-successful");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_CREATE_RESULT_EVENT);
        data.put("create-result", SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful create of the ha-flow %s", haFlowId), data);
    }

    /**
     * Log a y-flow-create-failed event.
     */
    public void onFailedHaFlowCreate(String haFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-create-failed");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_CREATE_RESULT_EVENT);
        data.put("create-result", FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed create of the ha-flow %s, reason: %s", haFlowId, failureReason),
                data);
    }

    /**
     * Log an ha-flow-delete event.
     */
    public void onHaFlowDelete(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-delete");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_DELETE_EVENT);
        invokeLogger(Level.INFO, String.format("Delete the ha-flow %s", haFlowId), data);
    }

    /**
     * Log a ha-flow-delete-successful event.
     */
    public void onSuccessfulHaFlowDelete(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-delete-successful");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_DELETE_RESULT_EVENT);
        data.put(DELETE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful delete of the ha-flow %s", haFlowId), data);
    }

    /**
     * Log a flow-delete-failed event.
     */
    public void onFailedHaFlowDelete(String haFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-delete-failed");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_DELETE_RESULT_EVENT);
        data.put(DELETE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed delete of the ha-flow %s, reason: %s", haFlowId, failureReason),
                data);
    }

    /**
     * Log a ha-flow-status-update event.
     */
    public void onHaFlowStatusUpdate(String haFlowId, FlowStatus status) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-status-update");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, STATUS_UPDATE_EVENT);
        data.put(STATUS, status.toString());
        invokeLogger(Level.INFO, String.format("Update the status of the ha-flow %s to %s", haFlowId, status), data);
    }

    /**
     * Log a ha-flow-update event.
     */
    public void onHaFlowUpdate(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-update");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Update the ha-flow %s", haFlowId), data);
    }

    /**
     * Log a ha-flow-update-successful event.
     */
    public void onSuccessfulHaFlowUpdate(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-update-successful");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_UPDATE_RESULT_EVENT);
        data.put(UPDATE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful update of the ha-flow %s", haFlowId), data);
    }


    /**
     * Log a ha-flow-update-failed event.
     */
    public void onFailedHaFlowUpdate(String haFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-update-failed");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_UPDATE_RESULT_EVENT);
        data.put(UPDATE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed update of the ha-flow %s, reason: %s", haFlowId, failureReason),
                data);
    }

    /**
     * Log an HA-flow patch update event.
     */
    public void onHaFlowPatchUpdate(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-patch-update");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_UPDATE_EVENT);
        invokeLogger(Level.INFO, String.format("Patch update the HA-flow: %s", haFlowId), data);
    }

    /**
     * Log a flow-paths-swap event.
     */
    public void onHaFlowPathsSwap(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-paths-swap");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_PATH_SWAP_EVENT);
        invokeLogger(Level.INFO, String.format("Swap paths for the ha-flow: %s", haFlowId), data);
    }

    /**
     * Log a ha-paths-swap-successful event.
     */
    public void onSuccessfulHaFlowPathSwap(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-paths-swap-successful");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_PATH_SWAP_RESULT_EVENT);
        data.put(UPDATE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful update of the ha-flow %s", haFlowId), data);
    }

    /**
     * Log a ha-paths-swap-failed event.
     */
    public void onFailedHaFlowPathSwap(String haFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-paths-swap-failed");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, HA_FLOW_PATH_SWAP_RESULT_EVENT);
        data.put(UPDATE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(Level.WARN, String.format("Failed path swap for the ha-flow %s, reason: %s",
                        haFlowId, failureReason), data);
    }

    /**
     * Log an ha-flow-reroute event.
     */
    public void onHaFlowReroute(String haFlowId, Collection<IslEndpoint> affectedIsl) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-paths-reroute");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, REROUTE_EVENT);
        invokeLogger(Level.INFO, String.format(
                "Reroute due to failure on %s ISLs HA-flow %s", affectedIsl, haFlowId), data);
    }

    /**
     * Log a ha-flow-reroute-successful event.
     */
    public void onSuccessfulHaFlowReroute(String haFlowId) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-reroute-successful");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, REROUTE_RESULT_EVENT);
        data.put(REROUTE_RESULT, SUCCESSFUL_RESULT);
        invokeLogger(Level.INFO, String.format("Successful reroute of the HA-flow %s", haFlowId), data);
    }

    /**
     * Log a ha-flow-reroute-failed event.
     */
    public void onFailedHaFlowReroute(String haFlowId, String failureReason) {
        Map<String, String> data = new HashMap<>();
        data.put(TAG, "ha-flow-reroute-failed");
        data.put(FLOW_ID, haFlowId);
        data.put(EVENT_TYPE, REROUTE_RESULT_EVENT);
        data.put(REROUTE_RESULT, FAILED_RESULT);
        data.put(FAILURE_REASON, failureReason);
        invokeLogger(
                Level.WARN, String.format(
                        "Failed reroute of the HA-flow %s, reason: %s", haFlowId, failureReason), data);
    }
}
