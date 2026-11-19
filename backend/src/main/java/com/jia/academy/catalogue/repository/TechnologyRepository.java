package com.jia.academy.catalogue.repository;
import com.jia.academy.catalogue.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TechnologyRepository extends JpaRepository<Technology, UUID> {
 List<Technology> findAllByStatusOrderBySortOrderAsc(PublicationStatus status);
 Optional<Technology> findBySlugAndStatus(String slug, PublicationStatus status);
}
