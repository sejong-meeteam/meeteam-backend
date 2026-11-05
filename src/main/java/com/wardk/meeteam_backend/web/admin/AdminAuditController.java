package com.wardk.meeteam_backend.web.admin;

import com.wardk.meeteam_backend.domain.admin.AdminAuditQueryService;
import com.wardk.meeteam_backend.domain.admin.AdminQueryOptions;
import com.wardk.meeteam_backend.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditController {
    private final AdminAuditQueryService service;
    @GetMapping
    public SuccessResponse<AdminResponses.Page<AdminResponses.AuditLog>> logs(@RequestParam Map<String, String> params) {
        var options = AdminQueryOptions.of(params.get("page"), params.get("size"), null, null);
        return SuccessResponse.onSuccess(service.logs(options, params.get("action"), params.get("targetType"), params.get("targetId")));
    }
}
