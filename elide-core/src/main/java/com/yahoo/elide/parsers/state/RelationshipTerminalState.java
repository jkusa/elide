/*
 * Copyright 2015, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.parsers.state;

import com.yahoo.elide.core.HttpStatus;
import com.yahoo.elide.core.PersistentResource;
import com.yahoo.elide.core.RelationshipType;
import com.yahoo.elide.core.RequestScope;
import com.yahoo.elide.core.exceptions.InvalidEntityBodyException;
import com.yahoo.elide.jsonapi.document.processors.DocumentProcessor;
import com.yahoo.elide.jsonapi.document.processors.IncludedProcessor;
import com.yahoo.elide.jsonapi.document.processors.SparseFieldsetProcessor;
import com.yahoo.elide.jsonapi.models.Data;
import com.yahoo.elide.jsonapi.models.JsonApiDocument;
import com.yahoo.elide.jsonapi.models.Relationship;
import com.yahoo.elide.jsonapi.models.Resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.ws.rs.core.MultivaluedMap;

/**
 * State to handle relationships.
 */
@Slf4j
public class RelationshipTerminalState extends BaseState {
    private final PersistentResource record;
    private final RelationshipType relationshipType;
    private final String relationshipName;

    public RelationshipTerminalState(PersistentResource record, String relationshipName) {
        this.record = record;

        this.relationshipType = record.getRelationshipType(relationshipName);
        this.relationshipName = relationshipName;
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handleGet(StateContext state) {
        JsonApiDocument doc = new JsonApiDocument();
        RequestScope requestScope = state.getRequestScope();
        ObjectMapper mapper = requestScope.getMapper().getObjectMapper();
        Optional<MultivaluedMap<String, String>> queryParams = requestScope.getQueryParams();

        Map<String, Relationship> relationships = record.toResource().getRelationships();
        Relationship relationship = null;
        if (relationships != null) {
            relationship = relationships.get(relationshipName);
        }

        // Handle valid relationship
        if (relationship != null) {

            // Set data
            Data<Resource> data = relationship.getData();
            doc.setData(data);

            // Run include processor
            DocumentProcessor includedProcessor = new IncludedProcessor();
            includedProcessor.execute(doc, record, queryParams);

            DocumentProcessor sparseFieldsetProcessor = new SparseFieldsetProcessor();
            sparseFieldsetProcessor.execute(doc, record, queryParams);

            return () -> Pair.of(HttpStatus.SC_OK, mapper.convertValue(doc, JsonNode.class));
        }

        // Handle no data for relationship
        if (relationshipType.isToMany()) {
            doc.setData(new Data<>(new ArrayList<>()));
        } else if (relationshipType.isToOne()) {
            doc.setData(new Data<>((Resource) null));
        } else {
            throw new IllegalStateException("Failed to PATCH a relationship");
        }
        return () -> Pair.of(HttpStatus.SC_OK, mapper.convertValue(doc, JsonNode.class));
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handlePatch(StateContext state) {
        return handleRequest(state, this::patch);
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handlePost(StateContext state) {
        return handleRequest(state, this::post);
    }

    @Override
    public Supplier<Pair<Integer, JsonNode>> handleDelete(StateContext state) {
        return handleRequest(state, this::delete);
    }

    private Supplier<Pair<Integer, JsonNode>> handleRequest(StateContext state,
                                                           BiFunction<Data<Resource>, RequestScope, Boolean> handler) {
        Data<Resource> data = state.getJsonApiDocument().getData();
        if (data == null) {
            throw new InvalidEntityBodyException("Expected data but received null");
        }
        handler.apply(data, state.getRequestScope());
        return () -> Pair.of(HttpStatus.SC_NO_CONTENT, null);
    }

    private boolean patch(Data<Resource> data, RequestScope requestScope) {
        boolean isUpdated = false;

        if (relationshipType.isToMany()) {
            Collection<Resource> resources = data.get();
            if (resources == null) {
                return false;
            }
            if (!resources.isEmpty()) {
                isUpdated = record.updateRelation(relationshipName,
                        new Relationship(null, new Data<>(resources)).toPersistentResources(requestScope));
            } else {
                isUpdated = record.clearRelation(relationshipName);
            }
        } else if (relationshipType.isToOne()) {
            Resource resource = data.get().iterator().next();
            if (resource != null) {
                isUpdated = record.updateRelation(
                    relationshipName,
                    new Relationship(null, new Data<>(resource)).toPersistentResources(requestScope));
            } else {
                isUpdated = record.clearRelation(relationshipName);
            }
        } else {
            throw new IllegalStateException("Bad relationship type");
        }

        return isUpdated;
    }

    private boolean post(Data<Resource> data, RequestScope requestScope) {
        Collection<Resource> resources = data.get();
        if (resources == null) {
            return false;
        }
        resources.stream().forEachOrdered(resource ->
            record.addRelation(relationshipName, resource.toPersistentResource(requestScope)));
        return true;
    }

    private boolean delete(Data<Resource> data, RequestScope requestScope) {
        Collection<Resource> resources = data.get();
        if (resources == null) {
            return false;
        }
        resources.stream().forEachOrdered(resource ->
            record.removeRelation(relationshipName, resource.toPersistentResource(requestScope)));
        return true;
    }
}
