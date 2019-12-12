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

package com.navercorp.pinpoint.web.service.map;

import com.navercorp.pinpoint.common.server.bo.SpanBo;
import com.navercorp.pinpoint.common.server.bo.SpanEventBo;
import com.navercorp.pinpoint.common.service.ServiceTypeRegistryService;
import com.navercorp.pinpoint.common.trace.HistogramSchema;
import com.navercorp.pinpoint.common.trace.HistogramSlot;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.web.applicationmap.rawdata.LinkDataDuplexMap;
import com.navercorp.pinpoint.web.applicationmap.rawdata.LinkDataMap;
import com.navercorp.pinpoint.web.security.ServerMapDataFilter;
import com.navercorp.pinpoint.web.service.ApplicationFactory;
import com.navercorp.pinpoint.web.service.DotExtractor;
import com.navercorp.pinpoint.web.util.TimeWindow;
import com.navercorp.pinpoint.web.util.TimeWindowDownSampler;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.ResponseHistograms;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author HyunGil Jeong
 */
public class FilteredMapBuilder {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ApplicationFactory applicationFactory;

    private final ServiceTypeRegistryService registry;

    private final Range range;

    private final int version;

    private final TimeWindow timeWindow;

    private final LinkDataDuplexMap linkDataDuplexMap;

    private final ResponseHistograms.Builder responseHistogramsBuilder;

    private final DotExtractor dotExtractor;

    // @Nullable
    private ServerMapDataFilter serverMapDataFilter;


    public FilteredMapBuilder(ApplicationFactory applicationFactory, ServiceTypeRegistryService registry, Range range, int version) {
        if (applicationFactory == null) {
            throw new NullPointerException("applicationFactory must not be null");
        }
        if (registry == null) {
            throw new NullPointerException("registry must not be null");
        }
        if (range == null) {
            throw new NullPointerException("range must not be null");
        }
        this.applicationFactory = applicationFactory;
        this.registry = registry;
        this.range = range;
        this.version = version;

        this.timeWindow = new TimeWindow(this.range, TimeWindowDownSampler.SAMPLER);
        this.linkDataDuplexMap = new LinkDataDuplexMap();
        this.responseHistogramsBuilder = new ResponseHistograms.Builder(this.range);
        this.dotExtractor = new DotExtractor(this.applicationFactory);
    }

    public FilteredMapBuilder serverMapDataFilter(ServerMapDataFilter serverMapDataFilter) {
        this.serverMapDataFilter = serverMapDataFilter;
        return this;
    }

    public FilteredMapBuilder addTransactions(List<List<SpanBo>> transactionList) {
        for (List<SpanBo> transaction : transactionList) {
            addTransaction(transaction);
        }
        return this;
    }

    public FilteredMapBuilder addTransaction(List<SpanBo> transaction) {
        final Map<Long, SpanBo> transactionSpanMap = checkDuplicatedSpanId(transaction);

        for (SpanBo span : transaction) {
            final Application parentApplication = createParentApplication(span, transactionSpanMap, version);
            final Application spanApplication = this.applicationFactory.createApplication(span.getApplicationId(), span.getApplicationServiceType());

            // records the Span's response time statistics
            responseHistogramsBuilder.addHistogram(spanApplication, span, span.getCollectorAcceptTime());

            if (!spanApplication.getServiceType().isRecordStatistics() || spanApplication.getServiceType().isRpcClient()) {
                // span's serviceType is probably not set correctly
                logger.warn("invalid span application:{}", spanApplication);
                continue;
            }

            final short slotTime = getHistogramSlotTime(span, spanApplication.getServiceType());
            // might need to reconsider using collector's accept time for link statistics.
            // we need to convert to time window's timestamp. If not, it may lead to OOM due to mismatch in timeslots.
            long timestamp = timeWindow.refineTimestamp(span.getCollectorAcceptTime());

            if (parentApplication.getServiceType().isUser()) {
                // Outbound data
                if (logger.isTraceEnabled()) {
                    logger.trace("span user:{} {} -> span:{} {}", parentApplication, span.getAgentId(), spanApplication, span.getAgentId());
                }
                final LinkDataMap sourceLinkData = linkDataDuplexMap.getSourceLinkDataMap();
                sourceLinkData.addLinkData(parentApplication, span.getAgentId(), spanApplication,  span.getAgentId(), timestamp, slotTime, 1);

                if (logger.isTraceEnabled()) {
                    logger.trace("span target user:{} {} -> span:{} {}", parentApplication, span.getAgentId(), spanApplication, span.getAgentId());
                }
                // Inbound data
                final LinkDataMap targetLinkDataMap = linkDataDuplexMap.getTargetLinkDataMap();
                targetLinkDataMap.addLinkData(parentApplication, span.getAgentId(), spanApplication, span.getAgentId(), timestamp, slotTime, 1);
            } else {
                // Inbound data
                if (logger.isTraceEnabled()) {
                    logger.trace("span target parent:{} {} -> span:{} {}", parentApplication, span.getAgentId(), spanApplication, span.getAgentId());
                }
                final LinkDataMap targetLinkDataMap = linkDataDuplexMap.getTargetLinkDataMap();
                targetLinkDataMap.addLinkData(parentApplication, span.getAgentId(), spanApplication, span.getAgentId(), timestamp, slotTime, 1);
            }

            dotExtractor.addDot(span);

            if (serverMapDataFilter != null && serverMapDataFilter.filter(spanApplication)) {
                continue;
            }

            addNodeFromSpanEvent(span, transactionSpanMap);
        }
        return this;
    }

    private Map<Long, SpanBo> checkDuplicatedSpanId(List<SpanBo> transaction) {
        final Map<Long, SpanBo> transactionSpanMap = new HashMap<>();
        for (SpanBo span : transaction) {
            final SpanBo old = transactionSpanMap.put(span.getSpanId(), span);
            if (old != null) {
                logger.warn("duplicated span found:{}", old);
            }
        }
        return transactionSpanMap;
    }

    private Application createParentApplication(SpanBo span, Map<Long, SpanBo> transactionSpanMap, int version) {
        final SpanBo parentSpan = transactionSpanMap.get(span.getParentSpanId());
        if (span.isRoot() || parentSpan == null) {
            ServiceType spanServiceType = this.registry.findServiceType(span.getServiceType());
            if (spanServiceType.isQueue()) {
                String applicationName = span.getAcceptorHost();
                ServiceType serviceType = spanServiceType;
                return this.applicationFactory.createApplication(applicationName, serviceType);
            } else {
                String applicationName;
                // FIXME magic number, remove after front end UI changes and simply use the newer one
                if (version >= 4) {
                    ServiceType applicationServiceType = this.registry.findServiceType(span.getApplicationServiceType());
                    applicationName = span.getApplicationId() + "_" + applicationServiceType;
                } else {
                    applicationName = span.getApplicationId();
                }
                ServiceType serviceType = this.registry.findServiceType(ServiceType.USER.getCode());
                return this.applicationFactory.createApplication(applicationName, serviceType);
            }
        } else {
            // create virtual queue node if current' span's service type is a queue AND :
            // 1. parent node's application service type is not a queue (it may have come from a queue that is traced)
            // 2. current node's application service type is not a queue (current node may be a queue that is traced)
            ServiceType spanServiceType = this.registry.findServiceType(span.getServiceType());
            if (spanServiceType.isQueue()) {
                ServiceType parentApplicationServiceType = this.registry.findServiceType(parentSpan.getApplicationServiceType());
                ServiceType spanApplicationServiceType = this.registry.findServiceType(span.getApplicationServiceType());
                if (!parentApplicationServiceType.isQueue() && !spanApplicationServiceType.isQueue()) {
                    String parentApplicationName = span.getAcceptorHost();
                    if (parentApplicationName == null) {
                        parentApplicationName = span.getRemoteAddr();
                    }
                    short parentServiceType = span.getServiceType();
                    return this.applicationFactory.createApplication(parentApplicationName, parentServiceType);
                }
            }
            String parentApplicationName = parentSpan.getApplicationId();
            short parentServiceType = parentSpan.getApplicationServiceType();
            return this.applicationFactory.createApplication(parentApplicationName, parentServiceType);
        }
    }

    private void addNodeFromSpanEvent(SpanBo span, Map<Long, SpanBo> transactionSpanMap) {
        /*
         * add span event statistics
         */
        final List<SpanEventBo> spanEventBoList = span.getSpanEventBoList();
        if (CollectionUtils.isEmpty(spanEventBoList)) {
            return;
        }
        final Application srcApplication = applicationFactory.createApplication(span.getApplicationId(), span.getApplicationServiceType());

        LinkDataMap sourceLinkDataMap = linkDataDuplexMap.getSourceLinkDataMap();
        for (SpanEventBo spanEvent : spanEventBoList) {

            ServiceType destServiceType = registry.findServiceType(spanEvent.getServiceType());
            if (!destServiceType.isRecordStatistics()) {
                // internal method
                continue;
            }
            // convert to Unknown if destServiceType is a rpc client and there is no acceptor.
            // acceptor exists if there is a span with spanId identical to the current spanEvent's next spanId.
            // logic for checking acceptor
            if (destServiceType.isRpcClient()) {
                if (!transactionSpanMap.containsKey(spanEvent.getNextSpanId())) {
                    destServiceType = ServiceType.UNKNOWN;
                }
            }

            String dest = spanEvent.getDestinationId();
            if (dest == null) {
                dest = "Unknown";
            }

            final Application destApplication = this.applicationFactory.createApplication(dest, destServiceType);

            final short slotTime = getHistogramSlotTime(spanEvent, destServiceType);

            // FIXME
            final long spanEventTimeStamp = timeWindow.refineTimestamp(span.getStartTime() + spanEvent.getStartElapsed());
            if (logger.isTraceEnabled()) {
                logger.trace("spanEvent  src:{} {} -> dest:{} {}", srcApplication, span.getAgentId(), destApplication, spanEvent.getEndPoint());
            }
            // endPoint may be null
            final String destinationAgentId = StringUtils.defaultString(spanEvent.getEndPoint());
            sourceLinkDataMap.addLinkData(srcApplication, span.getAgentId(), destApplication, destinationAgentId, spanEventTimeStamp, slotTime, 1);
        }
    }

    public FilteredMap build() {
        ResponseHistograms responseHistograms = responseHistogramsBuilder.build();
        return new FilteredMap(linkDataDuplexMap, responseHistograms, dotExtractor);
    }

    private short getHistogramSlotTime(SpanEventBo spanEvent, ServiceType serviceType) {
        return getHistogramSlotTime(spanEvent.hasException(), spanEvent.getEndElapsed(), serviceType);
    }

    private short getHistogramSlotTime(SpanBo span, ServiceType serviceType) {
        boolean allException = span.getErrCode() != 0;
        return getHistogramSlotTime(allException, span.getElapsed(), serviceType);
    }

    private short getHistogramSlotTime(boolean hasException, int elapsedTime, ServiceType serviceType) {
        final HistogramSchema schema = serviceType.getHistogramSchema();
        final HistogramSlot histogramSlot = schema.findHistogramSlot(elapsedTime, hasException);
        return histogramSlot.getSlotTime();
    }
}
