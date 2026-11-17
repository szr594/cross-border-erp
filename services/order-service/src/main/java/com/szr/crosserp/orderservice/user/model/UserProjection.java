package com.szr.crosserp.orderservice.user.model;

import jakarta.persistence.*;

@Entity
@Table(name="user_display_projections")
public class UserProjection {
    @Id private Long id;
    @Column(name="login_name",nullable=false,length=100) private String loginName;
    @Column(name="display_name",nullable=false,length=255) private String displayName;
    @Column(nullable=false,length=20) private String status;
    @Column(name="source_version",nullable=false) private long sourceVersion;
    protected UserProjection() {}
    public Long getId(){return id;}
    public String getLoginName(){return loginName;}
    public String getDisplayName(){return displayName;}
    public String getStatus(){return status;}
    public long getSourceVersion(){return sourceVersion;}
}
