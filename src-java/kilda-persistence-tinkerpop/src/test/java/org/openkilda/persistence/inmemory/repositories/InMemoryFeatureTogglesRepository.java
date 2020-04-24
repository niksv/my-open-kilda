/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.inmemory.repositories;

import org.openkilda.model.FeatureToggles.FeatureTogglesData;
import org.openkilda.persistence.exceptions.ConstraintViolationException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.ferma.frames.FeatureTogglesFrame;
import org.openkilda.persistence.ferma.repositories.FermaFeatureTogglesRepository;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.persistence.tx.TransactionManager;

/**
 * In-memory implementation of {@link FeatureTogglesRepository}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
public class InMemoryFeatureTogglesRepository extends FermaFeatureTogglesRepository {
    InMemoryFeatureTogglesRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        super(graphFactory, transactionManager);
    }

    @Override
    protected FeatureTogglesFrame doAdd(FeatureTogglesData data) {
        if (find().isPresent()) {
            throw new ConstraintViolationException("Unable to create " + FeatureTogglesFrame.FRAME_LABEL
                    + " vertex with duplicate keys.");
        }

        return super.doAdd(data);
    }
}
