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

import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.rpc.*;
import com.navercorp.pinpoint.rpc.client.ConnectFuture.Result;
import com.navercorp.pinpoint.rpc.cluster.ClusterOption;
import com.navercorp.pinpoint.rpc.common.SocketStateChangeResult;
import com.navercorp.pinpoint.rpc.common.SocketStateCode;
import com.navercorp.pinpoint.rpc.packet.*;
import com.navercorp.pinpoint.rpc.packet.stream.StreamPacket;
import com.navercorp.pinpoint.rpc.stream.*;
import com.navercorp.pinpoint.rpc.util.ClassUtils;
import com.navercorp.pinpoint.rpc.util.IDGenerator;
import org.jboss.netty.channel.*;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author emeroad
 * @author netspider
 * @author koo.taejin
 */
public class DefaultPinpointClientHandler extends SimpleChannelHandler implements PinpointClientHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AtomicInteger pingIdGenerator;
    private final PinpointClientHandlerState state;
    private final SocketAddressProvider socketAddressProvider;

    private volatile Channel channel;


    private final Timer channelTimer;

    private final ConnectionFactory connectionFactory;
    private volatile PinpointClient pinpointClient;

    private final MessageListener messageListener;
    private final ServerStreamChannelMessageListener serverStreamChannelMessageListener;

    private final RequestManager requestManager;

    private final ChannelFutureListener pingWriteFailFutureListener = new WriteFailFutureListener(this.logger, "ping write fail.", "ping write success.");
    private final ChannelFutureListener sendWriteFailFutureListener = new WriteFailFutureListener(this.logger, "send() write fail.", "send() write success.");

    private final ChannelFutureListener sendClosePacketFailFutureListener = new WriteFailFutureListener(this.logger, "sendClosedPacket() write fail.", "sendClosedPacket() write success.");

    private final PinpointClientHandshaker handshaker;

    private final ConnectFuture connectFuture = new ConnectFuture();

    private final String objectUniqName;
    private final ClientOption clientOption;
    private final ClusterOption localClusterOption;
    private volatile ClusterOption remoteClusterOption = ClusterOption.DISABLE_CLUSTER_OPTION;


    public DefaultPinpointClientHandler(ConnectionFactory connectionFactory, SocketAddressProvider socketAddressProvider, PinpointClientHandshaker handshaker,
                                        ClusterOption localClusterOption, ClientOption clientOption,
                                        Timer channelTimer,
                                        MessageListener messageListener,
                                        ServerStreamChannelMessageListener serverStreamChannelMessageListener,
                                        List<StateChangeEventListener> stateChangeEventListeners) {

        this.connectionFactory = Assert.requireNonNull(connectionFactory,   "clientFactory must not be null");
        this.socketAddressProvider = Assert.requireNonNull(socketAddressProvider, "socketAddressProvider must not be null");

        this.channelTimer = Assert.requireNonNull(channelTimer, "channelTimer must not be null");
        this.requestManager = new RequestManager(channelTimer, clientOption.getTimeoutMillis());
        this.clientOption = Assert.requireNonNull(clientOption, "clientOption must not be null");


        this.messageListener = Assert.requireNonNull(messageListener, "messageListener must not be null");
        this.serverStreamChannelMessageListener = Assert.requireNonNull(serverStreamChannelMessageListener, "serverStreamChannelMessageListener must not be null");

        this.objectUniqName = ClassUtils.simpleClassNameAndHashCodeString(this);
        this.handshaker = Assert.requireNonNull(handshaker, "handshaker must not be null");

        this.pingIdGenerator = new AtomicInteger(0);
        this.state = new PinpointClientHandlerState(this.objectUniqName, this, stateChangeEventListeners);

        this.localClusterOption = Assert.requireNonNull(localClusterOption, "clusterOption must not be null");

    }

    public void setPinpointClient(PinpointClient pinpointClient) {
        if (pinpointClient == null) {
            throw new NullPointerException("pinpointClient must not be null");
        }
        this.pinpointClient = pinpointClient;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        final Channel channel = e.getChannel();

        logger.debug("{} channelOpen() started. channel:{}", objectUniqName, channel);

        this.channel = channel;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        final Channel channel = e.getChannel();
        if ((null == channel) || (this.channel != channel)) {
            throw new IllegalArgumentException("Invalid channel variable. this.channel:" + this.channel + ", channel:" + channel + ".");
        }

        logger.info("{} channelConnected() started. channel:{}", objectUniqName, channel);

        SocketStateChangeResult stateChangeResult = state.toConnected();
        if (!stateChangeResult.isChange()) {
            throw new IllegalStateException("Invalid state:" + stateChangeResult.getCurrentState());
        }

        prepareChannel(channel);

        stateChangeResult = state.toRunWithoutHandshake();
        if (!stateChangeResult.isChange()) {
            throw new IllegalStateException("Failed to execute channelConnected() method. Error:" + stateChangeResult);
        }

        registerPing();


        handshaker.handshakeStart(channel);

        connectFuture.setResult(Result.SUCCESS);

        logger.info("{} channelConnected() completed.", objectUniqName);
    }

    private void prepareChannel(Channel channel) {
        StreamChannelManager streamChannelManager = new StreamChannelManager(channel, IDGenerator.createOddIdGenerator(), serverStreamChannelMessageListener);

        PinpointClientHandlerContext context = new PinpointClientHandlerContext(channel, streamChannelManager);
        channel.setAttachment(context);
    }

    @Override
    public void initReconnect() {
        logger.info("{} initReconnect() started.", objectUniqName);

        SocketStateChangeResult stateChangeResult = state.toBeingConnect();
        if (!stateChangeResult.isChange()) {
            throw new IllegalStateException("Failed to execute initReconnect() method. Error:" + stateChangeResult);
        }

        logger.info("{} initReconnect() completed.", objectUniqName);
    }

    private void registerPing() {
        final PingTask pingTask = new PingTask();
        newPingTimeout(pingTask);
    }

    private void newPingTimeout(TimerTask pingTask) {
        this.channelTimer.newTimeout(pingTask, clientOption.getPingDelay(), TimeUnit.MILLISECONDS);
    }

    private class PingTask implements TimerTask {
        @Override
        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled()) {
                newPingTimeout(this);
                return;
            }

            if (state.isClosed()) {
                return;
            }

            writePing();
            newPingTimeout(this);
        }
    }

    void writePing() {
        if (!state.isEnableCommunication()) {
            return;
        }
        logger.debug("{} writePing() started. channel:{}", objectUniqName, channel);

        PingPayloadPacket pingPacket = new PingPayloadPacket(pingIdGenerator.incrementAndGet(), (byte) 0, state.getCurrentStateCode().getId());
        write0(pingPacket, pingWriteFailFutureListener);
    }

    public void sendPing() {
        if (!state.isEnableCommunication()) {
            return;
        }
        logger.debug("{} sendPing() started.", objectUniqName);

        PingPayloadPacket pingPacket = new PingPayloadPacket(pingIdGenerator.incrementAndGet(), (byte) 0, state.getCurrentStateCode().getId());
        ChannelFuture future = write0(pingPacket);
        future.awaitUninterruptibly();
        if (!future.isSuccess()) {
            Throwable cause = future.getCause();
            throw new PinpointSocketException("send ping failed. Error:" + cause.getMessage(), cause);
        }

        logger.debug("{} sendPing() completed.", objectUniqName);
    }

    @Override
    public void send(byte[] bytes) {
        ChannelFuture future = send0(bytes);
        future.addListener(sendWriteFailFutureListener);
    }

    @Override
    public Future sendAsync(byte[] bytes) {
        ChannelFuture channelFuture = send0(bytes);
        final ChannelWriteCompleteListenableFuture future = new ChannelWriteCompleteListenableFuture(clientOption.getTimeoutMillis());
        channelFuture.addListener(future);
        return future;
    }

    @Override
    public void sendSync(byte[] bytes) {
        ChannelFuture write = send0(bytes);
        await(write);
    }

    @Override
    public void response(int requestId, byte[] payload) {
        if (payload == null) {
            throw new NullPointerException("bytes");
        }

        ensureOpen();
        ResponsePacket response = new ResponsePacket(requestId, payload);
        write0(response);
    }

    @Override
    public SocketAddress getRemoteAddress() {
        final Channel channel = this.channel;
        if (channel == null) {
            return null;
        }
        return channel.getRemoteAddress();
    }

    private void await(ChannelFuture channelFuture) {
        try {
            channelFuture.await(clientOption.getTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (channelFuture.isDone()) {
            boolean success = channelFuture.isSuccess();
            if (success) {
                return;
            } else {
                final Throwable cause = channelFuture.getCause();
                throw new PinpointSocketException(cause);
            }
        } else {
            boolean cancel = channelFuture.cancel();
            if (cancel) {
                // if IO not finished in 3 seconds, dose it mean timeout?
                throw new PinpointSocketException("io timeout");
            } else {
                // same logic as above because of success
                boolean success = channelFuture.isSuccess();
                if (success) {
                    return;
                } else {
                    final Throwable cause = channelFuture.getCause();
                    throw new PinpointSocketException(cause);
                }
            }
        }
    }

    private ChannelFuture send0(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }

        ensureOpen();
        SendPacket send = new SendPacket(bytes);

        return write0(send);
    }

    public Future<ResponseMessage> request(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }

        final boolean isEnable = state.isEnableCommunication();
        if (!isEnable) {
            DefaultFuture<ResponseMessage> closedException = new DefaultFuture<ResponseMessage>();
            closedException.setFailure(new PinpointSocketException("invalid state:" + state.getCurrentStateCode() + " channel:" + channel));
            return closedException;
        }
        final int requestId = this.requestManager.nextRequestId();
        final RequestPacket request = new RequestPacket(requestId, bytes);
        final ChannelWriteFailListenableFuture<ResponseMessage> messageFuture = this.requestManager.register(request.getRequestId(), clientOption.getTimeoutMillis());

        write0(request, messageFuture);
        return messageFuture;
    }

    @Override
    public ClientStreamChannelContext openStream(byte[] payload, ClientStreamChannelMessageListener messageListener) {
        return openStream(payload, messageListener, null);
    }

    @Override
    public ClientStreamChannelContext openStream(byte[] payload, ClientStreamChannelMessageListener messageListener, StreamChannelStateChangeEventHandler<ClientStreamChannel> stateChangeListener) {
        ensureOpen();

        PinpointClientHandlerContext context = getChannelContext(channel);
        return context.openStream(payload, messageListener, stateChangeListener);
    }

    @Override
    public StreamChannelContext findStreamChannel(int streamChannelId) {
        ensureOpen();

        PinpointClientHandlerContext context = getChannelContext(channel);
        return context.getStreamChannel(streamChannelId);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        final Object message = e.getMessage();
        if (message instanceof Packet) {
            final Packet packet = (Packet) message;
            final short packetType = packet.getPacketType();
            switch (packetType) {
                case PacketType.APPLICATION_RESPONSE:
                    this.requestManager.messageReceived((ResponsePacket) message, objectUniqName);
                    return;
                // have to handle a request message through connector
                case PacketType.APPLICATION_REQUEST:
                    this.messageListener.handleRequest((RequestPacket) message, pinpointClient);
                    return;
                case PacketType.APPLICATION_SEND:
                    this.messageListener.handleSend((SendPacket) message, pinpointClient);
                    return;
                case PacketType.APPLICATION_STREAM_CREATE:
                case PacketType.APPLICATION_STREAM_CLOSE:
                case PacketType.APPLICATION_STREAM_CREATE_SUCCESS:
                case PacketType.APPLICATION_STREAM_CREATE_FAIL:
                case PacketType.APPLICATION_STREAM_RESPONSE:
                case PacketType.APPLICATION_STREAM_PING:
                case PacketType.APPLICATION_STREAM_PONG:
                    PinpointClientHandlerContext context = getChannelContext(channel);
                    context.handleStreamEvent((StreamPacket) message);
                    return;
                case PacketType.CONTROL_SERVER_CLOSE:
                    handleClosedPacket(e.getChannel());
                    return;
                case PacketType.CONTROL_HANDSHAKE_RESPONSE:
                    handleHandshakePacket((ControlHandshakeResponsePacket) message, e.getChannel());
                    return;
                default:
                    logger.warn("{} messageReceived() failed. unexpectedMessage received:{} address:{}", objectUniqName, message, e.getRemoteAddress());
            }
        } else {
            logger.warn("{} messageReceived() failed. invalid messageReceived:{}", objectUniqName, message);
        }
    }

    private void handleClosedPacket(Channel channel) {
        logger.info("{} handleClosedPacket() started. channel:{}", objectUniqName, channel);

        state.toBeingCloseByPeer();
    }

    private void handleHandshakePacket(ControlHandshakeResponsePacket message, Channel channel) {
        boolean isCompleted = handshaker.handshakeComplete(message);

        logger.info("{} handleHandshakePacket() started. message:{}", objectUniqName, message);

        if (isCompleted) {
            HandshakeResponseCode code = handshaker.getHandshakeResult();

            if (code == HandshakeResponseCode.SUCCESS || code == HandshakeResponseCode.ALREADY_KNOWN) {
                state.toRunSimplex();
            } else if (code == HandshakeResponseCode.DUPLEX_COMMUNICATION || code == HandshakeResponseCode.ALREADY_DUPLEX_COMMUNICATION) {
                remoteClusterOption = handshaker.getClusterOption();
                state.toRunDuplex();
            } else if (code == HandshakeResponseCode.SIMPLEX_COMMUNICATION || code == HandshakeResponseCode.ALREADY_SIMPLEX_COMMUNICATION) {
                state.toRunSimplex();
            } else {
                logger.warn("{} handleHandshakePacket() failed. Error:Invalid Handshake Packet(code:{}).", objectUniqName, code);
                return;
            }

            logger.info("{} handleHandshakePacket() completed. code:{}", channel, code);
        } else if (handshaker.isFinished()) {
            logger.warn("{} handleHandshakePacket() failed. Error:Handshake already completed.");
        } else {
            logger.warn("{} handleHandshakePacket() failed. Error:Handshake not yet started.");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Throwable cause = e.getCause();

        SocketStateCode currentStateCode = state.getCurrentStateCode();
        if (currentStateCode == SocketStateCode.BEING_CONNECT) {
            // removed stackTrace when reconnect. so many logs.
            logger.info("{} exceptionCaught() occurred. state:{}, caused:{}.", objectUniqName, currentStateCode, cause.getMessage());
        } else {
            logger.warn("{} exceptionCaught() occurred. state:{}. Caused:{}", objectUniqName, currentStateCode, cause.getMessage(), cause);
        }

        // need to handle a error more precisely.
        // below code dose not reconnect when node on channel is just hang up or dead without specific reasons.

//        state.setClosed();
//        Channel channel = e.getChannel();
//        if (channel.isConnected()) {
//            channel.close();
//        }
    }

    private void ensureOpen() {
        SocketStateCode currentStateCode = state.getCurrentStateCode();
        if (state.isEnableCommunication(currentStateCode)) {
            return;
        }

        if (state.isReconnect(currentStateCode)) {
            throw new PinpointSocketException("reconnecting...");
        }

        throw new PinpointSocketException("Invalid socket state:" + currentStateCode);
    }

    // Calling this method on a closed PinpointClientHandler has no effect.
    public void close() {
        logger.debug("{} close() started.", objectUniqName);

        SocketStateCode currentStateCode = state.getCurrentStateCode();
        if (currentStateCode.isRun()) {
            state.toBeingClose();
            closeChannel();
        } else if (currentStateCode.isBeforeConnected()) {
            state.toClosed();
            closeResources();
        } else if (currentStateCode.onClose() || currentStateCode.isClosed()) {
            logger.warn("close() failed. Already closed.");
        } else {
            logger.warn("Illegal State :{}.", currentStateCode);
        }
    }

    private void closeChannel() {
        Channel channel = this.channel;
        if (channel != null) {
            sendClosedPacket(channel);

            ChannelFuture closeFuture = channel.close();
            closeFuture.addListener(new WriteFailFutureListener(logger, "close() event failed.", "close() event success."));
            closeFuture.awaitUninterruptibly();
        }
    }

    // Calling this method on a closed PinpointClientHandler has no effect.
    private void closeResources() {
        logger.debug("{} closeResources() started.", objectUniqName);

        Channel channel = this.channel;
        closeStreamChannelManager(channel);
        this.handshaker.handshakeAbort();
        this.requestManager.close();
        this.channelTimer.stop();
    }

    private void closeStreamChannelManager(Channel channel) {
        if (channel == null) {
            logger.debug("channel already set null. skip closeStreamChannelManager().");
            return;
        }

        // stream channel clear and send stream close packet 
        PinpointClientHandlerContext context = getChannelContext(channel);
        if (context != null) {
            context.closeAllStreamChannel();
        }
    }

    private void sendClosedPacket(Channel channel) {
        if (!channel.isConnected()) {
            logger.debug("{} sendClosedPacket() failed. Error:channel already closed.", objectUniqName);
            return;
        }

        logger.debug("{} sendClosedPacket() started.", objectUniqName);

        ClientClosePacket clientClosePacket = new ClientClosePacket();
        ChannelFuture write = write0(clientClosePacket, sendClosePacketFailFutureListener);
        write.awaitUninterruptibly(3000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
        logger.info("{} channelClosed() started.", objectUniqName);

        try {
            boolean factoryReleased = connectionFactory.isClosed();

            boolean needReconnect = false;
            SocketStateCode currentStateCode = state.getCurrentStateCode();
            if (currentStateCode == SocketStateCode.BEING_CLOSE_BY_CLIENT) {
                state.toClosed();
            } else if (currentStateCode == SocketStateCode.BEING_CLOSE_BY_SERVER) {
                needReconnect = state.toClosedByPeer().isChange();
            } else if (currentStateCode.isRun() && factoryReleased) {
                state.toUnexpectedClosed();
            } else if (currentStateCode.isRun()) {
                needReconnect = state.toUnexpectedClosedByPeer().isChange();
            } else if (currentStateCode.isBeforeConnected()) {
                state.toConnectFailed();
            } else {
                state.toErrorUnknown();
            }

            if (needReconnect) {
                reconnect();
            }
        } finally {
            closeResources();
            connectFuture.setResult(Result.FAIL);
        }
    }

    private void reconnect() {
        connectionFactory.reconnect(this.pinpointClient, this.socketAddressProvider);
    }

    private ChannelFuture write0(Object message) {
        return channel.write(message);
    }

    private ChannelFuture write0(Object message, ChannelFutureListener futureListener) {
        if (futureListener == null) {
            throw new NullPointerException("futureListener must not be null");
        }
        ChannelFuture future = channel.write(message);
        future.addListener(futureListener);
        return future;
    }


    @Override
    public ConnectFuture getConnectFuture() {
        return connectFuture;
    }

    @Override
    public SocketStateCode getCurrentStateCode() {
        return state.getCurrentStateCode();
    }

    private PinpointClientHandlerContext getChannelContext(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel must not be null");
        }
        return (PinpointClientHandlerContext) channel.getAttachment();
    }

    @Override
    public boolean isConnected() {
        return this.state.isEnableCommunication();
    }

    @Override
    public boolean isSupportServerMode() {
        return messageListener != SimpleMessageListener.INSTANCE;
    }

    @Override
    public ClusterOption getLocalClusterOption() {
        return localClusterOption;
    }

    @Override
    public ClusterOption getRemoteClusterOption() {
        return remoteClusterOption;
    }

    protected PinpointSocket getPinpointSocket() {
        return pinpointClient;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(objectUniqName);
        sb.append('{');
        sb.append("channel=").append(channel);
        sb.append("state=").append(state);
        sb.append('}');
        return sb.toString();
    }

}