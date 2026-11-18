package com.bankflow.gateway.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Répond aux chemins qui ne correspondent à aucune route de la Gateway. */
@RestController
class FallbackController {

    @RequestMapping("/**")
    void notFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemResponses.write(
                request, response, HttpStatus.NOT_FOUND, "Aucune ressource à cette adresse.");
    }
}
