package it.penny.api.security;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="penny_users")
public class PennyUser {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(nullable=false,unique=true) public String email;
 @Column(name="full_name",nullable=false) public String fullName;
 @Column(name="password_hash",nullable=false) public String passwordHash;
 @Column(nullable=false) public boolean enabled=true;
 @Column(name="must_change_password",nullable=false) public boolean mustChangePassword=true;
 @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
 @Column(name="password_changed_at") public Instant passwordChangedAt;
 @Column(name="session_version",nullable=false) public long sessionVersion=0;
}
