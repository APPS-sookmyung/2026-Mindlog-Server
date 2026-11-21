package com.apps.mindlog.reference.controller;

import com.apps.mindlog.reference.dto.ReferenceResponses.*;
import com.apps.mindlog.reference.service.ReferenceQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api", version = "1")
@SecurityRequirement(name = "bearerAuth")
public class ReferenceController {
    private final ReferenceQueryService queries;
    public ReferenceController(ReferenceQueryService queries) { this.queries=queries; }

    @GetMapping("/situation-types")
    public Catalog<Choice> situations(@RequestParam(defaultValue="before") String context,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.situations(context,includeInactive);
    }

    @GetMapping("/body-symptoms")
    public Catalog<DescribedChoice> symptoms(@RequestParam(defaultValue="after") String context,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.symptoms(context,includeInactive);
    }

    @GetMapping("/emotion-characters")
    public Catalog<Emotion> emotions(@RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.emotions(includeInactive);
    }

    @GetMapping("/anxiety-patterns")
    public Catalog<DescribedChoice> patterns(@RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.patterns(includeInactive);
    }

    @GetMapping("/distortion-tags")
    public Catalog<DescribedChoice> distortions(@RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.distortions(includeInactive);
    }

    @GetMapping("/distortion-tags/{distortionTagId}")
    public Guide guide(@PathVariable long distortionTagId) { return queries.guide(distortionTagId); }

    @GetMapping("/positive-solutions")
    public Catalog<Solution> solutions(@RequestParam(required=false) String category,
            @RequestParam(defaultValue="false") boolean includeInactive) {
        return queries.solutions(category,includeInactive);
    }
}
