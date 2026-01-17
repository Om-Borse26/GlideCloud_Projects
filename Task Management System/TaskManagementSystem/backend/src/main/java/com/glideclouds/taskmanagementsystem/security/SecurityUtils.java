package com.glideclouds.taskmanagementsystem.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        return null;
    }

    public static String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        String name = auth.getName();
        return name == null ? null : name.trim().toLowerCase();
    }

    public static boolean currentHasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        String expected = role == null ? "" : role.trim().toUpperCase();
        if (expected.isBlank()) {
            return false;
        }
        String authority = expected.startsWith("ROLE_") ? expected : "ROLE_" + expected;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (ga != null && authority.equalsIgnoreCase(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
