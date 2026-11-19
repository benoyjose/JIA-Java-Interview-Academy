package com.jia.academy.catalogue.repository;
import com.jia.academy.catalogue.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category, UUID> {
 List<Category> findAllByTechnologySlugAndStatusOrderBySortOrderAsc(String technologySlug, PublicationStatus status);
 Optional<Category> findBySlugAndStatus(String slug, PublicationStatus status);
}
