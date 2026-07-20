package com.ecoexpress.catalog.api;

import com.ecoexpress.catalog.domain.Category;
import com.ecoexpress.catalog.dto.CatalogDtos.CategoryResponse;
import com.ecoexpress.catalog.dto.CatalogDtos.CreateCategoryRequest;
import com.ecoexpress.catalog.mapper.CatalogMapper;
import com.ecoexpress.catalog.repository.CategoryRepository;
import com.ecoexpress.common.exception.ApiExceptions.ConflictException;
import com.ecoexpress.common.exception.ApiExceptions.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Categories")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CatalogMapper mapper;

    @Operation(summary = "Category tree for the storefront nav")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<CategoryResponse>> tree() {
        List<CategoryResponse> roots = categoryRepository.findActiveRoots().stream()
                .map(c -> mapper.toCategory(c, true))
                .toList();
        return ResponseEntity.ok(roots);
    }

    @Operation(summary = "Get a category by slug")
    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<CategoryResponse> bySlug(@PathVariable String slug) {
        Category c = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("No category '" + slug + "'."));
        return ResponseEntity.ok(mapper.toCategory(c, true));
    }

    @Operation(summary = "Create a category")
    @PostMapping
    @PreAuthorize("hasAuthority('category:write')")
    @Transactional
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new ConflictException("A category with slug '" + request.slug() + "' exists.");
        }

        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new NotFoundException("No category " + request.parentId()));
        }

        Category category = Category.builder()
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .imageUrl(request.imageUrl())
                .parent(parent)
                .position(request.position() == null ? 0 : request.position())
                .isActive(true)
                .build();

        Category saved = categoryRepository.save(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toCategory(saved, false));
    }
}
