package com.filemngt.v2.catalog.masterdata.adapter.in.web;

import com.filemngt.v2.catalog.masterdata.application.exception.ActressNotFoundException;
import com.filemngt.v2.catalog.masterdata.application.exception.DuplicateMasterDataException;
import com.filemngt.v2.catalog.masterdata.application.exception.ImportConflictException;
import com.filemngt.v2.catalog.masterdata.application.exception.StudioCodeNotFoundException;
import com.filemngt.v2.catalog.masterdata.application.exception.StudioNotFoundException;
import com.filemngt.v2.catalog.masterdata.application.exception.TagNotFoundException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {MasterDataController.class, MasterDataScanRegistryController.class})
public class MasterDataExceptionHandler {

    @ExceptionHandler({
        StudioNotFoundException.class,
        StudioCodeNotFoundException.class,
        TagNotFoundException.class,
        ActressNotFoundException.class
    })
    public ProblemDetail handleNotFound(RuntimeException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(DuplicateMasterDataException.class)
    public ProblemDetail handleDuplicate(DuplicateMasterDataException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(ImportConflictException.class)
    public ProblemDetail handleImportConflict(ImportConflictException ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Studio import has " + ex.conflicts().size() + " conflict(s)");
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Import Conflict");
        problem.setProperty("conflicts", ex.conflicts());
        return problem;
    }

    @ExceptionHandler(MasterDataController.InvalidMasterDataRequestException.class)
    public ProblemDetail handleBadRequest(MasterDataController.InvalidMasterDataRequestException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Bad Request");
        return problem;
    }
}
