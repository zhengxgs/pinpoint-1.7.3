/*
 * Copyright 2017 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.navercorp.pinpoint.collector.cluster.flink;

import com.navercorp.pinpoint.common.util.Assert;
import com.navercorp.pinpoint.profiler.sender.TcpDataSender;

/**
 * @author minwoo.jung
 */
public class SenderContext {
    private TcpDataSender tcpDataSender;

    public SenderContext(TcpDataSender tcpDataSender) {
        this.tcpDataSender = Assert.requireNonNull(tcpDataSender, "tcpDataSender must not be null");
    }

    public TcpDataSender getTcpDataSender() {
        return tcpDataSender;
    }

    public void close() {
        tcpDataSender.stop();
    }

}
