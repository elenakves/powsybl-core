/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

action('setTargetPFixed5') {
    tasks {
        setTargetPFixed('GEN2', 5.0)
    }
}

action('setTargetPDelta10') {
    tasks {
        setTargetPDelta('GEN2', -100.0)
    }
}
