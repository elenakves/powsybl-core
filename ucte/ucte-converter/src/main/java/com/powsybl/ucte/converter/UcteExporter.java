/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.ucte.converter;

import com.google.auto.service.AutoService;
import com.google.common.primitives.Doubles;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.entsoe.util.MergedXnode;
import com.powsybl.iidm.export.Exporter;
import com.powsybl.iidm.network.*;
import com.powsybl.ucte.network.*;
import com.powsybl.ucte.network.io.UcteWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.powsybl.ucte.converter.UcteConstants.*;
import static com.powsybl.ucte.network.UcteNode.DEFAULT_POWER_LIMIT;
import static com.powsybl.ucte.network.UcteNodeCode.isUcteNodeId;
import static com.powsybl.ucte.network.UcteNodeCode.parseUcteNodeCode;
import static com.powsybl.ucte.network.UcteVoltageLevelCode.voltageLevelCodeFromIidmVoltage;

/**
 * @author Abdelsalem HEDHILI < abdelsalem.hedhili at rte-france.com >
 */
@AutoService(Exporter.class)
public class UcteExporter implements Exporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(UcteExporter.class);
    private static final ArrayList POSSIBLE_CHARACTER = new ArrayList(Arrays.asList(
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            '1', '2', '3', '4', '5', '6', '7', '8', '9'));
    private String generatedGeographicalName = "aaaa";
    private static final String NO_COUNTRY_FOUND = ": No country found";

    Map<String, UcteNodeCode> iidmIdToUcteNodeCodeId = new HashMap<>();
    Map<String, UcteElementId> iidmIdToUcteElementId = new HashMap<>();

    @Override
    public String getFormat() {
        return "UCTE";
    }

    @Override
    public String getComment() {
        return "IIDM to UCTE converter";
    }

    @Override
    public void export(Network network, Properties parameters, DataSource dataSource) {
        if (network == null) {
            throw new IllegalArgumentException("network is null");
        }
        UcteNetwork ucteNetwork = createUcteNetwork(network);

        try (OutputStream os = dataSource.newOutputStream("", "uct", false);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            new UcteWriter(ucteNetwork).write(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param network the model
     * @return the UcteNetwork corresponding to the iidm network
     */
    private UcteNetwork createUcteNetwork(Network network) {
        UcteNetwork ucteNetwork = new UcteNetworkImpl();
        ucteNetwork.setVersion(UcteFormatVersion.SECOND);
        network.getSubstationStream().forEach(substation ->
                substation.getVoltageLevelStream().forEach(voltageLevel -> {
                    convertBuses(ucteNetwork, voltageLevel);
                    convertSwitches(ucteNetwork, voltageLevel);
                    voltageLevel.getDanglingLineStream().forEach(danglingLine -> convertDanglingLine(ucteNetwork, danglingLine));
                })
        );
        convertLines(ucteNetwork, network);
        convertTwoWindingsTransformers(network, ucteNetwork);
        if (network.getHvdcConverterStationCount() > 0 || network.getShuntCompensatorCount() > 0
                || network.getStaticVarCompensatorCount() > 0 || network.getBatteryCount() > 0
                || network.getHvdcLineCount() > 0 || network.getVscConverterStationCount() > 0) {
            throw new UcteException("This network contains unknown equipments");
        }
        return ucteNetwork;
    }

    /**
     * Iterates through the given {@link VoltageLevel}, get the buses and calls the method to convert them
     *
     * @param ucteNetwork the target network in ucte
     * @param voltageLevel the iidm VoltageLevel containing the buses we want to convert
     * @see UcteExporter#convertBus(UcteNetwork, Bus)
     */
    private void convertBuses(UcteNetwork ucteNetwork, VoltageLevel voltageLevel) {
        VoltageLevel.BusBreakerView busBreakerView = voltageLevel.getBusBreakerView();
        busBreakerView.getBusStream().forEach(bus -> {
            LOGGER.trace("Converting bus {}", bus.getId());
            convertBus(ucteNetwork, bus);
        });
    }

    /**
     * Gets the needed information from the bus to create the corresponding {@link UcteNode},
     * create the {@link UcteNode} and add it to the {@link UcteNetwork}
     *
     * @param ucteNetwork the target network in ucte
     * @param bus the bus to convert to UCTE
     */
    private void convertBus(UcteNetwork ucteNetwork, Bus bus) {
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        Optional<Country> country = voltageLevel.getSubstation().getCountry();
        if (!country.isPresent()) {
            throw new UcteException("Bus " + bus.getId() + NO_COUNTRY_FOUND);
        }

        Load load = null;
        Optional<Load> optLoad = bus.getLoadStream().findFirst();
        if (optLoad.isPresent()) {
            load = optLoad.get();
        }

        float p0 = 0;
        float q0 = 0;

        if (load == null) {
            p0 = DEFAULT_MAX_CURRENT;
            q0 = DEFAULT_MAX_CURRENT;
        } else {
            Iterable<Load> loads = bus.getLoads();
            for (Load currentLoad : loads) {
                p0 += currentLoad.getP0();
                q0 += currentLoad.getQ0();
            }
        }

        Iterable<Generator> generators = bus.getGenerators();
        ArrayList<Float> minimumPermissibleActivePowerGenerationList = new ArrayList<>();
        ArrayList<Float> maximumPermissibleActivePowerGenerationList = new ArrayList<>();
        ArrayList<Float> minimumPermissibleReactivePowerGenerationList = new ArrayList<>();
        ArrayList<Float> maximumPermissibleReactivePowerGenerationList = new ArrayList<>();
        float activePowerGeneration = 0;
        float reactivePowerGeneration = 0;
        float voltageReference = Float.NaN;
        UctePowerPlantType uctePowerPlantType = null;
        UcteNodeTypeCode ucteNodeTypeCode = UcteNodeTypeCode.PQ;

        if (!generators.iterator().hasNext()) {
            activePowerGeneration = DEFAULT_MAX_CURRENT;
            reactivePowerGeneration = DEFAULT_POWER_LIMIT;
        }
        for (Generator currentGenerator : generators) {
            activePowerGeneration += (float) -currentGenerator.getTargetP();
            reactivePowerGeneration += (float) -currentGenerator.getTargetQ();
            voltageReference = (float) currentGenerator.getTargetV();

            minimumPermissibleActivePowerGenerationList.add((float) -currentGenerator.getMinP());
            maximumPermissibleActivePowerGenerationList.add((float) -currentGenerator.getMaxP());
            minimumPermissibleReactivePowerGenerationList.add((float) -currentGenerator.getReactiveLimits().getMinQ(activePowerGeneration));
            maximumPermissibleReactivePowerGenerationList.add((float) -currentGenerator.getReactiveLimits().getMaxQ(activePowerGeneration));

            if (currentGenerator.isVoltageRegulatorOn()
                    && currentGenerator.getRegulatingTerminal().isConnected()) {
                ucteNodeTypeCode = UcteNodeTypeCode.PU;
            }
            uctePowerPlantType = energySourceToUctePowerPlantType(currentGenerator.getEnergySource());
        }

        Float minimumPermissibleActivePowerGeneration = Float.NaN;
        Float maximumPermissibleActivePowerGeneration = Float.NaN;
        Float minimumPermissibleReactivePowerGeneration = Float.NaN;
        Float maximumPermissibleReactivePowerGeneration = Float.NaN;

        if (!minimumPermissibleActivePowerGenerationList.isEmpty()) {
            minimumPermissibleActivePowerGeneration = Collections.min(minimumPermissibleActivePowerGenerationList) >= DEFAULT_POWER_LIMIT ?
                    Float.NaN : Collections.min(minimumPermissibleActivePowerGenerationList);
        }
        if (!maximumPermissibleActivePowerGenerationList.isEmpty()) {
            maximumPermissibleActivePowerGeneration = Collections.max(maximumPermissibleActivePowerGenerationList) <= -DEFAULT_POWER_LIMIT ?
                    Float.NaN : Collections.max(maximumPermissibleActivePowerGenerationList);
        }
        if (!minimumPermissibleReactivePowerGenerationList.isEmpty()) {
            minimumPermissibleReactivePowerGeneration = Collections.min(minimumPermissibleReactivePowerGenerationList) >= DEFAULT_POWER_LIMIT ?
                    Float.NaN : Collections.min(minimumPermissibleReactivePowerGenerationList);
        }
        if (!maximumPermissibleReactivePowerGenerationList.isEmpty()) {
            maximumPermissibleReactivePowerGeneration = Collections.max(maximumPermissibleReactivePowerGenerationList) <= -DEFAULT_POWER_LIMIT ?
                    Float.NaN : Collections.min(maximumPermissibleReactivePowerGenerationList);
        }

        String geographicalName = null;
        if (bus.getProperties().containsKey(GEOGRAPHICAL_NAME_PROPERTY_KEY)) {
            geographicalName = bus.getProperties().getProperty(GEOGRAPHICAL_NAME_PROPERTY_KEY);
        }

        UcteNodeCode ucteNodeCode = createUcteNodeCode(bus.getId(), voltageLevel, country.get().toString());

        UcteNode ucteNode = new UcteNode(
                ucteNodeCode,
                geographicalName,
                UcteNodeStatus.REAL,
                ucteNodeTypeCode,
                voltageReference,
                p0,
                q0,
                activePowerGeneration,
                reactivePowerGeneration,
                minimumPermissibleActivePowerGeneration,
                maximumPermissibleActivePowerGeneration,
                minimumPermissibleReactivePowerGeneration,
                maximumPermissibleReactivePowerGeneration,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                null
        );
        ucteNode.setPowerPlantType(uctePowerPlantType);
        ucteNetwork.addNode(ucteNode);
    }

    /**
     * Iterates through the {@link Line}s of the iidm {@link Network} and call the method to convert them
     *
     * @param ucteNetwork the target network in ucte
     * @param network the iidm network containing the buses we want to convert
     * @see UcteExporter#convertLine(UcteNetwork, Line)
     */
    private void convertLines(UcteNetwork ucteNetwork, Network network) {
        network.getLineStream().forEach(line -> convertLine(ucteNetwork, line));
    }

    /**
     * Checks if the line is a regular line and if so, get the needed information to create the corresponding {@link UcteLine}
     * and adds it to the {@link UcteNetwork}. Otherwise, if the line is a {@link TieLine} calls another method to convert it
     * ({@link UcteExporter#convertTieLine(UcteNetwork, Line)})
     *
     * @param ucteNetwork The target network in ucte
     * @param line The line to convert to {@link UcteLine}
     * @see UcteExporter#convertTieLine(UcteNetwork, Line)
     */
    private void convertLine(UcteNetwork ucteNetwork, Line line) {
        if (line.isTieLine()) {
            LOGGER.trace("exporting tie line {}", line.getId());
            convertTieLine(ucteNetwork, line);
            return;
        }

        Terminal terminal1 = line.getTerminal1();
        Terminal terminal2 = line.getTerminal2();
        Optional<Country> country1 = terminal1.getVoltageLevel().getSubstation().getCountry();
        Optional<Country> country2 = terminal2.getVoltageLevel().getSubstation().getCountry();

        if (!country1.isPresent() || !country2.isPresent()) {
            throw new UcteException("Tie line " + line.getId() + NO_COUNTRY_FOUND);
        }

        UcteNodeCode ucteTerminal1NodeCode = createUcteNodeCode(
                terminal1.getBusBreakerView().getConnectableBus().getId(),
                terminal1.getVoltageLevel(),
                country1.get().toString());

        UcteNodeCode ucteTerminal2NodeCode = createUcteNodeCode(
                terminal2.getBusBreakerView().getConnectableBus().getId(),
                terminal2.getVoltageLevel(),
                country2.get().toString());

        String elementName = line.getProperties().getProperty(ELEMENT_NAME_PROPERTY_KEY, null);

        UcteElementId lineId = convertUcteElementId(ucteTerminal1NodeCode, ucteTerminal2NodeCode, line.getId(), terminal1, terminal2);
        UcteLine ucteLine = new UcteLine(lineId, UcteElementStatus.REAL_ELEMENT_IN_OPERATION,
                (float) line.getR(), (float) line.getX(), (float) line.getB1() + (float) line.getB2(),
                (int) line.getCurrentLimits1().getPermanentLimit(), elementName);

        ucteNetwork.addLine(ucteLine);
    }

    /**
     * Gets the needed information from the dangling line to create the Xnode ({@link UcteNode} and then calls
     * {@link UcteExporter#createLineFromDanglingLine(UcteNetwork, DanglingLine)} to create the {@link UcteLine} and to add it to
     * the given ucteNetwork
     *
     * @param ucteNetwork The target network in ucte
     * @param danglingLine The danglingLine to convert to UCTE
     */
    private void convertDanglingLine(UcteNetwork ucteNetwork, DanglingLine danglingLine) {
        LOGGER.trace("Converting dangling line {}", danglingLine.getId());
        Optional<UcteNodeCode> optUcteXNodeCode = parseUcteNodeCode(danglingLine.getUcteXnodeCode());
        if (!optUcteXNodeCode.isPresent()) {
            throw new UcteException("Dangling line " + danglingLine.getId() + ": XnodeCode not found");
        }

        String geographicalName = danglingLine.getProperties().getProperty(GEOGRAPHICAL_NAME_PROPERTY_KEY, null);

        ucteNetwork.addNode(new UcteNode(
                optUcteXNodeCode.get(),
                geographicalName,
                UcteNodeStatus.EQUIVALENT,
                UcteNodeTypeCode.PQ,
                Float.NaN,
                (float) danglingLine.getP0(),
                (float) danglingLine.getQ0(),
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                Float.NaN,
                null
        ));
        createLineFromDanglingLine(ucteNetwork, danglingLine);
    }

    /**
     * Checks if the dangling line given contains an UCTE-compliant id. If it's the case, it creates the dangling line
     * and adds it to the ucteNetwork. Otherwise, it calls {@link UcteExporter#createLineFromNonCompliantIdDanglingLine(UcteNetwork, DanglingLine)}
     * to do specific handling to convert the dangling line
     *
     * @param ucteNetwork The target network in ucte
     * @param danglingLine The danglingLine to convert to UCTE
     * @see UcteExporter#createLineFromNonCompliantIdDanglingLine(UcteNetwork, DanglingLine)
     */
    private void createLineFromDanglingLine(UcteNetwork ucteNetwork, DanglingLine danglingLine) {
        if (danglingLine.getId().length() == 19) { // It's probably a ucte id
            Optional<UcteNodeCode> optUcteNodeCode1 = parseUcteNodeCode(danglingLine.getId().substring(0, 8));
            Optional<UcteNodeCode> optUcteNodeCode2 = parseUcteNodeCode(danglingLine.getId().substring(9, 17));
            if (optUcteNodeCode1.isPresent() && optUcteNodeCode2.isPresent()) { // It is a ucte id
                String elementName = danglingLine.getProperties().getProperty(ELEMENT_NAME_PROPERTY_KEY, null);
                UcteElementId ucteElementId = new UcteElementId(optUcteNodeCode1.get(), optUcteNodeCode2.get(), danglingLine.getId().charAt(18));
                UcteLine ucteLine = new UcteLine(ucteElementId,
                        UcteElementStatus.EQUIVALENT_ELEMENT_IN_OPERATION,
                        (float) danglingLine.getR(),
                        (float) danglingLine.getX(),
                        (float) danglingLine.getB(),
                        (int) danglingLine.getCurrentLimits().getPermanentLimit(),
                        elementName);
                ucteNetwork.addLine(ucteLine);
            } else { // It is not a ucte id
                createLineFromNonCompliantIdDanglingLine(ucteNetwork, danglingLine);
            }
        } else {  // It is not a ucte id
            createLineFromNonCompliantIdDanglingLine(ucteNetwork, danglingLine);
        }
    }

    void createLineFromNonCompliantIdDanglingLine(UcteNetwork ucteNetwork, DanglingLine danglingLine) {
        String ucteXnodeCode;
        if (danglingLine.getUcteXnodeCode() != null) {
            ucteXnodeCode = danglingLine.getUcteXnodeCode();
        } else {
            LOGGER.warn("The dangling line {} will be ignored: no ucteXnodeCode found", danglingLine.getId());
            return;
        }

        Optional<Country> country = danglingLine.getTerminal().getBusBreakerView().getConnectableBus().getVoltageLevel().getSubstation().getCountry();
        if (!country.isPresent()) {
            throw new UcteException("Dangling line " + danglingLine.getId() + NO_COUNTRY_FOUND);
        }

        UcteNodeCode ucteNodeCode1 = createUcteNodeCode(ucteXnodeCode,
                danglingLine.getTerminal().getVoltageLevel(),
                UcteCountryCode.XX.toString());
        UcteNodeCode ucteNodeCode2 = createUcteNodeCode(danglingLine.getTerminal().getBusBreakerView().getConnectableBus().getId(),
                danglingLine.getTerminal().getBusBreakerView().getConnectableBus().getVoltageLevel(),
                country.get().toString());
        UcteElementId ucteElementId = generateUcteElementId(danglingLine.getId(), ucteNodeCode1, ucteNodeCode2, null);

        int currentLimit;
        if (danglingLine.getCurrentLimits() == null) {
            currentLimit = DEFAULT_MAX_CURRENT;
            LOGGER.warn("The dangling line {} current limit is undefined, set value to {}", danglingLine.getId(), DEFAULT_MAX_CURRENT);
        } else {
            currentLimit = (int) danglingLine.getCurrentLimits().getPermanentLimit();
        }

        UcteLine ucteLine = new UcteLine(ucteElementId,
                UcteElementStatus.EQUIVALENT_ELEMENT_IN_OPERATION,
                (float) danglingLine.getR(),
                (float) danglingLine.getX(),
                (float) danglingLine.getB(),
                currentLimit,
                null);
        ucteNetwork.addLine(ucteLine);
    }

    /**
     * Iterates through the voltageLevel to get all the switches and create the corresponding {@link UcteLine} for
     * each switch. If the switch id is not UCTE-compliant we go through different steps to generate an Ucte-compliant id.
     * Add the  {@link UcteLine}s created (corresponding to the switches) to the ucteNetwork.
     *
     * @param ucteNetwork The target UcteNetwork
     * @param voltageLevel The VoltageLevel containing the switches we want to convert.
     * @see UcteExporter#createUcteNodeCode(String, VoltageLevel, String)
     * @see UcteExporter#generateUcteElementId(String, UcteNodeCode, UcteNodeCode, Character)
     */
    void convertSwitches(UcteNetwork ucteNetwork, VoltageLevel voltageLevel) {
        for (Switch sw : voltageLevel.getBusBreakerView().getSwitches()) {
            LOGGER.trace("Converting switch {}", sw.getId());
            String orderCodeProperty = sw.getProperties().getProperty(ORDER_CODE, null);
            Character orderCode = null;
            if (orderCodeProperty != null) {
                orderCode = orderCodeProperty.charAt(0);
            }
            UcteElementStatus switchStatus = sw.isOpen() ? UcteElementStatus.BUSBAR_COUPLER_OUT_OF_OPERATION :
                    UcteElementStatus.BUSBAR_COUPLER_IN_OPERATION;
            generateUcteLineFromSwitch(ucteNetwork, switchStatus, voltageLevel, sw, orderCode);
        }
    }

    void generateUcteLineFromSwitch(UcteNetwork ucteNetwork, UcteElementStatus switchStatus, VoltageLevel voltageLevel, Switch sw, Character propertyOrderCode) {
        Optional<Country> country = voltageLevel.getSubstation().getCountry();

        if (country.isPresent()) {
            Bus bus1 = voltageLevel.getBusBreakerView().getBus1(sw.getId());
            Bus bus2 = voltageLevel.getBusBreakerView().getBus2(sw.getId());
            UcteNodeCode ucteNodeCode1 = createUcteNodeCode(bus1.getId(), voltageLevel, country.get().toString());
            UcteNodeCode ucteNodeCode2 = createUcteNodeCode(bus2.getId(), voltageLevel, country.get().toString());
            UcteElementId ucteElementId = generateUcteElementId(sw.getId(), ucteNodeCode1, ucteNodeCode2, propertyOrderCode);
            String elementName = sw.getProperties().getProperty(ELEMENT_NAME_PROPERTY_KEY, null);
            UcteLine ucteLine = new UcteLine(ucteElementId, switchStatus, 0, 0, 0, null, elementName);
            setSwitchCurrentLimit(ucteLine, sw);
            ucteNetwork.addLine(ucteLine);
        } else {
            throw new UcteException("Switch " + sw.getId() + NO_COUNTRY_FOUND);
        }
    }

    String xnodeCodeForLine(Line line) {
        MergedXnode mergedXnode = line.getExtension(MergedXnode.class);
        if (mergedXnode != null) {
            return mergedXnode.getCode();
        } else {
            TieLine tieLine = (TieLine) line;
            return tieLine.getUcteXnodeCode();
        }
    }

    /**
     * Converts the {@link Line} (being a {@link TieLine}) to two {@link UcteLine}
     * and a Xnode ({@link UcteExporter#createXnodeFromTieLine(UcteNetwork, UcteNodeCode, Line)}.
     * Then, adds them in the UcteNetwork.
     * If the tie line has a non-compliant UCTE id, calls {@link UcteExporter#createTieLineWithGeneratedIds(UcteNetwork, Line)}
     * to do the specific handling
     *
     * @param ucteNetwork The target UcteNetwork
     * @param line The TieLine we want to convert
     * @see UcteExporter#createXnodeFromTieLine(UcteNetwork, UcteNodeCode, Line)
     * @see UcteExporter#createTieLineWithGeneratedIds(UcteNetwork, Line)
     */
    private void convertTieLine(UcteNetwork ucteNetwork, Line line) {
        if (isUcteTieLineId(line)) {
            MergedXnode mergedXnode = line.getExtension(MergedXnode.class);
            String id1 = mergedXnode.getLine1Name().substring(0, 8); // First node line 1
            String id2 = mergedXnode.getLine1Name().substring(9, 17); // Second node line 1
            String id3 = mergedXnode.getLine2Name().substring(0, 8); // First node line 2
            String id4 = mergedXnode.getLine2Name().substring(9, 17); // Second node line 2
            Optional<UcteNodeCode> optUcteNodeCode1 = parseUcteNodeCode(id1);
            Optional<UcteNodeCode> optUcteNodeCode2 = parseUcteNodeCode(id2);
            Optional<UcteNodeCode> optUcteNodeCode3 = parseUcteNodeCode(id3);
            Optional<UcteNodeCode> optUcteNodeCode4 = parseUcteNodeCode(id4);

            if (optUcteNodeCode1.isPresent() && optUcteNodeCode2.isPresent() && optUcteNodeCode3.isPresent() && optUcteNodeCode4.isPresent()) {
                UcteNodeCode[] ucteNodeCodes = {optUcteNodeCode1.get(), optUcteNodeCode2.get(), optUcteNodeCode3.get(), optUcteNodeCode4.get()};
                List<UcteNodeCode> ucteNodeCodeList = Arrays.asList(ucteNodeCodes);

                ucteNodeCodeList.forEach(ucteNodeCode ->
                        createXnodeFromTieLine(ucteNetwork, ucteNodeCode, line)
                );

                String elementName1 = line.getProperties().getProperty(ELEMENT_NAME_PROPERTY_KEY + "_1", null);
                String elementName2 = line.getProperties().getProperty(ELEMENT_NAME_PROPERTY_KEY + "_2", null);

                UcteElementId ucteElementId1 = new UcteElementId(ucteNodeCodeList.get(0), ucteNodeCodeList.get(1), line.getId().charAt(18));
                UcteElementId ucteElementId2 = new UcteElementId(ucteNodeCodeList.get(2), ucteNodeCodeList.get(3), line.getId().charAt(40));

                UcteLine ucteLine1 = new UcteLine(
                        ucteElementId1,
                        UcteElementStatus.REAL_ELEMENT_IN_OPERATION,
                        (float) line.getR() / 2,
                        (float) line.getX() / 2,
                        (float) line.getB2(),
                        Math.min((int) line.getCurrentLimits1().getPermanentLimit(), (int) line.getCurrentLimits2().getPermanentLimit()),
                        elementName1);

                UcteLine ucteLine2 = new UcteLine(
                        ucteElementId2,
                        UcteElementStatus.REAL_ELEMENT_IN_OPERATION,
                        (float) line.getR() / 2,
                        (float) line.getX() / 2,
                        (float) line.getB2(),
                        Math.min((int) line.getCurrentLimits1().getPermanentLimit(), (int) line.getCurrentLimits2().getPermanentLimit()),
                        elementName2);

                ucteNetwork.addLine(ucteLine1);
                ucteNetwork.addLine(ucteLine2);
            } else {
                createTieLineWithGeneratedIds(ucteNetwork, line);
            }
        } else {
            createTieLineWithGeneratedIds(ucteNetwork, line);
        }
    }

    /**
     * Creates an UCTE compliant id for the {@link UcteLine}, creates the two UcteLine corresponding to the tie line and adds it to the UcteNetwork
     *
     * @param ucteNetwork The target UcteNetwork
     * @param line The Tie line with the non-compliant UCTE id
     * @see UcteExporter#createUcteNodeCode
     * @see UcteExporter#generateUcteElementId(String, UcteNodeCode, UcteNodeCode, Character)
     */
    void createTieLineWithGeneratedIds(UcteNetwork ucteNetwork, Line line) {
        String xnodeCode = xnodeCodeForLine(line);
        VoltageLevel voltageLevel1 = line.getTerminal1().getVoltageLevel();
        VoltageLevel voltageLevel2 = line.getTerminal2().getVoltageLevel();
        Optional<Country> country1 = voltageLevel1.getSubstation().getCountry();
        Optional<Country> country2 = voltageLevel2.getSubstation().getCountry();

        if (xnodeCode == null || !country1.isPresent() || !country2.isPresent()) {
            throw new UcteException("TieLine " + line.getId() + NO_COUNTRY_FOUND);
        }

        UcteNodeCode ucteNodeCode1 = createUcteNodeCode(line.getTerminal1().getBusBreakerView().getConnectableBus().getId(),
                voltageLevel1,
                country1.get().toString());
        UcteNodeCode ucteNodeCode2 = createUcteNodeCode(line.getTerminal2().getBusBreakerView().getConnectableBus().getId(),
                voltageLevel2,
                country2.get().toString());
        UcteNodeCode ucteNodeCodeXnode = createUcteNodeCode(xnodeCode,
                voltageLevel1,
                UcteCountryCode.XX.toString());
        createXnodeFromTieLine(ucteNetwork, ucteNodeCodeXnode, line);
        UcteElementId ucteElementId1 = generateUcteElementId(line.getId() + "_1", ucteNodeCodeXnode, ucteNodeCode1, null);
        UcteElementId ucteElementId2 = generateUcteElementId(line.getId() + "_2", ucteNodeCodeXnode, ucteNodeCode2, null);

        UcteLine ucteLine1 = new UcteLine(
                ucteElementId1,
                UcteElementStatus.REAL_ELEMENT_IN_OPERATION,
                (float) line.getR() / 2,
                (float) line.getX() / 2,
                (float) line.getB2(),
                Math.min((int) line.getCurrentLimits1().getPermanentLimit(), (int) line.getCurrentLimits2().getPermanentLimit()),
                null);

        UcteLine ucteLine2 = new UcteLine(
                ucteElementId2,
                UcteElementStatus.REAL_ELEMENT_IN_OPERATION,
                (float) line.getR() / 2,
                (float) line.getX() / 2,
                (float) line.getB2(),
                Math.min((int) line.getCurrentLimits1().getPermanentLimit(), (int) line.getCurrentLimits2().getPermanentLimit()),
                null);
        ucteNetwork.addLine(ucteLine1);
        ucteNetwork.addLine(ucteLine2);
    }

    /**
     * Checks if the ucteNodeCode is the NodeCode corresponding to a Xnode. If so, create the Xnode and adds it to the ucteNetwork
     * We only want to add the xnodes here since the other "regular" nodes are already created and put in the network with the
     * {@link UcteExporter#convertBus(UcteNetwork, Bus)}.
     *
     * @param ucteNetwork The target UcteNetwork
     * @param ucteNodeCode the UcteNodeCode needed to create a UcteNode
     * @param line The tie line related to the Xnode
     */
    private void createXnodeFromTieLine(UcteNetwork ucteNetwork, UcteNodeCode ucteNodeCode, Line line) {
        if (ucteNodeCode.getUcteCountryCode() == UcteCountryCode.XX) {
            String geographicalName = null;
            String xnodeCode = xnodeCodeForLine(line);
            if (ucteNodeCode.toString().equals(xnodeCode)) {
                geographicalName = getGeographicalNameProperty(line);
            }
            ucteNetwork.addNode(
                    new UcteNode(
                            ucteNodeCode,
                            geographicalName,
                            UcteNodeStatus.EQUIVALENT,
                            UcteNodeTypeCode.PQ,
                            Float.NaN,
                            0f,
                            0f,
                            Float.NaN,
                            Float.NaN,
                            Float.NaN,
                            Float.NaN,
                            Float.NaN,
                            Float.NaN,
                            Float.NaN,
                            Float.NaN,
                            Float.NaN,
                            Float.NaN,
                            null
                    ));
        }
    }

    /**
     * Iterates through the {@link TwoWindingsTransformer}s and call the method to convert them
     * ({@link UcteExporter#convertTwoWindingsTransformer(UcteNetwork, TwoWindingsTransformer)})
     *
     * @param network the iidm Network containing the TwoWindingsTransformers we want to convert
     * @param ucteNetwork The target UcteNetwork
     */
    private void convertTwoWindingsTransformers(Network network, UcteNetwork ucteNetwork) {
        network.getTwoWindingsTransformerStream()
                .forEach(twoWindingsTransformer -> convertTwoWindingsTransformer(ucteNetwork, twoWindingsTransformer));
    }

    /**
     * Converts the {@link TwoWindingsTransformer} into a {@link UcteTransformer} and adds it to the ucteNetwork.
     * Also creates the adds the linked {@link UcteRegulation}
     *
     * @param ucteNetwork The target UcteNetwork
     * @param twoWindingsTransformer The two windings transformer we want to convert
     * @see UcteExporter#convertRegulation(UcteNetwork, UcteElementId, TwoWindingsTransformer)
     */
    private void convertTwoWindingsTransformer(UcteNetwork ucteNetwork, TwoWindingsTransformer twoWindingsTransformer) {
        Terminal terminal1 = twoWindingsTransformer.getTerminal1();
        Terminal terminal2 = twoWindingsTransformer.getTerminal2();

        Optional<Country> country1 = terminal1.getVoltageLevel().getSubstation().getCountry();
        Optional<Country> country2 = terminal2.getVoltageLevel().getSubstation().getCountry();

        if (!country1.isPresent() || !country2.isPresent()) {
            throw new UcteException("Two windings transformer " + twoWindingsTransformer.getId() + NO_COUNTRY_FOUND);
        }

        UcteNodeCode ucteNodeCode1 = createUcteNodeCode(
                terminal1.getBusBreakerView().getConnectableBus().getId(),
                terminal1.getVoltageLevel(),
                country1.get().toString()
        );

        UcteNodeCode ucteNodeCode2 = createUcteNodeCode(
                terminal2.getBusBreakerView().getConnectableBus().getId(),
                terminal2.getVoltageLevel(),
                country2.get().toString()
        );

        UcteElementId ucteElementId = convertUcteElementId(ucteNodeCode2, ucteNodeCode1, twoWindingsTransformer.getId(), terminal1, terminal2);
        String elementName = twoWindingsTransformer.getProperties().getProperty(ELEMENT_NAME_PROPERTY_KEY, null);
        UcteTransformer ucteTransformer = new UcteTransformer(
                ucteElementId,
                UcteElementStatus.fromCode(1),
                (float) twoWindingsTransformer.getR(),
                (float) twoWindingsTransformer.getX(),
                (float) twoWindingsTransformer.getB(),
                (int) twoWindingsTransformer.getCurrentLimits2().getPermanentLimit(),
                elementName,
                (float) twoWindingsTransformer.getRatedU2(),
                (float) twoWindingsTransformer.getRatedU1(),
                100,        //TODO Find a representation for the nominal power
                (float) twoWindingsTransformer.getG());
        convertRegulation(ucteNetwork, ucteElementId, twoWindingsTransformer);
        ucteNetwork.addTransformer(ucteTransformer);

    }

    /**
     * Creates and adds to the ucteNetwork the {@link UcteRegulation} linked to the TwoWindingsTransformer.
     * <li>{@link RatioTapChanger} into {@link UctePhaseRegulation}</li>
     * <li>{@link PhaseTapChanger} into {@link UcteAngleRegulation}</li>
     *
     * @param ucteNetwork The target UcteNetwork
     * @param ucteElementId The UcteElementId corresponding to the TwoWindingsTransformer
     * @param twoWindingsTransformer The TwoWindingTransformer we want to convert
     */
    private void convertRegulation(UcteNetwork ucteNetwork, UcteElementId ucteElementId, TwoWindingsTransformer twoWindingsTransformer) {
        if (twoWindingsTransformer.getRatioTapChanger() != null || twoWindingsTransformer.getPhaseTapChanger() != null) {
            UctePhaseRegulation uctePhaseRegulation = null;
            UcteAngleRegulation ucteAngleRegulation = null;
            if (twoWindingsTransformer.getRatioTapChanger() != null) {
                uctePhaseRegulation = convertRatioTapChanger(twoWindingsTransformer);
            }
            if (twoWindingsTransformer.getPhaseTapChanger() != null) {
                ucteAngleRegulation = convertPhaseTapChanger(twoWindingsTransformer);
            }
            UcteRegulation ucteRegulation = new UcteRegulation(ucteElementId, uctePhaseRegulation, ucteAngleRegulation);
            ucteNetwork.addRegulation(ucteRegulation);
        }
    }

    /**
     * Creates the {@link UcteRegulation} linked to the twoWindingsTransformer
     *
     * @param twoWindingsTransformer The TwoWindingsTransformers containing the RatioTapChanger we want to convert
     * @return the UctePhaseRegulation needed to create a {@link UcteRegulation}
     * @see UcteExporter#calculatePhaseDu(TwoWindingsTransformer)
     */
    private UctePhaseRegulation convertRatioTapChanger(TwoWindingsTransformer twoWindingsTransformer) {
        LOGGER.trace("Converting iidm ratio tap changer of transformer {}", twoWindingsTransformer.getId());

        float du = (float) calculatePhaseDu(twoWindingsTransformer);
        UctePhaseRegulation uctePhaseRegulation = new UctePhaseRegulation(
                du,
                twoWindingsTransformer.getRatioTapChanger().getHighTapPosition(),
                twoWindingsTransformer.getRatioTapChanger().getTapPosition(),
                Float.NaN);
        if (!Double.isNaN(twoWindingsTransformer.getRatioTapChanger().getTargetV())) {
            uctePhaseRegulation.setU((float) twoWindingsTransformer.getRatioTapChanger().getTargetV());
        }
        return uctePhaseRegulation;
    }

    /**
     * Determines the UcteAngleRegulationType and depending on it, creates the UcteAngleRegulation
     *
     * @param twoWindingsTransformer The TwoWindingsTransformers containing the PhaseTapChanger we want to convert
     * @return the UcteAngleRegulation needed to create a {@link UcteRegulation}
     * @see UcteAngleRegulation
     * @see UcteExporter#findRegulationType(TwoWindingsTransformer)
     */
    private UcteAngleRegulation convertPhaseTapChanger(TwoWindingsTransformer twoWindingsTransformer) {
        LOGGER.trace("Converting iidm Phase tap changer of transformer {}", twoWindingsTransformer.getId());
        UcteAngleRegulationType ucteAngleRegulationType = findRegulationType(twoWindingsTransformer);
        if (ucteAngleRegulationType == UcteAngleRegulationType.SYMM) {
            return new UcteAngleRegulation((float) calculateSymmAngleDu(twoWindingsTransformer),
                    90,
                    twoWindingsTransformer.getPhaseTapChanger().getHighTapPosition(),
                    twoWindingsTransformer.getPhaseTapChanger().getTapPosition(),
                    calculateAngleP(twoWindingsTransformer),
                    ucteAngleRegulationType);
        } else {
            return new UcteAngleRegulation((float) calculateAsymmAngleDu(twoWindingsTransformer),
                    (float) calculateAsymmAngleTheta(twoWindingsTransformer),
                    twoWindingsTransformer.getPhaseTapChanger().getHighTapPosition(),
                    twoWindingsTransformer.getPhaseTapChanger().getTapPosition(),
                    calculateAngleP(twoWindingsTransformer),
                    ucteAngleRegulationType);
        }
    }

    /**
     * calculate the δu(%) for the phase regulation of the two windings transformer
     *
     * @param twoWindingsTransformer The TwoWindingsTransformers containing the RatioTapChanger we want to convert
     * @return the δu needed to create a {@link UctePhaseRegulation}
     */
    double calculatePhaseDu(TwoWindingsTransformer twoWindingsTransformer) {
        double rhoMax = 0;
        double rhoMin = 0;
        int tapCount =  twoWindingsTransformer.getRatioTapChanger().getStepCount();
        double[] rhoList = new double[tapCount];
        int j = 0;
        for (int i = twoWindingsTransformer.getRatioTapChanger().getLowTapPosition();
             i <= twoWindingsTransformer.getRatioTapChanger().getHighTapPosition(); i++, j++) {
            rhoList[j] = twoWindingsTransformer.getRatioTapChanger().getStep(i).getRho();
        }
        rhoMax = Doubles.max(rhoList);
        rhoMin = Doubles.min(rhoList);
        double res = 100 * (1 / rhoMin - 1 / rhoMax) / (tapCount - 1);
        return BigDecimal.valueOf(res).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Calculate the δu(%) for the angle regulation of the two windings transformer
     * This computation only work for an angle regulation with the type symmetrical
     *
     * @param twoWindingsTransformer The twoWindingsTransformer containing the PhaseTapChanger we want to convert
     * @return the δu needed to create a {@link UcteAngleRegulation}
     */
    private double calculateSymmAngleDu(TwoWindingsTransformer twoWindingsTransformer) {
        double alphaMin = 0;
        double alphaMax = 0;
        int tabCount = twoWindingsTransformer.getPhaseTapChanger().getStepCount();
        double[] alphaList = new double[tabCount];
        int j = 0;
        for (int i = twoWindingsTransformer.getPhaseTapChanger().getLowTapPosition();
             i <= twoWindingsTransformer.getPhaseTapChanger().getHighTapPosition(); i++, j++) {
            alphaList[j] = twoWindingsTransformer.getPhaseTapChanger().getStep(i).getAlpha();
        }
        alphaMin = Math.toRadians(Doubles.min(alphaList));
        alphaMax = Math.toRadians(Doubles.max(alphaList));

        return 100 * (2 * (Math.tan(alphaMax / 2) - Math.tan(alphaMin / 2)) / (tabCount - 1));
    }

    /**
     * Calculate the δu(%) for the angle regulation of the two windings transformer
     * This computation only work for an angle regulation with the type asymmetrical
     *
     * @param twoWindingsTransformer The twoWindingsTransformer containing the PhaseTapChanger we want to convert
     * @return the δu needed to create a {@link UcteAngleRegulation}
     */
    private double calculateAsymmAngleDu(TwoWindingsTransformer twoWindingsTransformer) {
        PhaseTapChanger phaseTapChanger = twoWindingsTransformer.getPhaseTapChanger();
        int lowTapPosition = phaseTapChanger.getLowTapPosition();
        int highTapPosition = phaseTapChanger.getHighTapPosition();
        int tapNumber = phaseTapChanger.getStepCount();
        double lowPositionAlpha = Math.toRadians(-phaseTapChanger.getStep(lowTapPosition).getAlpha());
        double lowPositionRho = 1 / phaseTapChanger.getStep(lowTapPosition).getRho();
        double highPositionAlpha = Math.toRadians(-phaseTapChanger.getStep(highTapPosition).getAlpha());
        double highPositionRho = 1 / phaseTapChanger.getStep(highTapPosition).getRho();
        double xa = lowPositionRho * Math.cos(lowPositionAlpha);
        double ya = lowPositionRho * Math.sin(lowPositionAlpha);
        double xb = highPositionRho * Math.cos(highPositionAlpha);
        double yb = highPositionRho * Math.sin(highPositionAlpha);
        double distance = Math.sqrt(Math.pow(xb - xa, 2) + Math.pow(yb - ya, 2));
        double du = 100 * distance / (tapNumber - 1);
        return BigDecimal.valueOf(du).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Calculate the Θ for the angle regulation of the two windings transformer
     *
     * @param twoWindingsTransformer The twoWindingsTransformer containing the PhaseTapChanger we want to convert
     * @return the Θ needed to create a {@link UcteAngleRegulation}
     */
    private double calculateAsymmAngleTheta(TwoWindingsTransformer twoWindingsTransformer) {
        PhaseTapChanger phaseTapChanger = twoWindingsTransformer.getPhaseTapChanger();
        int lowTapPosition = phaseTapChanger.getLowTapPosition();
        int highTapPosition = phaseTapChanger.getHighTapPosition();
        double lowPositionAlpha = Math.toRadians(-phaseTapChanger.getStep(lowTapPosition).getAlpha());
        double lowPositionRho = 1 / phaseTapChanger.getStep(lowTapPosition).getRho();
        double highPositionAlpha = Math.toRadians(-phaseTapChanger.getStep(highTapPosition).getAlpha());
        double highPositionRho = 1 / phaseTapChanger.getStep(highTapPosition).getRho();
        double xa = lowPositionRho * Math.cos(lowPositionAlpha);
        double ya = lowPositionRho * Math.sin(lowPositionAlpha);
        double xb = highPositionRho * Math.cos(highPositionAlpha);
        double yb = highPositionRho * Math.sin(highPositionAlpha);
        return Math.toDegrees(Math.atan((yb - ya) / (xb - xa)));
    }

    /**
     * @param twoWindingsTransformer The twoWindingsTransformer containing the PhaseTapChanger we want to convert
     * @return P (MW) of the angle regulation for the two windings transformer
     */
    private float calculateAngleP(TwoWindingsTransformer twoWindingsTransformer) {
        return (float) twoWindingsTransformer.getPhaseTapChanger().getRegulationValue();
    }

    /**
     * Give the type of the UcteAngleRegulation
     *
     * @param twoWindingsTransformer containing the PhaseTapChanger we want to convert
     * @return The type of the UcteAngleRegulation
     */
    private UcteAngleRegulationType findRegulationType(TwoWindingsTransformer twoWindingsTransformer) {
        if (isSymm(twoWindingsTransformer)) {
            return UcteAngleRegulationType.SYMM;
        } else {
            return UcteAngleRegulationType.ASYM;
        }
    }

    private boolean isSymm(TwoWindingsTransformer twoWindingsTransformer) {
        for (int i = twoWindingsTransformer.getPhaseTapChanger().getLowTapPosition();
             i < twoWindingsTransformer.getPhaseTapChanger().getHighTapPosition(); i++) {
            if (twoWindingsTransformer.getPhaseTapChanger().getStep(i).getRho() != 1) {
                return false;
            }
        }
        return true;
    }

    public UcteNodeCode createUcteNodeCode(String id, VoltageLevel voltageLevel, String country) {
        UcteNodeCode ucteNodeCode;
        if (iidmIdToUcteNodeCodeId.containsKey(id)) {
            return iidmIdToUcteNodeCodeId.get(id);
        }
        Optional<UcteNodeCode> optionalUcteNodeCode = parseUcteNodeCode(id);
        if (optionalUcteNodeCode.isPresent()) { // the ID is already an UCTE id
            iidmIdToUcteNodeCodeId.put(id, optionalUcteNodeCode.get());
            return optionalUcteNodeCode.get();
        } else {
            convertIidmIdToUcteNodeCode(id, voltageLevel, country);
            ucteNodeCode = iidmIdToUcteNodeCodeId.get(id);
        }
        return ucteNodeCode;
    }

    /**
     * create a unique UcteNodeCode using the voltage level and the country given and
     * store it to {@link UcteExporter#iidmIdToUcteNodeCodeId} so we don't need to create it every time
     * and ensure  its uniqueness
     *
     * @param id the orignal id of the iidm component we want to convert
     * @param voltageLevel the {@link VoltageLevel} of the component we want to convert
     * @param country the string corresponding to the country where the iidm component is
     */
    void convertIidmIdToUcteNodeCode(String id, VoltageLevel voltageLevel, String country) {
        UcteNodeCode ucteNodeCode = null;
        String voltageNameOrId = voltageLevel.getName() != null ? voltageLevel.getName() : voltageLevel.getId();
        char busbar = 'a';
        do {
            if (busbar <= 'z') {
                ucteNodeCode = new UcteNodeCode(
                        UcteCountryCode.valueOf(country),
                        voltageNameOrId,
                        voltageLevelCodeFromIidmVoltage(voltageLevel.getNominalV()),
                        busbar
                );
            } else {
                LOGGER.warn("There is more than 26 nodes with the id starting by '{}'", ucteNodeCode);
                generateUcteNodeCode(id, voltageLevel, country);
                return;
            }
            busbar++;
        } while (iidmIdToUcteNodeCodeId.values().contains(ucteNodeCode));
        iidmIdToUcteNodeCodeId.put(id, ucteNodeCode);
    }

    /**
     * create a unique UcteNodeCode using the voltage level and the country given and
     * store it to {@link UcteExporter#iidmIdToUcteNodeCodeId} so we don't need to create it every time
     * and ensure  its uniqueness.
     * UcteNodeCode id = 1 character representing the country + 2 characters for the substation (or "aa" if the substation
     * doesn't have a name) + 3 character generated from "aaa" to "zzz" + 1 character for the voltage level code and a character
     * generated from "a" to "z". The goal is to create a ucte compliant id from any iidm component with non ucte compliant ids
     *
     * @param id the orignal id of the iidm component we want to convert
     * @param voltageLevel the {@link VoltageLevel} of the component we want to convert
     * @param country the string corresponding to the country where the iidm component is
     */
    void generateUcteNodeCode(String id, VoltageLevel voltageLevel, String country) {
        String substationName = null;
        if (voltageLevel.getSubstation().getName() != null && voltageLevel.getSubstation().getName().length() >= 2) {
            substationName = voltageLevel.getSubstation().getName().substring(0, 2);
        } else {
            substationName = "aa";
        }
        UcteNodeCode ucteNodeCode = null;
        do {
            generatedGeographicalName = incrementGeneratedGeographicalName(generatedGeographicalName);
            ucteNodeCode = new UcteNodeCode(
                    UcteCountryCode.valueOf(country),
                    substationName + generatedGeographicalName.substring(0, 3),
                    voltageLevelCodeFromIidmVoltage(voltageLevel.getNominalV()),
                    generatedGeographicalName.charAt(3)
            );
        } while (iidmIdToUcteNodeCodeId.values().contains(ucteNodeCode));
        iidmIdToUcteNodeCodeId.put(id, ucteNodeCode);
    }

    /**
     * create a unique UcteElementId with the given information in parameters
     *
     */
    UcteElementId convertUcteElementId(UcteNodeCode ucteNodeCode1, UcteNodeCode ucteNodeCode2, String id, Terminal terminal1, Terminal terminal2) {
        if (isUcteNodeId(terminal1.getBusBreakerView().getConnectableBus().getId()) &&
                isUcteNodeId(terminal2.getBusBreakerView().getConnectableBus().getId())) {
            return new UcteElementId(
                    ucteNodeCode1,
                    ucteNodeCode2,
                    id.charAt(18));
        } else {
            return generateUcteElementId(id, ucteNodeCode1, ucteNodeCode2, null);
        }
    }

    /**
     * increment the last character of the id from 'a' to 'z' until the id is unique, and return it
     *
     * @param id the id of the original iidm component to convert
     * @param ucteNodeCode1 the ucteNodeCode used to create the UcteElementId
     * @param ucteNodeCode2 the ucteNodeCode used to create the UcteElementId
     * @return the generated UcteElementId
     */
    UcteElementId generateUcteElementId(String id, UcteNodeCode ucteNodeCode1, UcteNodeCode ucteNodeCode2, Character propertyOrderCode) {
        if (iidmIdToUcteElementId.containsKey(id)) {
            return iidmIdToUcteElementId.get(id);
        } else {
            char orderCode = 'a';
            UcteElementId ucteElementId = null;

            if (propertyOrderCode != null) { // First we try to generate an ucteElementId with the orderCode in the property
                ucteElementId = new UcteElementId(ucteNodeCode1, ucteNodeCode2, propertyOrderCode);
                if (!iidmIdToUcteElementId.values().contains(ucteElementId)) {
                    iidmIdToUcteElementId.put(id, ucteElementId);
                    return ucteElementId;
                }
            }

            do {
                if (orderCode <= 'z') {
                    ucteElementId = new UcteElementId(ucteNodeCode1, ucteNodeCode2, (char) orderCode);
                } else {
                    LOGGER.warn("There is more than 26 element with the id starting by '{}'", ucteElementId);
                    return null;
                }
                orderCode++;
            } while (iidmIdToUcteElementId.values().contains(ucteElementId));
            iidmIdToUcteElementId.put(id, ucteElementId);
            return ucteElementId;
        }
    }

    /**
     * check if the id of the line match with the id of a tie line in ucte
     *
     * @param line is a tie line ?
     * @return true if the parameter is a tie line
     */
    boolean isUcteTieLineId(Line line) {

        MergedXnode mergedXnode = line.getExtension(MergedXnode.class);

        return mergedXnode != null
                && mergedXnode.getLine1Name() != null
                && mergedXnode.getLine2Name() != null
                && mergedXnode.getLine1Name().length() >= 19
                && mergedXnode.getLine2Name().length() >= 19
                && isUcteNodeId(mergedXnode.getLine1Name().substring(0, 8))
                && isUcteNodeId(mergedXnode.getLine1Name().substring(9, 17))
                && isUcteNodeId(mergedXnode.getLine2Name().substring(0, 8))
                && isUcteNodeId(mergedXnode.getLine2Name().substring(9, 17));
    }

    private void setSwitchCurrentLimit(UcteLine ucteLine, Switch sw) {
        if (sw.getProperties().containsKey(CURRENT_LIMIT_PROPERTY_KEY)) {
            try {
                ucteLine.setCurrentLimit(Integer.parseInt(sw.getProperties().getProperty(CURRENT_LIMIT_PROPERTY_KEY)));
            } catch (NumberFormatException exception) {
                ucteLine.setCurrentLimit(DEFAULT_MAX_CURRENT);
                LOGGER.warn("Switch {}: No current limit, set value to {}", sw.getId(), DEFAULT_MAX_CURRENT);
            }
        } else {
            ucteLine.setCurrentLimit(DEFAULT_MAX_CURRENT);
            LOGGER.warn("Switch {}: No current limit, set value to {}", sw.getId(), DEFAULT_MAX_CURRENT);
        }
    }

    String getGeographicalNameProperty(Line line) {
        return line.getProperties().getProperty(GEOGRAPHICAL_NAME_PROPERTY_KEY, "");
    }

    UctePowerPlantType energySourceToUctePowerPlantType(EnergySource energySource) {
        switch (energySource) {
            case HYDRO:
                return UctePowerPlantType.H;
            case NUCLEAR:
                return UctePowerPlantType.N;
            case THERMAL:
                return UctePowerPlantType.C;
            case WIND:
                return UctePowerPlantType.W;
            default:
                return UctePowerPlantType.F;
        }
    }

    /**
     * "increment" the string given in parameter by 1 : "aaaa" gives "aaab", "aaaz" gives "aaaA" etc...
     * It goes from a -> z -> A -> Z -> 0 -> 9 and it's used to generate a random geographicalName.
     * @param toIncrement the string to increment
     * @return a string "incremented" by 1
     */
    String incrementGeneratedGeographicalName(String toIncrement) {
        if (toIncrement.length() < 4) {
            throw new IllegalArgumentException("The string to increment is not long enough (should be at least 4)");
        }
        char charAt3 = toIncrement.charAt(3);
        char charAt2 = toIncrement.charAt(2);
        char charAt1 = toIncrement.charAt(1);
        char charAt0 = toIncrement.charAt(0);

        if (POSSIBLE_CHARACTER.indexOf(charAt3) == POSSIBLE_CHARACTER.size() - 1) {
            if (POSSIBLE_CHARACTER.indexOf(charAt2) == POSSIBLE_CHARACTER.size() - 1) {
                if (POSSIBLE_CHARACTER.indexOf(charAt1) == POSSIBLE_CHARACTER.size() - 1) {
                    if (POSSIBLE_CHARACTER.indexOf(charAt0) == POSSIBLE_CHARACTER.size() - 1) {
                        return "aaaa";
                    } else {
                        charAt3 = (char) POSSIBLE_CHARACTER.get(0);
                        charAt2 = (char) POSSIBLE_CHARACTER.get(0);
                        charAt1 = (char) POSSIBLE_CHARACTER.get(0);
                        charAt0 = (char) POSSIBLE_CHARACTER.get(POSSIBLE_CHARACTER.indexOf(charAt0) + 1);
                    }
                } else {
                    charAt3 = (char) POSSIBLE_CHARACTER.get(0);
                    charAt2 = (char) POSSIBLE_CHARACTER.get(0);
                    charAt1 = (char) POSSIBLE_CHARACTER.get(POSSIBLE_CHARACTER.indexOf(charAt1) + 1);
                }
            } else {
                charAt3 = (char) POSSIBLE_CHARACTER.get(0);
                charAt2 = (char) POSSIBLE_CHARACTER.get(POSSIBLE_CHARACTER.indexOf(charAt2) + 1);
            }
        } else {
            charAt3 = (char) POSSIBLE_CHARACTER.get(POSSIBLE_CHARACTER.indexOf(charAt3) + 1);
        }
        return String.format("%s%s%s%s", charAt0, charAt1, charAt2, charAt3);
    }
}
