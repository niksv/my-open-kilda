/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FeatureToggleDto {

    @JsonProperty(value = "sync_rules")
    private Boolean syncRulesEnabled;

    @JsonProperty(value = "reflow_on_switch_activation")
    private Boolean reflowOnSwitchActivationEnabled;

    public FeatureToggleDto(
            @JsonProperty(value = "sync_rules") Boolean syncRulesEnabled,
            @JsonProperty(value = "reflow_on_switch_activation") Boolean reflowOnSwitchActivationEnabled) {
        this.syncRulesEnabled = syncRulesEnabled;
        this.reflowOnSwitchActivationEnabled = reflowOnSwitchActivationEnabled;
    }

    public Boolean getSyncRulesEnabled() {
        return syncRulesEnabled;
    }

    public Boolean getReflowOnSwitchActivationEnabled() {
        return reflowOnSwitchActivationEnabled;
    }

    @Override
    public String toString() {
        return "FeatureToggleDto{" +
                "syncRulesEnabled=" + syncRulesEnabled +
                ", reflowOnSwitchActivationEnabled=" + reflowOnSwitchActivationEnabled +
                '}';
    }
}
