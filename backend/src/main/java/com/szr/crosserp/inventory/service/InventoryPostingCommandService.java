package com.szr.crosserp.inventory.service;

import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.inventory.dto.InventoryPostingResponse;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryPostingCommandService {
    private final InventoryPostingService postingService;
    private final UserRepository userRepository;

    public InventoryPostingCommandService(
        InventoryPostingService postingService,
        UserRepository userRepository
    ) {
        this.postingService = postingService;
        this.userRepository = userRepository;
    }

    @Transactional
    public InventoryPostingResponse postHistoricalReceipt(
        Long receiptId, String subject, List<String> roles
    ) {
        if (roles == null || !roles.contains("OWNER")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVENTORY_FORBIDDEN", "无权执行历史收货补偿");
        }
        User actor = requireActor(subject);
        return postingService.postReceipt(receiptId, actor);
    }

    private User requireActor(String subject) {
        try {
            return userRepository.findById(Long.valueOf(subject))
                .orElseThrow(() -> new ApiException(
                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效"
                ));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录状态无效");
        }
    }
}
