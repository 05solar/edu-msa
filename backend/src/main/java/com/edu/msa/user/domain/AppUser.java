package com.edu.msa.user.domain;

import com.edu.msa.common.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;
    private String dept;
    @Enumerated(EnumType.STRING)
    private Role role;

    protected AppUser() {}

    public AppUser(String name, String dept, Role role) {
        this.name = name;
        this.dept = dept;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDept() { return dept; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
