package com.edu.msa.program;

import com.edu.msa.program.dto.ProgramDtos.CommentRequest;
import com.edu.msa.program.dto.ProgramDtos.CommentResponse;
import com.edu.msa.program.dto.ProgramDtos.CreateProgramRequest;
import com.edu.msa.program.dto.ProgramDtos.ProgramDetailResponse;
import com.edu.msa.program.dto.ProgramDtos.ProgramSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/programs")
public class ProgramController {

    private final ProgramService service;

    public ProgramController(ProgramService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProgramSummaryResponse> list(
            @RequestParam(required = false) String cat,
            @RequestParam(required = false) List<String> purpose,
            @RequestParam(required = false) List<String> tech,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "latest") String sort) {
        return service.list(cat, purpose, tech, scope, q, sort);
    }

    @GetMapping("/pending")
    public List<ProgramSummaryResponse> pending() {
        return service.pending();
    }

    @GetMapping("/all")
    public List<ProgramSummaryResponse> all() {
        return service.all();
    }

    @GetMapping("/{id}")
    public ProgramDetailResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramDetailResponse create(@Valid @RequestBody CreateProgramRequest req) {
        return service.create(req);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return service.addComment(id, req);
    }
}
