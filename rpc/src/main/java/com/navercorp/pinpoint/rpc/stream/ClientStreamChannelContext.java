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

package com.navercorp.pinpoint.rpc.stream;

import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.rpc.packet.stream.StreamCreateFailPacket;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author koo.taejin
 */
public class ClientStreamChannelContext extends StreamChannelContext {

    private final ClientStreamChannel clientStreamChannel;
    private final ClientStreamChannelMessageListener clientStreamChannelMessageListener;
    private final AtomicReference<StreamCreateFailPacket> createFailPacketReference;

    public ClientStreamChannelContext(ClientStreamChannel clientStreamChannel, ClientStreamChannelMessageListener clientStreamChannelMessageListener) {
        Assert.requireNonNull(clientStreamChannel, "clientStreamChannel must not be null");
        Assert.requireNonNull(clientStreamChannelMessageListener, "clientStreamChannelMessageListener must not be null");

        this.clientStreamChannel = clientStreamChannel;
        this.clientStreamChannelMessageListener = clientStreamChannelMessageListener;
        this.createFailPacketReference = new AtomicReference<StreamCreateFailPacket>();
    }

    @Override
    public ClientStreamChannel getStreamChannel() {
        return clientStreamChannel;
    }

    public ClientStreamChannelMessageListener getClientStreamChannelMessageListener() {
        return clientStreamChannelMessageListener;
    }

    public StreamCreateFailPacket getCreateFailPacket() {
        return createFailPacketReference.get();
    }

    public boolean setCreateFailPacket(StreamCreateFailPacket createFailPacket) {
        return this.createFailPacketReference.compareAndSet(null, createFailPacket);
    }

}
