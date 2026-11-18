package com.szr.crosserp.identity.user;

import java.util.List;
import com.szr.crosserp.identity.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisplayDirectoryService {
    public record Entry(long id, String loginName, String displayName, String status, long sourceVersion) {}
    private final UserRepository users;
    public DisplayDirectoryService(UserRepository users) { this.users = users; }
    @Transactional(readOnly = true)
    public List<Entry> entries() {
        return users.findAll().stream().map(user -> new Entry(user.getId(),user.getLoginName(),user.getDisplayName(),
            user.getStatus().name(),Math.addExact(Math.multiplyExact(user.getUpdatedAt().getEpochSecond(),1000000L),user.getUpdatedAt().getNano()/1000))).toList();
    }
}
