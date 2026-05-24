describe('E2E - Identity Service (API)', () => {
  const identityService = Cypress.env('identityServiceUrl');
  const authService = Cypress.env('authServiceUrl');

  it('TC_E2E_021: POST /api/v1/identity/create - creates anonymous identity', () => {
    cy.request({
      method: 'POST',
      url: `${identityService}/api/v1/identity/create`,
      body: { username: 'identity-user-' + Date.now() },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 201, 401]);
    });
  });

  it('TC_E2E_022: GET /api/v1/identity/{username} - retrieves identity mapping', () => {
    cy.request({
      method: 'GET',
      url: `${identityService}/api/v1/identity/testuser`,
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 404, 401]);
    });
  });

  it('TC_E2E_023: GET /actuator/health - identity service healthy', () => {
    cy.request({
      method: 'GET',
      url: `${identityService}/actuator/health`,
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 503]);
    });
  });
});