package it.penny.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RbacServiceTest {
 @Test void explicitDenyWins(){JdbcTemplate db=mock(JdbcTemplate.class);when(db.query(contains("penny_user_permissions"),any(org.springframework.jdbc.core.RowMapper.class),eq(1L),eq("USERS_READ"))).thenReturn(List.of("DENY"));assertFalse(new RbacService(db).allowed(1L,"USERS_READ"));}
 @Test void roleDenyWinsOverAllow(){JdbcTemplate db=mock(JdbcTemplate.class);when(db.query(contains("penny_user_permissions"),any(org.springframework.jdbc.core.RowMapper.class),eq(1L),eq("USERS_READ"))).thenReturn(List.of());when(db.query(contains("penny_role_permissions"),any(org.springframework.jdbc.core.RowMapper.class),eq(1L),eq("USERS_READ"))).thenReturn(List.of("ALLOW","DENY"));assertFalse(new RbacService(db).allowed(1L,"USERS_READ"));}
 @Test void defaultDeny(){JdbcTemplate db=mock(JdbcTemplate.class);when(db.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),eq(1L),eq("USERS_READ"))).thenReturn(List.of());assertFalse(new RbacService(db).allowed(1L,"USERS_READ"));}
}
