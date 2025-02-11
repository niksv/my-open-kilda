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

package org.openkilda.rulemanager.factory.generator.flow;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.Switch;
import org.openkilda.model.cookie.CookieBase.CookieType;
import org.openkilda.model.cookie.PortColourCookie;
import org.openkilda.rulemanager.Constants.Priority;
import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.FlowSpeakerData.FlowSpeakerDataBuilder;
import org.openkilda.rulemanager.Instructions;
import org.openkilda.rulemanager.OfMetadata;
import org.openkilda.rulemanager.OfTable;
import org.openkilda.rulemanager.OfVersion;
import org.openkilda.rulemanager.ProtoConstants.EthType;
import org.openkilda.rulemanager.SpeakerData;
import org.openkilda.rulemanager.factory.RuleGenerator;
import org.openkilda.rulemanager.match.FieldMatch;
import org.openkilda.rulemanager.utils.RoutingMetadata;

import com.google.common.collect.Sets;
import lombok.Builder.Default;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuperBuilder
public class InputArpRuleGenerator implements RuleGenerator {

    @Default
    private final Set<FlowSideAdapter> overlappingIngressAdapters = new HashSet<>();
    private FlowEndpoint ingressEndpoint;
    private boolean multiTable;

    @Override
    public List<SpeakerData> generateCommands(Switch sw) {
        List<SpeakerData> result = new ArrayList<>();
        if (multiTable && ingressEndpoint.isTrackArpConnectedDevices()
                && overlappingIngressAdapters.stream().noneMatch(FlowSideAdapter::isDetectConnectedDevicesArp)) {
            result.add(buildArpInputCustomerFlowCommand(sw, ingressEndpoint));
        }
        return result;
    }

    private SpeakerData buildArpInputCustomerFlowCommand(Switch sw, FlowEndpoint endpoint) {
        RoutingMetadata metadata = RoutingMetadata.builder().arpFlag(true).build(sw.getFeatures());

        FlowSpeakerDataBuilder<?, ?> builder = FlowSpeakerData.builder()
                .switchId(endpoint.getSwitchId())
                .ofVersion(OfVersion.of(sw.getOfVersion()))
                .cookie(new PortColourCookie(CookieType.ARP_INPUT_CUSTOMER_TYPE, endpoint.getPortNumber()))
                .table(OfTable.INPUT)
                .priority(Priority.ARP_INPUT_CUSTOMER_PRIORITY)
                .match(Sets.newHashSet(
                        FieldMatch.builder().field(Field.IN_PORT).value(endpoint.getPortNumber()).build(),
                        FieldMatch.builder().field(Field.ETH_TYPE).value(EthType.ARP).build()))
                .instructions(Instructions.builder()
                        .goToTable(OfTable.PRE_INGRESS)
                        .writeMetadata(new OfMetadata(metadata.getValue(), metadata.getMask()))
                        .build());

        //todo add RESET_COUNTERS flag
        return builder.build();
    }
}
