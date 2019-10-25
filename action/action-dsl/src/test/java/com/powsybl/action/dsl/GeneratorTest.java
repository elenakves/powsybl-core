/**
 * Copyright (c) 2017, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.action.dsl;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import groovy.lang.GroovyCodeSource;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeneratorTest {

    private Network network;
    private Generator g1;
    private Generator g2;

    @Before
    public void setUp() {
        network = EurostagTutorialExample1Factory.create();
        addGensInNetwork(607.0);
        g1 = network.getGenerator("GEN");
        g2 = network.getGenerator("GEN2");
    }

    @Test
    public void testGeneratorSetTargetPFixed5() {
        ActionDb actionDb = new ActionDslLoader(new GroovyCodeSource(getClass().getResource("/generator-action.groovy"))).load(network);
        Action action = actionDb.getAction("setTargetPFixed5"); // scale to 15000
        action.run(network, null);
        assertEquals(5, g2.getTargetP(), 0.0);
    }

    @Test
    public void testGeneratorSetTargetPDelta10() {
        ActionDb actionDb = new ActionDslLoader(new GroovyCodeSource(getClass().getResource("/generator-action.groovy"))).load(network);
        Action action = actionDb.getAction("setTargetPDelta10"); // scale to 15000
        action.run(network, null);
        assertEquals(507, g2.getTargetP(), 0.0);
    }

    private void addGensInNetwork(double initTargetP) {
        VoltageLevel vlgen = network.getVoltageLevel("VLGEN");
        Generator generator2 = vlgen.newGenerator()
                .setId("GEN2")
                .setBus("NGEN")
                .setConnectableBus("NGEN")
                .setMinP(-9999.99)
                .setMaxP(9999.99)
                .setVoltageRegulatorOn(true)
                .setTargetV(24.5)
                .setTargetP(initTargetP)
                .setTargetQ(301.0)
                .add();
    }
}
