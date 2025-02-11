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

package org.openkilda.wfm.topology.flowhs.service.yflow;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.SwitchFlowEntriesBuilder.BURST_COEFFICIENT;
import static org.openkilda.wfm.topology.flowhs.fsm.validation.SwitchFlowEntriesBuilder.MIN_BURST_SIZE_IN_KBITS;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.switches.DumpGroupsForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpMetersForFlowHsRequest;
import org.openkilda.messaging.command.switches.DumpRulesForFlowHsRequest;
import org.openkilda.messaging.command.yflow.YFlowValidationResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowDumpResponse;
import org.openkilda.messaging.info.group.GroupDumpResponse;
import org.openkilda.messaging.info.meter.MeterDumpResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.rulemanager.RuleManagerImpl;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.exception.DuplicateKeyException;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowSwitchFlowEntriesBuilder;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.validation.YFlowValidationService;
import org.openkilda.wfm.topology.flowhs.service.AbstractYFlowTest;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.FlowValidationHubService;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class YFlowValidationHubServiceTest extends AbstractYFlowTest<Pair<String, CommandData>> {
    @Mock
    private FlowValidationHubCarrier flowValidationHubCarrier;
    @Mock
    private YFlowValidationHubCarrier yFlowValidationHubCarrier;
    private static RuleManagerConfig ruleManagerConfig;

    @BeforeClass
    public static void setUpOnce() {
        ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
    }

    @Before
    public void init() {
        doAnswer(invocation ->
                requests.offer(Pair.of(invocation.getArgument(0), invocation.getArgument(1))))
                .when(flowValidationHubCarrier).sendSpeakerRequest(any(String.class), any(CommandData.class));
        doAnswer(invocation ->
                requests.offer(Pair.of(invocation.getArgument(0), invocation.getArgument(1))))
                .when(yFlowValidationHubCarrier).sendSpeakerRequest(any(String.class), any(CommandData.class));
    }

    @Test
    public void validateYFlowSuccessfully() throws DuplicateKeyException {
        // given
        String yFlowId = "test_y_flow_1";
        YFlow yFlow = createYFlowViaTransit(yFlowId);
        YFlowSwitchFlowEntriesBuilder flowEntriesBuilder = new YFlowSwitchFlowEntriesBuilder(yFlow,
                persistenceManager.getRepositoryFactory().createTransitVlanRepository(),
                persistenceManager.getRepositoryFactory().createVxlanRepository());
        Map<SwitchId, Collection<FlowSpeakerData>> flowEntries = flowEntriesBuilder.getFlowEntries();
        Map<SwitchId, Collection<MeterSpeakerData>> meterEntries = flowEntriesBuilder.getMeterEntries();
        Map<SwitchId, Collection<GroupSpeakerData>> groupEntries = flowEntriesBuilder.getGroupEntries();

        YFlowValidationHubService service = makeYFlowValidationHubService();
        service.handleRequest(yFlow.getYFlowId(), new CommandContext(), yFlow.getYFlowId());

        // when
        handleSpeakerRequests(service, yFlowId, flowEntries, meterEntries, groupEntries);

        //then
        verifyNorthboundSuccessResponse(yFlowValidationHubCarrier);
    }

    @Test
    public void failIfNoYFlowFound() throws DuplicateKeyException {
        // given
        String yFlowId = "fake_test_y_flow";

        YFlowValidationHubService service = makeYFlowValidationHubService();
        service.handleRequest(yFlowId, new CommandContext(), yFlowId);

        // when
        handleSpeakerRequests(service, yFlowId, emptyMap(), emptyMap(), emptyMap());

        //then
        verifyNorthboundErrorResponse(yFlowValidationHubCarrier, ErrorType.NOT_FOUND);
    }

    @Test
    public void validateAndFailIfSubFlowHasMissingRule() throws DuplicateKeyException {
        // given
        String yFlowId = "test_y_flow_1";
        YFlow yFlow = createYFlowViaTransit(yFlowId);
        YSubFlow failedSubFlow = yFlow.getSubFlows().stream().findFirst()
                .orElseThrow(IllegalStateException::new);
        Flow failedFlow = failedSubFlow.getFlow();
        YFlowSwitchFlowEntriesBuilder flowEntriesBuilder = new YFlowSwitchFlowEntriesBuilder(yFlow,
                persistenceManager.getRepositoryFactory().createTransitVlanRepository(),
                persistenceManager.getRepositoryFactory().createVxlanRepository());
        Map<SwitchId, Collection<FlowSpeakerData>> flowEntries = flowEntriesBuilder.getFlowEntries();
        flowEntries.forEach((s, f) ->
                f.removeIf(entry ->
                        entry.getCookie().getValue() == failedFlow.getForwardPath().getCookie().getValue()));
        Map<SwitchId, Collection<MeterSpeakerData>> meterEntries = flowEntriesBuilder.getMeterEntries();
        Map<SwitchId, Collection<GroupSpeakerData>> groupEntries = flowEntriesBuilder.getGroupEntries();

        YFlowValidationHubService service = makeYFlowValidationHubService();
        service.handleRequest(yFlow.getYFlowId(), new CommandContext(), yFlow.getYFlowId());

        // when
        handleSpeakerRequests(service, yFlowId, flowEntries, meterEntries, groupEntries);

        //then
        YFlowValidationResponse response = getNorthboundResponse(yFlowValidationHubCarrier);
        assertFalse(response.isAsExpected());
        assertTrue(response.getYFlowValidationResult().isAsExpected());
        response.getSubFlowValidationResults()
                .forEach(result ->
                        assertTrue(result.getFlowId().equals(failedFlow.getFlowId()) || result.getAsExpected()));
        assertEquals(1, response.getSubFlowValidationResults().stream().filter(r -> !r.getAsExpected()).count());
    }

    private void handleSpeakerRequests(YFlowValidationHubService service, String yFlowFsmKey,
                                       Map<SwitchId, Collection<FlowSpeakerData>> flowEntries,
                                       Map<SwitchId, Collection<MeterSpeakerData>> meterEntries,
                                       Map<SwitchId, Collection<GroupSpeakerData>> groupEntries) {
        handleSpeakerRequests(pair -> {
            CommandData commandData = pair.getValue();
            InfoData result = null;
            if (commandData instanceof DumpRulesForFlowHsRequest) {
                SwitchId switchId = ((DumpRulesForFlowHsRequest) commandData).getSwitchId();
                Collection<FlowSpeakerData> foundFlowEntries = flowEntries.get(switchId);
                result = FlowDumpResponse.builder()
                        .flowSpeakerData(foundFlowEntries != null ? new ArrayList<>(foundFlowEntries) : emptyList())
                        .switchId(switchId)
                        .build();
            } else if (commandData instanceof DumpMetersForFlowHsRequest) {
                SwitchId switchId = ((DumpMetersForFlowHsRequest) commandData).getSwitchId();
                Collection<MeterSpeakerData> foundMeterEntries = meterEntries.get(switchId);
                result = MeterDumpResponse.builder().switchId(switchId)
                        .meterSpeakerData(foundMeterEntries != null ? new ArrayList<>(foundMeterEntries) : emptyList())
                        .build();
            } else if (commandData instanceof DumpGroupsForFlowHsRequest) {
                SwitchId switchId = ((DumpGroupsForFlowHsRequest) commandData).getSwitchId();
                Collection<GroupSpeakerData> foundGroupEntries = groupEntries.get(switchId);
                result = GroupDumpResponse.builder()
                        .switchId(switchId)
                        .groupSpeakerData(foundGroupEntries != null ? new ArrayList<>(foundGroupEntries) : emptyList())
                        .build();
            } else {
                fail();
            }

            String flowId = pair.getKey();
            service.handleAsyncResponse(yFlowFsmKey, flowId, result);
        });
    }

    private YFlowValidationHubService makeYFlowValidationHubService() {
        FlowValidationHubService flowValidationHubService = new FlowValidationHubService(flowValidationHubCarrier,
                persistenceManager, new RuleManagerImpl(ruleManagerConfig));
        YFlowValidationService yFlowValidationService = new YFlowValidationService(persistenceManager,
                flowResourcesManager, MIN_BURST_SIZE_IN_KBITS, BURST_COEFFICIENT);
        return new YFlowValidationHubService(yFlowValidationHubCarrier, persistenceManager, flowValidationHubService,
                yFlowValidationService);
    }

    private void verifyNorthboundSuccessResponse(YFlowValidationHubCarrier carrierMock) {
        YFlowValidationResponse response = getNorthboundResponse(carrierMock);
        assertTrue(response.isAsExpected());
        assertTrue(response.getYFlowValidationResult().isAsExpected());
        response.getSubFlowValidationResults()
                .forEach(result -> assertTrue(result.getAsExpected()));
    }

    private YFlowValidationResponse getNorthboundResponse(YFlowValidationHubCarrier carrierMock) {
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);
        verify(carrierMock, times(1)).sendNorthboundResponse(responseCaptor.capture());

        Message rawResponse = responseCaptor.getValue();
        assertNotNull(rawResponse);
        assertTrue(rawResponse instanceof InfoMessage);

        InfoData rawPayload = ((InfoMessage) rawResponse).getData();
        assertTrue(rawPayload instanceof YFlowValidationResponse);
        return (YFlowValidationResponse) rawPayload;
    }
}
