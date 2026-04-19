package com.example.ragbackend.controller;

import com.example.ragbackend.common.Result;
import com.example.ragbackend.entity.Folder;
import com.example.ragbackend.model.dto.FolderCreateDTO;
import com.example.ragbackend.model.dto.FolderUpdateDTO;
import com.example.ragbackend.service.FolderService;
import com.example.ragbackend.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folder")
public class FolderController {

    @Autowired
    private FolderService folderService;

    @GetMapping("/tree")
    public Result<List<Folder>> getTree(@RequestParam("spaceId") Long spaceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(folderService.getFolderTree(spaceId, userId, SecurityUtils.isAdmin()));
    }

    @PostMapping("/create")
    public Result<Folder> createFolder(@RequestBody FolderCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(folderService.createFolder(userId, SecurityUtils.isAdmin(), dto));
    }

    @PutMapping("/{id}")
    public Result<Folder> updateFolder(@PathVariable Long id, @RequestBody FolderUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(folderService.updateFolder(id, userId, SecurityUtils.isAdmin(), dto));
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteFolder(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        folderService.deleteFolder(id, userId, SecurityUtils.isAdmin());
        return Result.success("Folder deleted successfully");
    }
}
