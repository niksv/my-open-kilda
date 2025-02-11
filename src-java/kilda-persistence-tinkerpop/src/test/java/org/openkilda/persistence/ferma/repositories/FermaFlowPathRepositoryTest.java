/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.ferma.repositories;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaFlowPath;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildPath;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.YFlowRepository;

import com.google.common.collect.Sets;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FermaFlowPathRepositoryTest extends InMemoryGraphBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final String TEST_FLOW_ID_1 = "test_flow_1";
    static final String TEST_FLOW_ID_2 = "test_flow_2";
    static final String TEST_FLOW_ID_3 = "test_flow_3";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);
    static final String GROUP_1 = "group_1";
    static final String GROUP_2 = "group_2";
    static final int PORT_1 = 1;
    static final int PORT_2 = 2;
    static final int PORT_3 = 3;
    public static final int VLAN_1 = 3;
    public static final int VLAN_2 = 4;
    private static final FlowSegmentCookie COOKIE_1 = FlowSegmentCookie.builder()
            .flowEffectiveId(1).direction(FlowPathDirection.FORWARD).build();
    private static final FlowSegmentCookie COOKIE_2 = FlowSegmentCookie.builder()
            .flowEffectiveId(2).direction(FlowPathDirection.FORWARD).build();

    FlowPathRepository flowPathRepository;
    FlowRepository flowRepository;
    HaFlowRepository haFlowRepository;
    HaSubFlowRepository haSubFlowRepository;
    HaFlowPathRepository haFlowPathRepository;
    YFlowRepository yFlowRepository;
    SwitchRepository switchRepository;

    Switch switchA;
    Switch switchB;
    Switch switchC;
    Flow flow;

    @Before
    public void setUp() {
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        haFlowRepository = repositoryFactory.createHaFlowRepository();
        haSubFlowRepository = repositoryFactory.createHaSubFlowRepository();
        haFlowPathRepository = repositoryFactory.createHaFlowPathRepository();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        switchRepository = repositoryFactory.createSwitchRepository();

        switchA = createTestSwitch(TEST_SWITCH_A_ID.getId());
        switchB = createTestSwitch(TEST_SWITCH_B_ID.getId());
        switchC = createTestSwitch(TEST_SWITCH_C_ID.getId());

        assertEquals(3, switchRepository.findAll().size());

        flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .build();
        flowRepository.add(flow);
    }

    @Test
    public void shouldCreateFlowPaths() {
        createTestFlowPathPair();

        Collection<FlowPath> allPaths = flowPathRepository.findAll();
        assertThat(allPaths, hasSize(2));

        FlowPath foundForwardPath = flowPathRepository.findById(flow.getForwardPathId()).get();
        assertEquals(switchA.getSwitchId(), foundForwardPath.getSrcSwitchId());
        assertEquals(switchB.getSwitchId(), foundForwardPath.getDestSwitchId());

        Flow foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));

        assertEquals(foundFlow.getFlowId(), foundForwardPath.getFlow().getFlowId());
    }

    @Test
    public void shouldCreateFlowWithPaths() {
        createTestFlowPathPair();

        Collection<FlowPath> allPaths = flowPathRepository.findAll();
        assertThat(allPaths, hasSize(2));

        FlowPath foundForwardPath = flowPathRepository.findById(flow.getForwardPathId()).get();
        assertEquals(switchA.getSwitchId(), foundForwardPath.getSrcSwitchId());
        assertEquals(switchB.getSwitchId(), foundForwardPath.getDestSwitchId());

        Flow foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));

        assertEquals(foundFlow.getFlowId(), foundForwardPath.getFlow().getFlowId());
    }

    @Test
    public void shouldFlowPathUpdateKeepRelations() {
        createTestFlowPathPair();

        Flow foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));

        FlowPath foundPath = flowPathRepository.findById(flow.getForwardPathId()).get();
        foundPath.setStatus(FlowPathStatus.INACTIVE);

        foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));
    }

    @Test
    public void shouldFlowPathUpdateKeepFlowRelations() {
        createTestFlowPathPair();

        Flow foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));

        FlowPath flowPath = foundFlow.getPaths().stream()
                .filter(path -> path.getPathId().equals(flow.getReversePathId()))
                .findAny().get();
        flowPath.setStatus(FlowPathStatus.INACTIVE);

        foundFlow = flowRepository.findById(TEST_FLOW_ID).get();
        assertThat(foundFlow.getPaths(), hasSize(2));
    }

    @Test
    public void shouldDeleteFlowPath() {
        FlowPath flowPath = createTestFlowPath();

        transactionManager.doInTransaction(() ->
                flowPathRepository.remove(flowPath));

        assertEquals(0, flowPathRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowPath() {
        createTestFlowPath();

        transactionManager.doInTransaction(() -> {
            Collection<FlowPath> allPaths = flowPathRepository.findAll();
            FlowPath foundPath = allPaths.iterator().next();
            flowPathRepository.remove(foundPath);
        });

        assertEquals(0, flowPathRepository.findAll().size());
    }

    @Test
    public void shouldFindPathById() {
        FlowPath flowPath = createTestFlowPath();

        Optional<FlowPath> foundPath = flowPathRepository.findById(flowPath.getPathId());
        assertTrue(foundPath.isPresent());
    }

    @Test
    public void shouldFindPathByFlowIdAndCookie() {
        FlowPath flowPath = createTestFlowPath();

        Optional<FlowPath> foundPath = flowPathRepository.findByFlowIdAndCookie(TEST_FLOW_ID, flowPath.getCookie());
        assertTrue(foundPath.isPresent());
    }

    @Test
    public void shouldFindByEndpointSwitch() {
        createTestFlowPathPair();

        Collection<FlowPath> paths = flowPathRepository.findByEndpointSwitch(switchA.getSwitchId());
        assertThat(paths, containsInAnyOrder(flow.getForwardPath(), flow.getReversePath()));
    }

    @Test
    public void shouldNotFindProtectedIngressByEndpointSwitch() {
        createTestFlowPathPair();
        FlowPath protect = createFlowPath(flow, "_protectedpath", 10, 10, switchA, switchB);
        flow.setProtectedForwardPath(protect);

        Collection<FlowPath> paths = flowPathRepository.findByEndpointSwitch(switchA.getSwitchId());
        assertThat(paths, containsInAnyOrder(flow.getForwardPath(), flow.getReversePath()));
    }

    @Test
    public void shouldFindProtectedPathsByEndpointSwitchIncludeProtected() {
        createTestFlowPathPair();
        flow.setProtectedForwardPath(createFlowPath(flow, "_forward_protected", 10, 10, switchA, switchB));
        flow.setProtectedReversePath(createFlowPath(flow, "_reverse_protected", 11, 11, switchB, switchA));

        Collection<FlowPath> paths = flowPathRepository.findByEndpointSwitch(switchA.getSwitchId(), true);
        assertThat(paths, containsInAnyOrder(flow.getForwardPath(), flow.getReversePath(),
                flow.getProtectedForwardPath(), flow.getProtectedReversePath()));
    }

    @Test
    public void shouldFindProtectedPathsBySrcSwitchIncludeProtected() {
        createTestFlowPathPair();
        flow.setProtectedForwardPath(createFlowPath(flow, "_forward_protected", 10, 10, switchA, switchB));
        flow.setProtectedReversePath(createFlowPath(flow, "_reverse_protected", 11, 11, switchB, switchA));

        assertThat(flowPathRepository.findBySrcSwitch(switchA.getSwitchId(), true),
                containsInAnyOrder(flow.getForwardPath(), flow.getProtectedForwardPath()));
        assertThat(flowPathRepository.findBySrcSwitch(switchB.getSwitchId(), true),
                containsInAnyOrder(flow.getReversePath(), flow.getProtectedReversePath()));
    }

    @Test
    public void findHaProtectedPathsBySrcSwitchIncludeProtected() {
        HaFlow haFlow = createHaFlow(HA_FLOW_ID_1, switchA, switchB, switchC, null, null);
        Collection<FlowPath> foundSubPaths = flowPathRepository.findBySrcSwitch(switchA.getSwitchId(), true);

        assertEquals(2, foundSubPaths.size());
        assertEquals(new HashSet<>(haFlow.getSubPathIds()), getSubPathIds(foundSubPaths));
    }

    @Test
    public void findHaProtectedPathsBySrcSwitchNotIncludeProtected() {
        HaFlow haFlow = createHaFlow(HA_FLOW_ID_1, switchA, switchB, switchC, null, null);
        Collection<FlowPath> foundSubPaths = flowPathRepository.findBySrcSwitch(switchA.getSwitchId(), false);

        assertEquals(1, foundSubPaths.size());
        assertEquals(getSubPathIds(haFlow.getForwardPath().getSubPaths()), getSubPathIds(foundSubPaths));
    }

    @Test
    public void shouldFindBySrcSwitch() {
        createTestFlowPathPair();

        Collection<FlowPath> paths = flowPathRepository.findBySrcSwitch(switchA.getSwitchId());
        assertThat(paths, containsInAnyOrder(flow.getForwardPath()));
    }

    @Test
    public void shouldFindFlowPathsForIsl() {
        FlowPath flowPath = createTestFlowPathWithIntermediate(switchC, 100);
        flow.setForwardPath(flowPath);

        Collection<FlowPath> paths = flowPathRepository.findWithPathSegment(switchA.getSwitchId(), 1,
                switchC.getSwitchId(), 100);
        assertThat(paths, Matchers.hasSize(1));
        assertThat(paths, containsInAnyOrder(flow.getForwardPath()));
    }

    @Test
    public void shouldFindActiveAffectedPaths() {
        FlowPath flowPath = createTestFlowPathWithIntermediate(switchC, 100);
        flow.setForwardPath(flowPath);

        Collection<FlowPath> paths = flowPathRepository.findBySegmentEndpoint(
                switchC.getSwitchId(), 100);
        assertThat(paths, containsInAnyOrder(flowPath));
    }

    @Test
    public void shouldFindPathByFlowId() {
        createTestFlowPath();

        Collection<FlowPath> foundPaths = flowPathRepository.findByFlowId(TEST_FLOW_ID);
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldFindPathBySrc() {
        createTestFlowPath();

        Collection<FlowPath> foundPaths = flowPathRepository.findBySrcSwitch(switchA.getSwitchId());
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldNotFindPathByWrongSrc() {
        createTestFlowPath();

        Collection<FlowPath> foundPaths = flowPathRepository.findBySrcSwitch(switchB.getSwitchId());
        assertThat(foundPaths, Matchers.empty());
    }

    @Test
    public void shouldFindPathByEndpointSwitch() {
        createTestFlowPath();

        Collection<FlowPath> foundPaths = flowPathRepository.findByEndpointSwitch(switchB.getSwitchId());
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldFindPathBySegmentSwitch() {
        FlowPath flowPath = createTestFlowPathWithIntermediate(switchC, 100);
        flow.setForwardPath(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentSwitch(switchC.getSwitchId());
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldFindInactivePathBySegmentSwitch() {
        Flow activeFlow = Flow.builder()
                .flowId("active flow")
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .status(FlowStatus.UP)
                .build();
        flowRepository.add(activeFlow);

        FlowPath activeFlowPath = createFlowPath(activeFlow, "active", 100L, 200L, switchA, switchB);
        activeFlow.addPaths(activeFlowPath);
        activeFlowPath.getFlow().setStatus(FlowStatus.DOWN);

        FlowPath expectedFlowPath = createTestFlowPathWithIntermediate(switchC, 100);
        activeFlow.addPaths(expectedFlowPath);
        expectedFlowPath.getFlow().setStatus(FlowStatus.DOWN);

        Collection<FlowPath> foundPaths = flowPathRepository.findInactiveBySegmentSwitch(switchA.getSwitchId());
        assertThat(foundPaths, hasSize(1));
        FlowPath actualFlowPath = foundPaths.stream().findFirst().orElse(null);
        assertEquals(expectedFlowPath, actualFlowPath);
    }

    @Test
    public void shouldFindPathBySegmentDestSwitch() {
        FlowPath flowPath = createTestFlowPathWithIntermediate(switchC, 100);
        flow.setForwardPath(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentDestSwitch(switchC.getSwitchId());
        assertThat(foundPaths, hasSize(1));
    }

    @Test
    public void shouldNotFindPathByWrongSegmentDestSwitch() {
        FlowPath flowPath = createTestFlowPathWithIntermediate(switchC, 100);
        flow.setForwardPath(flowPath);

        Collection<FlowPath> foundPaths = flowPathRepository.findBySegmentDestSwitch(switchA.getSwitchId());
        assertThat(foundPaths, Matchers.empty());
    }

    @Test
    public void shouldKeepSegmentsOrdered() {
        FlowPath flowPath = createTestFlowPath();

        List<PathSegment> segments = asList(PathSegment.builder()
                        .pathId(flowPath.getPathId())
                        .srcSwitch(switchA)
                        .destSwitch(switchC)
                        .build(),
                PathSegment.builder()
                        .pathId(flowPath.getPathId())
                        .srcSwitch(switchC)
                        .destSwitch(switchB)
                        .build());
        flowPath.setSegments(segments);

        Optional<FlowPath> foundPath = flowPathRepository.findById(flowPath.getPathId());
        assertEquals(foundPath.get().getSegments().get(0).getDestSwitchId(), switchC.getSwitchId());
    }

    @Test
    public void shouldFindFlowPathIdsByFlowIds() {
        Flow flowA = buildTestProtectedFlow(TEST_FLOW_ID_1, switchA, PORT_1, VLAN_1, switchB, PORT_2, VLAN_2);
        flowRepository.add(flowA);
        Flow flowB = buildTestFlow(TEST_FLOW_ID_2, switchA, PORT_1, VLAN_2, switchB, PORT_2, 0);
        flowRepository.add(flowB);
        Flow flowC = buildTestProtectedFlow(TEST_FLOW_ID_3, switchB, PORT_1, VLAN_1, switchB, PORT_3, VLAN_1);
        flowRepository.add(flowC);

        Collection<PathId> pathIds =
                flowPathRepository.findActualPathIdsByFlowIds(Sets.newHashSet(TEST_FLOW_ID_1, TEST_FLOW_ID_2));
        assertEquals(6, pathIds.size());
        assertTrue(pathIds.contains(flowA.getForwardPathId()));
        assertTrue(pathIds.contains(flowA.getReversePathId()));
        assertTrue(pathIds.contains(flowA.getProtectedForwardPathId()));
        assertTrue(pathIds.contains(flowA.getProtectedReversePathId()));
        assertTrue(pathIds.contains(flowB.getForwardPathId()));
        assertTrue(pathIds.contains(flowB.getReversePathId()));
    }

    @Test
    public void findPathIdsBySharedBandwidthGroupId() {
        String sharedBandwidthGroupId = "shared-bw-gr-id";

        Flow flowA = buildTestFlow(TEST_FLOW_ID_1, switchA, PORT_1, VLAN_2, switchB, PORT_2, 0,
                sharedBandwidthGroupId);
        flowRepository.add(flowA);
        Flow flowB = buildTestFlow(TEST_FLOW_ID_2, switchB, PORT_1, VLAN_2, switchC, PORT_2, 0);
        flowRepository.add(flowB);

        Collection<PathId> pathIds = flowPathRepository.findPathIdsBySharedBandwidthGroupId(sharedBandwidthGroupId);
        assertEquals(2, pathIds.size());
        assertTrue(pathIds.contains(flowA.getForwardPathId()));
        assertTrue(pathIds.contains(flowA.getReversePathId()));
    }
    
    @Test
    public void findPathIdsByFlowDiverseGroupIdTest() {
        Flow flowA = createFlow(TEST_FLOW_ID_1, switchA, switchB, GROUP_1, null);
        createFlow(TEST_FLOW_ID_2, switchA, switchB, GROUP_2, null);
        createFlow(TEST_FLOW_ID_3, switchA, switchB, null, null);
        HaFlow haFlowA = createHaFlow(HA_FLOW_ID_1, switchA, switchB, switchC, GROUP_1, null);
        createHaFlow(HA_FLOW_ID_2, switchA, switchB, switchC, GROUP_2, null);
        createHaFlow(HA_FLOW_ID_3, switchA, switchB, switchC, null, null);
        YFlow yFlowA = createYFlow(Y_FLOW_ID_1, switchA, switchB, switchC, GROUP_1, null);
        createYFlow(Y_FLOW_ID_2, switchA, switchB, switchC, GROUP_2, null);
        createYFlow(Y_FLOW_ID_3, switchA, switchB, switchC, null, null);

        Collection<PathId> actualPaths = flowPathRepository.findPathIdsByFlowDiverseGroupId(GROUP_1);
        assertEquals(8, actualPaths.size());
        assertEquals(collectPathIds(flowA, haFlowA, yFlowA), new HashSet<>(actualPaths));
    }

    @Test
    public void findPathIdsByFlowAffinityGroupIdTest() {
        Flow flowA = createFlow(TEST_FLOW_ID_1, switchA, switchB, null, GROUP_1);
        createFlow(TEST_FLOW_ID_2, switchA, switchB, null, GROUP_2);
        createFlow(TEST_FLOW_ID_3, switchA, switchB, null, null);
        HaFlow haFlowA = createHaFlow(HA_FLOW_ID_1, switchA, switchB, switchC, null, GROUP_1);
        createHaFlow(HA_FLOW_ID_2, switchA, switchB, switchC, null, GROUP_2);
        createHaFlow(HA_FLOW_ID_3, switchA, switchB, switchC, null, null);
        YFlow yFlowA = createYFlow(Y_FLOW_ID_1, switchA, switchB, switchC, null, GROUP_1);
        createYFlow(Y_FLOW_ID_2, switchA, switchB, switchC, null, GROUP_2);
        createYFlow(Y_FLOW_ID_3, switchA, switchB, switchC, null, null);

        Collection<PathId> actualPaths = flowPathRepository.findPathIdsByFlowAffinityGroupId(GROUP_1);
        assertEquals(8, actualPaths.size());
        assertEquals(collectPathIds(flowA, haFlowA, yFlowA), new HashSet<>(actualPaths));
    }

    private Set<PathId> collectPathIds(Flow flow, HaFlow haFlow, YFlow yFlow) {
        Set<PathId> pathIds = getSubPathIds(flow.getPaths());
        haFlow.getPaths().stream()
                .flatMap(p -> p.getSubPaths().stream())
                .map(FlowPath::getPathId)
                .forEach(pathIds::add);
        yFlow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .flatMap(s -> s.getPaths().stream())
                .map(FlowPath::getPathId)
                .forEach(pathIds::add);
        return pathIds;
    }

    @Test
    public void createFlowPathWithHaSubFlowTest() {
        FlowPath path = createFlowPath(PATH_ID_1, 1, 1, switchA, switchB);
        HaSubFlow haSubFlow = FermaModelUtils.buildHaSubFlow(SUB_FLOW_ID_1, switchA, 1, 2, 3, null);
        haSubFlowRepository.add(haSubFlow);
        path.setHaSubFlow(haSubFlow);

        Optional<FlowPath> foundPath = flowPathRepository.findById(PATH_ID_1);
        assertTrue(foundPath.isPresent());
        assertEquals(PATH_ID_1, foundPath.get().getPathId());
        assertEquals(SUB_FLOW_ID_1, foundPath.get().getHaSubFlowId());
        assertEquals(haSubFlow, foundPath.get().getHaSubFlow());
    }

    @Test
    public void createFlowPathWithoutHaSubFlowTest() {
        createFlowPath(PATH_ID_1, 1, 1, switchA, switchB);
        Optional<FlowPath> foundPath = flowPathRepository.findById(PATH_ID_1);
        assertTrue(foundPath.isPresent());
        assertEquals(PATH_ID_1, foundPath.get().getPathId());
        assertNull(foundPath.get().getHaSubFlowId());
        assertNull(foundPath.get().getHaSubFlow());
    }

    @Test
    public void removeHaSubFlowFromFlowPathTest() {
        FlowPath path = createFlowPath(PATH_ID_1, 1, 1, switchA, switchB);
        HaSubFlow haSubFlow = FermaModelUtils.buildHaSubFlow(SUB_FLOW_ID_1, switchA, 1, 2, 3, null);
        haSubFlowRepository.add(haSubFlow);
        path.setHaSubFlow(haSubFlow);

        Optional<FlowPath> foundPath = flowPathRepository.findById(PATH_ID_1);
        assertTrue(foundPath.isPresent());
        assertEquals(PATH_ID_1, foundPath.get().getPathId());
        assertEquals(SUB_FLOW_ID_1, foundPath.get().getHaSubFlowId());
        assertEquals(haSubFlow, foundPath.get().getHaSubFlow());

        foundPath.get().setHaSubFlow(null);
        Optional<FlowPath> foundPathWithoutHaSubFlow = flowPathRepository.findById(PATH_ID_1);
        assertTrue(foundPathWithoutHaSubFlow.isPresent());
        assertEquals(PATH_ID_1, foundPathWithoutHaSubFlow.get().getPathId());
        assertNull(foundPath.get().getHaSubFlowId());
        assertNull(foundPath.get().getHaSubFlow());
    }

    private FlowPath createTestFlowPath() {
        FlowPath flowPath = createFlowPath(flow, "_path", 1, 1, switchA, switchB);
        flow.setForwardPath(flowPath);

        return flowPath;
    }

    private void createTestFlowPathPair() {
        FlowPath forwardPath = createFlowPath(flow, "_forward", 1, 1, switchA, switchB);
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = createFlowPath(flow, "_reverse", 2, 2, switchB, switchA);
        flow.setReversePath(reversePath);
    }

    private FlowPath createFlowPath(
            Flow flow, String suffixName, long cookie, long meterId, Switch srcSwitch, Switch dstSwitch) {
        return createFlowPath(new PathId(flow.getFlowId() + suffixName), cookie, meterId, srcSwitch, dstSwitch);
    }

    private FlowPath createFlowPath(
            PathId pathId, long cookie, long meterId, Switch srcSwitch, Switch dstSwitch) {
        FlowPath flowPath = FlowPath.builder()
                .pathId(pathId)
                .cookie(new FlowSegmentCookie(cookie))
                .meterId(new MeterId(meterId))
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flowPathRepository.add(flowPath);
        return flowPath;
    }

    private FlowPath createTestFlowPathWithIntermediate(Switch intSwitch, int intPort) {
        FlowPath flowPath = FlowPath.builder()
                .pathId(new PathId(flow.getFlowId() + "_forward_path"))
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 1L))
                .meterId(new MeterId(1))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .status(FlowPathStatus.ACTIVE)
                .build();

        PathSegment segment1 = PathSegment.builder()
                .pathId(flowPath.getPathId())
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(intSwitch)
                .destPort(intPort)
                .build();
        PathSegment segment2 = PathSegment.builder()
                .pathId(flowPath.getPathId())
                .srcSwitch(intSwitch)
                .srcPort(intPort + 100)
                .destSwitch(switchB)
                .destPort(2)
                .build();
        flowPath.setSegments(asList(segment1, segment2));

        flowPathRepository.add(flowPath);
        return flowPath;
    }

    private Flow createFlow(
            String flowId, Switch srcSwitch, Switch dstSwitch, String diverseGroup, String affinityGroup) {
        Flow flow = buildTestFlow(flowId, srcSwitch, 0, 0, dstSwitch, 0, 0);
        flow.setDiverseGroupId(diverseGroup);
        flow.setAffinityGroupId(affinityGroup);
        flowRepository.add(flow);
        return flow;
    }

    private HaFlow createHaFlow(
            String haFlowId, Switch sharedSwitch, Switch endpointSwitch1, Switch endpointSwitch2,
            String diverseGroup, String affinityGroup) {
        List<HaSubFlow> subFlows = newArrayList(
                buildHaSubFlow(haFlowId + "flow1", endpointSwitch1, PORT_1, VLAN_1, ZERO_INNER_VLAN, DESCRIPTION_1),
                buildHaSubFlow(haFlowId + "flow2", endpointSwitch2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2));
        subFlows.forEach(haSubFlowRepository::add);

        List<HaFlowPath> haPaths = newArrayList(
                buildHaFlowPath(new PathId(haFlowId + "ha_path_1"), 0, COOKIE_1, METER_ID_1, METER_ID_2,
                        sharedSwitch, endpointSwitch1.getSwitchId(), GROUP_ID_1),
                buildHaFlowPath(new PathId(haFlowId + "ha_path_2"), 0, COOKIE_2, METER_ID_1, METER_ID_2,
                        sharedSwitch, endpointSwitch1.getSwitchId(), GROUP_ID_1));
        for (HaFlowPath haPath : haPaths) {
            haFlowPathRepository.add(haPath);
            haPath.setHaSubFlows(subFlows);
            FlowPath subPath = buildPath(
                    haPath.getHaPathId().append("_sub_path"), haPath, sharedSwitch, endpointSwitch1);
            flowPathRepository.add(subPath);
            subPath.setHaSubFlow(subFlows.get(0));
            haPath.setSubPaths(newArrayList(subPath));
        }

        HaFlow haFlow = buildHaFlow(haFlowId, sharedSwitch);
        haFlow.setDiverseGroupId(diverseGroup);
        haFlow.setAffinityGroupId(affinityGroup);
        haFlowRepository.add(haFlow);
        haFlow.setHaSubFlows(subFlows);
        haFlow.setForwardPath(haPaths.get(0));
        haFlow.setProtectedForwardPath(haPaths.get(1));
        return haFlow;
    }

    private YFlow createYFlow(
            String yFlowId, Switch sharedSwitch, Switch endpointSwitch1, Switch endpointSwitch2,
            String diverseGroup, String affinityGroup) {

        YFlow yFlow = YFlow.builder()
                .yFlowId(yFlowId)
                .sharedEndpoint(new SharedEndpoint(sharedSwitch.getSwitchId(), 0))
                .build();
        yFlowRepository.add(yFlow);

        List<Flow> subFlows = newArrayList(
                createFlow(yFlowId + "y_sub_flow1", sharedSwitch, endpointSwitch1, diverseGroup, affinityGroup),
                createFlow(yFlowId + "y_sub_flow2", sharedSwitch, endpointSwitch2, diverseGroup, affinityGroup));

        for (Flow subFlow : subFlows) {
            YSubFlow ySubFlow = YSubFlow.builder()
                    .yFlow(yFlow)
                    .flow(subFlow)
                    .endpointSwitchId(subFlow.getDestSwitchId())
                    .build();
            yFlow.addSubFlow(ySubFlow);
        }
        return yFlow;
    }

    private Flow buildTestFlow(String flowId, Switch srcSwitch, int srcPort, int srcVlan,
                               Switch destSwitch, int destPort, int destVlan) {
        return buildTestFlow(flowId, srcSwitch, srcPort, srcVlan, destSwitch, destPort, destVlan, null);
    }

    private Flow buildTestFlow(String flowId, Switch srcSwitch, int srcPort, int srcVlan,
                               Switch destSwitch, int destPort, int destVlan, String sharedBandwidthGroupId) {
        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .srcVlan(srcVlan)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .destVlan(destVlan)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .status(FlowStatus.UP)
                .build();

        FlowPath forwardFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_forward_path"))
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 1L))
                .meterId(new MeterId(1))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(FlowPathStatus.ACTIVE)
                .sharedBandwidthGroupId(sharedBandwidthGroupId)
                .build();
        flow.setForwardPath(forwardFlowPath);

        PathSegment forwardSegment = PathSegment.builder()
                .pathId(forwardFlowPath.getPathId())
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .build();
        forwardFlowPath.setSegments(Collections.singletonList(forwardSegment));

        FlowPath reverseFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_reverse_path"))
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 1L))
                .meterId(new MeterId(2))
                .srcSwitch(destSwitch)
                .destSwitch(srcSwitch)
                .status(FlowPathStatus.ACTIVE)
                .sharedBandwidthGroupId(sharedBandwidthGroupId)
                .build();
        flow.setReversePath(reverseFlowPath);

        PathSegment reverseSegment = PathSegment.builder()
                .pathId(reverseFlowPath.getPathId())
                .srcSwitch(destSwitch)
                .srcPort(destPort)
                .destSwitch(srcSwitch)
                .destPort(srcPort)
                .build();
        reverseFlowPath.setSegments(Collections.singletonList(reverseSegment));

        return flow;
    }

    private Flow buildTestProtectedFlow(String flowId, Switch srcSwitch, int srcPort, int srcVlan,
                                        Switch destSwitch, int destPort, int destVlan) {
        Flow flow = buildTestFlow(flowId, srcSwitch, srcPort, srcVlan, destSwitch, destPort, destVlan);

        FlowPath forwardProtectedFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_forward_protected_path"))
                .cookie(new FlowSegmentCookie(FlowPathDirection.FORWARD, 2L))
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setProtectedForwardPath(forwardProtectedFlowPath);

        PathSegment forwardSegment = PathSegment.builder()
                .pathId(forwardProtectedFlowPath.getPathId())
                .srcSwitch(srcSwitch)
                .srcPort(srcPort)
                .destSwitch(destSwitch)
                .destPort(destPort)
                .build();
        forwardProtectedFlowPath.setSegments(Collections.singletonList(forwardSegment));

        FlowPath reverseProtectedFlowPath = FlowPath.builder()
                .pathId(new PathId(flowId + "_reverse_protected_path"))
                .cookie(new FlowSegmentCookie(FlowPathDirection.REVERSE, 2L))
                .srcSwitch(destSwitch)
                .destSwitch(srcSwitch)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flow.setProtectedReversePath(reverseProtectedFlowPath);

        PathSegment reverseSegment = PathSegment.builder()
                .pathId(reverseProtectedFlowPath.getPathId())
                .srcSwitch(destSwitch)
                .srcPort(destPort)
                .destSwitch(srcSwitch)
                .destPort(srcPort)
                .build();
        reverseProtectedFlowPath.setSegments(Collections.singletonList(reverseSegment));

        return flow;
    }

    private static Set<PathId> getSubPathIds(Collection<FlowPath> paths) {
        return paths.stream().map(FlowPath::getPathId).collect(Collectors.toSet());
    }
}
