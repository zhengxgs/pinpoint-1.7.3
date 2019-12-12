/*
 * Copyright 2016 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.navercorp.pinpoint.collector.cluster.connection;

import com.navercorp.pinpoint.collector.util.Address;
import com.navercorp.pinpoint.collector.util.DefaultAddress;
import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.rpc.MessageListener;
import com.navercorp.pinpoint.rpc.PinpointSocket;
import com.navercorp.pinpoint.rpc.cluster.ClusterOption;
import com.navercorp.pinpoint.rpc.cluster.Role;
import com.navercorp.pinpoint.rpc.common.SocketStateCode;
import com.navercorp.pinpoint.rpc.packet.HandshakeResponseCode;
import com.navercorp.pinpoint.rpc.packet.PingPayloadPacket;
import com.navercorp.pinpoint.rpc.packet.RequestPacket;
import com.navercorp.pinpoint.rpc.packet.SendPacket;
import com.navercorp.pinpoint.rpc.server.ChannelFilter;
import com.navercorp.pinpoint.rpc.server.PinpointServer;
import com.navercorp.pinpoint.rpc.server.PinpointServerAcceptor;
import com.navercorp.pinpoint.rpc.server.ServerMessageListener;
import com.navercorp.pinpoint.rpc.server.handler.ServerStateChangeEventHandler;
import com.navercorp.pinpoint.rpc.util.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

/**
 * @author Taejin Koo
 */
public class CollectorClusterAcceptor implements CollectorClusterConnectionProvider {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String name;
    private final InetSocketAddress bindAddress;
    private final CollectorClusterConnectionRepository clusterSocketRepository;

    private PinpointServerAcceptor serverAcceptor;

    private final CollectorClusterConnectionOption option;

    public CollectorClusterAcceptor(CollectorClusterConnectionOption option, InetSocketAddress bindAddress, CollectorClusterConnectionRepository clusterSocketRepository) {
        this.name = ClassUtils.simpleClassName(this);
        this.option = Assert.requireNonNull(option, "option must not be null");
        this.bindAddress = Assert.requireNonNull(bindAddress, "bindAddress must not be null");
        this.clusterSocketRepository = Assert.requireNonNull(clusterSocketRepository, "clusterSocketRepository must not be null");
    }

    @Override
    public void start() {
        logger.info("{} initialization started.", name);

        ClusterOption clusterOption = new ClusterOption(true, option.getClusterId(), Role.ROUTER);

        PinpointServerAcceptor serverAcceptor = new PinpointServerAcceptor(clusterOption, ChannelFilter.BYPASS);
        serverAcceptor.setMessageListener(new ClusterServerMessageListener(option.getClusterId(), option.getRouteMessageHandler()));
        serverAcceptor.setServerStreamChannelMessageListener(option.getRouteStreamMessageHandler());
        serverAcceptor.addStateChangeEventHandler(new WebClusterServerChannelStateChangeHandler());
        serverAcceptor.bind(bindAddress);

        this.serverAcceptor = serverAcceptor;

        logger.info("{} initialization completed.", name);
    }

    @Override
    public void stop() {
        logger.info("{} destroying started.", name);

        if (serverAcceptor != null) {
            serverAcceptor.close();
        }

        logger.info("{} destroying completed.", name);
    }

    class ClusterServerMessageListener implements ServerMessageListener {

        private final String clusterId;
        private final MessageListener routeMessageListener;

        public ClusterServerMessageListener(String clusterId, MessageListener routeMessageListener) {
            this.clusterId = clusterId;
            this.routeMessageListener = routeMessageListener;
        }

        @Override
        public void handleSend(SendPacket sendPacket, PinpointSocket pinpointSocket) {
            logger.info("handleSend packet:{}, remote:{}", sendPacket, pinpointSocket.getRemoteAddress());
        }

        @Override
        public void handleRequest(RequestPacket requestPacket, PinpointSocket pinpointSocket) {
            logger.info("handleRequest packet:{}, remote:{}", requestPacket, pinpointSocket.getRemoteAddress());

            // TODO : need handle control message (looks like getClusterId, ..)
            routeMessageListener.handleRequest(requestPacket, pinpointSocket);
        }

        @Override
        public HandshakeResponseCode handleHandshake(Map properties) {
            logger.info("handle handShake {}", properties);
            return HandshakeResponseCode.DUPLEX_COMMUNICATION;
        }

        @Override
        public void handlePing(PingPayloadPacket pingPacket, PinpointServer pinpointServer) {
            logger.info("ping received packet:{}, remote:{}", pingPacket, pinpointServer);
        }

    }

    class WebClusterServerChannelStateChangeHandler implements ServerStateChangeEventHandler {

        @Override
        public void eventPerformed(PinpointServer pinpointServer, SocketStateCode stateCode) throws Exception {
            if (stateCode.isRunDuplex()) {
                Address address = getAddress(pinpointServer);
                clusterSocketRepository.putIfAbsent(address, pinpointServer);
                return;
            } else if (stateCode.isClosed()) {
                Address address = getAddress(pinpointServer);
                clusterSocketRepository.remove(address);
                return;
            }
        }

        private Address getAddress(PinpointServer pinpointServer) {
            final SocketAddress remoteAddress = pinpointServer.getRemoteAddress();
            if (!(remoteAddress instanceof InetSocketAddress)) {
                throw new IllegalStateException("unexpected address type:" + remoteAddress);
            }
            InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
            return new DefaultAddress(inetSocketAddress.getHostString(), inetSocketAddress.getPort());
        }

        @Override
        public void exceptionCaught(PinpointServer pinpointServer, SocketStateCode stateCode, Throwable e) {
        }

    }

}
