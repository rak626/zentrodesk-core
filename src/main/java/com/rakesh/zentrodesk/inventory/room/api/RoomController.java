package com.rakesh.zentrodesk.inventory.room.api;

import com.rakesh.zentrodesk.common.api.ApiResponse;
import com.rakesh.zentrodesk.common.api.PageResponse;
import com.rakesh.zentrodesk.inventory.room.RoomStatus;
import com.rakesh.zentrodesk.inventory.room.RoomType;
import com.rakesh.zentrodesk.platform.role.TenantPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/rooms")
@PreAuthorize("hasAuthority('" + TenantPermissions.ROOM_READ + "')")
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> findAll(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(defaultValue = "createdAt") String sort,
                                                           @RequestParam(defaultValue = "DESC") String direction,
                                                           @RequestParam(required = false) String search,
                                                           @RequestParam(required = false) UUID propertyId,
                                                           @RequestParam(required = false) RoomType type,
                                                           @RequestParam(required = false) RoomStatus status,
                                                           @RequestParam(required = false) Integer minCapacity,
                                                           @RequestParam(required = false) Integer maxCapacity,
                                                           @RequestParam(required = false) Boolean active,
                                                           HttpServletRequest servletRequest) {
        return ApiResponse.success("Rooms retrieved successfully", roomService.findAll(
                page, size, sort, direction, search, propertyId, type, status, minCapacity, maxCapacity, active
        ), servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomResponse> findById(@PathVariable UUID id, HttpServletRequest servletRequest) {
        return ApiResponse.success("Room retrieved successfully", roomService.findById(id), servletRequest.getRequestURI());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + TenantPermissions.ROOM_CREATE + "')")
    public ApiResponse<RoomResponse> create(@Valid @RequestBody RoomRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Room created successfully", roomService.create(request), servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.ROOM_UPDATE + "')")
    public ApiResponse<RoomResponse> update(@PathVariable UUID id, @Valid @RequestBody RoomRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Room updated successfully", roomService.update(id, request), servletRequest.getRequestURI());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + TenantPermissions.ROOM_UPDATE + "')")
    public ApiResponse<RoomResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody RoomStatusRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.success("Room status updated successfully", roomService.updateStatus(id, request), servletRequest.getRequestURI());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + TenantPermissions.ROOM_UPDATE + "')")
    public ApiResponse<Void> disable(@PathVariable UUID id, HttpServletRequest servletRequest) {
        roomService.disable(id);
        return ApiResponse.success("Room disabled successfully", null, servletRequest.getRequestURI());
    }
}
