package com.apps.mindlog.reference.controller;

import com.apps.mindlog.global.config.ApiVersionConfig;
import com.apps.mindlog.reference.dto.ReferenceResponses.*;
import com.apps.mindlog.reference.service.ReferenceQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping(path = "/api", version = "1")
@SecurityRequirement(name = "bearerAuth")
public class ReferenceController {
    private static final CacheControl CATALOG_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
    private final ReferenceQueryService queries;
    private final JsonMapper json;

    public ReferenceController(ReferenceQueryService queries, JsonMapper json) {
        this.queries = queries;
        this.json = json;
    }

    @GetMapping("/situation-types")
    public ResponseEntity<Catalog<Choice>> situations(@RequestParam(defaultValue="before") String context,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return cached(queries.situations(context, includeInactive));
    }

    @GetMapping("/body-symptoms")
    public ResponseEntity<Catalog<DescribedChoice>> symptoms(@RequestParam(defaultValue="after") String context,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return cached(queries.symptoms(context, includeInactive));
    }

    @GetMapping("/emotion-characters")
    public ResponseEntity<Catalog<Emotion>> emotions(@RequestParam(defaultValue="false") boolean includeInactive) {
        return cached(queries.emotions(includeInactive));
    }

    @GetMapping("/anxiety-patterns")
    public ResponseEntity<Catalog<DescribedChoice>> patterns(@RequestParam(defaultValue="false") boolean includeInactive) {
        return cached(queries.patterns(includeInactive));
    }

    @GetMapping("/distortion-tags")
    public ResponseEntity<Catalog<DescribedChoice>> distortions(@RequestParam(defaultValue="false") boolean includeInactive) {
        return cached(queries.distortions(includeInactive));
    }

    @GetMapping("/distortion-tags/{distortionTagId}")
    public ResponseEntity<Guide> guide(@PathVariable long distortionTagId) {
        return cached(queries.guide(distortionTagId));
    }

    @GetMapping("/positive-solutions")
    public ResponseEntity<Catalog<Solution>> solutions(@RequestParam(required=false) String category,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return cached(queries.solutions(category, includeInactive));
    }

    private <T> ResponseEntity<T> cached(T catalog) {
        // A tag covers the filtered representation, including order, guide copy and active flags.
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(catalog));
            return ResponseEntity.ok().cacheControl(CATALOG_CACHE)
                    .varyBy(HttpHeaders.AUTHORIZATION, ApiVersionConfig.HEADER, HttpHeaders.ACCEPT)
                    .eTag("W/\"" + HexFormat.of().formatHex(digest) + "\"").body(catalog);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
