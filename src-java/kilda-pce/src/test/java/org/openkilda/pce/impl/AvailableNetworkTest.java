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

package org.openkilda.pce.impl;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.Node;
import org.openkilda.pce.model.PathWeight;
import org.openkilda.pce.model.WeightFunction;

import com.google.common.collect.Sets;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

public class AvailableNetworkTest {
    private static final int COST = 700;

    private static final WeightFunction WEIGHT_FUNCTION = edge -> {
        long total = edge.getCost();
        if (edge.isUnderMaintenance()) {
            total += 10_000;
        }
        if (edge.isUnstable()) {
            total += 10_000;
        }
        total += edge.getDiversityGroupUseCounter() * 1000L
                + edge.getDiversityGroupPerPopUseCounter() * 1000L
                + edge.getDestSwitch().getDiversityGroupUseCounter() * 100L;
        return new PathWeight(total);
    };

    private static final SwitchId SRC_SWITCH = new SwitchId("00:00:00:22:3d:6c:00:b8");
    private static final SwitchId DST_SWITCH = new SwitchId("00:00:00:22:3d:5a:04:87");

    private static final HashSet<SwitchId> DUMMY_SWITCH_IDS = Sets.newHashSet(
            new SwitchId("1"), new SwitchId("2"));

    @Test
    public void dontAllowDuplicatesTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 20, 5);

        assertThat(network.getSwitch(SRC_SWITCH).getOutgoingLinks().size(), is(1));
        assertTrue(network.getSwitch(SRC_SWITCH).getIncomingLinks().isEmpty());
        assertTrue(network.getSwitch(DST_SWITCH).getOutgoingLinks().isEmpty());
        assertThat(network.getSwitch(DST_SWITCH).getIncomingLinks().size(), is(1));
    }

    @Test
    public void keepLinksWithOtherSwitchesAfterReducingTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, new SwitchId("00:00"), 1, 1, 20, 5);
        addLink(network, SRC_SWITCH, DST_SWITCH, 2, 2, 10, 3);
        addLink(network, DST_SWITCH, SRC_SWITCH, 1, 1, 20, 5);
        addLink(network, new SwitchId("00:00"), SRC_SWITCH, 2, 2, 10, 3);

        assertThat(network.getSwitch(SRC_SWITCH).getOutgoingLinks().size(), is(2));
        assertThat(network.getSwitch(SRC_SWITCH).getIncomingLinks().size(), is(2));
    }

    private static final SwitchId SWITCH_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId SWITCH_4 = new SwitchId("00:00:00:00:00:00:00:04");
    private static final SwitchId SWITCH_5 = new SwitchId("00:00:00:00:00:00:00:05");
    private static final String POP_1 = "pop1";
    private static final String POP_2 = "pop2";
    private static final String POP_3 = "pop3";
    private static final String POP_4 = "pop4";

    @Test
    public void updateEdgeWeightWithPopDiversityPenaltyTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SWITCH_1, SWITCH_2, 1, 2, COST, 5, POP_1, POP_2);
        addLink(network, SWITCH_1, SWITCH_3, 2, 1, COST, 5, POP_1, POP_4);
        addLink(network, SWITCH_1, SWITCH_4, 3, 1, COST, 5, POP_1, POP_4);
        addLink(network, SWITCH_5, SWITCH_4, 1, 2, COST, 5, POP_3, POP_4);
        addLink(network, SWITCH_5, SWITCH_3, 2, 2, COST, 5, POP_3, POP_4);
        addLink(network, SWITCH_5, SWITCH_2, 3, 2, COST, 5, POP_3, POP_2);

        network.processDiversitySegmentsWithPop(
                asList(buildPathWithSegment(SWITCH_1, SWITCH_3, 2, 1, POP_1, POP_4, 0),
                        buildPathWithSegment(SWITCH_3, SWITCH_5, 2, 2, POP_4, POP_3, 1)));
        long expectedWeight = COST + 1000L;
        for (Edge edge : network.edges) {
            long currentWeight = WEIGHT_FUNCTION.apply(edge).getTotalWeight();
            if (edge.getSrcSwitch().getPop().equals(POP_4)
                    || edge.getDestSwitch().getPop().equals(POP_4)) {
                assertEquals(expectedWeight, currentWeight);
            } else {
                assertEquals(COST, currentWeight);
            }
        }
    }

    @Test
    public void dontUpdateWeightsWhenTransitSegmentsNotInPopTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SWITCH_1, SWITCH_2, 1, 2, COST, 5, POP_1, POP_2);
        addLink(network, SWITCH_1, SWITCH_3, 2, 1, COST, 5, POP_1, null);
        addLink(network, SWITCH_1, SWITCH_4, 3, 1, COST, 5, POP_1, POP_4);
        addLink(network, SWITCH_5, SWITCH_4, 1, 2, COST, 5, POP_3, POP_4);
        addLink(network, SWITCH_5, SWITCH_3, 2, 2, COST, 5, POP_3, null);
        addLink(network, SWITCH_5, SWITCH_2, 3, 2, COST, 5, POP_3, POP_2);

        network.processDiversitySegmentsWithPop(
                asList(buildPathWithSegment(SWITCH_1, SWITCH_3, 2, 1, POP_1, null, 0),
                        buildPathWithSegment(SWITCH_3, SWITCH_5, 2, 2, null, POP_3, 1)));
        for (Edge edge : network.edges) {
            long currentWeight = WEIGHT_FUNCTION.apply(edge).getTotalWeight();
            assertEquals(COST, currentWeight);
        }
    }

    @Test
    public void dontUpdateWeightsWhenTransitSegmentsOnlyInPopTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SWITCH_1, SWITCH_2, 1, 2, COST, 5, null, null);
        addLink(network, SWITCH_1, SWITCH_3, 2, 1, COST, 5, null, POP_4);
        addLink(network, SWITCH_1, SWITCH_4, 3, 1, COST, 5, null, null);
        addLink(network, SWITCH_5, SWITCH_4, 1, 2, COST, 5, null, null);
        addLink(network, SWITCH_5, SWITCH_3, 2, 2, COST, 5, null, POP_4);
        addLink(network, SWITCH_5, SWITCH_2, 3, 2, COST, 5, null, null);

        network.processDiversitySegmentsWithPop(
                asList(buildPathWithSegment(SWITCH_1, SWITCH_3, 2, 1, POP_1, null, 0),
                        buildPathWithSegment(SWITCH_3, SWITCH_5, 2, 2, null, POP_3, 1)));
        for (Edge edge : network.edges) {
            long currentWeight = WEIGHT_FUNCTION.apply(edge).getTotalWeight();
            assertEquals(COST, currentWeight);
        }
    }

    @Test
    public void dontUpdateEdgeWeightWithPopDiversityPenaltyIfNoPopTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SWITCH_1, SWITCH_2, 1, 2, COST, 5);
        addLink(network, SWITCH_1, SWITCH_3, 2, 1, COST, 5);
        addLink(network, SWITCH_1, SWITCH_4, 3, 1, COST, 5);
        addLink(network, SWITCH_5, SWITCH_4, 1, 2, COST, 5);
        addLink(network, SWITCH_5, SWITCH_3, 2, 2, COST, 5);
        addLink(network, SWITCH_5, SWITCH_2, 3, 2, COST, 5);

        network.processDiversitySegmentsWithPop(
                asList(buildPathSegment(SWITCH_1, SWITCH_3, 2, 1, 0),
                        buildPathSegment(SWITCH_3, SWITCH_5, 2, 2, 1)));
        for (Edge edge : network.edges) {
            long currentWeight = WEIGHT_FUNCTION.apply(edge).getTotalWeight();
            assertEquals(COST, currentWeight);
        }
    }

    @Test
    public void setEqualCostForPairedLinksTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);
        addLink(network, DST_SWITCH, SRC_SWITCH,
                60, 7, 20, 3);

        Node srcSwitch = network.getSwitch(SRC_SWITCH);
        Node dstSwitch = network.getSwitch(DST_SWITCH);

        Set<Edge> outgoingLinks = srcSwitch.getOutgoingLinks();
        assertThat(outgoingLinks, Matchers.hasSize(1));
        Edge outgoingIsl = outgoingLinks.iterator().next();
        assertEquals(outgoingIsl.getDestSwitch(), dstSwitch);
        assertEquals(10, outgoingIsl.getCost());

        Set<Edge> incomingLinks = srcSwitch.getIncomingLinks();
        assertThat(incomingLinks, Matchers.hasSize(1));
        Edge incomingIsl = incomingLinks.iterator().next();
        assertEquals(incomingIsl.getSrcSwitch(), dstSwitch);
        assertEquals(20, incomingIsl.getCost());
    }

    @Test
    public void createSymmetricOutgoingAndIncomingLinksTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);

        Node srcSwitch = network.getSwitch(SRC_SWITCH);
        Node dstSwitch = network.getSwitch(DST_SWITCH);

        Set<Edge> outgoingLinks = srcSwitch.getOutgoingLinks();
        assertThat(outgoingLinks, Matchers.hasSize(1));
        Edge outgoingIsl = outgoingLinks.iterator().next();
        assertEquals(dstSwitch, outgoingIsl.getDestSwitch());

        Set<Edge> incomingLinks = dstSwitch.getIncomingLinks();
        assertThat(incomingLinks, Matchers.hasSize(1));
        Edge incomingIsl = incomingLinks.iterator().next();
        assertEquals(srcSwitch, incomingIsl.getSrcSwitch());
    }

    @Test
    public void fillDiversityWeightsIngressTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);
        network.processDiversitySegments(
                singletonList(buildPathSegment(SRC_SWITCH, DST_SWITCH, 7, 60, 0)),
                DUMMY_SWITCH_IDS);

        Node srcSwitch = network.getSwitch(SRC_SWITCH);

        Edge edge = srcSwitch.getOutgoingLinks().iterator().next();
        assertEquals(1, edge.getDiversityGroupUseCounter());
        assertEquals(1, edge.getDestSwitch().getDiversityGroupUseCounter());
    }

    @Test
    public void fillEmptyDiversityWeightsForTerminatingSwitchTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH, 7, 60, 10, 3);

        network.processDiversitySegments(
                singletonList(buildPathSegment(SRC_SWITCH, DST_SWITCH, 7, 60, 0)),
                Sets.newHashSet(SRC_SWITCH, DST_SWITCH));

        Node srcSwitch = network.getSwitch(SRC_SWITCH);

        Edge edge = srcSwitch.getOutgoingLinks().iterator().next();
        assertEquals(1, edge.getDiversityGroupUseCounter());
        assertEquals(0, edge.getDestSwitch().getDiversityGroupUseCounter());
        assertEquals(0, edge.getSrcSwitch().getDiversityGroupUseCounter());
    }

    @Test
    public void fillDiversityWeightsTransitTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);
        network.processDiversitySegments(
                singletonList(buildPathSegment(SRC_SWITCH, DST_SWITCH, 7, 60, 1)),
                DUMMY_SWITCH_IDS);

        Node srcSwitch = network.getSwitch(SRC_SWITCH);

        Edge edge = srcSwitch.getOutgoingLinks().iterator().next();
        assertEquals(1, edge.getDiversityGroupUseCounter());
        assertEquals(1, edge.getDestSwitch().getDiversityGroupUseCounter());
    }

    @Test
    public void fillAffinityWeightsTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH, 7, 60, 10, 3);
        addLink(network, SRC_SWITCH, DST_SWITCH, 8, 61, 10, 3);
        addLink(network, SRC_SWITCH, DST_SWITCH, 9, 62, 10, 3);
        network.processAffinitySegments(
                singletonList(buildPathSegment(SRC_SWITCH, DST_SWITCH, 7, 60, 0)));

        Node srcSwitch = network.getSwitch(SRC_SWITCH);

        for (Edge edge : srcSwitch.getOutgoingLinks()) {
            if (edge.getSrcPort() == 7) {
                assertEquals(0, edge.getAffinityGroupUseCounter());
                continue;
            }
            assertEquals(1, edge.getAffinityGroupUseCounter());
        }
    }

    /*
        A = B - C = D
     */
    @Test
    public void fillDiversityWeightsPartiallyConnectedTest() {
        SwitchId switchA = new SwitchId("A");
        SwitchId switchB = new SwitchId("B");
        SwitchId switchC = new SwitchId("C");
        SwitchId switchD = new SwitchId("D");
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, switchA, switchB, 1, 1, 10, 3);
        addLink(network, switchB, switchC, 2, 2, 10, 3);
        addLink(network, switchC, switchD, 3, 3, 10, 3);
        network.processDiversitySegments(asList(
                buildPathSegment(switchA, switchB, 1, 1, 0),
                buildPathSegment(switchC, switchD, 3, 3, 0)), DUMMY_SWITCH_IDS);

        Node nodeB = network.getSwitch(switchB);

        Edge edge = nodeB.getOutgoingLinks().stream()
                .filter(link -> link.getDestSwitch().getSwitchId().equals(switchC))
                .findAny().orElseThrow(() -> new IllegalStateException("Link 'B-C' not found"));
        assertEquals(0, edge.getDiversityGroupUseCounter());
        assertEquals(1, edge.getDestSwitch().getDiversityGroupUseCounter());
    }

    @Test
    public void processAbsentDiversitySegmentTest() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);
        network.processDiversitySegments(
                singletonList(buildPathSegment(SRC_SWITCH, DST_SWITCH, 1, 2, 0)), DUMMY_SWITCH_IDS);

        Node srcSwitch = network.getSwitch(SRC_SWITCH);

        Edge edge = srcSwitch.getOutgoingLinks().iterator().next();
        assertEquals(0, edge.getDiversityGroupUseCounter());
        // as switches are in AvailableNetwork
        assertEquals(1, edge.getDestSwitch().getDiversityGroupUseCounter());
        assertEquals(1, edge.getSrcSwitch().getDiversityGroupUseCounter());
    }

    @Test
    public void processDiversityGroupForSingleSwitchFlowTest() {
        AvailableNetwork network = new AvailableNetwork();

        Switch srcSwitch = Switch.builder().switchId(SWITCH_1).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        FlowPath flowPath = FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .destSwitch(srcSwitch)
                .segments(Collections.emptyList())
                .build();

        addLink(network, SWITCH_1, SWITCH_1,
                7, 60, 10, 3);

        network.processDiversityGroupForSingleSwitchFlow(flowPath);

        Node node = network.getSwitch(SWITCH_1);

        assertEquals(1, node.getDiversityGroupUseCounter());

        Edge edge = node.getOutgoingLinks().iterator().next();
        assertEquals(0, edge.getDiversityGroupUseCounter());
        assertEquals(1, edge.getDestSwitch().getDiversityGroupUseCounter());
        assertEquals(1, edge.getSrcSwitch().getDiversityGroupUseCounter());
    }

    private void addLink(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                         int cost, int latency) {
        addLink(network, srcDpid, dstDpid, srcPort, dstPort, cost, latency, null, null);
    }

    private void addLink(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                         int cost, int latency, String srcPop, String dstPop) {
        Edge edge = Edge.builder()
                .srcSwitch(network.getOrAddNode(srcDpid, srcPop))
                .srcPort(srcPort)
                .destSwitch(network.getOrAddNode(dstDpid, dstPop))
                .destPort(dstPort)
                .latency(latency)
                .cost(cost)
                .availableBandwidth(500000)
                .underMaintenance(false)
                .unstable(false)
                .build();
        network.addEdge(edge);
    }

    private PathSegment buildPathSegment(SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort, int seqId) {
        return buildPathWithSegment(srcDpid, dstDpid, srcPort, dstPort, null, null, seqId);
    }

    private PathSegment buildPathWithSegment(SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                                             String srcPop, String dstPop, int seqId) {
        Switch srcSwitch = Switch.builder().switchId(srcDpid).pop(srcPop).build();
        Switch dstSwitch = Switch.builder().switchId(dstDpid).pop(dstPop).build();

        PathId pathId = new PathId(UUID.randomUUID().toString());
        FlowPath flowPath = FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .segments(IntStream.rangeClosed(0, seqId)
                        .mapToObj(i -> PathSegment.builder().pathId(pathId)
                                .srcSwitch(srcSwitch).destSwitch(dstSwitch).srcPort(srcPort).destPort(dstPort).build())
                        .collect(toList()))
                .build();

        return flowPath.getSegments().get(seqId);
    }
}
