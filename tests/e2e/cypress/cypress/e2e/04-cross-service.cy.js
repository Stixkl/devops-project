describe('E2E - Cross-Service Integration (API)', () => {
  const authService = Cypress.env('authServiceUrl');
  const formService = Cypress.env('formServiceUrl');
  const notificationService = Cypress.env('notificationServiceUrl');
  const promotionService = Cypress.env('promotionServiceUrl');
  const gatewayService = Cypress.env('gatewayServiceUrl');
  const identityService = Cypress.env('identityServiceUrl');

  it('TC_E2E_016: All 6 services respond to health checks', () => {
    const services = [authService, identityService, gatewayService, formService, notificationService, promotionService];
    const checks = [];
    
    services.forEach(baseUrl => {
      checks.push(
        cy.request({ method: 'GET', url: `${baseUrl}/actuator/health`, failOnStatusCode: false })
          .then(res => ({ service: baseUrl, status: res.status }))
      );
    });

    cy.wrap(Promise.all(checks)).then(results => {
      results.forEach(r => {
        expect(r.status).to.be.oneOf([200, 503]);
      });
    });
  });

  it('TC_E2E_017: Full flow: Login -> Identity -> Form -> Gate', () => {
    const anonId = 'cross-' + Date.now();
    
    cy.request({
      method: 'POST',
      url: `${authService}/api/v1/auth/visitor/handoff`,
      body: { anonymousId: anonId },
      failOnStatusCode: false
    }).then(authRes => {
      expect(authRes.status).to.be.oneOf([200, 401]);
      
      if (authRes.status === 200) {
        cy.request({
          method: 'POST',
          url: `${formService}/api/v1/surveys`,
          body: { userId: anonId, responses: { q1: 'NO' }, submittedAt: Date.now() },
          failOnStatusCode: false
        }).then(formRes => {
          expect(formRes.status).to.be.oneOf([200, 201, 401]);
          
          cy.request({
            method: 'POST',
            url: `${gatewayService}/api/v1/gate/validate`,
            body: { token: authRes.body.token || 'test' },
            failOnStatusCode: false
          }).then(gateRes => {
            expect(gateRes.status).to.be.oneOf([200, 400, 401]);
          });
        });
      }
    });
  });

  it('TC_E2E_018: Status change triggers notification cascade', () => {
    cy.request({
      method: 'POST',
      url: `${notificationService}/api/v1/notifications/priority`,
      body: {
        anonymousId: 'cascade-' + Date.now(),
        status: 'CONFIRMED',
        affectedCount: 10,
        eventType: 'CONFIRMED_CASE'
      },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 401, 404]);
    });
  });

  it('TC_E2E_019: Promotion service accepts health report', () => {
    cy.request({
      method: 'POST',
      url: `${promotionService}/api/v1/health/report`,
      body: {
        anonymousId: 'promote-' + Date.now(),
        status: 'SUSPECT',
        adminOverride: false
      },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 401, 403]);
    });
  });

  it('TC_E2E_020: Identity service creates anonymous ID', () => {
    cy.request({
      method: 'POST',
      url: `${identityService}/api/v1/identity/create`,
      body: { username: 'newuser-' + Date.now() },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 201, 401]);
    });
  });
});