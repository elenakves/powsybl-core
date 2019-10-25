/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.action.util;

import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.tasks.ModificationTask;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class SetTargetPDelta implements ModificationTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetTargetPDelta.class);

    private final String generatorId;
    private final float consPuissEff;

    public SetTargetPDelta(String generatorId, float consPuissEff) {
        this.generatorId = generatorId;
        this.consPuissEff = consPuissEff;
    }

    @Override
    public void modify(Network network, ComputationManager computationManager) {
        Objects.requireNonNull(network);
        Generator g = network.getGenerator(generatorId);
        if (g == null) {
            LOGGER.warn("Generator {} not found", generatorId);
        } else {
            if (!g.getTerminal().isConnected()) {
                g.getTerminal().connect();
            }
            g.setTargetP(Math.min(g.getMaxP(), Math.max(g.getMinP(), g.getTargetP() + consPuissEff)));
        }
    }
}
