package com.itilms.batch.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.itilms.batch.dto.response.PublicBatchResponse;
import com.itilms.batch.service.BatchCourseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** The batches the website offers to sell. Open to anyone; only batches with a fee and a free seat are listed. */
@Tag(name = "Public batches", description = "The batches the website sells")
@RestController
@RequestMapping("/api/public/batches")
@RequiredArgsConstructor
public class PublicBatchController {

    private final BatchCourseService batchCourses;

    @Operation(summary = "Batches open for sale",
            description = "Planned or ongoing batches that have a batch fee and a free seat, earliest start first. "
                    + "No student names; just what the batch covers, when it meets, its fee and seats left.")
    @GetMapping
    public List<PublicBatchResponse> openForSale() {
        return batchCourses.openForSale();
    }
}
