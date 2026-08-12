package com.filemngt.v2.query.application;

import com.filemngt.v2.query.adapter.out.persistence.QueryAssetEntity;
import com.filemngt.v2.query.adapter.out.persistence.QueryAssetRepository;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectEntity;
import com.filemngt.v2.query.adapter.out.persistence.QuerySubjectRepository;
import com.filemngt.v2.query.domain.MediaAssetRole;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryVideoGalleryService {
    private static final Comparator<QueryAssetEntity> PREVIEW_ORDER = Comparator.comparingInt(
                    QueryVideoGalleryService::previewPriority)
            .thenComparing(QueryAssetEntity::relativePath)
            .thenComparing(QueryAssetEntity::id);

    private final QueryAssetRepository assets;
    private final QuerySubjectRepository subjects;

    public QueryVideoGalleryService(QueryAssetRepository assets, QuerySubjectRepository subjects) {
        this.assets = assets;
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public Page<VideoCard> list(VideoFilter filter, Pageable pageable) {
        var idPage = assets.findGalleryCardIds(
                filter.rootKey(), filter.studio(), filter.actress(), filter.tag(), filter.search(), pageable);
        if (idPage.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);
        var assetById = assets.findAllWithTagsByIdIn(idPage.getContent()).stream()
                .collect(Collectors.toMap(QueryAssetEntity::id, Function.identity()));
        var subjectById = subjects
                .findAllWithAssetsByIdIn(assetById.values().stream()
                        .map(asset -> asset.subject().id())
                        .toList())
                .stream()
                .collect(Collectors.toMap(QuerySubjectEntity::id, Function.identity()));
        var cards = idPage.stream()
                .map(assetById::get)
                .map(representative -> toCard(
                        representative, subjectById.get(representative.subject().id())))
                .toList();
        return new PageImpl<>(cards, pageable, idPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<String> tags() {
        return assets.listVideoTags();
    }

    private VideoCard toCard(QueryAssetEntity representative, QuerySubjectEntity subject) {
        var previews = subject.assets().stream()
                .filter(QueryVideoGalleryService::isPreview)
                .sorted(PREVIEW_ORDER)
                .toList();
        var video = isVideo(representative) ? representative : null;
        var thumbnail = previews.isEmpty() ? null : previews.getFirst();
        return new VideoCard(representative, video, subject, thumbnail, previews);
    }

    private static boolean isVideo(QueryAssetEntity asset) {
        return asset.role() == MediaAssetRole.PRIMARY_VIDEO || asset.role() == MediaAssetRole.VIDEO;
    }

    private static boolean isPreview(QueryAssetEntity asset) {
        return asset.role() == MediaAssetRole.IMAGE || asset.role() == MediaAssetRole.GIF;
    }

    private static int previewPriority(QueryAssetEntity asset) {
        return asset.role() == MediaAssetRole.IMAGE ? 0 : 1;
    }

    public record VideoFilter(String rootKey, String studio, String actress, String tag, String search) {}

    public record VideoCard(
            QueryAssetEntity representative,
            QueryAssetEntity video,
            QuerySubjectEntity subject,
            QueryAssetEntity thumbnail,
            List<QueryAssetEntity> previews) {}
}
