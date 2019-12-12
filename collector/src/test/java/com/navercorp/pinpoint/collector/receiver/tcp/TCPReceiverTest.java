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

package com.navercorp.pinpoint.collector.receiver.tcp;

import com.google.common.util.concurrent.MoreExecutors;
import com.navercorp.pinpoint.collector.config.AgentBaseDataReceiverConfiguration;
import com.navercorp.pinpoint.collector.config.DeprecatedConfiguration;
import com.navercorp.pinpoint.collector.receiver.AddressFilterAdaptor;
import com.navercorp.pinpoint.collector.receiver.DispatchHandler;
import com.navercorp.pinpoint.common.server.util.AddressFilter;
import com.navercorp.pinpoint.rpc.server.ChannelFilter;
import com.navercorp.pinpoint.rpc.server.PinpointServerAcceptor;
import com.navercorp.pinpoint.thrift.dto.TResult;
import org.apache.thrift.TBase;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.SocketUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * @author emeroad
 */
public class TCPReceiverTest {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Test
    public void server() throws InterruptedException {
        Executor executor = MoreExecutors.directExecutor();

        ChannelFilter channelFilter = new AddressFilterAdaptor(AddressFilter.ALL);
        PinpointServerAcceptor acceptor = new PinpointServerAcceptor(channelFilter);

        AgentBaseDataReceiver tcpReceiver = new AgentBaseDataReceiver(createConfiguration(), executor, acceptor, new DispatchHandler() {

            @Override
            public void dispatchSendMessage(TBase<?, ?> tBase) {
            }

            @Override
            public TBase dispatchRequestMessage(TBase<?, ?> tBase) {
                return new TResult(true);
            }

        });
        try {
            tcpReceiver.start();
        } finally {
            tcpReceiver.stop();
        }
    }

    @Test
    public void l4ip() throws UnknownHostException {
        InetAddress byName = InetAddress.getByName("10.12.13.10");
        logger.debug("byName:{}", byName);
    }

    @Test
    public void l4ipList() throws UnknownHostException {
        String two = "10.12.13.10,10.12.13.20";
        String[] split = two.split(",");
        Assert.assertEquals(split.length, 2);

        String twoEmpty = "10.12.13.10,";
        String[] splitEmpty = twoEmpty.split(",");
        Assert.assertEquals(splitEmpty.length, 1);

    }

    private AgentBaseDataReceiverConfiguration createConfiguration() {
        Properties properties = new Properties();
        properties.put("collector.receiver.base.ip", "0.0.0.0");
        final int availableTcpPort = SocketUtils.findAvailableTcpPort(19099);
        properties.put("collector.receiver.base.port", String.valueOf(availableTcpPort));
        properties.put("collector.receiver.base.worker.threadSize", "8");
        properties.put("collector.receiver.base.worker.queueSize", "1024");

        AgentBaseDataReceiverConfiguration config = new AgentBaseDataReceiverConfiguration(properties, new DeprecatedConfiguration());
        return config;
    }

}
