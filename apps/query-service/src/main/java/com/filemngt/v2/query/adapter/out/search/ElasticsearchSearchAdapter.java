package com.filemngt.v2.query.adapter.out.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import com.filemngt.v2.query.domain.Region;
import com.filemngt.v2.query.domain.SubjectType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchSearchAdapter {
    static final String ALIAS = "media-subject-search";

    private final ElasticsearchClient client;

    public ElasticsearchSearchAdapter(ElasticsearchClient client) {
        this.client = client;
    }

    public void index(QuerySubjectEntity subject) throws IOException {
        ensureAlias();
        bulkIndex(ALIAS, List.of(subject));
        client.indices().refresh(request -> request.index(ALIAS));
    }

    public void indexAll(List<QuerySubjectEntity> subjects) throws IOException {
        if (subjects.isEmpty()) return;
        ensureAlias();
        indexAll(ALIAS, subjects);
    }

    public SearchResult search(String search, Region region, SubjectType type, String order, int page, int size)
            throws IOException {
        var filters = filters(region, type);
        var response = client.search(
                request -> request.index(ALIAS)
                        .from(page * size)
                        .size(size)
                        .trackTotalHits(track -> track.enabled(true))
                        .query(query ->
                                query.bool(bool -> bool.must(match -> match.multiMatch(multi -> multi.query(search)
                                                .fields("identityKey^3", "displayTitle")
                                                .fuzziness("AUTO")))
                                        .filter(filters)))
                        .sort(sort(order)),
                SearchDocument.class);
        var ids = response.hits().hits().stream()
                .map(hit -> UUID.fromString(hit.id()))
                .toList();
        var total = response.hits().total() == null
                ? ids.size()
                : response.hits().total().value();
        return new SearchResult(ids, total);
    }

    public List<String> suggest(String query, Region region, SubjectType type, int size) throws IOException {
        var filters = filters(region, type);
        var response = client.search(
                request -> request.index(ALIAS)
                        .size(size)
                        .query(search ->
                                search.bool(bool -> bool.must(match -> match.multiMatch(multi -> multi.query(query)
                                                .fields("identityKey^3", "displayTitle")
                                                .type(
                                                        co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
                                                                .BoolPrefix)))
                                        .filter(filters))),
                SearchDocument.class);
        return response.hits().hits().stream()
                .map(hit -> hit.source().identityKey())
                .distinct()
                .toList();
    }

    public String createCandidateIndex() throws IOException {
        var index = "media-subject-v1-" + System.currentTimeMillis();
        client.indices().create(request -> request.index(index)
                .mappings(mapping -> mapping.properties("region", property -> property.keyword(keyword -> keyword))
                        .properties("subjectType", property -> property.keyword(keyword -> keyword))
                        .properties(
                                "identityKey",
                                property -> property.text(text -> text.indexPrefixes(
                                                prefix -> prefix.minChars(2).maxChars(10))
                                        .fields("keyword", field -> field.keyword(keyword -> keyword))))
                        .properties(
                                "displayTitle",
                                property -> property.text(text -> text.indexPrefixes(
                                                prefix -> prefix.minChars(2).maxChars(10))
                                        .fields("keyword", field -> field.keyword(keyword -> keyword))))
                        .properties("createdAt", property -> property.date(date -> date))
                        .properties("projectedAt", property -> property.date(date -> date))));
        return index;
    }

    public void indexAll(String index, List<QuerySubjectEntity> subjects) throws IOException {
        if (subjects.isEmpty()) return;
        bulkIndex(index, subjects);
        client.indices().refresh(request -> request.index(index));
    }

    public void activate(String index) throws IOException {
        if (client.indices().existsAlias(request -> request.name(ALIAS)).value()) {
            client.indices().updateAliases(request -> request.actions(
                            action -> action.remove(remove -> remove.index("*").alias(ALIAS)))
                    .actions(action ->
                            action.add(add -> add.index(index).alias(ALIAS).isWriteIndex(true))));
        } else {
            client.indices()
                    .putAlias(request -> request.index(index).name(ALIAS).isWriteIndex(true));
        }
    }

    public void deleteIndex(String index) throws IOException {
        client.indices().delete(request -> request.index(index));
    }

    private synchronized void ensureAlias() throws IOException {
        if (client.indices().existsAlias(request -> request.name(ALIAS)).value()) return;
        var index = createCandidateIndex();
        try {
            activate(index);
        } catch (IOException | RuntimeException exception) {
            try {
                deleteIndex(index);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    private List<Query> filters(Region region, SubjectType type) {
        var filters = new ArrayList<Query>();
        if (region != null)
            filters.add(
                    Query.of(query -> query.term(term -> term.field("region").value(region.name()))));
        if (type != null)
            filters.add(Query.of(
                    query -> query.term(term -> term.field("subjectType").value(type.name()))));
        return filters;
    }

    private void bulkIndex(String index, List<QuerySubjectEntity> subjects) throws IOException {
        if (subjects.isEmpty()) return;
        var request = new BulkRequest.Builder();
        for (var subject : subjects) {
            request.operations(operation -> operation.index(indexOperation -> indexOperation
                    .index(index)
                    .id(subject.id().toString())
                    .document(SearchDocument.from(subject))
                    .version(subject.projectionVersion() + 1)
                    .versionType(VersionType.ExternalGte)));
        }
        var response = client.bulk(request.build());
        if (!response.errors()) return;
        var failures = response.items().stream()
                .filter(item -> item.error() != null)
                .map(item -> item.id() + ": " + item.error().reason())
                .limit(10)
                .toList();
        throw new IOException("Elasticsearch bulk indexing failed: " + String.join("; ", failures));
    }

    private co.elastic.clients.elasticsearch._types.SortOptions sort(String order) {
        return switch (order) {
            case "CREATED_AT" ->
                co.elastic.clients.elasticsearch._types.SortOptions.of(
                        sort -> sort.field(field -> field.field("createdAt").order(SortOrder.Desc)));
            case "TITLE" ->
                co.elastic.clients.elasticsearch._types.SortOptions.of(sort ->
                        sort.field(field -> field.field("displayTitle.keyword").order(SortOrder.Asc)));
            default ->
                co.elastic.clients.elasticsearch._types.SortOptions.of(
                        sort -> sort.score(score -> score.order(SortOrder.Desc)));
        };
    }
}
