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
import com.yahoo.elide.core.exceptions.InvalidEntityBodyException;
import com.yahoo.elide.jsonapi.document.processors.DocumentProcessor;
import com.yahoo.elide.jsonapi.document.processors.IncludedProcessor;
import com.yahoo.elide.jsonapi.document.processors.SparseFieldsetProcessor;
import com.yahoo.elide.jsonapi.models.Data;
import com.yahoo.elide.jsonapi.models.JsonApiDocument;
import com.yahoo.elide.jsonapi.models.Relationship;
import com.yahoo.elide.jsonapi.models.Resource;
import com.yahoo.elide.jsonapi.models.SingleElementSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.ws.rs.core.MultivaluedMap;

/**
 * Record Found State.
 */
@Slf4j
@ToString
public class RecordTerminalState extends BaseState {
    private final PersistentResource record;
    private final String parentField;

    public RecordTerminalState(String parentField, PersistentResource record) {
        this.parentField = parentField;
        this.record = record;
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handleGet(StateContext state) {
        ObjectMapper mapper = state.getRequestScope().getMapper().getObjectMapper();
        return () -> Pair.of(HttpStatus.SC_OK, getResponseBody(record, state.getRequestScope(), mapper));
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handlePatch(StateContext state) {
        final JsonNode empty = JsonNodeFactory.instance.nullNode();
        JsonApiDocument jsonApiDocument = state.getJsonApiDocument();

        Data<Resource> data = jsonApiDocument.getData();

        if (data == null) {
            throw new InvalidEntityBodyException("Expected data but found null");
        }

        if (data.get() instanceof SingleElementSet) {
            Resource resource = data.get().iterator().next();
            if (record.matchesId(resource.getId())) {
                patch(data.get().iterator().next(), state.getRequestScope());
                return () -> Pair.of(HttpStatus.SC_NO_CONTENT, empty);
            }
        }
        throw new InvalidEntityBodyException("Expected single element but found list");
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handleDelete(StateContext state) {
        try {
            record.deleteResource(parentField);
            return () -> Pair.of(HttpStatus.SC_NO_CONTENT, null);
        } catch (ForbiddenAccessException e) {
            return () -> Pair.of(e.getStatus(), null);
        }
    }

    private JsonNode getResponseBody(PersistentResource rec, RequestScope requestScope, ObjectMapper mapper) {
        Optional<MultivaluedMap<String, String>> queryParams = requestScope.getQueryParams();
        JsonApiDocument jsonApiDocument = new JsonApiDocument();

        //TODO Make this a document processor
        Data<Resource> data = rec == null ? null : new Data<>(rec.toResource());
        jsonApiDocument.setData(data);

        //TODO Iterate over set of document processors
        DocumentProcessor includedProcessor = new IncludedProcessor();
        includedProcessor.execute(jsonApiDocument, rec, queryParams);

        DocumentProcessor sparseFieldsetProcessor = new SparseFieldsetProcessor();
        sparseFieldsetProcessor.execute(jsonApiDocument, rec, queryParams);

        return mapper.convertValue(jsonApiDocument, JsonNode.class);
    }

    private boolean patch(Resource resource, RequestScope requestScope) {
        boolean isUpdated = false;

        // Update attrs first
        Map<String, Object> attrs = resource.getAttributes();
        if (attrs != null) {
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                String key = entry.getKey();
                Object newVal = entry.getValue();
                isUpdated |= record.updateAttribute(key, newVal);
            }
        }

        // Relations next
        Map<String, Relationship> relationships = resource.getRelationships();
        if (relationships != null) {
            for (Map.Entry<String, Relationship> entry : relationships.entrySet()) {
                String key = entry.getKey();
                Relationship relationship = entry.getValue();
                Set<PersistentResource> resources = (relationship == null)
                                                    ? null
                                                    : relationship.toPersistentResources(requestScope);
                isUpdated |= record.updateRelation(key, resources);
            }
        }

        return isUpdated;
    }
}
