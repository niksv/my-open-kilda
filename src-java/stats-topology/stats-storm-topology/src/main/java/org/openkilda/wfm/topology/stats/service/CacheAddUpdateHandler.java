/* Copyright 2021 Telstra Open Source
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

import org.openkilda.wfm.topology.stats.model.CookieCacheKey;
import org.openkilda.wfm.topology.stats.model.KildaEntryDescriptor;
import org.openkilda.wfm.topology.stats.model.MeterCacheKey;

import java.util.Map;

public class CacheAddUpdateHandler extends BaseCacheChangeHandler {
    public CacheAddUpdateHandler(
            Map<CookieCacheKey, KildaEntryDescriptor> cookieToEntry, Map<MeterCacheKey,
            KildaEntryDescriptor> meterToEntry) {
        super(cookieToEntry, meterToEntry);
    }

    @Override
    protected void cacheAction(CookieCacheKey key, KildaEntryDescriptor entry) {
        cookieToEntry.put(key, entry);
    }

    @Override
    protected void cacheAction(MeterCacheKey key, KildaEntryDescriptor entry) {
        meterToEntry.put(key, entry);
    }
}
