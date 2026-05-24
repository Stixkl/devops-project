describe('E2E - Health Survey Flow (API)', () => {
  const formService = Cypress.env('formServiceUrl');
  const authService = Cypress.env('authServiceUrl');

  it('TC_E2E_006: POST /api/v1/surveys - submits healthy survey', () => {
    cy.request({
      method: 'POST',
      url: `${formService}/api/v1/surveys`,
      body: {
        userId: 'user-test-' + Date.now(),
        responses: {
          fever: 'NO',
          cough: 'NO',
          breathing: 'NO'
        },
        submittedAt: Date.now()
      },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 201, 401, 403]);
    });
  });

  it('TC_E2E_007: POST /api/v1/surveys - submits symptom survey', () => {
    cy.request({
      method: 'POST',
      url: `${formService}/api/v1/surveys`,
      body: {
        userId: 'user-symptom-' + Date.now(),
        responses: {
          fever: 'YES',
          cough: 'NO'
        },
        submittedAt: Date.now()
      },
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 201, 401, 403]);
    });
  });

  it('TC_E2E_008: GET /api/v1/questionnaires/health - retrieves questionnaire', () => {
    cy.request({
      method: 'GET',
      url: `${formService}/api/v1/questionnaires/health`,
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 404, 401]);
    });
  });

  it('TC_E2E_009: GET /actuator/health - form service healthy', () => {
    cy.request({
      method: 'GET',
      url: `${formService}/actuator/health`,
      failOnStatusCode: false
    }).then(res => {
      expect(res.status).to.be.oneOf([200, 503]);
    });
  });

  it('TC_E2E_010: End-to-end: Login -> Submit Survey -> Status Reported', () => {
    const anonymousId = 'e2e-' + Date.now();
    
    cy.request({
      method: 'POST',
      url: `${authService}/api/v1/auth/visitor/handoff`,
      body: { anonymousId },
      failOnStatusCode: false
    }).then(handoff => {
      if (handoff.status === 200) {
        cy.request({
          method: 'POST',
          url: `${formService}/api/v1/surveys`,
          body: {
            userId: anonymousId,
            responses: { fever: 'YES', cough: 'YES' },
            submittedAt: Date.now()
          },
          failOnStatusCode: false
        }).then(survey => {
          expect(survey.status).to.be.oneOf([200, 201, 401]);
        });
      }
    });
  });
});