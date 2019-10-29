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
		String subRegionId = getSubRegion(country).get(SUBREGION_ID);
		map.put("country", new CgmesPredicateDetails("cim:Substation.Region", "_EQ", true, subRegionId));

		/**
		 * Create GeographicalRegion element, if not exist, with random UUID ID
		 */
		String regionId = getSubRegion(country).get(REGION_ID);
		map.put("rdfTypeRegion",
				new CgmesPredicateDetails("rdf:type", "_EQ", false, "cim:GeographicalRegion", regionId));

		map.put("nameRegion", new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false, country, regionId));

		/**
		 * Create SubGeographicalRegion element, if not exist, with random UUID ID
		 */
		map.put("rdfTypeSubRegion",
				new CgmesPredicateDetails("rdf:type", "_EQ", false, "cim:SubGeographicalRegion", subRegionId));

		map.put(SUBREGION_NAME, new CgmesPredicateDetails("cim:IdentifiedObject.name", "_EQ", false,
				getSubRegion(country).get(SUBREGION_NAME), subRegionId));

		map.put(SUBREGION_ID,
				new CgmesPredicateDetails("cim:SubGeographicalRegion.Region", "_EQ", true, regionId, subRegionId));

		return map;
	}

	private String getGeographicalTags(Substation newSubstation) {
		String geographicalTags = "";
		for (String t : newSubstation.getGeographicalTags()) {
			geographicalTags = geographicalTags.concat(t);
		}
		return geographicalTags;
	}

	// TODO elena fix SUBREGION_NAME if not exist
	private Map<String, String> getSubRegion(String country) {
		Map<String, String> map = new HashMap<String, String>();
		PropertyBags substations = cgmes.substations();
		Iterator i = substations.iterator();
		while (i.hasNext()) {
			PropertyBag pb = (PropertyBag) i.next();
			if (pb.getId(REGION_NAME).equals(country)) {
				map.put(REGION_NAME, country);
				map.put(REGION_ID, pb.getId("Region"));
				map.put(SUBREGION_ID, pb.getId("SubRegion"));
				map.put(SUBREGION_NAME, pb.getId("subRegionName") != null ? pb.getId("subRegionName") : "_01");
				return map;
			} else {
				continue;
			}
		}
		map.put(REGION_NAME, country);
		map.put(REGION_ID, UUID.randomUUID().toString());
		map.put(SUBREGION_ID, getGeographicalTags(newSubstation) != null ? getGeographicalTags(newSubstation)
				: UUID.randomUUID().toString());
		map.put(SUBREGION_NAME, "_01");
		return map;
	}

	private IidmChange change;
	private CgmesModel cgmes;
	Substation newSubstation;
}
