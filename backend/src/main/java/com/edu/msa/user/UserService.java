package com.edu.msa.user;

import com.edu.msa.common.NotFoundException;
import com.edu.msa.common.Role;
import com.edu.msa.user.domain.AppUser;
import com.edu.msa.user.dto.UserDtos.UserResponse;
import com.edu.msa.user.repository.AppUserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository repo;

    public UserService(AppUserRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return repo.findAll().stream()
                .map(u -> new UserResponse(u.getName(), u.getDept(), u.getRole()))
                .toList();
    }

    @Transactional
    public UserResponse updateRole(String name, Role role) {
        AppUser u = repo.findByName(name).orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + name));
        u.setRole(role);
        return new UserResponse(u.getName(), u.getDept(), u.getRole());
    }
}
