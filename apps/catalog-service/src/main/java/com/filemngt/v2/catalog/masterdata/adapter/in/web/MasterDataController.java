package com.filemngt.v2.catalog.masterdata.adapter.in.web;

import com.filemngt.v2.catalog.masterdata.application.MasterDataActressService;
import com.filemngt.v2.catalog.masterdata.application.MasterDataImportService;
import com.filemngt.v2.catalog.masterdata.application.MasterDataImportService.StudioImportPayload;
import com.filemngt.v2.catalog.masterdata.application.MasterDataImportService.StudioImportRow;
import com.filemngt.v2.catalog.masterdata.application.MasterDataStudioService;
import com.filemngt.v2.catalog.masterdata.application.MasterDataTagService;
import com.filemngt.v2.catalog.masterdata.application.dto.ActressView;
import com.filemngt.v2.catalog.masterdata.application.dto.ImportReportView;
import com.filemngt.v2.catalog.masterdata.application.dto.MasterDataPageView;
import com.filemngt.v2.catalog.masterdata.application.dto.StudioCodeView;
import com.filemngt.v2.catalog.masterdata.application.dto.StudioView;
import com.filemngt.v2.catalog.masterdata.application.dto.TagView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/master-data")
public class MasterDataController {

    private final MasterDataStudioService studioService;
    private final MasterDataTagService tagService;
    private final MasterDataActressService actressService;
    private final MasterDataImportService importService;

    public MasterDataController(
            MasterDataStudioService studioService,
            MasterDataTagService tagService,
            MasterDataActressService actressService,
            MasterDataImportService importService) {
        this.studioService = studioService;
        this.tagService = tagService;
        this.actressService = actressService;
        this.importService = importService;
    }

    // ── Studios ────────────────────────────────────────────────────────────────

    @GetMapping("/studios")
    public MasterDataPageView<StudioView> listStudios(
            @RequestParam(required = false) @Pattern(regexp = "JOKE|USE") String region,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return studioService.list(region, name, active, validPage(page, size), size);
    }

    @PostMapping("/studios")
    @ResponseStatus(HttpStatus.CREATED)
    public StudioView createStudio(@Valid @RequestBody CreateStudioRequest request) {
        return studioService.create(request.region(), request.displayName());
    }

    @GetMapping("/studios/{studioId}")
    public StudioView getStudio(@PathVariable UUID studioId) {
        return studioService.get(studioId);
    }

    @PatchMapping("/studios/{studioId}")
    public StudioView updateStudio(@PathVariable UUID studioId, @Valid @RequestBody UpdateStudioRequest request) {
        return studioService.update(studioId, request.displayName());
    }

    @PostMapping("/studios/{studioId}/enable")
    public StudioView enableStudio(@PathVariable UUID studioId) {
        return studioService.setActive(studioId, true);
    }

    @PostMapping("/studios/{studioId}/disable")
    public StudioView disableStudio(@PathVariable UUID studioId) {
        return studioService.setActive(studioId, false);
    }

    // ── Studio Codes ──────────────────────────────────────────────────────────

    @GetMapping("/studios/{studioId}/codes")
    public List<StudioCodeView> listCodes(@PathVariable UUID studioId, @RequestParam(required = false) Boolean active) {
        return studioService.listCodes(studioId, active);
    }

    @PostMapping("/studios/{studioId}/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public StudioCodeView addCode(@PathVariable UUID studioId, @Valid @RequestBody AddCodeRequest request) {
        return studioService.addCode(studioId, request.rawCode());
    }

    @DeleteMapping("/studios/{studioId}/codes/{codeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCode(@PathVariable UUID studioId, @PathVariable UUID codeId) {
        studioService.removeCode(studioId, codeId);
    }

    @PostMapping("/studios/{studioId}/codes/{codeId}/enable")
    public StudioCodeView enableCode(@PathVariable UUID studioId, @PathVariable UUID codeId) {
        return studioService.setCodeActive(studioId, codeId, true);
    }

    @PostMapping("/studios/{studioId}/codes/{codeId}/disable")
    public StudioCodeView disableCode(@PathVariable UUID studioId, @PathVariable UUID codeId) {
        return studioService.setCodeActive(studioId, codeId, false);
    }

    // ── Studio Import ─────────────────────────────────────────────────────────

    @PostMapping("/imports/studios")
    public ImportReportView importStudios(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestBody Map<String, List<StudioImportRow>> payload) {
        return importService.importStudios(new StudioImportPayload(payload), dryRun);
    }

    @PostMapping("/imports/tags")
    public ImportReportView importTags(
            @RequestParam(defaultValue = "true") boolean dryRun, @RequestBody List<String> payload) {
        return importService.importTags(new MasterDataImportService.TagImportPayload(payload), dryRun);
    }

    @PostMapping("/imports/actresses")
    public ImportReportView importActresses(
            @RequestParam(defaultValue = "true") boolean dryRun, @RequestBody Map<String, List<String>> payload) {
        return importService.importActresses(new MasterDataImportService.ActressImportPayload(payload), dryRun);
    }

    // ── Tags ──────────────────────────────────────────────────────────────────

    @GetMapping("/tags")
    public MasterDataPageView<TagView> listTags(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return tagService.list(name, active, validPage(page, size), size);
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagView createTag(@Valid @RequestBody CreateTagRequest request) {
        return tagService.create(request.displayName());
    }

    @PostMapping("/tags/{tagId}/enable")
    public TagView enableTag(@PathVariable UUID tagId) {
        return tagService.setActive(tagId, true);
    }

    @PostMapping("/tags/{tagId}/disable")
    public TagView disableTag(@PathVariable UUID tagId) {
        return tagService.setActive(tagId, false);
    }

    // ── Actresses ────────────────────────────────────────────────────────────

    @GetMapping("/actresses")
    public MasterDataPageView<ActressView> listActresses(
            @RequestParam(required = false) @Pattern(regexp = "JOKE|USE") String region,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return actressService.list(region, name, active, validPage(page, size), size);
    }

    @PostMapping("/actresses")
    @ResponseStatus(HttpStatus.CREATED)
    public ActressView createActress(@Valid @RequestBody CreateActressRequest request) {
        return actressService.create(request.region(), request.displayName());
    }

    @PostMapping("/actresses/{actressId}/enable")
    public ActressView enableActress(@PathVariable UUID actressId) {
        return actressService.setActive(actressId, true);
    }

    @PostMapping("/actresses/{actressId}/disable")
    public ActressView disableActress(@PathVariable UUID actressId) {
        return actressService.setActive(actressId, false);
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private int validPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidMasterDataRequestException("page >= 0 and 1 <= size <= 100");
        }
        return page;
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record CreateStudioRequest(
            @NotBlank @Pattern(regexp = "JOKE|USE") String region,
            @NotBlank @Size(max = 255) String displayName) {}

    public record UpdateStudioRequest(
            @NotBlank @Size(max = 255) String displayName) {}

    public record AddCodeRequest(@NotBlank @Size(max = 100) String rawCode) {}

    public record CreateTagRequest(
            @NotBlank @Size(max = 255) String displayName) {}

    public record CreateActressRequest(
            @NotBlank @Pattern(regexp = "JOKE|USE") String region,
            @NotBlank @Size(max = 255) String displayName) {}

    public static class InvalidMasterDataRequestException extends RuntimeException {
        public InvalidMasterDataRequestException(String msg) {
            super(msg);
        }
    }

    // Dùng @NotNull để tránh NPE khi Jackson deserialize Map với null value.
    // Không thêm annotation trực tiếp vào StudioImportRow vì nó định nghĩa ở application layer.
}
