
package com.commercecore.integration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "commercecore.payments.simulation-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final Long ORDER_ID = 999999L;

    // ============================================================
    // TESTE 1 - APROVACAO SIMULADA DESABILITADA
    // ============================================================

    @Test
    void shouldBlockApprovalWhenSimulationDisabled()
            throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/payment/approve",
                    ORDER_ID
                )
                    .with(jwt()
                        .jwt(token -> token.subject("1"))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )
                        ))
            )
            .andExpect(status().isForbidden());
    }

    // ============================================================
    // TESTE 2 - REJEICAO SIMULADA DESABILITADA
    // ============================================================

    @Test
    void shouldBlockRejectionWhenSimulationDisabled()
            throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/payment/reject",
                    ORDER_ID
                )
                    .with(jwt()
                        .jwt(token -> token.subject("1"))
                        .authorities(
                            new SimpleGrantedAuthority(
                                "ROLE_CUSTOMER"
                            )
                        ))
            )
            .andExpect(status().isForbidden());
    }

    // ============================================================
    // TESTE 3 - APROVACAO SEM JWT
    // ============================================================

    @Test
    void shouldRejectUnauthenticatedApproval()
            throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/payment/approve",
                    ORDER_ID
                )
            )
            .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // TESTE 4 - REJEICAO SEM JWT
    // ============================================================

    @Test
    void shouldRejectUnauthenticatedRejection()
            throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/orders/{orderId}/payment/reject",
                    ORDER_ID
                )
            )
            .andExpect(status().isUnauthorized());
    }
}
