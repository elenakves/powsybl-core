package com.powsybl.cgmes.conversion.test.update;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.powsybl.cgmes.conversion.update.ChangesListener;
import com.powsybl.cgmes.conversion.update.IidmChange;
import com.powsybl.iidm.network.*;

public final class UpdateNetworkFromCatalog16 {
	private UpdateNetworkFromCatalog16() {
	}

	public static void updateNetwork(Network network) {
		changes = new ArrayList<>();
		ChangesListener changeListener = new ChangesListener(changes);
		network.addListener(changeListener);
		/**
		 * Test onCreation
		 */
		try {
//			Substation substation = network.newSubstation()
//					.setCountry(network.getSubstation("_047c929a-c766-11e1-8775-005056c00008")
//					.getCountry()
//					.orElse(Country.ES))
//					.setGeographicalTags("_0472a781-c766-11e1-8775-005056c00008")
//					.setName("BUS 16_SS")
//					.setId("_BUS____16_SS")
//					.add();
			// VoltageLevel voltageLevel = substation.newVoltageLevel()
			// .setTopologyKind(TopologyKind.BUS_BREAKER)
			// .setId("_BUS____15_VL")
			// .setName("BUS 15_VL")
			// .setHighVoltageLimit(380.0)
			// .setLowVoltageLimit(320.0)
			// .setNominalV(200.0f)
			// .add();
			// VoltageLevel voltageLevel2 = substation.newVoltageLevel()
			// .setTopologyKind(TopologyKind.BUS_BREAKER)
			// .setId("_BUS____25_VL")
			// .setName("BUS 25_VL")
			// .setHighVoltageLimit(385.0)
			// .setLowVoltageLimit(325.0)
			// .setNominalV(205.0f)
			// .add();
			//// BusbarSection node1 = voltageLevel.getNodeBreakerView()
			//// .newBusbarSection()
			//// .setId("_BUS____15_VL_Node1")
			//// .setName("BUS 15_VL Node1")
			//// .setNode(1)
			//// .add(); --> vertex 1 not found
			// Bus bus = voltageLevel.getBusBreakerView()
			// .newBus()
			// .setName("bus1Name")
			// .setId("bus1")
			// .add();
			// Bus bus2 = voltageLevel2.getBusBreakerView()
			// .newBus()
			// .setName("bus2Name")
			// .setId("bus2")
			// .add();
			// Generator generator = voltageLevel.newGenerator()
			// .setId("_GEN____15_SM")
			// .setName("GEN 15")
			// .setBus("bus1")
			// .setVoltageRegulatorOn(false)
			// .setRatedS(150.0)
			// .setTargetP(1.0)
			// .setTargetQ(2.0)
			// .setMaxP(300.0)
			// .setMinP(-300.0)
			// .add();
			////// Terminal terminal = generator.getTerminal();
			//// generator.setRegulatingTerminal(terminal);
			// ShuntCompensator shuntCompensator =
			// voltageLevel.newShuntCompensator()
			// .setId("_BANK___15_SC")
			// .setName("BANK 15")
			// .setbPerSection(1)
			// .setMaximumSectionCount(2)
			// .setBus("bus1")
			// .setCurrentSectionCount(1)
			// .add();
			// TwoWindingsTransformer tWTransformer =
			// substation.newTwoWindingsTransformer()
			// .setId("_BUS____4-BUS____15-1_PT")
			// .setName("BUS 4-BUS 15-1")
			// .setVoltageLevel1("_BUS____15_VL")
			// .setVoltageLevel2("_BUS____25_VL")
			// .setConnectableBus1("bus1")
			// .setConnectableBus2("bus2")
			// .setR(2.0)
			// .setX(14.745)
			// .setG(4.0)
			// .setB(3.2E-5)
			// .setRatedU1(111.0)
			// .setRatedU2(222.0)
			// .add();
			// RatioTapChanger ratioTapChanger = tWTransformer.newRatioTapChanger()
			// .setLowTapPosition(0)
			// .setTapPosition(1)
			// .setRegulating(false)
			// .setRegulationTerminal(tWTransformer.getTerminal(Branch.Side.ONE))
			// .setTargetV(3)
			// .setLoadTapChangingCapabilities(false)
			// .beginStep().setR(-28.1).setX(-28.2).setG(0.1).setB(0.2).setRho(1.1).endStep()
			// .beginStep().setR(-28.3).setX(-28.4).setG(0.2).setB(0.3).setRho(1.3).endStep()
			//// .add();
			// PhaseTapChanger phaseTapChanger = tWTransformer.newPhaseTapChanger()
			// .setLowTapPosition(0)
			// .setTapPosition(1)
			// .setRegulationMode(PhaseTapChanger.RegulationMode.CURRENT_LIMITER)
			// .setRegulationValue(930.6667)
			// .setRegulating(false)
			// .setRegulationTerminal(tWTransformer.getTerminal(Branch.Side.ONE))
			// .beginStep().setR(-28.091503).setX(-28.091503).setG(0.0).setB(0.0).setRho(1.0).setAlpha(5.42).endStep()
			// .beginStep().setR(39.78473).setX(39.784725).setG(0.0).setB(0.0).setRho(1.0).setAlpha(-42.8).endStep()
			// .add();
			//// tWTransformer.newCurrentLimits1()
			//// .setPermanentLimit(931.0)
			//// .add();
			//// tWTransformer.newCurrentLimits2()
			//// .setPermanentLimit(931.0)
			//// .add();
			// Load load = voltageLevel.newLoad()
			// .setId("_LOAD___15_EC")
			// .setName("LOAD 15")
			// .setBus("bus1")
			// .setP0(20.0)
			// .setQ0(15.0)
			// .add();
			// LccConverterStation lccConverterStation =
			// voltageLevel.newLccConverterStation()
			// .setId("lcc")
			// .setName("lcc")
			// .setBus("bus1")
			// .setLossFactor(0.011f)
			// .setPowerFactor(0.5f)
			// .setConnectableBus("bus1")
			// .add();
			// Line line = network.newLine()
			// .setId("_LOAD___15_LINE")
			// .setName("BE-Line_9")
			// .setVoltageLevel1("_BUS____15_VL")
			// .setVoltageLevel2("_BUS____25_VL")
			// .setBus1("bus1")
			// .setBus2("bus2")
			// .setR(2.2)
			// .setX(68.1)
			// .setG1(0.01)
			// .setG2(0.02)
			// .setB1(0.03)
			// .setB2(0.04)
			// .add();

			// assertTrue(changes.size() == 1);

			/**
			 * Test onUpdate
			 */
			// network.getVoltageLevel("_0460f448-c766-11e1-8775-005056c00008")
			// .setHighVoltageLimit(1.2 * 381.0)
			// .setLowVoltageLimit(302.0);// .setNominalV(400);
			// network.getTwoWindingsTransformer("_045c1248-c766-11e1-8775-005056c00008")
			// .setR(0.3).setB(0.1)
			// .setG(0.2).setX(19.15)
			// .setRatedU1(132).setRatedU2(220);
			// Terminal p/q should be set by loadflow:
			network.getGenerator("_044ca8f0-c766-11e1-8775-005056c00008").setRatedS(201).setMaxP(161.0).setMinP(-51.0)
					.setTargetQ(1).setTargetP(84.0).getTerminal().setQ(-1).setP(-84.0);
			//
			// network.getLine("_044cd006-c766-11e1-8775-005056c00008").setR(6.0).setX(18.0).setB1(0.1).setB2(0.1).setG1(0.01)
			// .setG2(0.01);
			//
			// network.getBusBreakerView().getBus("_0471bd2a-c766-11e1-8775-005056c00008").setV(129.0);
			// // variant InitialState
			network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), "1");
			network.getVariantManager().setWorkingVariant("1");

			network.getBusBreakerView().getBus("_0471bd2a-c766-11e1-8775-005056c00008").setAngle(-19.0);
			// variant 1

			network.getLoad("_0448d86a-c766-11e1-8775-005056c00008").setP0(13.0).setQ0(5.0).getTerminal().setP(13.0)
					.setQ(5.0);
			// Terminal p/q should be set by loadflow

			network.getTwoWindingsTransformer("_045c1248-c766-11e1-8775-005056c00008").getRatioTapChanger()
					.setTapPosition(2);
			// .setLowTapPosition(2) --> will update indirectly highStep in cgmes
			network.getTwoWindingsTransformer("_045c1248-c766-11e1-8775-005056c00008").getPhaseTapChanger()
					.setTapPosition(2);

			// double p1 = 1.0;
			// double q1 = 2.0;
			// lccConverterStation.getTerminal().setP(p1);
			// lccConverterStation.getTerminal().setQ(q1);
			//
			// Terminal t =
			// network.getTwoWindingsTransformer("_045c1248-c766-11e1-8775-005056c00008")
			// .getTerminal(Branch.Side.ONE);
			// network.getTwoWindingsTransformer("_045c1248-c766-11e1-8775-005056c00008")
			// .newPhaseTapChanger()
			// .setLowTapPosition(0)
			// .setTapPosition(1)
			// .setRegulationMode(PhaseTapChanger.RegulationMode.CURRENT_LIMITER)
			// .setRegulationValue(930.6667)
			// .setRegulating(true)
			// .setRegulationTerminal(t)
			// .beginStep().setR(-28.091503).setX(-28.091503).setG(0.0).setB(0.0).setRho(1.0).setAlpha(5.42).endStep()
			// .beginStep().setR(39.78473).setX(39.784725).setG(0.0).setB(0.0).setRho(1.0).setAlpha(-42.8).endStep()
			// .add();
			// network.getSubstation("_047c929a-c766-11e1-8775-005056c00008").setCountry(Country.GR);

			// for (ShuntCompensator sc :
			// network.getVoltageLevel("_04728074-c766-11e1-8775-005056c00008")
			// .getShuntCompensators()) {
			// if (sc.getId().equals("_04553478-c766-11e1-8775-005056c00008")) {
			// sc.setbPerSection(2.2)
			// .setMaximumSectionCount(9);
			// }
			// }
			LOG.info("checkBusBreakerView "
			// +
			// network.getLoad("_0448d86a-c766-11e1-8775-005056c00008").getTerminal()
			// .getBusBreakerView().getBus().getId() // --> cim:TopologicalNode in
			// TP
			// + "; checkNodeBreakerView "
			// +
			// network.getLoad("_0448d86a-c766-11e1-8775-005056c00008").getTerminal()
			// .getNodeBreakerView().getNode()
			// + "; checkBusView "
			// +
			// network.getLoad("_0448d86a-c766-11e1-8775-005056c00008").getTerminal()
			// .getBusView().getBus().getId() // --> VoltageLevel ID + 0
			);

		} catch (Exception e) {
			LOG.info(e.getMessage());
		}

	}

	static List<IidmChange> changes;
	private static final Logger LOG = LoggerFactory.getLogger(UpdateNetworkFromCatalog16.class);
}
