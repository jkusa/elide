/*
 * Copyright 2015, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.jsonapi.document.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.elide.jsonapi.models.Data;
import com.yahoo.elide.jsonapi.models.JsonApiDocument;
import com.yahoo.elide.jsonapi.models.Relationship;
import com.yahoo.elide.jsonapi.models.Resource;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class SparseFieldsetProcessorTest {
    private final SparseFieldsetProcessor sparseFieldsetProcessor = new SparseFieldsetProcessor();;
    private final ObjectMapper mapper = new ObjectMapper();

    private Resource book1;
    private Resource book2;
    private Resource book3;
    private Resource author1;
    private Resource author2;

    private List<Resource> books = new ArrayList<>();
    private List<Resource> authors = new ArrayList<>();

    @BeforeMethod
    public void setUp() throws Exception {
        book1 = new Resource("book", "1");
        Map<String, Object> book1atts = new LinkedHashMap<>();
        book1.setAttributes(book1atts);
        book1atts.put("genre", "Literary Fiction");
        book1atts.put("language", "English");
        book1atts.put("title", "The Old Man and the Sea");
        Map<String, Relationship> book1rels = new LinkedHashMap<>();
        book1.setRelationships(book1rels);
        book1rels.put("authors", new Relationship(null, new Data<>(Collections.singleton(
                new Resource("author", "1")))));
        books.add(book1);


        book2 = new Resource("book", "2");
        Map<String, Object> book2atts = new LinkedHashMap<>();
        book2.setAttributes(book2atts);
        book2atts.put("genre", "Literary Fiction");
        book2atts.put("language", "English");
        book2atts.put("title", "For Whom the Bell Tolls");
        Map<String, Relationship> book2rels = new LinkedHashMap<>();
        book2.setRelationships(book2rels);
        book2rels.put("authors", new Relationship(null, new Data<>(Collections.singleton(
                new Resource("author", "1")))));
        books.add(book2);


        book3 = new Resource("book", "3");
        Map<String, Object> book3atts = new LinkedHashMap<>();
        book3.setAttributes(book3atts);
        book3atts.put("genre", "Science Fiction");
        book3atts.put("language", "English");
        book3atts.put("title", "Ender's Game");
        Map<String, Relationship> book3rels = new LinkedHashMap<>();
        book3.setRelationships(book3rels);
        book3rels.put("authors", new Relationship(null, new Data<>(Collections.singleton(
                new Resource("author", "2")))));
        books.add(book3);


        author1 = new Resource("author", "1");
        Map<String, Object> author1atts = new LinkedHashMap<>();
        author1.setAttributes(author1atts);
        author1atts.put("name", "Ernest Hemingway");
        Map<String, Relationship> author1rels = new LinkedHashMap<>();
        author1.setRelationships(author1rels);
        author1rels.put("books",
                new Relationship(null, new Data<>(Arrays.asList(
                        new Resource("book", "1"), new Resource("book", "2")))));
        authors.add(author1);


        author2 = new Resource("author", "2");
        Map<String, Object> author2atts = new LinkedHashMap<>();
        author2.setAttributes(author2atts);
        author2atts.put("name", "Orson Scott Card");
        Map<String, Relationship> author2rels = new LinkedHashMap<>();
        author2.setRelationships(author2rels);
        author2rels.put("books",
                new Relationship(null, new Data<>(Collections.singleton(
                        new Resource("book", "3")))));
        authors.add(author2);
    }

    @Test
    public void testSparseSingleDataFieldValue() throws Exception {
        JsonApiDocument jsonApiDocument = new JsonApiDocument();

        jsonApiDocument.setData(new Data<>(Arrays.asList(book1, book2, book3)));
        jsonApiDocument.addIncluded(author1);
        jsonApiDocument.addIncluded(author2);

        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.put("fields[book]", Collections.singletonList("title"));

        sparseFieldsetProcessor.execute(jsonApiDocument, Collections.emptySet(), Optional.of(queryParams));

        JsonNode responseBody = mapper.convertValue(jsonApiDocument, JsonNode.class);

        Assert.assertTrue(responseBody.has("data"));

        for (JsonNode bookNode : responseBody.get("data")) {
            Assert.assertTrue(bookNode.has("attributes"));
            Assert.assertFalse(bookNode.has("relationships"));

            JsonNode attributes = bookNode.get("attributes");
            Assert.assertEquals(attributes.size(), 1);
            Assert.assertTrue(attributes.has("title"));
        }

        Assert.assertTrue(responseBody.has("included"));

        for (JsonNode include : responseBody.get("included")) {
            Assert.assertFalse(include.has("attributes"));
            Assert.assertFalse(include.has("relationships"));
        }
    }

    @Test
    public void testSparseTwoDataFieldValuesNoIncludes() throws Exception {
        JsonApiDocument jsonApiDocument = new JsonApiDocument();

        jsonApiDocument.setData(new Data<>(Arrays.asList(book1, book2, book3)));

        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.put("fields[book]", Arrays.asList("title", "language"));

        sparseFieldsetProcessor.execute(jsonApiDocument, Collections.emptySet(), Optional.of(queryParams));

        JsonNode responseBody = mapper.convertValue(jsonApiDocument, JsonNode.class);

        Assert.assertTrue(responseBody.has("data"));

        for (JsonNode bookNode : responseBody.get("data")) {
            Assert.assertTrue(bookNode.has("attributes"));
            Assert.assertFalse(bookNode.has("relationships"));

            JsonNode attributes = bookNode.get("attributes");
            Assert.assertEquals(attributes.size(), 2);
            Assert.assertTrue(attributes.has("title"));
            Assert.assertTrue(attributes.has("language"));
        }

        Assert.assertFalse(responseBody.has("included"));
    }

    @Test
    public void testSparseNoFilters() throws Exception {
        JsonApiDocument jsonApiDocument = new JsonApiDocument();

        jsonApiDocument.setData(new Data<>(Arrays.asList(book1, book2, book3)));
        jsonApiDocument.addIncluded(author1);
        jsonApiDocument.addIncluded(author2);

        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();

        sparseFieldsetProcessor.execute(jsonApiDocument, Collections.emptySet(), Optional.of(queryParams));

        JsonNode responseBody = mapper.convertValue(jsonApiDocument, JsonNode.class);

        Assert.assertTrue(responseBody.has("data"));

        for (JsonNode bookNode : responseBody.get("data")) {
            Assert.assertTrue(bookNode.has("attributes"));
            JsonNode attributes = bookNode.get("attributes");
            Assert.assertTrue(attributes.has("title"));
            Assert.assertTrue(attributes.has("language"));
            Assert.assertTrue(attributes.has("genre"));

            Assert.assertTrue(bookNode.has("relationships"));
            JsonNode relationships = bookNode.get("relationships");
            Assert.assertTrue(relationships.has("authors"));
        }

        Assert.assertTrue(responseBody.has("included"));

        for (JsonNode include : responseBody.get("included")) {
            Assert.assertTrue(include.has("attributes"));
            JsonNode attributes = include.get("attributes");
            Assert.assertTrue(attributes.has("name"));

            Assert.assertTrue(include.has("relationships"));
            JsonNode relationships = include.get("relationships");
            Assert.assertTrue(relationships.has("books"));
        }
    }


    @Test
    public void testTwoSparseFieldFilters() throws Exception {
        JsonApiDocument jsonApiDocument = new JsonApiDocument();

        jsonApiDocument.setData(new Data<>(Arrays.asList(book1, book2, book3)));
        jsonApiDocument.addIncluded(author1);
        jsonApiDocument.addIncluded(author2);

        MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.put("fields[book]", Arrays.asList("title", "genre", "authors"));
        queryParams.put("fields[author]", Collections.singletonList("name"));

        sparseFieldsetProcessor.execute(jsonApiDocument, Collections.emptySet(), Optional.of(queryParams));

        JsonNode responseBody = mapper.convertValue(jsonApiDocument, JsonNode.class);

        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(responseBody));

        Assert.assertTrue(responseBody.has("data"));

        for (JsonNode bookNode : responseBody.get("data")) {
            Assert.assertTrue(bookNode.has("attributes"));
            JsonNode attributes = bookNode.get("attributes");
            Assert.assertEquals(attributes.size(), 2);
            Assert.assertTrue(attributes.has("title"));
            Assert.assertTrue(attributes.has("genre"));

            Assert.assertTrue(bookNode.has("relationships"));
            JsonNode relationships = bookNode.get("relationships");
            Assert.assertTrue(relationships.has("authors"));
        }

        Assert.assertTrue(responseBody.has("included"));

        for (JsonNode include : responseBody.get("included")) {
            Assert.assertTrue(include.has("attributes"));
            JsonNode attributes = include.get("attributes");
            Assert.assertTrue(attributes.has("name"));

            Assert.assertFalse(include.has("relationships"));
        }
    }
}
