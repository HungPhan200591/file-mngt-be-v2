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
    private final QueryAssetRepository assets;
    private final QuerySubjectRepository subjects;

    public QueryVideoGalleryService(QueryAssetRepository assets, QuerySubjectRepository subjects) {
        this.assets = assets;
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public Page<VideoCard> list(VideoFilter filter, Pageable pageable) {
        var idPage = assets.findVideoIds(
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
                .map(asset -> toCard(asset, subjectById.get(asset.subject().id())))
                .toList();
        return new PageImpl<>(cards, pageable, idPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<String> tags() {
        return assets.listVideoTags();
    }

    private VideoCard toCard(QueryAssetEntity video, QuerySubjectEntity subject) {
        var thumbnail = subject.assets().stream()
                .filter(asset -> asset.role() == MediaAssetRole.IMAGE || asset.role() == MediaAssetRole.GIF)
                .sorted(Comparator.comparingInt(this::thumbnailPriority).thenComparing(QueryAssetEntity::relativePath))
                .findFirst()
                .orElse(null);
        return new VideoCard(video, subject, thumbnail);
    }

    private int thumbnailPriority(QueryAssetEntity asset) {
        return asset.role() == MediaAssetRole.IMAGE ? 0 : 1;
    }

    public record VideoFilter(String rootKey, String studio, String actress, String tag, String search) {}

    public record VideoCard(QueryAssetEntity video, QuerySubjectEntity subject, QueryAssetEntity thumbnail) {}
}
