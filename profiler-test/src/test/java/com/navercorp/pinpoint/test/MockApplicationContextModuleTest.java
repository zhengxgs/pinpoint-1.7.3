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

package com.navercorp.pinpoint.test;

import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.navercorp.pinpoint.bootstrap.AgentOption;
import com.navercorp.pinpoint.bootstrap.DefaultAgentOption;
import com.navercorp.pinpoint.bootstrap.config.DefaultProfilerConfig;
import com.navercorp.pinpoint.bootstrap.config.ProfilerConfig;
import com.navercorp.pinpoint.common.service.DefaultAnnotationKeyRegistryService;
import com.navercorp.pinpoint.common.service.DefaultServiceTypeRegistryService;
import com.navercorp.pinpoint.profiler.AgentInfoSender;
import com.navercorp.pinpoint.profiler.ClassFileTransformerDispatcher;
import com.navercorp.pinpoint.profiler.context.module.ApplicationContextModule;
import com.navercorp.pinpoint.profiler.context.module.DefaultApplicationContext;
import com.navercorp.pinpoint.profiler.context.module.ModuleFactory;
import com.navercorp.pinpoint.profiler.interceptor.registry.InterceptorRegistryBinder;
import org.junit.Assert;
import org.junit.Test;

import java.net.URL;

/**
 * @author Woonduk Kang(emeroad)
 */
public class MockApplicationContextModuleTest {

    @Test
    public void test() {
        ProfilerConfig profilerConfig = new DefaultProfilerConfig();

        AgentOption agentOption = new DefaultAgentOption(new DummyInstrumentation(),
                "mockAgent", "mockApplicationName", profilerConfig, new URL[0],
                null, new DefaultServiceTypeRegistryService(), new DefaultAnnotationKeyRegistryService());

        PluginTestAgent pluginTestAgent = new PluginTestAgent(agentOption);
        try {
            pluginTestAgent.start();
        } finally {
            pluginTestAgent.stop(true);
        }
    }

    @Test
    public void testMockApplicationContext() {
        ProfilerConfig profilerConfig = new DefaultProfilerConfig();
        InterceptorRegistryBinder binder = new TestInterceptorRegistryBinder();
        AgentOption agentOption = new DefaultAgentOption(new DummyInstrumentation(),
                "mockAgent", "mockApplicationName", profilerConfig, new URL[0],
                null, new DefaultServiceTypeRegistryService(), new DefaultAnnotationKeyRegistryService());

        ModuleFactory moduleFactory = new ModuleFactory() {
            @Override
            public Module newModule(AgentOption agentOption, InterceptorRegistryBinder interceptorRegistryBinder) {

                Module module = new ApplicationContextModule(agentOption, interceptorRegistryBinder);
                Module pluginModule = new PluginApplicationContextModule();

                return Modules.override(module).with(pluginModule);
            }
        };

        DefaultApplicationContext applicationContext = new DefaultApplicationContext(agentOption, binder, moduleFactory);

        Injector injector = applicationContext.getInjector();
        // singleton check
        AgentInfoSender instance1 = injector.getInstance(AgentInfoSender.class);
        AgentInfoSender instance2 = injector.getInstance(AgentInfoSender.class);
        Assert.assertSame(instance1, instance2);

        ClassFileTransformerDispatcher instance4 = injector.getInstance(ClassFileTransformerDispatcher.class);
    }



}