/*
 * Copyright 2015, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.tests;

import static com.yahoo.elide.contrib.testhelpers.jsonapi.JsonApiDSL.*;
import static com.yahoo.elide.contrib.testhelpers.jsonapi.elements.PatchOperationType.add;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yahoo.elide.initialization.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Tests for pagination
 */
public class PaginateIT extends IntegrationTest {

    private String asimovId = null;
    private String hemingwayId = null;
    private String orsonCardId = null;

    private String parentId = null;

    private List<String> bookIds;
    private List<String> authorIds;
    private List<String> childIds;

    private int entityWithoutPaginateCreateCount = 20;
    private int entityWithPaginateCountableCreateCount = 5;
    private int entityWithPaginateDefaultLimitCreateCount = 5;
    private int entityWithPaginateMaxLimitCreateCount = 30;

    @BeforeEach
    void setup() throws IOException {
        createFamilyEntities();
        createBookEntities();
        createPaginationEntities();
    }

    private void createPaginationEntities() {
        BiConsumer<String, Integer> createEntities = (type, numberOfEntities) -> {
            IntStream.range(0, numberOfEntities).forEach(value -> {
                given()
                        .contentType("application/vnd.api+json")
                        .accept("application/vnd.api+json")
                        .body(
                                datum(
                                        resource(
                                                type(type),
                                                attributes(
                                                        attr("name", "A name")
                                                )
                                        )
                                ).toJSON()
                        ).post("/" + type)
                        .then()
                        .statusCode(CREATED_201);
            });
            get("/" + type).path("data.id");
        };

        createEntities.accept("entityWithoutPaginate", entityWithoutPaginateCreateCount);
        createEntities.accept("entityWithPaginateCountableFalse", entityWithPaginateCountableCreateCount);
        createEntities.accept("entityWithPaginateDefaultLimit", entityWithPaginateDefaultLimitCreateCount);
        createEntities.accept("entityWithPaginateMaxLimit", entityWithPaginateMaxLimitCreateCount);
    }

    private void createBookEntities() {
        String tempAuthorId1 = "12345678-1234-1234-1234-1234567890ab";
        String tempAuthorId2 = "12345679-1234-1234-1234-1234567890ab";
        String tempAuthorId3 = "12345681-1234-1234-1234-1234567890ab";

        String tempBookId1 = "12345678-1234-1234-1234-1234567890ac";
        String tempBookId2 = "12345678-1234-1234-1234-1234567890ad";
        String tempBookId3 = "12345679-1234-1234-1234-1234567890ac";
        String tempBookId4 = "23451234-1234-1234-1234-1234567890ac";
        String tempBookId5 = "12345680-1234-1234-1234-1234567890ac";
        String tempBookId6 = "12345680-1234-1234-1234-1234567890ad";
        String tempBookId7 = "12345681-1234-1234-1234-1234567890ac";
        String tempBookId8 = "12345681-1234-1234-1234-1234567890ad";

        String tempPubId = "12345678-1234-1234-1234-1234567890ae";

        given()
                .contentType("application/vnd.api+json; ext=jsonpatch")
                .accept("application/vnd.api+json; ext=jsonpatch")
                .body(
                        patchSet(
                                patchOperation(add, "/author", resource(
                                        type("author"),
                                        id(tempAuthorId1),
                                        attributes(
                                                attr("name", "Ernest Hemingway")
                                        ),
                                        relationships(
                                                relation("books",
                                                        linkage(type("book"), id(tempBookId1)),
                                                        linkage(type("book"), id(tempBookId2))
                                                )
                                        )
                                )),
                                patchOperation(add, "/book/", resource(
                                        type("book"),
                                        id(tempBookId1),
                                        attributes(
                                                attr("title", "The Old Man and the Sea"),
                                                attr("genre", "Literary Fiction"),
                                                attr("language", "English")
                                        ),
                                        relationships(
                                                relation("publisher",
                                                        linkage(type("publisher"), id(tempPubId))

                                                )
                                        )
                                )),
                                patchOperation(add, "/book/", resource(
                                        type("book"),
                                        id(tempBookId2),
                                        attributes(
                                                attr("title", "For Whom the Bell Tolls"),
                                                attr("genre", "Literary Fiction"),
                                                attr("language", "English")
                                        )
                                )),
                                patchOperation(add, "/book/" + tempBookId1 + "/publisher", resource(
                                        type("publisher"),
                                        id(tempPubId),
                                        attributes(
                                                attr("name", "Default publisher")
                                        )
                                ))
                        ).toJSON()
                )
                .patch("/")
                .then()
                .statusCode(OK_200);

        given()
                .contentType("application/vnd.api+json; ext=jsonpatch")
                .accept("application/vnd.api+json; ext=jsonpatch")
                .body(
                        patchSet(
                                patchOperation(add, "/author", resource(
                                        type("author"),
                                        id(tempAuthorId2),
                                        attributes(
                                                attr("name", "Orson Scott Card")
                                        ),
                                        relationships(
                                                relation("books",
                                                        linkage(type("book"), id(tempBookId3)),
                                                        linkage(type("book"), id(tempBookId4))
                                                )
                                        )
                                )),
                                patchOperation(add, "/book", resource(
                                        type("book"),
                                        id(tempBookId3),
                                        attributes(
                                                attr("title", "Enders Game"),
                                                attr("genre", "Science Fiction"),
                                                attr("language", "English"),
                                                attr("publishDate", 1454638927412L)
                                        )
                                )),
                                patchOperation(add, "/book", resource(
                                        type("book"),
                                        id(tempBookId4),
                                        attributes(
                                                attr("title", "Enders Shadow"),
                                                attr("genre", "Science Fiction"),
                                                attr("language", "English"),
                                                attr("publishDate", 1464638927412L)
                                        )
                                ))
                        )
                )
                .patch("/")
                .then()
                .statusCode(OK_200);

        given()
                .contentType("application/vnd.api+json; ext=jsonpatch")
                .accept("application/vnd.api+json; ext=jsonpatch")
                .body(
                        patchSet(
                                patchOperation(add, "/author", resource(
                                        type("author"),
                                        id(tempAuthorId3),
                                        attributes(
                                                attr("name", "Isaac Asimov")
                                        ),
                                        relationships(
                                                relation("books",
                                                        linkage(type("book"), id(tempBookId5)),
                                                        linkage(type("book"), id(tempBookId6))
                                                )
                                        )
                                )),
                                patchOperation(add, "/book", resource(
                                        type("book"),
                                        id(tempBookId5),
                                        attributes(
                                                attr("title", "Foundation"),
                                                attr("genre", "Science Fiction"),
                                                attr("language", "English")
                                        )
                                )),
                                patchOperation(add, "/book", resource(
                                        type("book"),
                                        id(tempBookId6),
                                        attributes(
                                                attr("title", "The Roman Republic"),
                                                //genre null
                                                attr("language", "English")
                                        )
                                ))
                        )
                )
                .patch("/")
                .then()
                .statusCode(OK_200);

        given()
                .contentType("application/vnd.api+json; ext=jsonpatch")
                .accept("application/vnd.api+json; ext=jsonpatch")
                .body(
                        patchSet(
                                patchOperation(add, "/author", resource(
                                        type("author"),
                                        id(tempAuthorId3),
                                        attributes(
                                                attr("name", "Null Ned")
                                        ),
                                        relationships(
                                                relation("books",
                                                        linkage(type("book"), id(tempBookId7)),
                                                        linkage(type("book"), id(tempBookId8))
                                                )
                                        )
                                )),
                                patchOperation(add, "/book", resource(
                                        type("book"),
                                        id(tempBookId7),
                                        attributes(
                                                attr("title", "Life with Null Ned"),
                                                attr("language", "English")
                                        )
                                )),
                                patchOperation(add, "/book", resource(
                                        type("book"),
                                        id(tempBookId8),
                                        attributes(
                                                attr("title", "Life with Null Ned 2"),
                                                attr("genre", "Not Null"),
                                                attr("language", "English")
                                        )
                                ))
                        ).toJSON()
                )
                .patch("/")
                .then()
                .statusCode(OK_200);

        bookIds = get("/book").path("data.id");

        Response authors = get("/author").then().extract().response();
        Function<String, String> findAuthorId = name ->
                authors.path("data.find { it.attributes.name=='" + name + "' }.id");

        asimovId = findAuthorId.apply("Isaac Asimov");
        orsonCardId = findAuthorId.apply("Orson Scott Card");
        hemingwayId = findAuthorId.apply("Ernest Hemingway");
    }

    private void createFamilyEntities() {
        String tempParentId = "12345678-1234-1234-1234-1234567890ab";
        String tempChildId1 = "12345678-1234-1234-1234-1234567890ac";
        String tempChildId2 = "12345678-1234-1234-1234-1234567890ad";
        String tempSpouseId = "12345678-1234-1234-1234-1234567890af";

        given()
                .contentType("application/vnd.api+json; ext=jsonpatch")
                .accept("application/vnd.api+json; ext=jsonpatch")
                .body(
                        patchSet(
                                patchOperation(add, "/parent", resource(
                                        type("parent"),
                                        id(tempParentId),
                                        relationships(
                                                relation("children",
                                                        linkage(type("child"), id(tempChildId1)),
                                                        linkage(type("child"), id(tempChildId2))
                                                ),
                                                relation("spouses",
                                                        linkage(type("parent"), id(tempSpouseId))
                                                )
                                        )
                                )),
                                patchOperation(add, "/parent/" + tempParentId + "/children", resource(
                                        type("child"),
                                        id(tempChildId1)
                                )),
                                patchOperation(add, "/parent/" + tempParentId + "/children", resource(
                                        type("child"),
                                        id(tempChildId2)
                                )),
                                patchOperation(add, "/parent", resource(
                                        type("parent"),
                                        id(tempSpouseId)
                                ))
                        ).toJSON()
                )
                .patch("/")
                .then()
                .statusCode(OK_200);

        Response parents = get("/parent").then().extract().response();
        parentId = parents.path("data[0].id");
        childIds = parents.path("data[0].relationships.children.data.id");
    }

    @Test
    public void testNoFilterSortDescPaginationFirstPage() {
        String url = "/book?sort=-title&page[size]=3";
        List actual = get(url).path("data.attributes.title");
        List expected = Arrays.asList("The Roman Republic", "The Old Man and the Sea", "Life with Null Ned 2");
        assertEquals(expected, actual);
    }

    @Test
    public void testPaginationOnSubRecords() {
        String url = "/author/" + orsonCardId + "/books?sort=-title&page[size]=1";
        List actual = get(url).path("data.attributes.title");
        List expected = Arrays.asList("Enders Shadow");
        assertEquals(expected, actual);
    }

    @Test
    public void testNoFilterSortDescPagination() {
        String url = "/book?sort=-title&page[number]=2&page[size]=3";
        List actual = get(url).path("data.attributes.title");
        List expected = Arrays.asList("Life with Null Ned", "Foundation", "For Whom the Bell Tolls");
        assertEquals(expected, actual);
    }

    @Test
    public void testNoFilterMultiSortPagination() {
        //select * from book order by title desc, genre asc;
        String url = "/book?sort=-title,genre&page[size]=3";
        Response response = get(url).then().extract().response();

        List actualTitles = response.path("data.attributes.title");
        List expectedTitles = Arrays.asList("The Roman Republic", "The Old Man and the Sea", "Life with Null Ned 2");
        assertEquals(expectedTitles, actualTitles);

        List actualGenes = response.path("data.attributes.genre");
        List expectedGenres = Arrays.asList(null, "Literary Fiction", "Not Null");
        assertEquals(expectedGenres, actualGenes);
        //"The Roman Republic has a null genre and should be should be first.
    }

    @Test
    public void testPublishDateLessThanFilter() {
        String url = "/book?filter[book.publishDate][lt]=1454638927411&page[size]=2";
        Response response = get(url).then().extract().response();

        List allBooks = response.path("data.attributes");
        assertEquals(2, allBooks.size());

        List filteredBooks = response.path("data.findAll { it.attributes.publishDate < 1454638927411L }");
        assertEquals(allBooks.size(), filteredBooks.size());
    }

    @Test
    public void testPageAndSortOnSubRecords() {

        String url = "/author/" + orsonCardId + "/books?sort=-title,publishDate&page[size]=1";
        when()
                .get(url)
                .then()
                .body("data", hasSize(1),
                        "data[0].attributes.publishDate", equalTo(1464638927412L),
                        "data[0].relationships.authors.data.id", contains(orsonCardId)
                );
    }

    @Test
    public void testPageAndSortOnSubRecordsPageTwo() {
        String url = "/author/" + orsonCardId + "/books?sort=-title&page[number]=2&page[size]=1";
        when()
                .get(url)
                .then()
                .body("data", hasSize(1),
                        "data[0].attributes.title", equalTo("Enders Game"),
                        "data[0].relationships.authors.data.id", contains(orsonCardId),
                        "data[0].relationships.authors.data.id", contains(orsonCardId)
                );
    }

    @Test
    public void testPageAndSortShouldFailOnBadSortFields() {
        String url = "/author/" + orsonCardId + "/books?sort=-title,publishDate,onion&page[size]=1";
        when()
                .get(url)
                .then()
                .body("errors", hasSize(1),
                        "errors[0]",
                        equalTo("InvalidValueException: Invalid value: book doesn't contain the field onion"))
                .statusCode(BAD_REQUEST_400);

    }

    @Test
    public void testBasicPageBasedPagination() {
        String url = "/book?page[number]=2&page[size]=2";
        when()
                .get(url)
                .then()
                .body("data", hasSize(2));
    }

    @Test
    public void testBasicOffsetBasedPagination() {
        String url = "/book?page[offset]=3&page[limit]=2";
        when()
                .get(url)
                .then()
                .body("data", hasSize(2));
    }

    @Test
    public void testPaginationOffsetOnly() {
        String url = "/book?page[offset]=3";
        when()
                .get(url)
                .then()
                .body("data", hasSize(5));
    }

    @Test
    public void testPaginationSizeOnly() {
        String url = "/book?page[size]=2";
        when()
                .get(url)
                .then()
                .body("data", hasSize(2));
    }

    @Test()
    public void testPaginationOffsetWithSorting() {
        String url = "/book?sort=title&page[offset]=3";
        when()
                .get(url)
                .then()
                .body("data", hasSize(5),
                        "data[0].attributes.title", equalTo("Foundation")
                );
    }

    @Test
    public void testPaginateInvalidParameter() {
        String url = "/entityWithoutPaginate?page[bad]=2&page[totals]";
        when()
                .get(url)
                .then()
                .body("errors[0]", containsString("Invalid Pagination Parameter"))
                .statusCode(BAD_REQUEST_400);
    }

    @Test
    public void testPaginateAnnotationTotals() {
        String url = "/entityWithoutPaginate?page[size]=2&page[totals]";
        when()
                .get(url)
                .then()
                .body("data", hasSize(2),
                        "meta.page.totalRecords", equalTo(entityWithoutPaginateCreateCount),
                        "meta.page.totalPages", equalTo(entityWithoutPaginateCreateCount / 2)
                );
    }

    @Test
    public void testPaginateAnnotationTotalsWithFilter() {
        String url = "/entityWithoutPaginate?page[size]=2&page[totals]&filter[entityWithoutPaginate.id][le]=10";
        when()
                .get(url)
                .then()
                .body("data", hasSize(2),
                        "meta.page.totalRecords", equalTo(10),
                        "meta.page.totalPages", equalTo(5)
                );
    }

    @Test
    void testPaginateAnnotationTotalsWithToManyJoinFilter() {
        /* Test RSQL Global */
        String url = "/author?page[totals]&filter=books.title=in=('The Roman Republic','Foundation','Life With Null Ned')";
        when()
                .get(url)
                .then()
                .body("data", hasSize(2),
                        "data.attributes.name", contains("Isaac Asimov", "Null Ned"),
                        "meta.page.totalRecords", equalTo(2)
                );
    }

    @Test
    public void testRelationshipPaginateAnnotationTotals() {
        String url = "/author/" + asimovId + "/books?page[size]=1&page[totals]";
        when()
                .get(url)
                .then()
                .body("data", hasSize(1),
                        "meta.page.totalRecords", equalTo(2),
                        "meta.page.totalPages", equalTo(2)
                );
    }

    @Test
    public void testRelationshipPaginateAnnotationTotalsWithFilter() {
        String url = "/author/" + asimovId + "/books?page[size]=1&page[totals]&filter[book.title][infixi]=FounDation";
        when()
                .get(url)
                .then()
                .body("data", hasSize(1),
                        "meta.page.totalRecords", equalTo(1),
                        "meta.page.totalPages", equalTo(1)
                );
    }

    @Test
    public void testPageTotalsForSameTypedRelationship() {
        String url = "/parent/" + parentId + "/spouses?page[totals]";
        when()
                .get(url)
                .then()
                .body("data", hasSize(1),
                        "meta.page.totalRecords", equalTo(1),
                        "meta.page.totalPages.", equalTo(1)
                );
    }

    @Test
    public void testRelationshipPaginateAnnotationTotalsWithNestedFilter() {
        String url = "/author/" + hemingwayId + "/books?filter[book.publisher.name]=Default publisher&page[totals]";
        when()
                .get(url)
                .then()
                .body("data", hasSize(1),
                        "meta.page.totalRecords", equalTo(1),
                        "meta.page.totalPages.", equalTo(1)
                );
    }

    @Test
    public void testPaginateAnnotationPreventTotals() {
        String url = "/entityWithPaginateCountableFalse?page[size]=3&page[totals]";
        when()
                .get(url)
                .then()
                .body("data", hasSize(3),
                        "meta.page.totalRecords", nullValue(),
                        "meta.page.totalPages.", nullValue()
                );
    }

    @Test
    public void testPaginateAnnotationDefaultLimit() {
        String url = "/entityWithPaginateDefaultLimit?page[number]=1";
        when()
                .get(url)
                .then()
                .body("data", hasSize(5),
                        "meta.page.number", equalTo(1),
                        "meta.page.limit", equalTo(5)
                );
    }

    @Test
    public void testPaginateAnnotationMaxLimit() {
        String url = "/entityWithPaginateMaxLimit?page[limit]=100";
        when()
                .get(url)
                .then()
                .body("errors", hasSize(1),
                        "errors[0]", containsString("page[limit] value must be less than or equal to 10"))
                .statusCode(BAD_REQUEST_400);
    }

    @Test
    public void testPaginationNotPossibleAtRoot() {
        String url = "/child?page[size]=1";
        ;
        when()
                .get(url)
                .then()
                .body("errors", hasSize(1),
                        "errors[0]", containsString("InvalidPredicateException: Cannot paginate child")
                ).statusCode(BAD_REQUEST_400);
    }

    @Test
    public void testPaginationNotPossibleAtRelationship() {
        String url = "/parent/" + parentId + "/children?page[size]=1";
        ;
        when()
                .get(url)
                .then()
                .body("errors", hasSize(1),
                        "errors[0]", containsString("InvalidPredicateException: Cannot paginate child")
                );
    }
}
