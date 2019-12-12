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

package com.navercorp.pinpoint.web.calltree.span;

import com.navercorp.pinpoint.common.server.bo.AnnotationBo;
import com.navercorp.pinpoint.common.server.bo.ApiMetaDataBo;
import com.navercorp.pinpoint.common.server.bo.MethodTypeEnum;
import com.navercorp.pinpoint.common.server.bo.SpanBo;
import com.navercorp.pinpoint.common.trace.AnnotationKey;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.util.AnnotationKeyUtils;
import com.navercorp.pinpoint.common.util.TransactionId;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jaehong.kim
 */
public class MetaSpanCallTreeFactory {
    private static final long DEFAULT_TIMEOUT_MILLISEC = 60 * 1000;

    private long timeoutMillisec = DEFAULT_TIMEOUT_MILLISEC;

    public CallTree unknown(final long startTimeMillis) {
        final SpanBo rootSpan = new SpanBo();
        rootSpan.setTransactionId(new TransactionId("UNKNOWN", 0, 0));
        rootSpan.setAgentId("UNKNOWN");
        rootSpan.setApplicationId("UNKNOWN");
        rootSpan.setStartTime(startTimeMillis);
        rootSpan.setServiceType(ServiceType.UNKNOWN.getCode());

        List<AnnotationBo> annotations = new ArrayList<>();
        ApiMetaDataBo apiMetaData = new ApiMetaDataBo();
        apiMetaData.setLineNumber(-1);
        apiMetaData.setApiInfo("Unknown");
        apiMetaData.setMethodTypeEnum(MethodTypeEnum.WEB_REQUEST);

        final AnnotationBo apiMetaDataAnnotation = new AnnotationBo();
        apiMetaDataAnnotation.setKey(AnnotationKey.API_METADATA.getCode());
        apiMetaDataAnnotation.setValue(apiMetaData);
        annotations.add(apiMetaDataAnnotation);

        final AnnotationBo argumentAnnotation = new AnnotationBo();
        argumentAnnotation.setKey(AnnotationKeyUtils.getArgs(0).getCode());
        argumentAnnotation.setValue("No Agent Data");
        annotations.add(argumentAnnotation);
        rootSpan.setAnnotationBoList(annotations);

        return new MetaSpanCallTree(new SpanAlign(rootSpan, true));
    }

    public SpanCallTree corrupted(final String title, final long parentSpanId, final long spanId, final long startTimeMillis) {
        final SpanBo rootSpan = new SpanBo();
        rootSpan.setParentSpanId(parentSpanId);
        rootSpan.setSpanId(spanId);
        rootSpan.setStartTime(startTimeMillis);

        rootSpan.setTransactionId(new TransactionId("CORRUPTED", 0, 0));
        rootSpan.setAgentId("CORRUPTED");
        rootSpan.setApplicationId("CORRUPTED");
        rootSpan.setServiceType(ServiceType.UNKNOWN.getCode());

        List<AnnotationBo> annotations = new ArrayList<>();

        ApiMetaDataBo apiMetaData = new ApiMetaDataBo();
        apiMetaData.setLineNumber(-1);
        apiMetaData.setApiInfo("...");
        apiMetaData.setMethodTypeEnum(MethodTypeEnum.CORRUPTED);

        final AnnotationBo apiMetaDataAnnotation = new AnnotationBo();
        apiMetaDataAnnotation.setKey(AnnotationKey.API_METADATA.getCode());
        apiMetaDataAnnotation.setValue(apiMetaData);
        annotations.add(apiMetaDataAnnotation);

        final AnnotationBo argumentAnnotation = new AnnotationBo();
        argumentAnnotation.setKey(AnnotationKeyUtils.getArgs(0).getCode());
        if (System.currentTimeMillis() - startTimeMillis < timeoutMillisec) {
            argumentAnnotation.setValue("Corrupted(waiting for packet) ");
        } else {
            if (title != null) {
                argumentAnnotation.setValue("Corrupted(" + title + ")");
            } else {
                argumentAnnotation.setValue("Corrupted");
            }
        }
        annotations.add(argumentAnnotation);
        rootSpan.setAnnotationBoList(annotations);
        return new MetaSpanCallTree(new SpanAlign(rootSpan, true));
    }
}