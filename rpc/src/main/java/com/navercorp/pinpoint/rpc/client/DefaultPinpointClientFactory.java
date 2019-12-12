/*
 * Copyright 2017 NAVER Corp.
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
import com.navercorp.pinpoint.rpc.PinpointSocketException;
import com.navercorp.pinpoint.rpc.StateChangeEventListener;
import com.navercorp.pinpoint.rpc.cluster.ClusterOption;
import com.navercorp.pinpoint.rpc.cluster.Role;
import com.navercorp.pinpoint.rpc.stream.DisabledServerStreamChannelMessageListener;
import com.navercorp.pinpoint.rpc.stream.ServerStreamChannelMessageListener;
import com.navercorp.pinpoint.rpc.util.LoggerFactorySetup;
import com.navercorp.pinpoint.rpc.util.TimerFactory;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Woonduk Kang(emeroad)
 */
public class DefaultPinpointClientFactory implements PinpointClientFactory {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AtomicInteger socketId = new AtomicInteger(1);

    private final Closed closed = new Closed();

    private final ChannelFactory channelFactory;
    private final SocketOption.Builder socketOptionBuilder;

    private Map<String, Object> properties = Collections.emptyMap();

    private final Timer timer;

    private final ConnectionFactoryProvider connectionFactoryProvider;

    private final ClientOption.Builder clientOptionBuilder = new ClientOption.Builder();

    private ClusterOption clusterOption = ClusterOption.DISABLE_CLUSTER_OPTION;

    private MessageListener messageListener = SimpleMessageListener.INSTANCE;
    private List<StateChangeEventListener> stateChangeEventListeners = new ArrayList<StateChangeEventListener>();
    private ServerStreamChannelMessageListener serverStreamChannelMessageListener = DisabledServerStreamChannelMessageListener.INSTANCE;


    static {
        LoggerFactorySetup.setupSlf4jLoggerFactory();
    }

    public DefaultPinpointClientFactory() {
        this(1, 1);
    }

    public DefaultPinpointClientFactory(ConnectionFactoryProvider connectionFactoryProvider) {
        this(1, 1, connectionFactoryProvider);
    }

    public DefaultPinpointClientFactory(int bossCount, int workerCount) {
        this(bossCount, workerCount, new DefaultConnectionFactoryProvider(new ClientCodecPipelineFactory()));
    }

    public DefaultPinpointClientFactory(int bossCount, int workerCount, ConnectionFactoryProvider connectionFactoryProvider) {
        if (bossCount < 1) {
            throw new IllegalArgumentException("bossCount is negative: " + bossCount);
        }

        // create a timer earlier because it is used for connectTimeout
        this.timer = createTimer("Pinpoint-SocketFactory-Timer");
        final ClientChannelFactory channelFactory = new ClientChannelFactory();
        logger.debug("createBootStrap boss:{}, worker:{}", bossCount, workerCount);
        this.channelFactory = channelFactory.createChannelFactory(bossCount, workerCount, timer);
        this.socketOptionBuilder = new SocketOption.Builder();
        this.connectionFactoryProvider = Assert.requireNonNull(connectionFactoryProvider, "connectionFactoryProvider must not be null");
    }

    private static Timer createTimer(String timerName) {
        HashedWheelTimer timer = TimerFactory.createHashedWheelTimer(timerName, 100, TimeUnit.MILLISECONDS, 512);
        timer.start();
        return timer;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.socketOptionBuilder.setConnectTimeout(connectTimeout);
    }

    public int getConnectTimeout() {
        return socketOptionBuilder.getConnectTimeout();
    }

    public long getReconnectDelay() {
        return clientOptionBuilder.getReconnectDelay();
    }

    public void setReconnectDelay(long reconnectDelay) {
        this.clientOptionBuilder.setReconnectDelay(reconnectDelay);
    }

    public long getPingDelay() {
        return this.clientOptionBuilder.getPingDelay();
    }

    public void setPingDelay(long pingDelay) {
        this.clientOptionBuilder.setPingDelay(pingDelay);
    }

    public long getEnableWorkerPacketDelay() {
        return this.clientOptionBuilder.getEnableWorkerPacketDelay();
    }

    public void setEnableWorkerPacketDelay(long enableWorkerPacketDelay) {
        this.clientOptionBuilder.setEnableWorkerPacketDelay(enableWorkerPacketDelay);
    }

    public long getTimeoutMillis() {
        return this.clientOptionBuilder.getTimeoutMillis();
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.clientOptionBuilder.setTimeoutMillis(timeoutMillis);
    }


    public PinpointClient connect(String host, int port) throws PinpointSocketException {
        SocketAddressProvider socketAddressProvider = new DnsSocketAddressProvider(host, port);
        return connect(socketAddressProvider);
    }

    @Deprecated
    public PinpointClient connect(InetSocketAddress connectAddress) throws PinpointSocketException {
        SocketAddressProvider socketAddressProvider = new StaticSocketAddressProvider(connectAddress);
        return connect(socketAddressProvider);
    }

    public PinpointClient connect(SocketAddressProvider socketAddressProvider) throws PinpointSocketException {
        Connection connection = connectInternal(socketAddressProvider, false);
        return connection.awaitConnected();
    }


    private Connection connectInternal(SocketAddressProvider socketAddressProvider, boolean reconnect) {
        final ConnectionFactory connectionFactory = createConnectionFactory();
        return connectionFactory.connect(socketAddressProvider, reconnect);
    }

    private ConnectionFactory createConnectionFactory() {
        final ClientOption clientOption = clientOptionBuilder.build();
        final ClusterOption clusterOption = ClusterOption.copy(this.clusterOption);

        final MessageListener messageListener = this.getMessageListener(SimpleMessageListener.INSTANCE);
        final ServerStreamChannelMessageListener serverStreamChannelMessageListener = this.getServerStreamChannelMessageListener(DisabledServerStreamChannelMessageListener.INSTANCE);
        final List<StateChangeEventListener> stateChangeEventListeners = this.getStateChangeEventListeners();

        Map<String, Object> copyProperties = new HashMap<String, Object>(this.properties);
        final HandshakerFactory handshakerFactory = new HandshakerFactory(socketId, copyProperties, clientOption, clusterOption);
        final ClientHandlerFactory clientHandlerFactory =  new DefaultPinpointClientHandlerFactory(clientOption, clusterOption, handshakerFactory,
                messageListener, serverStreamChannelMessageListener, stateChangeEventListeners);

        final SocketOption socketOption = this.socketOptionBuilder.build();

        return connectionFactoryProvider.get(timer, this.closed, this.channelFactory, socketOption, clientOption, clientHandlerFactory);
    }

    @Override
    public PinpointClient scheduledConnect(String host, int port) {
        SocketAddressProvider socketAddressProvider = new DnsSocketAddressProvider(host, port);
        return scheduledConnect(socketAddressProvider);
    }

    @Deprecated
    public PinpointClient scheduledConnect(InetSocketAddress connectAddress) {
        SocketAddressProvider socketAddressProvider = new StaticSocketAddressProvider(connectAddress);
        return scheduledConnect(socketAddressProvider);
    }

    @Override
    public PinpointClient scheduledConnect(SocketAddressProvider socketAddressProvider) {
        Assert.requireNonNull(socketAddressProvider, "socketAddressProvider must not be null");

        PinpointClient pinpointClient = new DefaultPinpointClient(new ReconnectStateClientHandler());
        ConnectionFactory connectionFactory = createConnectionFactory();
        connectionFactory.reconnect(pinpointClient, socketAddressProvider);
        return pinpointClient;
    }


    @Deprecated
    public ChannelFuture reconnect(final SocketAddress remoteAddress) {
        if (!(remoteAddress instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("invalid remoteAddress:" + remoteAddress);
        }
        SocketAddressProvider socketAddressProvider = new StaticSocketAddressProvider((InetSocketAddress) remoteAddress);
        Connection connection = connectInternal(socketAddressProvider, true);
        return connection.getConnectFuture();
    }


    public void release() {
        if (this.closed.isClosed()) {
            return;
        }
        if (!this.closed.close()) {
            return;
        }


        final ChannelFactory channelFactory = this.channelFactory;
        if (channelFactory != null) {
            channelFactory.releaseExternalResources();
        }
        Set<Timeout> stop = this.timer.stop();
        if (!stop.isEmpty()) {
            logger.info("stop Timeout:{}", stop.size());
        }

        // stop, cancel something?
    }

    public void setProperties(Map<String, Object> agentProperties) {
        Assert.requireNonNull(properties, "agentProperties must not be null");

        this.properties = new HashMap<String, Object>(agentProperties);
    }

    public ClusterOption getClusterOption() {
        return clusterOption;
    }

    public void setClusterOption(String id, List<Role> roles) {
        this.clusterOption = new ClusterOption(true, id, roles);
    }

    public void setClusterOption(ClusterOption clusterOption) {
        this.clusterOption = clusterOption;
    }

    public MessageListener getMessageListener() {
        return messageListener;
    }

    public MessageListener getMessageListener(MessageListener defaultMessageListener) {
        if (messageListener == null) {
            return defaultMessageListener;
        }

        return messageListener;
    }

    public void setMessageListener(MessageListener messageListener) {
        Assert.requireNonNull(messageListener, "messageListener must not be null");

        this.messageListener = messageListener;
    }

    public ServerStreamChannelMessageListener getServerStreamChannelMessageListener() {
        return serverStreamChannelMessageListener;
    }

    public ServerStreamChannelMessageListener getServerStreamChannelMessageListener(ServerStreamChannelMessageListener defaultStreamMessageListener) {
        if (serverStreamChannelMessageListener == null) {
            return defaultStreamMessageListener;
        }

        return serverStreamChannelMessageListener;
    }

    public void setServerStreamChannelMessageListener(ServerStreamChannelMessageListener serverStreamChannelMessageListener) {
        Assert.requireNonNull(messageListener, "messageListener must not be null");

        this.serverStreamChannelMessageListener = serverStreamChannelMessageListener;
    }

    public List<StateChangeEventListener> getStateChangeEventListeners() {
        return new ArrayList<StateChangeEventListener>(stateChangeEventListeners);
    }

    public void addStateChangeEventListener(StateChangeEventListener stateChangeEventListener) {
        this.stateChangeEventListeners.add(stateChangeEventListener);
    }

    private int nextSocketId() {
        return socketId.getAndIncrement();
    }
}
