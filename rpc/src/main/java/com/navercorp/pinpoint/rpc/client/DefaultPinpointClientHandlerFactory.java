/*
 * Copyright 2018 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.rpc.client;

import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.rpc.MessageListener;
import com.navercorp.pinpoint.rpc.StateChangeEventListener;
import com.navercorp.pinpoint.rpc.cluster.ClusterOption;
import com.navercorp.pinpoint.rpc.stream.ServerStreamChannelMessageListener;
import org.jboss.netty.util.Timer;

import java.util.List;

/**
 * @author Woonduk Kang(emeroad)
 */
public class DefaultPinpointClientHandlerFactory implements ClientHandlerFactory {

    private final ClientOption clientOption;
    private final ClusterOption clusterOption;
    private final HandshakerFactory handshakerFactory;

    private final MessageListener messageListener;
    private final ServerStreamChannelMessageListener serverStreamChannelMessageListener;
    private final List<StateChangeEventListener> stateChangeEventListeners;


    public DefaultPinpointClientHandlerFactory(ClientOption clientOption, ClusterOption clusterOption, HandshakerFactory handshakerFactory,
                                               MessageListener messageListener,
                                               ServerStreamChannelMessageListener serverStreamChannelMessageListener,
                                               List<StateChangeEventListener> stateChangeEventListeners) {

        this.clientOption = Assert.requireNonNull(clientOption, "clientOption must not be null");
        this.clusterOption = Assert.requireNonNull(clusterOption, "clusterOption must not be null");
        this.handshakerFactory = Assert.requireNonNull(handshakerFactory, "handshakerFactory must not be null");

        this.messageListener = Assert.requireNonNull(messageListener, "messageListener must not be null");
        this.serverStreamChannelMessageListener = Assert.requireNonNull(serverStreamChannelMessageListener, "serverStreamChannelMessageListener must not be null");
        this.stateChangeEventListeners = Assert.requireNonNull(stateChangeEventListeners, "stateChangeEventListeners must not be null");


    }


    @Override
    public PinpointClientHandler newClientHandler(ConnectionFactory connectionFactory, SocketAddressProvider remoteAddressProvider, Timer channelTimer, boolean reconnect) {
        PinpointClientHandshaker handshaker = handshakerFactory.newHandShaker(channelTimer);
        final DefaultPinpointClientHandler clientHandler = new DefaultPinpointClientHandler(connectionFactory, remoteAddressProvider, handshaker,
                clusterOption, clientOption, channelTimer,
                messageListener,
                serverStreamChannelMessageListener,
                stateChangeEventListeners
        );

        if (reconnect) {
            clientHandler.initReconnect();
        }
        return clientHandler;
    }


}
