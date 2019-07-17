/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.ucte.converter;

import com.powsybl.commons.datasource.*;
import com.powsybl.entsoe.util.MergedXnode;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.ucte.network.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static com.powsybl.ucte.converter.util.UcteConstants.GEOGRAPHICAL_NAME_PROPERTY_KEY;
import static org.junit.Assert.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

public class UcteExporterTest {

    private static Network transfomerRegulationNetwork;
    private static Network exportTestNetwork;
    private static Network iidmNetwork;
    private static Network iidmTieLineNetwork;
    private static Network iidmSwitchNetwork;
    private static Network mergedNetwork;
    private UcteExporter ucteExporter = new UcteExporter();

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    /**
     * Utility method to load a network file from resource directory without calling
     * @param filePath path of the file relative to resources directory
     * @return imported network
     */
    private static Network loadNetworkFromResourceFile(String filePath) {
        ReadOnlyDataSource dataSource = new ResourceDataSource(FilenameUtils.getBaseName(filePath), new ResourceSet(FilenameUtils.getPath(filePath), FilenameUtils.getName(filePath)));
        return new UcteImporter().importData(dataSource, NetworkFactory.findDefault(), null);
    }

    @BeforeClass
    public static void setUpBeforeClass() {
        transfomerRegulationNetwork = loadNetworkFromResourceFile("/transformerRegulation.uct");
        exportTestNetwork = loadNetworkFromResourceFile("/exportTest.uct");
        iidmNetwork = EurostagTutorialExample1Factory.create();
        iidmNetwork.getLine("NHV1_NHV2_1").getProperties().setProperty(GEOGRAPHICAL_NAME_PROPERTY_KEY, "geographicalName");
        createTieLineNetwork();
        createNetworkWithSwitch();
        mergedNetwork = Network.create("Merged network", "UCTE");
        mergedNetwork.merge(loadNetworkFromResourceFile("/frTestGridForMerging.uct"));
        mergedNetwork.merge(loadNetworkFromResourceFile("/beTestGridForMerging.uct"));
    }

    @Test
    public void exportUcteTest() throws IOException {
        MemDataSource exportedDataSource = new MemDataSource();
        new UcteExporter().export(exportTestNetwork, null, exportedDataSource);
        try (Reader exportedData = new InputStreamReader(new ByteArrayInputStream(exportedDataSource.getData(null, "uct")))) {
            Reader expectedData = new InputStreamReader(UcteExporter.class.getResourceAsStream("/expectedExport.uct"));
            assertTrue(IOUtils.contentEqualsIgnoreEOL(expectedData, exportedData));
        }

        exception.expect(IllegalArgumentException.class);
        new UcteExporter().export(null, null, null);
    }

    @Test
    public void convertUcteElementIdTest() {
        TwoWindingsTransformer twoWindingsTransformer = transfomerRegulationNetwork.getTwoWindingsTransformer("0BBBBB5  0AAAAA2  1");

        Map<String, UcteElementId> iidmIdToUcteElementId = new HashMap<>();

        Terminal terminal1 = twoWindingsTransformer.getTerminal1();
        Terminal terminal2 = twoWindingsTransformer.getTerminal2();

        UcteNodeCode ucteNodeCode1 = new UcteNodeCode(UcteCountryCode.ME, "BBBBB", UcteVoltageLevelCode.VL_110, ' ');
        UcteNodeCode ucteNodeCode2 = new UcteNodeCode(UcteCountryCode.ME, "AAAAA", UcteVoltageLevelCode.VL_220, ' ');

        UcteElementId ucteElementId1 = new UcteElementId(ucteNodeCode1, ucteNodeCode2, '1');
        UcteElementId ucteElementId2 = new UcteElementId(ucteNodeCode2, ucteNodeCode1, '1');

        assertEquals(ucteElementId1, ucteExporter.convertUcteElementId(ucteNodeCode1, ucteNodeCode2, twoWindingsTransformer.getId(), terminal1, terminal2, iidmIdToUcteElementId));
        assertNotEquals(ucteElementId2, ucteExporter.convertUcteElementId(ucteNodeCode1, ucteNodeCode2, twoWindingsTransformer.getId(), terminal1, terminal2, iidmIdToUcteElementId));
    }

    @Test
    public void convertSwitchTest() {
        UcteNetwork ucteNetwork = new UcteNetworkImpl();
        Map<String, UcteNodeCode> iidmIdToUcteNodeCodeId = new HashMap<>();
        Map<String, UcteElementId> iidmIdToUcteElementId = new HashMap<>();
        assertEquals(0, ucteNetwork.getLines().size());
        ucteExporter.convertSwitches(ucteNetwork, iidmSwitchNetwork.getVoltageLevel("VL1"), iidmIdToUcteNodeCodeId, iidmIdToUcteElementId);
        assertEquals(1, ucteNetwork.getLines().size());
        assertEquals("EVL1  1a EVL1  1b a", ucteNetwork.getLines().toArray()[0].toString());
        ucteExporter.convertSwitches(ucteNetwork, iidmSwitchNetwork.getVoltageLevel("VL2"), iidmIdToUcteNodeCodeId, iidmIdToUcteElementId);
        assertEquals(2, ucteNetwork.getLines().size());
        assertEquals("FVL2  1a FVL2  1b a", ucteNetwork.getLines().toArray()[1].toString());
    }

    @Test
    public void getFormatTest() {
        assertEquals("UCTE", ucteExporter.getFormat());
        assertNotEquals("IIDM", ucteExporter.getFormat());
    }

    @Test
    public void getCommentTest() {
        assertEquals("IIDM to UCTE converter", ucteExporter.getComment());
        assertNotEquals("UCTE to IIDM converter", ucteExporter.getComment());
    }

    @Test
    public void ucteSourcedMergeExportTest() {
        MemDataSource exportedDataSource = new MemDataSource();
        new UcteExporter().export(mergedNetwork, null, exportedDataSource);
    }

    private static void createTieLineNetwork() {
        iidmTieLineNetwork = Network.create("iidmTieLineNetwork", "test");
        Substation s1 = iidmTieLineNetwork.newSubstation()
                .setId("s1")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("vl1")
                .setNominalV(380.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Bus b1 = vl1.getBusBreakerView().newBus()
                .setId("b1")
                .add();
        vl1.newGenerator()
                .setId("g1")
                .setBus("b1")
                .setConnectableBus("b1")
                .setTargetP(100.0)
                .setTargetV(400.0)
                .setVoltageRegulatorOn(true)
                .setMinP(50.0)
                .setMaxP(150.0)
                .add();
        Substation s2 = iidmTieLineNetwork.newSubstation()
                .setId("s2")
                .setCountry(Country.BE)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("vl2")
                .setNominalV(380.0)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("b2")
                .add();
        vl2.newLoad()
                .setId("ld1")
                .setConnectableBus("b2")
                .setBus("b2")
                .setP0(0.0)
                .setQ0(0.0)
                .add();
        iidmTieLineNetwork.newTieLine()
                .setId("l1 + l2")
                .setVoltageLevel1("vl1")
                .setConnectableBus1("b1")
                .setBus1("b1")
                .setVoltageLevel2("vl2")
                .setConnectableBus2("b2")
                .setBus2("b2")
                .line1()
                .setId("l1")
                .setR(1.0)
                .setX(1.0)
                .setG1(0.0)
                .setG2(0.0)
                .setB1(0.0)
                .setB2(0.0)
                .setXnodeP(0.0)
                .setXnodeQ(0.0)
                .line2()
                .setId("l2")
                .setR(1.0)
                .setX(1.0)
                .setG1(0.0)
                .setG2(0.0)
                .setB1(0.0)
                .setB2(0.0)
                .setXnodeP(0.0)
                .setXnodeQ(0.0)
                .setUcteXnodeCode("XNODE")
                .add().addExtension(MergedXnode.class,
                new MergedXnode(iidmTieLineNetwork.getLine("l1 + l2"), 1, 1, 1, 0, 0, 0,
                        "l1", "l2", "testXnode"));

        iidmTieLineNetwork.getLine("l1 + l2").newCurrentLimits1()
                .setPermanentLimit(100)
                .beginTemporaryLimit()
                .setName("5'")
                .setAcceptableDuration(5 * 60)
                .setValue(1400)
                .endTemporaryLimit()
                .add();

        iidmTieLineNetwork.getLine("l1 + l2").newCurrentLimits2()
                .setPermanentLimit(150)
                .beginTemporaryLimit()
                .setName("6'")
                .setAcceptableDuration(5 * 60)
                .setValue(1400)
                .endTemporaryLimit()
                .add();
    }

    private static void createNetworkWithSwitch() {
        // For the buses to be valid they have to be connected to at least one branch
        iidmSwitchNetwork = Network.create("iidmSwitchNetwork", "test");
        Substation s1 = iidmSwitchNetwork.newSubstation()
                .setId("S1")
                .setCountry(Country.ES)
                .add();
        VoltageLevel vl1 = s1.newVoltageLevel()
                .setId("VL1")
                .setNominalV(400f)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        Substation s2 = iidmSwitchNetwork.newSubstation()
                .setId("S2")
                .setCountry(Country.FR)
                .add();
        VoltageLevel vl2 = s2.newVoltageLevel()
                .setId("VL2")
                .setNominalV(400f)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("B1a")
                .add();
        vl1.newLoad()
                .setId("L1")
                .setBus("B1a")
                .setP0(1)
                .setQ0(0)
                .add();
        vl1.getBusBreakerView().newBus()
                .setId("B1b")
                .add();
        vl1.newGenerator()
                .setId("G1")
                .setBus("B1b")
                .setMinP(0)
                .setMaxP(1)
                .setTargetP(1)
                .setTargetQ(0)
                .setVoltageRegulatorOn(false)
                .add();
        vl1.getBusBreakerView().newSwitch()
                .setId("SW")
                .setOpen(false)
                .setBus1("B1a")
                .setBus2("B1b")
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("B2")
                .add();
        vl2.newLoad()
                .setId("L2")
                .setBus("B2")
                .setP0(1)
                .setQ0(0)
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("B2a")
                .add();
        vl2.getBusBreakerView().newBus()
                .setId("B2b")
                .add();
        vl2.getBusBreakerView().newSwitch()
                .setId("IdWithMoreThan18CharacterButStillNonUcteCompliant")
                .setOpen(false)
                .setBus1("B2a")
                .setBus2("B2b")
                .add();
    }
}
