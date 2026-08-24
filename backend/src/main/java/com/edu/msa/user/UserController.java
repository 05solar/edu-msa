package com.edu.msa.user;

import com.edu.msa.user.dto.UserDtos.RoleUpdateRequest;
import com.edu.msa.user.dto.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list() {
        return service.list();
    }

    @PatchMapping("/{name}/role")
    public UserResponse updateRole(@PathVariable String name, @Valid @RequestBody RoleUpdateRequest req) {
        return service.updateRole(name, req.role());
    }
}
