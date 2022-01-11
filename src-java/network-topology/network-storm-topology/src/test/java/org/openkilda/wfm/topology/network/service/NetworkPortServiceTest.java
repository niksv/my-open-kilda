/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.network.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.PortProperties;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.tx.TransactionCallbackWithoutResult;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.share.history.model.PortHistoryEvent;
import org.openkilda.wfm.share.model.Endpoint;
import org.openkilda.wfm.topology.network.NetworkTopologyDashboardLogger;
import org.openkilda.wfm.topology.network.model.LinkStatus;
import org.openkilda.wfm.topology.network.model.OnlineStatus;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class NetworkPortServiceTest {
    @Mock
    private IPortCarrier carrier;

    @Mock
    private NetworkTopologyDashboardLogger dashboardLogger;

    @Mock
    private PersistenceManager persistenceManager;

    @Mock
    private TransactionManager transactionManager;

    @Mock
    private RepositoryFactory repositoryFactory;

    @Mock
    private PortPropertiesRepository portPropertiesRepository;

    @Mock
    private SwitchRepository switchRepository;

    private final SwitchId alphaDatapath = new SwitchId(1);

    @Before
    public void setup() {
        resetMocks();
    }

    private void resetMocks() {
        reset(carrier);
        reset(dashboardLogger);

        reset(persistenceManager);
        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
        when(persistenceManager.getTransactionManager()).thenReturn(transactionManager);
        doAnswer(invocation -> {
            TransactionCallbackWithoutResult tr = invocation.getArgument(0);
            tr.doInTransaction();
            return null;
        }).when(transactionManager).doInTransaction(any(TransactionCallbackWithoutResult.class));

        reset(portPropertiesRepository);
        doAnswer(invocation -> invocation.getArgument(0))
                .when(portPropertiesRepository).add(any());

        reset(switchRepository);

        reset(repositoryFactory);
        when(repositoryFactory.createPortPropertiesRepository()).thenReturn(portPropertiesRepository);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
    }

    @Test
    public void newPort() {
        NetworkPortService service = makeService();
        Endpoint port1 = Endpoint.of(alphaDatapath, 1);
        Endpoint port2 = Endpoint.of(alphaDatapath, 2);

        service.setup(port1, null);
        service.updateOnlineMode(port1, OnlineStatus.OFFLINE);
        service.setup(port2, null);
        service.updateOnlineMode(port2, OnlineStatus.OFFLINE);

        service.remove(port1);
        service.remove(port2);

        verify(carrier).setupUniIslHandler(Endpoint.of(alphaDatapath, 1), null);
        verify(carrier).removeUniIslHandler(Endpoint.of(alphaDatapath, 1));

        verify(carrier).setupUniIslHandler(Endpoint.of(alphaDatapath, 2), null);
        verify(carrier).removeUniIslHandler(Endpoint.of(alphaDatapath, 2));

        //System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void inOperationalUpDownPort() {
        NetworkPortService service = makeService();
        Endpoint port1 = Endpoint.of(alphaDatapath, 1);
        Endpoint port2 = Endpoint.of(alphaDatapath, 2);

        service.setup(port1, null);
        service.updateOnlineMode(port1, OnlineStatus.ONLINE);
        service.setup(port2, null);
        service.updateOnlineMode(port2, OnlineStatus.ONLINE);

        verify(carrier).setupUniIslHandler(Endpoint.of(alphaDatapath, 2), null);
        verifyNoInteractions(dashboardLogger);

        resetMocks();

        // Port 1 from Unknown to UP then DOWN

        service.updateLinkStatus(port1, LinkStatus.UP);
        service.updateLinkStatus(port1, LinkStatus.DOWN);

        verify(dashboardLogger).onPortUp(port1);
        verify(dashboardLogger).onPortDown(port1);
        verifyNoMoreInteractions(dashboardLogger);

        verify(carrier).enableDiscoveryPoll(Endpoint.of(alphaDatapath, 1));
        verify(carrier).disableDiscoveryPoll(Endpoint.of(alphaDatapath, 1));
        verify(carrier).notifyPortPhysicalDown(Endpoint.of(alphaDatapath, 1));

        resetMocks();

        // Port 2 from Unknown to DOWN then UP

        service.updateLinkStatus(port2, LinkStatus.DOWN);
        service.updateLinkStatus(port2, LinkStatus.UP);

        verify(dashboardLogger).onPortDown(port2);
        verify(dashboardLogger).onPortUp(port2);
        verifyNoMoreInteractions(dashboardLogger);

        verify(carrier).notifyPortPhysicalDown(Endpoint.of(alphaDatapath, 2));
        verify(carrier).enableDiscoveryPoll(Endpoint.of(alphaDatapath, 2));

        //System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void inUnOperationalUpDownPort() {
        NetworkPortService service = makeService();
        Endpoint endpoint = Endpoint.of(alphaDatapath, 1);

        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);

        resetMocks();

        service.updateOnlineMode(endpoint, OnlineStatus.OFFLINE);

        // Port 1 from Unknown to UP then DOWN

        service.updateLinkStatus(endpoint, LinkStatus.UP);
        service.updateLinkStatus(endpoint, LinkStatus.DOWN);

        verifyNoInteractions(dashboardLogger);

        verify(carrier, never()).enableDiscoveryPoll(endpoint);
        verify(carrier, never()).disableDiscoveryPoll(endpoint);
        verify(carrier, never()).notifyPortPhysicalDown(endpoint);

        resetMocks();

        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);

        service.updateLinkStatus(endpoint, LinkStatus.UP);
        service.updateLinkStatus(endpoint, LinkStatus.DOWN);

        verify(dashboardLogger).onPortUp(endpoint);
        verify(dashboardLogger).onPortDown(endpoint);
        verifyNoMoreInteractions(dashboardLogger);

        verify(carrier).enableDiscoveryPoll(endpoint);
        verify(carrier).disableDiscoveryPoll(endpoint);
        verify(carrier).notifyPortPhysicalDown(endpoint);
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_UP), any(Instant.class));
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_DOWN), any(Instant.class));

        // System.out.println(mockingDetails(carrier).printInvocations());
    }

    @Test
    public void createPortProperties() {
        NetworkPortService service = makeService();
        int port = 7;
        Endpoint endpoint = Endpoint.of(alphaDatapath, port);

        when(portPropertiesRepository.getBySwitchIdAndPort(alphaDatapath, port))
                .thenReturn(Optional.empty());
        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));

        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);
        service.updateLinkStatus(endpoint, LinkStatus.UP);
        service.updatePortProperties(endpoint, false);

        service.remove(endpoint);

        verify(carrier).setupUniIslHandler(endpoint, null);
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_UP), any(Instant.class));
        verify(carrier).enableDiscoveryPoll(endpoint);
        verify(carrier, new Times(2)).disableDiscoveryPoll(endpoint);
        verify(carrier).notifyPortDiscoveryFailed(endpoint);
        verify(carrier).notifyPortPropertiesChanged(any(PortProperties.class));
        verify(carrier).removeUniIslHandler(endpoint);

        verify(portPropertiesRepository).add(PortProperties.builder()
                .switchObj(getDefaultSwitch())
                .port(port)
                .discoveryEnabled(false)
                .build());
    }

    @Test
    public void disableDiscoveryWhenPortDown() {
        NetworkPortService service = makeService();
        int port = 7;
        Endpoint endpoint = Endpoint.of(alphaDatapath, port);

        expectSwitchLookup(endpoint, getDefaultSwitch());

        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);
        service.updateLinkStatus(endpoint, LinkStatus.DOWN);
        service.updatePortProperties(endpoint, false);
        service.updateLinkStatus(endpoint, LinkStatus.UP);

        service.remove(endpoint);

        verify(carrier).setupUniIslHandler(endpoint, null);
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_DOWN), any(Instant.class));
        verify(carrier, new Times(2)).disableDiscoveryPoll(endpoint);
        verify(carrier).notifyPortPhysicalDown(endpoint);
        verify(carrier).notifyPortPropertiesChanged(any(PortProperties.class));
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_UP), any(Instant.class));
        verify(carrier, new Times(0)).enableDiscoveryPoll(endpoint);
        verify(carrier).removeUniIslHandler(endpoint);

        verify(portPropertiesRepository).add(PortProperties.builder()
                .switchObj(getDefaultSwitch())
                .port(port)
                .discoveryEnabled(false)
                .build());
    }

    @Test
    public void testDiscoveryEventWhenDiscoveryDisabled() {
        Endpoint endpoint = Endpoint.of(alphaDatapath, 1);

        when(portPropertiesRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.empty());
        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));

        NetworkPortService service = makeService();
        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);
        service.updateLinkStatus(endpoint, LinkStatus.UP);

        verify(carrier).enableDiscoveryPoll(eq(endpoint));
        verify(carrier, never()).notifyPortDiscovered(eq(endpoint), any(IslInfoData.class));

        service.updatePortProperties(endpoint, false);
        verify(carrier).disableDiscoveryPoll(eq(endpoint));

        Endpoint remote = Endpoint.of(new SwitchId(endpoint.getDatapath().getId() + 1), 1);
        IslInfoData discovery = new IslInfoData(
                new PathNode(endpoint.getDatapath(), endpoint.getPortNumber(), 0),
                new PathNode(remote.getDatapath(), remote.getPortNumber(), 0),
                IslChangeType.DISCOVERED, false);
        service.discovery(endpoint, discovery);
        verify(carrier, never()).notifyPortDiscovered(eq(endpoint), any(IslInfoData.class));
    }

    @Test
    public void testEnableDiscoveryAfterOfflineOnlineCycle() {
        Endpoint endpoint = Endpoint.of(alphaDatapath, 1);

        when(portPropertiesRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(PortProperties.builder()
                        .switchObj(getDefaultSwitch())
                        .port(endpoint.getPortNumber())
                        .discoveryEnabled(false)
                        .build()));
        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(getDefaultSwitch()));

        NetworkPortService service = makeService();
        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);
        service.updateLinkStatus(endpoint, LinkStatus.UP);
        verify(carrier).enableDiscoveryPoll(eq(endpoint));

        service.updatePortProperties(endpoint, false);
        verify(carrier).disableDiscoveryPoll(eq(endpoint));

        service.updateOnlineMode(endpoint, OnlineStatus.OFFLINE);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);
        service.updateLinkStatus(endpoint, LinkStatus.UP);
        // discovery still disabled
        verify(carrier, times(1)).enableDiscoveryPoll(eq(endpoint));

        service.updatePortProperties(endpoint, true);
        verify(carrier, times(2)).enableDiscoveryPoll(eq(endpoint));

        Endpoint remote = Endpoint.of(new SwitchId(endpoint.getDatapath().getId() + 1), 1);
        IslInfoData discovery = new IslInfoData(
                new PathNode(endpoint.getDatapath(), endpoint.getPortNumber(), 0),
                new PathNode(remote.getDatapath(), remote.getPortNumber(), 0),
                IslChangeType.DISCOVERED, false);
        service.discovery(endpoint, discovery);
        verify(carrier).notifyPortDiscovered(eq(endpoint), eq(discovery));
    }

    @Test
    public void ignorePollFailWhenRegionOffline() {
        Endpoint endpoint = Endpoint.of(alphaDatapath, 8);

        expectSwitchLookup(endpoint, getDefaultSwitch());

        NetworkPortService service = makeService();
        service.setup(endpoint, null);
        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);
        service.updateLinkStatus(endpoint, LinkStatus.UP);

        verify(carrier).setupUniIslHandler(endpoint, null);
        verify(carrier).sendPortStateChangedHistory(eq(endpoint), eq(PortHistoryEvent.PORT_UP), any(Instant.class));
        verify(carrier).enableDiscoveryPoll(eq(endpoint));
        verifyNoMoreInteractions(carrier);

        service.updateOnlineMode(endpoint, OnlineStatus.REGION_OFFLINE);
        verify(carrier).disableDiscoveryPoll(endpoint);
        verifyNoMoreInteractions(carrier);

        service.fail(endpoint);
        verifyNoMoreInteractions(carrier);

        service.updateOnlineMode(endpoint, OnlineStatus.ONLINE);
        service.updateLinkStatus(endpoint, LinkStatus.UP);
        verify(carrier).enableDiscoveryPoll(endpoint);
        verifyNoMoreInteractions(carrier);

        service.fail(endpoint);
        verify(carrier).notifyPortDiscoveryFailed(endpoint);
    }

    private NetworkPortService makeService() {
        NetworkTopologyDashboardLogger.Builder dashboardLoggerBuilder = mock(
                NetworkTopologyDashboardLogger.Builder.class);
        when(dashboardLoggerBuilder.build(any(Logger.class))).thenReturn(dashboardLogger);

        return new NetworkPortService(carrier, persistenceManager, dashboardLoggerBuilder);
    }

    private void expectSwitchLookup(Endpoint endpoint, Switch entry) {
        when(portPropertiesRepository.getBySwitchIdAndPort(alphaDatapath, endpoint.getPortNumber()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(PortProperties.builder()
                        .switchObj(entry)
                        .port(endpoint.getPortNumber())
                        .discoveryEnabled(false)
                        .build()));
        when(switchRepository.findById(alphaDatapath))
                .thenReturn(Optional.of(entry));
    }

    private Switch getDefaultSwitch() {
        return Switch.builder().switchId(alphaDatapath).build();
    }
}
