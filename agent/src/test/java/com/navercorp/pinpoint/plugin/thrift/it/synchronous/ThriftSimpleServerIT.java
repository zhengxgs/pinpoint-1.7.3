/*
 * Copyright 2015 NAVER Corp.
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

package com.navercorp.pinpoint.plugin.thrift.it.synchronous;

import static org.junit.Assert.*;

import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TTransportException;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.navercorp.pinpoint.common.Version;
import com.navercorp.pinpoint.plugin.thrift.common.TestEnvironment;
import com.navercorp.pinpoint.plugin.thrift.common.server.EchoTestServer;
import com.navercorp.pinpoint.plugin.thrift.common.server.SyncEchoTestServer.SyncEchoTestServerFactory;
import com.navercorp.pinpoint.plugin.thrift.it.EchoTestRunner;
import com.navercorp.pinpoint.test.plugin.Dependency;
import com.navercorp.pinpoint.test.plugin.PinpointAgent;
import com.navercorp.pinpoint.test.plugin.PinpointPluginTestSuite;

/**
 * Integration test for TSimpleServer with synchronous processor.
 * 
 * Tests against libthrift 0.9.1+
 * 
 * @author HyunGil Jeong
 */
@RunWith(PinpointPluginTestSuite.class)
@PinpointAgent("agent/target/pinpoint-agent-" + Version.VERSION)
@Dependency({ "org.apache.thrift:libthrift:[0.9.1,)", "log4j:log4j:1.2.16", "org.slf4j:slf4j-log4j12:1.7.22" })
public class ThriftSimpleServerIT extends EchoTestRunner<TSimpleServer> {

    @Override
    protected EchoTestServer<TSimpleServer> createEchoServer(TestEnvironment environment) throws TTransportException {
        return SyncEchoTestServerFactory.simpleServer(environment);
    }

    @Test
    public void testSynchronousRpcCall() throws Exception {
        // Given
        final String expectedMessage = "TEST_MESSAGE";
        // When
        final String result = super.invokeEcho(expectedMessage);
        // Then
        assertEquals(expectedMessage, result);
    }

}
