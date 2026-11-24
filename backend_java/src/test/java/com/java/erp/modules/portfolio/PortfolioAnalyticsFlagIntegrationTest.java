package com.java.erp.modules.portfolio;

import com.java.erp.modules.portfolio.controller.PortfolioAnalyticsController;
import com.java.erp.modules.portfolio.controller.PortfolioController;
import com.java.erp.modules.portfolio.service.PortfolioSummaryService;
import com.java.erp.support.PortfolioTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Default configuration: Portfolio Analytics (GET /portfolio/summary) does not exist, while student portfolios and
 * the review queue keep working. PortfolioSummaryIntegrationTest runs analytics with the flag switched on.
 */
class PortfolioAnalyticsFlagIntegrationTest extends PortfolioTestBase {

    @Autowired private ApplicationContext context;
    @Autowired private Environment environment;

    @Test
    void analyticsAreOffByDefaultButTheCodeIsKept() {
        assertThat(environment.getProperty("features.portfolio-analytics.enabled", Boolean.class, false)).isFalse();
        assertThat(context.getBeansOfType(PortfolioAnalyticsController.class)).isEmpty();
        assertThat(context.getBeansOfType(PortfolioController.class)).hasSize(1);
        assertThat(context.getBeansOfType(PortfolioSummaryService.class)).hasSize(1);
    }

    @Test
    void analyticsEndpointIsUnavailableWhileReviewAndStudentPortfolioWork() throws Exception {
        mvc.perform(get("/portfolio/summary").header("Authorization", token(hod))).andExpect(status().isNotFound());
        mvc.perform(get("/portfolio/summary").header("Authorization", token(staff))).andExpect(status().isNotFound());
        mvc.perform(get("/portfolio/summary")).andExpect(status().isUnauthorized());

        mvc.perform(get("/portfolio/review").header("Authorization", token(staff))).andExpect(status().isOk());
        mvc.perform(get("/portfolio/review").header("Authorization", token(hod))).andExpect(status().isOk());
        mvc.perform(get("/portfolio/me").header("Authorization", token(student.user()))).andExpect(status().isOk());
        mvc.perform(get("/portfolio/me/summary").header("Authorization", token(student.user()))).andExpect(status().isOk());
        mvc.perform(get("/portfolio/students/" + student.student().getId()).header("Authorization", token(staff)))
                .andExpect(status().isOk());
    }
}
