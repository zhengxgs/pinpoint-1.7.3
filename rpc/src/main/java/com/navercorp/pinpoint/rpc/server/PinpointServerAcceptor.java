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

package com.navercorp.pinpoint.rpc.server;

import com.navercorp.pinpoint.common.annotations.VisibleForTesting;
import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.common.util.CpuUtils;
import com.navercorp.pinpoint.common.util.PinpointThreadFactory;
import com.navercorp.pinpoint.rpc.PinpointSocket;
import com.navercorp.pinpoint.rpc.PinpointSocketException;
import com.navercorp.pinpoint.rpc.PipelineFactory;
import com.navercorp.pinpoint.rpc.cluster.ClusterOption;
import com.navercorp.pinpoint.rpc.packet.ServerClosePacket;
import com.navercorp.pinpoint.rpc.server.handler.ServerStateChangeEventHandler;
import com.navercorp.pinpoint.rpc.stream.DisabledServerStreamChannelMessageListener;
import com.navercorp.pinpoint.rpc.stream.ServerStreamChannelMessageListener;
import com.navercorp.pinpoint.rpc.util.LoggerFactorySetup;
import com.navercorp.pinpoint.rpc.util.TimerFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Taejin Koo
 */
public class PinpointServerAcceptor implements PinpointServerConfig {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final long DEFAULT_TIMEOUT_MILLIS = 3 * 1000;
    private static final long CHANNEL_CLOSE_MAXIMUM_WAITING_TIME_MILLIS = 3 * 1000;
    private static final int HEALTH_CHECK_INTERVAL_TIME_MILLIS = 5 * 60 * 1000;
    private static final int WORKER_COUNT = CpuUtils.workerCount();

    private volatile boolean released;

    private ServerBootstrap bootstrap;

    private final ChannelFilter channelConnectedFilter;

    private Channel serverChannel;
    private final ChannelGroup channelGroup = new DefaultChannelGroup("PinpointServerFactory");

    private final PinpointServerChannelHandler nettyChannelHandler = new PinpointServerChannelHandler();

    private ServerMessageListener messageListener = SimpleServerMessageListener.SIMPLEX_INSTANCE;
    private ServerStreamChannelMessageListener serverStreamChannelMessageListener = DisabledServerStreamChannelMessageListener.INSTANCE;
    private List<ServerStateChangeEventHandler> stateChangeEventHandler = new ArrayList<ServerStateChangeEventHandler>();

    private final Timer healthCheckTimer;
    private final HealthCheckManager healthCheckManager;

    private final Timer requestManagerTimer;

    private final ClusterOption clusterOption;

    private final PipelineFactory pipelineFactory;

    private long defaultRequestTimeout = DEFAULT_TIMEOUT_MILLIS;

    static {
        LoggerFactorySetup.setupSlf4jLoggerFactory();
    }

    public PinpointServerAcceptor() {
        this(ClusterOption.DISABLE_CLUSTER_OPTION, ChannelFilter.BYPASS);
    }

    public PinpointServerAcceptor(ChannelFilter channelConnectedFilter) {
        this(ClusterOption.DISABLE_CLUSTER_OPTION, channelConnectedFilter);
    }

    public PinpointServerAcceptor(ChannelFilter channelConnectedFilter, PipelineFactory pipelineFactory) {
        this(ClusterOption.DISABLE_CLUSTER_OPTION, channelConnectedFilter, pipelineFactory);
    }

    public PinpointServerAcceptor(ClusterOption clusterOption, ChannelFilter channelConnectedFilter) {
        this(clusterOption, channelConnectedFilter, new ServerCodecPipelineFactory());
    }

    public PinpointServerAcceptor(ClusterOption clusterOption, ChannelFilter channelConnectedFilter, PipelineFactory pipelineFactory) {
        ServerBootstrap bootstrap = createBootStrap(1, WORKER_COUNT);
        setOptions(bootstrap);
        this.bootstrap = bootstrap;

        this.healthCheckTimer = TimerFactory.createHashedWheelTimer("PinpointServerSocket-HealthCheckTimer", 50, TimeUnit.MILLISECONDS, 512);
        this.healthCheckManager = new HealthCheckManager(healthCheckTimer, channelGroup);

        this.requestManagerTimer = TimerFactory.createHashedWheelTimer("PinpointServerSocket-RequestManager", 50, TimeUnit.MILLISECONDS, 512);

        this.clusterOption = clusterOption;
        this.channelConnectedFilter = Assert.requireNonNull(channelConnectedFilter, "channelConnectedFilter must not be null");

        this.pipelineFactory = Assert.requireNonNull(pipelineFactory, "pipelineFactory must not be null");
        addPipeline(bootstrap, pipelineFactory);
    }

    private ServerBootstrap createBootStrap(int bossCount, int workerCount) {
        // profiler, collector
        ExecutorService boss = Executors.newCachedThreadPool(new PinpointThreadFactory("Pinpoint-Server-Boss", true));
        NioServerBossPool nioServerBossPool = new NioServerBossPool(boss, bossCount, ThreadNameDeterminer.CURRENT);

        ExecutorService worker = Executors.newCachedThreadPool(new PinpointThreadFactory("Pinpoint-Server-Worker", true));
        NioWorkerPool nioWorkerPool = new NioWorkerPool(worker, workerCount, ThreadNameDeterminer.CURRENT);

        NioServerSocketChannelFactory nioClientSocketChannelFactory = new NioServerSocketChannelFactory(nioServerBossPool, nioWorkerPool);
        return new ServerBootstrap(nioClientSocketChannelFactory);
    }

    private void setOptions(ServerBootstrap bootstrap) {
        // is read/write timeout necessary? don't need it because of NIO?
        // write timeout should be set through additional interceptor. write
        // timeout exists.

        // tcp setting
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
        // buffer setting
        bootstrap.setOption("child.sendBufferSize", 1024 * 64);
        bootstrap.setOption("child.receiveBufferSize", 1024 * 64);

        // bootstrap.setOption("child.soLinger", 0);
    }

    private void addPipeline(ServerBootstrap bootstrap, final PipelineFactory pipelineFactory) {
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = pipelineFactory.newPipeline();
                pipeline.addLast("handler", nettyChannelHandler);

                return pipeline;
            }
        });
    }

    @VisibleForTesting
    void setPipelineFactory(ChannelPipelineFactory channelPipelineFactory) {
        if (channelPipelineFactory == null) {
            throw new NullPointerException("channelPipelineFactory must not be null");
        }
        bootstrap.setPipelineFactory(channelPipelineFactory);
    }

    @VisibleForTesting
    void setMessageHandler(final ChannelHandler messageHandler) {
        Assert.requireNonNull(messageHandler, "messageHandler must not be null");
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = pipelineFactory.newPipeline();
                pipeline.addLast("handler", messageHandler);

                return pipeline;
            }
        });
    }

    public void bind(String host, int port) throws PinpointSocketException {
        InetSocketAddress bindAddress = new InetSocketAddress(host, port);
        bind(bindAddress);
    }

    public void bind(InetSocketAddress bindAddress) throws PinpointSocketException {
        if (released) {
            return;
        }

        logger.info("bind() {}", bindAddress);
        this.serverChannel = bootstrap.bind(bindAddress);
        healthCheckManager.start(HEALTH_CHECK_INTERVAL_TIME_MILLIS);
    }

    private DefaultPinpointServer createPinpointServer(Channel channel) {
        DefaultPinpointServer pinpointServer = new DefaultPinpointServer(channel, this);
        return pinpointServer;
    }

    @Override
    public long getDefaultRequestTimeout() {
        return defaultRequestTimeout;
    }

    public void setDefaultRequestTimeout(long defaultRequestTimeout) {
        this.defaultRequestTimeout = defaultRequestTimeout;
    }


    @Override
    public ServerMessageListener getMessageListener() {
        return messageListener;
    }

    public void setMessageListener(ServerMessageListener messageListener) {
        Assert.requireNonNull(messageListener, "messageListener must not be null");

        this.messageListener = messageListener;
    }

    @Override
    public List<ServerStateChangeEventHandler> getStateChangeEventHandlers() {
        return stateChangeEventHandler;
    }

    public void addStateChangeEventHandler(ServerStateChangeEventHandler stateChangeEventHandler) {
        Assert.requireNonNull(stateChangeEventHandler, "stateChangeEventHandler must not be null");

        this.stateChangeEventHandler.add(stateChangeEventHandler);
    }

    @Override
    public ServerStreamChannelMessageListener getStreamMessageListener() {
        return serverStreamChannelMessageListener;
    }

    public void setServerStreamChannelMessageListener(ServerStreamChannelMessageListener serverStreamChannelMessageListener) {
        Assert.requireNonNull(serverStreamChannelMessageListener, "serverStreamChannelMessageListener must not be null");

        this.serverStreamChannelMessageListener = serverStreamChannelMessageListener;
    }

    @Override
    public Timer getRequestManagerTimer() {
        return requestManagerTimer;
    }

    @Override
    public ClusterOption getClusterOption() {
        return clusterOption;
    }

    public void close() {
        synchronized (this) {
            if (released) {
                return;
            }
            released = true;
        }
        healthCheckManager.stop();
        healthCheckTimer.stop();
        
        closePinpointServer();

        if (serverChannel != null) {
            ChannelFuture close = serverChannel.close();
            close.awaitUninterruptibly(CHANNEL_CLOSE_MAXIMUM_WAITING_TIME_MILLIS, TimeUnit.MILLISECONDS);
            serverChannel = null;
        }
        if (bootstrap != null) {
            bootstrap.releaseExternalResources();
            bootstrap = null;
        }

        // clear the request first and remove timer
        requestManagerTimer.stop();
    }
    
    private void closePinpointServer() {
        for (Channel channel : channelGroup) {
            DefaultPinpointServer pinpointServer = (DefaultPinpointServer) channel.getAttachment();

            if (pinpointServer != null) {
                pinpointServer.sendClosePacket();
            }
        }
    }
    
    public List<PinpointSocket> getWritableSocketList() {
        List<PinpointSocket> pinpointServerList = new ArrayList<PinpointSocket>();

        for (Channel channel : channelGroup) {
            DefaultPinpointServer pinpointServer = (DefaultPinpointServer) channel.getAttachment();
            if (pinpointServer != null && pinpointServer.isEnableDuplexCommunication()) {
                pinpointServerList.add(pinpointServer);
            }
        }

        return pinpointServerList;
    }

    class PinpointServerChannelHandler extends SimpleChannelHandler {
        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            final Channel channel = e.getChannel();
            logger.info("channelConnected started. channel:{}", channel);

            if (released) {
                logger.warn("already released. channel:{}", channel);
                channel.write(new ServerClosePacket()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.getChannel().close();
                    }
                });
                return;
            }

            final boolean accept = channelConnectedFilter.accept(channel);
            if (!accept) {
                logger.debug("channelConnected() channel discard. {}", channel);
                return;
            }

            DefaultPinpointServer pinpointServer = createPinpointServer(channel);
            
            channel.setAttachment(pinpointServer);
            channelGroup.add(channel);

            pinpointServer.start();

            super.channelConnected(ctx, e);
        }

        @Override
        public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            final Channel channel = e.getChannel();

            DefaultPinpointServer pinpointServer = (DefaultPinpointServer) channel.getAttachment();
            if (pinpointServer != null) {
                pinpointServer.stop(released);
            }

            super.channelDisconnected(ctx, e);
        }

        // ChannelClose event may also happen when the other party close socket
        // first and Disconnected occurs
        // Should consider that.
        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            final Channel channel = e.getChannel();

            channelGroup.remove(channel);

            super.channelClosed(ctx, e);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            final Channel channel = e.getChannel();

            DefaultPinpointServer pinpointServer = (DefaultPinpointServer) channel.getAttachment();
            if (pinpointServer != null) {
                Object message = e.getMessage();

                pinpointServer.messageReceived(message);
            }

            super.messageReceived(ctx, e);
        }
    }

}
