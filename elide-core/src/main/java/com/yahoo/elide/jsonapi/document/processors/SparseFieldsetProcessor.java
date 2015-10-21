/*
 * Copyright 2015, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.jsonapi.document.processors;

import com.yahoo.elide.core.PersistentResource;
import com.yahoo.elide.jsonapi.models.JsonApiDocument;
import com.yahoo.elide.jsonapi.models.Relationship;
import com.yahoo.elide.jsonapi.models.Resource;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation for JSON API 'fields' query param.
 * Includes only the fields requested by the arguments to fields.
 */
public class SparseFieldsetProcessor implements DocumentProcessor {
    private static Map<String, Set<String>> parseSparseFields(MultivaluedMap<String, String> queryParams) {
        Map<String, Set<String>> result = new HashMap<>();

        for (String key : queryParams.keySet()) {
            if (key.startsWith("fields[") && key.endsWith("]")) {
                String type = key.substring(7, key.length() - 1);

                LinkedHashSet<String> filters = new LinkedHashSet<>();
                for (String filterParams : queryParams.get(key)) {
                    Collections.addAll(filters, filterParams.split(","));
                }

                if (filters.size() > 0) {
                    result.put(type, filters);
                }
            }
        }

        return result;
    }

    private void filterResource(Resource resource, Map<String, Set<String>> sparseFields) {
        Set<String> includeFields = sparseFields.containsKey(resource.getType())
                ? sparseFields.get(resource.getType()) : Collections.emptySet();

        // Filter fields from "attributes"
        final Iterator<Map.Entry<String, Object>> attributeIterator =
                resource.getAttributes().entrySet().iterator();
        while (attributeIterator.hasNext()) {
            String attributeField = attributeIterator.next().getKey();
            if (!includeFields.contains(attributeField)) {
                attributeIterator.remove();
            }
        }

        // Filter fields from "relationships"
        final Iterator<Map.Entry<String, Relationship>> relationshipIterator =
                resource.getRelationships().entrySet().iterator();
        while (relationshipIterator.hasNext()) {
            String relationshipField = relationshipIterator.next().getKey();
            if (!includeFields.contains(relationshipField)) {
                relationshipIterator.remove();
            }
        }

    }

    @Override
    public void execute(JsonApiDocument jsonApiDocument, PersistentResource resource,
                        Optional<MultivaluedMap<String, String>> queryParams) {
        execute(jsonApiDocument, Collections.singleton(resource), queryParams);
    }

    @Override
    public void execute(JsonApiDocument jsonApiDocument, Set<PersistentResource> resources,
                        Optional<MultivaluedMap<String, String>> queryParams) {
        // Do nothing if we have no params
        if (!queryParams.isPresent()) {
            return;
        }

        Map<String, Set<String>> sparseFields = parseSparseFields(queryParams.get());

        // Do nothing if we have no sparse field specs
        if (sparseFields.size() == 0) {
            return;
        }

        // Filter fields from "data"
        if (jsonApiDocument.getData() != null) {
            for (Resource resource : jsonApiDocument.getData().get()) {
                filterResource(resource, sparseFields);
            }
        }

        // Filter fields from "included"
        if (jsonApiDocument.getIncluded() != null) {
            for (Resource resource : jsonApiDocument.getIncluded()) {
                filterResource(resource, sparseFields);
            }
        }
    }
}
