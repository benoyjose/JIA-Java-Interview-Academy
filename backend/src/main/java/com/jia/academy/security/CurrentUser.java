package com.jia.academy.security;

import java.util.List;
import java.util.UUID;

public record CurrentUser(UUID id, String name, String email, List<String> roles, List<String> permissions) {}
