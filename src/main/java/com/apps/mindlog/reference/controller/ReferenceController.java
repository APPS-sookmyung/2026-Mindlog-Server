package com.apps.mindlog.reference.controller;

import com.apps.mindlog.reference.dto.ReferenceResponses.*;
import com.apps.mindlog.reference.service.ReferenceQueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api", version = "1")
public class ReferenceController {
    private final ReferenceQueryService queries;
    public ReferenceController(ReferenceQueryService queries) { this.queries=queries; }

    @GetMapping("/situation-types")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public Catalog<Choice> situations(@RequestParam(defaultValue="before") String context,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.situations(context,includeInactive);
    }

    @GetMapping("/body-symptoms")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public Catalog<DescribedChoice> symptoms(@RequestParam(defaultValue="after") String context,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.symptoms(context,includeInactive);
    }

    @GetMapping("/emotion-characters")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public Catalog<Emotion> emotions(@RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.emotions(includeInactive);
    }

    @GetMapping("/anxiety-patterns")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public Catalog<DescribedChoice> patterns(@RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.patterns(includeInactive);
    }

    @GetMapping("/distortion-tags")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public Catalog<DescribedChoice> distortions(@RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.distortions(includeInactive);
    }

    @GetMapping("/distortion-tags/{distortionTagId}")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public Guide guide(@PathVariable long distortionTagId) { return queries.guide(distortionTagId); }

    @GetMapping("/positive-solutions")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public Catalog<Solution> solutions(@RequestParam(required=false) String category,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.solutions(category,includeInactive);
    }
}
