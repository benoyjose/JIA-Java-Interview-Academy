package com.jia.academy.content.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContentReleaseResponse(long version, Instant createdAt, List<Item> items) {
    public record Item(UUID id, String slug, String title, int contentRevision, String checksum) {}
}
