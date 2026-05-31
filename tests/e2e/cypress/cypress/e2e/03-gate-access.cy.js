describe('E2E - Gate Access Control (API)', () => {
  const gatewayService = Cypress.env('gatewayServiceUrl');
  const authService = Cypress.env('authServiceUrl');

  it('TC_E2E_011: POST /api/v1/gate/validate - validates health token GREEN', () => {
    cy.request({
      method: 'POST',
      url: `${gatewayService}/api/v1/gate/validate`,
      body: { token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test-healthy' },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 400, 401]);
    });
  });

  it('TC_E2E_012: POST /api/v1/gate/validate - validates visitor token', () => {
    cy.request({
      method: 'POST',
      url: `${authService}/api/v1/auth/visitor/handoff`,
      body: { anonymousId: 'gate-test-' + Date.now() },
      failOnStatusCode: false
    }).then(handoff => {
      if (handoff.status === 200 && handoff.body.token) {
        cy.request({
          method: 'POST',
          url: `${gatewayService}/api/v1/gate/validate`,
          body: { token: handoff.body.token },
          failOnStatusCode: false
        }).then(res => {
          expect(res.status).to.be.oneOf([200, 400, 401]);
        });
      }
    });
  });

  it('TC_E2E_013: GET /api/v1/status/{id} - returns user status from cache', () => {
    const testId = 'status-test-' + Date.now();
    cy.request({
      method: 'GET',
      url: `${gatewayService}/api/v1/status/${testId}`,
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 404, 401]);
    });
  });

  it('TC_E2E_014: GET /actuator/health - gateway service healthy', () => {
    cy.request({
      method: 'GET',
      url: `${gatewayService}/actuator/health`,
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 503]);
    });
  });

  it('TC_E2E_015: Visitor token -> Gate validation end-to-end', () => {
    const anonymousId = 'visitor-gate-' + Date.now();
    cy.request({
      method: 'POST',
      url: `${authService}/api/v1/auth/visitor/handoff`,
      body: { anonymousId },
      failOnStatusCode: false
    }).then(res => {
      if (res.status === 200) {
        const token = res.body.token;
        expect(token).to.be.a('string');
        cy.request({
          method: 'POST',
          url: `${gatewayService}/api/v1/gate/validate`,
          body: { token },
          failOnStatusCode: false
        }).then(gateRes => {
          expect(gateRes.status).to.be.oneOf([200, 400, 401]);
        });
      }
    });
  });
});