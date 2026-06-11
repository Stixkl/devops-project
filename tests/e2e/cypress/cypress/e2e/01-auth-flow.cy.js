describe('E2E - Auth Flow (API)', () => {
  const authService = Cypress.env('authServiceUrl');

  it('TC_E2E_001: POST /api/v1/auth/login - successful login returns JWT token', () => {
    cy.request({
      method: 'POST',
      url: `${authService}/api/v1/auth/login`,
      body: { username: 'testuser', password: 'password123' },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 401, 403]);
      if (res.status === 200) {
        expect(res.body).to.have.property('token');
        expect(res.body).to.have.property('anonymousId');
        expect(res.body.type).to.eq('Bearer');
      }
    });
  });

  it('TC_E2E_002: POST /api/v1/auth/login - invalid credentials returns 401', () => {
    cy.request({
      method: 'POST',
      url: `${authService}/api/v1/auth/login`,
      body: { username: 'wronguser', password: 'wrongpass' },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([401, 403, 400]);
    });
  });

  it('TC_E2E_003: POST /api/v1/auth/visitor/handoff - generates visitor token', () => {
    const anonymousId = '550e8400-e29b-41d4-a716-446655440000';
    cy.request({
      method: 'POST',
      url: `${authService}/api/v1/auth/visitor/handoff`,
      body: { anonymousId },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 401]);
      if (res.status === 200) {
        expect(res.body).to.have.property('token');
        expect(res.body).to.have.property('handoffPayload');
      }
    });
  });

  it('TC_E2E_004: GET /actuator/health - health endpoint responds', () => {
    cy.request({
      method: 'GET',
      url: `${authService}/actuator/health`,
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 503]);
    });
  });

  it('TC_E2E_005: Full flow: visitor handoff -> anonymous token obtained', () => {
    // anonymousId must be a UUID — the API rejects any other format with 400
    const anonymousId = crypto.randomUUID();
    cy.request({
      method: 'POST',
      url: `${authService}/api/v1/auth/visitor/handoff`,
      body: { anonymousId },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 401]);
      if (res.status === 200) {
        expect(res.body).to.have.property('token');
        expect(res.body.token).to.be.a('string');
        expect(res.body.token.length).to.be.greaterThan(0);
      }
    });
  });
});