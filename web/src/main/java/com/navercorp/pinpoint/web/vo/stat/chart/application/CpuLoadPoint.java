/*
 * Copyright 2017 NAVER Corp.
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
package com.navercorp.pinpoint.web.vo.stat.chart.application;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.navercorp.pinpoint.common.server.bo.stat.join.JoinCpuLoadBo;
import com.navercorp.pinpoint.web.view.CpuLoadPointSerializer;
import com.navercorp.pinpoint.web.vo.chart.Point;

/**
 * @author minwoo.jung
 */
@JsonSerialize(using = CpuLoadPointSerializer.class)
public class CpuLoadPoint implements Point {

    private final long xVal;
    private final double yValForMin;
    private final String  agentIdForMin;
    private final double yValForMax;
    private final String agentIdForMax;
    private final double yValForAvg;

    public CpuLoadPoint(long xVal, double yValForMin, String agentIdForMin, double yValForMax, String agentIdForMax, double yValForAvg) {
        this.xVal = xVal;
        this.yValForMin = yValForMin;
        this.agentIdForMin = agentIdForMin;
        this.yValForMax = yValForMax;
        this.agentIdForMax = agentIdForMax;
        this.yValForAvg = yValForAvg;
    }

    public double getYValForMin() {
        return yValForMin;
    }

    public String getAgentIdForMin() {
        return agentIdForMin;
    }

    public double getYValForMax() {
        return yValForMax;
    }

    public String getAgentIdForMax() {
        return agentIdForMax;
    }

    public double getYValForAvg() {
        return yValForAvg;
    }

    @Override
    public long getXVal() {
        return this.xVal;
    }

    public static class UncollectedCpuLoadPointCreator implements UncollectedPointCreator<CpuLoadPoint> {
        @Override
        public CpuLoadPoint createUnCollectedPoint(long xVal) {
            return new CpuLoadPoint(xVal, JoinCpuLoadBo.UNCOLLECTED_VALUE, JoinCpuLoadBo.UNKNOWN_AGENT, JoinCpuLoadBo.UNCOLLECTED_VALUE, JoinCpuLoadBo.UNKNOWN_AGENT, JoinCpuLoadBo.UNCOLLECTED_VALUE);
        }
    }
}
