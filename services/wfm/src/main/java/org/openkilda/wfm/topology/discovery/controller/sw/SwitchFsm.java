/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.discovery.controller.sw;

import org.openkilda.messaging.model.SpeakerSwitchDescription;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.utils.AbstractBaseFsm;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.sw.SwitchFsm.SwitchFsmContext;
import org.openkilda.wfm.topology.discovery.controller.sw.SwitchFsm.SwitchFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.sw.SwitchFsm.SwitchFsmState;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.LinkStatus;
import org.openkilda.wfm.topology.discovery.model.facts.HistoryFacts;
import org.openkilda.wfm.topology.discovery.service.ISwitchCarrier;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public final class SwitchFsm extends AbstractBaseFsm<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> {
    private final TransactionManager transactionManager;
    private final SwitchRepository switchRepository;

    private final SwitchId switchId;
    private final Integer bfdLogicalPortOffset;

    private final Set<SpeakerSwitchView.Feature> features = new HashSet<>();
    private final Map<Integer, AbstractPort> portByNumber = new HashMap<>();

    private static final StateMachineBuilder<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                SwitchFsm.class, SwitchFsmState.class, SwitchFsmEvent.class, SwitchFsmContext.class,
                // extra parameters
                PersistenceManager.class, SwitchId.class, Integer.class);

        // INIT
        builder.transition()
                .from(SwitchFsmState.INIT).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.HISTORY)
                .callMethod("applyHistory");
        builder.transition()
                .from(SwitchFsmState.INIT).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.ONLINE);

        // SETUP
        builder.transition()
                .from(SwitchFsmState.SETUP).to(SwitchFsmState.ONLINE).on(SwitchFsmEvent.NEXT);
        builder.onEntry(SwitchFsmState.SETUP)
                .callMethod("setupEnter");

        // ONLINE
        builder.transition().from(SwitchFsmState.ONLINE).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.OFFLINE);
        builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.PORT_ADD)
                .callMethod("handlePortAdd");
        builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.PORT_DEL)
                .callMethod("handlePortDel");
        builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.PORT_UP)
                .callMethod("handlePortLinkStateChange");
        builder.internalTransition().within(SwitchFsmState.ONLINE).on(SwitchFsmEvent.PORT_DOWN)
                .callMethod("handlePortLinkStateChange");
        builder.onEntry(SwitchFsmState.ONLINE)
                .callMethod("onlineEnter");

        // OFFLINE
        builder.transition().from(SwitchFsmState.OFFLINE).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.ONLINE);
        builder.transition().from(SwitchFsmState.OFFLINE).to(SwitchFsmState.DELETED).on(SwitchFsmEvent.SWITCH_REMOVE)
                .callMethod("removePortsFsm");
        builder.onEntry(SwitchFsmState.OFFLINE)
                .callMethod("offlineEnter");
    }

    public static FsmExecutor<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> makeExecutor() {
        return new FsmExecutor<>(SwitchFsmEvent.NEXT);
    }

    public static SwitchFsm create(PersistenceManager persistenceManager, SwitchId switchId,
                                   Integer bfdLocalPortOffset) {
        return builder.newStateMachine(SwitchFsmState.INIT, persistenceManager, switchId, bfdLocalPortOffset);
    }

    public SwitchFsm(PersistenceManager persistenceManager, SwitchId switchId, Integer bfdLocalPortOffset) {
        this.transactionManager = persistenceManager.getTransactionManager();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();

        this.switchId = switchId;
        this.bfdLogicalPortOffset = bfdLocalPortOffset;
    }

    // -- FSM actions --

    protected void applyHistory(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                SwitchFsmContext context) {
        HistoryFacts historyFacts = context.getHistory();
        for (Isl outgoingLink : historyFacts.getOutgoingLinks()) {
            PhysicalPort port = new PhysicalPort(Endpoint.of(switchId, outgoingLink.getSrcPort()));
            port.setHistory(outgoingLink);
            portAdd(port, context);
        }
    }

    protected void setupEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        SpeakerSwitchView speakerData = context.getSpeakerData();

        // set features for correct port (re)identification
        if (!features.equals(speakerData.getFeatures())) {
            log.info("Update features for {} - old:{} new:{}", switchId, features, speakerData.getFeatures());
            features.clear();
            features.addAll(speakerData.getFeatures());
        }

        // locate changes
        Map<Integer, AbstractPort> removedPorts = new HashMap<>(portByNumber);
        List<AbstractPort> addedPorts = new ArrayList<>();
        List<AbstractPort> upPorts = new ArrayList<>();
        List<AbstractPort> downPorts = new ArrayList<>();
        for (SpeakerSwitchPortView speakerPort : speakerData.getPorts()) {
            AbstractPort actualPort = makePortRecord(speakerPort);
            AbstractPort storedPort = portByNumber.get(speakerPort.getNumber());
            if (storedPort == null) {
                addedPorts.add(actualPort);
            } else if (!storedPort.isSameKind(actualPort)) {
                // port kind have been changed, we must recreate port handler
                addedPorts.add(actualPort);
            } else {
                removedPorts.remove(speakerPort.getNumber());
            }

            switch (actualPort.getLinkStatus()) {
                case UP:
                    upPorts.add(actualPort);
                    break;
                case DOWN:
                    downPorts.add(actualPort);
                    break;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Unsupported port admin state value %s (%s)",
                            actualPort.getLinkStatus(), actualPort.getLinkStatus().getClass().getName()));
            }
        }

        // apply changes
        for (AbstractPort port : removedPorts.values()) {
            portDel(port, context);
        }

        for (AbstractPort port : addedPorts) {
            portAdd(port, context);
        }

        // emit "online" status for all ports
        for (AbstractPort port : portByNumber.values()) {
            updateOnlineStatus(port, context, true);
        }

        // emit ports up/down
        for (AbstractPort port : downPorts) {
            port.setLinkStatus(LinkStatus.DOWN);
            updatePortLinkMode(port, context);
        }

        for (AbstractPort port : upPorts) {
            port.setLinkStatus(LinkStatus.UP);
            updatePortLinkMode(port, context);
        }
    }

    protected void onlineEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        transactionManager.doInTransaction(() -> persistSwitchData(context));
        initialSwitchSetup(context);
    }

    protected void offlineEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                SwitchFsmContext context) {
        transactionManager.doInTransaction(() -> updatePersistentStatus(SwitchStatus.INACTIVE));

        for (AbstractPort port : portByNumber.values()) {
            updateOnlineStatus(port, context, false);
        }
    }

    protected void handlePortAdd(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                 SwitchFsmContext context) {
        AbstractPort port = makePortRecord(Endpoint.of(switchId, context.getPortNumber()));
        log.info("Receive port-add notification for {}", port);

        portAdd(port, context);
        updateOnlineStatus(port, context, true);
    }

    protected void handlePortDel(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                 SwitchFsmContext context) {
        AbstractPort port = portByNumber.get(context.getPortNumber());
        if (port != null) {
            portDel(port, context);
        } else {
            log.error("Receive port del request for {}, but this port is missing",
                      Endpoint.of(switchId, context.getPortNumber()));
        }
    }

    protected void handlePortLinkStateChange(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                           SwitchFsmContext context) {
        AbstractPort port = portByNumber.get(context.getPortNumber());
        if (port == null) {
            log.error("Port {} is not listed into {}", context.getPortNumber(), switchId);
            return;
        }

        switch (event) {
            case PORT_UP:
                port.setLinkStatus(LinkStatus.UP);
                break;
            case PORT_DOWN:
                port.setLinkStatus(LinkStatus.DOWN);
                break;
            default:
                throw new IllegalArgumentException(String.format("Unexpected event %s received in state %s (%s)",
                                                                 event, to, getClass().getName()));
        }

        updatePortLinkMode(port, context);
    }

    /**
     * Removed ports FSM on SWITCH_REMOVE event.
     */
    public void removePortsFsm(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                             SwitchFsmContext context) {
        List<AbstractPort> ports = new ArrayList<>(portByNumber.values());
        for (AbstractPort port: ports) {
            portDel(port, context);
        }
    }

    // -- private/service methods --

    private void portAdd(AbstractPort port, SwitchFsmContext context) {
        portByNumber.put(port.getPortNumber(), port);
        port.portAdd(context.getOutput());
    }

    private void portDel(AbstractPort port, SwitchFsmContext context) {
        portByNumber.remove(port.getPortNumber());
        port.portDel(context.getOutput());
    }

    private void updatePortLinkMode(AbstractPort port, SwitchFsmContext context) {
        port.updatePortLinkMode(context.getOutput());
    }

    private void updateOnlineStatus(AbstractPort port, SwitchFsmContext context, boolean mode) {
        port.updateOnlineStatus(context.getOutput(), mode);
    }

    private void persistSwitchData(SwitchFsmContext context) {
        Switch sw = switchRepository.findById(switchId)
                .orElseGet(() -> Switch.builder().switchId(switchId).build());

        SpeakerSwitchView speakerData = context.getSpeakerData();
        InetSocketAddress socketAddress = speakerData.getSwitchSocketAddress();
        sw.setAddress(socketAddress.getAddress().getHostAddress());
        sw.setHostname(socketAddress.getHostName());

        SpeakerSwitchDescription description = speakerData.getDescription();
        sw.setDescription(String.format("%s %s %s",
                                        description.getManufacturer(),
                                        speakerData.getOfVersion(),
                                        description.getSoftware()));

        sw.setOfVersion(speakerData.getOfVersion());
        sw.setOfDescriptionManufacturer(description.getManufacturer());
        sw.setOfDescriptionHardware(description.getHardware());
        sw.setOfDescriptionSoftware(description.getSoftware());
        sw.setOfDescriptionSerialNumber(description.getSerialNumber());
        sw.setOfDescriptionDatapath(description.getDatapath());

        sw.setStatus(SwitchStatus.ACTIVE);

        switchRepository.createOrUpdate(sw);
    }

    private void updatePersistentStatus(SwitchStatus status) {
        switchRepository.findById(switchId)
                .ifPresent(entry -> {
                    entry.setStatus(status);
                    switchRepository.createOrUpdate(entry);
                });
    }

    private void initialSwitchSetup(SwitchFsmContext context) {
        // FIXME(surabujin): move initial switch setup here (from FL)
    }

    private AbstractPort makePortRecord(SpeakerSwitchPortView speakerPort) {
        AbstractPort port = makePortRecord(Endpoint.of(switchId, speakerPort.getNumber()));
        port.setLinkStatus(LinkStatus.of(speakerPort.getState()));
        return port;
    }

    private AbstractPort makePortRecord(Endpoint endpoint) {
        AbstractPort record;
        if (isPhysicalPort(endpoint.getPortNumber())) {
            record = new PhysicalPort(endpoint);
        } else {
            // at this moment we know only 2 kind of ports - physical and logical-BFD
            record = new LogicalBfdPort(endpoint, endpoint.getPortNumber() - bfdLogicalPortOffset);
        }

        return record;
    }

    /**
     * Distinguish physical ports from other port types.
     *
     * <p>At this moment we have 2 kind of ports - physical ports and logical-BFD ports. So if this method return false
     * wee have a deal with logical-BFD port.
     */
    private boolean isPhysicalPort(int portNumber) {
        if (features.contains(SpeakerSwitchView.Feature.BFD)) {
            return portNumber < bfdLogicalPortOffset;
        }
        return true;
    }

    // -- service data types --

    @Value
    @Builder(toBuilder = true)
    public static class SwitchFsmContext {
        private final ISwitchCarrier output;

        private SpeakerSwitchView speakerData;
        private HistoryFacts history;

        private Integer portNumber;

        public static SwitchFsmContextBuilder builder(ISwitchCarrier output) {
            return (new SwitchFsmContextBuilder()).output(output);
        }
    }

    public enum SwitchFsmEvent {
        NEXT,

        HISTORY,

        ONLINE,
        OFFLINE,

        PORT_ADD, PORT_DEL, PORT_UP, SWITCH_REMOVE, PORT_DOWN
    }

    public enum SwitchFsmState {
        INIT,
        OFFLINE,
        ONLINE,
        SETUP,
        DELETED
    }
}
