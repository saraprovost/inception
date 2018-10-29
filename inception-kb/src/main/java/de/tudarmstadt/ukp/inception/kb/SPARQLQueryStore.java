/*
 * Copyright 2018
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
package de.tudarmstadt.ukp.inception.kb;

import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.ntriples.NTriplesUtil;

import de.tudarmstadt.ukp.inception.kb.model.KnowledgeBase;

public final class SPARQLQueryStore
{

    public static final String SPARQL_PREFIX = String.join("\n",
            "PREFIX rdf: <" + RDF.NAMESPACE + ">",
            "PREFIX rdfs: <" + RDFS.NAMESPACE + ">",
            "PREFIX owl: <" + OWL.NAMESPACE + ">");

    /**
     * Return formatted String for the OPTIONAL part of SPARQL query for language and description
     * filter
     * 
     * @param aProperty
     *            The property IRI for the optional filter
     * @param aLanguage
     *            The variable indicating the language
     * @param variable
     *            The variable for the IRI like '?s'
     * @param filterVariable
     *            The variable for the language (?l) and description (?d)
     * @return String format for OPTIONAL part of SPARQL query
     */
    private static final String optionalLanguageFilteredValue(String aProperty, String aLanguage,
            String variable, String filterVariable)
    {
        StringBuilder fragment = new StringBuilder();
        
        fragment.append("  OPTIONAL {\n");
        fragment.append("    " + variable + " ").append(aProperty).append(" ")
                .append(filterVariable).append(" .\n");
        
        if (aLanguage != null) {
            // If a certain language is specified, we look exactly for that
            String escapedLang = NTriplesUtil.escapeString(aLanguage);
            fragment.append("    FILTER(LANGMATCHES(LANG(").append(filterVariable).append("), \"").append(escapedLang).append("\"))\n");
        }
        else {
            // If no language is specified, we look for statements without a language as otherwise
            // we might easily run into trouble on multi-lingual resources where we'd get all the
            // labels in all the languages being retrieved if we simply didn't apply any filter.
            fragment.append("    FILTER(LANG(").append(filterVariable).append(") = \"\")\n");
        }
        fragment.append(" }\n");
        return fragment.toString();
    }

    /**
     * adds an OPTIONAL block which looks for a value that is declared with a
     * subproperty of the given property
     */
    private static final String optionalLanguageFilteredSubPropertyValue(String aProperty,
        String aLanguage, String aVariable)
    {
        StringBuilder fragment = new StringBuilder();
        fragment.append("  OPTIONAL {\n");
        fragment.append("    ?s ").append("?p ").append(aVariable).append(" .\n");
        fragment.append("    ?p ").append("?pSUBPROPERTY ").append(aProperty).append(" .\n");
        fragment.append(languageFilterForVariable(aVariable, aLanguage));
        fragment.append(" }\n");
        return fragment.toString();
    }
  
    private static final String languageFilterForVariable(String aVariable, String aLanguage) {
        StringBuilder fragment = new StringBuilder();
        if (aLanguage != null) {
            // If a certain language is specified, we look exactly for that
            String escapedLang = NTriplesUtil.escapeString(aLanguage);
            fragment.append("    FILTER(LANGMATCHES(LANG(").append(aVariable).append("), \"").append(escapedLang).append("\"))\n");
        }
        else {
            // If no language is specified, we look for statements without a language as otherwise
            // we might easily run into trouble on multi-lingual resources where we'd get all the
            // labels in all the languages being retrieved if we simply didn't apply any filter.
            fragment.append("    FILTER(LANG(").append(aVariable).append(") = \"\")\n");
        }
        return fragment.toString();
    }

    /** 
     * Query to list all concepts from a knowledge base.
     */
    public static final String queryForAllConceptList(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX
                , "SELECT DISTINCT ?s ?l ?d WHERE { "
                , "  { ?s ?pTYPE ?oCLASS . } "
                , "  UNION { ?someSubClass ?pSUBCLASS ?s . } ."
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?l")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(), "?spl")
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?d")
                , "}"
                , "LIMIT " + aKB.getMaxResults());
    }
    
    /** 
     * Query to list all instances from a knowledge base.
     */
    public static final String listInstances(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX
                , "SELECT DISTINCT ?s ?l ?d ?spl WHERE {"
                , "  ?s ?pTYPE ?oPROPERTY ."
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?l")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(), "?spl")
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?d")
                , "}"
                , "LIMIT " + aKB.getMaxResults());
    }
    
    
    /** 
     * Query to list root concepts from a knowledge base.
     */
    public static final String listRootConcepts(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX    
                , "SELECT DISTINCT ?s (MIN(?label) AS ?l) (MIN(?desc) AS ?d) (MIN(?subproplabel) AS ?spl) WHERE { "
                , "  { ?s ?pTYPE ?oCLASS . } "
                , "  UNION { ?someSubClass ?pSUBCLASS ?s . } ."
                , "  FILTER NOT EXISTS { "
                , "    ?s ?pSUBCLASS ?otherSub . "
                , "    FILTER (?s != ?otherSub) }"
                , "  FILTER NOT EXISTS { "
                , "    ?s owl:intersectionOf ?list . }"
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?label")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(),
                        "?subproplabel")                          
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?desc")
                , "} GROUP BY ?s"
                , "LIMIT " + aKB.getMaxResults());
    }
    
    /** 
     * Query to list child concepts from a knowledge base.
     */
    public static final String listChildConcepts(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX    
                , "SELECT DISTINCT ?s (MIN(?label) AS ?l) (MIN(?desc) AS ?d) (MIN(?subproplabel) AS ?spl) WHERE { "
                , "  {?s ?pSUBCLASS ?oPARENT . }" 
                , "  UNION { ?s ?pTYPE ?oCLASS ."
                , "    ?s owl:intersectionOf ?list . "
                , "    FILTER EXISTS { ?list rdf:rest*/rdf:first ?oPARENT} }"
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?label")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(),
                        "?subproplabel")                 
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?desc")
                , "} GROUP BY ?s"
                , "LIMIT " + aKB.getMaxResults());
    }
    
    /** 
     * Query to read concept from a knowledge base.
     */
    public static final String readConcept(KnowledgeBase aKB, int limit)
    {
        return String.join("\n"
                , SPARQL_PREFIX    
                , "SELECT ?oItem ((?label) AS ?l) ((?desc) AS ?d) WHERE { "
                , "  { ?oItem ?pTYPE ?oCLASS . } "
                , "  UNION {?someSubClass ?pSUBCLASS ?oItem . } "
                , "  UNION {?oItem ?pSUBCLASS ?oPARENT . }" 
                , "  UNION {?oItem ?pTYPE ?oCLASS ."
                , "    ?oItem owl:intersectionOf ?list . "
                , "    FILTER EXISTS { ?list rdf:rest*/rdf:first ?oPARENT} }"
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?oItem","?label")
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?oItem","?desc")
                , "} "
                , "LIMIT " + limit);
    }
    
    
    /** 
     * Query to list properties from a knowledge base.
     */
    public static final String queryForPropertyList(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX
                , "SELECT DISTINCT ?s ?l ?d ?spl WHERE {"
                , "  { ?s ?pTYPE ?oPROPERTY .}"
                , "  UNION "
                , "  { ?s a ?prop" 
                , "    VALUES ?prop { rdf:Property owl:ObjectProperty owl:DatatypeProperty owl:AnnotationProperty }"
                , "  }"
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?l")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(),
                        "?spl")                           
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?d")
                , "}"
                , "LIMIT " + aKB.getMaxResults());
    }
        
    /**
     * Query to get property specific domain elements including properties which do not have a 
     * domain specified.
     */
    public static final String queryForPropertyListWithDomain(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX
                , "SELECT DISTINCT ?s ?l ?d ?spl WHERE {"
                , "{  ?s rdfs:domain/(owl:unionOf/rdf:rest*/rdf:first)* ?aDomain }"
                , " UNION "
                , "{ ?s a ?prop "
                , "    VALUES ?prop { rdf:Property owl:ObjectProperty owl:DatatypeProperty owl:AnnotationProperty} "
                , "    FILTER NOT EXISTS {  ?s rdfs:domain/(owl:unionOf/rdf:rest*/rdf:first)* ?x } }"
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?l")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(), "?spl")
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?d")
                , "}"
                , "LIMIT " + aKB.getMaxResults());
    }
    
    /**
     * Query to get property specific range elements
     */
    public static final String queryForPropertySpecificRange(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX
                , "SELECT DISTINCT ?s ?l ?d ?spl WHERE {"
                , "  ?aProperty rdfs:range/(owl:unionOf/rdf:rest*/rdf:first)* ?s "
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?l")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(),
                        "?spl")                           
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?d")
                , "}"
                , "LIMIT " + aKB.getMaxResults());

    }
    
    /**
     *  Query to retrieve super class concept for a concept
     */
    public static final String queryForParentConcept(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX
                , "SELECT DISTINCT ?s ?l ?d ?spl WHERE { "
                , "   {?oChild ?pSUBCLASS ?s . }"
                , "   UNION { ?s ?pTYPE ?oCLASS ."
                , "     ?oChild owl:intersectionOf ?list . "
                , "     FILTER EXISTS {?list rdf:rest*/rdf:first ?s. } }"
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?l")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(),
                        "?spl")                           
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?d")
                , "}");

    }
    
    /**
     *  Query to retrieve concept for an instance
     */
    public static final String queryForConceptForInstance(KnowledgeBase aKB)
    {
        return String.join("\n"
                , SPARQL_PREFIX
                , "SELECT DISTINCT ?s ?l ?d ?spl WHERE {"
                , "  ?pInstance ?pTYPE ?s ."
                , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?l")
                , optionalLanguageFilteredSubPropertyValue("?pLABEL", aKB.getDefaultLanguage(),
                        "?spl")                           
                , optionalLanguageFilteredValue("?pDESCRIPTION", aKB.getDefaultLanguage(),"?s","?d")
                , "}");

    }

}
