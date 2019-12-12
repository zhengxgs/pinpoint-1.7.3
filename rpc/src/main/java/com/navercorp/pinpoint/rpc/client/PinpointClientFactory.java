/*
 * Copyright 2014 NAVER Corp.
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
 */

package com.navercorp.pinpoint.rpc.client;


import com.navercorp.pinpoint.rpc.MessageListener;
import com.navercorp.pinpoint.rpc.PinpointSocketException;
import com.navercorp.pinpoint.rpc.StateChangeEventListener;
import com.navercorp.pinpoint.rpc.cluster.ClusterOption;
import com.navercorp.pinpoint.rpc.cluster.Role;
import com.navercorp.pinpoint.rpc.stream.ServerStreamChannelMessageListener;
import org.jboss.netty.channel.ChannelFuture;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

/**
 * @author emeroad
 * @author koo.taejin
 */
public interface PinpointClientFactory {


    void setConnectTimeout(int connectTimeout);

    int getConnectTimeout();

    long getReconnectDelay();

    void setReconnectDelay(long reconnectDelay);

    long getPingDelay();

    void setPingDelay(long pingDelay);

    long getEnableWorkerPacketDelay();

    void setEnableWorkerPacketDelay(long enableWorkerPacketDelay);

    long getTimeoutMillis();

    void setTimeoutMillis(long timeoutMillis);


    PinpointClient connect(String host, int port) throws PinpointSocketException;

    PinpointClient connect(SocketAddressProvider socketAddressProvider) throws PinpointSocketException;

    /**
     * @deprecated Since 1.7.2 Use {@link #connect(String, int)}
     */
    @Deprecated
    PinpointClient connect(InetSocketAddress connectAddress) throws PinpointSocketException;

    PinpointClient scheduledConnect(String host, int port);

    PinpointClient scheduledConnect(SocketAddressProvider socketAddressProvider);

    /**
     * @deprecated Since 1.7.2 Use {@link #scheduledConnect(String, int)}
     */
    @Deprecated
    PinpointClient scheduledConnect(InetSocketAddress connectAddress);

    /**
     * @deprecated Since 1.7.2 Use {@link #scheduledConnect(String, int)}
     */
    @Deprecated
    ChannelFuture reconnect(final SocketAddress remoteAddress);


    void release();


    void setProperties(Map<String, Object> agentProperties);

    ClusterOption getClusterOption();

    void setClusterOption(String id, List<Role> roles);

    void setClusterOption(ClusterOption clusterOption);

    MessageListener getMessageListener();

    MessageListener getMessageListener(MessageListener defaultMessageListener);

    void setMessageListener(MessageListener messageListener);

    ServerStreamChannelMessageListener getServerStreamChannelMessageListener();

    ServerStreamChannelMessageListener getServerStreamChannelMessageListener(ServerStreamChannelMessageListener defaultStreamMessageListener);


    void setServerStreamChannelMessageListener(ServerStreamChannelMessageListener serverStreamChannelMessageListener);

    List<StateChangeEventListener> getStateChangeEventListeners();

    void addStateChangeEventListener(StateChangeEventListener stateChangeEventListener);


}
