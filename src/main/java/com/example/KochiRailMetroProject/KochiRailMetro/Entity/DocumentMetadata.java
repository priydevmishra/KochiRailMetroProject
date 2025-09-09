package com.example.KochiRailMetroProject.KochiRailMetro.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // yahan key ko meta_key bana diya
    @Column(name = "meta_key", nullable = false, length = 100)
    private String key;

    @Column(name = "meta_value", length = 255)
    private String value;

    // naya column jisse tumhara setDataType(...) kaam karega
    @Column(name = "data_type", length = 100)
    private String dataType;

    // agar Document se relation hai
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
}
