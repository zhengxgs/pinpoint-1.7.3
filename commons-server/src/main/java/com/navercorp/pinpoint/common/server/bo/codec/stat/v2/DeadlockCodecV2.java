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

package com.navercorp.pinpoint.common.server.bo.codec.stat.v2;

import com.navercorp.pinpoint.common.buffer.Buffer;
import com.navercorp.pinpoint.common.server.bo.codec.stat.AgentStatCodec;
import com.navercorp.pinpoint.common.server.bo.codec.stat.AgentStatDataPointCodec;
import com.navercorp.pinpoint.common.server.bo.codec.stat.CodecFactory;
import com.navercorp.pinpoint.common.server.bo.codec.stat.header.AgentStatHeaderDecoder;
import com.navercorp.pinpoint.common.server.bo.codec.stat.header.AgentStatHeaderEncoder;
import com.navercorp.pinpoint.common.server.bo.codec.stat.header.BitCountingHeaderEncoder;
import com.navercorp.pinpoint.common.server.bo.codec.stat.strategy.StrategyAnalyzer;
import com.navercorp.pinpoint.common.server.bo.codec.stat.strategy.UnsignedIntegerEncodingStrategy;
import com.navercorp.pinpoint.common.server.bo.codec.strategy.EncodingStrategy;
import com.navercorp.pinpoint.common.server.bo.stat.DeadlockBo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

/**
 * @author Taejin Koo
 */
@Component("deadlockCodecV2")
public class DeadlockCodecV2 extends AgentStatCodecV2<DeadlockBo> {

    @Autowired
    public DeadlockCodecV2(AgentStatDataPointCodec codec) {
        super(new DeadlockCodecFactory(codec));
    }


    private static class DeadlockCodecFactory implements CodecFactory<DeadlockBo> {

        private final AgentStatDataPointCodec codec;

        private DeadlockCodecFactory(AgentStatDataPointCodec codec) {
            Assert.notNull(codec, "codec must not be null");
            this.codec = codec;
        }

        @Override
        public AgentStatDataPointCodec getCodec() {
            return codec;
        }

        @Override
        public CodecEncoder<DeadlockBo> createCodecEncoder() {
            return new DeadlockCodecEncoder(codec);
        }

        @Override
        public CodecDecoder<DeadlockBo> createCodecDecoder() {
            return new DeadlockCodecDecoder(codec);
        }
    }

    private static class DeadlockCodecEncoder implements AgentStatCodec.CodecEncoder<DeadlockBo> {

        private final AgentStatDataPointCodec codec;
        private final UnsignedIntegerEncodingStrategy.Analyzer.Builder deadlockedThreadCountAnalyzerBuilder = new UnsignedIntegerEncodingStrategy.Analyzer.Builder();

        private DeadlockCodecEncoder(AgentStatDataPointCodec codec) {
            Assert.notNull(codec, "codec must not be null");
            this.codec = codec;
        }

        @Override
        public void addValue(DeadlockBo deadlockBo) {
            deadlockedThreadCountAnalyzerBuilder.addValue(deadlockBo.getDeadlockedThreadCount());
        }

        @Override
        public void encode(Buffer valueBuffer) {
            StrategyAnalyzer<Integer> deadlockedThreadIdAnalyzer = deadlockedThreadCountAnalyzerBuilder.build();

            // encode header
            AgentStatHeaderEncoder headerEncoder = new BitCountingHeaderEncoder();
            headerEncoder.addCode(deadlockedThreadIdAnalyzer.getBestStrategy().getCode());

            final byte[] header = headerEncoder.getHeader();
            valueBuffer.putPrefixedBytes(header);

            // encode values
            this.codec.encodeValues(valueBuffer, deadlockedThreadIdAnalyzer.getBestStrategy(), deadlockedThreadIdAnalyzer.getValues());
        }

    }

    private static class DeadlockCodecDecoder implements AgentStatCodec.CodecDecoder<DeadlockBo> {

        private final AgentStatDataPointCodec codec;

        private List<Integer> deadlockedThreadCountList;

        public DeadlockCodecDecoder(AgentStatDataPointCodec codec) {
            Assert.notNull(codec, "codec must not be null");
            this.codec = codec;
        }

        @Override
        public void decode(Buffer valueBuffer, AgentStatHeaderDecoder headerDecoder, int valueSize) {
            EncodingStrategy<Integer> deadlockedThreadCountEncodingStrategy = UnsignedIntegerEncodingStrategy.getFromCode(headerDecoder.getCode());

            // decode values
            this.deadlockedThreadCountList = codec.decodeValues(valueBuffer, deadlockedThreadCountEncodingStrategy, valueSize);
        }

        @Override
        public DeadlockBo getValue(int index) {
            DeadlockBo deadlockBo = new DeadlockBo();
            deadlockBo.setDeadlockedThreadCount(deadlockedThreadCountList.get(index));
            return deadlockBo;
        }

    }

}
