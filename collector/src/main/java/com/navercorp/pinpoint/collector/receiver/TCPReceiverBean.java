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

package com.navercorp.pinpoint.collector.receiver;

import com.navercorp.pinpoint.collector.receiver.tcp.DefaultTCPPacketHandlerFactory;
import com.navercorp.pinpoint.collector.receiver.tcp.TCPPacketHandler;
import com.navercorp.pinpoint.collector.receiver.tcp.TCPPacketHandlerFactory;
import com.navercorp.pinpoint.collector.receiver.tcp.TCPReceiver;
import com.navercorp.pinpoint.common.server.util.AddressFilter;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * @author Woonduk Kang(emeroad)
 */
public class TCPReceiverBean implements InitializingBean, DisposableBean, BeanNameAware {
    private String beanName;

    private boolean enable = true;

    private String bindIp;
    private int bindPort;

    private TCPReceiver tcpReceiver;
    private Executor executor;

    private DispatchHandler dispatchHandler;
    private AddressFilter addressFilter;

    private TCPPacketHandlerFactory tcpPacketHandlerFactory;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!enable) {
            return;
        }
        Objects.requireNonNull(beanName, "beanName must not be null");
        Objects.requireNonNull(bindIp, "bindIp must not be null");
        Objects.requireNonNull(dispatchHandler, "dispatchHandler must not be null");
        Objects.requireNonNull(addressFilter, "addressFilter must not be null");
        Objects.requireNonNull(executor, "executor must not be null");

        tcpReceiver = createTcpReceiver(beanName, this.bindIp, bindPort, executor, dispatchHandler, this.tcpPacketHandlerFactory, addressFilter);
        tcpReceiver.start();
    }


    private TCPReceiver createTcpReceiver(String beanName, String bindIp, int port, Executor executor,
                                          DispatchHandler dispatchHandler, TCPPacketHandlerFactory tcpPacketHandlerFactory, AddressFilter addressFilter) {
        InetSocketAddress bindAddress = new InetSocketAddress(bindIp, port);
        TCPPacketHandler tcpPacketHandler = wrapDispatchHandler(dispatchHandler, tcpPacketHandlerFactory);

        return new TCPReceiver(beanName, tcpPacketHandler, executor, bindAddress, addressFilter);
    }

    private TCPPacketHandler wrapDispatchHandler(DispatchHandler dispatchHandler, TCPPacketHandlerFactory tcpPacketHandlerFactory) {
        if (tcpPacketHandlerFactory == null) {
            // using default Factory
            tcpPacketHandlerFactory = new DefaultTCPPacketHandlerFactory();
        }
        return tcpPacketHandlerFactory.build(dispatchHandler);
    }


    @Override
    public void destroy() throws Exception {
        if (!enable) {
            return;
        }
        if (tcpReceiver != null) {
            tcpReceiver.shutdown();
        }
    }

    public void setExecutor(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public void setDispatchHandler(DispatchHandler dispatchHandler) {
        this.dispatchHandler = dispatchHandler;
    }

    public void setTcpPacketHandlerFactory(TCPPacketHandlerFactory tcpPacketHandlerFactory) {
        this.tcpPacketHandlerFactory = tcpPacketHandlerFactory;
    }

    public void setBindIp(String bindIp) {
        this.bindIp = Objects.requireNonNull(bindIp, "bindIp must not be null");
    }

    public void setBindPort(int bindPort) {
        this.bindPort = bindPort;
    }


    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public void setAddressFilter(AddressFilter addressFilter) {
        this.addressFilter = addressFilter;
    }
}
