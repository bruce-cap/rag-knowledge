package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.model.dto.KnowledgeSpaceCreateDTO;
import com.example.ragbackend.model.dto.KnowledgeSpaceUpdateDTO;
import com.example.ragbackend.model.dto.SpaceMemberAddDTO;
import com.example.ragbackend.model.vo.UserListItemVO;

import java.util.List;

public interface KnowledgeSpaceService extends IService<KnowledgeSpace> {

    List<KnowledgeSpace> listAccessibleSpaces(Long userId, boolean isAdmin);

    List<KnowledgeSpace> listAllSpaces(Long userId, boolean isSuperAdmin);

    KnowledgeSpace createSpace(Long userId, boolean isSuperAdmin, KnowledgeSpaceCreateDTO dto);

    KnowledgeSpace updateSpace(Long spaceId, Long userId, boolean isSuperAdmin, KnowledgeSpaceUpdateDTO dto);

    void deleteSpace(Long spaceId, Long userId, boolean isSuperAdmin);

    List<SpaceMember> listMembers(Long spaceId, Long userId, boolean isSuperAdmin);

    List<UserListItemVO> listInvitableUsers(Long spaceId, Long userId, boolean isSuperAdmin);

    SpaceMember addMember(Long spaceId, Long operatorId, boolean isSuperAdmin, SpaceMemberAddDTO dto);

    void removeMember(Long spaceId, Long memberUserId, Long operatorId, boolean isSuperAdmin);

    SpaceMember updateMemberRole(Long spaceId, Long memberUserId, Long operatorId, boolean isSuperAdmin, String role);
}
