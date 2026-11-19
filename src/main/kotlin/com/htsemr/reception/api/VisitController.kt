package com.htsemr.reception.api

import com.htsemr.reception.RegistrationService
import com.htsemr.reception.VisitQueryService
import com.htsemr.reception.VisitView
import com.htsemr.reception.api.dto.RegisterVisitRequest
import com.htsemr.reception.api.dto.RegisteredVisit
import com.htsemr.reception.api.dto.VisitResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1/visits")
internal class VisitController (
    private val registrationService : RegistrationService,
    private val visitQueryService : VisitQueryService
){

    @GetMapping("/{visitId}")
    fun getVisit(@PathVariable visitId : Long) : VisitResponse {
        val visitView : VisitView = visitQueryService.visit(visitId = visitId)
        return VisitResponse.from(
            view = visitView
        )
    }

    @PostMapping
    fun registerVisit(@Valid @RequestBody request: RegisterVisitRequest): ResponseEntity<RegisteredVisit> {
        val visitId = registrationService.registerVisit(
            patientNo = request.patientNo,
            departmentCode = request.departmentCode,
            doctorNo = request.doctorNo,
            visitDate = request.visitDate,
        )

        return ResponseEntity
            .created(URI.create("/api/v1/visits/$visitId"))
            .body(RegisteredVisit(visitId))
    }

}