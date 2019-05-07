/**
 * Copyright (c) 2016, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.iidm.api.converter;

import com.powsybl.iidm.api.anonymizer.Anonymizer;
import com.powsybl.iidm.api.anonymizer.FakeAnonymizer;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public abstract class AbstractConverterContext<T> {

    private final Anonymizer anonymizer;

    public AbstractConverterContext(Anonymizer anonymizer) {
        this.anonymizer = anonymizer != null ? anonymizer : new FakeAnonymizer();
    }

    public Anonymizer getAnonymizer() {
        return anonymizer;
    }

    public abstract T getOptions();
}
