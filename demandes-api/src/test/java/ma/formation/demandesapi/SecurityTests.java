package ma.formation.demandesapi;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

/**
 * Travail supplementaire 3 : tests de securite.
 * Le JwtDecoder est simule : les tests n'ont pas besoin que Keycloak tourne.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeEach
    void configurerDecodeur() {
        // Tout jeton "brut" envoye dans l'en-tete Authorization est considere invalide.
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("Jeton invalide"));
    }

    private RequestPostProcessor alice() {
        return jwt()
            .jwt(j -> j.subject("alice-id").claim("preferred_username", "alice"))
            .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private RequestPostProcessor bob() {
        return jwt()
            .jwt(j -> j.subject("bob-id").claim("preferred_username", "bob"))
            .authorities(new SimpleGrantedAuthority("ROLE_USER"),
                         new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void apiPubliqueAccessibleSansToken() throws Exception {
        mvc.perform(get("/api/public"))
            .andExpect(status().isOk());
    }

    @Test
    void requeteSansTokenRefusee() throws Exception {
        mvc.perform(get("/api/demandes"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void requeteAvecTokenInvalideRefusee() throws Exception {
        mvc.perform(get("/api/demandes")
                .header("Authorization", "Bearer eyJ.token.invalide"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userNePeutPasUtiliserApiAdmin() throws Exception {
        mvc.perform(get("/api/admin/demandes").with(alice()))
            .andExpect(status().isForbidden());

        mvc.perform(patch("/api/admin/demandes/1")
                .with(alice())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\":\"VALIDEE\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminPeutConsulterToutesLesDemandes() throws Exception {
        mvc.perform(get("/api/admin/demandes").with(bob()))
            .andExpect(status().isOk());
    }

    @Test
    void adminPeutValiderEtRefuserUneDemande() throws Exception {

        String json = mvc.perform(post("/api/demandes")
                .with(alice())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objet\":\"Attestation de scolarite\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
            .andReturn().getResponse().getContentAsString();

        Integer id = JsonPath.read(json, "$.id");

        mvc.perform(patch("/api/admin/demandes/" + id)
                .with(bob())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\":\"VALIDEE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statut").value("VALIDEE"));

        mvc.perform(patch("/api/admin/demandes/" + id)
                .with(bob())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\":\"REFUSEE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statut").value("REFUSEE"));
    }

    @Test
    void statutInvalideRejete() throws Exception {
        mvc.perform(patch("/api/admin/demandes/1")
                .with(bob())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\":\"N_IMPORTE_QUOI\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void profilExtraitDuJwt() throws Exception {
        mvc.perform(get("/api/profil").with(alice()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("alice-id"))
            .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void profilSansTokenRefuse() throws Exception {
        mvc.perform(get("/api/profil"))
            .andExpect(status().isUnauthorized());
    }
}
