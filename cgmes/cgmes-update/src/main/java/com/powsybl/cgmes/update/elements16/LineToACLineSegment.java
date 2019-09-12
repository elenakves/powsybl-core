package com.powsybl.cgmes.update.elements16;

import java.util.Iterator;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.powsybl.cgmes.model.CgmesModel;
import com.powsybl.cgmes.update.CgmesPredicateDetails;
import com.powsybl.cgmes.update.ConversionMapper;
import com.powsybl.cgmes.update.IidmChange;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Line;
import com.powsybl.triplestore.api.PropertyBag;
import com.powsybl.triplestore.api.PropertyBags;

public class LineToACLineSegment implements ConversionMapper {

    public LineToACLineSegment(IidmChange change, CgmesModel cgmes) {
        this.change = change;
        this.cgmes = cgmes;
    }

    @Override
    public Multimap<String, CgmesPredicateDetails> mapIidmToCgmesPredicates() {

        final Multimap<String, CgmesPredicateDetails> map = ArrayListMultimap.create();
        Line newLine = (Line) change.getIdentifiable();

        map.put("rdfType", new CgmesPredicateDetails("rdf:type", "_EQ", false, "cim:ACLineSegment"));

        String name = newLine.getName();
        if (name != null) {
            map.put("name", new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false, name));
        }

        double r = newLine.getR();
        if (!String.valueOf(r).equals("NaN")) {
            map.put("r", new CgmesPredicateDetails("cim:ACLineSegment.r", "_EQ", false, String.valueOf(r)));
        }

        double x = newLine.getX();
        if (!String.valueOf(x).equals("NaN")) {
            map.put("x", new CgmesPredicateDetails("cim:ACLineSegment.x", "_EQ", false, String.valueOf(x)));
        }

        double b1 = !String.valueOf(newLine.getB1()).equals("NaN") ? newLine.getB1() : 0.0;
        double b2 = !String.valueOf(newLine.getB2()).equals("NaN") ? newLine.getB2() : 0.0;
        map.put("b1",
            new CgmesPredicateDetails("cim:ACLineSegment.bch", "_EQ", false, String.valueOf(b1 + b2)));
        map.put("b2",
            new CgmesPredicateDetails("cim:ACLineSegment.bch", "_EQ", false, String.valueOf(b1 + b2)));

        double g1 = !String.valueOf(newLine.getG1()).equals("NaN") ? newLine.getG1() : 0.0;
        double g2 = !String.valueOf(newLine.getG2()).equals("NaN") ? newLine.getG2() : 0.0;
        map.put("g1",
            new CgmesPredicateDetails("cim:ACLineSegment.gch", "_EQ", false, String.valueOf(g1 + g2)));
        map.put("g2",
            new CgmesPredicateDetails("cim:ACLineSegment.gch", "_EQ", false, String.valueOf(g1 + g2)));

        String baseVoltageId = getBaseVoltageId(newLine);
        map.put("BaseVoltage", new CgmesPredicateDetails(
            "cim:ConductingEquipment.BaseVoltage", "_EQ", true, baseVoltageId));

        return map;
    }

    /**
     * @return the base voltage id
     */
    private String getBaseVoltageId(Line newLine) {
        String VoltageLevelId = newLine.getTerminal(Branch.Side.ONE).getVoltageLevel().getId();
        PropertyBags voltageLevels = cgmes.voltageLevels();
        Iterator i = voltageLevels.iterator();
        while (i.hasNext()) {
            PropertyBag pb = (PropertyBag) i.next();
            if (pb.getId("VoltageLevel").equals(VoltageLevelId)) {
                return pb.getId("BaseVoltage");
            } else {
                continue;
            }
        }
        return VoltageLevelId.concat("_BV");
    }

    private IidmChange change;
    private CgmesModel cgmes;
}
