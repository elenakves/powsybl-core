package com.powsybl.cgmes.conversion.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Multimap;
import com.powsybl.cgmes.conversion.update.elements14.*;
import com.powsybl.cgmes.model.CgmesModel;

public class IidmToCgmes14 extends AbstractIidmToCgmes {

    public IidmToCgmes14(IidmChange change, CgmesModel cgmes) {
        super(change, cgmes);
    }

    public IidmToCgmes14(IidmChange change) {
        super(change);
    }

    @Override
    protected Multimap<String, CgmesPredicateDetails> switcher() {
        switch (getIidmInstanceName()) {
            case SUBSTATION_IMPL:
                SubstationToSubstation sb = new SubstationToSubstation(change, cgmes);
                mapIidmToCgmesPredicates = sb.mapIidmToCgmesPredicates();
                break;
            case BUSBREAKER_VOLTAGELEVEL:
                VoltageLevelToVoltageLevel vl = new VoltageLevelToVoltageLevel(change, cgmes);
                mapIidmToCgmesPredicates = vl.mapIidmToCgmesPredicates();
                break;
            case CONFIGUREDBUS_IMPL:
                BusToTopologicalNode btn = new BusToTopologicalNode(change, cgmes);
                mapIidmToCgmesPredicates = btn.mapIidmToCgmesPredicates();
                break;
            case TWOWINDINGS_TRANSFORMER_IMPL:
                TwoWindingsTransformerToPowerTransformer twpt = new TwoWindingsTransformerToPowerTransformer(change,
                    cgmes);
                mapIidmToCgmesPredicates = twpt.mapIidmToCgmesPredicates();
                break;
            case GENERATOR_IMPL:
                GeneratorToSynchronousMachine gsm = new GeneratorToSynchronousMachine(change, cgmes);
                mapIidmToCgmesPredicates = gsm.mapIidmToCgmesPredicates();
                break;
            case LOAD_IMPL:
                LoadToEnergyConsumer lec = new LoadToEnergyConsumer(change, cgmes);
                mapIidmToCgmesPredicates = lec.mapIidmToCgmesPredicates();
                break;
            case LINE_IMPL:
                LineToACLineSegment lac = new LineToACLineSegment(change, cgmes);
                mapIidmToCgmesPredicates = lac.mapIidmToCgmesPredicates();
                break;
            case SHUNTCOMPENSATOR_IMPL:
                ShuntCompensatorToShuntCompensator sc = new ShuntCompensatorToShuntCompensator(change);
                mapIidmToCgmesPredicates = sc.mapIidmToCgmesPredicates();
                break;
            default:
                LOG.info("This element is not convertable to CGMES");
        }

        return mapIidmToCgmesPredicates;
    }

    private static final Logger LOG = LoggerFactory.getLogger(IidmToCgmes14.class);
}
