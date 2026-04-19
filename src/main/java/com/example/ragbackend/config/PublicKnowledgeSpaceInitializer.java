package com.example.ragbackend.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ragbackend.constant.SystemSpaceConstants;
import com.example.ragbackend.entity.KnowledgeSpace;
import com.example.ragbackend.mapper.KnowledgeSpaceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class PublicKnowledgeSpaceInitializer implements ApplicationRunner {

    private final KnowledgeSpaceMapper knowledgeSpaceMapper;

    public PublicKnowledgeSpaceInitializer(KnowledgeSpaceMapper knowledgeSpaceMapper) {
        this.knowledgeSpaceMapper = knowledgeSpaceMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        KnowledgeSpace existing = knowledgeSpaceMapper.selectOne(new LambdaQueryWrapper<KnowledgeSpace>()
                .eq(KnowledgeSpace::getCode, SystemSpaceConstants.PUBLIC_SPACE_CODE)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }

        KnowledgeSpace publicSpace = new KnowledgeSpace();
        publicSpace.setName(SystemSpaceConstants.PUBLIC_SPACE_NAME);
        publicSpace.setCode(SystemSpaceConstants.PUBLIC_SPACE_CODE);
        publicSpace.setType(SystemSpaceConstants.PUBLIC_SPACE_TYPE);
        publicSpace.setIsSystem(true);
        publicSpace.setDescription("System-managed public knowledge space");
        publicSpace.setStatus(1);
        publicSpace.setCreateBy(SystemSpaceConstants.SYSTEM_OPERATOR_ID);
        publicSpace.setCreateTime(LocalDateTime.now());
        publicSpace.setUpdateTime(LocalDateTime.now());
        knowledgeSpaceMapper.insert(publicSpace);
        log.info("Public knowledge space initialized, id={}", publicSpace.getId());
    }
}
