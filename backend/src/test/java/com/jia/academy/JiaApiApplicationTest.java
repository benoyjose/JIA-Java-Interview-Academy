package com.jia.academy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.jia.academy.catalogue.domain.PublicationStatus;
import com.jia.academy.catalogue.repository.CategoryRepository;
import com.jia.academy.catalogue.repository.LessonRepository;
import com.jia.academy.catalogue.repository.TechnologyRepository;
import com.jia.academy.catalogue.service.CatalogueService;
import java.util.List;
import org.junit.jupiter.api.Test;

class JiaApiApplicationTest {
    @Test
    void learnerCatalogueReturnsOnlyPublishedTechnologyRecords() {
        var technologyRepository = mock(TechnologyRepository.class);
        var categoryRepository = mock(CategoryRepository.class);
        var lessonRepository = mock(LessonRepository.class);
        when(technologyRepository.findAllByStatusOrderBySortOrderAsc(PublicationStatus.PUBLISHED)).thenReturn(List.of());

        var service = new CatalogueService(technologyRepository, categoryRepository, lessonRepository);

        assertThat(service.technologies()).isEmpty();
        verify(technologyRepository).findAllByStatusOrderBySortOrderAsc(PublicationStatus.PUBLISHED);
        verifyNoInteractions(categoryRepository, lessonRepository);
    }
}
