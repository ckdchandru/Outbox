package com.ob.controller;

import com.ob.dto.EventDetails;
import com.ob.dto.EventSummary;
import com.ob.dto.PageResponse;
import com.ob.dto.RequeueResponse;
import com.ob.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/outbox")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dead")
    public PageResponse<EventSummary> deadEvents(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return adminService.getDeadEvents(page, size);
    }

    @GetMapping("/{id}")
    public EventDetails event(@PathVariable UUID id) {
        return EventDetails.from(adminService.getEvent(id));
    }

    @PostMapping("/{id}/requeue")
    public RequeueResponse requeue(@PathVariable UUID id) {
        adminService.requeue(id);
        return new RequeueResponse(id, "PENDING");
    }
}
