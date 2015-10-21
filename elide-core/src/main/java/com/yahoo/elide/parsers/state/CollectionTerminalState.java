/*
 * Copyright 2015, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.parsers.state;

import com.yahoo.elide.core.HttpStatus;
import com.yahoo.elide.core.PersistentResource;
import com.yahoo.elide.core.RequestScope;
import com.yahoo.elide.core.exceptions.ForbiddenAccessException;
import com.yahoo.elide.core.exceptions.InternalServerErrorException;
import com.yahoo.elide.core.exceptions.InvalidEntityBodyException;
import com.yahoo.elide.core.exceptions.InvalidObjectIdentifierException;
import com.yahoo.elide.jsonapi.JsonApiMapper;
import com.yahoo.elide.jsonapi.document.processors.DocumentProcessor;
import com.yahoo.elide.jsonapi.document.processors.IncludedProcessor;
import com.yahoo.elide.jsonapi.document.processors.SortProcessor;
import com.yahoo.elide.jsonapi.document.processors.SparseFieldsetProcessor;
import com.yahoo.elide.jsonapi.models.Data;
import com.yahoo.elide.jsonapi.models.JsonApiDocument;
import com.yahoo.elide.jsonapi.models.Relationship;
import com.yahoo.elide.jsonapi.models.Resource;
import com.yahoo.elide.security.User;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.ws.rs.core.MultivaluedMap;

/**
 * Collection State.
 */
@ToString
@Slf4j
public class CollectionTerminalState extends BaseState {
    private final Optional<PersistentResource> parent;
    private final Optional<String> relationName;
    private final Class<?> entityClass;
    private PersistentResource newObject;

    public CollectionTerminalState(Class<?> entityClass, Optional<PersistentResource> parent,
                                   Optional<String> relationName) {
        this.parent = parent;
        this.relationName = relationName;
        this.entityClass = entityClass;
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handleGet(StateContext state) {
        JsonApiDocument jsonApiDocument = new JsonApiDocument();
        RequestScope requestScope = state.getRequestScope();
        ObjectMapper mapper = requestScope.getMapper().getObjectMapper();
        Optional<MultivaluedMap<String, String>> queryParams = requestScope.getQueryParams();

        Set<PersistentResource> collection = getResourceCollection(requestScope);

        // Set data
        jsonApiDocument.setData(getData(requestScope, collection));

        // Run include processor
        DocumentProcessor includedProcessor = new IncludedProcessor();
        includedProcessor.execute(jsonApiDocument, collection, queryParams);

        DocumentProcessor sparseFieldsetProcessor = new SparseFieldsetProcessor();
        sparseFieldsetProcessor.execute(jsonApiDocument, collection, queryParams);

        DocumentProcessor sortProcessor = new SortProcessor();
        sortProcessor.execute(jsonApiDocument, collection, queryParams);

        JsonNode responseBody = mapper.convertValue(jsonApiDocument, JsonNode.class);
        return () -> Pair.of(HttpStatus.SC_OK, responseBody);
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handlePost(StateContext state) {
        RequestScope requestScope = state.getRequestScope();
        JsonApiMapper mapper = requestScope.getMapper();

        newObject = createObject(requestScope);
        if (parent.isPresent()) {
            parent.get().addRelation(relationName.get(), newObject);
        }
        requestScope.getTransaction().save(newObject.getObject());
        return () -> {
            JsonApiDocument returnDoc = new JsonApiDocument();
            returnDoc.setData(new Data(newObject.toResource()));
            JsonNode responseBody = mapper.getObjectMapper().convertValue(returnDoc, JsonNode.class);
            return Pair.of(HttpStatus.SC_CREATED, responseBody);
        };
    }

    private Set<PersistentResource> getResourceCollection(RequestScope requestScope) {
        final Set<PersistentResource> collection;

        if (parent.isPresent()) {
            collection = parent.get().getRelation(relationName.get());
        } else {
            collection = (Set) PersistentResource.loadRecords(entityClass, requestScope);
        }

        return collection;
    }

    private Data getData(RequestScope requestScope, Set<PersistentResource> collection) {
        User user = requestScope.getUser();
        Preconditions.checkNotNull(collection);
        Preconditions.checkNotNull(user);

        List<Resource> resources = new ArrayList<>();
        for (PersistentResource r : collection) {
            resources.add(r.toResource());
        }
        return new Data<>(resources);
    }

    private PersistentResource createObject(RequestScope requestScope)
        throws ForbiddenAccessException, InvalidObjectIdentifierException {
        JsonApiDocument doc = requestScope.getJsonApiDocument();
        JsonApiMapper mapper = requestScope.getMapper();

        Data<Resource> data = doc.getData();
        Collection<Resource> resources = data.get();

        Resource resource = (resources.size() == 1) ? resources.iterator().next() : null;
        if (resource == null) {
            try {
                throw new InvalidEntityBodyException(mapper.writeJsonApiDocument(doc));
            } catch (JsonProcessingException e) {
                throw new InternalServerErrorException(e);
            }
        }

        String id = resource.getId();
        if (id == null || id.isEmpty()) {
            throw new ForbiddenAccessException();
        }

        PersistentResource pResource;
        if (parent.isPresent()) {
            pResource = PersistentResource.createObject(parent.get(), entityClass, requestScope, id);
        } else {
            pResource = PersistentResource.createObject(entityClass, requestScope, id);
        }

        Map<String, Object> attrs = resource.getAttributes();
        if (attrs != null) {
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                pResource.updateAttribute(key, val);
            }
        }

        Map<String, Relationship> relationships = resource.getRelationships();
        if (relationships != null) {
            for (Map.Entry<String, Relationship> entry : relationships.entrySet()) {
                String fieldName = entry.getKey();
                Relationship relationship = entry.getValue();
                Set<PersistentResource> resourceSet = (relationship == null)
                                                    ? null
                                                    : relationship.toPersistentResources(requestScope);
                pResource.updateRelation(fieldName, resourceSet);
            }
        }

        return pResource;
    }
}
