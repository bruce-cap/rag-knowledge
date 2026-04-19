package com.example.ragbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.entity.SpaceMember;
import com.example.ragbackend.model.dto.KnowledgeSpaceCreateDTO;
import com.example.ragbackend.model.dto.KnowledgeSpaceUpdateDTO;
import com.example.ragbackend.model.dto.SpaceMemberAddDTO;

import java.util.List;

public interface KnowledgeSpaceService extends IService<KnowledgeSpace> {

    List<KnowledgeSpace> listAccessibleSpaces(Long userId, boolean isAdmin);

    KnowledgeSpace createSpace(Long userId, KnowledgeSpaceCreateDTO dto);

    KnowledgeSpace updateSpace(Long spaceId, Long userId, boolean isAdmin, KnowledgeSpaceUpdateDTO dto);

    void deleteSpace(Long spaceId, Long userId, boolean isAdmin);

    List<SpaceMember> listMembers(Long spaceId, Long userId, boolean isAdmin);

    SpaceMember addMember(Long spaceId, Long operatorId, boolean isAdmin, SpaceMemberAddDTO dto);

    void removeMember(Long spaceId, Long memberUserId, Long operatorId, boolean isAdmin);
}
