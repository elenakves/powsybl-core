/**
 * Copyright (c) 2017-2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.powsybl.triplestore.impl.jena;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.LabelExistsException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFWriter;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.Util;
import org.apache.jena.shared.PropertyNotFoundException;
import org.apache.jena.shared.uuid.JenaUUID;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.powsybl.commons.datasource.DataSource;
import com.powsybl.triplestore.api.AbstractPowsyblTripleStore;
import com.powsybl.triplestore.api.PropertyBag;
import com.powsybl.triplestore.api.PropertyBags;
import com.powsybl.triplestore.api.TripleStoreException;

/**
 * @author Luma Zamarreño <zamarrenolm at aia.es>
 */
public class TripleStoreJena extends AbstractPowsyblTripleStore {

    public TripleStoreJena() {
        // creates an in-memory Jena model that is able to contain multiple graphs
        dataset = DatasetFactory.createMem();

        // Create a model just to obtain a writer and configure it
        writer = ModelFactory.createDefaultModel().getWriter("RDF/XML-ABBREV");
        writer.setProperty("showXmlDeclaration", "true");
        writer.setProperty("tab", "8");
        writer.setProperty("relativeURIs", "same-document,relative");

        // We create a model that will be the union of all loaded graphs,
        // to be able to make queries over all data without the need for specifying a
        // graph
        // https://stackoverflow.com/questions/6981467/jena-arq-difference-between-model-graph-and-dataset
        union = ModelFactory.createDefaultModel();
    }

    @Override
    public void read(String base, String contextName, InputStream is) {
        Model m = ModelFactory.createDefaultModel();
        m.read(is, base, formatFromName(contextName));
        dataset.addNamedModel(namedModelFromName(contextName), m);
        union = union.union(m);
    }

    private static String formatFromName(String name) {
        if (name.endsWith(".ttl")) {
            return "TURTLE";
        } else if (name.endsWith(".xml")) {
            return "RDF/XML";
        }
        return "RDF/XML";
    }

    @Override
    public void write(DataSource ds) {
        Iterator<String> k = dataset.listNames();
        while (k.hasNext()) {
            String n = k.next();
            Model m = dataset.getNamedModel(n);
            writer.setProperty("prettyTypes", subjectsTypes(m));
            writer.write(m, outputStream(ds, n), n);
        }
    }

    @Override
    public void print(PrintStream out) {
        out.println("TripleStore based on Jena. Graph names and sizes with subjects types");
        Iterator<String> k = dataset.listNames();
        while (k.hasNext()) {
            String n = k.next();
            Model m = dataset.getNamedModel(n);
            out.println("    " + n + " : " + m.size());
            out.println("        " + Arrays.toString(subjectsTypes(m)).replace(",", ",\n        "));
        }
    }

    @Override
    public Set<String> contextNames() {
        Iterable<String> it = dataset::listNames;
        return StreamSupport.stream(it.spliterator(), false).collect(Collectors.toSet());
    }

    @Override
    public void clear(String contextName) {
        String mname = namedModelFromName(contextName);
        Model m = dataset.getNamedModel(mname);
        union.remove(m);
        dataset.removeNamedModel(mname);
        m.removeAll();
    }

    @Override
    public PropertyBags query(String query) {
        String query1 = adjustedQuery(query);
        PropertyBags results = new PropertyBags();
        // Because Jena in-memory does not support default graph
        // as the union of named graphs
        // We use the dataset for maintaining separate graphs,
        // but query in general against union
        // Only query against dataset if we found a GRAPH clause in the query text
        try (QueryExecution q = queryExecutionFromQueryText(query1)) {
            // If we want to analyze the algebra of the query,
            // set ARQ.symLogExec to true in the query context
            ResultSet r = q.execSelect();
            List<String> names = r.getResultVars();
            while (r.hasNext()) {
                QuerySolution s = r.next();
                PropertyBag result = new PropertyBag(names);
                names.forEach(name -> {
                    if (s.contains(name)) {
                        result.put(name, stringValue(s.get(name)));
                    }
                });
                if (!result.isEmpty()) {
                    results.add(result);
                }
            }
        }
        return results;
    }

    @Override
    public void update(String query) {
        // TODO elena
        // https://jena.apache.org/documentation/query/update.html
        String updateStatement = adjustedQuery(query);
        UpdateRequest request = UpdateFactory.create(updateStatement);
        UpdateAction.execute(request, dataset);
    }

    @Override
    public void duplicate() {
        // TODO elena Clone by statements
        Dataset datasetClone = null;
        try {
            datasetClone = DatasetFactory.createMem();
            Iterator<String> names = dataset.listNames();
            while (names.hasNext()) {
                List<Statement> listStatements = new ArrayList<Statement>();
                String name = names.next();
                Model modelClone = datasetClone.getNamedModel(name);
                if (dataset.containsNamedModel(namedModelFromName(name))) {
                    Model m = dataset.getNamedModel(namedModelFromName(name));
                    StmtIterator statements = m.listStatements();
                    while (statements.hasNext()) {
                        Statement statement = statements.next();
                        listStatements.add(statement);
                    }
                    modelClone.add(listStatements);
                }
            }
            checkClonedRepo(dataset, datasetClone);
        } finally {
            dataset.close();
            if (datasetClone != null) {
                datasetClone.close();
            }
        }
    }

    @Override
    public void duplicateRepo() {
        // TODO elena clone by repo
        Dataset datasetClone = null;
        try {
            datasetClone = DatasetFactory.createMem();

            Iterator<String> k = dataset.listNames();
            while (k.hasNext()) {
                String n = k.next();
                if (dataset.containsNamedModel(namedModelFromName(n))) {
                    Model m = dataset.getNamedModel(namedModelFromName(n));
                    datasetClone.addNamedModel(namedModelFromName(n), m);
                    if (datasetClone.containsNamedModel(namedModelFromName(n))) {
                        Model mClone = datasetClone.getNamedModel(namedModelFromName(n));
                    }
                }
            }
            checkClonedRepo(dataset, datasetClone);
        } finally {
            dataset.close();
            if (datasetClone != null) {
                datasetClone.close();
            }
        }
    }

    private void checkClonedRepo(Dataset dataset, Dataset datasetClone) {
        Iterator<String> names = datasetClone.listNames();
        while (names.hasNext()) {
            String name = names.next();
            dataset.removeNamedModel(namedModelFromName(name));
            if (dataset.containsNamedModel(namedModelFromName(name))) {
                Model m = dataset.getNamedModel(name);
                LOGGER.info("***checkClonedRepo***\n dataset contains  " + name + " size : " + m.size());
            } else if (datasetClone.containsNamedModel(namedModelFromName(name))) {
                Model m = datasetClone.getNamedModel(name);
                LOGGER.info("***checkClonedRepo***\n datasetClone contains  " + name + " size : " + m.size() +
                    "\n But dataset does not --> they are independent");
            } else {
                LOGGER.info("Neither dataset nor datasetClone contains " + name);
            }
        }
    }

    @Override
    public void add(String graph, String objType, PropertyBags statements) {
        String name = null;
        Iterator<String> k = dataset.listNames();
        while (k.hasNext()) {
            String n = k.next();
            if (n.contains("EQ")) {
                name = n.replace("EQ", graph);
                break;
            }
        }

        String context = name;
        Model m = dataset.getNamedModel(context);
        if (m == null) {
            m = ModelFactory.createDefaultModel();
            m.setNsPrefixes(union.getNsPrefixMap());
        }

        for (PropertyBag statement : statements) {
            createStatements(m, objType, statement);
        }
        dataset.addNamedModel(context, m);
        union = union.union(m);
    }

    private void createStatements(Model m, String objType, PropertyBag statement) {

        Resource resource = m.createResource(JenaUUID.generate().asString());
        Property parentPredicate = RDF.type;
        Resource parentObject = m.createResource(objType);
        Statement parentSt = m.createStatement(resource, parentPredicate, parentObject);
        m.add(parentSt);

        List<String> names = statement.propertyNames();
        names.forEach(name -> {
            Property predicate = m.createProperty(objType + "." + name);
            Statement st;
            if (statement.isResource(name)) {
                String namespace = m.getNsPrefixURI(statement.namespacePrefix(name));
                Resource object = m.createResource(namespace + statement.get(name));
                st = m.createStatement(resource, predicate, object);
            } else {
                String object = statement.get(name);
                st = m.createStatement(resource, predicate, object);
            }
            m.add(st);
        });
    }

    private QueryExecution queryExecutionFromQueryText(String query) {
        if (containsGraphClause(query)) {
            return QueryExecutionFactory.create(query, dataset);
        } else {
            return QueryExecutionFactory.create(query, union);
        }
    }

    private static boolean containsGraphClause(String query) {
        return query.contains("GRAPH ");
    }

    private static String stringValue(RDFNode n) {
        if (n.isResource()) {
            return n.asResource().getURI();
        } else if (n.isLiteral()) {
            return n.asLiteral().getValue().toString();
        }
        return n.toString();
    }

    private static Resource[] subjectsTypes(Model model) {
        Set<Resource> types = new HashSet<>();
        ResIterator rs = model.listSubjects();
        while (rs.hasNext()) {
            Resource r = rs.nextResource();
            Statement s = type(r);
            if (s != null) {
                types.add(s.getObject().asResource());
            }
        }
        return types.toArray(new Resource[0]);
    }

    private static Statement type(Resource r) {
        Statement rslt;
        try {
            if (r instanceof Statement) {
                rslt = ((Statement) r).getStatementProperty(RDF.type);
                if (rslt == null || (!rslt.getObject().equals(RDF.Statement))) {
                    throw new TripleStoreException(String.format("Looking for RDF.type for statement %s", r));
                }
            } else {
                rslt = r.getRequiredProperty(RDF.type);
            }
        } catch (PropertyNotFoundException x) {
            if (r instanceof Statement) {
                throw new TripleStoreException(String.format("Missing RDF.type for statement %s", r), x);
            }
            rslt = null;
        }
        if (rslt == null || !validType(rslt.getObject())) {
            return null;
        }
        return rslt;
    }

    private static boolean validType(RDFNode n) {
        if (!(n instanceof Resource)) {
            return false;
        }
        if (n.isAnon()) {
            return false;
        }
        // Only allow resources with namespace and fragment ID
        String uri = ((Resource) n).getURI();
        int split = Util.splitNamespaceXML(uri);
        return !(split == 0 || split == uri.length());
    }

    private String namedModelFromName(String contextName) {
        if (contextName.startsWith(namespaceForContexts())) {
            return contextName;
        }
        return namespaceForContexts() + contextName;
    }

    private final Dataset dataset;
    private Model union;
    private RDFWriter writer;
    private static final Logger LOGGER = LoggerFactory.getLogger(TripleStoreJena.class);
}
