package com.powsybl.cgmes.conversion.update.elements14;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.powsybl.cgmes.conversion.update.CgmesPredicateDetails;
import com.powsybl.cgmes.conversion.update.ConversionMapper;
import com.powsybl.cgmes.conversion.update.IidmChange;
import com.powsybl.cgmes.model.CgmesModel;
import com.powsybl.iidm.network.Substation;
import com.powsybl.triplestore.api.PropertyBag;
import com.powsybl.triplestore.api.PropertyBags;

public class SubstationToSubstation implements ConversionMapper {

    public SubstationToSubstation(IidmChange change, CgmesModel cgmes) {
        this.change = change;
        this.cgmes = cgmes;
        newSubstation = (Substation) change.getIdentifiable();
    }

    @Override
    public Multimap<String, CgmesPredicateDetails> mapIidmToCgmesPredicates() {

        final Multimap<String, CgmesPredicateDetails> map = ArrayListMultimap.create();

        map.put("rdfType", new CgmesPredicateDetails("rdf:type", "_EQ", false, "cim:Substation"));

        String name = newSubstation.getName();
        if (name != null) {
            map.put("name", new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false, name));
        }

        String country = newSubstation.getCountry().map(Enum::toString).orElse("");
        
        if (getRegion(country).containsKey(REGION_NAME)) {
            /**
             * Create GeographicalRegion element with random UUID ID
             */
            String regionId = getRegion(country).get(REGION_ID);
            map.put("rdfTypeRegion",
                new CgmesPredicateDetails("rdf:type", "_EQ", false, "cim:GeographicalRegion", regionId));

            map.put("nameRegion",
                new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false, country, regionId));
            /**
             * Create SubGeographicalRegion element with random UUID ID
             */
            String subRegionId = getRegion(country).get(SUBREGION_ID);        
           
            map.put("rdfTypeSubRegion",
                new CgmesPredicateDetails("rdf:type", "_EQ", false, "cim:SubGeographicalRegion", subRegionId));

            map.put(SUBREGION_NAME, new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false,
                getRegion(country).get(SUBREGION_NAME), subRegionId));

            map.put(SUBREGION_ID,
                new CgmesPredicateDetails("cim:SubGeographicalRegion.Region", "_EQ", true, regionId, subRegionId));
            
            map.put("country", new CgmesPredicateDetails("cim:Substation.Region", "_EQ", true, subRegionId));
            
        } else if (getRegion(country).containsKey(REGION_ID)) {
            /**
             * Create SubGeographicalRegion element with random UUID ID
             */
            String subRegionId = getRegion(country).get(SUBREGION_ID);
            map.put("rdfTypeSubRegion",
                new CgmesPredicateDetails("rdf:type", "_EQ", false, "cim:SubGeographicalRegion", subRegionId));

            map.put(SUBREGION_NAME, new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false,
                getRegion(country).get(SUBREGION_NAME), subRegionId));

            String regionId = getRegion(country).get(REGION_ID);
            map.put(SUBREGION_ID,
                new CgmesPredicateDetails("cim:SubGeographicalRegion.Region", "_EQ", true, regionId, subRegionId));
            
            map.put("country", new CgmesPredicateDetails("cim:Substation.Region", "_EQ", true, subRegionId));
            
        } else {
            /**
             * Create cim:Substation.Region element for new Substation
             */
          String subRegionId = getRegion(country).get(SUBREGION_ID);
          
          map.put("country", new CgmesPredicateDetails("cim:Substation.Region", "_EQ", true, subRegionId));          
        }
        return map;
    }

    private String getGeographicalTags(Substation newSubstation) {
        String geographicalTags = "";
        for (String t : newSubstation.getGeographicalTags()) {
            geographicalTags = geographicalTags.concat(t);
        }
        return geographicalTags;
    }

    private Map<String, String> getRegion(String country) {
        Map<String, String> m = new HashMap<String, String>();
        PropertyBags substations = cgmes.substations();
        Iterator i = substations.iterator();
        while (i.hasNext()) {
            PropertyBag pb = (PropertyBag) i.next();
            if (pb.getId(REGION_NAME).equals(country)) {
                if (getGeographicalTags(newSubstation).contains(pb.getId("SubRegion"))) {
                    m.put(SUBREGION_ID, pb.getId("SubRegion"));
                    return m;
                } else {
                    // if subRegion is new
                    m.put(REGION_ID, pb.getId("Region"));
                    m.put(SUBREGION_ID,
                        getGeographicalTags(newSubstation) != null ? getGeographicalTags(newSubstation)
                            : UUID.randomUUID().toString());
                    // TODO elena: what should be IdentifiedObject.name for new
                    // SubGeographicalRegion? Keep it as tags for now.
                    m.put(SUBREGION_NAME, getGeographicalTags(newSubstation));
                    return m;
                }
            } else {
                continue;
            }
        }
        // if region is new
        m.put(REGION_NAME, country);
        m.put(REGION_ID, UUID.randomUUID().toString());
        m.put(SUBREGION_ID, getGeographicalTags(newSubstation) != null ? getGeographicalTags(newSubstation)
            : UUID.randomUUID().toString());
        m.put(SUBREGION_NAME, getGeographicalTags(newSubstation));
        return m;
    }

//    // TODO elena fix SUBREGION_NAME if not exist
//    private Map<String, String> getSubRegion(String country) {
//        Map<String, String> map = new HashMap<String, String>();
//        PropertyBags substations = cgmes.substations();
//        Iterator i = substations.iterator();
//        while (i.hasNext()) {
//            PropertyBag pb = (PropertyBag) i.next();
//            if (pb.getId(REGION_NAME).equals(country)) {
//                map.put(REGION_NAME, country);
//                map.put(REGION_ID, pb.getId("Region"));
//                map.put(SUBREGION_ID, pb.getId("SubRegion"));
//                map.put(SUBREGION_NAME, pb.getId("subRegionName") != null ? pb.getId("subRegionName") : "_01");
//                return map;
//            } else {
//                continue;
//            }
//        }
//        map.put(REGION_NAME, country);
//        map.put(REGION_ID, UUID.randomUUID().toString());
//        map.put(SUBREGION_ID, getGeographicalTags(newSubstation) != null ? getGeographicalTags(newSubstation)
//            : UUID.randomUUID().toString());
//        map.put(SUBREGION_NAME, "_01");
//        return map;
//    }

    private IidmChange change;
    private CgmesModel cgmes;
    Substation newSubstation;
}

final class GeographicalRegion {

}
