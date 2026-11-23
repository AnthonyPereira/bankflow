package com.bankflow.gateway.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Répond 404 aux chemins qui ne correspondent à aucune route de la Gateway, en lecture ({@link
 * #respondToUnknownRead}) comme en écriture ({@link #respondToUnknownWrite}).
 */
@RestController
class FallbackController {

    @RequestMapping(
            path = "/**",
            method = {RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS})
    void respondToUnknownRead(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        respondNotFound(request, response);
    }

    @RequestMapping(
            path = "/**",
            method = {
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.PATCH,
                RequestMethod.DELETE
            })
    void respondToUnknownWrite(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        respondNotFound(request, response);
    }

    private static void respondNotFound(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ProblemResponses.write(
                request, response, HttpStatus.NOT_FOUND, "Aucune ressource à cette adresse.");
    }
}
