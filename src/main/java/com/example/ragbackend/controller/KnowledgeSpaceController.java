package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.model.dto.KnowledgeSpaceCreateDTO;
import com.example.ragbackend.model.dto.KnowledgeSpaceUpdateDTO;
import com.example.ragbackend.model.dto.SpaceMemberAddDTO;
import com.example.ragbackend.model.dto.SpaceMemberRoleUpdateDTO;
import com.example.ragbackend.model.vo.UserListItemVO;
import com.example.ragbackend.service.KnowledgeSpaceService;
import com.example.ragbackend.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/space")
public class KnowledgeSpaceController {

    @Autowired
    private KnowledgeSpaceService knowledgeSpaceService;

    @GetMapping("/list")
    public Result<List<KnowledgeSpace>> listSpaces() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeSpaceService.listAccessibleSpaces(userId, SecurityUtils.isAdmin()));
    }


    // 列出当前用户所有可以加入的空间
    @GetMapping("/listAll")
    public Result<List<KnowledgeSpace>> listAllSpaces() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeSpaceService.listAllSpaces(userId, SecurityUtils.isSuperAdmin()));
    }

    @PostMapping("/create")
    public Result<KnowledgeSpace> createSpace(@RequestBody KnowledgeSpaceCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeSpaceService.createSpace(userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @PutMapping("/update/{id}")
    public Result<KnowledgeSpace> updateSpace(@PathVariable Long id, @RequestBody KnowledgeSpaceUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeSpaceService.updateSpace(id, userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> deleteSpace(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        knowledgeSpaceService.deleteSpace(id, userId, SecurityUtils.isSuperAdmin());
        return Result.success("Knowledge space deleted successfully");
    }

    @GetMapping("/{id}/members")
    public Result<List<SpaceMember>> listMembers(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeSpaceService.listMembers(id, userId, SecurityUtils.isAdmin()));
    }

    @GetMapping("/{id}/invitableUsers")
    public Result<List<UserListItemVO>> listInvitableUsers(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeSpaceService.listInvitableUsers(id, userId, SecurityUtils.isSuperAdmin()));
    }

    @PostMapping("/{id}/members")
    public Result<SpaceMember> addMember(@PathVariable Long id, @RequestBody SpaceMemberAddDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeSpaceService.addMember(id, userId, SecurityUtils.isSuperAdmin(), dto));
    }

    @DeleteMapping("/{id}/members/{memberUserId}")
    public Result<String> removeMember(@PathVariable Long id, @PathVariable Long memberUserId) {
        Long userId = SecurityUtils.getCurrentUserId();
        knowledgeSpaceService.removeMember(id, memberUserId, userId, SecurityUtils.isSuperAdmin());
        return Result.success("Member removed successfully");
    }

    @PutMapping("/{id}/members/updateRole/{memberUserId}")
    public Result<SpaceMember> updateMemberRole(@PathVariable Long id,
                                                @PathVariable Long memberUserId,
                                                @RequestBody SpaceMemberRoleUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(knowledgeSpaceService.updateMemberRole(
                id, memberUserId, userId, SecurityUtils.isSuperAdmin(), dto == null ? null : dto.getRole()));
    }
}
