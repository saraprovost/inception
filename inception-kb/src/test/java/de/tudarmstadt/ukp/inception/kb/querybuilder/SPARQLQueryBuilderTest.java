/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.kb.querybuilder;

import static de.tudarmstadt.ukp.inception.kb.querybuilder.SPARQLQueryBuilderAsserts.asHandles;
import static de.tudarmstadt.ukp.inception.kb.querybuilder.SPARQLQueryBuilderAsserts.assertThatChildrenOfExplicitRootCanBeRetrieved;
import static de.tudarmstadt.ukp.inception.kb.querybuilder.SPARQLQueryBuilderAsserts.exists;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.lucene.LuceneSail;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import de.tudarmstadt.ukp.inception.kb.IriConstants;
import de.tudarmstadt.ukp.inception.kb.RepositoryType;
import de.tudarmstadt.ukp.inception.kb.graph.KBHandle;
import de.tudarmstadt.ukp.inception.kb.model.KnowledgeBase;

public class SPARQLQueryBuilderTest
{
    private static final String TURTLE_PREFIX = String.join("\n",
            "@base <http://example.org/> .",
            "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .",
            "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .");
    
    private static final String DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE = String.join("\n",
            "<#green-goblin>",
            "    rdfs:label 'Green Goblin' ;",
            "    rdfs:label 'Green Goblin'@en ;",
            "    rdfs:label 'Grüner Goblin'@de ;",
            "    rdfs:label 'Goblin vert'@fr ;",
            "    rdfs:comment 'Little green monster' ;",
            "    rdfs:comment 'Little green monster'@en ;",
            "    rdfs:comment 'Kleines grünes Monster'@de .",
            "",
            "<#lucky-green>",
            "    rdfs:label 'Lucky Green' ;",
            "    rdfs:label 'Lucky Green'@en ;",
            "    rdfs:comment 'Lucky Irish charm' ;",
            "    rdfs:comment 'Lucky Irish charm'@en .",
            "",
            "<#red-goblin>",
            "    rdfs:label 'Red Goblin' ;",
            "    rdfs:comment 'Little red monster' .");

    private static final String DATA_LABELS_WITHOUT_LANGUAGE = String.join("\n",
            "<#green-goblin>",
            "    rdfs:label 'Green Goblin' .",
            "",
            "<#lucky-green>",
            "    rdfs:label 'Lucky Green' .",
            "",
            "<#red-goblin>",
            "    rdfs:label 'Red Goblin' .");

    private static final String LABEL_SUBPROPERTY = String.join("\n",
            "<#sublabel>",
            "    rdfs:subPropertyOf rdfs:label .",
            "",
            "<#green-goblin>",
            "    <#sublabel> 'Green Goblin' .");

    /**
     * This dataset contains a hierarchy of classes and instances with a naming scheme.
     * There is an implicit and an explicit root class. All classes have "class" in their name.
     * Subclasses start with "subclass" and then a number. Instances start with the number of the
     * class to which they belong followed by a number.
     */
    private static final String DATA_CLASS_RDFS_HIERARCHY = String.join("\n",
            "<#explicitRoot>",
            "    rdf:type rdfs:Class .",
            "<#subclass1>",
            "    rdf:type rdfs:Class ;",
            "    rdfs:subClassOf <#explicitRoot> .",
            "<#subclass1-1>",
            "    rdfs:subClassOf <#subclass1> .",
            "<#subclass1-1-1>",
            "    rdfs:subClassOf <#subclass1-1> .",
            "<#subclass2>",
            "    rdfs:subClassOf <#explicitRoot> .",
            "<#subclass3>",
            "    rdfs:subClassOf <#implicitRoot> .",
            "<#0-instance-1>",
            "    rdf:type <#root> .",
            "<#1-instance-1>",
            "    rdf:type <#subclass1> .",
            "<#2-instance-2>",
            "    rdf:type <#subclass2> .",
            "<#3-instance-3>",
            "    rdf:type <#subclass3> .",
            "<#1-1-1-instance-4>",
            "    rdf:type <#subclass1-1-1> ."
    );

    private KnowledgeBase kb;
    private Repository rdf4jLocalRepo;
    private Repository ukpVirtuosoRepo;
    private Repository zbwStw;
    private Repository zbwGnd;
    private Repository wikidata;
    private Repository dbpedia;
    private Repository yago;
    private Repository hucit;
    private Repository britishMuseum;
    
    @Before
    public void setUp()
    {
        kb = new KnowledgeBase();
        kb.setDefaultLanguage("en");
        kb.setType(RepositoryType.LOCAL);
        kb.setClassIri(RDFS.CLASS);
        kb.setSubclassIri(RDFS.SUBCLASSOF);
        kb.setTypeIri(RDF.TYPE);
        kb.setLabelIri(RDFS.LABEL);
        kb.setPropertyTypeIri(RDF.PROPERTY);
        kb.setDescriptionIri(RDFS.COMMENT);
        kb.setPropertyLabelIri(RDFS.LABEL);
        kb.setPropertyDescriptionIri(RDFS.COMMENT);
        kb.setSubPropertyIri(RDFS.SUBPROPERTYOF);
        kb.setFullTextSearchIri(null);
        kb.setMaxResults(1000);
        
        // Local in-memory store - this should be used for most tests because we can
        // a) rely on its availability
        // b) import custom test data
        LuceneSail lucenesail = new LuceneSail();
        lucenesail.setParameter(LuceneSail.LUCENE_RAMDIR_KEY, "true");
        lucenesail.setBaseSail(new MemoryStore());
        rdf4jLocalRepo = new SailRepository(lucenesail);
        rdf4jLocalRepo.init();
        
        ukpVirtuosoRepo = new SPARQLRepository(
                "http://knowledgebase.ukp.informatik.tu-darmstadt.de:8890/sparql");
        ukpVirtuosoRepo.init();

        // http://zbw.eu/beta/sparql-lab/?endpoint=http://zbw.eu/beta/sparql/stw/query
        zbwStw = new SPARQLRepository("http://zbw.eu/beta/sparql/stw/query");
        zbwStw.init();

        // http://zbw.eu/beta/sparql-lab/?endpoint=http://zbw.eu/beta/sparql/gnd/query
        zbwGnd = new SPARQLRepository("http://zbw.eu/beta/sparql/gnd/query");
        zbwGnd.init();

        // https://query.wikidata.org/sparql
        wikidata = new SPARQLRepository("https://query.wikidata.org/sparql");
        wikidata.init();
        
        // https://dbpedia.org/sparql
        dbpedia = new SPARQLRepository("https://dbpedia.org/sparql");
        dbpedia.init();

        // https://linkeddata1.calcul.u-psud.fr/sparql
        yago = new SPARQLRepository("https://linkeddata1.calcul.u-psud.fr/sparql");
        yago.init();

        // http://nlp.dainst.org:8888/sparql
        hucit = new SPARQLRepository("http://nlp.dainst.org:8888/sparql");
        hucit.init();
        
        // http://collection.britishmuseum.org/sparql
        britishMuseum = new SPARQLRepository("http://collection.britishmuseum.org/sparql");
        britishMuseum.init();
    }
    
    /**
     * Checks that {@code SPARQLQueryBuilder#exists(RepositoryConnection, boolean)} can return 
     * {@code true} by querying for a list of all classes in {@link #DATA_CLASS_RDFS_HIERARCHY}
     * which contains a number of classes.
     */
    @Test
    public void thatExistsReturnsTrueWhenDataQueriedForExists() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        boolean result = exists(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb));
        
        assertThat(result).isTrue();
    }

    /**
     * If the KB has no default language set, then only labels and descriptions with no language
     * at all should be returned.
     */
    @Test
    public void thatOnlyLabelsAndDescriptionsWithNoLanguageAreRetrieved() throws Exception
    {
        kb.setDefaultLanguage(null);
        
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX,
                DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);
        
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withIdentifier("http://example.org/#green-goblin")
                .retrieveLabel()
                .retrieveDescription());
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "description", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#green-goblin", "Green Goblin",
                                "Little green monster"));
    }
    
    /**
     * If the KB has a default language set, then labels/descriptions in that language should be 
     * preferred it is permitted to fall back to labels/descriptions without any language.
     * The dataset contains only labels for French but no descriptions, so it should fall back to
     * returning the description without any language.
     */
    @Test
    public void thatLabelsAndDescriptionsWithLanguageArePreferred() throws Exception
    {
        // The dataset contains only labels for French but no descriptions
        kb.setDefaultLanguage("fr");
        
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX,
                DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);
        
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withIdentifier("http://example.org/#green-goblin")
                .retrieveLabel()
                .retrieveDescription());
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "description", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#green-goblin", "Goblin vert",
                                "Little green monster", "fr"));
    }
    
    /**
     * Checks that {@code SPARQLQueryBuilder#exists(RepositoryConnection, boolean)} can return 
     * {@code false} by querying for the parent of a root class in 
     * {@link #DATA_CLASS_RDFS_HIERARCHY} which does not exist.
     */
    @Test
    public void thatExistsReturnsFalseWhenDataQueriedForDoesNotExist() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        boolean result = exists(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .parentsOf("http://example.org/#explicitRoot"));
        
        assertThat(result).isFalse();
    }
    
    /**
     * Checks that an explicitly defined class can be retrieved using its identifier.
     */
    @Test
    public void thatExplicitClassCanBeRetrievedByItsIdentifier() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        boolean result = exists(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .withIdentifier("http://example.org/#explicitRoot"));
        
        assertThat(result).isTrue();
    }

    /**
     * Checks that an implicitly defined class can be retrieved using its identifier.
     */
    @Test
    public void thatImplicitClassCanBeRetrievedByItsIdentifier() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        boolean result = exists(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .withIdentifier("http://example.org/#implicitRoot"));
        
        assertThat(result).isTrue();
    }

    /**
     * Checks that a either explicitly nor implicitly defined class can be retrieved using its 
     * identifier.
     */
    @Test
    public void thatNonClassCannotBeRetrievedByItsIdentifier() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        boolean result = exists(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .withIdentifier("http://example.org/#DoesNotExist"));
        
        assertThat(result).isFalse();
    }

    /**
     * Checks that item information can be obtained for a given subject.
     */
    @Test
    public void thatCanRetrieveItemInfoForIdentifier() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX,
                DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);

        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withIdentifier("http://example.org/#red-goblin")
                .retrieveLabel()
                .retrieveDescription());
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "description", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#red-goblin", "Red Goblin",
                                "Little red monster"));
    }

    @Test
    public void thatQueryLimitedToRootClassesDoesNotReturnOutOfScopeResults() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .roots());
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getUiLabel)
                .containsExactlyInAnyOrder("explicitRoot", "implicitRoot");
    }
    
    @Test
    public void thatQueryWithExplicitRootClassDoesNotReturnOutOfScopeResults() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        ValueFactory vf = SimpleValueFactory.getInstance();
        kb.setRootConcepts(asList(vf.createIRI("http://example.org/#implicitRoot")));
        
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .roots());
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getUiLabel)
                .containsExactlyInAnyOrder("implicitRoot");
    }
    
    @Test
    public void thatNonRootClassCanBeUsedAsExplicitRootClass() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        ValueFactory vf = SimpleValueFactory.getInstance();
        kb.setRootConcepts(asList(
                vf.createIRI("http://example.org/#implicitRoot"),
                vf.createIRI("http://example.org/#subclass2")));
        
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .roots());
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getUiLabel)
                .containsExactlyInAnyOrder("implicitRoot", "subclass2");
    }
    @Test
    public void thatQueryLimitedToClassesDoesNotReturnInstances() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder.forClasses(kb));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getUiLabel)
                .noneMatch(label -> label.contains("instance"));
    }

    @Test
    public void thatQueryLimitedToInstancesDoesNotReturnClasses() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder.forInstances(kb));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getUiLabel)
                .noneMatch(label -> label.contains("class"));
    }
    
    @Test
    public void thatClassQueryLimitedToAnchestorsDoesNotReturnOutOfScopeResults() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);
    
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .ancestorsOf("http://example.org/#subclass1-1-1"));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getIdentifier)
                .containsExactlyInAnyOrder("http://example.org/#explicitRoot", 
                        "http://example.org/#subclass1", "http://example.org/#subclass1-1");
    }
    
    @Test
    public void thatInstanceQueryLimitedToAnchestorsDoesNotReturnOutOfScopeResults() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);
    
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .ancestorsOf("http://example.org/#1-1-1-instance-4"));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getIdentifier)
                .containsExactlyInAnyOrder("http://example.org/#explicitRoot", 
                        "http://example.org/#subclass1", "http://example.org/#subclass1-1",
                        "http://example.org/#subclass1-1-1");
    }    
    @Test
    public void thatClassQueryLimitedToParentsDoesNotReturnOutOfScopeResults() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);
    
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .parentsOf("http://example.org/#subclass1-1"));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getIdentifier)
                .containsExactlyInAnyOrder("http://example.org/#subclass1");
    }

    @Test
    public void thatClassQueryLimitedToChildrenDoesNotReturnOutOfScopeResults() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);
    
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .childrenOf("http://example.org/#subclass1"));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getIdentifier)
                .containsExactlyInAnyOrder("http://example.org/#subclass1-1");
    }

    @Test
    public void thatClassQueryLimitedToDescendantsDoesNotReturnOutOfScopeResults() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);
    
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forClasses(kb)
                .descendantsOf("http://example.org/#subclass1"));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getIdentifier)
                .containsExactlyInAnyOrder("http://example.org/#subclass1-1", 
                        "http://example.org/#subclass1-1-1");
    }

    @Test
    public void thatInstanceQueryLimitedToDescendantsDoesNotReturnOutOfScopeResults()
        throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forInstances(kb)
                .descendantsOf("http://example.org/#subclass1"));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getIdentifier)
                .allMatch(label -> label.matches("http://example.org/#1(-1)*-instance-.*"));
    }

    @Test
    public void thatItemQueryLimitedToDescendantsDoesNotReturnOutOfScopeResults() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_CLASS_RDFS_HIERARCHY);

        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .descendantsOf("http://example.org/#subclass1"));
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(KBHandle::getIdentifier)
                .allMatch(label -> label.matches("http://example.org/#1(-1)*-instance-.*") || 
                        label.startsWith("http://example.org/#subclass1-"));
    }

    @Test
    public void testWithLabelContainingAnyOf_RDF4J_withLanguage_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        
        __testWithLabelContainingAnyOf_withLanguage(rdf4jLocalRepo);
    }
    
    @Test
    public void testWithLabelContainingAnyOf_RDF4J_withLanguage_FTS() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_LUCENE);
        
        __testWithLabelContainingAnyOf_withLanguage(rdf4jLocalRepo);
    }
    
    public void __testWithLabelContainingAnyOf_withLanguage(Repository aRepository)
        throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX,
                DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);

        List<KBHandle> results = asHandles(aRepository, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelContainingAnyOf("Goblin"));
        
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.contains("Goblin"));
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#red-goblin", "Red Goblin"),
                        new KBHandle("http://example.org/#green-goblin", "Green Goblin",
                                null, "en"));
    }
    
    @Test
    public void testWithLabelMatchingExactlyAnyOf_RDF4J_withLanguage_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        
        __testWithLabelMatchingExactlyAnyOf_withLanguage(rdf4jLocalRepo);
    }

    @Test
    public void testWithLabelMatchingExactlyAnyOf_RDF4J_withLanguage_FTS() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_LUCENE);
        
        __testWithLabelMatchingExactlyAnyOf_withLanguage(rdf4jLocalRepo);
    }

    public void __testWithLabelMatchingExactlyAnyOf_withLanguage(Repository aRepository)
        throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX,
                DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);

        List<KBHandle> results = asHandles(aRepository, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelMatchingExactlyAnyOf("Green Goblin"));
        
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.equals("Green Goblin"));
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#green-goblin", "Green Goblin",
                                null, "en"));
    }
    
    @Test
    public void testWithLabelMatchingExactlyAnyOf_RDF4J_subproperty_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        kb.setSubPropertyIri(RDFS.SUBPROPERTYOF);
        kb.setLabelIri(RDFS.LABEL);
        
        __testWithLabelMatchingExactlyAnyOf_RDF4J_subproperty(rdf4jLocalRepo);
    }
    
    @Test
    public void testWithLabelMatchingExactlyAnyOf_RDF4J_subproperty_FTS() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_LUCENE);
        kb.setSubPropertyIri(RDFS.SUBPROPERTYOF);
        kb.setLabelIri(RDFS.LABEL);
        
        __testWithLabelMatchingExactlyAnyOf_RDF4J_subproperty(rdf4jLocalRepo);
    }
    
    public void __testWithLabelMatchingExactlyAnyOf_RDF4J_subproperty(Repository aRepository)
        throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, LABEL_SUBPROPERTY);
        
        // The label "Green Goblin" is not assigned directly via rdfs:label but rather via a
        // subproperty of it. Thus, this test also checks if the label sub-property support works.
        List<KBHandle> results = asHandles(aRepository, SPARQLQueryBuilder.forItems(kb)
                    .withLabelMatchingExactlyAnyOf("Green Goblin"));
        
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.equals("Green Goblin"));
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#green-goblin", "Green Goblin"));
    }    
        
    @Test
    public void testWithLabelStartingWith_RDF4J_withoutLanguage_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        
        __testWithLabelStartingWith_RDF4J_withoutLanguage();
    }

    @Test
    public void testWithLabelStartingWith_RDF4J_withoutLanguage_FTS() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_LUCENE);
        
        __testWithLabelStartingWith_RDF4J_withoutLanguage();
    }

    public void __testWithLabelStartingWith_RDF4J_withoutLanguage() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_LABELS_WITHOUT_LANGUAGE);
    
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Green"));
        
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Green"));
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#green-goblin", "Green Goblin"));
    }

    @Test
    public void testWithLabelStartingWith_RDF4J_withLanguage_noFTS() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);

        kb.setFullTextSearchIri(null);
        
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Green Goblin"));
        
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Green"));
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#green-goblin", "Green Goblin",
                                null, "en"));
    }

    @Test
    public void testWithLabelStartingWith_RDF4J_withLanguage_FTS_1() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);

        kb.setFullTextSearchIri(IriConstants.FTS_LUCENE);
        
        // Single word - actually, we add a wildcard here so anything that starts with "Green"
        // would also be matched
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Green"));
        
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Green"));
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#green-goblin", "Green Goblin", null,
                                "en"));
    }
    
    @Test
    public void testWithLabelStartingWith_RDF4J_withLanguage_FTS_2() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);

        kb.setFullTextSearchIri(IriConstants.FTS_LUCENE);
        
        // Two words with the second being very short - this is no problem for the LUCENE FTS
        // and we simply add a wildcard to match "Green Go*"
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Green Go"));
        
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Green Go"));
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "description", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://example.org/#green-goblin", "Green Goblin",
                                null, "en"));
    }
    
    @Test
    public void testWithLabelStartingWith_RDF4J_withLanguage_FTS_3() throws Exception
    {
        importDataFromString(RDFFormat.TURTLE, TURTLE_PREFIX, DATA_LABELS_AND_DESCRIPTIONS_WITH_LANGUAGE);

        kb.setFullTextSearchIri(IriConstants.FTS_LUCENE);
        
        // Two words with the second being very short and a space following - in this case we
        // assume that the user is in fact searching for "Barack Ob" and do either drop the
        // last element nor add a wildcard
        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Green Go "));
        
        assertThat(results).isEmpty();
    }
    
    @Test
    public void testWithLabelContainingAnyOf_Virtuoso_withLanguage_FTS() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_VIRTUOSO);
        
        List<KBHandle> results = asHandles(ukpVirtuosoRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelContainingAnyOf("Tower"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.toLowerCase().contains("tower"));
    }

    @Test
    public void testWithLabelMatchingExactlyAnyOf_Virtuoso_withLanguage_FTS() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_VIRTUOSO);
        
        List<KBHandle> results = asHandles(ukpVirtuosoRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelMatchingExactlyAnyOf("Green Goblin"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> "Green Goblin".equals(label));
    }
    
    @Test
    public void testWithLabelStartingWith_Virtuoso_withLanguage_FTS_1() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_VIRTUOSO);
    
        // Single word - actually, we add a wildcard here so anything that starts with "Barack"
        // would also be matched
        List<KBHandle> results = asHandles(ukpVirtuosoRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Barack"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Barack"));
    }

    @Test
    public void testWithLabelStartingWith_Virtuoso_withLanguage_FTS_2() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_VIRTUOSO);
    
        // Two words with the second being very short - in this case, we drop the very short word
        // so that the user doesn't stop getting suggestions while writing because Virtuoso doesn't
        // do wildcards on words shorter than 4 characters
        List<KBHandle> results = asHandles(ukpVirtuosoRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Barack Ob"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Barack"));
    }

    @Test
    public void testWithLabelStartingWith_Virtuoso_withLanguage_FTS_3() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_VIRTUOSO);
    
        // Two words with the second being very short and a space following - in this case we
        // assmume that the user is in fact searching for "Barack Ob" and do either drop the
        // last element nor add a wildcard
        List<KBHandle> results = asHandles(ukpVirtuosoRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Barack Ob "));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Barack"));
    }

    @Test
    public void testWithLabelStartingWith_Virtuoso_withLanguage_FTS_4() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_VIRTUOSO);
    
        // Two words with the second being 4+ chars - we add a wildcard here so anything
        // starting with "Barack Obam" should match
        List<KBHandle> results = asHandles(ukpVirtuosoRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Barack Obam"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Barack Obam"));
    }

    @Test
    public void testWithLabelMatchingExactlyAnyOf_ZBW_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        
        List<KBHandle> results = asHandles(zbwStw, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelMatchingExactlyAnyOf("Labour"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> "Labour".equals(label));
    }

    @Test
    public void testWithLabelMatchingExactlyAnyOf_GND_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        kb.setLabelIri(RDFS.LABEL);
        kb.setSubPropertyIri(RDFS.SUBPROPERTYOF);
        
        // The label "Thomas Henricus" is not assigned directly via rdfs:label but rather via a
        // subproperty of it. Thus, this test also checks if the label sub-property support works.
        List<KBHandle> results = asHandles(zbwGnd, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelMatchingExactlyAnyOf("Thomas Henricus"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> "Thomas Henricus".equals(label));
    }

    @Test
    public void testWithLabelMatchingExactlyAnyOf_Wikidata_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        
        List<KBHandle> results = asHandles(wikidata, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelMatchingExactlyAnyOf("Labour"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> "Labour".equals(label));
    }
    
    @Test
    public void testWithLabelStartingWith_HUCIT_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        
        List<KBHandle> results = asHandles(hucit, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelStartingWith("Achilles"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Achilles"));
    }

    @Test
    public void testWithLabelStartingWith_onlyDescendants_HUCIT_noFTS() throws Exception
    {
        kb.setFullTextSearchIri(null);
        
        List<KBHandle> results = asHandles(hucit, SPARQLQueryBuilder
                .forInstances(kb)
                .descendantsOf("http://erlangen-crm.org/efrbroo/F1_Work")
                .withLabelStartingWith("Achilles"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.startsWith("Achilles"));
    }

    @Test
    public void testWithLabelContainingAnyOf_classes_HUCIT_FTS() throws Exception
    {
        kb.setFullTextSearchIri(IriConstants.FTS_VIRTUOSO);
        
        List<KBHandle> results = asHandles(hucit, SPARQLQueryBuilder
                .forClasses(kb)
                .withLabelContainingAnyOf("work"));
        
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results).isNotEmpty();
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.toLowerCase().contains("work"));
    }

    @Test
    public void thatRootsCanBeRetrieved_BritishMuseum()
    {
        List<KBHandle> results = asHandles(britishMuseum, SPARQLQueryBuilder.forClasses(kb).roots());
        
        assertThat(results).isNotEmpty();
    }

    @Test
    public void thatChildrenCanBeRetrieved_BritishMuseum()
    {
        List<KBHandle> results = asHandles(britishMuseum, SPARQLQueryBuilder
                .forClasses(kb)
                .childrenOf("file:/data-to-load/07bde589-588c-4f0d-8715-c71c0ba2bfdb/crm-extensions/E12_Production"));
        
        assertThat(results).isNotEmpty();
    }

    @Test
    public void thatChildrenOfExplicitRootCanBeRetrieved_DBPedia()
    {
        assertThatChildrenOfExplicitRootCanBeRetrieved(kb, dbpedia,
                "http://www.w3.org/2002/07/owl#Thing");
    }

    @Test
    public void thatChildrenOfExplicitRootCanBeRetrieved_YAGO()
    {
        assertThatChildrenOfExplicitRootCanBeRetrieved(kb, yago,
                "http://www.w3.org/2002/07/owl#Thing");
    }

    @Test
    public void thatParentsCanBeRetrieved_Wikidata()
    {
        ValueFactory vf = SimpleValueFactory.getInstance();
        kb.setClassIri(vf.createIRI("http://www.wikidata.org/entity/Q35120"));
        kb.setSubclassIri(vf.createIRI("http://www.wikidata.org/prop/direct/P279"));
        kb.setTypeIri(vf.createIRI("http://www.wikidata.org/prop/direct/P31"));
        
        List<KBHandle> results = asHandles(wikidata, SPARQLQueryBuilder
                .forClasses(kb)
                .ancestorsOf("http://www.wikidata.org/entity/Q5")
                .retrieveLabel());
        
        assertThat(results).isNotEmpty();
        assertThat(results)
                .as("Root concept http://www.wikidata.org/entity/Q35120 should be included")
                .extracting(KBHandle::getIdentifier)
                .contains("http://www.wikidata.org/entity/Q35120");
    }
    
    @Ignore(
            "This times out unless we restrict the query to the named graph 'http://dbpedia.org' " +
            "but we cannot do that due to https://github.com/eclipse/rdf4j/issues/1324")
    @Test
    public void thatRootsCanBeRetrieved_DBPedia()
    {
        List<KBHandle> results = asHandles(dbpedia, SPARQLQueryBuilder
                .forClasses(kb)
                .roots()
                .retrieveLabel());
        
        assertThat(results).isNotEmpty();
        
        assertThat(results)
                .extracting(KBHandle::getName)
                .contains("agent");
    }
    
    @Test
    public void thatParentsCanBeRetrieved_DBPedia()
    {
        List<KBHandle> results = asHandles(dbpedia, SPARQLQueryBuilder
                .forClasses(kb)
                .ancestorsOf("http://dbpedia.org/ontology/Organisation")
                .retrieveLabel());
        
        assertThat(results).isNotEmpty();
        
        assertThat(results)
                .extracting(KBHandle::getName)
                .contains("agent", "Thing");
    }

    public void __thatChildrenOfExplicitRootCanBeRetrieved(Repository aRepository,
            String aRootClass)
    {
        List<KBHandle> results = asHandles(aRepository, SPARQLQueryBuilder
                .forClasses(kb)
                .childrenOf(aRootClass)
                .retrieveLabel());
        
        assertThat(results).isNotEmpty();
        
        assertThat(results).allMatch(_child -> {
            try (RepositoryConnection conn = aRepository.getConnection()) {
                return SPARQLQueryBuilder.forClasses(kb).parentsOf(_child.getIdentifier())
                        .asHandles(conn, true)
                        .stream()
                        .map(KBHandle::getIdentifier)
                        .anyMatch(iri -> iri.equals(aRootClass));
            }
        });
    }
    
    @Test
    public void testWithLabelContainingAnyOf_RDF4J_pets_ttl() throws Exception
    {
        importDataFromFile("src/test/resources/data/pets.ttl");

        List<KBHandle> results = asHandles(rdf4jLocalRepo, SPARQLQueryBuilder
                .forItems(kb)
                .withLabelContainingAnyOf("Socke"));
        
        assertThat(results).extracting(KBHandle::getUiLabel)
                .allMatch(label -> label.contains("Socke"));
        assertThat(results).extracting(KBHandle::getIdentifier).doesNotHaveDuplicates();
        assertThat(results)
                .usingElementComparatorOnFields(
                        "identifier", "name", "language")
                .containsExactlyInAnyOrder(
                        new KBHandle("http://mbugert.de/pets#socke", "Socke"));
    }

    private void importDataFromFile(String aFilename) throws IOException
    {
        // Detect the file format
        RDFFormat format = Rio.getParserFormatForFileName(aFilename).orElse(RDFFormat.RDFXML);

        // Load files into the repository
        try (InputStream is = new FileInputStream(aFilename)) {
            importData(format, is);
        }
    }
    
    private void importDataFromString(RDFFormat aFormat, String... aRdfData) throws IOException
    {
        String data = String.join("\n", aRdfData);
        
        // Load files into the repository
        try (InputStream is = IOUtils.toInputStream(data, UTF_8)) {
            importData(aFormat, is);
        }
    }
    
    private void importData(RDFFormat aFormat, InputStream aIS) throws IOException
    {
        try (RepositoryConnection conn = rdf4jLocalRepo.getConnection()) {
            // If the RDF file contains relative URLs, then they probably start with a hash.
            // To avoid having two hashes here, we drop the hash from the base prefix configured
            // by the user.
            String prefix = StringUtils.removeEnd(kb.getBasePrefix(), "#");
            conn.add(aIS, prefix, aFormat);
        }
    }
}
