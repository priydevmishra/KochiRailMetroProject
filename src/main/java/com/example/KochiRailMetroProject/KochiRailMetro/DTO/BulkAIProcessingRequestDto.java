package com.example.KochiRailMetroProject.KochiRailMetro.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkAIProcessingRequestDto {
    private List<Long> documentIds;
}