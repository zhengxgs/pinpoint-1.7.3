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
package com.navercorp.pinpoint.web.controller;

import com.navercorp.pinpoint.web.service.stat.ApplicationActiveTraceService;
import com.navercorp.pinpoint.web.service.stat.ApplicationCpuLoadService;
import com.navercorp.pinpoint.web.service.stat.ApplicationDataSourceService;
import com.navercorp.pinpoint.web.service.stat.ApplicationMemoryService;
import com.navercorp.pinpoint.web.service.stat.ApplicationResponseTimeService;
import com.navercorp.pinpoint.web.service.stat.ApplicationStatChartService;
import com.navercorp.pinpoint.web.service.stat.ApplicationTransactionService;
import com.navercorp.pinpoint.web.util.TimeWindow;
import com.navercorp.pinpoint.web.util.TimeWindowSlotCentricSampler;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.stat.chart.StatChart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * @author minwoo.jung
 */
public class ApplicationStatController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ApplicationStatChartService applicationStatChartService;

    public ApplicationStatController(ApplicationStatChartService applicationStatChartService) {
        this.applicationStatChartService = applicationStatChartService;
    }

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public StatChart getAgentStatChart(@RequestParam("applicationId") String applicationId, @RequestParam("from") long from, @RequestParam("to") long to) {
        TimeWindowSlotCentricSampler sampler = new TimeWindowSlotCentricSampler();
        TimeWindow timeWindow = new TimeWindow(new Range(from, to), sampler);
        try {
            return this.applicationStatChartService.selectApplicationChart(applicationId, timeWindow);
        } catch (Exception e ) {
            logger.error("error" , e);
            throw e;
        }
    }

    @Controller
    @RequestMapping("/getApplicationStat/cpuLoad/chart")
    public static class ApplicationCpuLoadController extends ApplicationStatController {
        @Autowired
        public ApplicationCpuLoadController(ApplicationCpuLoadService applicationCpuLoadService) {
            super(applicationCpuLoadService);
        }
    }

    @Controller
    @RequestMapping("/getApplicationStat/memory/chart")
    public static class ApplicationMemoryController extends ApplicationStatController {
        @Autowired
        public ApplicationMemoryController(ApplicationMemoryService applicationMemoryService) {
            super(applicationMemoryService);
        }
    }

    @Controller
    @RequestMapping("/getApplicationStat/transaction/chart")
    public static class ApplicationTransactionController extends ApplicationStatController {
        @Autowired
        public ApplicationTransactionController(ApplicationTransactionService applicationTransactionService) {
            super(applicationTransactionService);
        }
    }

    @Controller
    @RequestMapping("/getApplicationStat/activeTrace/chart")
    public static class ApplicationActiveTraceController extends ApplicationStatController {
        @Autowired
        public ApplicationActiveTraceController(ApplicationActiveTraceService applicationActiveTraceService) {
            super(applicationActiveTraceService);
        }
    }

    @Controller
    @RequestMapping("/getApplicationStat/responseTime/chart")
    public static class ApplicationResponseTimeController extends ApplicationStatController {
        @Autowired
        public ApplicationResponseTimeController(ApplicationResponseTimeService applicationResponseTimeService) {
            super(applicationResponseTimeService);
        }
    }

    @Controller
    @RequestMapping("/getApplicationStat/dataSource/chart")
    public static class ApplicationDataSourceController {

        private final Logger logger = LoggerFactory.getLogger(ApplicationDataSourceController.this.getClass());
        private ApplicationDataSourceService applicationDataSourceService;

        @Autowired
        public ApplicationDataSourceController(ApplicationDataSourceService applicationDataSourceService) {
            ApplicationDataSourceController.this.applicationDataSourceService = applicationDataSourceService;
        }

        @RequestMapping(method = RequestMethod.GET)
        @ResponseBody
        public List<StatChart> getAgentStatChart(@RequestParam("applicationId") String applicationId, @RequestParam("from") long from, @RequestParam("to") long to) {
            TimeWindowSlotCentricSampler sampler = new TimeWindowSlotCentricSampler();
            TimeWindow timeWindow = new TimeWindow(new Range(from, to), sampler);
            try {
                return this.applicationDataSourceService.selectApplicationChart(applicationId, timeWindow);
            } catch (Exception e ) {
                logger.error("error" , e);
                throw e;
            }
        }
    }
}

