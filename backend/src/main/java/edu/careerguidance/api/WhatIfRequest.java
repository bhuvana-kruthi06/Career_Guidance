package edu.careerguidance.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record WhatIfRequest(@Valid @NotNull MatchRequest profile,
                            @NotEmpty @Size(min=1, max=3) Map<@NotBlank @Size(max=120) String, @NotNull @Min(1) @Max(5) Integer> changes) { }
