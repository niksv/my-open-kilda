/* Copyright 2022 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.stats.service;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.cookie.CookieBase.CookieType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeterStatsHandlerTest {
    @Test
    public void shouldRefreshCommonFlowsCookieCache() {
        assertEquals("0x80E00000XXXXXXXX", MeterStatsHandler.getCookieTagForPortColorCookie(
                CookieType.LACP_REPLY_INPUT));
    }
}
