package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.info.event.SwitchInfoData

import groovy.transform.Memoized
import spock.lang.See

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/grpc-client")
@Tags(HARDWARE)
class GrpcBaseSpecification extends HealthCheckSpecification {
    @Memoized
    List<SwitchInfoData> getNoviflowSwitches() {
        northbound.activeSwitches.findAll {
            // it is not working properly if version <= 6.4
            def matcher = it.description =~ /NW[0-9]+.([0-9].[0-9])/
            return matcher && matcher[0][1] > "6.4"
        }
    }
}
