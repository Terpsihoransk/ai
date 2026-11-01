package ru.ars.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.ars.ai.model.LoadedDocument;

public interface DocumentRepository extends JpaRepository<LoadedDocument, Long> {

    boolean existsByFilenameAndContentHash(String filename, String contentHash);

}