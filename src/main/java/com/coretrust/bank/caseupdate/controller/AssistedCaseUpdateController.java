package com.coretrust.bank.caseupdate.controller;

import com.coretrust.bank.caseupdate.dto.CaseUpdateRequest;
import com.coretrust.bank.caseupdate.service.CaseUpdateService;
import com.coretrust.bank.common.dto.MessageResponse;
import com.coretrust.bank.security.SecurityUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Case updates", description = "Support sends progress updates on a customer's case")
@RestController
@RequestMapping("/api/v1/assisted/case-updates")
@PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
@RequiredArgsConstructor
public class AssistedCaseUpdateController {

    private final CaseUpdateService caseUpdateService;

    @PostMapping
    public MessageResponse send(@AuthenticationPrincipal SecurityUser staff,
                                @Valid @RequestBody CaseUpdateRequest request) {
        String sentTo = caseUpdateService.send(staff, request);
        return MessageResponse.of("Update sent to the customer (%s).".formatted(sentTo));
    }
}