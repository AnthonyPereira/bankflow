package com.bankflow.gateway.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Répond aux chemins qui ne correspondent à aucune route de la Gateway. */
@RestController
class FallbackController {

    @RequestMapping(
            path = "/**",
            method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS})
    void readNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notFound(request, response);
    }

    @RequestMapping(
            path = "/**",
            method = {
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.PATCH,
                RequestMethod.DELETE
            })
    void writeNotFound(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        notFound(request, response);
    }

    private static void notFound(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ProblemResponses.write(
                request, response, HttpStatus.NOT_FOUND, "Aucune ressource à cette adresse.");
    }
}
