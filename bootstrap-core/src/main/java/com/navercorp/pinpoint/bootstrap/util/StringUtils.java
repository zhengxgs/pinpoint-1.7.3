/*
 * Copyright 2014 NAVER Corp.
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

package com.navercorp.pinpoint.bootstrap.util;


import java.util.List;

/**
 * @deprecated Since 1.7.0. Use {@link com.navercorp.pinpoint.common.util.StringUtils}
 */
@Deprecated
public final class StringUtils {

    /**
     * @deprecated Since 1.7.0. Use {@link com.navercorp.pinpoint.common.util.StringUtils#defaultString(String, String)}
     */
    public static String defaultString(final String str, final String defaultStr) {
        return com.navercorp.pinpoint.common.util.StringUtils.defaultString(str, defaultStr);
    }

    /**
     * @deprecated Since 1.7.0. Use {@link com.navercorp.pinpoint.common.util.StringUtils#isEmpty(String)}
     */
    public static boolean isEmpty(String string) {
        return com.navercorp.pinpoint.common.util.StringUtils.isEmpty(string);
    }

    /**
     * @deprecated Since 1.7.0. Use {@link com.navercorp.pinpoint.common.util.StringUtils#toString(Object)}
     */
    public static String toString(final Object object) {
        return com.navercorp.pinpoint.common.util.StringUtils.toString(object);
    }

    /**
     * @deprecated Since 1.7.0. Use {@link com.navercorp.pinpoint.common.util.StringUtils#tokenizeToStringList(String, String)}
     */
    @Deprecated
    public static List<String> splitAndTrim(final String value, final String separator) {
        return com.navercorp.pinpoint.common.util.StringUtils.tokenizeToStringList(value, separator);
    }

    /**
     * @deprecated Since 1.6.1. Use {@link com.navercorp.pinpoint.common.util.StringUtils#abbreviate(String)}
     */
    @Deprecated
    public static String drop(final String str) {
        return com.navercorp.pinpoint.common.util.StringUtils.abbreviate(str);
    }

    /**
     * @deprecated Since 1.6.1. Use {@link com.navercorp.pinpoint.common.util.StringUtils#abbreviate(String, int)}
     */
    @Deprecated
    public static String drop(final String str, final int maxWidth) {
        return com.navercorp.pinpoint.common.util.StringUtils.abbreviate(str, maxWidth);
    }


    /**
     * @deprecated Since 1.6.1. Use {@link com.navercorp.pinpoint.common.util.StringUtils#appendAbbreviate(StringBuilder, String, int)}
     */
    @Deprecated
    public static void appendDrop(final StringBuilder builder, final String str, final int maxWidth) {
        com.navercorp.pinpoint.common.util.StringUtils.appendAbbreviate(builder, str, maxWidth);
    }

}
