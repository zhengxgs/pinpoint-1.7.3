/*
 * Copyright 2018 NAVER Corp.
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

package com.navercorp.pinpoint.plugin.jboss.aspect;

import com.navercorp.pinpoint.bootstrap.context.Header;
import com.navercorp.pinpoint.bootstrap.instrument.aspect.Aspect;
import com.navercorp.pinpoint.bootstrap.instrument.aspect.JointPoint;
import com.navercorp.pinpoint.bootstrap.instrument.aspect.PointCut;
import com.navercorp.pinpoint.common.util.DelegateEnumeration;
import com.navercorp.pinpoint.common.util.EmptyEnumeration;

import java.util.Enumeration;

/**
 * filtering pinpoint header
 *
 * @author emeroad
 * @author jaehong.kim
 */
@Aspect
public abstract class RequestFacadeAspect {

    @PointCut
    public String getHeader(String name) {
        if (Header.startWithPinpointHeader(name)) {
            return null;
        }
        return __getHeader(name);
    }

    @JointPoint
    abstract String __getHeader(String name);

    @PointCut
    public Enumeration getHeaders(String name) {
        if (Header.startWithPinpointHeader(name)) {
            return new EmptyEnumeration();
        }
        return __getHeaders(name);
    }

    @JointPoint
    abstract Enumeration __getHeaders(String name);

    @PointCut
    public Enumeration getHeaderNames() {
        return new DelegateEnumeration(__getHeaderNames(), Header.FILTER);
    }

    @JointPoint
    abstract Enumeration __getHeaderNames();
}